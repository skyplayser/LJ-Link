package dev.ljlinkpro;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Takes part in an Ableton Link session and leads its tempo from the deck chosen in SEND BPM, optionally also the beat
 * and bar phase from that deck's beat grid. Every Link program in the network (Ableton Live, Resolume, Traktor, apps …)
 * then runs at the DJ's tempo. Output only: LJ Link never controls the DJ gear through this.
 *
 * <p>Built from the protocol of Ableton's open source Link library, no code of it:
 * <ul>
 * <li>Discovery: {@code "_asdp_v" 1} messages on the multicast group, alive (1) every 250 ms, response (2) to every alive
 * we see, bye (3) when leaving. Entries: timeline {@code tmln}, session {@code sess}, start/stop {@code stst} and the
 * measurement endpoint {@code mep4}.</li>
 * <li>Clock: every session has a "ghost time". Peers measure it with {@code "_link_v" 1} ping (1) / pong (2) messages to
 * the measurement endpoint; the pong carries the session and the ghost time and echoes the ping.</li>
 * <li>Sessions: a peer joins the other session when its ghost time is more than half a second ahead (it is older), on a
 * tie the smaller session id wins. Within a session the timeline with the larger beat origin wins.</li>
 * </ul>
 */
final class AbletonLinkPeer {
    private static final byte[] DISCOVERY = {'_', 'a', 's', 'd', 'p', '_', 'v', 1};
    private static final byte[] MEASUREMENT = {'_', 'l', 'i', 'n', 'k', '_', 'v', 1};
    private static final int ALIVE = 1, RESPONSE = 2, BYE = 3, PING = 1, PONG = 2;
    private static final int TMLN = key("tmln"), SESS = key("sess"), STST = key("stst"), MEP4 = key("mep4");
    private static final int GHOST_TIME = key("__gt"), PREV_GHOST_TIME = key("_pgt"), HOST_TIME = key("__ht");
    private static final int TTL_SECONDS = 5;
    private static final long SESSION_EPS_MICROS = 500_000;
    private static final int MEASUREMENT_POINTS = 100;
    /** A phase error of the session against the deck beyond this many milliseconds is corrected. */
    private static final double PHASE_TOLERANCE_MS = 8;
    private static final long PHASE_FIX_INTERVAL_NANOS = 300_000_000L;

    /** Tempo, beat origin (micro beats) and time origin (ghost microseconds): the session's beat at any time. */
    record Timeline(long microsPerBeat, long beatOrigin, long timeOrigin) {
        double beatsAt(long ghost) {
            return (beatOrigin + (ghost - timeOrigin) * 1e6 / microsPerBeat) / 1e6;
        }

        double bpm() {
            return 60e6 / microsPerBeat;
        }
    }

    record StartStop(boolean playing, long beats, long timestamp) {}

    record Status(boolean enabled, boolean phase, int peers, Double bpm, String sendingDeck, String note) {}

    private record Peer(byte[] session, Timeline timeline, StartStop startStop, InetSocketAddress endpoint, long seenNanos) {}

    /** One network adapter: its own socket sends our announcements and receives responses, pings and pongs. */
    private record Iface(InetAddress address, short prefix, MulticastSocket socket) {}

    private final class Measurement {
        final byte[] session;
        final InetSocketAddress endpoint;
        final List<Double> data = new ArrayList<>();
        long lastPingNanos;
        int retries;

        Measurement(byte[] session, InetSocketAddress endpoint) {
            this.session = session;
            this.endpoint = endpoint;
        }
    }

    private final Hub hub;
    private final LinkService lightDesk;
    private final byte[] nodeId = newNodeId();
    private final Map<String, Peer> peers = new ConcurrentHashMap<>();
    private final Map<String, Measurement> measurements = new ConcurrentHashMap<>();
    /** Sessions whose measurement failed, and when they may be tried again. */
    private final Map<String, Long> failedUntil = new ConcurrentHashMap<>();
    private final List<Iface> ifaces = new CopyOnWriteArrayList<>();

    private volatile boolean enabled = AppSettings.getBoolean("abletonLink", false);
    private volatile boolean phase = AppSettings.getBoolean("abletonLinkPhase", true);
    private volatile boolean running;
    private MulticastSocket group;
    private ScheduledExecutorService timer;

    // Session state, guarded by "this".
    private byte[] session;
    private long intercept;
    private Timeline timeline;
    private StartStop startStop;
    private long lastRemeasureNanos;
    private long lastPhaseFixNanos;
    private String sendingDeck;
    private String note = "";

    AbletonLinkPeer(Hub hub, LinkService lightDesk) {
        this.hub = hub;
        this.lightDesk = lightDesk;
        AbletonLink.ignoredPeer = nodeIdHex();
        if (enabled) {
            start();
        }
    }

    /** Our peer id, so that the listener for the Link deck does not count LJ Link itself. */
    private String nodeIdHex() {
        return HexFormat.of().formatHex(nodeId);
    }

    synchronized Status status() {
        int inSession = 0;
        if (running) {
            prunePeers();
            for (Peer p : peers.values()) {
                if (Arrays.equals(p.session(), session)) {
                    inSession++;
                }
            }
        }
        return new Status(enabled, phase, inSession, running ? timeline.bpm() : null, sendingDeck, note);
    }

    synchronized void configure(boolean on, boolean withPhase) {
        phase = withPhase;
        AppSettings.set("abletonLinkPhase", withPhase);
        AppSettings.set("abletonLink", on);
        if (on && !enabled) {
            enabled = true;
            start();
        } else if (!on && enabled) {
            enabled = false;
            stop();
        }
    }

    // ------------------------------------------------------------------ lifecycle

    private synchronized void start() {
        if (running) {
            return;
        }
        long host = hostMicros();
        session = nodeId.clone();
        intercept = -host; // a new session's ghost time starts at 0
        timeline = new Timeline(500_000, 0, 0); // 120 BPM until a deck leads
        startStop = new StartStop(false, 0, 0);
        peers.clear();
        measurements.clear();
        note = "";
        try {
            group = new MulticastSocket(null);
            group.setReuseAddress(true);
            group.bind(new InetSocketAddress(AbletonLink.PORT));
            group.setSoTimeout(1000);
        } catch (IOException e) {
            note = "Ableton Link could not start: " + e.getMessage();
            System.err.println(note);
            return;
        }
        running = true;
        scanInterfaces();
        Thread t = new Thread(() -> receive(group, null), "ableton-link-peer");
        t.setDaemon(true);
        t.start();
        timer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread th = new Thread(r, "ableton-link-clock");
            th.setDaemon(true);
            return th;
        });
        timer.scheduleAtFixedRate(() -> guard(this::broadcastAlive), 0, 250, TimeUnit.MILLISECONDS);
        timer.scheduleAtFixedRate(() -> guard(this::lead), 100, 10, TimeUnit.MILLISECONDS);
        timer.scheduleAtFixedRate(() -> guard(this::measureTick), 100, 20, TimeUnit.MILLISECONDS);
        timer.scheduleAtFixedRate(() -> guard(this::scanInterfaces), 10, 10, TimeUnit.SECONDS);
    }

    private synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        byte[] bye = header(BYE, 0).array();
        for (Iface i : ifaces) {
            sendQuietly(i.socket(), bye, groupAddress());
        }
        if (timer != null) {
            timer.shutdownNow();
        }
        for (Iface i : ifaces) {
            i.socket().close();
        }
        ifaces.clear();
        group.close();
        peers.clear();
        measurements.clear();
        sendingDeck = null;
        note = "";
        for (Deck d : hub.allDecks()) {
            d.sendingLink = false;
        }
    }

    private static void guard(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            System.err.println("Ableton Link: " + e);
        }
    }

    /** Adds adapters that came up (cable plugged in, Wi-Fi joined) and drops those that went away. */
    private void scanInterfaces() {
        List<InterfaceAddress> found = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || !ni.supportsMulticast() || ni.isLoopback() || ni.isVirtual()) {
                    continue;
                }
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    if (ia.getAddress() instanceof Inet4Address) {
                        found.add(ia);
                        if (ifaces.stream().noneMatch(i -> i.address().equals(ia.getAddress()))) {
                            open(ni, ia);
                        }
                    }
                }
            }
        } catch (SocketException e) {
            return;
        }
        for (Iface i : ifaces) {
            if (found.stream().noneMatch(ia -> ia.getAddress().equals(i.address()))) {
                ifaces.remove(i);
                i.socket().close();
            }
        }
    }

    private void open(NetworkInterface ni, InterfaceAddress ia) {
        try {
            MulticastSocket s = new MulticastSocket(new InetSocketAddress(ia.getAddress(), 0));
            s.setNetworkInterface(ni);
            s.setSoTimeout(1000);
            try {
                group.joinGroup(groupAddress(), ni);
            } catch (IOException e) {
                // already joined on this adapter, or it takes no multicast
            }
            Iface i = new Iface(ia.getAddress(), ia.getNetworkPrefixLength(), s);
            ifaces.add(i);
            Thread t = new Thread(() -> receive(s, i), "ableton-link-" + ni.getName());
            t.setDaemon(true);
            t.start();
        } catch (IOException e) {
            System.err.println("Ableton Link: no socket on " + ni.getName() + ": " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ receiving

    private void receive(MulticastSocket s, Iface via) {
        byte[] buf = new byte[1024];
        while (running && !s.isClosed()) {
            try {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                s.receive(p);
                byte[] data = Arrays.copyOf(p.getData(), p.getLength());
                if (startsWith(data, DISCOVERY)) {
                    onDiscovery(data, p.getSocketAddress(), via);
                } else if (via != null && startsWith(data, MEASUREMENT)) {
                    onMeasurement(data, p.getSocketAddress(), s);
                }
            } catch (SocketTimeoutException e) {
                // check whether we were stopped
            } catch (IOException | RuntimeException e) {
                if (running && !s.isClosed()) {
                    System.err.println("Ableton Link receive: " + e);
                }
            }
        }
    }

    private void onDiscovery(byte[] data, SocketAddress from, Iface via) {
        if (data.length < 20) {
            return;
        }
        ByteBuffer b = ByteBuffer.wrap(data);
        b.position(8);
        int type = b.get() & 0xFF;
        b.get(); // time to live
        b.getShort(); // group
        byte[] id = new byte[8];
        b.get(id);
        if (Arrays.equals(id, nodeId)) {
            return; // our own announcement, looped back
        }
        String key = HexFormat.of().formatHex(id);
        if (type == BYE) {
            peers.remove(key);
            return;
        }
        Timeline tl = null;
        StartStop ss = null;
        byte[] sess = null;
        InetSocketAddress endpoint = null;
        while (b.remaining() >= 8) {
            int k = b.getInt();
            int size = b.getInt();
            if (size < 0 || size > b.remaining()) {
                return;
            }
            int start = b.position();
            if (k == TMLN && size >= 24) {
                tl = new Timeline(b.getLong(), b.getLong(), b.getLong());
            } else if (k == SESS && size >= 8) {
                sess = new byte[8];
                b.get(sess);
            } else if (k == STST && size >= 17) {
                ss = new StartStop(b.get() != 0, b.getLong(), b.getLong());
            } else if (k == MEP4 && size >= 6) {
                byte[] a = new byte[4];
                b.get(a);
                int port = b.getShort() & 0xFFFF;
                try {
                    endpoint = new InetSocketAddress(InetAddress.getByAddress(a), port);
                } catch (IOException e) {
                    endpoint = null;
                }
            }
            b.position(start + size);
        }
        if (tl == null || sess == null || tl.microsPerBeat() <= 0) {
            return;
        }
        peers.put(key, new Peer(sess, tl, ss, endpoint, System.nanoTime()));
        if (type == ALIVE) {
            Iface out = via != null ? via : ifaceFor(from);
            if (out != null) {
                sendQuietly(out.socket(), state(RESPONSE, out), from);
            }
        }
        sawPeer(sess, tl, ss, endpoint);
    }

    private synchronized void sawPeer(byte[] sess, Timeline tl, StartStop ss, InetSocketAddress endpoint) {
        if (!running) {
            return;
        }
        if (Arrays.equals(sess, session)) {
            if (tl.beatOrigin() > timeline.beatOrigin()) {
                timeline = clamp(tl); // someone changed the tempo; we lead again on the next tick if a deck leads
            }
            if (ss != null && ss.timestamp() > startStop.timestamp()) {
                startStop = ss; // start/stop is not ours to control, we only pass it on
            }
            return;
        }
        String key = HexFormat.of().formatHex(sess);
        if (endpoint == null || measurements.containsKey(key) || failedUntil.getOrDefault(key, 0L) > System.nanoTime()) {
            return;
        }
        Measurement m = new Measurement(sess, endpoint);
        measurements.put(key, m);
        ping(m, null);
    }

    private void onMeasurement(byte[] data, SocketAddress from, MulticastSocket s) {
        if (data.length < 9) {
            return;
        }
        int type = data[8] & 0xFF;
        if (type == PING) {
            if (data.length > 9 + 64) {
                return; // never answer large pings with larger pongs
            }
            byte[] sess;
            long ghost;
            synchronized (this) {
                sess = session;
                ghost = hostMicros() + intercept;
            }
            ByteBuffer pong = ByteBuffer.allocate(9 + 16 + 16 + data.length - 9);
            pong.put(MEASUREMENT).put((byte) PONG);
            pong.putInt(SESS).putInt(8).put(sess);
            pong.putInt(GHOST_TIME).putInt(8).putLong(ghost);
            pong.put(data, 9, data.length - 9);
            sendQuietly(s, pong.array(), from);
        } else if (type == PONG) {
            onPong(data, s);
        }
    }

    private void onPong(byte[] data, MulticastSocket s) {
        long now = hostMicros();
        ByteBuffer b = ByteBuffer.wrap(data);
        b.position(9);
        byte[] sess = null;
        Long ghost = null, prevGhost = null, host = null;
        while (b.remaining() >= 8) {
            int k = b.getInt();
            int size = b.getInt();
            if (size < 0 || size > b.remaining()) {
                return;
            }
            int start = b.position();
            if (k == SESS && size >= 8) {
                sess = new byte[8];
                b.get(sess);
            } else if (k == GHOST_TIME && size >= 8) {
                ghost = b.getLong();
            } else if (k == PREV_GHOST_TIME && size >= 8) {
                prevGhost = b.getLong();
            } else if (k == HOST_TIME && size >= 8) {
                host = b.getLong();
            }
            b.position(start + size);
        }
        if (sess == null || ghost == null || host == null) {
            return;
        }
        Measurement m = measurements.get(HexFormat.of().formatHex(sess));
        if (m == null) {
            return;
        }
        synchronized (m) {
            m.data.add(ghost - (now + host) / 2.0);
            if (prevGhost != null && prevGhost != 0) {
                m.data.add((ghost + prevGhost) / 2.0 - host);
            }
            if (m.data.size() > MEASUREMENT_POINTS) {
                measurements.remove(HexFormat.of().formatHex(sess));
                measured(sess, Math.round(median(m.data)));
            } else {
                m.retries = 0;
                ping(m, ghost);
            }
        }
    }

    /** Resends lost pings and gives up on peers that do not answer. */
    private void measureTick() {
        long now = System.nanoTime();
        for (Map.Entry<String, Measurement> e : measurements.entrySet()) {
            Measurement m = e.getValue();
            synchronized (m) {
                if (now - m.lastPingNanos > 50_000_000L) {
                    if (++m.retries > 5) {
                        measurements.remove(e.getKey());
                        failedUntil.put(e.getKey(), now + 5_000_000_000L);
                    } else {
                        ping(m, null);
                    }
                }
            }
        }
        synchronized (this) {
            // A joined session is measured again now and then, so that small clock differences do not add up.
            if (!Arrays.equals(session, nodeId) && now - lastRemeasureNanos > 30_000_000_000L) {
                lastRemeasureNanos = now;
                for (Peer p : peers.values()) {
                    String key = HexFormat.of().formatHex(session);
                    if (Arrays.equals(p.session(), session) && p.endpoint() != null && !measurements.containsKey(key)) {
                        Measurement m = new Measurement(session.clone(), p.endpoint());
                        measurements.put(key, m);
                        ping(m, null);
                        break;
                    }
                }
            }
        }
    }

    private void ping(Measurement m, Long prevGhost) {
        ByteBuffer b = ByteBuffer.allocate(9 + 16 + (prevGhost != null ? 16 : 0));
        b.put(MEASUREMENT).put((byte) PING);
        b.putInt(HOST_TIME).putInt(8).putLong(hostMicros());
        if (prevGhost != null) {
            b.putInt(PREV_GHOST_TIME).putInt(8).putLong(prevGhost);
        }
        m.lastPingNanos = System.nanoTime();
        Iface out = ifaceFor(m.endpoint);
        if (out != null) {
            sendQuietly(out.socket(), b.array(), m.endpoint);
        }
    }

    /** A measurement finished: stay, take the new clock of our own session, or switch to the older session. */
    private synchronized void measured(byte[] sess, long newIntercept) {
        if (!running) {
            return;
        }
        if (Arrays.equals(sess, session)) {
            intercept = newIntercept;
            return;
        }
        long diff = newIntercept - intercept;
        if (diff > SESSION_EPS_MICROS || (Math.abs(diff) < SESSION_EPS_MICROS && compare(sess, session) < 0)) {
            Peer latest = null;
            for (Peer p : peers.values()) {
                if (Arrays.equals(p.session(), sess) && (latest == null || p.timeline().beatOrigin() > latest.timeline().beatOrigin())) {
                    latest = p;
                }
            }
            if (latest == null) {
                return; // the session is gone already
            }
            session = sess.clone();
            intercept = newIntercept;
            timeline = clamp(latest.timeline());
            startStop = latest.startStop() != null ? latest.startStop() : new StartStop(false, 0, 0);
            lastRemeasureNanos = System.nanoTime();
            broadcastAlive();
        }
    }

    // ------------------------------------------------------------------ leading

    /** Every 10 ms: brings the session's tempo, and with {@code phase} its beat, in line with the leading deck. */
    private void lead() {
        Deck lead = lightDesk.leadingDeck();
        String problem = null;
        if (lead == null) {
            problem = "No deck with a tempo: the Link session keeps its tempo.";
        } else if (lead.scope != null && "demo".equals(lead.scope.kind) && !lightDesk.sendDemo()) {
            problem = "Demo mode: the Link session is not led.";
        } else if (lead.scope != null && "link".equals(lead.scope.kind)) {
            problem = "SEND BPM takes the Link session itself: choose a DJ deck to lead Link.";
        }
        Deck sending = problem == null ? lead : null;
        for (Deck d : hub.allDecks()) {
            d.sendingLink = d == sending;
        }
        synchronized (this) {
            if (!running) {
                return;
            }
            note = problem == null ? "" : problem;
            sendingDeck = sending == null ? null : lightDesk.keyOf(sending);
            if (sending == null) {
                return;
            }
            double bpm = Math.max(20, Math.min(999, sending.stableBpm(System.nanoTime())));
            long microsPerBeat = Math.round(60e6 / bpm);
            long nowNanos = System.nanoTime();
            long ghost = hostMicros() + intercept;
            boolean tempoOff = Math.abs(timeline.bpm() - bpm) >= 0.005;
            double sessionBeat = timeline.beatsAt(ghost);
            Double deckBeat = phase ? deckBeat(sending, nowNanos) : null;
            if (deckBeat != null) {
                double error = Math.floorMod(Math.round((deckBeat - sessionBeat) * 1e6), 4_000_000L) / 1e6;
                if (error >= 2) {
                    error -= 4; // the shorter way round the bar
                }
                double errorMs = error * 60_000 / bpm;
                if (tempoOff || (Math.abs(errorMs) > PHASE_TOLERANCE_MS && nowNanos - lastPhaseFixNanos > PHASE_FIX_INTERVAL_NANOS)) {
                    lastPhaseFixNanos = nowNanos;
                    setTimeline(new Timeline(microsPerBeat, Math.round((sessionBeat + error) * 1e6), ghost));
                }
            } else if (tempoOff) {
                setTimeline(new Timeline(microsPerBeat, Math.round(sessionBeat * 1e6), ghost));
            }
        }
    }

    /**
     * The deck's beat right now on a bar-aligned count (a multiple of 4 on the first beat of a bar), from its beat grid
     * and position; null when it does not play or has no grid and position.
     */
    static Double deckBeat(Deck d, long nowNanos) {
        int[] times = d.beatTimes;
        if (!d.playing || d.velocity <= 0.05 || times == null || times.length < 2) {
            return null;
        }
        long position = d.predictedPositionMs(nowNanos);
        if (position < 0) {
            return null;
        }
        int i = Arrays.binarySearch(times, (int) position);
        if (i < 0) {
            i = -i - 2;
        }
        if (i < 0 || i >= times.length - 1) {
            return null; // before the first or after the last beat of the grid
        }
        double frac = (position - times[i]) / (double) (times[i + 1] - times[i]);
        byte[] bars = d.beatBars;
        int bar = bars != null && bars.length > i && bars[i] >= 1 && bars[i] <= 4 ? bars[i] : 1;
        // Counted so that the beat's place in the bar is its count modulo 4 (first beat of a bar: a multiple of 4).
        return i + Math.floorMod(bar - 1 - i, 4) + frac;
    }

    private void setTimeline(Timeline t) {
        long beatOrigin = t.beatOrigin();
        long timeOrigin = t.timeOrigin();
        // Peers keep the timeline with the larger beat origin, so ours must be larger; whole bars later is the same line.
        while (beatOrigin <= timeline.beatOrigin()) {
            beatOrigin += 4_000_000;
            timeOrigin += 4 * t.microsPerBeat();
        }
        timeline = new Timeline(t.microsPerBeat(), beatOrigin, timeOrigin);
        broadcastAlive();
    }

    private static Timeline clamp(Timeline t) {
        long min = Math.round(60e6 / 999), max = Math.round(60e6 / 20);
        return new Timeline(Math.max(min, Math.min(max, t.microsPerBeat())), t.beatOrigin(), t.timeOrigin());
    }

    // ------------------------------------------------------------------ sending

    private synchronized void broadcastAlive() {
        if (!running) {
            return;
        }
        for (Iface i : ifaces) {
            sendQuietly(i.socket(), state(ALIVE, i), groupAddress());
        }
    }

    private synchronized byte[] state(int type, Iface via) {
        ByteBuffer b = header(type, TTL_SECONDS);
        b.putInt(TMLN).putInt(24).putLong(timeline.microsPerBeat()).putLong(timeline.beatOrigin()).putLong(timeline.timeOrigin());
        b.putInt(SESS).putInt(8).put(session);
        b.putInt(STST).putInt(17).put((byte) (startStop.playing() ? 1 : 0)).putLong(startStop.beats()).putLong(startStop.timestamp());
        b.putInt(MEP4).putInt(6).put(via.address().getAddress()).putShort((short) via.socket().getLocalPort());
        return Arrays.copyOf(b.array(), b.position());
    }

    private ByteBuffer header(int type, int ttl) {
        ByteBuffer b = ByteBuffer.allocate(256);
        b.put(DISCOVERY).put((byte) type).put((byte) ttl).putShort((short) 0).put(nodeId);
        return b;
    }

    private Iface ifaceFor(SocketAddress to) {
        if (to instanceof InetSocketAddress a && a.getAddress() instanceof Inet4Address target) {
            for (Iface i : ifaces) {
                if (sameNetwork(i.address(), target, i.prefix())) {
                    return i;
                }
            }
        }
        return ifaces.isEmpty() ? null : ifaces.get(0);
    }

    private static void sendQuietly(MulticastSocket s, byte[] data, SocketAddress to) {
        try {
            s.send(new DatagramPacket(data, data.length, to));
        } catch (IOException e) {
            // an adapter that just went away; the next scan drops it
        }
    }

    // ------------------------------------------------------------------ helpers

    private void prunePeers() {
        long now = System.nanoTime();
        peers.values().removeIf(p -> now - p.seenNanos() > TTL_SECONDS * 1_000_000_000L);
    }

    private static long hostMicros() {
        return System.nanoTime() / 1000;
    }

    private static InetSocketAddress groupAddress() {
        return new InetSocketAddress(AbletonLink.GROUP, AbletonLink.PORT);
    }

    private static int key(String s) {
        return (s.charAt(0) << 24) | (s.charAt(1) << 16) | (s.charAt(2) << 8) | s.charAt(3);
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        return data.length >= prefix.length && Arrays.equals(data, 0, prefix.length, prefix, 0, prefix.length);
    }

    static boolean sameNetwork(InetAddress a, InetAddress b, int prefix) {
        int x = ByteBuffer.wrap(a.getAddress()).getInt(), y = ByteBuffer.wrap(b.getAddress()).getInt();
        int mask = prefix <= 0 ? 0 : prefix >= 32 ? -1 : -1 << (32 - prefix);
        return (x & mask) == (y & mask);
    }

    /** Compares session ids byte by byte, unsigned, like the Link library orders them. */
    static int compare(byte[] a, byte[] b) {
        return Arrays.compareUnsigned(a, b);
    }

    static double median(List<Double> values) {
        List<Double> v = new ArrayList<>(values);
        Collections.sort(v);
        int n = v.size();
        return n % 2 == 1 ? v.get(n / 2) : (v.get(n / 2 - 1) + v.get(n / 2)) / 2;
    }

    private static byte[] newNodeId() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom r = new SecureRandom();
        byte[] id = new byte[8];
        for (int i = 0; i < id.length; i++) {
            id[i] = (byte) chars.charAt(r.nextInt(chars.length()));
        }
        return id;
    }
}
