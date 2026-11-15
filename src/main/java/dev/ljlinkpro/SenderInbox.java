package dev.ljlinkpro;

import com.fasterxml.jackson.databind.JsonNode;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WriteCallback;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * The input for LJL Link (Alpha, sender protocol version 1): a small app on the DJ's computer that reports which track is on which Serato deck, its length and waveforms, over the WebSocket
 * {@code /sender}. It is reachable from the network, but only a sender with the current club key ({@link SenderClub}) is
 * accepted, and everything it sends is read only as deck data: nothing in LJ Link, on the players or on the light desks
 * can be controlled this way.
 * <ul>
 * <li>{@code hello} → {@code welcome} or {@code reject} (key | protocol | full, then closed); deck messages before the
 * welcome are ignored.</li>
 * <li>Each sender gets its own {@link Hub.Scope} ({@code sender-s1} …, kind {@value #KIND}), named after the sender, with
 * decks 1-{@value #MAX_DECKS}; it survives "Search" and "Stop", does not count for the phase of the search, and goes away
 * with the sender.</li>
 * <li>{@code names: false}: titles and artists are never stored; the decks say {@code titleHidden}.</li>
 * <li>Limits: {@value #MAX_SENDERS} senders, 2 MB per message (64 KB before the welcome), names of {@value #MAX_NAME}
 * characters. Silent for 15 s: the sender is gone.</li>
 * <li>Connections without a hello: at most {@value #MAX_PENDING_PER_ADDRESS} per address and {@value #MAX_PENDING} in
 * all, closed after 3 s; a new connection always gets a place, the oldest waiting one (of the same address first) makes
 * room, so nobody without the key can keep a real sender out.</li>
 * <li>Refusals are logged at most once a minute per reason, then counted.</li>
 * </ul>
 */
final class SenderInbox {
    static final int PROTOCOL = 1;
    static final String KIND = "sender";
    static final int MAX_SENDERS = 4;
    static final int MAX_DECKS = 4;
    /** Connections that have not (yet) been welcomed; when all are taken, the oldest makes room for a new one. */
    static final int MAX_PENDING = 8;
    /** Connections without a welcome from one address; more from it are closed at once. */
    static final int MAX_PENDING_PER_ADDRESS = 2;
    /** A sender says hello right after connecting; a connection without one is closed after this long. */
    static final long HELLO_TIMEOUT_NANOS = 3_000_000_000L;
    /** A refusal of the same reason is logged at most once in this time; the others are counted. */
    static final long LOG_EVERY_NANOS = 60_000_000_000L;
    static final int MAX_MESSAGE_BYTES = 2 * 1024 * 1024;
    /** Before the welcome a message is a hello, which is tiny. */
    static final int PENDING_MESSAGE_BYTES = 64 * 1024;
    static final int MAX_NAME = 64;
    static final int MAX_TEXT = 200;
    static final int MAX_ID = 64;
    static final long TIMEOUT_NANOS = 15_000_000_000L;
    /** The browser draws detail waveforms with 150 entries per second (FRAMES_PER_SECOND in app.js). */
    static final int DETAIL_FPS = 150;
    static final long MAX_LENGTH_MS = 24L * 3600 * 1000;
    static final String DEFAULT_NAME = "LJL Link";

    /** Close codes (RFC 6455). */
    static final int CLOSE_NORMAL = 1000;
    static final int CLOSE_GOING_AWAY = 1001;
    static final int CLOSE_POLICY = 1008;
    static final int CLOSE_TOO_LARGE = 1009;

    /** The connection to one sender, as the inbox needs it; a real WebSocket session or a test's fake. */
    interface Peer {
        void send(String text);

        void close(int code, String reason);

        /** After the welcome the transport may take messages up to {@link #MAX_MESSAGE_BYTES}. */
        default void allowLargeMessages() { }
    }

    private static final class Conn {
        final Peer peer;
        final String address;
        final long openedAt;
        long lastSeen;
        boolean welcomed;
        String id;
        String name;
        String software;
        /** Name of this sender's decks in the browser (the software, numbered if two senders run the same). */
        String groupName;
        String app;
        boolean names;
        Hub.Scope scope;
        /** The track id the sender gave for each deck with a track. */
        final Map<Integer, String> tracks = new HashMap<>();

        Conn(Peer peer, String address, long now) {
            this.peer = peer;
            this.address = address == null ? "" : address;
            this.openedAt = now;
            this.lastSeen = now;
        }
    }

    private final Hub hub;
    private final Supplier<String> clubKey;
    private final Supplier<String> clubName;
    /** Guarded by this; keyed by the WebSocket session (or a test's own key). */
    private final Map<Object, Conn> conns = new LinkedHashMap<>();
    private ScheduledExecutorService sweeper;
    private final java.util.function.Consumer<String> log;
    /** Guarded by this: per refusal reason, when it was last logged and how many were left out since. */
    private final Map<String, long[]> refusals = new HashMap<>();

    SenderInbox(Hub hub) {
        this(hub, SenderClub::key, SenderClub::name, System.out::println);
    }

    SenderInbox(Hub hub, Supplier<String> clubKey, Supplier<String> clubName) {
        this(hub, clubKey, clubName, System.out::println);
    }

    SenderInbox(Hub hub, Supplier<String> clubKey, Supplier<String> clubName, java.util.function.Consumer<String> log) {
        this.hub = hub;
        this.clubKey = clubKey;
        this.clubName = clubName;
        this.log = log;
    }

    /** Checks twice a second for senders that went silent and connections without a hello. */
    void start() {
        sweeper = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sender-sweep");
            t.setDaemon(true);
            return t;
        });
        sweeper.scheduleAtFixedRate(() -> {
            try {
                sweep(System.nanoTime());
            } catch (RuntimeException e) {
                System.err.println("Sender check failed: " + e);
            }
        }, 500, 500, TimeUnit.MILLISECONDS);
    }

    // ------------------------------------------------------------------ WebSocket

    /** Wires the {@code /sender} endpoint of a Javalin app to {@code inbox}. */
    static void wire(io.javalin.websocket.WsConfig ws, SenderInbox inbox) {
        ws.onConnect(ctx -> {
            ctx.session.setMaxTextMessageSize(PENDING_MESSAGE_BYTES);
            inbox.open(ctx.session, peer(ctx.session), address(ctx.session), System.nanoTime());
        });
        ws.onMessage(ctx -> inbox.message(ctx.session, ctx.message(), System.nanoTime()));
        // A message over the limit makes Jetty close the connection (1009); that ends here, too.
        ws.onClose(ctx -> inbox.closed(ctx.session));
        ws.onError(ctx -> inbox.closed(ctx.session));
    }

    private static Peer peer(Session s) {
        return new Peer() {
            @Override
            public void send(String text) {
                if (s.isOpen()) {
                    s.getRemote().sendString(text, WriteCallback.NOOP); // never blocks the inbox on a slow network
                }
            }

            @Override
            public void close(int code, String reason) {
                s.close(code, reason);
            }

            @Override
            public void allowLargeMessages() {
                s.setMaxTextMessageSize(MAX_MESSAGE_BYTES);
            }
        };
    }

    private static String address(Session s) {
        return s.getRemoteAddress() instanceof java.net.InetSocketAddress a && a.getAddress() != null
                ? a.getAddress().getHostAddress() : "";
    }

    // ------------------------------------------------------------------ connections

    synchronized void open(Object handle, Peer peer, String address, long now) {
        String from = address == null ? "" : address;
        int pending = 0;
        int fromAddress = 0;
        Map.Entry<Object, Conn> oldest = null;
        Map.Entry<Object, Conn> oldestFromAddress = null;
        for (Map.Entry<Object, Conn> e : conns.entrySet()) {
            Conn c = e.getValue();
            if (c.welcomed) {
                continue;
            }
            pending++;
            if (c.address.equals(from)) {
                fromAddress++;
                if (oldestFromAddress == null || c.openedAt - oldestFromAddress.getValue().openedAt < 0) {
                    oldestFromAddress = e;
                }
            }
            if (oldest == null || c.openedAt - oldest.getValue().openedAt < 0) {
                oldest = e;
            }
        }
        if (fromAddress >= MAX_PENDING_PER_ADDRESS && oldestFromAddress != null) {
            // One address holds at most this many places; its oldest waiting connection makes room (a sender that
            // reconnects quickly is never kept out by its own earlier attempts, and another address is not touched).
            refused("busy", from, now);
            drop(oldestFromAddress.getKey(), oldestFromAddress.getValue(), CLOSE_POLICY, "busy");
            pending--;
            if (oldest == oldestFromAddress) {
                oldest = null;
            }
        }
        if (pending >= MAX_PENDING && oldest != null) {
            // Room for the newcomer: a real sender says hello at once, a connection that keeps a place without one goes.
            refused("busy", oldest.getValue().address, now);
            drop(oldest.getKey(), oldest.getValue(), CLOSE_POLICY, "busy");
        }
        conns.put(handle, new Conn(peer, from, now));
    }

    /**
     * Logs a refusal at most once per {@link #LOG_EVERY_NANOS} and reason; the ones left out are counted and named with
     * the next line (or by {@link #sweep} once the minute is over), so a flood cannot push everything else out of the log.
     */
    private void refused(String reason, String address, long now) {
        long[] r = refusals.get(reason);
        if (r != null && now - r[0] < LOG_EVERY_NANOS) {
            r[1]++;
            return;
        }
        long left = r == null ? 0 : r[1];
        refusals.put(reason, new long[] {now, 0});
        log.accept("LJL Link from " + (address.isEmpty() ? "unknown address" : address) + " refused: " + reason
                + (left > 0 ? " (" + left + " more refused for this reason in the last minute)" : ""));
    }

    /** Names the refusals still counted once their minute is over. */
    private void flushRefusals(long now) {
        for (Map.Entry<String, long[]> e : refusals.entrySet()) {
            long[] r = e.getValue();
            if (r[1] > 0 && now - r[0] >= LOG_EVERY_NANOS) {
                log.accept("LJL Link: " + r[1] + " more refused (" + e.getKey() + ") in the last minute");
                r[0] = now;
                r[1] = 0;
            }
        }
    }

    /** The connection closed (by the sender, the network or Jetty): the sender's decks go away. */
    synchronized void closed(Object handle) {
        Conn c = conns.remove(handle);
        if (c != null && c.welcomed) {
            log.accept("LJL Link " + c.id + " disconnected");
            c.scope.close();
        }
    }

    /** Ends a connection from this side: its decks go away, then the socket is closed. */
    private void drop(Object handle, Conn c, int code, String reason) {
        conns.remove(handle);
        if (c.welcomed) {
            c.scope.close();
        }
        try {
            c.peer.close(code, reason);
        } catch (RuntimeException e) {
            // already gone
        }
    }

    /**
     * Senders silent for {@link #TIMEOUT_NANOS} are dropped with their decks; connections without a hello after
     * {@link #HELLO_TIMEOUT_NANOS}.
     */
    synchronized void sweep(long now) {
        for (Map.Entry<Object, Conn> e : new ArrayList<>(conns.entrySet())) {
            Conn c = e.getValue();
            if (c.welcomed ? now - c.lastSeen > TIMEOUT_NANOS : now - c.openedAt > HELLO_TIMEOUT_NANOS) {
                if (c.welcomed) {
                    log.accept("LJL Link " + c.id + " silent for 15 s; its decks are removed");
                }
                drop(e.getKey(), c, CLOSE_GOING_AWAY, "timeout");
            }
        }
        flushRefusals(now);
    }

    /**
     * The club key was replaced: every sender is told {@code reject key} and disconnected, so a lost club.json stops
     * working at once, not only on its next connection.
     */
    synchronized void disconnectAll() {
        for (Map.Entry<Object, Conn> e : new ArrayList<>(conns.entrySet())) {
            try {
                e.getValue().peer.send(reject("key"));
            } catch (RuntimeException ex) {
                // closing anyway
            }
            drop(e.getKey(), e.getValue(), CLOSE_POLICY, "key");
        }
    }

    /** The connected senders, for the local API: no titles, no key. */
    synchronized List<Map<String, Object>> senders() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Conn c : conns.values()) {
            if (!c.welcomed) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.id);
            m.put("name", c.name);
            m.put("address", c.address);
            m.put("software", c.software);
            m.put("app", c.app);
            m.put("names", c.names);
            m.put("decks", c.tracks.size());
            out.add(m);
        }
        return out;
    }

    /** Lines for a problem report: no names of DJs, no titles, no key. */
    synchronized List<String> diagnostics() {
        List<String> out = new ArrayList<>();
        int pending = 0;
        for (Conn c : conns.values()) {
            if (c.welcomed) {
                out.add("Sender " + c.id + " from " + c.address + ": " + describe(c) + ", titles "
                        + (c.names ? "allowed" : "hidden by the DJ") + ", decks with a track " + c.tracks.keySet());
            } else {
                pending++;
            }
        }
        if (pending > 0) {
            out.add("Connections without hello: " + pending);
        }
        if (out.isEmpty()) {
            out.add("No LJL Link connected");
        }
        return out;
    }

    // ------------------------------------------------------------------ messages

    synchronized void message(Object handle, String text, long now) {
        Conn c = conns.get(handle);
        if (c == null || text == null) {
            return;
        }
        // Jetty already refuses longer messages (in bytes); this also covers other transports and tests.
        int limit = c.welcomed ? MAX_MESSAGE_BYTES : PENDING_MESSAGE_BYTES;
        if (text.length() > limit || text.getBytes(StandardCharsets.UTF_8).length > limit) {
            drop(handle, c, CLOSE_TOO_LARGE, "too large");
            return;
        }
        JsonNode m;
        try {
            m = Hub.JSON.readTree(text);
        } catch (java.io.IOException | RuntimeException e) {
            return; // broken: ignored, like an unknown type
        }
        if (m == null || !m.isObject()) {
            return;
        }
        String type = m.path("type").asText("");
        if (!c.welcomed) {
            if ("hello".equals(type)) {
                hello(handle, c, m, now);
            }
            return;
        }
        c.lastSeen = now;
        switch (type) {
            case "load" -> load(c, m);
            case "waveform" -> waveform(c, m);
            case "position" -> position(c, m);
            case "unload" -> unload(c, m);
            case "names" -> names(c, m);
            case "ping" -> c.peer.send("{\"type\":\"pong\"}");
            case "bye" -> {
                log.accept("LJL Link " + c.id + " quit");
                drop(handle, c, CLOSE_NORMAL, "bye");
            }
            default -> { } // unknown types are ignored (protocol: additive changes keep version 1)
        }
    }

    private void hello(Object handle, Conn c, JsonNode m, long now) {
        JsonNode protocol = m.path("protocol");
        String reason = null;
        if (!protocol.isIntegralNumber() || protocol.asInt() != PROTOCOL) {
            reason = "protocol";
        } else if (!m.path("key").isTextual() || !SenderClub.matches(clubKey.get(), m.path("key").asText())) {
            reason = "key";
        } else if (welcomedCount() >= MAX_SENDERS) {
            reason = "full";
        }
        if (reason != null) {
            refused(reason, c.address, now);
            c.peer.send(reject(reason));
            drop(handle, c, CLOSE_POLICY, reason);
            return;
        }
        c.id = freeId();
        String name = SenderClub.clean(m.path("name").asText(""), MAX_NAME);
        c.name = name.isEmpty() ? DEFAULT_NAME : name;
        c.software = SenderClub.clean(m.path("software").asText(""), MAX_NAME);
        c.app = SenderClub.clean(m.path("app").asText(""), 20);
        // Without a clear "yes" there are no titles.
        c.names = m.path("names").isBoolean() && m.path("names").asBoolean();
        c.welcomed = true;
        c.lastSeen = now;
        // Shown on every deck of this sender: the DJ software, not the computer's name (personal, and long names were
        // cut off on the deck). A second sender with the same software gets a number, so a DJ handover stays clear.
        String shown = c.software.isEmpty() ? DEFAULT_NAME : c.software;
        int same = 0;
        for (Conn o : conns.values()) if (o != c && o.welcomed && shown.equals(o.groupName)) same++;
        c.groupName = same == 0 ? shown : shown + " " + (same + 1);
        c.scope = hub.persistentScope("sender-" + c.id, KIND, false, c.groupName);
        c.scope.setState("monitoring", "live", "Connected");
        Map<String, Object> w = new LinkedHashMap<>();
        w.put("type", "welcome");
        w.put("protocol", PROTOCOL);
        w.put("club", clubName.get());
        w.put("sender", c.id);
        c.peer.allowLargeMessages();
        c.peer.send(write(w));
        // No DJ name in the log: it ends up in problem reports.
        log.accept("LJL Link " + c.id + " connected from " + c.address + " (" + describe(c) + ", titles "
                + (c.names ? "allowed" : "hidden") + ")");
    }

    /** "Serato DJ Pro 5.0.0, app 0.1.0"; parts the sender left out are named as unknown or left away. */
    private static String describe(Conn c) {
        return (c.software.isEmpty() ? "software not given" : c.software) + (c.app.isEmpty() ? "" : ", app " + c.app);
    }

    private int welcomedCount() {
        int n = 0;
        for (Conn c : conns.values()) {
            if (c.welcomed) {
                n++;
            }
        }
        return n;
    }

    private String freeId() {
        for (int i = 1; ; i++) {
            String id = "s" + i;
            boolean used = false;
            for (Conn c : conns.values()) {
                if (id.equals(c.id)) {
                    used = true;
                    break;
                }
            }
            if (!used) {
                return id;
            }
        }
    }

    /** The deck number 1-4 of a deck message, or 0 when it is missing or out of range. */
    private static int deckNumber(JsonNode m) {
        JsonNode n = m.path("deck");
        if (!n.isIntegralNumber()) {
            return 0;
        }
        int deck = n.asInt();
        return deck >= 1 && deck <= MAX_DECKS ? deck : 0;
    }

    private static String trackId(JsonNode m) {
        JsonNode n = m.path("id");
        if (!n.isTextual()) {
            return null;
        }
        String id = n.asText();
        return id.isEmpty() || id.length() > MAX_ID ? null : id;
    }

    /** The deck if {@code id} is the track the sender last loaded on it; otherwise null (a late message of an old track). */
    private static Deck current(Conn c, int n, JsonNode m) {
        String id = trackId(m);
        if (n == 0 || id == null || !id.equals(c.tracks.get(n))) {
            return null;
        }
        return c.scope.existingDeck(n);
    }

    private void load(Conn c, JsonNode m) {
        int n = deckNumber(m);
        String id = trackId(m);
        if (n == 0 || id == null) {
            return;
        }
        Deck d = c.scope.deck(n);
        // The same track again (the DJ switched titles back on, the sender sends its loads anew): only the tags change.
        // Clearing it dropped the waveforms and the playhead of every deck.
        boolean same = id.equals(c.tracks.get(n)) && d.hasTrack;
        c.tracks.put(n, id);
        if (!same) {
            d.clearTrack();
            d.samplePosition(-1);
            d.trackVersion++;
            d.playing = false;
            d.speed = 1.0;
        }
        d.exactTempo = true; // the BPM of the file: no pitch, no jitter (like the local Serato source)
        // The DJ software, like the local Serato source; the computer's name (personal) is only the group name, which
        // the problem report leaves out.
        d.name = c.software.isEmpty() ? DEFAULT_NAME : c.software;
        d.address = c.address;
        d.hasTrack = true;
        // Positions from Serato's display have 0.1 s steps: the browser smooths them like an NXS2's.
        d.coarsePosition = true;
        JsonNode length = m.path("lengthMs");
        d.durationMs = length.isNumber() ? Math.max(0, Math.min(MAX_LENGTH_MS, length.asLong())) : 0;
        JsonNode bpm = m.path("bpm");
        double tempo = bpm.isNumber() ? bpm.asDouble() : 0;
        tempo = tempo > 0 && tempo < 1000 ? tempo : 0;
        d.trackBpm = tempo;
        d.effectiveBpm = tempo;
        String key = SenderClub.clean(m.path("key").isTextual() ? m.path("key").asText() : null, 8);
        d.key = key.isEmpty() ? null : key;
        if (c.names) {
            d.title = text(m.path("title"));
            d.artist = text(m.path("artist"));
        }
        d.titleHidden = !c.names;
        d.previewStyle = "threeband";
        d.detailStyle = "threeband";
        d.noWaveformSource = false; // the sender computes the waveforms and sends them a moment later
    }

    private static String text(JsonNode n) {
        if (!n.isTextual()) {
            return null;
        }
        String s = SenderClub.clean(n.asText(), MAX_TEXT);
        return s.isEmpty() ? null : s;
    }

    private void waveform(Conn c, JsonNode m) {
        Deck d = current(c, deckNumber(m), m);
        if (d == null) {
            return;
        }
        String kind = m.path("kind").asText("");
        boolean detail = "detail".equals(kind);
        if (!detail && !"overview".equals(kind)) {
            return;
        }
        if (detail && m.has("fps") && m.path("fps").asInt(-1) != DETAIL_FPS) {
            return; // the browser can only draw 150 entries per second
        }
        JsonNode entries = m.path("entries");
        if (!entries.isIntegralNumber() || entries.asLong() <= 0 || !m.path("data").isTextual()) {
            return;
        }
        byte[] data;
        try {
            data = Base64.getDecoder().decode(m.path("data").asText());
        } catch (IllegalArgumentException e) {
            return;
        }
        if (entries.asLong() * 4 != data.length) {
            return;
        }
        if (detail) {
            d.detailStyle = "threeband";
            d.setDetail(data);
        } else {
            d.previewStyle = "threeband";
            d.setWaveform(data);
        }
    }

    /** Later phase of the sender (Alpha): the playhead, as read from Serato's screen or estimated. */
    private void position(Conn c, JsonNode m) {
        Deck d = current(c, deckNumber(m), m);
        JsonNode ms = m.path("ms");
        if (d != null && ms.isNull()) {
            // The sender cannot see this deck's time any more (e.g. Serato's 2-deck view hides decks 3/4): no playhead
            // instead of one that runs on blindly.
            d.playing = false;
            d.samplePosition(-1);
            return;
        }
        if (d == null || !ms.isNumber()) {
            return;
        }
        long pos = Math.max(0, ms.asLong());
        if (d.durationMs > 0) {
            pos = Math.min(pos, d.durationMs);
        }
        d.playing = m.path("playing").isBoolean() && m.path("playing").asBoolean();
        // The sender's measured speed (pitch); without it the file's own speed.
        JsonNode sp = m.path("speed");
        double speed = sp.isNumber() ? Math.max(0.5, Math.min(1.5, sp.asDouble())) : 1.0;
        d.samplePositionWithSpeed(pos, d.playing ? speed : 0);
    }

    private void unload(Conn c, JsonNode m) {
        int n = deckNumber(m);
        if (n == 0) {
            return;
        }
        c.tracks.remove(n);
        Deck d = c.scope.existingDeck(n);
        if (d != null) {
            d.clearTrack();
            d.samplePosition(-1);
            d.hasTrack = false;
            d.playing = false;
            d.effectiveBpm = 0;
            d.titleHidden = false;
            d.noWaveformSource = false;
        }
    }

    /** The DJ switched track titles on or off; off removes them from every deck of this sender at once. */
    private void names(Conn c, JsonNode m) {
        JsonNode on = m.path("on");
        if (!on.isBoolean()) {
            return;
        }
        c.names = on.asBoolean();
        if (!c.names) {
            for (int n = 1; n <= MAX_DECKS; n++) {
                Deck d = c.scope.existingDeck(n);
                if (d != null) {
                    d.title = null;
                    d.artist = null;
                    d.titleHidden = d.hasTrack;
                }
            }
        }
        // Switched on: the titles come with the next track (the sender does not repeat its loads).
    }

    static String reject(String reason) {
        return "{\"type\":\"reject\",\"reason\":\"" + reason + "\"}";
    }

    private static String write(Map<String, Object> m) {
        try {
            return Hub.JSON.writeValueAsString(m);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
