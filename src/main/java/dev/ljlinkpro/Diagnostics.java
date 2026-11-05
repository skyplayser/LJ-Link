package dev.ljlinkpro;

import java.net.Inet4Address;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The diagnostic part of a problem report: program and system, network adapters, what every source sees, the decks and
 * the last log lines. It never contains track titles, artists, covers or passwords.
 */
final class Diagnostics {
    private Diagnostics() {}

    static String report(Hub hub, Session session, LinkService link, AbletonLinkPeer ableton) {
        List<String> out = new ArrayList<>();
        out.add("== LJ Link diagnostics ==");
        out.add("Version " + Updater.currentVersion() + ", " + ZonedDateTime.now());
        out.add("System " + System.getProperty("os.name") + " " + System.getProperty("os.version") + " (" + System.getProperty("os.arch")
                + "), Java " + System.getProperty("java.version"));
        out.add("Privacy mode " + Privacy.enabled() + ", language " + AppSettings.language());
        out.add("");
        out.add("== Network ==");
        out.addAll(network());
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

    /** Every adapter with its addresses, and a warning when two adapters are in the same network. */
    static List<String> network() {
        List<String> out = new ArrayList<>();
        Map<String, List<String>> bySubnet = new HashMap<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                List<String> addresses = new ArrayList<>();
                for (InterfaceAddress a : ni.getInterfaceAddresses()) {
                    if (a.getAddress() instanceof Inet4Address) {
                        addresses.add(a.getAddress().getHostAddress() + "/" + a.getNetworkPrefixLength());
                        bySubnet.computeIfAbsent(subnet(a), k -> new ArrayList<>()).add(ni.getName());
                    }
                }
                if (!addresses.isEmpty()) {
                    out.add(ni.getName() + " (" + ni.getDisplayName() + ")" + (ni.isVirtual() ? " virtual" : "") + ": " + addresses);
                }
            }
        } catch (java.io.IOException e) {
            out.add("Adapters: " + e);
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

    private static String subnet(InterfaceAddress a) {
        byte[] ip = a.getAddress().getAddress();
        int prefix = a.getNetworkPrefixLength();
        int v = (ip[0] & 0xFF) << 24 | (ip[1] & 0xFF) << 16 | (ip[2] & 0xFF) << 8 | (ip[3] & 0xFF);
        int masked = prefix == 0 ? 0 : v & (-1 << (32 - prefix));
        return (masked >>> 24) + "." + (masked >> 16 & 0xFF) + "." + (masked >> 8 & 0xFF) + "." + (masked & 0xFF) + "/" + prefix;
    }
}
