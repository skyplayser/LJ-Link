package dev.ljlinkpro;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.websocket.WsContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The VJ-Link interface: a WebSocket endpoint ({@code /vjlink}) that streams what a VJ program needs to follow the DJ,
 * as small JSON messages (protocol version 1, every message has a field {@code t}; receivers ignore unknown fields):
 * <ul>
 * <li>{@code hello} on connect (and again when the privacy mode changes), then every deck, then the tempo;</li>
 * <li>{@code tempo} about 30 times a second: tempo and a running beat counter of the master deck (whole number = beat);</li>
 * <li>{@code deck} at once on a change, otherwise twice a second; {@code gone} when a deck goes away;</li>
 * <li>{@code event}: a new track on air, phrase changes and warnings before them ({@code next}), hot cues, loops;</li>
 * <li>{@code wave} about 30 times a second: bass, mids and highs of the master deck at the play position;</li>
 * <li>{@code mixer} about 10 times a second when faders are known.</li>
 * </ul>
 * The VJ program may send {@code {"t":"vj","scene":"Drop",...}}; LJ Link shows the connection and the scene.
 * Always on, nothing to set up. Works the same with the demo.
 */
final class VjLink {
    static final int VERSION = 1;
    static final long TICK_MS = 33;
    private static final long DECK_EVERY_NANOS = 500_000_000L;
    private static final int[] WARN_BEATS = {32, 16, 8, 4};

    private final Hub hub;
    private final String appVersion;
    private final Set<WsContext> clients = ConcurrentHashMap.newKeySet();
    private final Map<WsContext, Map<String, Object>> remote = new ConcurrentHashMap<>();
    private final Map<WsContext, Long> remoteSeen = new ConcurrentHashMap<>();
    /** A VJ program that sent no status for this long counts as gone. */
    static final long STATUS_TIMEOUT_NANOS = 6_000_000_000L;
    private volatile String lastStatus = "";
    private ScheduledExecutorService ticker;

    // Guarded by this: what was sent and seen, per deck number.
    private final Map<Integer, String> lastDeck = new HashMap<>();
    private final Map<Integer, Long> lastDeckNanos = new HashMap<>();
    private final Map<Integer, Track> seen = new HashMap<>();
    private final Set<Integer> known = new HashSet<>();
    private int masterDeck;
    private double gridPrev = Double.NaN;
    private double counter;
    private long tickCount;
    private boolean started;

    /** What the event detection remembers about a deck between ticks. */
    private static final class Track {
        int trackVersion = Integer.MIN_VALUE;
        boolean announced;
        long lastPos = -1;
        int phrase = -2;
        double beatsLeft = Double.NaN;
        boolean loop;
    }

    VjLink(Hub hub, String appVersion) {
        this.hub = hub;
        this.appVersion = appVersion;
    }

    void start() {
        Privacy.addListener(this::privacyChanged);
        ticker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "vjlink");
            t.setDaemon(true);
            return t;
        });
        ticker.scheduleAtFixedRate(() -> {
            try {
                List<String> out = tick(System.nanoTime());
                if (!remoteSeen.isEmpty() && tickCount % 30 == 0) {
                    reportStatus(); // a VJ program that went quiet disappears from the header
                }
                if (!clients.isEmpty()) {
                    for (String m : out) {
                        broadcast(m);
                    }
                }
            } catch (RuntimeException e) {
                System.err.println("VJ-Link tick failed: " + e);
            }
        }, TICK_MS, TICK_MS, TimeUnit.MILLISECONDS);
    }

    // ------------------------------------------------------------------ clients

    void addClient(WsContext ctx) {
        clients.add(ctx);
        for (String m : greeting(System.nanoTime())) {
            send(ctx, m);
        }
        System.out.println("VJ-Link connected (" + clients.size() + ")");
        reportStatus();
    }

    void removeClient(WsContext ctx) {
        if (clients.remove(ctx)) {
            System.out.println("VJ-Link disconnected (" + clients.size() + ")");
        }
        remote.remove(ctx);
        remoteSeen.remove(ctx);
        reportStatus();
    }

    /**
     * A status message from the VJ program, sent on connect, on every change and otherwise every 2 s:
     * {"t":"vj","app":"VJ-Link","version":"0.x","scene":"Drop","sceneIndex":4,"blackout":false,"master":0.85,
     * "bpm":126.0,"bpmSource":"ljlink","outputs":2,"nowPlaying":true}. Only known fields with the right type are kept.
     */
    void onMessage(WsContext ctx, String text) {
        try {
            Map<?, ?> m = Hub.JSON.readValue(text, Map.class);
            if (!"vj".equals(m.get("t"))) {
                return;
            }
            Map<String, Object> s = new LinkedHashMap<>();
            for (String k : List.of("app", "version", "scene", "bpmSource")) {
                if (m.get(k) instanceof String str && !str.isBlank()) {
                    s.put(k, str.length() > 60 ? str.substring(0, 60) : str);
                }
            }
            for (String k : List.of("sceneIndex", "master", "bpm", "outputs")) {
                if (m.get(k) instanceof Number n) {
                    s.put(k, n);
                }
            }
            for (String k : List.of("blackout", "nowPlaying")) {
                if (m.get(k) instanceof Boolean b) {
                    s.put(k, b);
                }
            }
            remote.put(ctx, s);
            remoteSeen.put(ctx, System.nanoTime());
            reportStatus();
        } catch (JsonProcessingException | RuntimeException e) {
            // Not ours or broken: ignore, the stream goes on.
        }
    }

    /**
     * For the browser header: the VJ programs that sent a status within {@link #STATUS_TIMEOUT_NANOS} (the newest one's
     * fields, and how many); null when there is none, so the header shows nothing.
     */
    Map<String, Object> status() {
        long now = System.nanoTime();
        Map<String, Object> newest = null;
        long newestAt = Long.MIN_VALUE;
        int live = 0;
        for (Map.Entry<WsContext, Long> e : remoteSeen.entrySet()) {
            if (now - e.getValue() > STATUS_TIMEOUT_NANOS) {
                continue;
            }
            live++;
            if (newest == null || e.getValue() - newestAt > 0) {
                newest = remote.get(e.getKey());
                newestAt = e.getValue();
            }
        }
        if (newest == null) {
            return null;
        }
        Map<String, Object> s = new LinkedHashMap<>(newest);
        s.put("clients", live);
        return s;
    }

    private void reportStatus() {
        Map<String, Object> s = status();
        String key = s == null ? "" : write(s);
        if (!key.equals(lastStatus)) {
            lastStatus = key;
            hub.setStateExtra("vjlink", s);
        }
    }

    private void privacyChanged() {
        if (clients.isEmpty()) {
            return;
        }
        for (String m : greeting(System.nanoTime())) {
            broadcast(m);
        }
    }

    // ------------------------------------------------------------------ messages

    /** Sent to a new client (and to all when the privacy mode changes): hello, every deck, the tempo. */
    synchronized List<String> greeting(long now) {
        List<String> out = new ArrayList<>();
        Map<String, Object> hello = new LinkedHashMap<>();
        hello.put("t", "hello");
        hello.put("version", VERSION);
        hello.put("privacy", Privacy.enabled());
        hello.put("app", "LJ-Link PRO");
        hello.put("appVersion", appVersion);
        out.add(write(hello));
        for (Deck d : decks()) {
            out.add(write(deckMessage(d, position(d, now))));
        }
        Deck m = master();
        if (m != null) {
            out.add(write(tempoMessage(m, now, false)));
        }
        return out;
    }

    /** One tick: everything that is due now, in order. */
    synchronized List<String> tick(long now) {
        List<String> out = new ArrayList<>();
        tickCount++;
        List<Deck> decks = decks();
        Set<Integer> present = new HashSet<>();
        Deck master = master();
        for (Deck d : decks) {
            present.add(d.number);
            known.add(d.number);
            long pos = position(d, now);
            Track tr = seen.computeIfAbsent(d.number, k -> new Track());
            // Positions for scrolling waveforms: the master every tick, the others 5 times a second, and at once after a jump.
            long prev = tr.lastPos;
            boolean jumped = pos >= 0 && prev >= 0 && (pos < prev - 50 || pos - prev > 400);
            if (pos >= 0 && (d == master || tickCount % 6 == 0 || jumped)) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("t", "pos");
                p.put("deck", d.number);
                p.put("ms", pos);
                p.put("v", round(d.playing ? d.velocity : 0, 4));
                out.add(write(p));
            }
            events(d, tr, pos, out);
            Map<String, Object> msg = deckMessage(d, pos);
            // Changes are compared without the position (it moves all the time; it goes out with the regular update).
            Map<String, Object> inner = new LinkedHashMap<>(castMap(msg.get("deck")));
            inner.remove("position");
            String key = write(inner);
            Long last = lastDeckNanos.get(d.number);
            if (!key.equals(lastDeck.get(d.number)) || last == null || now - last >= DECK_EVERY_NANOS) {
                lastDeck.put(d.number, key);
                lastDeckNanos.put(d.number, now);
                out.add(write(msg));
            }
        }
        for (Integer n : new ArrayList<>(known)) {
            if (!present.contains(n)) {
                known.remove(n);
                seen.remove(n);
                lastDeck.remove(n);
                lastDeckNanos.remove(n);
                out.add(write(Map.of("t", "gone", "deck", n)));
            }
        }
        Deck m = master;
        if (m != null) {
            out.add(write(tempoMessage(m, now, true)));
            Map<String, Object> wave = waveMessage(m, position(m, now));
            if (wave != null) {
                out.add(write(wave));
            }
        }
        if (tickCount % 3 == 0) {
            Map<String, Object> mixer = mixerMessage(decks);
            if (mixer != null) {
                out.add(write(mixer));
            }
        }
        started = true;
        return out;
    }

    private void events(Deck d, Track tr, long pos, List<String> out) {
        boolean live = d.playing && (d.master || d.onAir);
        if (d.trackVersion != tr.trackVersion) {
            tr.trackVersion = d.trackVersion;
            tr.announced = false;
            tr.phrase = -2;
            tr.beatsLeft = Double.NaN;
            tr.lastPos = -1;
        }
        if (live && !tr.announced && d.hasTrack) {
            tr.announced = true;
            if (started) {
                out.add(write(Map.of("t", "event", "kind", "track", "deck", d.number)));
            }
        }
        // Phrases and the warnings before the next one.
        Phrases ph = d.phrases;
        int[] times = d.beatTimes;
        if (ph != null && times != null && pos >= 0) {
            double beat = beatAt(times, pos) + 1; // 1-based like the phrase starts
            int idx = ph.indexAt(beat);
            if (idx != tr.phrase) {
                if (idx >= 0 && d.playing && tr.phrase != -2) {
                    out.add(write(Map.of("t", "event", "kind", "phrase", "deck", d.number, "phrase", ph.kinds()[idx])));
                }
                tr.phrase = idx;
                tr.beatsLeft = Double.NaN;
            }
            int next = ph.nextStart(idx);
            if (idx >= 0 && next > 0 && idx + 1 < ph.kinds().length) {
                double left = next - beat;
                if (d.playing && !Double.isNaN(tr.beatsLeft)) {
                    for (int w : WARN_BEATS) {
                        if (tr.beatsLeft > w && left <= w && left > w - 1) {
                            Map<String, Object> e = new LinkedHashMap<>();
                            e.put("t", "event");
                            e.put("kind", "next");
                            e.put("deck", d.number);
                            e.put("phrase", ph.kinds()[idx + 1]);
                            e.put("inBeats", w);
                            out.add(write(e));
                        }
                    }
                }
                tr.beatsLeft = left;
            } else {
                tr.beatsLeft = Double.NaN;
            }
        }
        // Hot cues and memory points: passed while playing forward, or jumped to (pressed).
        long prev = tr.lastPos;
        if (pos >= 0 && prev >= 0 && pos != prev) {
            long delta = pos - prev;
            boolean forward = d.playing && delta > 0 && delta < 1500;
            for (Map<String, Object> c : d.cues) {
                long t = ((Number) c.get("t")).longValue();
                boolean passed = forward && prev < t && t <= pos;
                long[] loop = d.loop;
                boolean loopingBack = loop != null && Math.abs(pos - loop[0]) <= 80; // the loop repeating is no cue press
                boolean jumped = !forward && !loopingBack && Math.abs(pos - t) <= 80 && Math.abs(delta) > 200;
                if (passed || jumped) {
                    int hot = ((Number) c.get("hot")).intValue();
                    Map<String, Object> e = new LinkedHashMap<>();
                    e.put("t", "event");
                    e.put("kind", "cue");
                    e.put("deck", d.number);
                    e.put("cue", hot >= 1 && hot <= 8 ? String.valueOf((char) ('A' + hot - 1)) : "M");
                    if (c.get("color") != null) {
                        e.put("color", c.get("color"));
                    }
                    out.add(write(e));
                }
            }
        }
        tr.lastPos = pos;
        boolean loop = d.loop != null || d.loopBeats > 0;
        if (loop != tr.loop) {
            tr.loop = loop;
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("t", "event");
            e.put("kind", "loop");
            e.put("deck", d.number);
            e.put("active", loop);
            if (loop && d.loopBeats > 0) {
                e.put("beats", d.loopBeats);
            }
            out.add(write(e));
        }
    }

    Map<String, Object> deckMessage(Deck d, long pos) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("deck", d.number);
        if (d.name != null && !d.name.isBlank()) {
            m.put("model", d.name);
        }
        m.put("playing", d.playing);
        m.put("onAir", d.onAir);
        m.put("master", d.master);
        m.put("bpm", round(d.effectiveBpm, 2));
        if (d.key != null) {
            m.put("key", d.key);
        }
        Phrases ph = d.phrases;
        if (ph != null && d.beatTimes != null && pos >= 0) {
            int idx = ph.indexAt(beatAt(d.beatTimes, pos) + 1);
            if (idx >= 0) {
                m.put("phrase", ph.kinds()[idx]);
            }
        }
        if (d.durationMs > 0 && pos >= 0) {
            m.put("position", round(Math.min(1.0, pos / (double) d.durationMs), 4));
        }
        if (!Privacy.enabled()) {
            if (d.title != null) {
                m.put("title", d.title);
            }
            if (d.artist != null) {
                m.put("artist", d.artist);
            }
            if (d.art != null) {
                m.put("art", "/api/deck/" + d.number + "/art?v=" + d.artVersion);
            }
        }
        // Waveform for a scrolling display: the same data as for the browser (4 bytes per frame, 150 frames a second).
        if (d.detail != null) {
            m.put("detail", "/api/deck/" + d.number + "/detail?v=" + d.detailVersion);
            m.put("detailStyle", d.detailStyle);
        }
        if (d.durationMs > 0) {
            m.put("durationMs", d.durationMs);
        }
        if (d.beatTimes != null) {
            m.put("beats", "/api/deck/" + d.number + "/beats?v=" + d.beatVersion);
        }
        String id = trackId(d);
        if (id != null) {
            m.put("trackId", id);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("t", "deck");
        out.put("deck", m);
        return out;
    }

    /** Stable per track: a hash of title, artist and length (works for every source and does not reveal the title). */
    static String trackId(Deck d) {
        if (!d.hasTrack || d.title == null || d.title.isBlank()) {
            return null;
        }
        String s = d.title + "\u0000" + (d.artist == null ? "" : d.artist) + "\u0000" + Math.round(d.durationMs / 1000.0);
        try {
            byte[] h = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder("t");
            for (int i = 0; i < 8; i++) {
                b.append(String.format("%02x", h[i]));
            }
            return b.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return null;
        }
    }

    /**
     * Tempo and the running beat counter of the master deck. The counter follows the beat grid at the play position and
     * stays continuous over loops, cues, jumps and a change of master: after a jump it moves on to the same beat phase.
     */
    private Map<String, Object> tempoMessage(Deck m, long now, boolean advance) {
        long pos = position(m, now);
        int[] times = m.beatTimes;
        double grid;
        if (times != null && times.length > 1 && pos >= 0) {
            grid = beatAt(times, pos);
        } else if (pos >= 0 && m.trackBpm > 0) {
            grid = pos / 60_000.0 * m.trackBpm;
        } else {
            grid = Double.NaN;
        }
        if (advance && !Double.isNaN(grid)) {
            if (masterDeck == 0) {
                counter = Math.max(0, grid); // the first master: the counter starts at its beat in the track
            } else if (m.number != masterDeck || Double.isNaN(gridPrev)) {
                counter = jumpTo(counter, grid);
            } else {
                double delta = grid - gridPrev;
                counter = delta >= 0 && delta < 2 ? counter + delta : jumpTo(counter, grid);
            }
            gridPrev = grid;
            masterDeck = m.number;
        }
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("t", "tempo");
        t.put("bpm", round(m.effectiveBpm, 2));
        t.put("beat", round(counter, 3));
        t.put("masterDeck", m.number);
        if (!Double.isNaN(grid) && times != null && m.beatBars != null) {
            int i = Math.max(0, Math.min(times.length - 1, (int) Math.floor(grid)));
            int bar = m.beatBars[i];
            if (bar >= 1 && bar <= 4) {
                t.put("bar", bar);
            }
            Phrases ph = m.phrases;
            if (ph != null) {
                int beatNo = i + 1;
                int idx = ph.indexAt(beatNo);
                if (idx >= 0) {
                    t.put("phraseBeat", beatNo - ph.startBeats()[idx] + 1);
                }
            }
        }
        return t;
    }

    /** The counter after a jump: forward to the next value with the same fraction as the grid (less than one beat on). */
    static double jumpTo(double counter, double grid) {
        double frac = grid - Math.floor(grid);
        double base = Math.floor(counter) + frac;
        return base < counter ? base + 1 : base;
    }

    /** Bass, mids and highs (0..1) of the master deck at the play position, times channel fader and EQ when known. */
    Map<String, Object> waveMessage(Deck d, long pos) {
        if (pos < 0 || !d.hasTrack) {
            return null;
        }
        double bass;
        double mid;
        double high;
        byte[] detail = d.detail;
        if (detail != null && detail.length >= 4) {
            int frames = detail.length / 4;
            int from = (int) Math.max(0, (pos - TICK_MS) * 150 / 1000);
            int to = (int) Math.min(frames - 1, pos * 150 / 1000);
            if (from > to) {
                from = to;
            }
            if (to < 0) {
                return null;
            }
            bass = 0;
            mid = 0;
            high = 0;
            boolean threeBand = "threeband".equals(d.detailStyle);
            for (int f = from; f <= to; f++) { // the strongest frame since the last message, so no kick is missed
                double[] b = bands(detail, f, threeBand);
                bass = Math.max(bass, b[0]);
                mid = Math.max(mid, b[1]);
                high = Math.max(high, b[2]);
            }
        } else if (d.waveform != null && d.waveform.length >= 4 && d.durationMs > 0) {
            int segs = d.waveform.length / 4;
            int s = (int) Math.min(segs - 1, pos * segs / d.durationMs);
            double[] b = bands(d.waveform, s, "threeband".equals(d.previewStyle));
            bass = b[0];
            mid = b[1];
            high = b[2];
        } else {
            return null;
        }
        if (!d.playing) {
            bass = 0;
            mid = 0;
            high = 0;
        }
        double fader = d.fader == null ? 1 : d.fader;
        bass *= fader * eqGain(d.eqLow);
        mid *= fader * eqGain(d.eqMid);
        high *= fader * eqGain(d.eqHi);
        Map<String, Object> w = new LinkedHashMap<>();
        w.put("t", "wave");
        w.put("deck", d.number);
        w.put("bass", round(clamp(bass), 3));
        w.put("mid", round(clamp(mid), 3));
        w.put("high", round(clamp(high), 3));
        return w;
    }

    /** One waveform entry as bass, mids, highs: three-band entries are the three heights; colour entries are the height
     * split by red (bass), green (mids) and blue (highs), the way rekordbox colours its RGB waveform. */
    static double[] bands(byte[] data, int i, boolean threeBand) {
        int a = data[i * 4] & 0xff;
        int b = data[i * 4 + 1] & 0xff;
        int c = data[i * 4 + 2] & 0xff;
        int e = data[i * 4 + 3] & 0xff;
        if (threeBand) {
            return new double[] {a / 255.0, b / 255.0, c / 255.0};
        }
        double h = a / 255.0;
        return new double[] {h * b / 255.0, h * c / 255.0, h * e / 255.0};
    }

    /** EQ -1 (kill) .. 0 (flat) .. +1 (boost) as a gain for the visuals: a kill takes the band out, a boost adds up to half. */
    static double eqGain(Double eq) {
        if (eq == null) {
            return 1;
        }
        return eq < 0 ? Math.max(0, 1 + eq) : 1 + 0.5 * Math.min(1, eq);
    }

    private Map<String, Object> mixerMessage(List<Deck> decks) {
        List<Map<String, Object>> channels = new ArrayList<>();
        for (Deck d : decks) {
            if (d.fader == null) {
                continue;
            }
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("ch", d.label);
            c.put("deck", d.number);
            c.put("fader", round(d.fader, 3));
            if (d.eqLow != null) {
                Map<String, Object> eq = new LinkedHashMap<>();
                eq.put("low", round(d.eqLow, 3));
                eq.put("mid", round(d.eqMid == null ? 0 : d.eqMid, 3));
                eq.put("hi", round(d.eqHi == null ? 0 : d.eqHi, 3));
                c.put("eq", eq);
            }
            channels.add(c);
        }
        Double xf = hub.crossfader;
        if (channels.isEmpty() && xf == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("t", "mixer");
        if (xf != null) {
            m.put("crossfader", round(xf, 3));
        }
        m.put("channels", channels);
        return m;
    }

    // ------------------------------------------------------------------ helpers

    private List<Deck> decks() {
        List<Deck> list = new ArrayList<>(hub.allDecks());
        list.sort(java.util.Comparator.comparingInt(d -> d.number));
        return list;
    }

    /** The deck the visuals follow: the tempo master; else a playing deck on air; else any playing deck with a track. */
    private Deck master() {
        Deck onAir = null;
        Deck playing = null;
        Deck previous = null;
        for (Deck d : decks()) {
            if (!d.hasTrack) {
                continue;
            }
            if (d.master) {
                return d;
            }
            if (d.playing && d.onAir && onAir == null) {
                onAir = d;
            }
            if (d.playing && playing == null) {
                playing = d;
            }
            if (d.number == masterDeck) {
                previous = d;
            }
        }
        return onAir != null ? onAir : playing != null ? playing : previous;
    }

    /** The position now; inside an active loop the prediction wraps at the loop end instead of running past it. */
    static long position(Deck d, long now) {
        if (!d.hasTrack) {
            return -1;
        }
        long pos = d.predictedPositionMs(now);
        long[] loop = d.loop;
        if (loop != null && pos >= loop[1] && loop[1] > loop[0] && d.positionMs < loop[1]) {
            pos = loop[0] + (pos - loop[1]) % (loop[1] - loop[0]);
        }
        return pos;
    }

    /** Beat position in the grid (0 = first beat, with fraction) at a time in ms. */
    static double beatAt(int[] times, long ms) {
        int n = times.length;
        if (n == 0) {
            return 0;
        }
        if (n == 1 || ms <= times[0]) {
            double len = n > 1 ? times[1] - times[0] : 500;
            return (ms - times[0]) / Math.max(1.0, len);
        }
        int lo = 0;
        int hi = n - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (times[mid] <= ms) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        double len = lo + 1 < n ? times[lo + 1] - times[lo] : times[lo] - times[lo - 1];
        return lo + (ms - times[lo]) / Math.max(1.0, len);
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private static double round(double v, int places) {
        double f = Math.pow(10, places);
        return Math.round(v * f) / f;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object o) {
        return (Map<String, Object>) o;
    }

    private static String write(Map<String, Object> m) {
        try {
            return Hub.JSON.writeValueAsString(m);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private void broadcast(String text) {
        for (WsContext c : clients) {
            send(c, text);
        }
    }

    private static void send(WsContext ctx, String text) {
        try {
            if (ctx.session.isOpen()) {
                ctx.send(text);
            }
        } catch (RuntimeException e) {
            // A broken client must never disturb the others.
        }
    }
}
