package dev.ljlinkpro;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.StandardSocketOptions;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The suite chat (Alpha, docs/suite-chat.md): a room for every program of the suite in the same network, without a
 * host, over UDP multicast. This class is the room as one program sees it; {@link Net} is the network side. Chat text
 * is never logged and never part of a problem report.
 */
final class SuiteChat {
    static final String GROUP = "239.255.76.76";
    static final int PORT = 47676;

    /**
     * The chat port: 47676, the real room. Tests and test instances without a live purpose take another one with
     * {@code -Dljlink.chatPort=…} or {@code LJLINK_CHAT_PORT} (docs/suite-chat.md).
     */
    static int port() {
        String v = System.getProperty("ljlink.chatPort", System.getenv("LJLINK_CHAT_PORT"));
        try {
            int p = v == null ? PORT : Integer.parseInt(v.trim());
            return p > 0 && p < 65536 ? p : PORT;
        } catch (NumberFormatException e) {
            return PORT;
        }
    }
    static final int MAX_TEXT = 500;
    static final int MAX_NICK = 24;
    static final int MAX_KEPT = 200;
    static final int MAX_PEERS = 50;
    static final int BURST = 10;
    static final long BURST_WINDOW_NANOS = 10_000_000_000L;
    static final long HERE_EVERY_NANOS = 5_000_000_000L;
    static final long GONE_AFTER_NANOS = 15_000_000_000L;
    static final int HISTORY_ANSWER = 20;
    static final int MAX_DATAGRAM = 8 * 1024;
    /** A "history?" is answered at most once in this time, whoever asks (the answers of the others are enough). */
    static final long HISTORY_EVERY_NANOS = 10_000_000_000L;
    /** At most this many EMERGENCY messages per peer in {@link #URGENT_WINDOW_NANOS}; more are shown as normal ones. */
    static final int URGENT_BURST = 3;
    static final long URGENT_WINDOW_NANOS = 60_000_000_000L;
    /** At most this many EMERGENCY messages open at once; more are shown as normal ones. */
    static final int MAX_OPEN_ALERTS = 20;
    /** Datagrams waiting to be sent; more are dropped (a flood never fills the memory). */
    static final int MAX_QUEUED = 64;
    /** No history answer this long after joining: ask once more (everyone may have been in the 10-s lock). */
    static final long HISTORY_AGAIN_NANOS = 12_000_000_000L;
    /** Settings: on/off (on by default) and the nickname of this program. */
    static final String SETTING_ON = "suiteChat";
    static final String SETTING_NICK = "suiteChatNick";
    static final String SETTING_WHERE = "suiteChatWhere";

    /** Where datagrams go; {@code delayMs} for the second copy of a message and the spaced history answer. */
    interface Out {
        void send(String json, long delayMs);
    }

    /**
     * Told when the open EMERGENCY messages, the unread count or on/off changed (for the window and the program
     * icon outside the browser). Called while the room is locked: hand the work to another thread, never call back
     * into the room from inside.
     */
    interface Listener {
        void changed();
    }

    private static final class Peer {
        String nick = "";
        String where = "";
        String app = "";
        long lastSeen;
        final ArrayDeque<Long> times = new ArrayDeque<>();
        final ArrayDeque<Long> urgentTimes = new ArrayDeque<>();
    }

    private final String peer;
    private final String app;
    private final Out out;
    private final java.util.function.LongSupplier wallClock;
    /** Guarded by this. */
    private boolean on;
    private String nick = "";
    /** Where this person stands ("DJ Booth", "FOH", "Bar"). */
    private String where = "";
    private long seq;
    private long shown;
    private long lastHere = Long.MIN_VALUE;
    /** Until when answers to this program's "history?" are expected; they do not count towards {@link #BURST}. */
    private long historyUntil = Long.MIN_VALUE;
    /** When this program last answered a "history?" (at most once in {@link #HISTORY_EVERY_NANOS}). */
    private long historyAnswered = Long.MIN_VALUE;
    /** When this program joined; whether a history answer came; whether it asked a second time already. */
    private long joinedAt;
    private boolean heardHistory;
    private boolean askedAgain;
    private final List<Map<String, Object>> msgs = new ArrayList<>();
    private final Set<String> ids = new LinkedHashSet<>();
    private final Map<String, Peer> peers = new LinkedHashMap<>();
    /** When history messages of senders not in the room were taken (outside this program's own 5-s window). */
    private final ArrayDeque<Long> strangerOld = new ArrayDeque<>();
    /** EMERGENCY messages from others not yet confirmed with "OK" (in the browser or the window), oldest first. */
    private final List<Map<String, Object>> alerts = new ArrayList<>();
    /** Live messages from others since the browser last showed the chat (history answers do not count). */
    private int unread;
    /** An EMERGENCY from someone else came since the chat was last shown: the chat button blinks yellow. */
    private boolean urgentUnread;
    /** Counts the times the chat was switched on: a browser that missed an off/on starts its list anew. */
    private int epoch;
    private Listener listener = () -> { };
    /** Differs on every start: an open browser sees a restart even when the numbers match again. */
    private final String instance;

    SuiteChat(String app, Out out) {
        this(newPeerId(), app, out, System::currentTimeMillis);
    }

    SuiteChat(String peer, String app, Out out, java.util.function.LongSupplier wallClock) {
        this.peer = peer;
        this.app = app;
        this.out = out;
        this.wallClock = wallClock;
        this.instance = newPeerId();
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    static String randomTail() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            b.append(Character.forDigit(RANDOM.nextInt(36), 36));
        }
        return b.toString();
    }

    static String newPeerId() {
        SecureRandom r = new SecureRandom();
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            b.append(Character.forDigit(r.nextInt(36), 36));
        }
        return b.toString();
    }

    String peer() {
        return peer;
    }

    synchronized void setListener(Listener l) {
        listener = l == null ? () -> { } : l;
    }

    // ------------------------------------------------------------------ this program

    /** On: joins (presence and a request for the history). Off: leaves; nothing is sent or taken any more. */
    synchronized void setOn(boolean value, long now) {
        if (value == on) {
            return;
        }
        on = value;
        if (on) {
            here(now);
            historyUntil = now + 5_000_000_000L;
            joinedAt = now;
            epoch++;
            heardHistory = false;
            askedAgain = false;
            out.send(base("history?").toString(), 0);
        } else {
            out.send(base("bye").toString(), 0);
            peers.clear();
            alerts.clear();
            unread = 0;
            urgentUnread = false;
            // Off means off: nothing stays readable (GET /api/chat). Switched on again, the others send the history.
            msgs.clear();
            ids.clear();
        }
        listener.changed();
    }

    synchronized boolean on() {
        return on;
    }

    synchronized void setNick(String value, long now) {
        setProfile(value, where, now);
    }

    synchronized void setProfile(String name, String place, long now) {
        nick = text(name, MAX_NICK);
        where = text(place, MAX_NICK);
        if (on) {
            here(now);
        }
    }

    synchronized String where() {
        return where;
    }

    synchronized String nick() {
        return nick;
    }

    synchronized boolean post(String value, long now) {
        return post(value, false, now);
    }

    /**
     * A message from this program; false while off, without a nickname or with an empty text. {@code urgent}: an
     * EMERGENCY message; every other program shows it as a pop-up at once.
     */
    synchronized boolean post(String value, boolean urgent, long now) {
        String t = text(value, MAX_TEXT);
        if (!on || t.isEmpty() || nick.isEmpty()) {
            return false;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        // peer-counter + 8 random characters: nobody can take the next ids of this program in advance (docs/suite-chat.md).
        m.put("id", peer + "-" + (++seq) + randomTail());
        m.put("nick", nick);
        m.put("where", where);
        m.put("app", app);
        m.put("text", t);
        m.put("urgent", urgent);
        m.put("at", wallClock.getAsLong());
        keep(m, true);
        String json = msgJson(m, false);
        out.send(json, 0);
        out.send(json, 300); // a second copy: Wi-Fi loses single datagrams now and then
        return true;
    }

    /** Twice a second: presence every 5 s, peers that went silent leave. */
    synchronized void tick(long now) {
        if (!on) {
            return;
        }
        if (lastHere == Long.MIN_VALUE || now - lastHere >= HERE_EVERY_NANOS) {
            here(now);
        }
        // Joined right after another newcomer, every program was in its 10-s history lock: ask exactly once more.
        if (!askedAgain && !heardHistory && now - joinedAt >= HISTORY_AGAIN_NANOS) {
            askedAgain = true;
            historyUntil = now + 5_000_000_000L;
            out.send(base("history?").toString(), 0);
        }
        peers.values().removeIf(p -> now - p.lastSeen > GONE_AFTER_NANOS);
    }

    /** Quitting: the others see it at once instead of after 15 s. Sent at once, not queued (the program ends). */
    synchronized void stop() {
        if (on) {
            String bye = base("bye").toString();
            if (out instanceof Net n) {
                n.sendNow(bye);
            } else {
                out.send(bye, 0);
            }
        }
    }

    private void here(long now) {
        lastHere = now;
        StringBuilder b = base("here");
        b.setLength(b.length() - 1);
        b.append(",\"nick\":").append(quote(nick)).append(",\"where\":").append(quote(where)).append(",\"app\":")
                .append(quote(app)).append('}');
        out.send(b.toString(), 0);
    }

    // ------------------------------------------------------------------ the others

    /** A datagram from the network. Anything malformed, from this program itself or beyond the limits is dropped. */
    synchronized void receive(String json, long now) {
        if (!on || json == null || json.length() > MAX_DATAGRAM) {
            return;
        }
        JsonNode m;
        try {
            m = Hub.JSON.readTree(json);
        } catch (IOException | RuntimeException e) {
            return;
        }
        if (m == null || !m.isObject() || !"chat".equals(str(m, "suite")) || !(m.path("v").isIntegralNumber() && m.path("v").asLong() == 1)) {
            return;
        }
        String from = str(m, "peer");
        if (!from.matches("[0-9a-z]{8,32}") || from.equals(peer)) {
            return;
        }
        String type = str(m, "t");
        if ("bye".equals(type)) {
            peers.remove(from);
            return;
        }
        Peer p = peers.get(from);
        // A message sent again as history may come from someone who left long ago: it neither adds a peer to the
        // room nor keeps one in it (otherwise everyone who ever wrote stood in the room again after every newcomer).
        if ("msg".equals(type) && isTrue(m, "old")) {
            heardHistory = true;
            message(p, m, now);
            return;
        }
        if (p == null) {
            if (peers.size() >= MAX_PEERS) {
                return;
            }
            p = new Peer();
            peers.put(from, p);
        }
        p.lastSeen = now;
        switch (type) {
            case "here" -> {
                p.nick = text(str(m, "nick"), MAX_NICK);
                p.where = text(str(m, "where"), MAX_NICK);
                p.app = text(str(m, "app"), MAX_NICK);
            }
            case "msg" -> message(p, m, now);
            case "history?" -> {
                // At most once in 10 s, whoever asks, and only with something to tell: a tiny datagram must not make
                // every program in the room send 20 messages each time (flood of the club network, QS round 10).
                if (msgs.isEmpty() || (historyAnswered != Long.MIN_VALUE && now - historyAnswered < HISTORY_EVERY_NANOS)) {
                    return;
                }
                historyAnswered = now;
                int start = Math.max(0, msgs.size() - HISTORY_ANSWER);
                for (int i = start; i < msgs.size(); i++) {
                    out.send(msgJson(msgs.get(i), true), 20L * (i - start + 1));
                }
            }
            default -> { } // unknown types: ignored (newer versions)
        }
    }

    /** {@code p}: the sender, or null for a history message from someone not in the room. */
    private void message(Peer p, JsonNode m, long now) {
        String id = str(m, "id");
        if (id.isEmpty() || id.length() > 64 || ids.contains(id)) {
            return; // the second copy, or the history again
        }
        // An id belongs to its sender ("peer-17"): nobody can take another program's future ids and so hide them.
        if (!id.startsWith(str(m, "peer") + "-")) {
            return;
        }
        String t = text(str(m, "text"), MAX_TEXT);
        if (t.isEmpty()) {
            return;
        }
        boolean old = isTrue(m, "old");
        // The history this program asked for comes in a burst; it is bounded (20 per answering peer) and expected.
        boolean asked = old && now - historyUntil < 0;
        if (!asked) {
            // Senders in the room: 10 in 10 s each. History of senders not in the room: 10 in 10 s for all of them.
            ArrayDeque<Long> times = p != null ? p.times : strangerOld;
            while (!times.isEmpty() && now - times.peekFirst() > BURST_WINDOW_NANOS) {
                times.pollFirst();
            }
            if (times.size() >= BURST) {
                return;
            }
            times.addLast(now);
        }
        // A live message also tells who wrote it (a "here" may have been lost); a history message never does.
        if (p != null && !old) {
            if (p.nick.isEmpty()) {
                p.nick = text(str(m, "nick"), MAX_NICK);
            }
            if (p.app.isEmpty()) {
                p.app = text(str(m, "app"), MAX_NICK);
            }
            if (p.where.isEmpty()) {
                p.where = text(str(m, "where"), MAX_NICK);
            }
        }
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("id", id);
        msg.put("nick", text(str(m, "nick"), MAX_NICK));
        msg.put("where", text(str(m, "where"), MAX_NICK));
        msg.put("app", text(str(m, "app"), MAX_NICK));
        msg.put("text", t);
        // EMERGENCY: shown as a pop-up; a history answer never brings an old one up again as new. At most 3 per peer
        // in 60 s and 20 open at once; more come as normal messages (no flood of pop-ups, QS round 10).
        boolean urgent = isTrue(m, "urgent") && !isTrue(m, "old");
        if (urgent) {
            while (!p.urgentTimes.isEmpty() && now - p.urgentTimes.peekFirst() > URGENT_WINDOW_NANOS) {
                p.urgentTimes.pollFirst();
            }
            if (p.urgentTimes.size() >= URGENT_BURST || alerts.size() >= MAX_OPEN_ALERTS) {
                urgent = false;
            } else {
                p.urgentTimes.addLast(now);
            }
        }
        msg.put("urgent", urgent);
        // Live: the time it arrived here (a sender's wrong clock must not hide new messages); history: the sender's.
        msg.put("at", isTrue(m, "old") ? at(m.path("at")) : wallClock.getAsLong());
        keep(msg, false);
        if (!isTrue(m, "old")) {
            unread++;
            if (Boolean.TRUE.equals(msg.get("urgent"))) {
                alerts.add(msg);
                urgentUnread = true;
            }
            listener.changed();
        }
    }

    /** Latest time a sender's clock may say: 2100-01-01 (docs/suite-chat.md). */
    static final long MAX_AT = 4_102_444_800_000L;

    /** A string field; any other type (number, true, object) counts as missing (docs/suite-chat.md, strict types). */
    private static String str(JsonNode m, String field) {
        JsonNode v = m.path(field);
        return v.isTextual() ? v.asText() : "";
    }

    /** Only a real JSON {@code true} counts, not 1 and not "true". */
    private static boolean isTrue(JsonNode m, String field) {
        JsonNode v = m.path(field);
        return v.isBoolean() && v.booleanValue();
    }

    /** The sender's time if it is a whole number from 0 to {@link #MAX_AT}, otherwise this program's clock. */
    private long at(JsonNode at) {
        if (at.isIntegralNumber() && at.canConvertToLong()) {
            long v = at.asLong();
            if (v >= 0 && v <= MAX_AT) {
                return v;
            }
        }
        return wallClock.getAsLong();
    }

    // ------------------------------------------------------------------ EMERGENCY and unread, outside the browser

    /** The EMERGENCY messages not yet confirmed, oldest first (copies: nick, where, app, text, at, n). */
    synchronized List<Map<String, Object>> alerts() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> m : alerts) {
            Map<String, Object> c = new LinkedHashMap<>(m);
            c.remove("id");
            list.add(c);
        }
        return list;
    }

    /**
     * "Close all": every open EMERGENCY up to number {@code upTo} is confirmed at once; one that arrived after the
     * window or the browser showed the list stays open.
     */
    synchronized void ackAll(long upTo) {
        if (alerts.removeIf(m -> (long) m.get("n") <= upTo)) {
            listener.changed();
        }
    }

    /** "OK" on EMERGENCY number {@code n}, in the browser or the window: gone from both. */
    synchronized void ack(long n) {
        if (alerts.removeIf(m -> (long) m.get("n") == n)) {
            listener.changed();
        }
    }

    synchronized int unread() {
        return unread;
    }

    /** True while an EMERGENCY from someone else has not been seen in the chat ("OK" on the pop-up does not count). */
    synchronized boolean urgentUnread() {
        return urgentUnread;
    }

    /** The browser on this computer shows the chat: nothing is unread any more, the yellow blinking stops. */
    synchronized void read() {
        if (unread != 0 || urgentUnread) {
            unread = 0;
            urgentUnread = false;
            listener.changed();
        }
    }

    private void keep(Map<String, Object> m, boolean self) {
        ids.add((String) m.get("id"));
        if (ids.size() > 4 * MAX_KEPT) {
            ids.remove(ids.iterator().next());
        }
        m.put("self", self);
        m.put("n", ++shown);
        msgs.add(m);
        while (msgs.size() > MAX_KEPT) {
            msgs.remove(0);
        }
    }

    // ------------------------------------------------------------------ for the browser on this computer

    synchronized Map<String, Object> state(long since) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("enabled", on);
        s.put("nick", nick);
        s.put("where", where);
        s.put("app", app);
        s.put("instance", instance);
        s.put("epoch", epoch);
        List<Map<String, Object>> who = new ArrayList<>();
        for (Peer p : peers.values()) {
            if (!p.nick.isEmpty() || !p.app.isEmpty()) { // not yet said who it is (only "history?" so far): not listed
                who.add(Map.of("nick", p.nick, "where", p.where, "app", p.app));
            }
        }
        s.put("peers", who);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> m : msgs) {
            if ((long) m.get("n") > since) {
                list.add(new LinkedHashMap<>(m)); // with its id: the browser never lists one message twice
            }
        }
        s.put("messages", list);
        s.put("last", shown);
        // Numbers of the EMERGENCY messages still open: one confirmed in the window closes in the browser too.
        List<Long> open = new ArrayList<>();
        for (Map<String, Object> m : alerts) {
            open.add((Long) m.get("n"));
        }
        s.put("alerts", open);
        s.put("unread", unread);
        s.put("urgentUnread", urgentUnread);
        return s;
    }

    /** For a problem report: never the text, never a nickname. */
    synchronized String diagnostics() {
        return "Suite chat: " + (on ? "on" : "off") + ", " + peers.size() + " other programs, " + msgs.size()
                + " messages in memory, " + alerts.size() + " emergencies open";
    }

    // ------------------------------------------------------------------ helpers

    /** Line breaks and tabs become spaces, direction marks and control characters go, trimmed, at most {@code max}. */
    static String text(String value, int max) {
        if (value == null) {
            return "";
        }
        // Line and paragraph separators (U+2028/9) count as line breaks; U+061C is a direction mark too.
        String t = value.replaceAll("[\\r\\n\\t\\u2028\\u2029]+", " ")
                .replaceAll("[\\u061C\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]", "");
        // Unicode spaces at both ends go too (strip, not only trim); spaces inside stay.
        return strip(SenderClub.clean(strip(t), max));
    }

    /** Removes every Unicode space at both ends (also U+00A0 and U+3000, which String.strip keeps or treats unevenly). */
    static String strip(String s) {
        int b = 0;
        int e = s.length();
        while (b < e && isSpace(s.codePointAt(b))) {
            b += Character.charCount(s.codePointAt(b));
        }
        while (e > b && isSpace(s.codePointBefore(e))) {
            e -= Character.charCount(s.codePointBefore(e));
        }
        return s.substring(b, e);
    }

    private static boolean isSpace(int cp) {
        return Character.isWhitespace(cp) || Character.isSpaceChar(cp);
    }

    private StringBuilder base(String type) {
        return new StringBuilder("{\"suite\":\"chat\",\"v\":1,\"t\":\"").append(type).append("\",\"peer\":\"").append(peer)
                .append("\"}");
    }

    /** {@code old}: sent again as history (receivers may take a burst of these right after they asked). */
    private String msgJson(Map<String, Object> m, boolean old) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("suite", "chat");
        o.put("v", 1);
        o.put("t", "msg");
        String id = (String) m.get("id");
        o.put("peer", id.substring(0, id.indexOf('-'))); // the author: everything before the first "-"
        o.put("id", m.get("id"));
        o.put("nick", m.get("nick"));
        o.put("where", m.get("where"));
        o.put("app", m.get("app"));
        o.put("text", m.get("text"));
        if (Boolean.TRUE.equals(m.get("urgent"))) {
            o.put("urgent", true);
        }
        o.put("at", m.get("at"));
        if (old) {
            o.put("old", true);
        }
        try {
            return Hub.JSON.writeValueAsString(o);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String quote(String s) {
        try {
            return Hub.JSON.writeValueAsString(s);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------ network

    /**
     * UDP multicast on every IPv4 adapter that is up; the adapters are checked again every 30 s (Wi-Fi comes and goes).
     * Errors only end the chat on this adapter; LJ Link itself is never affected.
     */
    static final class Net implements Out {
        private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "suite-chat");
            t.setDaemon(true);
            return t;
        });
        private MulticastSocket socket;
        /** Adapters joined, by name (an adapter whose address changed is still the same adapter). */
        private final Map<String, NetworkInterface> joined = new LinkedHashMap<>();
        private final int port = port();
        private final InetSocketAddress group = new InetSocketAddress(GROUP, port);
        private SuiteChat chat;
        private volatile boolean running;
        /** Datagrams scheduled but not sent yet (at most {@link #MAX_QUEUED}). */
        private final java.util.concurrent.atomic.AtomicInteger queued = new java.util.concurrent.atomic.AtomicInteger();

        /** Opens the socket and starts listening; false if the port cannot be used (the chat then stays empty). */
        synchronized boolean start(SuiteChat chat) {
            this.chat = chat;
            if (running) {
                return true;
            }
            try {
                MulticastSocket s = new MulticastSocket(null);
                s.setReuseAddress(true);
                if (s.supportedOptions().contains(StandardSocketOptions.SO_REUSEPORT)) {
                    s.setOption(StandardSocketOptions.SO_REUSEPORT, true);
                }
                s.bind(new InetSocketAddress(port));
                s.setTimeToLive(1);
                s.setOption(StandardSocketOptions.IP_MULTICAST_LOOP, true);
                socket = s;
            } catch (IOException e) {
                System.err.println("Suite chat: port " + port + " not available (" + e.getMessage() + ")");
                return false;
            }
            running = true;
            rejoin();
            Thread reader = new Thread(this::read, "suite-chat-read");
            reader.setDaemon(true);
            reader.start();
            timer.scheduleAtFixedRate(() -> chat.tick(System.nanoTime()), 500, 500, TimeUnit.MILLISECONDS);
            timer.scheduleAtFixedRate(this::rejoin, 30, 30, TimeUnit.SECONDS);
            return true;
        }

        private static List<NetworkInterface> adapters() {
            List<NetworkInterface> out = new ArrayList<>();
            try {
                for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                    if (!ni.isUp() || ni.isLoopback() || ni.isVirtual() || !ni.supportsMulticast()) {
                        continue;
                    }
                    for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                        if (a instanceof Inet4Address && !a.isLinkLocalAddress()) {
                            out.add(ni);
                            break;
                        }
                    }
                }
            } catch (IOException e) {
                // none
            }
            return out;
        }

        private synchronized void rejoin() {
            if (socket == null) {
                return;
            }
            List<NetworkInterface> now = adapters();
            Set<String> names = new HashSet<>();
            for (NetworkInterface ni : now) {
                names.add(ni.getName());
                if (!joined.containsKey(ni.getName())) {
                    try {
                        socket.joinGroup(group, ni);
                    } catch (IOException e) {
                        if (!String.valueOf(e.getMessage()).toLowerCase(java.util.Locale.ROOT).contains("already")) {
                            continue; // this adapter cannot do multicast: the others still can
                        }
                    }
                }
                joined.put(ni.getName(), ni); // the current object (its addresses may have changed)
            }
            joined.keySet().retainAll(names);
        }

        private void read() {
            byte[] buf = new byte[MAX_DATAGRAM + 1];
            while (running) {
                try {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    socket.receive(p);
                    if (p.getLength() <= MAX_DATAGRAM) {
                        chat.receive(new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8), System.nanoTime());
                    }
                } catch (IOException | RuntimeException e) {
                    if (!running) {
                        return;
                    }
                    try {
                        Thread.sleep(200); // a lasting error must not spin a core
                    } catch (InterruptedException ie) {
                        return;
                    }
                }
            }
        }

        @Override
        public void send(String json, long delayMs) {
            if (queued.incrementAndGet() > MAX_QUEUED) {
                queued.decrementAndGet(); // the queue is full: dropped
                return;
            }
            Runnable r = () -> {
                queued.decrementAndGet();
                sendNow(json);
            };
            if (delayMs > 0) {
                timer.schedule(r, delayMs, TimeUnit.MILLISECONDS);
            } else {
                timer.execute(r);
            }
        }

        int queued() {
            return queued.get();
        }

        synchronized void sendNow(String json) {
            if (socket == null) {
                return;
            }
            byte[] data = json.getBytes(StandardCharsets.UTF_8);
            DatagramPacket p = new DatagramPacket(data, data.length, group);
            for (NetworkInterface ni : new ArrayList<>(joined.values())) {
                try {
                    socket.setNetworkInterface(ni);
                    socket.send(p);
                } catch (IOException e) {
                    // this adapter went away; rejoin() notices
                }
            }
        }
    }
}
