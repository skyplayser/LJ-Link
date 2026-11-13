package dev.ljlinkpro;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The diagnostic part of a problem report: a short summary, program and system, network adapters, what every source
 * sees, the decks and the last log lines. It never contains track titles, artists, covers or passwords; IPv6 addresses
 * are only counted (they can contain the hardware address).
 */
final class Diagnostics {
    /**
     * Starts the short part at the top of the report. The mail dialog (app.js) puts this part into the mail text when the
     * whole report is too long for a mailto link, so the most important lines arrive even without the attached file.
     */
    static final String SUMMARY_HEADER = "== Summary ==";

    private Diagnostics() {}

    /** One IPv4 address of an adapter. */
    record Ipv4(String address, int prefix) {}

    /** An active adapter: its IPv4 addresses and how many IPv6 addresses it has (not listed). */
    record Adapter(String name, String displayName, boolean virtual, List<Ipv4> ipv4, int ipv6, int ipv6LinkLocal) {}

    static String report(Hub hub, Session session, LinkService link, AbletonLinkPeer ableton) {
        List<Adapter> adapters = adapters();
        List<String> out = new ArrayList<>();
        out.add("== LJ Link diagnostics ==");
        out.add(SUMMARY_HEADER);
        out.addAll(summary(hub, session, adapters));
        out.add("");
        out.add("== Program ==");
        out.add("Version " + Updater.currentVersion() + ", " + ZonedDateTime.now());
        out.add("System " + System.getProperty("os.name") + " " + System.getProperty("os.version") + " (" + System.getProperty("os.arch")
                + "), Java " + System.getProperty("java.version"));
        out.add("Privacy mode " + Privacy.enabled() + ", language " + AppSettings.language());
        out.add("");
        out.add("== Network ==");
        out.addAll(network(adapters));
        out.add("");
        out.add("== State ==");
        out.addAll(hub.diagnostics());
        out.add("");
        out.add("== Sources ==");
        out.addAll(session.diagnostics());
        out.add("");
        out.add("== Light desk link ==");
        LinkService.Config c = link.config();
        LinkService.Status st = link.status();
        out.add("Type " + c.type() + ", target " + c.host() + ":" + c.port() + ", command " + c.command()
                + ", user " + (c.user() == null || c.user().isBlank() ? "none" : "set") + ", source " + st.source()
                + ", sent " + st.sentCount() + (st.error() == null ? "" : ", error: " + st.error()));
        out.add("");
        out.add("== Ableton Link (send) ==");
        AbletonLinkPeer.Status ab = ableton.status();
        out.add("Enabled " + ab.enabled() + ", phase " + ab.phase() + ", peers in session " + ab.peers()
                + (ab.bpm() == null ? "" : String.format(java.util.Locale.ROOT, ", session %.2f BPM", ab.bpm()))
                + ", leading deck " + (ab.sendingDeck() == null ? "none" : ab.sendingDeck())
                + (ab.note().isEmpty() ? "" : ", note: " + ab.note()));
        out.add("");
        out.add("== Log (last lines) ==");
        List<String> log = LogBuffer.lines();
        out.addAll(log.subList(Math.max(0, log.size() - 150), log.size()));
        return String.join("\n", out) + "\n";
    }

    /**
     * The most important lines, short and most important first (the mail dialog drops lines from the end when they do not
     * fit): version and system, adapters and network warnings, the state of every source, what the sources see, the decks.
     */
    static List<String> summary(Hub hub, Session session, List<Adapter> adapters) {
        return summary(hub, session.summary(), adapters);
    }

    /**
     * Order: version; every WARNING and NOTE (mixer without status, ShowKontrol, two adapters in one network); what the
     * sources see (own player number and adapter, players, media, seen devices); adapters; sources that found something;
     * decks; last the sources that are still searching or idle with nothing found.
     */
    static List<String> summary(Hub hub, List<String> sourceLines, List<Adapter> adapters) {
        List<String> rest = new ArrayList<>(sourceLines);
        rest.add(adapterSummary(adapters));
        List<String> warnings = networkWarnings(adapters);
        if (warnings.stream().anyMatch(l -> l.startsWith("NOTE: an adapter has a self-assigned"))) {
            rest.add("Self-assigned address 169.254.x.x (no DHCP)"); // only a detail here, not one of the notes in front
        }
        rest.addAll(hub.summarySources(true));
        rest.addAll(hub.summaryDecks());
        rest.addAll(hub.summarySources(false));
        List<String> out = new ArrayList<>();
        out.add("LJ Link " + Updater.currentVersion() + ", " + System.getProperty("os.name") + " " + System.getProperty("os.version")
                + " " + System.getProperty("os.arch") + ", Java " + System.getProperty("java.version"));
        warnings.stream().filter(l -> l.startsWith("WARNING:")).forEach(out::add);
        rest.stream().filter(Diagnostics::important).forEach(out::add);
        rest.stream().filter(l -> !important(l)).forEach(out::add);
        return out;
    }

    private static boolean important(String line) {
        return line.startsWith("WARNING:") || line.startsWith("NOTE:");
    }

    /** "Adapters: en0 172.16.200.50/24, en5 169.254.131.110/16" (IPv4 only). */
    static String adapterSummary(List<Adapter> adapters) {
        List<String> parts = new ArrayList<>();
        for (Adapter a : adapters) {
            for (Ipv4 ip : a.ipv4()) {
                parts.add(a.name() + " " + ip.address() + "/" + ip.prefix());
            }
        }
        return "Adapters: " + (parts.isEmpty() ? "none with IPv4" : String.join(", ", parts));
    }

    /** The active adapters of this computer (not loopback). */
    static List<Adapter> adapters() {
        List<Adapter> out = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                List<Ipv4> v4 = new ArrayList<>();
                int v6 = 0;
                int v6Local = 0;
                for (InterfaceAddress a : ni.getInterfaceAddresses()) {
                    if (a.getAddress() instanceof Inet4Address) {
                        v4.add(new Ipv4(a.getAddress().getHostAddress(), a.getNetworkPrefixLength()));
                    } else if (a.getAddress() instanceof Inet6Address) {
                        v6++;
                        if (a.getAddress().isLinkLocalAddress()) {
                            v6Local++;
                        }
                    }
                }
                out.add(new Adapter(ni.getName(), ni.getDisplayName(), ni.isVirtual(), v4, v6, v6Local));
            }
        } catch (java.io.IOException | RuntimeException e) {
            System.err.println("Listing the network adapters failed: " + e);
        }
        return out;
    }

    /** Every adapter with its addresses, and the warnings. */
    static List<String> network(List<Adapter> adapters) {
        List<String> out = new ArrayList<>();
        List<String> withoutIpv4 = new ArrayList<>();
        for (Adapter a : adapters) {
            String v6 = a.ipv6() == 0 ? "" : "IPv6 " + a.ipv6() + " (link-local " + a.ipv6LinkLocal() + ")";
            if (a.ipv4().isEmpty()) {
                withoutIpv4.add(a.name() + (v6.isEmpty() ? "" : " " + v6));
                continue;
            }
            List<String> v4 = a.ipv4().stream().map(ip -> ip.address() + "/" + ip.prefix()).toList();
            out.add(a.name() + " (" + a.displayName() + ")" + (a.virtual() ? " virtual" : "") + ": " + v4 + (v6.isEmpty() ? "" : ", " + v6));
        }
        if (!withoutIpv4.isEmpty()) {
            out.add("Active without IPv4: " + String.join(", ", withoutIpv4));
        }
        out.add(adapters.stream().anyMatch(a -> a.ipv6() > 0) ? "(IPv6 addresses are only counted, not listed)"
                : "(IPv4 only: LJ Link runs with java.net.preferIPv4Stack, IPv6 addresses are not visible to it)");
        out.addAll(networkWarnings(adapters));
        return out;
    }

    /** A warning when two adapters are in the same network, and a note on self-assigned addresses. */
    static List<String> networkWarnings(List<Adapter> adapters) {
        List<String> out = new ArrayList<>();
        Map<String, List<String>> bySubnet = new LinkedHashMap<>();
        for (Adapter a : adapters) {
            for (Ipv4 ip : a.ipv4()) {
                String net = subnet(ip);
                if (net != null) {
                    bySubnet.computeIfAbsent(net, k -> new ArrayList<>()).add(a.name());
                }
            }
        }
        bySubnet.forEach((net, names) -> {
            if (names.size() > 1) {
                out.add("WARNING: " + names + " are in the same network " + net + " (for example Wi-Fi and cable at once): DJ Link packets arrive twice");
            }
        });
        bySubnet.keySet().stream().filter(n -> n.startsWith("169.254.")).findFirst()
                .ifPresent(n -> out.add("NOTE: an adapter has a self-assigned address (169.254.x.x): no DHCP in that network"));
        return out;
    }

    /** The name of the adapter whose IPv4 network contains this address, or null (beat-link picks its adapter the same way). */
    static String adapterFor(String address, List<Adapter> adapters) {
        Integer target = ipv4(address);
        if (target == null) {
            return null;
        }
        for (Adapter a : adapters) {
            for (Ipv4 ip : a.ipv4()) {
                Integer own = ipv4(ip.address());
                if (own != null && (own & mask(ip.prefix())) == (target & mask(ip.prefix()))) {
                    return a.name();
                }
            }
        }
        return null;
    }

    private static String subnet(Ipv4 ip) {
        Integer v = ipv4(ip.address());
        if (v == null) {
            return null;
        }
        int masked = v & mask(ip.prefix());
        return (masked >>> 24) + "." + (masked >> 16 & 0xFF) + "." + (masked >> 8 & 0xFF) + "." + (masked & 0xFF) + "/" + ip.prefix();
    }

    private static int mask(int prefix) {
        return prefix <= 0 ? 0 : prefix >= 32 ? -1 : -1 << (32 - prefix);
    }

    /** "a.b.c.d" as a number, or null for anything else (no name lookups). */
    private static Integer ipv4(String address) {
        if (address == null) {
            return null;
        }
        String[] p = address.split("\\.");
        if (p.length != 4) {
            return null;
        }
        int v = 0;
        for (String part : p) {
            if (part.isEmpty() || part.length() > 3 || !part.chars().allMatch(Character::isDigit)) {
                return null;
            }
            int n = Integer.parseInt(part);
            if (n > 255) {
                return null;
            }
            v = v << 8 | n;
        }
        return v;
    }
}
