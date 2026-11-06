package dev.ljlinkpro;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Sends the tempo of the leading deck to a lighting desk or software: to a grandMA2 as a command line over Telnet, to a
 * grandMA3 as a command line over OSC, to anything else as an OSC number, to the Wolfmix software (WTOOLS) and other
 * OS2L receivers as a beat on every beat, and to a ChamSys MagicQ as an OSC tap on every beat. Output only: LJ Link never
 * receives from or controls the DJ gear through this.
 */
final class LinkService {
    /**
     * {@code type}: "off", "ma2" (grandMA2 / onPC command line over Telnet, with {@code user} and {@code password}),
     * "ma" (grandMA3 / onPC command line over OSC {@code /cmd}), "osc" (the tempo as an OSC number to {@code address})
     * "os2l" (beats and tempo as OS2L messages over TCP, for the Wolfmix software WTOOLS) or "chamsys" (an OSC message
     * without argument on every beat to each address in {@code address}, a comma separated list; MagicQ maps it to Tap to Time).
     */
    record Config(String type, String host, int port, String address, String command, String user, String password) {
        static Config off() {
            return new Config("off", "127.0.0.1", 8000, "/ljlinkpro/bpm", MA3_COMMAND, "", "");
        }
    }

    static final String CHAMSYS_ADDRESS = "/tap";
    static final String MA2_COMMAND = "SpecialMaster 3.16 At {bpm}";
    /** Every grandMA2 show has these users: "Administrator" with the password "admin", and "Guest". */
    static final String MA2_DEFAULT_USER = "administrator";
    static final String MA2_DEFAULT_PASSWORD = "admin";
    static final String MA3_COMMAND = "Master 3.1 At BPM {bpm}";
    /** The command line of a grandMA2 is not flooded: unchanged tempos are repeated only this often. */
    private static final long MA2_KEEPALIVE_MS = 15_000;

    private final Ma2Telnet telnet = new Ma2Telnet();
    private final Os2lClient os2l = new Os2lClient();
    private final Os2lBeats beats = new Os2lBeats();
    /** OS2L: how often the beat clock is checked, which is also the largest delay a beat can get. */
    private static final long BEAT_TICK_MS = 4;

    record Status(Double bpm, int sentCount, String error, String source, String sendingDeck) {}

    /** A deck the tempo can be taken from, for the choice in the browser. */
    record Candidate(String key, String name, int label, String kind, double bpm, boolean playing, boolean master, boolean onAir) {}

    private static final Path FILE = Path.of(System.getProperty("user.home"), ".ljlinkpro", "link.json");
    private static final Path SOURCE_FILE = Path.of(System.getProperty("user.home"), ".ljlinkpro", "link-source.txt");
    static final String AUTO = "auto";
    private static final long KEEPALIVE_MS = 2000;

    private final Hub hub;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "link-output");
        t.setDaemon(true);
        return t;
    });
    private final ScheduledExecutorService beatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "os2l-beat");
        t.setDaemon(true);
        return t;
    });
    private volatile Config config = load();
    private volatile long beatRetryAt;
    private volatile Double lastBpm;
    private volatile long lastSentAt;
    private volatile int sentCount;
    private volatile String error;
    /** "auto" (master, else the deck on air) or the key of one deck, see {@link Deck#key()}. */
    private volatile String source = loadSource();
    private volatile String sendingDeck;
    private volatile long retryAt;

    LinkService(Hub hub) {
        this(hub, true);
    }

    /** {@code send = false} for tests: nothing is ever sent to a real desk. */
    LinkService(Hub hub, boolean send) {
        this.hub = hub;
        if (send) {
            executor.scheduleAtFixedRate(this::tick, 250, 250, TimeUnit.MILLISECONDS);
            beatExecutor.scheduleAtFixedRate(this::beatTick, 250, BEAT_TICK_MS, TimeUnit.MILLISECONDS);
        }
    }

    Config config() {
        return config;
    }

    Status status() {
        return new Status(lastBpm, sentCount, error, source, sendingDeck);
    }

    void setConfig(Config c) throws IOException {
        String type = java.util.List.of("off", "ma2", "ma", "osc", "os2l", "chamsys").contains(c.type()) ? c.type() : "off";
        Config clean = new Config(
                type,
                c.host() == null ? "127.0.0.1" : c.host().trim(),
                c.port() < 1 || c.port() > 65535 ? ("ma2".equals(type) ? Ma2Telnet.PORT : "os2l".equals(type) ? Os2lClient.PORT : 8000) : c.port(),
                "chamsys".equals(type) ? String.join(",", tapAddresses(c.address()))
                        : c.address() == null || c.address().isBlank() ? "/ljlinkpro/bpm" : c.address().trim(),
                c.command() == null || c.command().isBlank() ? ("ma2".equals(type) ? MA2_COMMAND : MA3_COMMAND) : c.command().trim(),
                "ma2".equals(type) && (c.user() == null || c.user().isBlank()) ? MA2_DEFAULT_USER : c.user() == null ? "" : c.user().trim(),
                "ma2".equals(type) && (c.user() == null || c.user().isBlank()) && (c.password() == null || c.password().isEmpty())
                        ? MA2_DEFAULT_PASSWORD : c.password() == null ? "" : c.password());
        telnet.close();
        os2l.close();
        beats.reset();
        retryAt = 0;
        beatRetryAt = 0;
        config = clean;
        error = null;
        lastBpm = null;
        Files.createDirectories(FILE.getParent());
        Files.writeString(FILE, Hub.JSON.writeValueAsString(clean));
    }

    String source() {
        return source;
    }

    void setSource(String deckKey) throws IOException {
        source = deckKey == null || deckKey.isBlank() ? AUTO : deckKey.trim();
        lastBpm = null;
        Files.createDirectories(SOURCE_FILE.getParent());
        Files.writeString(SOURCE_FILE, source);
    }

    private static String loadSource() {
        try {
            if (Files.exists(SOURCE_FILE)) {
                String s = Files.readString(SOURCE_FILE).trim();
                return s.isEmpty() ? AUTO : s;
            }
        } catch (IOException e) {
            // fall back to automatic
        }
        return AUTO;
    }

    /** All decks with a track, in display order. */
    java.util.List<Candidate> candidates() {
        java.util.List<Deck> list = new java.util.ArrayList<>(hub.allDecks());
        list.sort(java.util.Comparator.comparingInt((Deck d) -> d.scope == null ? 0 : d.scope.order).thenComparingInt(d -> d.label));
        java.util.List<Candidate> out = new java.util.ArrayList<>();
        for (Deck d : list) {
            String name = d.scope != null && !d.scope.groupPerDeck ? d.scope.groupName : d.name;
            out.add(new Candidate(d.key(), name == null ? "" : name, d.label, d.scope == null ? "" : d.scope.kind,
                    d.hasTrack ? d.effectiveBpm : 0, d.playing, d.master, d.onAir));
        }
        return out;
    }

    /** Sends a fixed tempo once so the user can check the connection and the mapping on the desk. */
    void sendTest() {
        Config c = config;
        if (c.type().equals("os2l") || c.type().equals("chamsys")) {
            // Eight beats at 120 BPM, off the beat thread so the call returns at once.
            Thread t = new Thread(() -> {
                try {
                    for (int i = 0; i < 8; i++) {
                        if (c.type().equals("chamsys")) {
                            sendTaps(c);
                        } else {
                            os2l.beat(c.host(), c.port(), i == 0, i, 120.0);
                        }
                        sentCount++;
                        lastBpm = 120.0;
                        error = null;
                        Thread.sleep(500);
                    }
                } catch (IOException e) {
                    error = c.type().equals("chamsys") ? tapError(c, e) : os2lError(c, e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, c.type().equals("chamsys") ? "chamsys-test" : "os2l-test");
            t.setDaemon(true);
            t.start();
            return;
        }
        send(config, 120.0);
    }

    private static String os2lError(Config c, IOException e) {
        return "No OS2L receiver at " + c.host() + ":" + c.port() + " (" + e.getMessage()
                + "). Is the lighting program running with OS2L switched on?";
    }

    private static String tapError(Config c, IOException e) {
        return "Sending to " + c.host() + ":" + c.port() + " failed: " + e.getMessage();
    }

    /** The OSC addresses of a ChamSys tap list ("/tap1, /tap2"): only entries that start with a slash, "/tap" when none. */
    static java.util.List<String> tapAddresses(String list) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (list != null) {
            for (String a : list.split("[,;\\s]+")) {
                if (a.startsWith("/") && a.length() > 1 && !out.contains(a)) {
                    out.add(a);
                }
            }
        }
        if (out.isEmpty()) {
            out.add(CHAMSYS_ADDRESS);
        }
        return out;
    }

    /** One OSC message without argument to every address of the tap list: what ChamSys MagicQ maps to Tap to Time. */
    void sendTaps(Config c) throws IOException {
        java.util.List<String> addresses = tapAddresses(c.address());
        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress target = InetAddress.getByName(c.host());
            for (String address : addresses) {
                byte[] packet = oscTap(address);
                socket.send(new DatagramPacket(packet, packet.length, target, c.port()));
            }
        }
    }

    /** An OSC message with an empty type tag: address and no argument. */
    static byte[] oscTap(String address) {
        byte[] a = padded(address);
        byte[] t = padded(",");
        return ByteBuffer.allocate(a.length + t.length).put(a).put(t).array();
    }

    /** OS2L and ChamSys: sends a beat when one of the leading deck passes. Runs every few milliseconds, so it must stay cheap. */
    private void beatTick() {
        try {
            Config c = config;
            boolean chamsys = c.type().equals("chamsys");
            if (!c.type().equals("os2l") && !chamsys) {
                return;
            }
            Deck lead = leadingDeck();
            if (lead != null && lead.scope != null && "demo".equals(lead.scope.kind)) {
                return; // the demo tempo never reaches a real receiver; tick() explains it in the status
            }
            Os2lBeats.Beat beat = beats.next(lead, System.nanoTime());
            if (beat == null) {
                return;
            }
            long now = System.currentTimeMillis();
            if (now < beatRetryAt) {
                return;
            }
            try {
                if (chamsys) {
                    sendTaps(c);
                } else {
                    os2l.beat(c.host(), c.port(), beat.change(), beat.pos(), beat.bpm());
                }
                sentCount++;
                lastBpm = beat.bpm();
                lastSentAt = now;
                if (error != null && (error.startsWith("No OS2L") || error.startsWith("Sending to"))) {
                    error = null;
                }
            } catch (IOException e) {
                error = chamsys ? tapError(c, e) : os2lError(c, e);
                beatRetryAt = now + 5000;
            }
        } catch (RuntimeException e) {
            error = e.getMessage();
        }
    }

    private static Config load() {
        try {
            if (Files.exists(FILE)) {
                return Hub.JSON.readValue(Files.readString(FILE), Config.class);
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("Could not read link settings: " + e);
        }
        return Config.off();
    }

    /**
     * The deck whose tempo is sent: the chosen one, or automatically the master deck, else the deck that is on air and
     * playing, else any playing deck. Null when there is nothing to send (the chosen deck is gone or has no tempo).
     */
    Deck leadingDeck() {
        String src = source;
        if (!AUTO.equals(src)) {
            for (Deck d : hub.allDecks()) {
                if (src.equals(d.key())) {
                    return d.hasTrack && d.effectiveBpm > 0 ? d : null;
                }
            }
            return null;
        }
        Deck best = null;
        int rank = 0;
        // With two systems (say CDJs and a Denon) each has its own master; the one that is also live and playing wins,
        // and on a tie the device found first.
        java.util.List<Deck> ordered = new java.util.ArrayList<>(hub.allDecks());
        ordered.sort(java.util.Comparator.comparingInt((Deck d) -> d.scope == null ? 0 : d.scope.order).thenComparingInt(d -> d.label));
        for (Deck d : ordered) {
            if (!d.hasTrack || d.effectiveBpm <= 0) {
                continue;
            }
            int r = (d.master ? 4 : 0) + (d.onAir && d.playing ? 2 : 0) + (d.playing ? 1 : 0);
            if (r > rank) {
                rank = r;
                best = d;
            }
        }
        return best;
    }

    /** Marks the deck the tempo comes from, so every browser can show it on that deck. */
    private void markSending(Deck lead) {
        for (Deck d : hub.allDecks()) {
            d.sendingBpm = d == lead;
        }
    }

    private void tick() {
        try {
            Config c = config;
            if (c.type().equals("off")) {
                markSending(null);
                return;
            }
            Deck lead = leadingDeck();
            sendingDeck = lead == null ? null : lead.key();
            markSending(lead);
            if (lead == null) {
                if (AUTO.equals(source) && error != null && error.startsWith("The chosen deck")) {
                    error = null;
                }
                if (!AUTO.equals(source)) {
                    error = "The chosen deck has no tempo right now, nothing is sent.";
                }
                return;
            }
            if (lead.scope != null && "demo".equals(lead.scope.kind)) {
                // Simulated tempos must never reach a real desk.
                error = "Demo mode: nothing is sent to the desk.";
                return;
            }
            if (error != null && (error.startsWith("The chosen deck") || error.startsWith("Demo mode"))) {
                error = null;
            }
            if (c.type().equals("os2l") || c.type().equals("chamsys")) {
                return; // beats go out from beatTick()
            }
            double bpm = lead.effectiveBpm;
            if (c.type().equals("ma2")) {
                // The grandMA2 command line takes no fractional values ("At 174.0" does nothing), so whole BPM only.
                bpm = Math.round(bpm);
            }
            long now = System.currentTimeMillis();
            boolean changed = lastBpm == null || Math.abs(bpm - lastBpm) >= 0.1;
            if (now < retryAt) {
                return; // the last attempt failed; do not hammer the desk
            }
            long keepAlive = c.type().equals("ma2") ? MA2_KEEPALIVE_MS : KEEPALIVE_MS;
            if (changed || now - lastSentAt >= keepAlive) {
                if (send(c, bpm)) {
                    lastBpm = bpm;
                    lastSentAt = now;
                } else {
                    retryAt = now + 5000;
                }
            }
        } catch (RuntimeException e) {
            error = e.getMessage();
        }
    }

    private boolean send(Config c, double bpm) {
        String text = c.type().equals("ma2") ? Long.toString(Math.round(bpm)) : String.format(Locale.ROOT, "%.1f", bpm);
        try {
            if (c.type().equals("ma2")) {
                telnet.send(c.host(), c.port(), c.user(), c.password(), c.command().replace("{bpm}", text));
            } else {
                try (DatagramSocket socket = new DatagramSocket()) {
                    byte[] packet = c.type().equals("ma")
                            ? osc("/cmd", c.command().replace("{bpm}", text))
                            : osc(c.address(), (float) bpm);
                    socket.send(new DatagramPacket(packet, packet.length, InetAddress.getByName(c.host()), c.port()));
                }
            }
            sentCount++;
            String refused = c.type().equals("ma2") ? telnet.deskError() : null;
            error = refused == null ? null : "The desk refused the command: " + refused
                    + (refused.contains("RIGHTS") ? " (give the user more rights on the desk, e.g. Admin)" : "");
            return true;
        } catch (IOException e) {
            error = "Sending to " + c.host() + ":" + c.port() + " failed: " + e.getMessage();
            return false;
        }
    }

    private static byte[] osc(String address, String value) {
        byte[] a = padded(address);
        byte[] t = padded(",s");
        byte[] v = padded(value);
        return ByteBuffer.allocate(a.length + t.length + v.length).put(a).put(t).put(v).array();
    }

    private static byte[] osc(String address, float value) {
        byte[] a = padded(address);
        byte[] t = padded(",f");
        return ByteBuffer.allocate(a.length + t.length + 4).put(a).put(t).putFloat(value).array();
    }

    /** OSC strings are NUL-terminated and padded to a multiple of 4 bytes. */
    private static byte[] padded(String s) {
        byte[] raw = s.getBytes(StandardCharsets.UTF_8);
        int len = (raw.length / 4 + 1) * 4;
        byte[] out = new byte[len];
        System.arraycopy(raw, 0, out, 0, raw.length);
        return out;
    }
}
