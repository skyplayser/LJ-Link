package dev.ljlinkpro;

import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Demo source: three synthetic decks with generated waveforms, so the UI works without hardware. */
final class MockSource implements DeckSource {
    private static final int SEGMENTS = 400;
    private static final long TRACK_MS = 4 * 60_000L;

    private record Track(String title, String artist, String key, double bpm) {}

    private static final List<Track> TRACKS = List.of(
            new Track("Demo Track One", "Demo Artist", "8A", 126),
            new Track("Demo Track Two", "Demo Artist", "5B", 128),
            new Track("Demo Track Three", "Another Demo", "11A", 124),
            new Track("Demo Track Four", "Another Demo", "2A", 130));

    private final Hub.Scope hub;
    private ScheduledExecutorService executor;
    private final long[] originNanos = new long[3];
    private final long[] offsets = {60_000, 150_000, 0};
    private final int[] trackIndex = {0, 1, 2};

    MockSource(Hub.Scope hub) {
        this.hub = hub;
        hub.setSource(this);
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
        java.util.Arrays.fill(originNanos, System.nanoTime());
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mock-source");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleAtFixedRate(this::update, 0, 50, TimeUnit.MILLISECONDS);
        hub.setMixer(java.util.Map.of("present", true, "name", "DEMO-MIXER", "number", 33, "address", "demo",
                "bpm", 126.0, "master", true, "simulated", true, "levels", true));
        hub.setState("monitoring", "demo", "Demo mode – no real hardware");
    }

    private void loadTrack(int i) {
        Deck d = hub.deck(i + 1);
        Track t = TRACKS.get(trackIndex[i] % TRACKS.size());
        d.name = "DEMO-PLAYER";
        d.address = "demo";
        d.hasTrack = true;
        d.title = t.title();
        d.artist = t.artist();
        d.key = t.key();
        d.trackBpm = t.bpm();
        d.effectiveBpm = t.bpm();
        d.speed = 1.0;
        d.pitchPercent = i == 1 ? 0.35 : 0.0;
        d.trackNumber = trackIndex[i] % 9 + 1;
        d.durationMs = TRACK_MS;
        d.setWaveform(waveform(trackIndex[i] * 31L + i));
        d.setArt(cover(trackIndex[i] * 31L + i));
        d.setDetail(detail(trackIndex[i] * 31L + i, t.bpm()));
        int beats = (int) (TRACK_MS / 1000.0 * t.bpm() / 60.0);
        int[] times = new int[beats];
        byte[] bars = new byte[beats];
        for (int b = 0; b < beats; b++) {
            times[b] = (int) (b * 60_000.0 / t.bpm());
            bars[b] = (byte) (b % 4 + 1);
        }
        d.setBeats(times, bars);
        d.trackVersion++;
        d.cues = List.of(
                java.util.Map.of("t", 60_000L, "hot", 0, "loop", true, "loopEnd", 67_620L, "color", "#ff8800"),
                java.util.Map.of("t", 0L, "hot", 1, "loop", false, "loopEnd", 0L, "color", "#ff0017"),
                java.util.Map.of("t", 24_000L, "hot", 2, "loop", false, "loopEnd", 0L, "color", "#00c4ff"),
                java.util.Map.of("t", 96_000L, "hot", 3, "loop", false, "loopEnd", 0L, "color", "#33ff00"),
                java.util.Map.of("t", 150_000L, "hot", 0, "loop", false, "loopEnd", 0L, "color", "#ffffff"));
    }

    private void update() {
        try {
            long now = System.nanoTime();
            for (int i = 0; i < 3; i++) {
                Deck d = hub.deck(i + 1);
                d.playing = i != 2;
                d.onAir = i == 0;
                double t = now / 1e9;
                d.fader = i == 2 ? 0.0 : (i == 0 ? 0.9 : 0.55 + 0.4 * Math.sin(t / 3 + i));
                d.eqHi = 0.3 * Math.sin(t / 4 + i);
                d.eqMid = 0.4 * Math.sin(t / 5 + 2 * i);
                d.eqLow = i == 1 ? -0.7 : 0.2 * Math.sin(t / 6 + i);
                d.master = i == 0;
                d.synced = i == 1;
                long pos = d.playing ? offsets[i] + (now - originNanos[i]) / 1_000_000 : offsets[i];
                if (pos >= TRACK_MS) {
                    trackIndex[i]++;
                    offsets[i] = 0;
                    originNanos[i] = now;
                    loadTrack(i);
                    pos = 0;
                }
                d.velocity = d.playing ? 1.0 : 0.0;
                d.samplePosition(pos);
                d.velocity = d.playing ? 1.0 : 0.0;
            }
        } catch (RuntimeException e) {
            System.err.println("Mock update failed: " + e);
        }
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

    /** A plausible-looking track: quiet intro, build-up, loud drop, breakdown, second drop, outro. */
    private static byte[] waveform(long seed) {
        Random rnd = new Random(seed);
        byte[] out = new byte[SEGMENTS * 4];
        for (int i = 0; i < SEGMENTS; i++) {
            double p = i / (double) SEGMENTS;
            double energy = p < 0.1 ? 0.3 : p < 0.25 ? 0.3 + (p - 0.1) * 4 : p < 0.5 ? 0.95
                    : p < 0.6 ? 0.4 : p < 0.9 ? 0.95 : 0.3;
            double beat = 0.75 + 0.25 * Math.abs(Math.sin(i * Math.PI / 4.0));
            double h = Math.min(1.0, energy * beat * (0.85 + rnd.nextDouble() * 0.15));
            out[i * 4] = (byte) (h * 255);
            double mix = Math.max(0, Math.min(1, (energy - 0.3) / 0.65));
            out[i * 4 + 1] = (byte) (40 + 215 * mix);
            out[i * 4 + 2] = (byte) (200 - 110 * mix);
            out[i * 4 + 3] = (byte) (230 - 180 * mix);
        }
        return out;
    }

    /** Detail waveform at 150 frames per second with a kick on every beat, following the same song sections. */
    private static byte[] detail(long seed, double bpm) {
        Random rnd = new Random(seed);
        int frames = (int) (TRACK_MS * 150 / 1000);
        byte[] out = new byte[frames * 4];
        double framesPerBeat = 150 * 60.0 / bpm;
        for (int i = 0; i < frames; i++) {
            double p = i / (double) frames;
            double energy = p < 0.1 ? 0.3 : p < 0.25 ? 0.3 + (p - 0.1) * 4 : p < 0.5 ? 0.95
                    : p < 0.6 ? 0.4 : p < 0.9 ? 0.95 : 0.3;
            double phase = (i % framesPerBeat) / framesPerBeat;
            double kick = Math.exp(-phase * 6);
            double h = Math.min(1.0, energy * (0.45 + 0.55 * kick) * (0.9 + rnd.nextDouble() * 0.1));
            out[i * 4] = (byte) (h * 255);
            out[i * 4 + 1] = (byte) (60 + 195 * kick);
            out[i * 4 + 2] = (byte) (70 - 20 * kick);
            out[i * 4 + 3] = (byte) (255 - 45 * kick);
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
                        row.apply("Key", String.valueOf(d.key)), row.apply("Duration", "04:00.0"))),
                java.util.Map.of("title", "Cues and loops", "rows", java.util.List.of(
                        row.apply("Hot cue A", "00:00.0"), row.apply("Hot cue B", "00:24.0"),
                        row.apply("Hot cue C", "01:36.0"), row.apply("Memory point", "01:00.0 – loop until 01:07.6"))));
    }

    @Override
    public void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }
}
