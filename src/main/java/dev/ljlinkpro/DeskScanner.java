package dev.ljlinkpro;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Looks for a grandMA2 (onPC or console) in the local networks. OSC runs over UDP and does not answer, so UDP cannot be
 * probed. The grandMA2 telnet port (TCP 30000) can: it greets with a login prompt (or "Remote commandline disabled", which still shows that a grandMA2 is there). Whatever answers there is offered to the
 * user; a desk with telnet switched off is entered by hand. Read only, nothing is sent except the TCP connect.
 */
final class DeskScanner {
    private DeskScanner() {}

    static final int MA2_TELNET_PORT = 30000;

    record Found(String host, String name, boolean sure) {}

    static List<Found> scan() {
        Set<String> hosts = new LinkedHashSet<>();
        hosts.add("127.0.0.1"); // onPC is often on the same computer
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                ni.getInterfaceAddresses().stream()
                        .filter(a -> a.getAddress() instanceof Inet4Address && !a.getAddress().isLinkLocalAddress())
                        .forEach(a -> {
                            hosts.add(a.getAddress().getHostAddress());
                            byte[] ip = a.getAddress().getAddress();
                            int prefix = Math.max(a.getNetworkPrefixLength(), 22); // never scan more than four /24 blocks
                            int count = 1 << (32 - prefix);
                            int base = ((ip[0] & 0xFF) << 24 | (ip[1] & 0xFF) << 16 | (ip[2] & 0xFF) << 8 | (ip[3] & 0xFF)) & (-1 << (32 - prefix));
                            for (int i = 1; i < count - 1; i++) {
                                int v = base + i;
                                hosts.add((v >>> 24) + "." + (v >> 16 & 0xFF) + "." + (v >> 8 & 0xFF) + "." + (v & 0xFF));
                            }
                        });
            }
        } catch (IOException e) {
            // scan what we have
        }
        ExecutorService pool = Executors.newFixedThreadPool(96);
        List<Future<Found>> futures = new ArrayList<>();
        for (String h : hosts) {
            futures.add(pool.submit(() -> probe(h)));
        }
        List<Found> out = new ArrayList<>();
        for (Future<Found> f : futures) {
            try {
                Found found = f.get(15, TimeUnit.SECONDS);
                if (found != null) {
                    out.add(found);
                }
            } catch (Exception e) {
                // a host that does not answer in time is simply not a desk
            }
        }
        pool.shutdownNow();
        return out;
    }

    private static Found probe(String host) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, MA2_TELNET_PORT), 400);
            s.setSoTimeout(700);
            String greeting = "";
            try {
                InputStream in = s.getInputStream();
                byte[] buf = new byte[256];
                int n = in.read(buf);
                greeting = n > 0 ? new String(buf, 0, n, StandardCharsets.ISO_8859_1) : "";
            } catch (IOException e) {
                // no greeting: still something listens on the telnet port
            }
            String g = greeting.toLowerCase();
            boolean ma = g.contains("login") || g.contains("remote commandline");
            String name = host.equals("127.0.0.1") ? "grandMA2 onPC (this computer)" : "grandMA2 (" + host + ")";
            return new Found(host, ma ? name : "Telnet port 30000 open (" + host + ")", ma);
        } catch (IOException e) {
            return null;
        }
    }
}
