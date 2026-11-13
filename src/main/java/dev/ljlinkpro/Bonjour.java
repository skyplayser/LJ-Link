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
 * Announces LJ Link in the local network via mDNS/Bonjour as {@code _ljlink._tcp} (TXT {@code vjlink=1}, {@code path=/vjlink}),
 * so programs like VJ-Link find it on another computer without typing an address. One announcement per network
 * adapter with an IPv4 address. Failures are only logged: everything else works without it.
 */
final class Bonjour {
    static final String TYPE = "_ljlink._tcp.local.";
    private final List<JmDNS> running = new ArrayList<>();

    /** Starts the announcement in the background (JmDNS takes a moment per adapter). */
    static Bonjour announce(int port, String version) {
        Bonjour b = new Bonjour();
        Thread t = new Thread(() -> b.start(port, version), "bonjour");
        t.setDaemon(true);
        t.start();
        Runtime.getRuntime().addShutdownHook(new Thread(b::close));
        return b;
    }

    private synchronized void start(int port, String version) {
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
                dns.registerService(ServiceInfo.create(TYPE, name, port, 0, 0,
                        Map.of("vjlink", "1", "path", "/vjlink", "version", String.valueOf(VjLink.VERSION), "app", version)));
                running.add(dns);
                System.out.println("Bonjour: announced " + TYPE + " on " + addr.getHostAddress() + ":" + port);
            } catch (java.io.IOException | RuntimeException e) {
                System.err.println("Bonjour on " + addr.getHostAddress() + " failed: " + e);
            }
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
