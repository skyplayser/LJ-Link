package dev.ljlinkpro;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Sends the tempo of the leading deck to a lighting desk or software as OSC over UDP.
 * Output only: LJ-Link-PRO never receives from or controls the DJ gear through this.
 */
final class LinkService {
    /** {@code type}: "off", "ma" (GrandMA2/3/onPC command line via OSC) or "osc" (plain OSC float). */
    record Config(String type, String host, int port, String address, String command) {
        static Config off() {
            return new Config("off", "127.0.0.1", 8000, "/ljlinkpro/bpm", "SpecialMaster 3.1 At {bpm}");
        }
    }

    record Status(Double bpm, int sentCount, String error, String source, String sendingDeck) {}

    /** A deck the tempo can be taken from, for the choice in the browser. */
    record Candidate(String key, String name, int label, String kind, double bpm, boolean playing, boolean master, boolean onAir) {}

    private static final Path FILE = Path.of(System.getProperty("user.home"), ".ljlinkpro", "link.json");
    private static final Path SOURCE_FILE = Path.of(System.getProperty("user.home"), ".ljlinkpro", "link-source.txt");
    static final String AUTO = "auto";
    private static final long KEEPALIVE_MS = 2000;

    private final Hub hub;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "link-output");
        t.setDaemon(true);
        return t;
    });
    private volatile Config config = load();
    private volatile Double lastBpm;
    private volatile long lastSentAt;
    private volatile int sentCount;
    private volatile String error;
    /** "auto" (master, else the deck on air) or the key of one deck, see {@link Deck#key()}. */
    private volatile String source = loadSource();
    private volatile String sendingDeck;

    LinkService(Hub hub) {
        this(hub, true);
    }

    /** {@code send = false} for tests: nothing is ever sent to a real desk. */
    LinkService(Hub hub, boolean send) {
        this.hub = hub;
        if (send) {
            executor.scheduleAtFixedRate(this::tick, 250, 250, TimeUnit.MILLISECONDS);
        }
    }

    Config config() {
        return config;
    }

    Status status() {
        return new Status(lastBpm, sentCount, error, source, sendingDeck);
    }

    void setConfig(Config c) throws IOException {
        Config clean = new Config(
                java.util.List.of("off", "ma", "osc").contains(c.type()) ? c.type() : "off",
                c.host() == null ? "127.0.0.1" : c.host().trim(),
                c.port() < 1 || c.port() > 65535 ? 8000 : c.port(),
                c.address() == null || c.address().isBlank() ? "/ljlinkpro/bpm" : c.address().trim(),
                c.command() == null || c.command().isBlank() ? "SpecialMaster 3.1 At {bpm}" : c.command().trim());
        config = clean;
        error = null;
        lastBpm = null;
        Files.createDirectories(FILE.getParent());
        Files.writeString(FILE, Hub.JSON.writeValueAsString(clean));
    }

    String source() {
        return source;
    }

    void setSource(String deckKey) throws IOException {
        source = deckKey == null || deckKey.isBlank() ? AUTO : deckKey.trim();
        lastBpm = null;
        Files.createDirectories(SOURCE_FILE.getParent());
        Files.writeString(SOURCE_FILE, source);
    }

    private static String loadSource() {
        try {
            if (Files.exists(SOURCE_FILE)) {
                String s = Files.readString(SOURCE_FILE).trim();
                return s.isEmpty() ? AUTO : s;
            }
        } catch (IOException e) {
            // fall back to automatic
        }
        return AUTO;
    }

    /** All decks with a track, in display order. */
    java.util.List<Candidate> candidates() {
        java.util.List<Deck> list = new java.util.ArrayList<>(hub.allDecks());
        list.sort(java.util.Comparator.comparingInt((Deck d) -> d.scope == null ? 0 : d.scope.order).thenComparingInt(d -> d.label));
        java.util.List<Candidate> out = new java.util.ArrayList<>();
        for (Deck d : list) {
            String name = d.scope != null && !d.scope.groupPerDeck ? d.scope.groupName : d.name;
            out.add(new Candidate(d.key(), name == null ? "" : name, d.label, d.scope == null ? "" : d.scope.kind,
                    d.hasTrack ? d.effectiveBpm : 0, d.playing, d.master, d.onAir));
        }
        return out;
    }

    /** Sends a fixed tempo once so the user can check the connection and the mapping on the desk. */
    void sendTest() {
        send(config, 120.0);
    }

    private static Config load() {
        try {
            if (Files.exists(FILE)) {
                return Hub.JSON.readValue(Files.readString(FILE), Config.class);
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("Could not read link settings: " + e);
        }
        return Config.off();
    }

    /**
     * The deck whose tempo is sent: the chosen one, or automatically the master deck, else the deck that is on air and
     * playing, else any playing deck. Null when there is nothing to send (the chosen deck is gone or has no tempo).
     */
    Deck leadingDeck() {
        String src = source;
        if (!AUTO.equals(src)) {
            for (Deck d : hub.allDecks()) {
                if (src.equals(d.key())) {
                    return d.hasTrack && d.effectiveBpm > 0 ? d : null;
                }
            }
            return null;
        }
        Deck best = null;
        int rank = 0;
        // With two systems (say CDJs and a Denon) each has its own master; the one that is also live and playing wins,
        // and on a tie the device found first.
        java.util.List<Deck> ordered = new java.util.ArrayList<>(hub.allDecks());
        ordered.sort(java.util.Comparator.comparingInt((Deck d) -> d.scope == null ? 0 : d.scope.order).thenComparingInt(d -> d.label));
        for (Deck d : ordered) {
            if (!d.hasTrack || d.effectiveBpm <= 0) {
                continue;
            }
            int r = (d.master ? 4 : 0) + (d.onAir && d.playing ? 2 : 0) + (d.playing ? 1 : 0);
            if (r > rank) {
                rank = r;
                best = d;
            }
        }
        return best;
    }

    /** Marks the deck the tempo comes from, so every browser can show it on that deck. */
    private void markSending(Deck lead) {
        for (Deck d : hub.allDecks()) {
            d.sendingBpm = d == lead;
        }
    }

    private void tick() {
        try {
            Config c = config;
            if (c.type().equals("off")) {
                markSending(null);
                return;
            }
            Deck lead = leadingDeck();
            sendingDeck = lead == null ? null : lead.key();
            markSending(lead);
            if (lead == null) {
                if (!AUTO.equals(source)) {
                    error = "The chosen deck has no tempo right now, nothing is sent.";
                }
                return;
            }
            if (error != null && error.startsWith("The chosen deck")) {
                error = null;
            }
            double bpm = lead.effectiveBpm;
            long now = System.currentTimeMillis();
            boolean changed = lastBpm == null || Math.abs(bpm - lastBpm) >= 0.1;
            if (changed || now - lastSentAt >= KEEPALIVE_MS) {
                if (send(c, bpm)) {
                    lastBpm = bpm;
                    lastSentAt = now;
                }
            }
        } catch (RuntimeException e) {
            error = e.getMessage();
        }
    }

    private boolean send(Config c, double bpm) {
        String text = String.format(Locale.ROOT, "%.1f", bpm);
        try (DatagramSocket socket = new DatagramSocket()) {
            byte[] packet = c.type().equals("ma")
                    ? osc("/cmd", c.command().replace("{bpm}", text))
                    : osc(c.address(), (float) bpm);
            socket.send(new DatagramPacket(packet, packet.length, InetAddress.getByName(c.host()), c.port()));
            sentCount++;
            error = null;
            return true;
        } catch (IOException e) {
            error = "Sending to " + c.host() + ":" + c.port() + " failed: " + e.getMessage();
            return false;
        }
    }

    private static byte[] osc(String address, String value) {
        byte[] a = padded(address);
        byte[] t = padded(",s");
        byte[] v = padded(value);
        return ByteBuffer.allocate(a.length + t.length + v.length).put(a).put(t).put(v).array();
    }

    private static byte[] osc(String address, float value) {
        byte[] a = padded(address);
        byte[] t = padded(",f");
        return ByteBuffer.allocate(a.length + t.length + 4).put(a).put(t).putFloat(value).array();
    }

    /** OSC strings are NUL-terminated and padded to a multiple of 4 bytes. */
    private static byte[] padded(String s) {
        byte[] raw = s.getBytes(StandardCharsets.UTF_8);
        int len = (raw.length / 4 + 1) * 4;
        byte[] out = new byte[len];
        System.arraycopy(raw, 0, out, 0, raw.length);
        return out;
    }
}
