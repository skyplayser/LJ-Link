package dev.ljlinkpro;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Helps users whose automatic search found nothing. beat-link can only discover devices through
 * broadcast announcements, so a manually entered IP cannot be connected to directly. Instead we
 * check whether the computer and the device can see each other and explain what to fix.
 */
final class NetworkCheck {
    private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");
    /** Port of the player's database server discovery; open on CDJs that share rekordbox metadata. */
    private static final int DBSERVER_PORT = 12523;

    private NetworkCheck() {}

    record Adapter(String name, String ip, int prefix) {}

    static List<Adapter> adapters() {
        List<Adapter> result = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) {
                    continue;
                }
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    if (ia.getAddress() instanceof Inet4Address a) {
                        result.add(new Adapter(ni.getDisplayName(), a.getHostAddress(), ia.getNetworkPrefixLength()));
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Listing network adapters failed: " + e);
        }
        return result;
    }

    /** URLs under which other devices (tablets) can reach this app. */
    static List<String> lanUrls(int port) {
        return adapters().stream().filter(a -> !a.ip().startsWith("169.254.")).map(a -> "http://" + a.ip() + ":" + port).toList();
    }

    /**
     * Texts are English templates with {0}, {1} placeholders and an {@code args} list; the browser translates the
     * template and fills in the arguments.
     */
    static Map<String, Object> check(String ip) {
        List<Map<String, Object>> checks = new ArrayList<>();
        if (ip == null || !IPV4.matcher(ip.trim()).matches() || !validOctets(ip.trim())) {
            checks.add(item(false, "IP address", "This is not a valid IP address. It looks like this: 192.168.1.20"));
            return Map.of("ok", false, "checks", checks);
        }
        ip = ip.trim();
        checks.add(item(true, "IP address", "{0}", ip));

        List<Adapter> adapters = adapters();
        if (adapters.isEmpty()) {
            checks.add(item(false, "Network connection",
                    "This computer has no active network connection. Check the cable."));
            return Map.of("ok", false, "checks", checks);
        }

        Adapter same = null;
        for (Adapter a : adapters) {
            if (sameSubnet(a.ip(), ip, a.prefix())) {
                same = a;
                break;
            }
        }
        if (same != null) {
            checks.add(item(true, "Same network", "Computer ({0}) and device are in the same network.", same.ip()));
        } else if (ip.startsWith("169.254.")) {
            checks.add(item(false, "Same network",
                    "The device has an automatic address (169.254.x.x). The computer then needs a 169.254.x.x address too, "
                    + "otherwise they cannot find each other. Easiest: connect the computer and the DJ devices to the same "
                    + "switch, set the computer's IP address to \"automatic (DHCP)\" and wait about a minute."));
        } else {
            checks.add(item(false, "Same network",
                    "The computer ({0}) is in a different network than the device. Set the computer's network connection to "
                    + "an address in the same range, for example {1}, and choose 255.255.255.0 as subnet mask.",
                    adapters.get(0).ip(), ip.substring(0, ip.lastIndexOf('.') + 1) + "200"));
        }

        boolean reachable = false;
        try {
            reachable = InetAddress.getByName(ip).isReachable(1500);
        } catch (IOException e) {
            // treated as not reachable
        }
        boolean dbPort = tcpOpen(ip, DBSERVER_PORT);
        if (reachable || dbPort) {
            checks.add(item(true, "Device responds", "The device is reachable."));
        } else {
            checks.add(item(false, "Device responds",
                    "The device does not respond. Is it switched on and connected with a network cable? "
                    + "Is the IP address correct? (On the player: Menu, Utility, Link Info)"));
        }
        if (dbPort) {
            checks.add(item(true, "Data interface", "The player shares track information."));
        }

        boolean ok = same != null && (reachable || dbPort);
        if (ok) {
            checks.add(item(true, "Result", "Connection possible. Press \"Search automatically again\"."));
        }
        return Map.of("ok", ok, "checks", checks);
    }

    private static Map<String, Object> item(boolean ok, String label, String hint, Object... args) {
        return Map.of("ok", ok, "label", label, "hint", hint, "args", List.of(args));
    }

    private static boolean validOctets(String ip) {
        for (String part : ip.split("\\.")) {
            if (Integer.parseInt(part) > 255) {
                return false;
            }
        }
        return true;
    }

    private static boolean tcpOpen(String ip, int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(ip, port), 1000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean sameSubnet(String a, String b, int prefix) {
        try {
            byte[] x = InetAddress.getByName(a).getAddress();
            byte[] y = InetAddress.getByName(b).getAddress();
            int full = prefix / 8;
            for (int i = 0; i < full; i++) {
                if (x[i] != y[i]) {
                    return false;
                }
            }
            int rest = prefix % 8;
            if (rest == 0) {
                return true;
            }
            int mask = 0xFF << (8 - rest);
            return (x[full] & mask) == (y[full] & mask);
        } catch (IOException e) {
            return false;
        }
    }
}
