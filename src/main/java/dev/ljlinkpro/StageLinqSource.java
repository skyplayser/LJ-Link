package dev.ljlinkpro;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Denon StageLinQ source (Prime 4 / 4+, SC6000, Prime Go and other Engine DJ devices). Passive: it announces itself
 * like the other StageLinQ clients, subscribes to values and listens. It never writes values to the device.
 *
 * <p>What it shows: title, artist, tempo, play state, master, track length, the channel fader positions and the
 * playhead position (from BeatInfo). Waveform, beat grid, cues and cover art come from the Engine library, which is
 * downloaded once from the device's FileTransfer service ({@link StageLinqLibrary}).
 */
final class StageLinqSource implements DeckSource {
    private static final String SOFTWARE_NAME = "LJ Link";
    private static final String SOFTWARE_VERSION = "1.4.4";
    private static final int DEFAULT_SAMPLE_RATE = 44_100;
    private static final Pattern DECK_VALUE = Pattern.compile("^/Engine/Deck(\\d)/(.+)$");
    private static final Pattern FADER = Pattern.compile("^/Mixer/CH(\\d)faderPosition$");

    /** The values we subscribe to, per deck 1 to 4, plus the mixer. */
    private static final List<String> DECK_VALUES = List.of(
            "Track/SongLoaded", "Track/SongName", "Track/ArtistName", "Track/TrackLength", "Track/CurrentBPM",
            "Track/SampleRate", "CurrentBPM", "Play", "PlayState", "DeckIsMaster", "Speed", "Track/CurrentKeyIndex",
            "Track/LoopEnableState", "Track/CurrentLoopInPosition", "Track/CurrentLoopOutPosition",
            "Track/CurrentLoopSizeInBeats", "Track/CuePosition", "Track/SongAnalyzed", "Track/TrackNetworkPath",
            "Track/TrackUri", "Track/TrackName");

    /** Helper services some Denon devices announce next to the real device; their "decks" are not players. */
    private static final java.util.Set<String> IGNORED_SOFTWARE = java.util.Set.of("offlineanalyzer");

    private final Hub.Scope hub;
    private final byte[] token;
    /** Called when the connection to the device ended on its own (the device was switched off or unplugged). */
    private final Runnable onEnd;
    private final java.util.List<Thread> sessions = new java.util.concurrent.CopyOnWriteArrayList<>();
    /** Every open connection, so that stop() can close them (a blocked read does not notice an interrupt). */
    private final java.util.Set<Socket> open = ConcurrentHashMap.newKeySet();
    private final int[] sampleRate = {0, DEFAULT_SAMPLE_RATE, DEFAULT_SAMPLE_RATE, DEFAULT_SAMPLE_RATE, DEFAULT_SAMPLE_RATE};
    private final double[] trackBpm = new double[5];
    private final double[] trackLengthSamples = new double[5];
    private final boolean[] loopOn = new boolean[5];
    private final double[] loopIn = new double[5];
    private final double[] loopOut = new double[5];
    private final double[] currentBpm = new double[5];
    private static final boolean DEBUG = System.getenv("LJLINKPRO_DEBUG") != null;
    private final java.util.Set<String> debugSeen = ConcurrentHashMap.newKeySet();
    private volatile boolean stopped;
    private final java.util.concurrent.atomic.AtomicReference<StageLinqLibrary> library = new java.util.concurrent.atomic.AtomicReference<>();
    private final String[] trackPath = new String[5];
    private final long[] trackPathTime = new long[5];
    /** Waveform bands (low, mid, high; 3 bytes per entry) of the loaded tracks, kept to build the detail view when the length is known. */
    private final byte[][] bands = new byte[5][];
    private volatile boolean connected;
    private final String deviceName;

    StageLinqSource(Hub.Scope hub, byte[] token, String deviceName, Runnable onEnd) {
        this.hub = hub;
        this.token = token;
        this.deviceName = deviceName;
        this.onEnd = onEnd;
        hub.setSource(this);
    }

    /** Connects to the device; the discovery ({@link StageLinqDiscovery}) found it. */
    @Override
    public void start() {
        // connectDevice() is called by the discovery, which knows the address
    }

    /* ---------- device session ---------- */

    /** Opens the directory service of a device. Also used by the tests, which play the device. */
    Thread connectDevice(String host, int port, byte[] deviceToken) {
        Thread t = new Thread(() -> runSession(host, port, deviceToken), "stagelinq-" + host);
        t.setDaemon(true);
        sessions.add(t);
        t.start();
        return t;
    }

    private void runSession(String host, int port, byte[] deviceToken) {
        Map<String, Integer> services = new ConcurrentHashMap<>();
        try (Socket sock = new Socket()) {
            open.add(sock);
            sock.connect(new InetSocketAddress(host, port), 5000);
            sock.setTcpNoDelay(true);
            debug("directory connected " + host + ":" + port);
            DataInputStream in = new DataInputStream(new BufferedInputStream(sock.getInputStream()));
            DataOutputStream out = new DataOutputStream(sock.getOutputStream());
            synchronized (out) {
                out.write(StageLinqProtocol.servicesRequest(token));
                out.flush();
            }
            Thread heartbeat = new Thread(() -> {
                while (!stopped && !sock.isClosed()) {
                    try {
                        synchronized (out) {
                            out.write(StageLinqProtocol.reference(token, deviceToken, 0));
                            out.flush();
                        }
                        Thread.sleep(250);
                    } catch (IOException | InterruptedException e) {
                        return;
                    }
                }
            }, "stagelinq-heartbeat");
            heartbeat.setDaemon(true);
            heartbeat.start();

            boolean servicesStarted = false;
            while (!stopped) {
                int id = in.readInt();
                switch (id) {
                    case StageLinqProtocol.ID_SERVICE_ANNOUNCEMENT -> {
                        in.readFully(new byte[16]);
                        String name = StageLinqProtocol.readString(in);
                        int servicePort = in.readUnsignedShort();
                        services.put(name, servicePort);
                        debug("service " + name + " on port " + servicePort);
                    }
                    case StageLinqProtocol.ID_REFERENCE -> {
                        in.readFully(new byte[32]);
                        in.readLong();
                        if (!servicesStarted && !services.isEmpty()) {
                            servicesStarted = true;
                            debug("services: " + services.keySet());
                            startServices(host, services);
                        }
                    }
                    case StageLinqProtocol.ID_SERVICES_REQUEST -> in.readFully(new byte[16]); // we offer no services
                    default -> throw new IOException("Unknown directory message " + id);
                }
            }
        } catch (IOException e) {
            if (!stopped) {
                System.err.println("StageLinQ session to " + host + " ended: " + e);
            }
        }
        if (!stopped && onEnd != null) {
            onEnd.run();
        }
    }

    private void startServices(String host, Map<String, Integer> services) {
        Integer stateMap = services.get("StateMap");
        if (stateMap != null) {
            Thread t = new Thread(() -> runStateMap(host, stateMap), "stagelinq-statemap");
            t.setDaemon(true);
            t.start();
        }
        Integer files = services.get("FileTransfer");
        if (files != null) {
            Thread t = new Thread(() -> runFiles(host, files), "stagelinq-files");
            t.setDaemon(true);
            t.start();
        }
        Integer beatInfo = services.get("BeatInfo");
        if (beatInfo != null) {
            Thread t = new Thread(() -> runBeatInfo(host, beatInfo), "stagelinq-beatinfo");
            t.setDaemon(true);
            t.start();
        }
    }

    private void runStateMap(String host, int port) {
        try (Socket sock = new Socket()) {
            open.add(sock);
            sock.connect(new InetSocketAddress(host, port), 5000);
            DataInputStream in = new DataInputStream(new BufferedInputStream(sock.getInputStream()));
            DataOutputStream out = new DataOutputStream(sock.getOutputStream());
            out.write(StageLinqProtocol.serviceAnnouncement(token, "StateMap", sock.getLocalPort()));
            for (int deck = 1; deck <= 4; deck++) {
                for (String v : DECK_VALUES) {
                    out.write(StageLinqProtocol.subscribe("/Engine/Deck" + deck + "/" + v, 0));
                }
                out.write(StageLinqProtocol.subscribe("/Mixer/CH" + deck + "faderPosition", 0));
            }
            out.flush();
            debug("StateMap subscribed");
            markConnected();
            while (!stopped) {
                StageLinqProtocol.StateEmit e = StageLinqProtocol.parseStateFrame(StageLinqProtocol.readFrame(in));
                if (e != null) {
                    if (DEBUG && debugSeen.add(e.name())) {
                        System.out.println("StageLinQ value " + e.name() + " = " + e.json());
                    }
                    try {
                        applyState(e.name(), Hub.JSON.readTree(e.json()));
                    } catch (IOException ex) {
                        // a value we cannot read is skipped, the rest keeps working
                    }
                }
            }
        } catch (IOException e) {
            if (!stopped) {
                System.err.println("StageLinQ StateMap ended: " + e.getMessage());
            }
        }
    }

    /** Fetches the Engine library from the device's media once; waveforms, beat grids and cues of loaded tracks come from it. */
    private void runFiles(String host, int port) {
        try (StageLinqFiles files = new StageLinqFiles(host, port, token)) {
            List<String> sources = files.sources();
            debug("sources: " + sources);
            for (String source : sources) {
                for (String db : List.of("/" + source + "/Engine Library/Database2/m.db", "/" + source + "/Engine Library/m.db")) {
                    long size = files.size(db);
                    debug("file " + db + " size " + size);
                    if (size > 0) {
                        byte[] data = files.read(db);
                        StageLinqLibrary lib = StageLinqLibrary.open(source, data);
                        StageLinqLibrary old = library.getAndSet(lib);
                        if (old != null) {
                            old.close();
                        }
                        debug("library loaded: " + data.length + " bytes");
                        for (int n = 1; n <= 4; n++) {
                            loadTrack(n);
                        }
                        break;
                    }
                }
            }
        } catch (IOException | java.sql.SQLException e) {
            System.err.println("StageLinQ FileTransfer ended: " + e);
        }
    }

    /** Looks the loaded track of a deck up in the library (when both the path and the library are there). */
    private void loadTrack(int n) {
        StageLinqLibrary lib = library.get();
        String path = trackPath[n];
        Deck d = hub.deck(n);
        if (lib == null || path == null || !d.hasTrack) {
            return;
        }
        try {
            StageLinqLibrary.TrackData t = lib.find(path);
            debug("deck " + n + " library lookup " + path + " -> " + (t == null ? "not found" : "found"));
            if (t == null || !path.equals(trackPath[n])) {
                return;
            }
            debug("deck " + n + " waveform " + (t.waveform() == null ? "null" : t.waveform().length) + " beats " + t.beatTimes().length + " cues " + t.cues().size());
            d.noWaveformSource = false;
            d.previewStyle = "threeband";
            d.detailStyle = "threeband";
            d.setWaveform(t.waveform());
            bands[n] = t.bands();
            d.setDetail(StageLinqLibrary.detailFrom(t.bands(), d.durationMs));
            d.setBeats(t.beatTimes(), t.beatBars());
            if (t.art() != null) {
                d.setArt(t.art());
            }
            if (!t.cues().isEmpty()) {
                d.cues = t.cues();
            }
        } catch (java.sql.SQLException e) {
            System.err.println("StageLinQ library lookup failed: " + e.getMessage());
        }
    }

    private void runBeatInfo(String host, int port) {
        try (Socket sock = new Socket()) {
            open.add(sock);
            sock.connect(new InetSocketAddress(host, port), 5000);
            DataInputStream in = new DataInputStream(new BufferedInputStream(sock.getInputStream()));
            DataOutputStream out = new DataOutputStream(sock.getOutputStream());
            out.write(StageLinqProtocol.serviceAnnouncement(token, "BeatInfo", sock.getLocalPort()));
            out.write(StageLinqProtocol.beatStartStream());
            out.flush();
            while (!stopped) {
                StageLinqProtocol.Beat b = StageLinqProtocol.parseBeatFrame(StageLinqProtocol.readFrame(in));
                if (b != null) {
                    if (DEBUG && debugSeen.add("beat" + (System.currentTimeMillis() / 2000))) {
                        System.out.println("StageLinQ beat clock=" + b.clock() + " beat=" + java.util.Arrays.toString(b.beat())
                                + " total=" + java.util.Arrays.toString(b.totalBeats()) + " bpm=" + java.util.Arrays.toString(b.bpm())
                                + " samples=" + java.util.Arrays.toString(b.samples()));
                    }
                    applyBeat(b);
                }
            }
        } catch (IOException e) {
            if (!stopped) {
                System.err.println("StageLinQ BeatInfo ended: " + e.getMessage());
            }
        }
    }

    private static void debug(String message) {
        if (DEBUG) {
            System.out.println("StageLinQ " + message);
        }
    }

    /** True once the device delivers values. */
    boolean isConnected() {
        return connected;
    }

    private void markConnected() {
        if (!connected) {
            connected = true;
            hub.setMixer(Map.of("present", true, "name", deviceName, "number", 0, "address", "", "bpm", 0.0,
                    "master", false, "simulated", false, "levels", true, "eq", false));
            hub.setState("monitoring", "live", "Connected");
        }
    }

    /* ---------- values to decks ---------- */

    /** Applies one value from the StateMap. Package-private for the tests. */
    void applyState(String name, JsonNode json) {
        Matcher f = FADER.matcher(name);
        if (f.matches()) {
            int n = Integer.parseInt(f.group(1));
            Deck d = hub.deck(n);
            Double v = number(json);
            if (v != null) {
                d.fader = Math.max(0, Math.min(1, v));
                d.onAir = d.fader > 0.05;
            }
            return;
        }
        Matcher m = DECK_VALUE.matcher(name);
        if (!m.matches()) {
            return;
        }
        int n = Integer.parseInt(m.group(1));
        if (n < 1 || n > 4) {
            return;
        }
        Deck d = hub.deck(n);
        d.name = deviceName;
        d.noWaveformSource = d.waveform == null;
        switch (m.group(2)) {
            case "Track/SongLoaded" -> {
                Boolean loaded = bool(json);
                if (loaded != null) {
                    d.hasTrack = loaded;
                    if (loaded && trackPath[n] != null && System.currentTimeMillis() - trackPathTime[n] < 5000) {
                        loadTrack(n);
                    }
                    if (!loaded) {
                        debug("deck " + n + " unloaded");
                        bands[n] = null;
                        d.clearTrack();
                    }
                }
            }
            case "Track/TrackNetworkPath" -> {
                String path = text(json);
                if (path != null && !path.isBlank()) {
                    if (!path.equals(trackPath[n])) {
                        d.trackVersion++; // another track (Deck.stableBpm, Os2lBeats)
                    }
                    trackPath[n] = path;
                    trackPathTime[n] = System.currentTimeMillis();
                    loadTrack(n);
                }
            }
            case "Track/SongName" -> d.title = text(json);
            case "Track/ArtistName" -> d.artist = text(json);
            case "Track/TrackLength" -> {
                Double v = number(json);
                if (v != null) {
                    // A number of samples (11473056 at 44100 Hz is 260 s on a Prime 4+ with firmware 5.0.4).
                    d.durationMs = Math.round(v * 1000.0 / sampleRate[n]);
                    trackLengthSamples[n] = v;
                    if (bands[n] != null) {
                        d.setDetail(StageLinqLibrary.detailFrom(bands[n], d.durationMs));
                    }
                }
            }
            case "Track/SampleRate" -> {
                Double v = number(json);
                if (v != null && v > 8000) {
                    sampleRate[n] = v.intValue();
                    if (trackLengthSamples[n] > 0) {
                        d.durationMs = Math.round(trackLengthSamples[n] * 1000.0 / sampleRate[n]);
                    }
                }
            }
            case "Track/CurrentBPM" -> {
                Double v = number(json);
                if (v != null && v > 0) {
                    trackBpm[n] = v;
                    d.trackBpm = v;
                }
                updatePitch(n, d);
            }
            case "CurrentBPM" -> {
                Double v = number(json);
                if (v != null && v > 0) {
                    currentBpm[n] = v;
                    d.effectiveBpm = v;
                }
                updatePitch(n, d);
            }
            case "Play", "PlayState" -> {
                Boolean playing = bool(json);
                if (playing != null) {
                    d.playing = playing;
                }
            }
            case "Track/LoopEnableState" -> {
                Boolean on = bool(json);
                if (on != null) {
                    loopOn[n] = on;
                    updateLoop(n, d);
                }
            }
            case "Track/CurrentLoopInPosition" -> {
                Double v = number(json);
                if (v != null) {
                    loopIn[n] = v;
                    updateLoop(n, d);
                }
            }
            case "Track/CurrentLoopOutPosition" -> {
                Double v = number(json);
                if (v != null) {
                    loopOut[n] = v;
                    updateLoop(n, d);
                }
            }
            case "Track/CurrentLoopSizeInBeats" -> {
                Double v = number(json);
                if (v != null) {
                    d.loopBeats = (int) Math.round(v);
                }
            }
            case "Track/CuePosition" -> {
                Double v = number(json);
                if (v != null && v > 0) {
                    // The main cue point, shown like a memory cue. The unit is milliseconds on the firmware we saw.
                    d.cues = d.durationMs > 0 && v > d.durationMs ? List.of() : List.of(Map.<String, Object>of(
                            "t", Math.round(v), "hot", 0, "loop", false, "loopEnd", 0L, "color", "#ff2a2a"));
                }
            }
            case "DeckIsMaster" -> {
                Boolean master = bool(json);
                if (master != null) {
                    d.master = master;
                }
            }
            default -> {
                // other values are not used
            }
        }
    }

    /** The loop is shown only when the positions look like milliseconds inside the track, otherwise it is ignored. */
    private void updateLoop(int n, Deck d) {
        boolean plausible = loopOn[n] && loopOut[n] > loopIn[n] && d.durationMs > 0 && loopOut[n] <= d.durationMs;
        d.loop = plausible ? new long[] {Math.round(loopIn[n]), Math.round(loopOut[n])} : null;
    }

    private void updatePitch(int n, Deck d) {
        if (trackBpm[n] > 0 && currentBpm[n] > 0) {
            d.pitchPercent = (currentBpm[n] / trackBpm[n] - 1) * 100;
            d.speed = currentBpm[n] / trackBpm[n];
        }
    }

    /** Applies a BeatInfo message: the playhead position of every deck from its sample position. */
    void applyBeat(StageLinqProtocol.Beat b) {
        for (int i = 0; i < b.beat().length && i < 4; i++) {
            int n = i + 1;
            Deck d = hub.deck(n);
            double samples = b.samples()[i];
            if (samples > 0) {
                d.samplePosition(Math.round(samples * 1000.0 / sampleRate[n]), b.clock());
            } else if (b.bpm()[i] > 0) {
                // Without a sample position the beat count at the current tempo gives a rough position.
                d.samplePosition(Math.round(b.beat()[i] * 60_000.0 / b.bpm()[i]));
            }
        }
    }

    private static Double number(JsonNode json) {
        JsonNode v = json.path("value");
        if (v.isNumber()) {
            return v.asDouble();
        }
        JsonNode s = json.path("string");
        if (s.isTextual()) {
            try {
                return Double.parseDouble(s.asText());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static Boolean bool(JsonNode json) {
        JsonNode state = json.path("state");
        if (state.isBoolean()) {
            return state.asBoolean();
        }
        JsonNode v = json.path("value");
        if (v.isBoolean()) {
            return v.asBoolean();
        }
        if (v.isNumber()) {
            return v.asDouble() != 0;
        }
        return null;
    }

    private static String text(JsonNode json) {
        JsonNode s = json.path("string");
        return s.isTextual() ? s.asText() : null;
    }

    /* ---------- detail view ---------- */

    @Override
    public List<Map<String, Object>> info(int deck) {
        Deck d = hub.deck(deck);
        java.util.function.BiFunction<String, Object, Map<String, String>> row =
                (k, v) -> Map.of("k", k, "v", v == null ? "–" : String.valueOf(v));
        String yes = "yes";
        String no = "no";
        return List.of(
                Map.of("title", "Device", "rows", List.of(row.apply("Name", d.name), row.apply("Player number", deck),
                        row.apply("Protocol", "Denon StageLinQ"))),
                Map.of("title", "Status", "rows", List.of(row.apply("Track loaded", d.hasTrack ? yes : no),
                        row.apply("Playing", d.playing ? yes : no), row.apply("Tempo master", d.master ? yes : no),
                        row.apply("ON AIR (from mixer)", d.onAir ? yes : no))),
                Map.of("title", "Tempo and sync", "rows", List.of(
                        row.apply("Track BPM", d.trackBpm > 0 ? String.format(java.util.Locale.ROOT, "%.2f", d.trackBpm) : null),
                        row.apply("Effective BPM", d.effectiveBpm > 0 ? String.format(java.util.Locale.ROOT, "%.2f", d.effectiveBpm) : null),
                        row.apply("Pitch", String.format(java.util.Locale.ROOT, "%+.2f %%", d.pitchPercent)))),
                Map.of("title", "Track", "rows", List.of(row.apply("Title", d.title), row.apply("Artist", d.artist),
                        row.apply("Duration", d.durationMs > 0 ? d.durationMs / 1000 + " s" : null))),
                Map.of("title", "Mixer", "rows", List.of(row.apply("Channel fader",
                        d.fader == null ? null : Math.round(d.fader * 100) + " %"))));
    }

    /* ---------- stop ---------- */

    @Override
    public void stop() {
        stopped = true;
        for (Socket sock : open) {
            try {
                sock.close();
            } catch (IOException e) {
                // already closed
            }
        }
        for (Thread t : sessions) {
            if (t != Thread.currentThread()) {
                t.interrupt();
            }
        }
    }
}
