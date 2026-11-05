package dev.ljlinkpro;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens to an Ableton Link session (Serato DJ Pro, Traktor, rekordbox, Ableton Live and many apps) and reports its tempo.
 * Passive: it only reads the peers' announcements on the Link multicast group and never joins or changes the session.
 *
 * <p>Wire format, as published in Ableton's open source Link library (discovery v1): header {@code "_asdp_v" 1}, message type
 * (1 = alive, 2 = response, 3 = bye), time to live, group id (2 bytes), peer id (8 bytes), then entries of a four
 * character key, a 4 byte size and the value. The timeline entry {@code tmln} starts with the tempo as microseconds per beat.
 */
final class AbletonLink {
    static final String GROUP = "224.76.78.75";
    static final int PORT = 20808;
    private static final byte[] HEADER = {'_', 'a', 's', 'd', 'p', '_', 'v', 1};
    private static final int TMLN = ('t' << 24) | ('m' << 16) | ('l' << 8) | 'n';
    private static final int STST = ('s' << 24) | ('t' << 16) | ('s' << 8) | 't';
    /** A peer that has not announced itself for this long has left. */
    private static final long PEER_TIMEOUT_MS = 6_000;

    record State(double bpm, Boolean playing, int peers) {}

    private record Peer(double bpm, Boolean playing, long seen) {}

    /** The peer id of LJ Link's own Link peer ({@link AbletonLinkPeer}), which is not one of the programs it shows. */
    static volatile String ignoredPeer;

    private final Map<String, Peer> peers = new ConcurrentHashMap<>();
    private volatile boolean stopped;
    private MulticastSocket socket;

    /** Parses one announcement: the tempo in BPM (or null) and the play state (or null when not sent). */
    static Object[] parse(byte[] data, int length) {
        if (length < 20) {
            return null;
        }
        for (int i = 0; i < HEADER.length; i++) {
            if (data[i] != HEADER[i]) {
                return null;
            }
        }
        ByteBuffer b = ByteBuffer.wrap(data, 0, length);
        b.position(8);
        int type = b.get() & 0xFF;
        b.get(); // time to live
        b.getShort(); // group
        byte[] id = new byte[8];
        b.get(id);
        if (type == 3) {
            return new Object[] {java.util.HexFormat.of().formatHex(id), null, null, true};
        }
        Double bpm = null;
        Boolean playing = null;
        while (b.remaining() >= 8) {
            int key = b.getInt();
            int size = b.getInt();
            if (size < 0 || size > b.remaining()) {
                break;
            }
            int start = b.position();
            if (key == TMLN && size >= 8) {
                long microsPerBeat = b.getLong();
                if (microsPerBeat > 0) {
                    bpm = 60_000_000.0 / microsPerBeat;
                }
            } else if (key == STST && size >= 1) {
                playing = b.get() != 0;
            }
            b.position(start + size);
        }
        return new Object[] {java.util.HexFormat.of().formatHex(id), bpm, playing, false};
    }

    void start() {
        Thread t = new Thread(this::run, "ableton-link");
        t.setDaemon(true);
        t.start();
    }

    private void run() {
        try {
            socket = new MulticastSocket(null);
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(PORT));
            socket.setSoTimeout(1000);
            InetAddress group = InetAddress.getByName(GROUP);
            boolean joined = false;
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                try {
                    if (ni.isUp() && ni.supportsMulticast()) {
                        socket.joinGroup(new InetSocketAddress(group, PORT), ni);
                        joined = true;
                    }
                } catch (IOException e) {
                    // an interface without multicast: the others still work
                }
            }
            if (!joined) {
                socket.joinGroup(new InetSocketAddress(group, PORT), null);
            }
        } catch (IOException e) {
            System.err.println("Ableton Link unavailable: " + e.getMessage());
            return;
        }
        byte[] buf = new byte[2048];
        while (!stopped) {
            try {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                socket.receive(p);
                Object[] m = parse(buf, p.getLength());
                if (m == null) {
                    continue;
                }
                String id = (String) m[0];
                if (id.equals(ignoredPeer)) {
                    continue;
                }
                if ((Boolean) m[3]) {
                    peers.remove(id);
                } else if (m[1] != null) {
                    peers.put(id, new Peer((Double) m[1], (Boolean) m[2], System.currentTimeMillis()));
                }
            } catch (SocketTimeoutException e) {
                // keep looping so that stop() is noticed
            } catch (IOException e) {
                if (!stopped) {
                    System.err.println("Ableton Link ended: " + e.getMessage());
                }
                return;
            }
        }
    }

    /** The session tempo (all peers share it), or null when nobody is in a Link session. */
    State state() {
        long now = System.currentTimeMillis();
        peers.values().removeIf(p -> now - p.seen() > PEER_TIMEOUT_MS);
        Peer newest = null;
        for (Peer p : peers.values()) {
            if (newest == null || p.seen() > newest.seen()) {
                newest = p;
            }
        }
        return newest == null ? null : new State(newest.bpm(), newest.playing(), peers.size());
    }

    void stop() {
        stopped = true;
        if (socket != null) {
            socket.close();
        }
    }
}
