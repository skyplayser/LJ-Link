package dev.ljlinkpro;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.websocket.WsContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final Map<Integer, Long> lastPositionSample = new ConcurrentHashMap<>();
    private final Set<WsContext> clients = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "hub-ticker");
        t.setDaemon(true);
        return t;
    });

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
                broadcast(Map.of("type", "deckGone", "deck", d.number));
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

        void setMixer(Map<String, Object> base) {
            mixerBase = base;
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
        Privacy.addListener(this::broadcastState);
        ticker.scheduleAtFixedRate(this::tick, 100, 100, TimeUnit.MILLISECONDS);
        ticker.scheduleAtFixedRate(this::positionTick, 20, 20, TimeUnit.MILLISECONDS);
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

    /** Removes everything: all scopes and decks (the sources were stopped). */
    void reset() {
        for (Integer n : decks.keySet()) {
            broadcast(Map.of("type", "deckGone", "deck", n));
        }
        decks.clear();
        scopes.clear();
        lastSent.clear();
        lastPositionSample.clear();
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

    void addClient(WsContext ctx) {
        clients.add(ctx);
        send(ctx, stateJson());
        send(ctx, mixerJson());
        for (Deck d : decks.values()) {
            send(ctx, deckJson(d));
            send(ctx, positionJson(d));
        }
    }

    void removeClient(WsContext ctx) {
        clients.remove(ctx);
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
        List<Map<String, Object>> groupList = new ArrayList<>(groups.values());
        groupList.sort(java.util.Comparator.comparingInt(g -> (Integer) g.get("order")));
        m.put("groups", groupList);
        return m;
    }

    String stateJson() {
        return write(stateJson0());
    }

    private void broadcastState() {
        broadcast(stateJson0());
    }

    private void broadcast(Map<String, Object> msg) {
        String text = write(msg);
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
            // A broken client must never disturb the others or the monitoring itself.
        }
    }

    private static String write(Map<String, Object> msg) {
        try {
            return JSON.writeValueAsString(msg);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String positionJson(Deck d) {
        return "{\"type\":\"pos\",\"deck\":" + d.number + ",\"p\":" + d.positionMs + ",\"v\":"
                + String.format(java.util.Locale.ROOT, "%.4f", d.velocity) + "}";
    }

    /** Compact position messages at up to 50 Hz, only when a new sample arrived. */
    private void positionTick() {
        try {
            for (Deck d : decks.values()) {
                long sample = d.positionSample;
                Long last = lastPositionSample.put(d.number, sample);
                if (last != null && last == sample) {
                    continue;
                }
                String text = positionJson(d);
                for (WsContext c : clients) {
                    send(c, text);
                }
            }
        } catch (RuntimeException e) {
            System.err.println("Position tick failed: " + e);
        }
    }

    /** Sends decks that changed; playing decks are sent every tick so clients get fresh positions. */
    private void tick() {
        try {
            String st = stateJson();
            if (!st.equals(lastState)) {
                lastState = st;
                for (WsContext c : clients) {
                    send(c, st);
                }
            }
            String mixer = mixerJson();
            if (!mixer.equals(lastMixer)) {
                lastMixer = mixer;
                for (WsContext c : clients) {
                    send(c, mixer);
                }
            }
            for (Deck d : decks.values()) {
                String base = deckJson(d);
                String prev = lastSent.get(d.number);
                if (!base.equals(prev)) {
                    lastSent.put(d.number, base);
                    String text = base;
                    for (WsContext c : clients) {
                        send(c, text);
                    }
                }
            }
        } catch (RuntimeException e) {
            System.err.println("Hub tick failed: " + e);
        }
    }

    /** Overall state, sources and decks for a problem report. No track titles. */
    List<String> diagnostics() {
        List<String> out = new ArrayList<>();
        out.add("Overall phase: " + phase() + ", browsers connected: " + clients.size());
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
