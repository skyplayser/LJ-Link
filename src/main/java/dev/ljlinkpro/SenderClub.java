package dev.ljlinkpro;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The club key and the contents of {@code club.json} for LJL Link (sender protocol version 1). The key is 128
 * random bits as lowercase hex, created on first use and kept in the settings file; a new key makes every older
 * {@code club.json} useless. Neither the key nor anything else from here is ever logged or sent to tablets:
 * only the local API ({@link ControlGuard}) shows it.
 */
final class SenderClub {
    private SenderClub() {}

    static final String KEY_SETTING = "senderClubKey";
    static final String NAME_SETTING = "senderClubName";
    /** The booth id announced by Bonjour ({@code booth=}): LJL Link derives its pseudonym for this LJ Link from it. */
    static final String BOOTH_SETTING = "senderBoothId";
    static final int MAX_NAME = 64;
    private static final Pattern KEY = Pattern.compile("[0-9a-f]{32}");
    private static final Pattern BOOTH = Pattern.compile("[0-9a-f]{16}");
    private static final SecureRandom RANDOM = new SecureRandom();

    /** The current club key; created and saved when there is none yet (or the saved one is broken). */
    static synchronized String key() {
        String k = AppSettings.getString(KEY_SETTING, null);
        if (k == null || !KEY.matcher(k).matches()) {
            k = newKeyValue();
            AppSettings.set(KEY_SETTING, k);
        }
        return k;
    }

    /** Replaces the club key with a new random one and returns it. */
    static synchronized String renewKey() {
        String k = newKeyValue();
        AppSettings.set(KEY_SETTING, k);
        return k;
    }

    /**
     * The booth id (64 random bits as 16 lowercase hex digits), created and saved when there is none yet (or the saved one
     * is broken). Not secret, it is announced in the network; replaced together with the club key ("New key").
     */
    static synchronized String booth() {
        String b = AppSettings.getString(BOOTH_SETTING, null);
        if (b == null || !BOOTH.matcher(b).matches()) {
            b = newBoothValue();
            AppSettings.set(BOOTH_SETTING, b);
        }
        return b;
    }

    /** Replaces the booth id with a new random one and returns it: every pseudonym of a DJ Mac changes with it. */
    static synchronized String renewBooth() {
        String b = newBoothValue();
        AppSettings.set(BOOTH_SETTING, b);
        return b;
    }

    static String newBoothValue() {
        byte[] b = new byte[8];
        RANDOM.nextBytes(b);
        return java.util.HexFormat.of().formatHex(b);
    }

    static String newKeyValue() {
        byte[] b = new byte[16];
        RANDOM.nextBytes(b);
        return java.util.HexFormat.of().formatHex(b);
    }

    /**
     * True when {@code given} is exactly the expected key. The time taken depends only on the length of the expected key,
     * not on how many characters match ({@link MessageDigest#isEqual}).
     */
    static boolean matches(String expected, String given) {
        if (expected == null || expected.isEmpty() || given == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8));
    }

    /** The club name for {@code club.json} and the welcome: the one set by the LJ, otherwise this computer's name. */
    static String name() {
        String n = clean(AppSettings.getString(NAME_SETTING, null), MAX_NAME);
        return n.isEmpty() ? hostName() : n;
    }

    /** True when the LJ has set a club name (otherwise the computer's name is used). */
    static boolean nameSet() {
        return !clean(AppSettings.getString(NAME_SETTING, null), MAX_NAME).isEmpty();
    }

    /** Sets the club name; blank goes back to the computer's name. */
    static void setName(String name) {
        String n = clean(name, MAX_NAME);
        AppSettings.set(NAME_SETTING, n.isEmpty() ? null : n);
    }

    static String hostName() {
        try {
            String h = InetAddress.getLocalHost().getHostName().replaceFirst("\\..*$", "");
            return h.isBlank() ? "LJ Link" : clean(h, MAX_NAME);
        } catch (java.io.IOException e) {
            return "LJ Link";
        }
    }

    /** The IPv4 addresses of this computer's network adapters (no loopback, no self-assigned 169.254.x.x). */
    static List<String> hosts() {
        List<String> out = new ArrayList<>();
        for (NetworkCheck.Adapter a : NetworkCheck.adapters()) {
            if (!a.ip().startsWith("169.254.") && !out.contains(a.ip())) {
                out.add(a.ip());
            }
        }
        return out;
    }

    /** The contents of {@code club.json}, the file LJL Link reads its address and key from. */
    static Map<String, Object> clubJson(String club, List<String> hosts, int port, String key) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("version", 1);
        m.put("club", club);
        m.put("hosts", List.copyOf(hosts));
        m.put("port", port);
        m.put("key", key);
        return m;
    }

    /**
     * Text from the network or the settings made safe to show: control characters removed, trimmed, at most {@code max}
     * characters (counted as code points, so an emoji is never cut in half). Null gives "".
     */
    static String clean(String text, int max) {
        if (text == null) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        text.codePoints().filter(cp -> !Character.isISOControl(cp)).forEach(b::appendCodePoint);
        String s = b.toString().trim();
        if (s.codePointCount(0, s.length()) > max) {
            s = s.substring(0, s.offsetByCodePoints(0, max)).trim();
        }
        return s;
    }
}
