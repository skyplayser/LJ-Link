package dev.ljlinkpro;

import org.deepsymmetry.beatlink.Beat;
import org.deepsymmetry.beatlink.BeatFinder;
import org.deepsymmetry.beatlink.CdjStatus;
import org.deepsymmetry.beatlink.DeviceAnnouncement;
import org.deepsymmetry.beatlink.DeviceAnnouncementListener;
import org.deepsymmetry.beatlink.DeviceFinder;
import org.deepsymmetry.beatlink.DeviceUpdate;
import org.deepsymmetry.beatlink.LifecycleListener;
import org.deepsymmetry.beatlink.LifecycleParticipant;
import org.deepsymmetry.beatlink.VirtualCdj;
import org.deepsymmetry.beatlink.data.ArtFinder;
import org.deepsymmetry.beatlink.data.BeatGridFinder;
import org.deepsymmetry.beatlink.data.CrateDigger;
import org.deepsymmetry.beatlink.data.MetadataFinder;
import org.deepsymmetry.beatlink.data.TimeFinder;
import org.deepsymmetry.beatlink.data.TrackMetadata;
import org.deepsymmetry.beatlink.data.WaveformFinder;
import org.deepsymmetry.beatlink.data.WaveformListener;
import org.deepsymmetry.beatlink.data.WaveformDetailUpdate;
import org.deepsymmetry.beatlink.data.WaveformPreviewUpdate;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Passive Pro DJ Link source built on beat-link. First only listens for device announcements
 * (so searching never blocks), then joins as a virtual CDJ once something was found.
 * Sends no sync, master, tempo or fader commands.
 */
final class ProDjLinkSource implements DeckSource {
    private static final int NORMAL_PITCH = 0x100000;

    private final Hub.Scope hub;
    private volatile boolean stopped;
    private Thread thread;
    private ScheduledExecutorService positions;
    private boolean virtualCdjStarted;
    private boolean beatListenerAdded;
    /** Unexpected shutdowns of the virtual CDJ in a row, and the last reason, for the automatic rejoin and the diagnostics. */
    private volatile int lostInARow;
    private volatile int lostTotal;
    private volatile String lostReason;
    private volatile long joinedAt;

    /** beat-link shuts the virtual CDJ down when another device defends our player number; then we rejoin by ourselves. */
    private final LifecycleListener virtualCdjLifecycle = new LifecycleListener() {
        @Override
        public void started(LifecycleParticipant sender) {
        }

        @Override
        public void stopped(LifecycleParticipant sender) {
            if (!stopped) {
                onVirtualCdjLost();
            }
        }
    };

    private final DeviceAnnouncementListener announcementListener = new DeviceAnnouncementListener() {
        @Override
        public void deviceFound(DeviceAnnouncement a) {
            publishDevices();
        }

        @Override
        public void deviceLost(DeviceAnnouncement a) {
            if (a.getDeviceNumber() >= 1 && a.getDeviceNumber() <= 6) {
                hub.removeDeck(a.getDeviceNumber()); // the player was switched off or unplugged
                forgetPlayer(a.getDeviceNumber());
            }
            publishDevices();
        }
    };

    /** The latest status of every player, for the diagnostics. */
    final java.util.Map<Integer, CdjStatus> lastStatus = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<Integer, String> trackKeys = new java.util.concurrent.ConcurrentHashMap<>();
    /** The latest beat packet of every player, for the diagnostics (its tempo is a second, independent reading). */
    private final java.util.Map<Integer, Beat> lastBeat = new java.util.concurrent.ConcurrentHashMap<>();
    /** The effective tempo of the status packets of the last {@link TempoTrace#WINDOW_NANOS} per player. */
    private final java.util.Map<Integer, TempoTrace> statusTempo = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<Integer, TempoTrace> beatTempo = new java.util.concurrent.ConcurrentHashMap<>();
    /** LJLINKPRO_DEBUG=1 records the tempo readings of every player once per second, in a buffer of their own. */
    private static final boolean DEBUG = System.getenv("LJLINKPRO_DEBUG") != null;
    private boolean debugLogged;
    private long lastDebugNanos;
    private final TempoDebug tempoDebug = new TempoDebug();

    ProDjLinkSource(Hub.Scope hub) {
        this.hub = hub;
        hub.setSource(this);
    }

    @Override
    public void start() {
        thread = new Thread(this::run, "prodjlink-search");
        thread.setDaemon(true);
        thread.start();
    }

    private void publishDevices() {
        if (stopped) {
            return;
        }
        try {
            List<Hub.DeviceInfo> list = DeviceFinder.getInstance().getCurrentDevices().stream()
                    .sorted(Comparator.comparingInt(DeviceAnnouncement::getDeviceNumber))
                    .map(a -> new Hub.DeviceInfo(a.getDeviceNumber(), a.getDeviceName(), ip(a.getAddress())))
                    .toList();
            hub.setDevices(list);
        } catch (IllegalStateException e) {
            // DeviceFinder was stopped while an announcement was still being delivered.
        }
    }

    private void run() {
        try {
            hub.setState("searching", "live", "Searching for Pioneer CDJs (Pro DJ Link) …");
            DeviceFinder finder = DeviceFinder.getInstance();
            finder.addDeviceAnnouncementListener(announcementListener);
            finder.start();

            while (!stopped && finder.getCurrentDevices().isEmpty()) {
                Thread.sleep(500);
            }
            if (stopped) {
                return;
            }
            // Give the remaining devices a moment to announce themselves before we join the network.
            hub.setMessage("Device found – connecting …");
            Thread.sleep(2500);
            if (stopped) {
                return;
            }

            VirtualCdj vcdj = VirtualCdj.getInstance();
            vcdj.setUseStandardPlayerNumber(false);
            vcdj.addUpdateListener(this::onUpdate);
            vcdj.addLifecycleListener(virtualCdjLifecycle);
            MetadataFinder.getInstance().addTrackMetadataListener(u -> onMetadata(u.player, u.metadata));
            ArtFinder.getInstance().addAlbumArtListener(u -> {
                java.nio.ByteBuffer raw = u.art == null ? null : u.art.getRawBytes();
                byte[] bytes = null;
                if (raw != null) {
                    bytes = new byte[raw.remaining()];
                    raw.get(bytes);
                }
                hub.deck(u.player).setArt(bytes);
            });
            BeatGridFinder.getInstance().addBeatGridListener(u -> {
                var grid = u.beatGrid;
                if (grid == null) {
                    hub.deck(u.player).setBeats(null, null);
                    return;
                }
                int[] times = new int[grid.beatCount];
                byte[] bars = new byte[grid.beatCount];
                for (int i = 0; i < grid.beatCount; i++) {
                    times[i] = (int) grid.getTimeWithinTrack(i + 1);
                    bars[i] = (byte) grid.getBeatWithinBar(i + 1);
                }
                hub.deck(u.player).setBeats(times, bars);
            });
            WaveformFinder wf = WaveformFinder.getInstance();
            wf.setFindDetails(true);
            wf.setPreferredStyle(WaveformFinder.WaveformStyle.RGB);
            wf.addWaveformListener(new WaveformListener() {
                @Override
                public void previewChanged(WaveformPreviewUpdate u) {
                    Deck dk = hub.deck(u.player);
                    if (u.preview != null) {
                        dk.previewStyle = WaveformEncoder.styleName(u.preview.style);
                    }
                    dk.setWaveform(u.preview == null ? null : WaveformEncoder.encodePreview(u.preview));
                }

                @Override
                public void detailChanged(WaveformDetailUpdate u) {
                    Deck dk = hub.deck(u.player);
                    if (u.detail != null) {
                        dk.detailStyle = WaveformEncoder.styleName(u.detail.style);
                    }
                    dk.setDetail(u.detail == null ? null : WaveformEncoder.encodeDetail(u.detail));
                }
            });
            String note = join();
            if (note == null) {
                return;
            }

            positions = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "prodjlink-positions");
                t.setDaemon(true);
                return t;
            });
            positions.scheduleAtFixedRate(this::pollPositions, 20, 20, TimeUnit.MILLISECONDS);

            if (lostInARow == 0) {
                hub.setState("monitoring", "live", note);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (!stopped) {
                System.err.println("Pro DJ Link failed: " + e);
                boolean portTaken = e instanceof java.net.BindException
                        || String.valueOf(e.getMessage()).toLowerCase(java.util.Locale.ROOT).contains("address already in use");
                hub.setState("error", "live", portTaken
                        ? "The Pioneer search could not start: another program on this computer uses Pro DJ Link (rekordbox, Beat Link Trigger …). Close it and search again."
                        : "Pro DJ Link: " + e.getMessage());
            }
        }
    }

    /**
     * Starts the virtual CDJ and the finders; the listeners were registered once before. Returns the status note,
     * or null when joining failed (the error is already shown).
     */
    private String join() throws Exception {
        if (!VirtualCdj.getInstance().start()) {
            hub.setState("error", "live", "Connection to the DJ network failed. Please try again.");
            return null;
        }
        virtualCdjStarted = true;
        joinedAt = System.currentTimeMillis();
        MetadataFinder.getInstance().start();
        CrateDigger.getInstance().start();
        ArtFinder.getInstance().start();
        BeatGridFinder.getInstance().start();
        WaveformFinder.getInstance().start();
        try {
            TimeFinder.getInstance().start();
            if (!beatListenerAdded) {
                BeatFinder.getInstance().addBeatListener(this::onBeat);
                beatListenerAdded = true;
            }
        } catch (Exception e) {
            // Usually UDP port 50001 is taken (another program listens for beats). Positions then come from the
            // beat grid and the beat number in the player status, which is coarser but keeps everything else working.
            System.err.println("Beat packets unavailable, positions are estimated: " + e);
            return "Beat port 50001 is in use by another program; positions are estimated.";
        }
        return "Connected";
    }

    /** The virtual CDJ was shut down by beat-link (another device defended our player number): explain it and rejoin. */
    private void onVirtualCdjLost() {
        // A connection that held for a minute counts as healthy again; only quick repeats are a conflict.
        if (System.currentTimeMillis() - joinedAt > 60_000) {
            lostInARow = 0;
        }
        lostInARow++;
        lostTotal++;
        String local = localBeatLinkDevice();
        lostReason = local != null
                ? "another Pro DJ Link program on this computer (" + local + ")"
                : "another device claimed LJ Link's player number";
        System.err.println("Virtual CDJ was shut down (" + lostInARow + " in a row): " + lostReason);
        if (lostInARow > 3) {
            hub.setState("error", "live", local != null
                    ? "Another program on this computer keeps taking over the Pioneer connection (a second LJ Link, Beat Link Trigger, rekordbox …). Close it and search again."
                    : "Another device on the network keeps taking LJ Link's player number. Check that no second LJ Link or Beat Link program is running, then search again.");
            return;
        }
        hub.setState("searching", "live", local != null
                ? "Another program on this computer uses Pro DJ Link (a second LJ Link, Beat Link Trigger, rekordbox …). Reconnecting …"
                : "The connection to the players was taken over. Reconnecting …");
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(3000L * lostInARow);
                if (stopped || VirtualCdj.getInstance().isRunning()) {
                    return;
                }
                String note = join();
                if (note != null && !stopped) {
                    hub.setState("monitoring", "live", note);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                if (!stopped) {
                    hub.setState("error", "live", "Pro DJ Link: " + e.getMessage());
                }
            }
        }, "prodjlink-rejoin");
        t.setDaemon(true);
        t.start();
    }

    /** A device announcing itself from one of this computer's own addresses (another beat-link program), or null. */
    private static String localBeatLinkDevice() {
        try {
            java.util.Set<String> own = new java.util.HashSet<>();
            for (var ni : java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())) {
                for (var ia : ni.getInterfaceAddresses()) {
                    own.add(ip(ia.getAddress()));
                }
            }
            for (DeviceAnnouncement a : DeviceFinder.getInstance().getCurrentDevices()) {
                if (own.contains(ip(a.getAddress()))) {
                    return "\"" + a.getDeviceName() + "\" as player " + a.getDeviceNumber() + " at " + ip(a.getAddress());
                }
            }
        } catch (Exception e) {
            // Only used for the explanation.
        }
        return null;
    }

    /** The plain IPv4 text of an address, also when the system hands it over as IPv4-mapped IPv6 (::ffff:a.b.c.d). */
    static String ip(java.net.InetAddress a) {
        byte[] b = a.getAddress();
        boolean mapped = b.length == 16 && (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff;
        for (int i = 0; mapped && i < 10; i++) {
            mapped = b[i] == 0;
        }
        return mapped ? (b[12] & 0xff) + "." + (b[13] & 0xff) + "." + (b[14] & 0xff) + "." + (b[15] & 0xff) : a.getHostAddress();
    }

    private void onUpdate(DeviceUpdate update) {
        if (update instanceof org.deepsymmetry.beatlink.MixerStatus m) {
            hub.setMixer(java.util.Map.of("present", true, "name", m.getDeviceName(), "number", m.getDeviceNumber(),
                    "address", ip(m.getAddress()), "bpm", m.getBpm() / 100.0, "master", m.isTempoMaster(),
                    "simulated", false, "levels", false));
            return;
        }
        if (!(update instanceof CdjStatus s)) {
            return;
        }
        Deck d = hub.deck(s.getDeviceNumber());
        d.name = s.getDeviceName();
        boolean loaded = s.getTrackType() != CdjStatus.TrackType.NO_TRACK;
        // Tracks without rekordbox analysis (rekordbox id 0: unanalysed files, CDs, some streaming sources) are loaded
        // too, they just have no metadata, waveform or beat grid. They used to show up as "No track loaded".
        boolean analysed = loaded && s.getRekordboxId() != 0;
        if (!loaded && d.hasTrack) {
            d.clearTrack();
        }
        d.hasTrack = loaded;
        d.noWaveformSource = loaded && !analysed;
        String trackKey = s.getTrackType() + "/" + s.getTrackSourceSlot() + "/" + s.getTrackSourcePlayer() + "/"
                + s.getRekordboxId() + "/" + s.getTrackNumber();
        boolean otherTrack = loaded && !trackKey.equals(trackKeys.put(s.getDeviceNumber(), trackKey));
        if (otherTrack && !analysed) {
            d.title = null; // another unanalysed track: the title of the previous one must not stay
        }
        if (loaded && !analysed && (d.title == null || d.title.isBlank())) {
            d.title = "Track without rekordbox analysis (" + s.getTrackType() + ", " + s.getTrackSourceSlot() + ")";
        }
        lastStatus.put(s.getDeviceNumber(), s);
        d.playing = s.isPlaying();
        d.onAir = s.isOnAir();
        d.master = s.isTempoMaster();
        d.synced = s.isSynced();
        // An empty player reports the placeholder 655.35 BPM; show nothing instead of that.
        boolean bpmKnown = loaded && s.getBpm() > 0 && s.getBpm() != 0xFFFF;
        d.effectiveBpm = bpmKnown ? s.getEffectiveTempo() : 0;
        if (otherTrack) {
            d.trackVersion++; // after the tempo, so the steadied tempo takes the new one at once (Deck.stableBpm)
        }
        if (bpmKnown) {
            statusTempo.computeIfAbsent(s.getDeviceNumber(), k -> new TempoTrace()).add(System.nanoTime(), d.effectiveBpm);
        }
        d.speed = s.getPitch() / (double) NORMAL_PITCH;
        // Only the CDJ-3000 sends its exact position several times per second; older players are interpolated between beats.
        d.coarsePosition = d.name == null || !d.name.contains("3000");
        d.pitchPercent = loaded ? (s.getPitch() - NORMAL_PITCH) * 100.0 / NORMAL_PITCH : 0;
        d.trackNumber = s.getTrackNumber();
        d.loopBeats = s.isLooping() ? s.getActiveLoopBeats() : 0;
        d.loop = s.isLooping() && s.getLoopEnd() > s.getLoopStart() ? new long[] {s.getLoopStart(), s.getLoopEnd()} : null;
    }

    /** A player sent a beat packet: it is exactly on the beat, so the light desk outputs can take their beats from it. */
    private void onBeat(Beat b) {
        int player = b.getDeviceNumber();
        if (player < 1 || player > 6) {
            return; // mixers send beat packets too
        }
        Deck d = hub.deck(player);
        d.beatPacketInBar = b.isBeatWithinBarMeaningful() ? b.getBeatWithinBar() : 0;
        d.beatPacketNanos = System.nanoTime();
        d.beatPacketCount++;
        lastBeat.put(player, b);
        if (b.getBpm() > 0 && b.getBpm() != 0xFFFF) {
            beatTempo.computeIfAbsent(player, k -> new TempoTrace()).add(System.nanoTime(), b.getEffectiveTempo());
        }
    }

    private void onMetadata(int player, TrackMetadata md) {
        Deck d = hub.deck(player);
        if (md == null) {
            d.clearTrack();
            return;
        }
        d.title = md.getTitle();
        d.artist = md.getArtist() == null ? null : md.getArtist().label;
        d.key = md.getKey() == null ? null : md.getKey().label;
        d.trackBpm = md.getTempo() / 100.0;
        d.durationMs = md.getDuration() * 1000L;
        d.cues = cuesOf(md);
    }

    /** Hot cues (A-H, hot &gt; 0) and memory points (hot == 0) with their time in ms and an optional color. */
    private static List<java.util.Map<String, Object>> cuesOf(TrackMetadata md) {
        List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
        if (md.getCueList() == null) {
            return out;
        }
        for (var e : md.getCueList().entries) {
            java.awt.Color c = e.embeddedColor != null ? e.embeddedColor : e.rekordboxColor;
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("t", e.cueTime);
            m.put("hot", e.hotCueNumber);
            m.put("loop", e.isLoop);
            m.put("loopEnd", e.isLoop ? e.loopTime : 0);
            m.put("color", c == null ? null : String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue()));
            out.add(m);
        }
        return out;
    }

    /** Paused players send no moving position; the last reported beat number and the beat grid still give it. */
    private static long positionFromBeatGrid(int player) {
        DeviceUpdate u = VirtualCdj.getInstance().getLatestStatusFor(player);

        if (u instanceof CdjStatus s && BeatGridFinder.getInstance().isRunning()) {
            var grid = BeatGridFinder.getInstance().getLatestBeatGridFor(player);
            int beat = s.getBeatNumber();
            if (grid != null && beat > 0 && beat <= grid.beatCount) {
                return grid.getTimeWithinTrack(beat);
            }
        }
        return -1;
    }

    private void pollPositions() {
        try {
            for (DeviceAnnouncement a : DeviceFinder.getInstance().getCurrentDevices()) {
                int n = a.getDeviceNumber();
                if (n < 1 || n > 6) {
                    continue; // mixers and other non-player devices
                }
                Deck d = hub.deck(n);
                d.address = ip(a.getAddress());
                if (d.name.isEmpty()) {
                    d.name = a.getDeviceName();
                }
                long t = TimeFinder.getInstance().isRunning() ? TimeFinder.getInstance().getTimeFor(n) : -1;
                if (t < 0) {
                    t = positionFromBeatGrid(n);
                }
                d.samplePosition(t);
            }
            long now = System.nanoTime();
            if (DEBUG && (!debugLogged || now - lastDebugNanos >= 1_000_000_000L)) {
                debugLogged = true;
                lastDebugNanos = now;
                tempoDebug.add(tempoLines());
            }
        } catch (RuntimeException e) {
            // Position polling must never take the monitor down.

        }
    }

    @Override
    public java.util.List<java.util.Map<String, Object>> info(int deck) {
        return DeckInspector.sections(deck);
    }

    @Override
    public void stop() {
        stopped = true;
        stopQuietly(() -> VirtualCdj.getInstance().removeLifecycleListener(virtualCdjLifecycle));
        if (thread != null) {
            thread.interrupt();
        }
        if (positions != null) {
            positions.shutdownNow();
        }
        stopQuietly(() -> TimeFinder.getInstance().stop());
        stopQuietly(() -> WaveformFinder.getInstance().stop());
        stopQuietly(() -> BeatGridFinder.getInstance().stop());
        stopQuietly(() -> ArtFinder.getInstance().stop());
        stopQuietly(() -> CrateDigger.getInstance().stop());
        stopQuietly(() -> MetadataFinder.getInstance().stop());
        if (virtualCdjStarted) {
            stopQuietly(() -> VirtualCdj.getInstance().stop());
        }
        stopQuietly(() -> DeviceFinder.getInstance().removeDeviceAnnouncementListener(announcementListener));
        stopQuietly(() -> DeviceFinder.getInstance().stop());
    }

    private static void stopQuietly(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            System.err.println("Stop step failed: " + e);
        }
    }

    @Override
    public java.util.List<String> diagnostics() {
        java.util.List<String> out = new java.util.ArrayList<>();
        try {
            VirtualCdj v = VirtualCdj.getInstance();
            out.add("VirtualCdj running: " + v.isRunning() + (v.isRunning() ? ", own player number " + v.getDeviceNumber()
                    + " (standard numbers 1-4 " + (v.getUseStandardPlayerNumber() ? "used" : "not used") + ")"
                    + ", address " + v.getLocalAddress() + ", interfaces " + v.getMatchingInterfaces() : ""));
        } catch (RuntimeException e) {
            out.add("VirtualCdj: " + e);
        }
        if (lostTotal > 0) {
            out.add("WARNING: the virtual CDJ was shut down " + lostTotal + " times, last because of " + lostReason);
        }
        out.add("Finders running: DeviceFinder " + DeviceFinder.getInstance().isRunning()
                + ", MetadataFinder " + MetadataFinder.getInstance().isRunning()
                + (MetadataFinder.getInstance().isPassive() ? " (passive)" : "")
                + ", CrateDigger " + CrateDigger.getInstance().isRunning()
                + ", mounted media " + mountedMedia()
                + ", WaveformFinder " + WaveformFinder.getInstance().isRunning()
                + ", BeatGridFinder " + BeatGridFinder.getInstance().isRunning()
                + ", TimeFinder " + TimeFinder.getInstance().isRunning());
        try {
            java.util.Map<Integer, Integer> perNumber = new java.util.HashMap<>();
            for (DeviceAnnouncement a : DeviceFinder.getInstance().getCurrentDevices()) {
                perNumber.merge(a.getDeviceNumber(), 1, Integer::sum);
                out.add("Device " + a.getDeviceNumber() + ": " + a.getDeviceName() + " at " + ip(a.getAddress())
                        + ", peers " + a.getPeerCount());
            }
            perNumber.forEach((n, c) -> {
                if (c > 1) {
                    out.add("WARNING: player number " + n + " is announced " + c + " times (same number twice, or one player on two addresses)");
                }
            });
        } catch (RuntimeException e) {
            out.add("Devices: " + e);
        }
        for (CdjStatus s : lastStatus.values()) {
            out.add("Player " + s.getDeviceNumber() + " status: firmware " + s.getFirmwareVersion() + ", track type " + s.getTrackType()
                    + ", source slot " + s.getTrackSourceSlot() + " of player " + s.getTrackSourcePlayer()
                    + ", rekordbox id " + (s.getRekordboxId() != 0 ? "set" : "0 (no analysis)")
                    + ", USB " + s.isLocalUsbLoaded() + ", SD " + s.isLocalSdLoaded() + ", playing " + s.isPlaying()
                    + ", on air " + s.isOnAir() + ", master " + s.isTempoMaster());
        }
        out.addAll(tempoLines());
        if (DEBUG) {
            out.addAll(tempoDebug.lines());
        }
        return out;
    }

    /** The player left the network: its readings go, so the diagnostics do not list it any more. */
    void forgetPlayer(int player) {
        lastStatus.remove(player);
        lastBeat.remove(player);
        statusTempo.remove(player);
        beatTempo.remove(player);
        trackKeys.remove(player);
    }

    /**
     * Per player every tempo reading LJ Link has: the track BPM and the four pitch copies of the status packet, the effective
     * tempo of the status and of the beat packets (now and the range of the last 10 s), sync and master, and the steadied
     * tempo the outputs send. Makes jitter on the rounding edge (137.95) and display differences (137.9 against 140.0) visible.
     */
    java.util.List<String> tempoLines() {
        java.util.List<String> out = new java.util.ArrayList<>();
        long now = System.nanoTime();
        java.util.List<Integer> players = new java.util.ArrayList<>(lastStatus.keySet());
        java.util.Collections.sort(players);
        for (int n : players) {
            CdjStatus s = lastStatus.get(n);
            StringBuilder b = new StringBuilder("Player " + n + " tempo: track BPM ");
            b.append(s.getBpm() == 0xFFFF ? "none" : fmt(s.getBpm() / 100.0, 2));
            b.append(", pitch 1-4 ");
            for (int i = 1; i <= 4; i++) {
                b.append(i > 1 ? "/" : "").append(fmt(org.deepsymmetry.beatlink.Util.pitchToPercentage(s.getPitch(i)), 3));
            }
            b.append(" %, status ").append(fmt(s.getEffectiveTempo(), 3));
            TempoTrace st = statusTempo.get(n);
            if (st != null) {
                b.append(" ").append(st.summary(now));
            }
            Beat beat = lastBeat.get(n);
            if (beat != null) {
                b.append(", beat packet BPM ").append(fmt(beat.getBpm() / 100.0, 2)).append(" pitch ")
                        .append(fmt(org.deepsymmetry.beatlink.Util.pitchToPercentage(beat.getPitch()), 3)).append(" % = ")
                        .append(fmt(beat.getEffectiveTempo(), 3));
                TempoTrace bt = beatTempo.get(n);
                if (bt != null) {
                    b.append(" ").append(bt.summary(now));
                }
                Deck withBeat = hub.existingDeck(n);
                if (withBeat != null) {
                    b.append(" (").append((now - withBeat.beatPacketNanos) / 1_000_000).append(" ms ago)");
                }
            } else {
                b.append(", no beat packets");
            }
            Deck d = hub.existingDeck(n); // only look: asking hub.deck() would bring back the deck of a player that left
            b.append(", synced ").append(s.isSynced()).append(", BPM-only sync ").append(s.isBpmOnlySynced())
                    .append(", master ").append(s.isTempoMaster());
            if (d == null) {
                b.append(", no deck");
            } else {
                b.append(", sent tempo ").append(fmt(d.sentBpm(), 1)).append(" (").append(d.stableChanges()).append(" changes)")
                        .append(d.sendingBpm ? ", leads the light desk" : "").append(d.sendingLink ? ", leads Ableton Link" : "");
            }
            out.add(b.toString());
        }
        return out;
    }

    private static String mountedMedia() {
        try {
            return MetadataFinder.getInstance().isRunning() ? String.valueOf(MetadataFinder.getInstance().getMountedMediaSlots()) : "-";
        } catch (RuntimeException e) {
            return "?";
        }
    }

    private static String fmt(double v, int digits) {
        return String.format(java.util.Locale.ROOT, "%." + digits + "f", v);
    }

    /**
     * The tempo lines of the debug mode, in a buffer of their own: written to the log once per second they pushed the
     * start and error messages out of the diagnostics within minutes.
     */
    static final class TempoDebug {
        static final int MAX_LINES = 300;
        private final java.util.ArrayDeque<String> lines = new java.util.ArrayDeque<>();
        private static final java.time.format.DateTimeFormatter TIME = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss");

        synchronized void add(java.util.List<String> batch) {
            String time = java.time.LocalTime.now().format(TIME);
            for (String line : batch) {
                lines.addLast(time + " " + line);
                while (lines.size() > MAX_LINES) {
                    lines.removeFirst();
                }
            }
        }

        synchronized java.util.List<String> lines() {
            java.util.List<String> out = new java.util.ArrayList<>();
            out.add("Tempo debug (LJLINKPRO_DEBUG, once per second, last " + lines.size() + " of at most " + MAX_LINES + " lines):");
            out.addAll(lines);
            return out;
        }
    }

    /** The readings of a tempo over the last 10 s: minimum, maximum and count. */
    static final class TempoTrace {
        static final long WINDOW_NANOS = 10_000_000_000L;
        private final java.util.ArrayDeque<double[]> samples = new java.util.ArrayDeque<>();

        synchronized void add(long nowNanos, double bpm) {
            samples.addLast(new double[] {nowNanos, bpm});
            trim(nowNanos);
        }

        private void trim(long nowNanos) {
            while (!samples.isEmpty() && nowNanos - (long) samples.peekFirst()[0] > WINDOW_NANOS) {
                samples.removeFirst();
            }
        }

        synchronized String summary(long nowNanos) {
            trim(nowNanos);
            if (samples.isEmpty()) {
                return "[none in 10 s]";
            }
            double min = Double.MAX_VALUE;
            double max = -Double.MAX_VALUE;
            for (double[] x : samples) {
                min = Math.min(min, x[1]);
                max = Math.max(max, x[1]);
            }
            return "[10 s: " + fmt(min, 3) + "-" + fmt(max, 3) + ", " + samples.size() + " readings]";
        }
    }
}
