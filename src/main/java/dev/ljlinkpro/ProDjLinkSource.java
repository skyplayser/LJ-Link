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
    private volatile ScheduledExecutorService positions;
    private boolean virtualCdjStarted;
    private boolean beatListenerAdded;
    /** Unexpected shutdowns of the virtual CDJ in a row, and the last reason, for the automatic rejoin and the diagnostics. */
    private volatile int lostInARow;
    private volatile int lostTotal;
    private volatile String lostReason;
    private volatile long joinedAt;

    /**
     * Player numbers LJ Link gave up because a real device announced the same number while LJ Link had it (issue #2: an
     * XDJ-AZ announces itself as 1-4, 7 and 33). beat-link would "defend" the number against that device; LJ Link steps
     * aside instead and joins again with another number. At most {@link #MAX_NUMBER_CHANGES} times per search, in case
     * a device copies every number it sees (then LJ Link keeps its last number and says so in the diagnostics).
     */
    private final java.util.Set<Integer> conflictNumbers = java.util.concurrent.ConcurrentHashMap.newKeySet();
    static final int MAX_NUMBER_CHANGES = 3;
    private volatile int numberChanges;
    private volatile String numberConflict;
    private final java.util.concurrent.atomic.AtomicBoolean changingNumber = new java.util.concurrent.atomic.AtomicBoolean();
    /** Stops of the virtual CDJ that LJ Link made itself (to change its number): not a lost connection. */
    private final java.util.concurrent.atomic.AtomicInteger ownStops = new java.util.concurrent.atomic.AtomicInteger();

    /** beat-link shuts the virtual CDJ down when another device defends our player number; then we rejoin by ourselves. */
    private final LifecycleListener virtualCdjLifecycle = new LifecycleListener() {
        @Override
        public void started(LifecycleParticipant sender) {
        }

        @Override
        public void stopped(LifecycleParticipant sender) {
            if (ownStops.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                return; // LJ Link stopped it to take another number
            }
            if (!stopped) {
                onVirtualCdjLost();
            }
        }
    };

    private final DeviceAnnouncementListener announcementListener = new DeviceAnnouncementListener() {
        @Override
        public void deviceFound(DeviceAnnouncement a) {
            noteDevice(a.getDeviceNumber(), a.getDeviceName(), ip(a.getAddress()), true);
            publishDevices();
            checkNumberConflict();
        }

        @Override
        public void deviceLost(DeviceAnnouncement a) {
            noteDevice(a.getDeviceNumber(), a.getDeviceName(), ip(a.getAddress()), false);
            if (a.getDeviceNumber() >= 1 && a.getDeviceNumber() <= 6) {
                hub.removeDeck(a.getDeviceNumber()); // the player was switched off or unplugged
                forgetPlayer(a.getDeviceNumber());
            }
            publishDevices();
        }
    };

    // The listeners on beat-link's singletons are fields, so stop() can remove them again: a stopped search must not
    // keep receiving updates (or bring back decks) after the next search started with a new source.
    private final org.deepsymmetry.beatlink.DeviceUpdateListener updateListener = this::onUpdate;
    private final org.deepsymmetry.beatlink.BeatListener beatListener = this::onBeat;
    private final org.deepsymmetry.beatlink.data.TrackMetadataListener metadataListener = u -> onMetadata(u.player, u.metadata);
    private final org.deepsymmetry.beatlink.data.AlbumArtListener artListener = this::onArt;
    private final org.deepsymmetry.beatlink.data.BeatGridListener beatGridListener = this::onBeatGrid;
    private final org.deepsymmetry.beatlink.data.AnalysisTagListener phraseListener = this::onPhrases;
    private final WaveformListener waveformListener = new WaveformListener() {
        @Override
        public void previewChanged(WaveformPreviewUpdate u) {
            if (stopped) {
                return;
            }
            Deck dk = hub.deck(u.player);
            if (u.preview != null) {
                dk.previewStyle = WaveformEncoder.styleName(u.preview.style);
            }
            dk.setWaveform(u.preview == null ? null : WaveformEncoder.encodePreview(u.preview));
        }

        @Override
        public void detailChanged(WaveformDetailUpdate u) {
            if (stopped) {
                return;
            }
            Deck dk = hub.deck(u.player);
            if (u.detail != null) {
                dk.detailStyle = WaveformEncoder.styleName(u.detail.style);
            }
            dk.setDetail(u.detail == null ? null : WaveformEncoder.encodeDetail(u.detail));
        }
    };
    /** Joining and stopping exclude each other: stop() waits for a join in progress, a join after stop() does nothing. */
    private final Object lifecycle = new Object();

    /** A device that announced itself since the search started, kept after it left: for the diagnostics only. */
    static final class SeenDevice {
        final int number;
        final String name;
        final String address;
        volatile boolean present = true;

        SeenDevice(int number, String name, String address) {
            this.number = number;
            this.name = name;
            this.address = address;
        }
    }

    /** Every device seen by its announcement (number@address), for the diagnostics. Shows a mixer that sends no status too. */
    private final java.util.Map<String, SeenDevice> seenDevices = new java.util.concurrent.ConcurrentHashMap<>();
    /** Packets received per device number and kind (CdjStatus, MixerStatus, Beat), for the diagnostics only. */
    private final java.util.Map<Integer, java.util.Map<String, java.util.concurrent.atomic.LongAdder>> packets =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** The latest status of every player, for the diagnostics. */
    final java.util.Map<Integer, CdjStatus> lastStatus = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<Integer, String> trackKeys = new java.util.concurrent.ConcurrentHashMap<>();
    /** Since when (ms) each player has its current track: for the hint when no metadata arrives. */
    private final java.util.Map<Integer, Long> trackSince = new java.util.concurrent.ConcurrentHashMap<>();
    /** The latest beat packet of every player, for the diagnostics (its tempo is a second, independent reading). */
    private final java.util.Map<Integer, Beat> lastBeat = new java.util.concurrent.ConcurrentHashMap<>();
    /** Per player: bridges short gaps of TimeFinder's position (see {@link PositionHold}). */
    private final java.util.Map<Integer, PositionHold> holds = new java.util.concurrent.ConcurrentHashMap<>();
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
            vcdj.addUpdateListener(updateListener);
            vcdj.addLifecycleListener(virtualCdjLifecycle);
            MetadataFinder.getInstance().addTrackMetadataListener(metadataListener);
            ArtFinder.getInstance().addAlbumArtListener(artListener);
            BeatGridFinder.getInstance().addBeatGridListener(beatGridListener);
            // Phrases (rekordbox phrase analysis, "PSSI" in the .EXT file) for the VJ-Link interface. Alpha: untested on real players.
            org.deepsymmetry.beatlink.data.AnalysisTagFinder.getInstance().addAnalysisTagListener(phraseListener, ".EXT", "PSSI");
            WaveformFinder wf = WaveformFinder.getInstance();
            wf.setFindDetails(true);
            wf.setPreferredStyle(WaveformFinder.WaveformStyle.RGB);
            wf.addWaveformListener(waveformListener);
            String note = join();
            if (note == null) {
                return;
            }

            // Under the lifecycle lock: a stop() between the join and here must not leave the executor running.
            synchronized (lifecycle) {
                if (stopped) {
                    return;
                }
                positions = Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "prodjlink-positions");
                    t.setDaemon(true);
                    return t;
                });
                positions.scheduleAtFixedRate(this::pollPositions, 20, 20, TimeUnit.MILLISECONDS);
                positions.scheduleAtFixedRate(() -> checkMixerData(listening()), 1, 1, TimeUnit.SECONDS);
                positions.scheduleAtFixedRate(this::checkNumberConflict, 1, 1, TimeUnit.SECONDS);
            }

            if (lostInARow == 0 && !stopped) {
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
        synchronized (lifecycle) {
            if (stopped) {
                return null;
            }
            return joinLocked();
        }
    }

    private String joinLocked() throws Exception {
        if (!startVirtualCdj()) {
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
            org.deepsymmetry.beatlink.data.AnalysisTagFinder.getInstance().start();
        } catch (Exception e) {
            System.err.println("Phrase analysis unavailable: " + e);
        }
        try {
            TimeFinder.getInstance().start();
            if (!beatListenerAdded) {
                BeatFinder.getInstance().addBeatListener(beatListener);
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

    /**
     * Starts the virtual CDJ with a player number LJ Link chose itself from the devices seen so far ({@link #chooseOwnNumber}).
     * When that number is refused (another device defends it), beat-link's own choice is tried once more.
     */
    private boolean startVirtualCdj() throws Exception {
        VirtualCdj v = VirtualCdj.getInstance();
        if (v.isRunning()) {
            return true;
        }
        // As beat-link before it chooses a number itself: every device has announced itself at least twice by now.
        long first;
        try {
            first = DeviceFinder.getInstance().getFirstDeviceTime();
        } catch (IllegalStateException e) {
            first = 0; // no device seen (yet): beat-link's start waits for one itself
        }
        long wait = first > 0 ? SEEN_BEFORE_CHOOSING_MS - (System.currentTimeMillis() - first) : 0;
        if (wait > 0) {
            Thread.sleep(wait);
        }
        int own = chooseOwnNumber(usedNumbers(), seenModels(), conflictNumbers);
        v.setDeviceNumber((byte) own);
        if (v.start()) {
            return true;
        }
        if (own == 0 || stopped) {
            return false;
        }
        System.err.println("Player number " + own + " was refused; letting beat-link choose a free number.");
        v.setDeviceNumber((byte) 0);
        return v.start();
    }

    /** How long devices are watched (since the first one) before LJ Link chooses its number, as beat-link does. */
    static final long SEEN_BEFORE_CHOOSING_MS = 4000;
    /** The first own player number LJ Link tries, if no device uses it (as beat-link: 7 to 15, 5 and 6 are CDJ-3000 decks). */
    static final int FIRST_OWN_NUMBER = 7;
    static final int LAST_OWN_NUMBER = 15;
    /**
     * Numbers a model is known to announce besides its decks, also when it was not seen announcing them yet. Assumption
     * from the diagnostics of issue #2 (not checked on hardware): the XDJ-AZ announces 7 as well.
     */
    static final java.util.Map<String, java.util.Set<Integer>> RESERVED_BY_MODEL = java.util.Map.of("XDJ-AZ", java.util.Set.of(7));

    /**
     * LJ Link's own player number: the first from {@link #FIRST_OWN_NUMBER} to {@link #LAST_OWN_NUMBER} that no device
     * announced (now or since the search started), that no seen model reserves and that LJ Link did not have to give up
     * already. 0 when all are taken: then beat-link chooses (and gives up if it finds none).
     */
    static int chooseOwnNumber(java.util.Collection<Integer> used, java.util.Collection<String> models,
            java.util.Collection<Integer> givenUp) {
        java.util.Set<Integer> taken = new java.util.HashSet<>(used);
        taken.addAll(givenUp);
        for (String model : models) {
            String m = model == null ? "" : model.trim();
            RESERVED_BY_MODEL.forEach((name, numbers) -> {
                if (m.equalsIgnoreCase(name)) {
                    taken.addAll(numbers);
                }
            });
        }
        for (int n = FIRST_OWN_NUMBER; n <= LAST_OWN_NUMBER; n++) {
            if (!taken.contains(n)) {
                return n;
            }
        }
        return 0;
    }

    /** Every number announced since the search started (also by devices that left again) and the current ones. */
    private java.util.Set<Integer> usedNumbers() {
        java.util.Set<Integer> out = new java.util.HashSet<>();
        for (SeenDevice d : seenDevices.values()) {
            out.add(d.number);
        }
        try {
            for (DeviceAnnouncement a : DeviceFinder.getInstance().getCurrentDevices()) {
                out.add(a.getDeviceNumber());
            }
        } catch (RuntimeException e) {
            // DeviceFinder stopped meanwhile: the seen devices are enough.
        }
        return out;
    }

    private java.util.Set<String> seenModels() {
        java.util.Set<String> out = new java.util.HashSet<>();
        for (SeenDevice d : seenDevices.values()) {
            out.add(d.name);
        }
        return out;
    }

    /** The first device (other than LJ Link) that announces LJ Link's own number, or null. */
    static DeviceAnnouncement numberTakenBy(int own, java.util.Collection<DeviceAnnouncement> devices) {
        if (own <= 0) {
            return null;
        }
        for (DeviceAnnouncement a : devices) {
            // DeviceFinder ignores LJ Link's own announcements, so every match is another device.
            if (a.getDeviceNumber() == own) {
                return a;
            }
        }
        return null;
    }

    /**
     * Called for every new device and once a second: when a real device announces LJ Link's number, LJ Link gives it up
     * and joins again with another one, instead of letting beat-link defend it against the device (issue #2).
     */
    void checkNumberConflict() {
        try {
            if (stopped || changingNumber.get()) {
                return;
            }
            VirtualCdj v = VirtualCdj.getInstance();
            if (!v.isRunning()) {
                return;
            }
            int own = v.getDeviceNumber();
            DeviceAnnouncement other = numberTakenBy(own, DeviceFinder.getInstance().getCurrentDevices());
            if (other == null) {
                return;
            }
            String who = "\"" + other.getDeviceName() + "\" at " + ip(other.getAddress());
            conflictNumbers.add(own);
            if (numberChanges >= MAX_NUMBER_CHANGES) {
                String kept = "player number " + own + " is also announced by " + who + "; kept after " + numberChanges
                        + " changes (the device seems to copy LJ Link's number)";
                if (!kept.equals(numberConflict)) {
                    numberConflict = kept;
                    System.err.println("Player number conflict: " + kept);
                }
                return;
            }
            if (!changingNumber.compareAndSet(false, true)) {
                return;
            }
            numberChanges++;
            numberConflict = "player number " + own + " was also announced by " + who + "; LJ Link changed its number ("
                    + numberChanges + (numberChanges == 1 ? " time)" : " times)");
            System.err.println("Player number " + own + " is also used by " + who + ": LJ Link gives it up and joins with another number.");
            Thread t = new Thread(this::changeNumber, "prodjlink-number");
            t.setDaemon(true);
            t.start();
        } catch (RuntimeException e) {
            // Runs on the position executor: an exception must not cancel the schedule.
        }
    }

    /** Leaves the network with the conflicting number and joins again; {@link #startVirtualCdj} avoids the given-up numbers. */
    private void changeNumber() {
        try {
            synchronized (lifecycle) {
                if (stopped) {
                    return;
                }
                // VirtualCdj.stop() is synchronized on the instance: holding it, beat-link cannot stop the virtual CDJ
                // between the check and our stop, so the count is only raised for a stop that really happens here.
                VirtualCdj v = VirtualCdj.getInstance();
                synchronized (v) {
                    if (!v.isRunning()) {
                        return;
                    }
                    ownStops.incrementAndGet();
                    v.stop();
                }
            }
            String note = join();
            if (note != null && !stopped && lostInARow == 0) {
                hub.setState("monitoring", "live", note);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (!stopped) {
                hub.setState("error", "live", "Pro DJ Link: " + e.getMessage());
            }
        } finally {
            changingNumber.set(false);
        }
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

    void onUpdate(DeviceUpdate update) {
        if (stopped) {
            return;
        }
        countPacket(update.getDeviceNumber(), update.getClass().getSimpleName());
        if (update instanceof org.deepsymmetry.beatlink.MixerStatus m) {
            mixerStatusSeen = true;
            hub.setMixer(java.util.Map.of("present", true, "name", m.getDeviceName().trim(), "number", m.getDeviceNumber(),
                    "address", ip(m.getAddress()), "bpm", m.getBpm() / 100.0, "master", m.isTempoMaster(),
                    "statusPackets", "ok", "simulated", false, "levels", false));
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
        long now = System.currentTimeMillis();
        if (otherTrack) {
            trackSince.put(s.getDeviceNumber(), now);
        }
        if (otherTrack && !analysed) {
            d.title = null; // another unanalysed track: the title of the previous one must not stay
        }
        if (loaded && !analysed && (d.title == null || d.title.isBlank())) {
            d.title = "Track without rekordbox analysis (" + s.getTrackType() + ", " + s.getTrackSourceSlot() + ")";
        }
        d.metaHint = metadataHint(d.name, analysed, d.title != null && !d.title.isBlank(),
                now - trackSince.getOrDefault(s.getDeviceNumber(), now));
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

    /**
     * Two copies of one beat packet arrive within this time when the computer is in the players' network twice (two
     * adapters in the same subnet, issue #2). Real beats are at least 100 ms apart (300 BPM at +100 %).
     */
    static final long DUPLICATE_BEAT_NANOS = 30_000_000L;

    /** A player sent a beat packet: it is exactly on the beat, so the light desk outputs can take their beats from it. */
    void onBeat(Beat b) {
        if (stopped) {
            return;
        }
        int player = b.getDeviceNumber();
        countPacket(player, "Beat");
        if (player < 1 || player > 6) {
            return; // mixers send beat packets too
        }
        Deck d = hub.deck(player);
        long now = System.nanoTime();
        Beat previous = lastBeat.get(player);
        if (isDuplicateBeat(previous, d.beatPacketNanos, b, now)) {
            countPacket(player, "Beat duplicate");
            return; // the same packet a second time: one beat, not two, for the light desk
        }
        d.beatPacketInBar = beatInBar(b, latestStatus(b));
        d.beatPacketNanos = now;
        d.beatPacketCount++;
        lastBeat.put(player, b);
        if (b.getBpm() > 0 && b.getBpm() != 0xFFFF) {
            beatTempo.computeIfAbsent(player, k -> new TempoTrace()).add(System.nanoTime(), b.getEffectiveTempo());
        }
    }

    /** The same beat from the same player again within {@link #DUPLICATE_BEAT_NANOS}: a copy, not a new beat. */
    static boolean isDuplicateBeat(Beat previous, long previousNanos, Beat b, long nowNanos) {
        return previous != null && previousNanos != 0 && nowNanos - previousNanos < DUPLICATE_BEAT_NANOS
                && previous.getDeviceNumber() == b.getDeviceNumber() && previous.getBeatWithinBar() == b.getBeatWithinBar()
                && previous.getAddress().equals(b.getAddress());
    }

    /** The latest status packet of the player that sent this beat, or null (none yet, or the virtual CDJ is not running). */
    private static DeviceUpdate latestStatus(Beat b) {
        try {
            VirtualCdj v = VirtualCdj.getInstance();
            return v.isRunning() ? v.getLatestStatusFor(b) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * The beat within the bar (1-4) of a beat packet, or 0 when it is not meaningful or unknown. Not
     * {@link Beat#isBeatWithinBarMeaningful()}: it throws a NullPointerException while no status packet of the player has
     * arrived, which flooded the log with an XDJ-AZ (issue #2). Without a status the beat in the bar counts as unknown.
     */
    static int beatInBar(Beat b, DeviceUpdate latestStatus) {
        if (latestStatus == null || !latestStatus.isBeatWithinBarMeaningful()) {
            return 0;
        }
        int inBar = b.getBeatWithinBar();
        return inBar >= 1 && inBar <= 4 ? inBar : 0;
    }

    private void onArt(org.deepsymmetry.beatlink.data.AlbumArtUpdate u) {
        if (stopped) {
            return;
        }
        java.nio.ByteBuffer raw = u.art == null ? null : u.art.getRawBytes();
        byte[] bytes = null;
        if (raw != null) {
            bytes = new byte[raw.remaining()];
            raw.get(bytes);
        }
        hub.deck(u.player).setArt(bytes);
    }

    private void onBeatGrid(org.deepsymmetry.beatlink.data.BeatGridUpdate u) {
        if (stopped) {
            return;
        }
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
    }

    /** Phrases (rekordbox phrase analysis, "PSSI" in the .EXT file) for the VJ-Link interface. Alpha: untested on real players. */
    private void onPhrases(org.deepsymmetry.beatlink.data.AnalysisTagUpdate u) {
        if (stopped) {
            return;
        }
        Phrases ph = null;
        if (u.taggedSection != null
                && u.taggedSection.body() instanceof org.deepsymmetry.cratedigger.pdb.RekordboxAnlz.SongStructureTag tag) {
            try {
                ph = Phrases.of(tag);
            } catch (RuntimeException e) {
                System.err.println("Phrases of player " + u.player + " not readable: " + e);
            }
        }
        hub.deck(u.player).phrases = ph;
    }

    private void onMetadata(int player, TrackMetadata md) {
        if (stopped) {
            return;
        }
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
                // A short -1 is bridged instead of switching to the coarser beat-grid position and back (ADR-024).
                double rate = !d.playing ? 0 : Math.abs(d.velocity) > 0.05 ? d.velocity : d.speed;
                t = holds.computeIfAbsent(n, k -> new PositionHold())
                        .next(t, System.nanoTime(), d.hasTrack, d.trackVersion, rate, () -> positionFromBeatGrid(n));
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
        // Waits for a join in progress, so nothing it starts afterwards stays online.
        synchronized (lifecycle) {
            if (positions != null) {
                positions.shutdownNow();
            }
            stopBeatLink();
        }
        hub.setNote(null);
    }

    /**
     * Removes this source's listeners and stops the finders, the virtual CDJ (also when its start was not confirmed yet)
     * and the DeviceFinder: afterwards LJ Link no longer takes part in the Pro DJ Link network.
     */
    private void stopBeatLink() {
        stopQuietly(() -> VirtualCdj.getInstance().removeUpdateListener(updateListener));
        stopQuietly(() -> BeatFinder.getInstance().removeBeatListener(beatListener));
        stopQuietly(() -> MetadataFinder.getInstance().removeTrackMetadataListener(metadataListener));
        stopQuietly(() -> ArtFinder.getInstance().removeAlbumArtListener(artListener));
        stopQuietly(() -> BeatGridFinder.getInstance().removeBeatGridListener(beatGridListener));
        stopQuietly(() -> org.deepsymmetry.beatlink.data.AnalysisTagFinder.getInstance().removeAnalysisTagListener(phraseListener, ".EXT", "PSSI"));
        stopQuietly(() -> WaveformFinder.getInstance().removeWaveformListener(waveformListener));
        stopQuietly(() -> TimeFinder.getInstance().stop());
        stopQuietly(() -> org.deepsymmetry.beatlink.data.AnalysisTagFinder.getInstance().stop());
        stopQuietly(() -> WaveformFinder.getInstance().stop());
        stopQuietly(() -> BeatGridFinder.getInstance().stop());
        stopQuietly(() -> ArtFinder.getInstance().stop());
        stopQuietly(() -> CrateDigger.getInstance().stop());
        stopQuietly(() -> MetadataFinder.getInstance().stop());
        stopQuietly(() -> BeatFinder.getInstance().stop());
        stopQuietly(() -> VirtualCdj.getInstance().stop());
        stopQuietly(() -> DeviceFinder.getInstance().removeDeviceAnnouncementListener(announcementListener));
        stopQuietly(() -> DeviceFinder.getInstance().stop());
    }

    /** True when none of beat-link's network parts is running any more (after stop(): LJ Link has left the network). */
    static boolean beatLinkStopped() {
        return !VirtualCdj.getInstance().isRunning() && !DeviceFinder.getInstance().isRunning() && !BeatFinder.getInstance().isRunning()
                && !MetadataFinder.getInstance().isRunning() && !CrateDigger.getInstance().isRunning()
                && !WaveformFinder.getInstance().isRunning() && !BeatGridFinder.getInstance().isRunning()
                && !ArtFinder.getInstance().isRunning() && !TimeFinder.getInstance().isRunning();
    }

    private static void stopQuietly(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            System.err.println("Stop step failed: " + e);
        }
    }

    @Override
    public java.util.List<String> summary() {
        return summary(listening(), Diagnostics.adapters());
    }

    java.util.List<String> summary(boolean listening, java.util.List<Diagnostics.Adapter> adapters) {
        java.util.List<String> out = new java.util.ArrayList<>();
        out.add(virtualCdjLine(true));
        java.util.List<String> players = new java.util.ArrayList<>();
        new java.util.TreeMap<>(lastStatus).forEach((n, st) -> {
            Deck pd = hub.existingDeck(n);
            players.add(n + " " + st.getDeviceName().trim() + (oneLibrary(st.getDeviceName()) ? " OneLibrary" : "")
                    + (pd != null && pd.metaHint != null ? " no metadata" : ""));
        });
        if (!players.isEmpty()) {
            out.add("Players: " + String.join(", ", players));
        }
        out.add("Metadata: MetadataFinder " + onOff(MetadataFinder.getInstance().isRunning())
                + (MetadataFinder.getInstance().isPassive() ? " (passive)" : "")
                + ", CrateDigger " + onOff(CrateDigger.getInstance().isRunning()) + ", media " + mountedMedia());
        if (lostTotal > 0) {
            out.add("WARNING: virtual CDJ shut down " + lostTotal + " times, last: " + lostReason);
        }
        if (numberConflict != null) {
            out.add("WARNING: " + numberConflict);
        }
        out.addAll(seenDeviceLines(ip -> Diagnostics.adapterFor(ip, adapters), listening, true));
        return out;
    }

    @Override
    public java.util.List<String> diagnostics() {
        java.util.List<String> out = new java.util.ArrayList<>();
        out.add(virtualCdjLine(false));
        if (lostTotal > 0) {
            out.add("WARNING: the virtual CDJ was shut down " + lostTotal + " times, last because of " + lostReason);
        }
        if (numberConflict != null) {
            out.add("WARNING: " + numberConflict + "; numbers given up: " + new java.util.TreeSet<>(conflictNumbers));
        }
        out.add("Finders running: DeviceFinder " + DeviceFinder.getInstance().isRunning()
                + ", MetadataFinder " + MetadataFinder.getInstance().isRunning()
                + (MetadataFinder.getInstance().isPassive() ? " (passive)" : "")
                + ", CrateDigger " + CrateDigger.getInstance().isRunning()
                + ", mounted media " + mountedMedia()
                + ", WaveformFinder " + WaveformFinder.getInstance().isRunning()
                + ", BeatGridFinder " + BeatGridFinder.getInstance().isRunning()
                + ", TimeFinder " + TimeFinder.getInstance().isRunning());
        java.util.List<Diagnostics.Adapter> adapters = Diagnostics.adapters();
        out.addAll(seenDeviceLines(ip -> Diagnostics.adapterFor(ip, adapters), listening()));
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
            Deck pd = hub.existingDeck(s.getDeviceNumber());
            out.add("Player " + s.getDeviceNumber() + " status: model " + s.getDeviceName().trim()
                    + (oneLibrary(s.getDeviceName()) ? " (OneLibrary, not readable with beat-link 8.0.0)" : "")
                    + ", metadata " + (pd == null ? "-" : pd.title != null && !pd.title.isBlank() ? "yes" : pd.metaHint != null ? "missing (" + pd.metaHint + ")" : "not yet")
                    + ", firmware " + s.getFirmwareVersion() + ", track type " + s.getTrackType()
                    + ", source slot " + s.getTrackSourceSlot() + " of player " + s.getTrackSourcePlayer()
                    + ", rekordbox id " + (s.getRekordboxId() != 0 ? "set" : "0 (no analysis)")
                    + ", USB " + s.isLocalUsbLoaded() + ", SD " + s.isLocalSdLoaded() + ", playing " + s.isPlaying()
                    + ", on air " + s.isOnAir() + ", master " + s.isTempoMaster());
        }
        out.addAll(crateDiggerProblems(LogBuffer.lines(), 8));
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
        trackSince.remove(player);
        holds.remove(player);
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

    /** LJ Link's own player number and the adapter beat-link chose (the one in the players' network); short for the summary. */
    private static String virtualCdjLine(boolean shortForm) {
        try {
            VirtualCdj v = VirtualCdj.getInstance();
            if (!v.isRunning()) {
                return "VirtualCdj running: false";
            }
            java.util.List<String> names = new java.util.ArrayList<>();
            for (java.net.NetworkInterface ni : v.getMatchingInterfaces()) {
                names.add(ni.getName());
            }
            if (shortForm) {
                return "Own player number " + v.getDeviceNumber() + (v.getUseStandardPlayerNumber() ? "" : " (1-4 not used)")
                        + ", adapter " + (names.isEmpty() ? "?" : String.join("+", names)) + " " + ip(v.getLocalAddress());
            }
            return "VirtualCdj running: true, own player number " + v.getDeviceNumber()
                    + " (standard numbers 1-4 " + (v.getUseStandardPlayerNumber() ? "used" : "not used") + ")"
                    + ", adapter " + (names.isEmpty() ? "?" : String.join("+", names))
                    + ", address " + ip(v.getLocalAddress()) + ", broadcast " + (v.getBroadcastAddress() == null ? "?" : ip(v.getBroadcastAddress()));
        } catch (RuntimeException e) {
            return "VirtualCdj: " + e;
        }
    }

    /** True once the virtual CDJ has listened for a few seconds: only then does "no packets" mean something. */
    private boolean listening() {
        try {
            return VirtualCdj.getInstance().isRunning() && System.currentTimeMillis() - joinedAt > 5000;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static String onOff(boolean on) {
        return on ? "on" : "off";
    }

    /** A device announced itself ({@code present}) or left; only recorded for the diagnostics. */
    void noteDevice(int number, String name, String address, boolean present) {
        SeenDevice d = seenDevices.computeIfAbsent(number + "@" + address, k -> new SeenDevice(number, name, address));
        d.present = present;
        if (isMixer(number, name)) {
            if (present) {
                mixerAnnounced(number, name, address);
            } else {
                mixerLeft(number, address);
            }
        }
        showKontrolNote();
    }

    /**
     * A mixer by its announcement (Alpha, untested with a DJM-A9): numbers 33-40 are the mixers' range, and Pioneer's mixers
     * are called "DJM-…". Mixer status packets are not needed for this; they only add tempo and master.
     */
    static boolean isMixer(int number, String name) {
        return number >= 33 && number <= 40 || name != null && name.trim().toUpperCase(java.util.Locale.ROOT).startsWith("DJM");
    }

    /** Players that keep their library in OneLibrary (exportLibrary.db), as listed by beat-link main; beat-link 8.0.0 cannot read it. */
    static final java.util.Set<String> ONE_LIBRARY_MODELS = java.util.Set.of("CDJ-3000X", "XDJ-AZ", "OPUS-QUAD");

    static boolean oneLibrary(String model) {
        return model != null && ONE_LIBRARY_MODELS.contains(model.trim().toUpperCase(java.util.Locale.ROOT));
    }

    /** How long a player may report an analysed track without metadata before the deck shows a hint. */
    static final long METADATA_HINT_MS = 30_000;

    /**
     * The hint of a deck whose analysed track has had no metadata for {@link #METADATA_HINT_MS}: "onelibrary" for players
     * LJ Link cannot read yet, "missing" for the others, null while there is nothing to say.
     */
    static String metadataHint(String model, boolean analysed, boolean titleKnown, long ageMs) {
        if (!analysed || titleKnown || ageMs < METADATA_HINT_MS) {
            return null;
        }
        return oneLibrary(model) ? "onelibrary" : "missing";
    }

    /** The announced mixer (number, name, address) or null, and whether a mixer status of it arrived. */
    private volatile SeenDevice mixer;
    private volatile boolean mixerStatusSeen;
    private volatile boolean mixerNoData;

    private void mixerAnnounced(int number, String name, String address) {
        SeenDevice m = mixer;
        if (m != null && m.number == number && m.address.equals(address) || mixerStatusSeen) {
            return;
        }
        mixer = new SeenDevice(number, name, address);
        mixerNoData = false;
        publishMixer(mixer, "waiting");
    }

    private void mixerLeft(int number, String address) {
        SeenDevice m = mixer;
        if (m == null || m.number != number || !m.address.equals(address)) {
            return;
        }
        mixer = null;
        mixerStatusSeen = false;
        mixerNoData = false;
        hub.setMixer(java.util.Map.of("present", false));
    }

    private void publishMixer(SeenDevice m, String statusPackets) {
        hub.setMixer(java.util.Map.of("present", true, "name", m.name.trim(), "number", m.number, "address", m.address,
                "statusPackets", statusPackets, "simulated", false, "levels", false));
    }

    /**
     * Once the virtual CDJ has listened for a while without any mixer status from the announced mixer, the Mixer tab says
     * so instead of waiting forever. Called every second.
     */
    void checkMixerData(boolean listening) {
        SeenDevice m = mixer;
        if (m != null && listening && !mixerStatusSeen && !mixerNoData) {
            mixerNoData = true;
            publishMixer(m, "none");
            System.err.println("Mixer " + m.name.trim() + " (device " + m.number + ") is announced but sends no mixer status LJ Link can read");
        }
    }

    static final String SHOWKONTROL_NOTE = "ShowKontrol is running on the network. Both programs at the same time on one computer can disturb each other.";
    static final String SHOWKONTROL_LOCAL_NOTE = "ShowKontrol runs on this computer. Both programs at the same time on one computer can disturb each other.";

    /** Whether an address belongs to this computer; replaceable for tests. */
    java.util.function.Predicate<String> ownAddress = ProDjLinkSource::isOwnAddress;

    static boolean isOwnAddress(String address) {
        for (Diagnostics.Adapter a : Diagnostics.adapters()) {
            for (Diagnostics.Ipv4 ip : a.ipv4()) {
                if (ip.address().equals(address)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * ShowKontrol on this computer (its announcement came from one of our own addresses). Kept for the whole search:
     * once the virtual CDJ runs, beat-link ignores every announcement from its own address, so ShowKontrol on the same
     * computer seems to leave about 10 s after joining although it still runs.
     */
    private volatile SeenDevice showKontrolHere;

    /** ShowKontrol announces itself as "TCS-SHOWKONTROL": a quiet note in the app while it is there (on this computer: for the whole search). */
    private void showKontrolNote() {
        for (SeenDevice d : seenDevices.values()) {
            if (d.present && isShowKontrol(d.name) && showKontrolHere == null && ownAddress.test(d.address)) {
                showKontrolHere = d;
            }
        }
        boolean there = seenDevices.values().stream().anyMatch(d -> d.present && isShowKontrol(d.name));
        hub.setNote(showKontrolHere != null ? SHOWKONTROL_LOCAL_NOTE : there ? SHOWKONTROL_NOTE : null);
    }

    static boolean isShowKontrol(String name) {
        return name != null && name.toUpperCase(java.util.Locale.ROOT).replace("-", "").replace(" ", "").contains("SHOWKONTROL");
    }

    /** Counts one received packet of a device, for the diagnostics. */
    void countPacket(int device, String kind) {
        packets.computeIfAbsent(device, k -> new java.util.concurrent.ConcurrentHashMap<>())
                .computeIfAbsent(kind, k -> new java.util.concurrent.atomic.LongAdder()).increment();
    }

    /** "CdjStatus 1234, Beat 300" or "no status or beat packets". */
    private String packetSummary(int device) {
        java.util.Map<String, java.util.concurrent.atomic.LongAdder> m = packets.get(device);
        if (m == null || m.isEmpty()) {
            return "no status or beat packets";
        }
        java.util.List<String> parts = new java.util.ArrayList<>();
        new java.util.TreeMap<>(m).forEach((k, v) -> parts.add(k + " " + v.sum()));
        return String.join(", ", parts);
    }

    /**
     * Every device seen since the search started, with number, name, address, the adapter in its network and the packets
     * received from it; the count of mixer status packets; and a note when a mixer announced itself but sent no mixer status
     * LJ Link could read (then the Mixer tab says "No mixer found" although the mixer is on the network).
     *
     * @param adapterOf the local adapter in the network of an address, or null
     * @param listening the virtual CDJ has been listening long enough for "no packets" to mean something
     */
    java.util.List<String> seenDeviceLines(java.util.function.Function<String, String> adapterOf, boolean listening) {
        return seenDeviceLines(adapterOf, listening, false);
    }

    /** As above; {@code compact} for the summary: "Seen 33 DJM-A9 169.254.57.164 en5: no packets". */
    java.util.List<String> seenDeviceLines(java.util.function.Function<String, String> adapterOf, boolean listening, boolean compact) {
        java.util.List<String> out = new java.util.ArrayList<>();
        java.util.List<SeenDevice> list = new java.util.ArrayList<>(seenDevices.values());
        list.sort(Comparator.comparingInt((SeenDevice d) -> d.number).thenComparing(d -> d.address));
        long mixerPackets = 0;
        for (java.util.Map<String, java.util.concurrent.atomic.LongAdder> m : packets.values()) {
            java.util.concurrent.atomic.LongAdder a = m.get("MixerStatus");
            mixerPackets += a == null ? 0 : a.sum();
        }
        out.add("Mixer status packets received: " + mixerPackets);
        for (SeenDevice d : list) {
            java.util.Map<String, java.util.concurrent.atomic.LongAdder> m = packets.get(d.number);
            if (listening && d.present && isMixer(d.number, d.name) && (m == null || m.get("MixerStatus") == null)) {
                out.add(compact ? "NOTE: mixer " + d.name.trim() + " (" + d.number + ") sends no readable mixer status" : "NOTE: mixer " + d.name.trim() + " (device " + d.number + ") is on the network but no readable mixer status"
                        + " arrived from it");
            }
        }
        SeenDevice here = showKontrolHere;
        for (SeenDevice d : list) {
            if (d == here && compact) {
                out.add("NOTE: ShowKontrol on this computer (" + d.number + ", " + d.address + "), may disturb LJ Link");
            } else if (d == here) {
                out.add("NOTE: ShowKontrol (" + d.name.trim() + ") announced itself as device " + d.number + " from this computer ("
                        + d.address + "); beat-link ignores its own address after joining, so it may still run. Both on one computer can disturb each other");
            } else if (d.present && isShowKontrol(d.name) && compact) {
                out.add("NOTE: ShowKontrol on the network (" + d.number + ", " + d.address + ")");
            } else if (d.present && isShowKontrol(d.name)) {
                out.add("NOTE: ShowKontrol (" + d.name.trim() + ") is on the network as device " + d.number + " at " + d.address
                        + "; running it and LJ Link on one computer can disturb each other");
            }
        }
        for (SeenDevice d : list) {
            String adapter = adapterOf.apply(d.address);
            if (compact) {
                java.util.Map<String, java.util.concurrent.atomic.LongAdder> m = packets.get(d.number);
                out.add("Seen " + d.number + " " + d.name.trim() + " " + d.address + " " + (adapter == null ? "no adapter" : adapter)
                        + (d.present ? "" : " left") + ": " + (m == null || m.isEmpty() ? "no packets" : packetSummary(d.number)));
                continue;
            }
            out.add("Seen device " + d.number + ": " + d.name + " at " + d.address + " via "
                    + (adapter == null ? "no adapter in its network" : adapter) + (d.present ? "" : ", left")
                    + ", " + packetSummary(d.number));
        }
        return out;
    }

    /**
     * The last problems CrateDigger logged (it downloads export.pdb and the analysis files per player and slot), each with
     * the following exception line: shows why a medium has no database (for example a OneLibrary stick without export.pdb).
     */
    static java.util.List<String> crateDiggerProblems(java.util.List<String> log, int max) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (int i = 0; i < log.size(); i++) {
            String line = log.get(i);
            if (line.contains("CrateDigger") && (line.contains("WARN") || line.contains("ERROR"))
                    && (line.contains("Problem") || line.contains("failed") || line.contains("Unable"))) {
                String next = i + 1 < log.size() ? log.get(i + 1) : "";
                boolean cause = next.matches("^(\\d\\d:\\d\\d:\\d\\d )?[a-zA-Z_$][\\w.$]*(Exception|Error)\\b.*");
                out.add("CrateDigger: " + line + (cause ? " | " + next : ""));
            }
        }
        return out.size() > max ? out.subList(out.size() - max, out.size()) : out;
    }

    /** The mounted media as "1 usb db, 2 sd no db" (db: CrateDigger has its export.pdb), "none", or "-" while the MetadataFinder is not running. */
    private static String mountedMedia() {
        try {
            if (!MetadataFinder.getInstance().isRunning()) {
                return "-";
            }
            java.util.List<String> slots = new java.util.ArrayList<>();
            for (org.deepsymmetry.beatlink.data.SlotReference r : MetadataFinder.getInstance().getMountedMediaSlots()) {
                boolean db;
                try {
                    db = CrateDigger.getInstance().isRunning() && CrateDigger.getInstance().findDatabase(r) != null;
                } catch (RuntimeException e) {
                    db = false;
                }
                slots.add(r.player + " " + String.valueOf(r.slot).replace("_SLOT", "").toLowerCase(java.util.Locale.ROOT)
                        + (db ? " db" : " no db"));
            }
            java.util.Collections.sort(slots);
            return slots.isEmpty() ? "none" : String.join(", ", slots);
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
