package dev.ljlinkpro;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

/**
 * Announces LJ Link in the local network via mDNS/Bonjour as {@code _ljlink._tcp} (TXT {@code vjlink=1}, {@code path=/vjlink},
 * and for LJL Link {@code sender=1}, {@code senderpath=/sender}, {@code pair=1}, {@code booth=<id>}), so programs like
 * VJ-Link find it on another
 * computer without typing an address. One announcement per network
 * adapter with an IPv4 address. Failures are only logged: everything else works without it.
 */
final class Bonjour {
    static final String TYPE = "_ljlink._tcp.local.";
    private final List<JmDNS> running = new ArrayList<>();
    /** The registered services, one per adapter; their TXT is replaced when the booth id changes. */
    private final List<ServiceInfo> services = new ArrayList<>();
    private final java.util.function.Supplier<String> booth;
    private String version = "";

    private Bonjour(java.util.function.Supplier<String> booth) {
        this.booth = booth;
    }

    /** Starts the announcement in the background (JmDNS takes a moment per adapter); the booth id from the settings. */
    static Bonjour announce(int port, String version) {
        return announce(port, version, SenderClub::booth);
    }

    static Bonjour announce(int port, String version, java.util.function.Supplier<String> booth) {
        Bonjour b = new Bonjour(booth);
        Thread t = new Thread(() -> b.start(port, version), "bonjour");
        t.setDaemon(true);
        t.start();
        Runtime.getRuntime().addShutdownHook(new Thread(b::close));
        return b;
    }

    private synchronized void start(int port, String version) {
        this.version = version;
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName().replaceFirst("\\..*$", "");
        } catch (java.io.IOException e) {
            host = "computer";
        }
        String name = "LJ Link (" + host + ")" + (port == 8080 ? "" : " :" + port);
        for (InetAddress addr : addresses()) {
            try {
                JmDNS dns = JmDNS.create(addr, host);
                ServiceInfo info = ServiceInfo.create(TYPE, name, port, 0, 0, txt(version, booth.get()));
                dns.registerService(info);
                running.add(dns);
                services.add(info);
                System.out.println("Bonjour: announced " + TYPE + " on " + addr.getHostAddress() + ":" + port);
            } catch (java.io.IOException | RuntimeException e) {
                System.err.println("Bonjour on " + addr.getHostAddress() + " failed: " + e);
            }
        }
    }

    /**
     * The TXT record: VJ-Link ({@code vjlink}, {@code path}) and LJL Link ({@code sender}, {@code senderpath}; approval
     * without club.json: {@code pair=1}, {@code booth} = the booth id, see {@link SenderPairing}).
     */
    static Map<String, String> txt(String version, String booth) {
        return Map.of("vjlink", "1", "path", "/vjlink", "version", String.valueOf(VjLink.VERSION), "app", version,
                "sender", "1", "senderpath", "/sender", "pair", "1", "booth", booth);
    }

    /**
     * Announces the TXT record again with the current booth id ("New key"), on every adapter. JmDNS sends the changed
     * record at once ({@link ServiceInfo#setText(Map)}).
     */
    synchronized void reannounce() {
        Map<String, String> txt = txt(version, booth.get());
        for (ServiceInfo info : services) {
            try {
                info.setText(txt);
            } catch (RuntimeException e) {
                System.err.println("Bonjour: new TXT record failed: " + e);
            }
        }
        if (!services.isEmpty()) {
            System.out.println("Bonjour: announced again with a new booth id");
        }
    }

    private static List<InetAddress> addresses() {
        List<InetAddress> out = new ArrayList<>();
        try {
            for (NetworkInterface ni : java.util.Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual() || !ni.supportsMulticast()) {
                    continue;
                }
                for (InetAddress a : java.util.Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && !a.isLinkLocalAddress() && !a.isLoopbackAddress()) {
                        out.add(a);
                    }
                }
            }
        } catch (java.net.SocketException e) {
            System.err.println("Bonjour: no network adapters: " + e);
        }
        return out;
    }

    /** How long quitting waits for the Bonjour goodbyes; JmDNS waits several seconds per adapter otherwise. */
    static final long CLOSE_TIMEOUT_MS = 1500;

    /**
     * Withdraws the announcements on all adapters at once and waits at most {@link #CLOSE_TIMEOUT_MS}: the goodbye packets
     * go out at the start, the rest of JmDNS's close only waits. Before, closing one adapter after the other kept LJ Link
     * running for 8 s and more after "Quit".
     */
    synchronized void close() {
        List<Runnable> tasks = new ArrayList<>();
        for (JmDNS dns : running) {
            tasks.add(() -> {
                try {
                    dns.unregisterAllServices();
                    dns.close();
                } catch (java.io.IOException | RuntimeException e) {
                    // shutting down anyway
                }
            });
        }
        running.clear();
        services.clear();
        int open = closeAll(tasks, CLOSE_TIMEOUT_MS);
        if (open > 0) {
            System.err.println("Bonjour: " + open + " announcement(s) still closing, not waiting longer");
        }
    }

    /** Runs the tasks in parallel on daemon threads and waits at most {@code timeoutMs}; returns how many are still running. */
    static int closeAll(List<Runnable> tasks, long timeoutMs) {
        List<Thread> threads = new ArrayList<>();
        for (Runnable task : tasks) {
            Thread t = new Thread(task, "bonjour-close");
            t.setDaemon(true);
            t.start();
            threads.add(t);
        }
        long deadline = System.nanoTime() + timeoutMs * 1_000_000;
        int open = 0;
        for (Thread t : threads) {
            long left = (deadline - System.nanoTime()) / 1_000_000;
            try {
                if (left > 0) {
                    t.join(left);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (t.isAlive()) {
                open++;
            }
        }
        return open;
    }
}
