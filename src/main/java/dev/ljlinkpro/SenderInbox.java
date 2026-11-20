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
 * <li>{@code hello} → {@code welcome} or {@code reject} (key | protocol | full | denied | timeout, then closed); deck
 * messages before the welcome are ignored.</li>
 * <li>Approval without club.json (Alpha): a hello without a valid key but with a pseudonym ({@code device}) of a Mac the
 * LJ approved before is welcomed; an unknown one gets {@code pending} and waits for "Allow" or "Deny" in the browser on
 * this computer ({@link SenderPairing}). Name, pseudonym and address of these Macs are never logged.</li>
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
    /** An approval request without an answer in this time gets {@code reject timeout}. */
    static final long APPROVAL_TIMEOUT_NANOS = 120_000_000_000L;
    /** More approval requests than this already waiting: a new one gets {@code reject full}. */
    static final int MAX_WAITING = 2;

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
        /** The pseudonym from the hello (32 lowercase hex digits), or null. */
        String device;
        /** Waiting for the LJ to allow or deny it (sent {@code pending}). */
        boolean awaiting;
        long requestedAt;
        /** The browser's handle of the waiting request ("r1" …). */
        String requestId;
        /** Welcomed because the LJ approved this Mac (not with the club key): its address is not logged or shown. */
        boolean approvedMac;
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
    private final SenderPairing pairing;
    private int nextRequest = 1;

    SenderInbox(Hub hub) {
        this(hub, SenderClub::key, SenderClub::name, System.out::println, SenderPairing.fromSettings());
    }

    /** For tests: the approved Macs are kept in memory only. */
    SenderInbox(Hub hub, Supplier<String> clubKey, Supplier<String> clubName) {
        this(hub, clubKey, clubName, System.out::println);
    }

    /** For tests: the approved Macs are kept in memory only. */
    SenderInbox(Hub hub, Supplier<String> clubKey, Supplier<String> clubName, java.util.function.Consumer<String> log) {
        this(hub, clubKey, clubName, log, SenderPairing.inMemory(System::currentTimeMillis));
    }

    SenderInbox(Hub hub, Supplier<String> clubKey, Supplier<String> clubName, java.util.function.Consumer<String> log,
            SenderPairing pairing) {
        this.hub = hub;
        this.clubKey = clubKey;
        this.clubName = clubName;
        this.log = log;
        this.pairing = pairing;
    }

    SenderPairing pairing() {
        return pairing;
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
            if (c.welcomed || c.awaiting) {
                continue; // a request waiting for the LJ has its own limit (MAX_WAITING)
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
        // address null: a Mac on the approval path, whose address is not logged.
        log.accept("LJL Link" + (address == null ? "" : " from " + (address.isEmpty() ? "unknown address" : address))
                + " refused: " + reason + (left > 0 ? " (" + left + " more refused for this reason in the last minute)" : ""));
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
            gone(c);
        } else if (c != null && c.awaiting) {
            log.accept("LJL Link: an approval request ended, the Mac disconnected");
        }
    }

    /** A welcomed sender goes: its decks go away, an approved Mac's last seen is updated. */
    private void gone(Conn c) {
        c.scope.close();
        if (c.approvedMac) {
            pairing.seen(c.device, null);
        }
    }

    /** Ends a connection from this side: its decks go away, then the socket is closed. */
    private void drop(Object handle, Conn c, int code, String reason) {
        conns.remove(handle);
        if (c.welcomed) {
            gone(c);
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
            if (c.awaiting) {
                if (now - c.requestedAt > APPROVAL_TIMEOUT_NANOS) {
                    // No answer in the booth: the request goes, the sender may try the next place.
                    log.accept("LJL Link: an approval request got no answer within 120 s");
                    send(c, reject("timeout"));
                    drop(e.getKey(), c, CLOSE_POLICY, "timeout");
                } else if (now - c.lastSeen > TIMEOUT_NANOS) {
                    log.accept("LJL Link: an approval request ended, the Mac went silent");
                    drop(e.getKey(), c, CLOSE_GOING_AWAY, "timeout");
                }
                continue;
            }
            if (c.welcomed ? now - c.lastSeen > TIMEOUT_NANOS : now - c.openedAt > HELLO_TIMEOUT_NANOS) {
                if (c.welcomed) {
                    log.accept("LJL Link " + c.id + " silent for 15 s; its decks are removed");
                }
                drop(e.getKey(), c, CLOSE_GOING_AWAY, "timeout");
            }
        }
        pairing.sweep(now);
        flushRefusals(now);
    }

    private static void send(Conn c, String text) {
        try {
            c.peer.send(text);
        } catch (RuntimeException ex) {
            // closing anyway
        }
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
            m.put("approved", c.approvedMac);
            m.put("decks", c.tracks.size());
            out.add(m);
        }
        return out;
    }

    /** Lines for a problem report: no names of DJs, no titles, no key. */
    synchronized List<String> diagnostics() {
        List<String> out = new ArrayList<>();
        int pending = 0;
        int waiting = 0;
        for (Conn c : conns.values()) {
            if (c.welcomed) {
                // An approved Mac only by its id: no address (and never its name or pseudonym).
                out.add("Sender " + c.id + (c.approvedMac ? " (approved Mac)" : " from " + c.address) + ": " + describe(c)
                        + ", titles " + (c.names ? "allowed" : "hidden by the DJ") + ", decks with a track " + c.tracks.keySet());
            } else if (c.awaiting) {
                waiting++;
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
        // Counts only.
        out.add("Approved DJ Macs: " + pairing.size() + ", approval requests waiting: " + waiting);
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
            if (c.awaiting) {
                // Waiting for the LJ: ping keeps the request alive (15-s rule), bye withdraws it, the rest is ignored.
                c.lastSeen = now;
                if ("ping".equals(type)) {
                    c.peer.send("{\"type\":\"pong\"}");
                } else if ("bye".equals(type)) {
                    log.accept("LJL Link: an approval request was withdrawn by the Mac");
                    drop(handle, c, CLOSE_NORMAL, "bye");
                }
            } else if ("hello".equals(type)) {
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

    /**
     * The decision on a hello, in the order of the protocol ("Approval without club.json"): protocol → valid key →
     * approved Mac → 4 senders → refused in the last 10 minutes → more than {@value #MAX_WAITING} requests waiting →
     * {@code pending}; no key and no pseudonym → {@code reject key}. A wrong key with a pseudonym takes the approval
     * path. A full LJ Link also refuses a valid key or an approved Mac ({@code full}, as before).
     */
    private void hello(Object handle, Conn c, JsonNode m, long now) {
        JsonNode protocol = m.path("protocol");
        if (!protocol.isIntegralNumber() || protocol.asInt() != PROTOCOL) {
            refuse(handle, c, "protocol", c.address, now);
            return;
        }
        readHello(c, m);
        boolean keyOk = m.path("key").isTextual() && SenderClub.matches(clubKey.get(), m.path("key").asText());
        String device = keyOk ? null : SenderPairing.device(m);
        if (keyOk) {
            if (welcomedCount() >= MAX_SENDERS) {
                refuse(handle, c, "full", c.address, now);
            } else {
                welcome(c, now, false);
            }
            return;
        }
        if (device != null && pairing.isApproved(device)) {
            c.device = device;
            if (welcomedCount() >= MAX_SENDERS) {
                refuse(handle, c, "full", null, now);
            } else {
                pairing.seen(device, c.name);
                welcome(c, now, true);
            }
            return;
        }
        // Without a valid key and without a device (LJL Link 0.3, old stick): "key" as before, also when full.
        if (device == null) {
            refuse(handle, c, "key", c.address, now);
            return;
        }
        if (welcomedCount() >= MAX_SENDERS) {
            refuse(handle, c, "full", null, now);
            return;
        }
        c.device = device;
        if (pairing.isDenied(device, now)) {
            refuse(handle, c, "denied", null, now);
            return;
        }
        // The same Mac asks again (e.g. it reconnected): its older request makes room, it is not shown twice.
        for (Map.Entry<Object, Conn> e : new ArrayList<>(conns.entrySet())) {
            Conn o = e.getValue();
            if (o != c && o.awaiting && device.equals(o.device)) {
                drop(e.getKey(), o, CLOSE_POLICY, "replaced");
            }
        }
        if (waitingCount() > MAX_WAITING) {
            refuse(handle, c, "full", null, now);
            return;
        }
        c.awaiting = true;
        c.requestedAt = now;
        c.lastSeen = now;
        c.requestId = "r" + nextRequest++;
        c.peer.send("{\"type\":\"pending\"}");
        log.accept("LJL Link: approval request waiting in the booth (" + waitingCount() + " waiting)");
    }

    /** Sends {@code reject reason}, closes and logs it (without the address when {@code address} is null). */
    private void refuse(Object handle, Conn c, String reason, String address, long now) {
        refused(reason, address, now);
        send(c, reject(reason));
        drop(handle, c, CLOSE_POLICY, reason);
    }

    /** Name, software, app and the titles switch from a hello. */
    private static void readHello(Conn c, JsonNode m) {
        String name = SenderClub.clean(m.path("name").asText(""), MAX_NAME);
        c.name = name.isEmpty() ? DEFAULT_NAME : name;
        c.software = SenderClub.clean(m.path("software").asText(""), MAX_NAME);
        c.app = SenderClub.clean(m.path("app").asText(""), 20);
        // Without a clear "yes" there are no titles.
        c.names = m.path("names").isBoolean() && m.path("names").asBoolean();
    }

    private void welcome(Conn c, long now, boolean approvedMac) {
        c.id = freeId();
        c.awaiting = false;
        c.approvedMac = approvedMac;
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
        // No DJ name in the log: it ends up in problem reports. An approved Mac not even with its address.
        log.accept("LJL Link " + c.id + " connected " + (approvedMac ? "(approved Mac; " : "from " + c.address + " (")
                + describe(c) + ", titles " + (c.names ? "allowed" : "hidden") + ")");
    }

    private int waitingCount() {
        int n = 0;
        for (Conn c : conns.values()) {
            if (c.awaiting) {
                n++;
            }
        }
        return n;
    }

    private Map.Entry<Object, Conn> request(String id) {
        for (Map.Entry<Object, Conn> e : conns.entrySet()) {
            if (e.getValue().awaiting && e.getValue().requestId.equals(id)) {
                return e;
            }
        }
        return null;
    }

    /** What became of "Allow". */
    enum Allowed { WELCOMED, FULL, GONE }

    /**
     * "Allow": the Mac goes into the list and the waiting connection gets its welcome. If 4 senders are connected by now,
     * it is approved but gets {@code reject full} (it is let in on its next try once there is room).
     */
    synchronized Allowed allow(String requestId, long now) {
        Map.Entry<Object, Conn> e = requestId == null ? null : request(requestId);
        if (e == null) {
            return Allowed.GONE;
        }
        Conn c = e.getValue();
        pairing.approve(c.device, c.name);
        log.accept("LJL Link: approval request allowed");
        if (welcomedCount() >= MAX_SENDERS) {
            c.awaiting = false;
            refuse(e.getKey(), c, "full", null, now);
            return Allowed.FULL;
        }
        welcome(c, now, true);
        return Allowed.WELCOMED;
    }

    /** "Deny": {@code reject denied}, closed, and this Mac is refused without a new request for 10 minutes. */
    synchronized boolean deny(String requestId, long now) {
        Map.Entry<Object, Conn> e = requestId == null ? null : request(requestId);
        if (e == null) {
            return false;
        }
        Conn c = e.getValue();
        pairing.deny(c.device, now);
        log.accept("LJL Link: approval request denied");
        send(c, reject("denied"));
        drop(e.getKey(), c, CLOSE_POLICY, "denied");
        return true;
    }

    /**
     * "Remove" in the list of approved Macs: the entry goes, and a connection this Mac has through its approval is
     * closed at once ({@code reject denied}; it may ask again, a new request then appears). Connections with the club key
     * are not touched. False when there is no such entry.
     */
    synchronized boolean removeApproved(String ref) {
        String device = ref == null ? null : pairing.remove(ref);
        if (device == null) {
            return false;
        }
        int closed = 0;
        for (Map.Entry<Object, Conn> e : new ArrayList<>(conns.entrySet())) {
            Conn c = e.getValue();
            if (c.welcomed && c.approvedMac && device.equals(c.device)) {
                send(c, reject("denied"));
                drop(e.getKey(), c, CLOSE_POLICY, "removed");
                closed++;
            }
        }
        log.accept("LJL Link: an approved Mac was removed" + (closed > 0 ? ", its connection was closed" : ""));
        return true;
    }

    /** "New key": the list of approved Macs is emptied and every sender and request is disconnected. */
    synchronized void forgetEverything() {
        pairing.clear();
        disconnectAll();
    }

    /**
     * For the local API only (never a tablet): the waiting requests (handle, name, address, seconds left) and the approved
     * Macs (reference, name, first and last seen, connected now). No pseudonym.
     */
    synchronized Map<String, Object> pairingState(long now) {
        List<Map<String, Object>> requests = new ArrayList<>();
        java.util.Set<String> online = new java.util.HashSet<>();
        for (Conn c : conns.values()) {
            if (c.awaiting) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("id", c.requestId);
                r.put("name", c.name);
                r.put("address", c.address);
                r.put("software", c.software);
                r.put("left", Math.max(0, (APPROVAL_TIMEOUT_NANOS - (now - c.requestedAt)) / 1_000_000_000L));
                requests.add(r);
            } else if (c.welcomed && c.approvedMac) {
                online.add(c.device);
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("requests", requests);
        m.put("approved", pairing.list(online));
        m.put("max", SenderPairing.MAX_APPROVED);
        return m;
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
        // An approved Mac's address does not go to the browsers (tablets); with the club key as before.
        d.address = c.approvedMac ? "" : c.address;
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
