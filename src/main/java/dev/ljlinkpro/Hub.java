package dev.ljlinkpro;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.websocket.WsContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Holds the shared state (phase, devices, decks) and pushes it to all connected browsers. */
final class Hub {
    static final ObjectMapper JSON = new ObjectMapper();

    record DeviceInfo(int number, String name, String address) {}

    private final Map<Integer, Deck> decks = new ConcurrentHashMap<>();
    private final Map<String, Scope> scopes = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicInteger nextDeck = new java.util.concurrent.atomic.AtomicInteger(1);
    private final java.util.concurrent.atomic.AtomicInteger nextScope = new java.util.concurrent.atomic.AtomicInteger();
    private final Map<Integer, String> lastSent = new ConcurrentHashMap<>();
    /** What was last sent as the position of each deck, for sending only changes (plus a keepalive). */
    private final Map<Integer, SentPosition> lastPosition = new ConcurrentHashMap<>();
    /** Connected browsers, each with its own sender (ADR-024). Keyed by the {@link WsContext} (or a test's own key). */
    private final Map<Object, WsClient> clients = new ConcurrentHashMap<>();
    private final ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "hub-ticker");
        t.setDaemon(true);
        return t;
    });
    /** Runs the senders of the browsers: one task per browser at a time, so a slow one only blocks its own thread. */
    private final java.util.concurrent.Executor senders;
    /** Start of the server clock in {@code pos} messages ({@code t}, monotonic milliseconds). */
    private static final long CLOCK_ORIGIN = System.nanoTime();
    /** A deck whose position did not change gets its position again after this long anyway (keepalive). */
    static final long POSITION_KEEPALIVE_NANOS = 1_000_000_000L;
    /** Counts {@code pos} messages, so a browser can drop one that arrives after a newer one. */
    private final java.util.concurrent.atomic.AtomicLong positionSequence = new java.util.concurrent.atomic.AtomicLong();
    private final long stallNanos;
    /** Browsers disconnected because they stopped taking messages, for the diagnostics. */
    private final java.util.concurrent.atomic.AtomicInteger stalledDrops = new java.util.concurrent.atomic.AtomicInteger();

    private record SentPosition(String pv, long atNanos) {}

    /** Told the number of connected browsers whenever it changes (used by {@link SleepGuard}). */
    private volatile java.util.function.IntConsumer clientCountListener = n -> { };
    /** Change of the client set and its report happen together, so reports arrive in order (1 then 0, never swapped). */
    private final Object clientCountLock = new Object();

    /** Crossfader 0 (left) .. 1 (right), or null when no source reports it (only the demo does so far). */
    volatile Double crossfader;
    /** Extra fields for the state message, e.g. the VJ-Link connection; see {@link #setStateExtra}. */
    private final Map<String, Object> stateExtras = new ConcurrentHashMap<>();

    private volatile String lastMixer = "";
    private volatile String lastState = "";

    /**
     * One source of decks (the Pro DJ Link network, one Denon device, the demo). Several scopes live side by side, so
     * devices of different makers can be shown together. A scope keeps its own search state, mixer and decks; the hub
     * merges them for the browsers.
     */
    final class Scope {
        final String key;
        final String kind;
        final int order;
        /** True when every deck is its own device (CDJs); otherwise the whole scope is one device (a Denon controller). */
        final boolean groupPerDeck;
        volatile String groupName;
        volatile DeckSource source;
        volatile String phase = "searching";
        volatile String mode = "live";
        volatile String message = "";
        volatile String note;
        volatile List<DeviceInfo> devices = List.of();
        volatile Map<String, Object> mixerBase = Map.of("present", false);
        private final Map<Integer, Deck> own = new ConcurrentHashMap<>();

        private Scope(String key, String kind, boolean groupPerDeck, String groupName) {
            this.key = key;
            this.kind = kind;
            this.groupPerDeck = groupPerDeck;
            this.groupName = groupName;
            this.order = nextScope.getAndIncrement();
        }

        /** The deck for the device's own number; created when it is first seen. */
        Deck deck(int label) {
            return own.computeIfAbsent(label, l -> {
                Deck d = new Deck(nextDeck.getAndIncrement());
                d.label = l;
                d.scope = this;
                d.source = source;
                decks.put(d.number, d);
                return d;
            });
        }

        /** The deck for this number if it exists; never creates one (for diagnostics, which must not bring decks back). */
        Deck existingDeck(int label) {
            return own.get(label);
        }

        void removeDeck(int label) {
            Deck d = own.remove(label);
            if (d != null) {
                decks.remove(d.number);
                lastSent.remove(d.number);
                lastPosition.remove(d.number);
                broadcastDeckGone(d.number);
            }
        }

        void removeAllDecks() {
            for (Integer l : new ArrayList<>(own.keySet())) {
                removeDeck(l);
            }
        }

        /** The device went away: its decks disappear and it no longer counts for the overall state. */
        void close() {
            removeAllDecks();
            scopes.remove(key);
            broadcastState();
        }

        void setSource(DeckSource source) {
            this.source = source;
        }

        void setCrossfader(Double value) {
            crossfader = value;
        }

        void setMixer(Map<String, Object> base) {
            mixerBase = base;
        }

        /** A quiet note shown in the app while this source is connected (for example ShowKontrol on the network); null removes it. */
        void setNote(String text) {
            String before = note;
            note = text;
            if (!java.util.Objects.equals(before, text)) {
                broadcastState();
            }
        }

        void setState(String phase, String mode, String message) {
            this.phase = phase;
            this.mode = mode;
            this.message = message;
            broadcastState();
        }

        void setMessage(String message) {
            this.message = message;
            broadcastState();
        }

        void setDevices(List<DeviceInfo> devices) {
            this.devices = List.copyOf(devices);
            broadcastState();
        }

        String phase() {
            return phase;
        }
    }

    /** Creates (or returns) the scope with this key. */
    Scope scope(String key, String kind, boolean groupPerDeck, String groupName) {
        return scopes.computeIfAbsent(key, k -> new Scope(k, kind, groupPerDeck, groupName));
    }

    Deck deckById(int number) {
        return decks.get(number);
    }

    private String groupId(Deck d) {
        return d.scope.groupPerDeck ? d.scope.key + ":" + d.label : d.scope.key;
    }

    private String groupName(Deck d) {
        if (!d.scope.groupPerDeck) {
            return d.scope.groupName;
        }
        String n = d.name == null || d.name.isBlank() ? "Player" : d.name;
        return n + " · " + d.label;
    }

    private String deckJson(Deck d) {
        Map<String, Object> m = d.toJson(false);
        m.put("kind", d.scope.kind);
        m.put("group", groupId(d));
        m.put("groupName", groupName(d));
        m.put("order", d.scope.order * 100 + d.label);
        return write(m);
    }

    Hub() {
        this(WsClient.STALL_NANOS);
    }

    /** {@code stallNanos}: how long one send to a browser may block before that browser is disconnected. */
    Hub(long stallNanos) {
        this(stallNanos, true);
    }

    /**
     * {@code ticking}: false for tests that call {@link #positionTick(long)} and {@link #dropStalledClients()} themselves,
     * so no second thread sends at the same time.
     */
    Hub(long stallNanos, boolean ticking) {
        this(stallNanos, ticking, Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "hub-send");
            t.setDaemon(true);
            return t;
        }));
    }

    /** {@code senders} runs the browsers' send tasks; a test may run them at once on the calling thread. */
    Hub(long stallNanos, boolean ticking, java.util.concurrent.Executor senders) {
        this.stallNanos = stallNanos;
        this.senders = senders;
        Privacy.addListener(this::broadcastState);
        if (ticking) {
            ticker.scheduleAtFixedRate(this::tick, 100, 100, TimeUnit.MILLISECONDS);
            ticker.scheduleAtFixedRate(this::positionTick, 20, 20, TimeUnit.MILLISECONDS);
        }
    }

    private String mixerJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "mixer");
        List<Map<String, Object>> mixers = new ArrayList<>();
        for (Scope sc : sortedScopes()) {
            List<Deck> own = new ArrayList<>(sc.own.values());
            if (own.isEmpty() && !Boolean.TRUE.equals(sc.mixerBase.get("present"))) {
                continue;
            }
            Map<String, Object> mixer = new LinkedHashMap<>(sc.mixerBase);
            mixer.put("id", sc.key);
            mixer.put("kind", sc.kind);
            int channels = 4;
            for (Deck d : own) {
                channels = Math.max(channels, d.label);
            }
            List<Map<String, Object>> list = new ArrayList<>();
            for (int ch = 1; ch <= channels; ch++) {
                Deck d = sc.own.get(ch);
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("ch", ch);
                c.put("deck", d != null);
                c.put("number", d == null ? 0 : d.number);
                c.put("title", d == null || Privacy.enabled() ? null : d.title);
                c.put("onAir", d != null && d.onAir);
                c.put("fader", d == null ? null : d.fader);
                c.put("eq", d == null || d.eqHi == null ? null : Map.of("hi", d.eqHi, "mid", d.eqMid, "low", d.eqLow));
                list.add(c);
            }
            mixer.put("channels", list);
            mixers.add(mixer);
        }
        m.put("mixers", mixers);
        return write(m);
    }

    private List<Scope> sortedScopes() {
        List<Scope> list = new ArrayList<>(scopes.values());
        list.sort(java.util.Comparator.comparingInt(sc -> sc.order));
        return list;
    }

    /** How many decks still have a remembered last sent position or deck message (for tests: none after removal). */
    int sentPositionCount() {
        return lastPosition.size() + lastSent.size();
    }

    /** Removes everything: all scopes and decks (the sources were stopped). */
    void reset() {
        for (Integer n : decks.keySet()) {
            broadcastDeckGone(n);
        }
        decks.clear();
        scopes.clear();
        lastSent.clear();
        lastPosition.clear();
        broadcastState();
    }

    /** The overall phase: monitoring as soon as anything is connected, else searching, else an error, else idle. */
    String phase() {
        boolean searching = false;
        boolean error = false;
        for (Scope sc : scopes.values()) {
            switch (sc.phase) {
                case "monitoring" -> {
                    return "monitoring";
                }
                case "searching" -> searching = true;
                case "error" -> error = true;
                default -> { }
            }
        }
        return searching ? "searching" : error ? "error" : "idle";
    }

    /** Sets the idle state after the user stopped everything. */
    void setIdle() {
        broadcastState();
    }

    byte[] waveform(int deckNumber) {
        Deck d = decks.get(deckNumber);
        return d == null ? null : d.waveform;
    }

    java.util.Collection<Deck> allDecks() {
        return decks.values();
    }

    Map<String, Object> beats(int deckNumber) {
        Deck d = decks.get(deckNumber);
        if (d == null || d.beatTimes == null) {
            return null;
        }
        int[] t = d.beatTimes;
        byte[] b = d.beatBars;
        List<Integer> bars = new ArrayList<>();
        for (byte x : b) {
            bars.add((int) x);
        }
        List<Integer> times = new ArrayList<>();
        for (int x : t) {
            times.add(x);
        }
        return Map.of("t", times, "b", bars);
    }

    byte[] art(int deckNumber) {
        Deck d = decks.get(deckNumber);
        return d == null ? null : d.art;
    }

    byte[] detail(int deckNumber) {
        Deck d = decks.get(deckNumber);
        return d == null ? null : d.detail;
    }

    void onClientCount(java.util.function.IntConsumer listener) {
        clientCountListener = listener == null ? n -> { } : listener;
    }

    int clientCount() {
        return clients.size();
    }

    void addClient(WsContext ctx) {
        addClient(ctx, new WsClient.Sink() {
            @Override
            public boolean isOpen() {
                return ctx.session.isOpen();
            }

            @Override
            public void send(String text) {
                ctx.send(text);
            }

            @Override
            public void disconnect() {
                ctx.session.disconnect();
            }
        });
    }

    /** Connects a browser (or a test's fake) under {@code key} and queues the full current state for it. */
    void addClient(Object key, WsClient.Sink sink) {
        WsClient client = new WsClient(sink, senders);
        synchronized (clientCountLock) {
            if (clients.putIfAbsent(key, client) != null) {
                return;
            }
            clientCountListener.accept(clients.size());
        }
        client.offer("state", stateJson());
        client.offer("mixer", mixerJson());
        for (Deck d : decks.values()) {
            client.offerDeck("deck", d.number, deckJson(d));
            client.offerDeck("pos", d.number, positionJson(d, System.nanoTime()));
        }
    }

    void removeClient(Object key) {
        WsClient gone;
        synchronized (clientCountLock) {
            gone = clients.remove(key);
            if (gone != null) {
                clientCountListener.accept(clients.size());
            }
        }
        if (gone != null) {
            gone.close();
        }
    }

    /** Disconnects browsers whose send has been blocked too long (asleep, gone); they reconnect by themselves. */
    void dropStalledClients() {
        long now = System.nanoTime();
        for (Map.Entry<Object, WsClient> e : clients.entrySet()) {
            if (e.getValue().stalled(now, stallNanos)) {
                System.err.println("A browser did not take messages for " + stallNanos / 1_000_000 + " ms; disconnecting it (it reconnects by itself).");
                e.getValue().kill();
                stalledDrops.incrementAndGet();
                removeClient(e.getKey());
            }
        }
    }

    private Map<String, Object> stateJson0() {
        Map<String, Object> m = new LinkedHashMap<>();
        String phase = phase();
        String mode = "";
        String message = "";
        List<Map<String, Object>> list = new ArrayList<>();
        java.util.Map<String, Map<String, Object>> groups = new java.util.TreeMap<>();
        for (Scope sc : sortedScopes()) {
            if (mode.isEmpty() || "searching".equals(sc.phase) && message.isEmpty()) {
                mode = sc.mode;
            }
            if (sc.phase.equals(phase) && message.isEmpty()) {
                message = sc.message; // the first scope with a message: Pioneer before Denon
            }
            for (DeviceInfo d : sc.devices) {
                list.add(Map.of("number", d.number(), "name", d.name(), "address", d.address()));
            }
        }
        for (Deck d : decks.values()) {
            String id = groupId(d);
            groups.putIfAbsent(id, new LinkedHashMap<>(Map.of("id", id, "name", groupName(d), "order", d.scope.order * 100 + d.label)));
        }
        m.put("type", "state");
        m.put("phase", phase);
        m.put("mode", mode);
        m.put("message", message);
        m.put("privacy", Privacy.enabled());
        m.put("devices", list);
        // Problems of one source while another one works (for example Pioneer fails while a Denon is shown).
        List<String> warnings = new ArrayList<>();
        for (Scope sc : sortedScopes()) {
            boolean problem = "error".equals(sc.phase) || "monitoring".equals(sc.phase) && sc.message.startsWith("Beat port");
            if (problem && !sc.message.isBlank()) {
                warnings.add(sc.message);
            }
        }
        m.put("warnings", warnings);
        // While still searching, a source that failed (Pioneer, while Denon is still searched) must not go unnoticed.
        List<String> searchWarnings = new ArrayList<>();
        for (Scope sc : sortedScopes()) {
            if ("searching".equals(phase) && "error".equals(sc.phase) && !sc.message.isBlank()) {
                searchWarnings.add(sc.message);
            }
        }
        m.put("searchWarnings", searchWarnings);
        List<String> notes = new ArrayList<>();
        for (Scope sc : sortedScopes()) {
            if (sc.note != null && !sc.note.isBlank()) {
                notes.add(sc.note);
            }
        }
        m.put("notes", notes);
        List<Map<String, Object>> groupList = new ArrayList<>(groups.values());
        groupList.sort(java.util.Comparator.comparingInt(g -> (Integer) g.get("order")));
        m.put("groups", groupList);
        m.putAll(stateExtras);
        return m;
    }

    /** Sets (or with null removes) an extra field of the state message and sends the state to the browsers. */
    void setStateExtra(String key, Object value) {
        if (value == null) {
            stateExtras.remove(key);
        } else {
            stateExtras.put(key, value);
        }
        broadcastState();
    }

    String stateJson() {
        return write(stateJson0());
    }

    private void broadcastState() {
        String text = write(stateJson0());
        for (WsClient c : clients.values()) {
            c.offer("state", text);
        }
    }

    private void broadcastDeckGone(int deck) {
        String text = write(Map.of("type", "deckGone", "deck", deck));
        for (WsClient c : clients.values()) {
            c.deckGone(deck, text);
        }
    }

    private static String write(Map<String, Object> msg) {
        try {
            return JSON.writeValueAsString(msg);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Position and speed as sent, the part that tells whether anything changed. */
    private static String positionValues(Deck.PositionSnapshot s) {
        return "\"p\":" + s.ms() + ",\"v\":" + String.format(java.util.Locale.ROOT, "%.4f", s.velocity());
    }

    /**
     * {@code {"type":"pos","deck":n,"p":ms,"v":speed,"s":sequence,"t":serverMs,"a":ageMs}}: {@code t} is the server clock
     * (monotonic ms) when the message was made, {@code a} how old the sample {@code p} was then. The browser estimates
     * its offset to the server clock from {@code t} and so knows when {@code p} was valid, even if the message was late.
     */
    private String positionJson(Deck d, long nowNanos) {
        return positionJson(d.number, d.positionSnapshot(), nowNanos);
    }

    private String positionJson(int deck, Deck.PositionSnapshot s, long nowNanos) {
        // Never sampled (a deck without a position): the age is meaningless, keep the number small.
        long age = s.atNanos() == 0 ? 0 : Math.max(0, Math.min(60_000, (nowNanos - s.atNanos()) / 1_000_000));
        return "{\"type\":\"pos\",\"deck\":" + deck + "," + positionValues(s) + ",\"s\":" + positionSequence.incrementAndGet()
                + ",\"t\":" + serverMillis(nowNanos) + ",\"a\":" + age + "}";
    }

    static long serverMillis(long nanos) {
        return (nanos - CLOCK_ORIGIN) / 1_000_000;
    }

    /**
     * Compact position messages, checked every 20 ms: sent when a new sample brought another position or speed, and
     * otherwise once per {@link #POSITION_KEEPALIVE_NANOS} (a paused deck no longer gets 50 identical messages a second).
     */
    void positionTick() {
        positionTick(System.nanoTime());
    }

    /** {@link #positionTick()} at the given time ({@link System#nanoTime()} scale); tests pass their own. */
    void positionTick(long now) {
        try {
            for (Deck d : decks.values()) {
                Deck.PositionSnapshot s = d.positionSnapshot();
                SentPosition last = lastPosition.get(d.number);
                String pv = positionValues(s);
                boolean due = last == null || now - last.atNanos() >= POSITION_KEEPALIVE_NANOS;
                boolean changed = last == null || !last.pv().equals(pv);
                if (!due && !changed) {
                    continue;
                }
                lastPosition.put(d.number, new SentPosition(pv, now));
                if (!decks.containsKey(d.number)) {
                    // Removed meanwhile (removeDeck takes it out of decks first, then out of lastPosition): no leftover.
                    lastPosition.remove(d.number);
                    continue;
                }
                String text = positionJson(d.number, s, now);
                for (WsClient c : clients.values()) {
                    c.offerDeck("pos", d.number, text);
                }
            }
        } catch (RuntimeException e) {
            System.err.println("Position tick failed: " + e);
        }
    }

    /** Sends decks that changed; playing decks are sent every tick so clients get fresh positions. */
    private void tick() {
        try {
            dropStalledClients();
            String st = stateJson();
            if (!st.equals(lastState)) {
                lastState = st;
                for (WsClient c : clients.values()) {
                    c.offer("state", st);
                }
            }
            String mixer = mixerJson();
            if (!mixer.equals(lastMixer)) {
                lastMixer = mixer;
                for (WsClient c : clients.values()) {
                    c.offer("mixer", mixer);
                }
            }
            for (Deck d : decks.values()) {
                String base = deckJson(d);
                String prev = lastSent.get(d.number);
                if (!base.equals(prev)) {
                    lastSent.put(d.number, base);
                    if (!decks.containsKey(d.number)) {
                        lastSent.remove(d.number); // removed meanwhile, as in positionTick
                        continue;
                    }
                    for (WsClient c : clients.values()) {
                        c.offerDeck("deck", d.number, base);
                    }
                }
            }
        } catch (RuntimeException e) {
            System.err.println("Hub tick failed: " + e);
        }
    }

    /**
     * One short line per source for the summary of a problem report: state, message, decks, mixer. {@code active}: the
     * sources that found something or failed; otherwise the ones still searching or idle with nothing (they go last).
     */
    List<String> summarySources(boolean active) {
        List<String> out = new ArrayList<>();
        for (Scope sc : sortedScopes()) {
            boolean found = !sc.own.isEmpty() || Boolean.TRUE.equals(sc.mixerBase.get("present"))
                    || "monitoring".equals(sc.phase) || "error".equals(sc.phase);
            if (found != active) {
                continue;
            }
            String msg = sc.message.length() > 90 ? sc.message.substring(0, 89) + "…" : sc.message;
            Object packets = sc.mixerBase.get("statusPackets");
            out.add("Source " + sc.key + ": " + sc.phase + (msg.isBlank() ? "" : " \"" + msg + "\"") + ", decks " + sc.own.size()
                    + ", mixer " + (Boolean.TRUE.equals(sc.mixerBase.get("present")) ? sc.mixerBase.get("name") : "none")
                    + (packets == null ? "" : " (mixer status " + packets + ")"));
        }
        return out;
    }

    /** One short line per deck for the summary of a problem report: what arrived and what is missing. No track titles. */
    List<String> summaryDecks() {
        List<Deck> list = new ArrayList<>(decks.values());
        list.sort(java.util.Comparator.comparingInt(d -> d.number));
        List<String> out = new ArrayList<>();
        for (Deck d : list) {
            out.add("Deck " + d.number + " " + d.name + ": " + (d.hasTrack ? "track" : "no track") + (d.playing ? ", playing" : "")
                    + String.format(java.util.Locale.ROOT, ", %.2f BPM", d.effectiveBpm)
                    + (d.title != null && !d.title.isBlank() ? ", title known" : ", no title")
                    + (d.waveform != null ? ", waveform" : ", no waveform") + (d.detail != null ? ", detail" : ", no detail")
                    + ", grid " + (d.beatTimes == null ? 0 : d.beatTimes.length) + (d.art != null ? ", art" : "")
                    + (d.noWaveformSource ? ", NO WAVEFORM SOURCE" : "") + (d.metaHint == null ? "" : ", metadata hint " + d.metaHint));
        }
        return out;
    }

    /** Overall state, sources and decks for a problem report. No track titles. */
    List<String> diagnostics() {
        List<String> out = new ArrayList<>();
        out.add("Overall phase: " + phase() + ", browsers connected: " + clients.size()
                + ", browsers dropped as stalled: " + stalledDrops.get());
        for (Scope sc : sortedScopes()) {
            out.add("Source " + sc.key + " (" + sc.kind + "): " + sc.phase + (sc.message.isBlank() ? "" : ", \"" + sc.message + "\"")
                    + ", decks " + sc.own.size() + ", mixer " + (Boolean.TRUE.equals(sc.mixerBase.get("present")) ? sc.mixerBase.get("name") : "none"));
            for (DeviceInfo d : sc.devices) {
                out.add("  device " + d.number() + " " + d.name() + " " + d.address());
            }
        }
        List<Deck> list = new ArrayList<>(decks.values());
        list.sort(java.util.Comparator.comparingInt(d -> d.number));
        for (Deck d : list) {
            out.add("Deck " + d.number + " (" + (d.scope == null ? "?" : d.scope.kind) + " " + d.label + ", " + d.name + "): "
                    + "track " + d.hasTrack + ", playing " + d.playing + ", on air " + d.onAir + ", master " + d.master
                    + ", BPM " + String.format(java.util.Locale.ROOT, "%.2f", d.effectiveBpm)
                    + ", sent tempo " + String.format(java.util.Locale.ROOT, "%.1f", d.sentBpm()) + " (" + d.stableChanges() + " changes)"
                    + ", synced " + d.synced
                    + ", length " + d.durationMs + " ms, position " + d.positionMs + " ms"
                    + ", waveform " + (d.waveform != null) + ", detail " + (d.detail != null)
                    + ", beat grid " + (d.beatTimes == null ? 0 : d.beatTimes.length) + ", cues " + d.cues.size()
                    + ", art " + (d.art != null) + ", title known " + (d.title != null && !d.title.isBlank())
                    + (d.noWaveformSource ? ", NO WAVEFORM SOURCE" : "") + (d.coarsePosition ? ", coarse position" : ""));
        }
        return out;
    }
}
