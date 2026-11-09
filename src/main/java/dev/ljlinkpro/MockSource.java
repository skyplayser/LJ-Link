package dev.ljlinkpro;

import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Demo source: three synthetic decks with generated waveforms, so the UI (and VJ-Link) works without hardware.
 * Decks 1 and 2 are mixed like a DJ would: the master plays its track (16 bars intro, 8 up, 16 chorus, 8 down, 16 chorus,
 * 16 outro); at the start of its outro the other deck starts beat-aligned and in sync, the crossfader and channel faders move
 * over, and when the old track ends the new deck becomes master and the old one loads the next track. Every other track
 * has a 4-beat loop at the end of its breakdown. Deck 3 stands paused with a track loaded.
 */
final class MockSource implements DeckSource {
    private static final int SEGMENTS = 400;
    /** Phrase plan in bars (4 beats each). */
    private static final int[] PHRASE_BARS = {16, 8, 16, 8, 16, 16};
    private static final String[] PHRASE_PLAN = {"intro", "up", "chorus", "down", "chorus", "outro"};
    static final int TRACK_BEATS;
    static final int[] PHRASE_STARTS = new int[PHRASE_BARS.length];
    static {
        int beat = 1;
        for (int i = 0; i < PHRASE_BARS.length; i++) {
            PHRASE_STARTS[i] = beat;
            beat += PHRASE_BARS[i] * 4;
        }
        TRACK_BEATS = beat - 1;
    }
    private static final int OUTRO_START = PHRASE_STARTS[5];
    /** The 4-beat loop: the last bar of the breakdown ("down"), played 4 times. */
    private static final int LOOP_START = PHRASE_STARTS[4] - 4;
    private static final int LOOP_REPEATS = 4;

    private record Track(String title, String artist, String key, double bpm) {}

    private static final List<Track> TRACKS = List.of(
            new Track("Demo Track One", "Demo Artist", "8A", 126),
            new Track("Demo Track Two", "Demo Artist", "5B", 128),
            new Track("Demo Track Three", "Another Demo", "11A", 124),
            new Track("Demo Track Four", "Another Demo", "2A", 130));

    private final Hub.Scope hub;
    private ScheduledExecutorService executor;
    /** Position in ms (deck 1-3), speed, playing; the master is deck 1 or 2. */
    private final double[] pos = new double[3];
    private final double[] speed = {1, 1, 1};
    private final boolean[] playing = {true, false, false};
    private final int[] trackIndex = {0, 1, 2};
    private int nextTrack = 3;
    private int master = 0;
    private boolean mixing;
    private int loopsLeft;
    private long lastNanos;

    MockSource(Hub.Scope hub) {
        this.hub = hub;
        hub.setSource(this);
    }

    static double msPerBeat(double bpm) {
        return 60_000.0 / bpm;
    }

    @Override
    public void start() {
        hub.setDevices(List.of(
                new Hub.DeviceInfo(1, "DEMO-PLAYER", "demo"),
                new Hub.DeviceInfo(2, "DEMO-PLAYER", "demo"),
                new Hub.DeviceInfo(3, "DEMO-PLAYER", "demo")));
        for (int i = 0; i < 3; i++) {
            loadTrack(i);
        }
        // Start a little into the intro of deck 1, so the first mix comes after about a minute and a half.
        pos[0] = (PHRASE_STARTS[1] - 1 - 40) * msPerBeat(TRACKS.get(0).bpm());
        lastNanos = System.nanoTime();
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mock-source");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleAtFixedRate(this::update, 0, 20, TimeUnit.MILLISECONDS);
        hub.setMixer(java.util.Map.of("present", true, "name", "DEMO-MIXER", "number", 33, "address", "demo",
                "bpm", 126.0, "master", true, "simulated", true, "levels", true));
        hub.setState("monitoring", "demo", "Demo mode – no real hardware");
    }

    private Track track(int i) {
        return TRACKS.get(trackIndex[i] % TRACKS.size());
    }

    private void loadTrack(int i) {
        Deck d = hub.deck(i + 1);
        Track t = track(i);
        d.name = "DEMO-PLAYER";
        d.address = "demo";
        d.hasTrack = true;
        d.title = t.title();
        d.artist = t.artist();
        d.key = t.key();
        d.trackBpm = t.bpm();
        d.effectiveBpm = t.bpm() * speed[i];
        d.speed = speed[i];
        d.pitchPercent = (speed[i] - 1) * 100;
        d.trackNumber = trackIndex[i] % 9 + 1;
        double beatMs = msPerBeat(t.bpm());
        d.durationMs = Math.round(TRACK_BEATS * beatMs);
        long seed = trackIndex[i] * 31L + i;
        d.setWaveform(waveform(seed));
        d.setArt(cover(seed));
        d.setDetail(detail(seed, t.bpm(), d.durationMs));
        int[] times = new int[TRACK_BEATS];
        byte[] bars = new byte[TRACK_BEATS];
        for (int b = 0; b < TRACK_BEATS; b++) {
            times[b] = (int) Math.round(b * beatMs);
            bars[b] = (byte) (b % 4 + 1);
        }
        d.setBeats(times, bars);
        d.loop = null;
        d.loopBeats = 0;
        String[] kinds = PHRASE_PLAN.clone();
        d.phrases = new Phrases(PHRASE_STARTS.clone(), kinds, TRACK_BEATS + 1);
        d.trackVersion++;
        java.util.function.IntFunction<Long> at = beat -> Math.round((beat - 1) * beatMs);
        d.cues = List.of(
                java.util.Map.of("t", at.apply(PHRASE_STARTS[0]), "hot", 1, "loop", false, "loopEnd", 0L, "color", "#ff0017"),
                java.util.Map.of("t", at.apply(PHRASE_STARTS[2]), "hot", 2, "loop", false, "loopEnd", 0L, "color", "#ff8800"),
                java.util.Map.of("t", at.apply(PHRASE_STARTS[4]), "hot", 3, "loop", false, "loopEnd", 0L, "color", "#00c4ff"),
                java.util.Map.of("t", at.apply(OUTRO_START), "hot", 4, "loop", false, "loopEnd", 0L, "color", "#33ff00"),
                java.util.Map.of("t", at.apply(PHRASE_STARTS[1]), "hot", 0, "loop", false, "loopEnd", 0L, "color", "#ffffff"),
                java.util.Map.of("t", at.apply(LOOP_START), "hot", 0, "loop", true,
                        "loopEnd", at.apply(LOOP_START + 4), "color", "#ff8800"));
    }

    private void update() {
        try {
            long now = System.nanoTime();
            double dt = Math.min(200, (now - lastNanos) / 1e6);
            lastNanos = now;
            int other = 1 - master;
            Track mt = track(master);
            double masterBeatMs = msPerBeat(mt.bpm() * speed[master]);
            // The master glides back to its own tempo once it plays alone (the DJ rides the pitch back slowly).
            if (!mixing) {
                speed[master] += (1 - speed[master]) * Math.min(1, dt / 20_000);
            }
            double before = pos[master];
            pos[master] += dt * speed[master];
            // The loop at the end of the breakdown, on every other track.
            Deck md = hub.deck(master + 1);
            double trackBeatMs = msPerBeat(mt.bpm());
            double loopStart = (LOOP_START - 1) * trackBeatMs;
            double loopEnd = loopStart + 4 * trackBeatMs;
            if (trackIndex[master] % 2 == 1 && before < loopStart && pos[master] >= loopStart) {
                loopsLeft = LOOP_REPEATS - 1;
                md.loop = new long[] {Math.round(loopStart), Math.round(loopEnd)};
                md.loopBeats = 4;
            }
            if (md.loop != null && pos[master] >= loopEnd) {
                if (loopsLeft > 0) {
                    loopsLeft--;
                    pos[master] -= loopEnd - loopStart;
                } else {
                    md.loop = null;
                    md.loopBeats = 0;
                }
            }
            double outroAt = (OUTRO_START - 1) * trackBeatMs;
            if (!mixing && before < outroAt && pos[master] >= outroAt) {
                // Mix in: the other deck starts in sync and on the beat.
                mixing = true;
                Track ot = track(other);
                speed[other] = mt.bpm() * speed[master] / ot.bpm();
                playing[other] = true;
                pos[other] = (pos[master] - outroAt) / masterBeatMs * msPerBeat(ot.bpm());
            } else if (mixing) {
                Track ot = track(other);
                speed[other] = mt.bpm() * speed[master] / ot.bpm();
                pos[other] += dt * speed[other];
            }
            double mixProgress = mixing ? Math.max(0, Math.min(1, (pos[master] - outroAt) / (TRACK_BEATS * trackBeatMs - outroAt))) : 0;
            if (pos[master] >= TRACK_BEATS * trackBeatMs) {
                // The old track ends: the other deck is the master now, the old one loads the next track and waits.
                playing[master] = false;
                pos[master] = 0;
                speed[master] = 1;
                trackIndex[master] = nextTrack++;
                loadTrack(master);
                master = other;
                other = 1 - master;
                mixing = false;
                mixProgress = 0;
            }
            double t = now / 1e9;
            for (int i = 0; i < 3; i++) {
                Deck d = hub.deck(i + 1);
                Track tr = track(i);
                d.playing = i < 2 && playing[i];
                d.master = i == master;
                d.synced = i == other && mixing;
                d.onAir = i == master || i == other && mixing;
                d.speed = speed[i];
                d.effectiveBpm = tr.bpm() * speed[i];
                d.pitchPercent = (speed[i] - 1) * 100;
                // Channel faders and EQ: the master is up; the incoming deck comes up over the first half of the mix with
                // its bass cut, and the basses are swapped at the middle; the old deck goes down over the second half.
                double up = Math.min(1, mixProgress * 2);
                double down = Math.min(1, Math.max(0, mixProgress * 2 - 1));
                if (i == 2) {
                    d.fader = 0.0;
                    d.eqHi = 0.0;
                    d.eqMid = 0.0;
                    d.eqLow = 0.0;
                } else if (i == master) {
                    d.fader = 0.9 * (1 - down);
                    d.eqLow = mixing && mixProgress >= 0.5 ? -1.0 : 0.0;
                    d.eqMid = 0.15 * Math.sin(t / 5 + i);
                    d.eqHi = 0.2 * Math.sin(t / 4 + i);
                } else {
                    d.fader = mixing ? 0.9 * up : 0.0;
                    d.eqLow = mixing && mixProgress < 0.5 ? -1.0 : 0.0;
                    d.eqMid = 0.0;
                    d.eqHi = 0.0;
                }
                d.velocity = d.playing ? speed[i] : 0.0;
                d.samplePosition(Math.round(pos[i]));
                d.velocity = d.playing ? speed[i] : 0.0;
            }
            // Crossfader: deck 1 left (0), deck 2 right (1); it moves over during the mix.
            double side = master == 0 ? 0 : 1;
            hub.setCrossfader(mixing ? side + (1 - 2 * side) * mixProgress : side);
        } catch (RuntimeException e) {
            System.err.println("Mock update failed: " + e);
        }
    }

    /** Loudness of the demo song at a beat (1-based): quiet intro, rising build-up, loud choruses, breakdown, outro. */
    private static double energyAt(double beat) {
        if (beat < PHRASE_STARTS[1]) {
            return 0.3;
        }
        if (beat < PHRASE_STARTS[2]) {
            return 0.3 + 0.6 * (beat - PHRASE_STARTS[1]) / (PHRASE_STARTS[2] - PHRASE_STARTS[1]);
        }
        if (beat < PHRASE_STARTS[3] || beat >= PHRASE_STARTS[4] && beat < PHRASE_STARTS[5]) {
            return 0.95;
        }
        if (beat < PHRASE_STARTS[4]) {
            return 0.4;
        }
        return 0.3;
    }

    /** A small generated cover so the demo shows artwork without shipping any real album art. */
    private static byte[] cover(long seed) {
        Random rnd = new Random(seed);
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(120, 120, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        float hue = rnd.nextFloat();
        g.setPaint(new java.awt.GradientPaint(0, 0, java.awt.Color.getHSBColor(hue, 0.8f, 0.9f),
                120, 120, java.awt.Color.getHSBColor((hue + 0.35f) % 1f, 0.9f, 0.35f)));
        g.fillRect(0, 0, 120, 120);
        g.setColor(new java.awt.Color(255, 255, 255, 140));
        for (int i = 0; i < 6; i++) {
            int h = 20 + rnd.nextInt(70);
            g.fillRoundRect(12 + i * 17, 110 - h, 11, h, 6, 6);
        }
        g.dispose();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try {
            javax.imageio.ImageIO.write(img, "png", out);
        } catch (java.io.IOException e) {
            return null;
        }
        return out.toByteArray();
    }

    /** Preview waveform following the phrases: quiet intro, build-up, loud choruses, breakdown, outro. */
    private static byte[] waveform(long seed) {
        Random rnd = new Random(seed);
        byte[] out = new byte[SEGMENTS * 4];
        for (int i = 0; i < SEGMENTS; i++) {
            double beat = 1 + i * (double) TRACK_BEATS / SEGMENTS;
            double energy = energyAt(beat);
            double pulse = 0.75 + 0.25 * Math.abs(Math.sin(i * Math.PI / 4.0));
            double h = Math.min(1.0, energy * pulse * (0.85 + rnd.nextDouble() * 0.15));
            out[i * 4] = (byte) (h * 255);
            double mix = Math.max(0, Math.min(1, (energy - 0.3) / 0.65));
            out[i * 4 + 1] = (byte) (40 + 215 * mix);
            out[i * 4 + 2] = (byte) (200 - 110 * mix);
            out[i * 4 + 3] = (byte) (230 - 180 * mix);
        }
        return out;
    }

    /**
     * Detail waveform at 150 frames per second: height plus red (bass: a kick on every beat, none in the breakdown),
     * green (mids, follow the energy) and blue (highs: a hi-hat on every off-beat), like rekordbox's RGB waveform.
     */
    private static byte[] detail(long seed, double bpm, long durationMs) {
        Random rnd = new Random(seed);
        int frames = (int) (durationMs * 150 / 1000);
        byte[] out = new byte[frames * 4];
        double framesPerBeat = 150 * 60.0 / bpm;
        for (int i = 0; i < frames; i++) {
            double beat = 1 + i / framesPerBeat;
            double energy = energyAt(beat);
            double phase = (i % framesPerBeat) / framesPerBeat;
            boolean breakdown = beat >= PHRASE_STARTS[3] && beat < PHRASE_STARTS[4];
            double kick = breakdown || beat < PHRASE_STARTS[0] + 4 ? 0 : Math.exp(-phase * 6);
            double hat = energy > 0.35 ? Math.exp(-Math.abs(phase - 0.5) * 18) : 0;
            double mid = energy * (0.7 + 0.3 * rnd.nextDouble());
            double h = Math.min(1.0, energy * (0.45 + 0.55 * Math.max(kick, hat * 0.6)) * (0.9 + rnd.nextDouble() * 0.1));
            out[i * 4] = (byte) (h * 255);
            out[i * 4 + 1] = (byte) Math.min(255, 40 + 215 * kick);
            out[i * 4 + 2] = (byte) Math.min(255, 30 + 120 * mid);
            out[i * 4 + 3] = (byte) Math.min(255, 60 + 195 * hat);
        }
        return out;
    }

    @Override
    public java.util.List<java.util.Map<String, Object>> info(int deck) {
        Deck d = hub.deck(deck);
        java.util.function.BiFunction<String, String, java.util.Map<String, String>> row = (k, v) -> java.util.Map.of("k", k, "v", v);
        String bpm = String.format(java.util.Locale.ROOT, "%.2f", d.effectiveBpm);
        String yes = "yes";
        String no = "no";
        return java.util.List.of(
                java.util.Map.of("title", "Device", "rows", java.util.List.of(
                        row.apply("Name", "DEMO-PLAYER"), row.apply("Player number", String.valueOf(deck)),
                        row.apply("IP address", "192.168.1." + (19 + deck)), row.apply("Firmware", "1.00"),
                        row.apply("Note", "Demo mode: simulated data, no real hardware"))),
                java.util.Map.of("title", "Status", "rows", java.util.List.of(
                        row.apply("Playing", d.playing ? yes : no), row.apply("ON AIR (from mixer)", d.onAir ? yes : no),
                        row.apply("Track loaded", yes), row.apply("Busy (loading)", no))),
                java.util.Map.of("title", "Tempo and sync", "rows", java.util.List.of(
                        row.apply("Track BPM", bpm), row.apply("Pitch", String.format(java.util.Locale.ROOT, "%+.2f %%", d.pitchPercent)),
                        row.apply("Tempo master", d.master ? yes : no), row.apply("Sync active", d.synced ? yes : no))),
                java.util.Map.of("title", "Track", "rows", java.util.List.of(
                        row.apply("Title", String.valueOf(d.title)), row.apply("Artist", String.valueOf(d.artist)),
                        row.apply("Key", String.valueOf(d.key)), row.apply("Duration", String.format(java.util.Locale.ROOT, "%02d:%04.1f", d.durationMs / 60_000, d.durationMs % 60_000 / 1000.0)))),
                java.util.Map.of("title", "Cues and loops", "rows", java.util.List.of(
                        row.apply("Hot cues", "A intro, B first chorus, C second chorus, D outro"),
                        row.apply("Memory points", "start of the build-up; a 4-beat loop at the end of the breakdown"))));
    }

    @Override
    public void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }
}
