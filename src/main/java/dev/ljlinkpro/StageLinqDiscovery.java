package dev.ljlinkpro;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Finds Denon devices (StageLinQ) and gives each its own {@link StageLinqSource} and {@link Hub.Scope}, so several
 * controllers can be shown at the same time and each can come and go. It announces itself like the other StageLinQ
 * clients and stays passive.
 */
final class StageLinqDiscovery implements DeckSource {
    private static final String SOFTWARE_NAME = "LJ Link";
    private static final String SOFTWARE_VERSION = "1.4.4";
    /** Helper services some Denon devices announce next to the real device; their "decks" are not players. */
    private static final java.util.Set<String> IGNORED_SOFTWARE = java.util.Set.of("offlineanalyzer");
    /** A device that has not announced itself for this long is gone. */
    private static final long GONE_AFTER_MS = 20_000;
    /** A connection that delivers nothing for this long is dropped and made again (the device may still hold an old one). */
    private static final long CONNECT_TIMEOUT_MS = 45_000;
    /** After a connection ended the device needs a moment before it accepts a new one; reconnecting at once loops. */
    private static final long RECONNECT_PAUSE_MS = 10_000;

    private record Known(StageLinqSource source, Hub.Scope scope, long[] lastSeen, long since) {}

    private final Hub hub;
    private final Hub.Scope scope;
    private final byte[] token = new byte[16];
    private final Map<String, Known> devices = new ConcurrentHashMap<>();
    private final Map<String, Long> pausedUntil = new ConcurrentHashMap<>();
    private volatile boolean stopped;
    private DatagramSocket socket;
    private Thread discoveryThread;
    private Thread announceThread;

    StageLinqDiscovery(Hub hub, Hub.Scope scope) {
        this.hub = hub;
        this.scope = scope;
        new SecureRandom().nextBytes(token);
        scope.setSource(this);
    }

    @Override
    public void start() {
        discoveryThread = new Thread(this::discoveryLoop, "stagelinq-discovery");
        discoveryThread.setDaemon(true);
        discoveryThread.start();
    }

    private void discoveryLoop() {
        scope.setState("searching", "live", "Searching for Denon devices (StageLinQ) …");
        try {
            socket = new DatagramSocket(null);
            socket.setReuseAddress(true);
            socket.setBroadcast(true);
            socket.bind(new InetSocketAddress(StageLinqProtocol.DISCOVERY_PORT));
            socket.setSoTimeout(500);
        } catch (IOException e) {
            // Another program (for example Engine DJ) may hold the port; the other makers keep working.
            System.err.println("StageLinQ discovery unavailable: " + e.getMessage());
            scope.setState("idle", "live", "");
            return;
        }
        announceThread = new Thread(this::announceLoop, "stagelinq-announce");
        announceThread.setDaemon(true);
        announceThread.start();

        byte[] buf = new byte[8192];
        while (!stopped) {
            try {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                socket.receive(p);
                StageLinqProtocol.Discovery d = StageLinqProtocol.parseDiscovery(buf, p.getLength());
                if (d == null || java.util.Arrays.equals(d.token(), token)) {
                    continue; // not StageLinQ, or our own announcement
                }
                if (StageLinqProtocol.HOWDY.equals(d.action())) {
                    onDevice(p.getAddress(), d);
                } else if (StageLinqProtocol.EXIT.equals(d.action())) {
                    drop(HexFormat.of().formatHex(d.token()));
                }
            } catch (SocketTimeoutException e) {
                // keep looping so that stop() is noticed
            } catch (IOException e) {
                if (!stopped) {
                    System.err.println("StageLinQ discovery ended: " + e.getMessage());
                }
                return;
            }
        }
    }

    private void announceLoop() {
        byte[] packet = StageLinqProtocol.discovery(new StageLinqProtocol.Discovery(
                token, "ljlinkpro", StageLinqProtocol.HOWDY, SOFTWARE_NAME, SOFTWARE_VERSION, 0));
        while (!stopped) {
            for (InetAddress broadcast : broadcastAddresses()) {
                try {
                    socket.send(new DatagramPacket(packet, packet.length, broadcast, StageLinqProtocol.DISCOVERY_PORT));
                } catch (IOException e) {
                    // an interface may disappear; the next round tries again
                }
            }
            long now = System.currentTimeMillis();
            for (Map.Entry<String, Known> e : devices.entrySet()) {
                Known k = e.getValue();
                if (now - k.lastSeen()[0] > GONE_AFTER_MS || !k.source().isConnected() && now - k.since() > CONNECT_TIMEOUT_MS) {
                    drop(e.getKey()); // the next announcement of the device connects it again
                }
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    static List<InetAddress> broadcastAddresses() {
        List<InetAddress> out = new ArrayList<>();
        try {
            out.add(InetAddress.getByName("255.255.255.255"));
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                ni.getInterfaceAddresses().stream()
                        .map(a -> a.getBroadcast())
                        .filter(b -> b != null && !b.getHostAddress().startsWith("169.254."))
                        .forEach(out::add);
            }
        } catch (IOException e) {
            // fall back to what we have
        }
        return out;
    }

    /** The announcing software is a firmware code such as "JC11"; the source name tells the model ("prime4plus"). */
    static String displayName(StageLinqProtocol.Discovery d) {
        String source = d.source().isBlank() ? d.softwareName() : d.source();
        return switch (source.toLowerCase()) {
            case "prime4" -> "Denon Prime 4";
            case "prime4plus" -> "Denon Prime 4+";
            case "primego" -> "Denon Prime Go";
            case "sc6000" -> "Denon SC6000";
            case "sc6000m" -> "Denon SC6000M";
            case "sc5000" -> "Denon SC5000";
            default -> source;
        };
    }

    private void onDevice(InetAddress address, StageLinqProtocol.Discovery d) {
        String key = HexFormat.of().formatHex(d.token());
        Known known = devices.get(key);
        if (known != null) {
            known.lastSeen()[0] = System.currentTimeMillis();
            return;
        }
        Long pause = pausedUntil.get(key);
        if (pause != null && System.currentTimeMillis() < pause) {
            return;
        }
        if (IGNORED_SOFTWARE.contains(d.softwareName().toLowerCase())) {
            return; // internal helper services of the device, they report decks that do not exist
        }
        if (d.port() == 0 || SOFTWARE_NAME.equalsIgnoreCase(d.softwareName())) {
            return; // another listener (a second LJ Link, lighting software): it offers no decks
        }
        System.out.println("StageLinQ device: " + d.source() + " / " + d.softwareName() + " " + d.softwareVersion()
                + " at " + address.getHostAddress() + ":" + d.port());
        String name = displayName(d);
        Hub.Scope deviceScope = hub.scope("denon-" + key, "denon", false, name);
        deviceScope.setDevices(List.of(new Hub.DeviceInfo(0, name, address.getHostAddress())));
        deviceScope.setState("searching", "live", "Denon device found – connecting …");
        StageLinqSource source = new StageLinqSource(deviceScope, token, name, () -> drop(key));
        devices.put(key, new Known(source, deviceScope, new long[] {System.currentTimeMillis()}, System.currentTimeMillis()));
        source.connectDevice(address.getHostAddress(), d.port(), d.token());
    }

    /** The device is gone (switched off, unplugged, left the network): its decks disappear, it may come back later. */
    private void drop(String key) {
        Known known = devices.remove(key);
        if (known != null) {
            pausedUntil.put(key, System.currentTimeMillis() + RECONNECT_PAUSE_MS);
            System.out.println("StageLinQ device left: " + known.scope().groupName);
            known.scope().close();
            known.source().stop();
        }
    }

    @Override
    public java.util.List<java.util.Map<String, Object>> info(int deck) {
        return java.util.List.of();
    }

    @Override
    public void stop() {
        stopped = true;
        if (socket != null) {
            try {
                byte[] bye = StageLinqProtocol.discovery(new StageLinqProtocol.Discovery(
                        token, "ljlinkpro", StageLinqProtocol.EXIT, SOFTWARE_NAME, SOFTWARE_VERSION, 0));
                for (InetAddress b : broadcastAddresses()) {
                    socket.send(new DatagramPacket(bye, bye.length, b, StageLinqProtocol.DISCOVERY_PORT));
                }
            } catch (IOException | RuntimeException e) {
                // leaving quietly is fine
            }
            socket.close();
        }
        if (announceThread != null) {
            announceThread.interrupt();
        }
        for (Known k : devices.values()) {
            k.source().stop();
        }
        devices.clear();
    }

    @Override
    public java.util.List<String> diagnostics() {
        java.util.List<String> out = new java.util.ArrayList<>();
        out.add("StageLinQ discovery: socket " + (socket != null && !socket.isClosed() ? "open on UDP " + StageLinqProtocol.DISCOVERY_PORT : "not open"));
        for (Known k : devices.values()) {
            out.add("Denon device " + k.scope().groupName + ": connected " + k.source().isConnected()
                    + ", last announcement " + (System.currentTimeMillis() - k.lastSeen()[0]) + " ms ago");
        }
        if (devices.isEmpty()) {
            out.add("No Denon device seen");
        }
        return out;
    }
}
