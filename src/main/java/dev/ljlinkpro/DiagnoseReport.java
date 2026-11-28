package dev.ljlinkpro;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What LJ Link puts into its suite diagnostics file (ADR-046, {@link SuiteDiagnose}): state, sources, decks, light desk
 * output, updates and the program's own checks.
 *
 * <p>Built only from flags, counts, model names and the program's own texts. Never read here: track titles, artists,
 * covers, names of DJs or LJL Link apps, chat nicknames and texts, the club key, desk user and password, adapter
 * display names. Addresses only from the show network (the launcher and {@link SuiteDiagnose#clean} replace public ones).
 * Every input is optional (null), so tests can give only what they check.
 */
final class DiagnoseReport implements SuiteDiagnose.Source {
    Hub hub;
    Supplier<LinkService.Config> desk;
    Supplier<LinkService.Status> deskStatus;
    Supplier<AbletonLinkPeer.Status> ableton;
    Supplier<Map<String, Object>> vjLink;
    Supplier<List<Map<String, Object>>> senders;
    BooleanSupplier chatOn;
    Supplier<Map<String, Object>> timecode;
    Supplier<List<Diagnostics.Adapter>> adapters;
    BooleanSupplier autoUpdate;
    Supplier<Updater.Release> update;
    String version = Updater.currentVersion();
    long startedAt = System.currentTimeMillis();
    /** Clock for the running time (tests). */
    java.util.function.LongSupplier clock = System::currentTimeMillis;

    /** Decks listed one by one in the summary (the summary has at most 20 lines). */
    static final int MAX_DECK_LINES = 7;

    @Override
    public String signature() {
        rememberNames();
        StringBuilder sb = new StringBuilder();
        if (hub != null) {
            sb.append(hub.phase());
            for (Hub.Scope sc : hub.scopesInOrder()) {
                sb.append('|').append(sc.key).append(':').append(sc.phase).append(':').append(sc.xdjAzNoStatus)
                        .append(':').append(sc.note == null ? "" : sc.note.length());
            }
            for (Deck d : sortedDecks()) {
                sb.append('|').append(d.number).append(d.hasTrack ? 't' : '-').append(d.waveform != null ? 'w' : '-')
                        .append(d.sendingBpm ? 's' : '-');
            }
        }
        LinkService.Config c = get(desk);
        LinkService.Status st = get(deskStatus);
        sb.append("|desk ").append(c == null ? "" : c.type()).append(st == null || st.error() == null ? "" : "error");
        AbletonLinkPeer.Status ab = get(ableton);
        sb.append("|ableton ").append(ab != null && ab.enabled()).append(ab == null || ab.note().isEmpty() ? "" : "note");
        Map<String, Object> tc = get(timecode);
        sb.append("|tc ").append(tc == null ? "" : tc.get("on") + "/" + tc.get("listening") + "/" + (tc.get("error") != null));
        List<Map<String, Object>> s = get(senders);
        sb.append("|senders ").append(s == null ? 0 : s.size());
        Updater.Release r = get(update);
        sb.append("|update ").append(r == null ? "" : r.version());
        return sb.toString();
    }

    @Override
    public SuiteDiagnose.State state() {
        Map<String, String> summary = new LinkedHashMap<>();
        List<SuiteDiagnose.Check> checks = new ArrayList<>();
        List<Deck> decks = sortedDecks();

        if (hub != null) {
            String phase = hub.phase();
            boolean demo = hub.scopesInOrder().stream().anyMatch(sc -> "demo".equals(sc.kind) && "monitoring".equals(sc.phase));
            summary.put("State", phase + (demo ? " (demo mode, no real hardware)" : ""));
            summary.put("Sources", sources());
            long withTrack = decks.stream().filter(d -> d.hasTrack).count();
            long playing = decks.stream().filter(d -> d.playing).count();
            summary.put("Decks", decks.size() + " (" + withTrack + " with a track, " + playing + " playing)"
                    + (decks.size() > MAX_DECK_LINES ? ", first " + MAX_DECK_LINES + " listed" : ""));
        }
        summary.put("Light desk output", deskSummary());
        AbletonLinkPeer.Status ab = get(ableton);
        if (ab != null) {
            summary.put("Ableton Link (send)", !ab.enabled() ? "off"
                    : "on, " + ab.peers() + " peer(s)" + (ab.bpm() == null ? "" : String.format(Locale.ROOT, ", session %.2f BPM", ab.bpm()))
                    + (ab.sendingDeck() == null ? "" : ", leading " + deckRef(ab.sendingDeck())));
        }
        summary.put("Updates", updateSummary());
        List<Diagnostics.Adapter> ads = get(adapters);
        if (ads != null) {
            summary.put("Network", network(ads));
        }
        summary.put("Running for", duration(clock.getAsLong() - startedAt));
        summary.put("Java", System.getProperty("java.version", "?"));
        if (hub != null) {
            summary.put("Browsers connected", String.valueOf(hub.clientCount()));
        }
        Map<String, Object> vj = get(vjLink);
        if (vjLink != null) {
            summary.put("VJ-Link", vj == null ? "no VJ program" : vj.getOrDefault("clients", 1) + " VJ program(s)");
        }
        Map<String, Object> tc = get(timecode);
        if (tc != null) {
            summary.put("Art-Net timecode", !Boolean.TRUE.equals(tc.get("on")) ? "off"
                    : "on, " + (Boolean.TRUE.equals(tc.get("listening")) ? "listening" : "not listening")
                    + ", " + (Boolean.TRUE.equals(tc.get("receiving")) ? "receiving" : "nothing received"));
        }
        List<String> options = new ArrayList<>();
        options.add("privacy mode " + (Privacy.enabled() ? "on" : "off"));
        if (chatOn != null) {
            options.add("suite chat " + (chatOn.getAsBoolean() ? "on" : "off"));
        }
        options.add("language " + AppSettings.language());
        summary.put("Options", String.join(", ", options));
        int room = SuiteDiagnose.MAX_ITEMS - summary.size();
        for (Deck d : decks.subList(0, Math.min(decks.size(), Math.min(MAX_DECK_LINES, Math.max(0, room))))) {
            summary.put("Deck " + d.number, deckLine(d));
        }

        if (hub != null) {
            sourceChecks(checks);
            waveformCheck(decks, checks);
        }
        if (ads != null) {
            List<String> warnings = Diagnostics.networkWarnings(ads);
            boolean twice = warnings.stream().anyMatch(w -> w.startsWith("WARNING:"));
            checks.add(new SuiteDiagnose.Check("Network adapters", !twice, warnings.isEmpty() ? "one adapter per network"
                    : String.join(" ", warnings.stream().map(w -> w.replaceFirst("^(WARNING|NOTE): ", "")).toList())));
        }
        deskCheck(checks);
        if (ab != null && ab.enabled()) {
            checks.add(new SuiteDiagnose.Check("Ableton Link (send)", ab.note().isEmpty(),
                    ab.note().isEmpty() ? ab.peers() + " peer(s)" : safe(ab.note())));
        }
        if (tc != null && Boolean.TRUE.equals(tc.get("on"))) {
            Object err = tc.get("error");
            checks.add(new SuiteDiagnose.Check("Art-Net timecode", err == null && Boolean.TRUE.equals(tc.get("listening")),
                    err != null ? safe(String.valueOf(err))
                            : Boolean.TRUE.equals(tc.get("receiving")) ? "receiving timecode" : "listening, no timecode yet"));
        }
        Updater.Release r = get(update);
        if (r != null && version != null) {
            boolean newer = Updater.isNewer(r.version(), version);
            checks.add(new SuiteDiagnose.Check("Update", !newer, newer ? "version " + r.version() + " is available" : "up to date"));
        }
        return new SuiteDiagnose.State(summary, checks);
    }

    private List<Deck> sortedDecks() {
        if (hub == null) {
            return List.of();
        }
        List<Deck> list = new ArrayList<>(hub.allDecks());
        list.sort(java.util.Comparator.comparingInt(d -> d.number));
        return list;
    }

    /** "Pro DJ Link: monitoring, 2 decks; Denon: searching; LJL Link: 1 app, 2 decks". No names of devices or DJs. */
    private String sources() {
        Map<String, int[]> appsAndDecks = new LinkedHashMap<>();
        List<String> parts = new ArrayList<>();
        Map<Hub.Scope, Integer> deckCount = new LinkedHashMap<>();
        for (Deck d : hub.allDecks()) {
            if (d.scope != null) {
                deckCount.merge(d.scope, 1, Integer::sum);
            }
        }
        for (Hub.Scope sc : hub.scopesInOrder()) {
            int n = deckCount.getOrDefault(sc, 0);
            if (sc.persistent) {
                int[] a = appsAndDecks.computeIfAbsent(kindName(sc.kind), k -> new int[2]);
                a[0]++;
                a[1] += n;
                continue;
            }
            parts.add(kindName(sc.kind) + ": " + sc.phase + (n > 0 ? ", " + n + " deck" + (n == 1 ? "" : "s") : ""));
        }
        appsAndDecks.forEach((k, a) -> parts.add(k + ": " + a[0] + " app" + (a[0] == 1 ? "" : "s") + ", " + a[1] + " deck" + (a[1] == 1 ? "" : "s")));
        List<Map<String, Object>> s = get(senders);
        if (s != null && !s.isEmpty() && appsAndDecks.isEmpty()) {
            parts.add("LJL Link: " + s.size() + " app" + (s.size() == 1 ? "" : "s"));
        }
        return parts.isEmpty() ? "none running" : String.join("; ", parts);
    }

    static String kindName(String kind) {
        return switch (kind == null ? "" : kind) {
            case "pioneer" -> "Pro DJ Link";
            case "denon" -> "Denon StageLinQ";
            case "serato" -> "Serato";
            case "link" -> "Ableton Link (in)";
            case "demo" -> "Demo";
            case SenderInbox.KIND -> "LJL Link";
            default -> kind;
        };
    }

    /** "CDJ-3000 (Pro DJ Link 2): track, playing, waveform yes, detail yes, beat grid yes, 128.00 BPM measured, to the desk". */
    static String deckLine(Deck d) {
        String kind = d.scope == null ? "?" : kindName(d.scope.kind);
        return model(d.name) + " (" + kind + " " + d.label + "): " + (d.hasTrack ? "track" : "no track")
                + (d.playing ? ", playing" : "") + (d.onAir ? ", on air" : "") + (d.master ? ", master" : "")
                + ", waveform " + (d.waveform != null ? "yes" : d.noWaveformSource ? "not from this source" : "no")
                + ", detail " + (d.detail != null ? "yes" : "no")
                + ", beat grid " + (d.beatTimes != null && d.beatTimes.length > 0 ? "yes" : "no")
                + String.format(Locale.ROOT, ", %.2f BPM %s", d.effectiveBpm, d.exactTempo ? "exact" : "measured")
                + (d.sendingBpm ? ", to the desk" : "") + (d.sendingLink ? ", leads Ableton Link" : "")
                + (d.coarsePosition ? ", coarse position" : "")
                + (d.metaHint == null ? "" : ", metadata hint " + plain(d.metaHint));
    }

    /** A word of LJ Link itself (type, version, adapter): letters, digits and a few signs, at most 40 characters. */
    static String plain(String name) {
        String m = name == null ? "" : name.replaceAll("[^A-Za-z0-9 .+_\\-]", "").replaceAll("\\s+", " ").strip();
        if (m.length() > 40) {
            m = m.substring(0, 40);
        }
        return m.isEmpty() ? "?" : m;
    }

    /**
     * Known models and programs: Pioneer (CDJ-3000, XDJ-AZ, DJM-A9, OPUS-QUAD …), Denon (SC6000, Prime 4+ …), DJ and
     * show software. Only the matched part is kept, so "CDJ-3000 von XY" becomes "CDJ-3000".
     */
    private static final Pattern MODEL = Pattern.compile("(?i)\\b(?:"
            + "(?:CDJ|XDJ|DJM|DDJ|OPUS|PLX|RMX|DJS|XPRS)-[A-Z0-9]+(?:-[A-Z0-9]+)*"
            + "|(?:Denon(?: DJ)? )?(?:SC ?LIVE ?\\d|SC ?\\d{4}M?|Prime ?(?:4\\+?|2|Go\\+?)|MIXSTREAM PRO(?: GO|\\+)?|LC6000|X1850|X1800)"
            + "|rekordbox|Serato DJ (?:Pro|Lite)|Serato|Traktor(?: Pro)?|VirtualDJ|djay(?: Pro)?|Engine DJ|Ableton Link"
            + "|Beat Link Trigger|beat-link|TCS-SHOWKONTROL|ShowKontrol|LJ Link|LJL Link|DEMO-PLAYER|Fixed value"
            + ")(?![\\w-])");

    /** The model of a device or program from the network, never a name someone gave it; "other device" otherwise. */
    static String model(String name) {
        if (name == null) {
            return "other device";
        }
        Matcher m = MODEL.matcher(name);
        return m.find() ? m.group() : "other device";
    }

    // ------------------------------------------------------------------ names that must never reach the file

    /** Names seen in this run (devices, titles, artists, LJL Link apps) and what stands for them; memory only, at most 128. */
    private static final LinkedHashMap<String, String> NAMES = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > 128;
        }
    };

    /** Remembers a name from the network or a track, so that error texts and checks never carry it. */
    static void remember(String name, String replacement) {
        if (name == null) {
            return;
        }
        String n = name.strip();
        if (n.length() < 4 || n.equals(replacement)) {
            return;
        }
        synchronized (NAMES) {
            NAMES.put(n, replacement);
        }
    }

    /** Every remembered name in the text replaced (longest first, any case). */
    static String withoutNames(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        List<Map.Entry<String, String>> list;
        synchronized (NAMES) {
            if (NAMES.isEmpty()) {
                return text;
            }
            list = new ArrayList<>(NAMES.entrySet());
        }
        list.sort((a, b) -> b.getKey().length() - a.getKey().length());
        String t = text;
        for (Map.Entry<String, String> e : list) {
            if (t.toLowerCase(Locale.ROOT).contains(e.getKey().toLowerCase(Locale.ROOT))) {
                t = Pattern.compile(Pattern.quote(e.getKey()), Pattern.CASE_INSENSITIVE).matcher(t)
                        .replaceAll(Matcher.quoteReplacement(e.getValue()));
            }
        }
        return t;
    }

    /** A text of LJ Link or an exception for a check: without paths and without remembered names. */
    static String safe(String text) {
        return withoutNames(SuiteDiagnose.withoutPaths(text));
    }

    /** Takes the names of what is there now into {@link #remember} (device names, titles, artists, LJL Link apps). */
    private void rememberNames() {
        if (hub != null) {
            for (Hub.Scope sc : hub.scopesInOrder()) {
                for (Hub.DeviceInfo d : sc.devices) {
                    String model = model(d.name());
                    if (!model.equalsIgnoreCase(d.name() == null ? "" : d.name().strip())) {
                        remember(d.name(), "other device".equals(model) ? "[Gerät]" : model);
                    }
                }
                if (sc.persistent) {
                    remember(sc.groupName, "[Name]");
                }
            }
            for (Deck d : hub.allDecks()) {
                String model = model(d.name);
                if (d.name != null && !model.equalsIgnoreCase(d.name.strip())) {
                    remember(d.name, "other device".equals(model) ? "[Gerät]" : model);
                }
                remember(d.title, "[Titel]");
                remember(d.artist, "[Künstler]");
            }
        }
        List<Map<String, Object>> s = get(senders);
        if (s != null) {
            for (Map<String, Object> m : s) {
                Object n = m.get("name");
                remember(n == null ? null : String.valueOf(n), "[Name]");
            }
        }
    }

    private void sourceChecks(List<SuiteDiagnose.Check> checks) {
        for (Hub.Scope sc : hub.scopesInOrder()) {
            if (sc.persistent || "idle".equals(sc.phase) && sc.note == null) {
                continue;
            }
            boolean beatPort = "monitoring".equals(sc.phase) && sc.message.startsWith("Beat port");
            boolean ok = !"error".equals(sc.phase) && !beatPort;
            String msg = sc.message == null || sc.message.isBlank() ? "" : ": " + safe(sc.message);
            checks.add(new SuiteDiagnose.Check(kindName(sc.kind), ok, sc.phase + msg));
            if (sc.xdjAzNoStatus) {
                checks.add(new SuiteDiagnose.Check("XDJ-AZ", false, "announced, but sends no deck data (4-deck mode)"));
            }
            if (sc.note != null && !sc.note.isBlank()) {
                checks.add(new SuiteDiagnose.Check(kindName(sc.kind) + " note", false, safe(sc.note)));
            }
        }
    }

    private static void waveformCheck(List<Deck> decks, List<SuiteDiagnose.Check> checks) {
        List<String> missing = new ArrayList<>();
        boolean any = false;
        for (Deck d : decks) {
            if (!d.hasTrack || d.noWaveformSource) {
                continue;
            }
            any = true;
            if (d.waveform == null) {
                missing.add(String.valueOf(d.number));
            }
        }
        if (any) {
            checks.add(new SuiteDiagnose.Check("Waveforms", missing.isEmpty(),
                    missing.isEmpty() ? "every loaded deck has one" : "no waveform yet on deck " + String.join(", ", missing)));
        }
    }

    private String deskSummary() {
        LinkService.Config c = get(desk);
        LinkService.Status st = get(deskStatus);
        if (c == null || "off".equals(c.type())) {
            return "off";
        }
        StringBuilder sb = new StringBuilder(deskName(c.type()));
        if (!"ableton".equals(c.type())) {
            sb.append(" to ").append(host(c.host())).append(':').append(c.port());
        }
        if (st != null) {
            sb.append(", tempo from ").append(deckRef(st.source()));
            if (st.sendingDeck() != null) {
                sb.append(" (now ").append(deckRef(st.sendingDeck())).append(')');
            }
            sb.append(", ").append(st.sentCount()).append(" sent");
            if (st.bpm() != null) {
                sb.append(String.format(Locale.ROOT, ", last %.1f BPM", st.bpm()));
            }
        }
        return sb.toString();
    }

    /** "auto", "manual" (the fixed value) or "deck 2" for the key of a deck; never the key itself (it holds a source id). */
    String deckRef(String key) {
        if (key == null || LinkService.AUTO.equals(key)) {
            return "auto";
        }
        if (LinkService.MANUAL.equals(key)) {
            return "fixed value";
        }
        for (Deck d : sortedDecks()) {
            if (key.equals(d.key())) {
                return "deck " + d.number;
            }
        }
        return "a deck that is not there";
    }

    private void deskCheck(List<SuiteDiagnose.Check> checks) {
        LinkService.Config c = get(desk);
        LinkService.Status st = get(deskStatus);
        if (c == null || "off".equals(c.type())) {
            return;
        }
        String error = st == null ? null : st.error();
        checks.add(new SuiteDiagnose.Check("Light desk output", error == null,
                error == null ? (st == null ? "on" : st.sentCount() + " sent") : "error: " + safe(error)));
    }

    static String deskName(String type) {
        return switch (type) {
            case "ma" -> "grandMA3 (OSC command line)";
            case "ma2" -> "grandMA2 (Telnet)";
            case "osc" -> "OSC";
            case "os2l" -> "OS2L";
            case "chamsys" -> "ChamSys MagicQ (OSC tap)";
            case "daslight" -> "Daslight (OSC tap)";
            case "ableton" -> "Ableton Link";
            default -> plain(type);
        };
    }

    /** An IPv4 address or localhost as it is (public ones are replaced later); a host name only as [Rechner]. */
    static String host(String h) {
        if (h == null || h.isBlank()) {
            return "?";
        }
        String t = h.trim();
        if (t.matches("\\d{1,3}(\\.\\d{1,3}){3}") || "localhost".equalsIgnoreCase(t)) {
            return t;
        }
        return "[Rechner]";
    }

    private String updateSummary() {
        if (autoUpdate != null && !autoUpdate.getAsBoolean()) {
            return "automatic check off";
        }
        Updater.Release r = get(update);
        if (r == null) {
            return "automatic check on, nothing known yet";
        }
        return "automatic check on, " + (version != null && Updater.isNewer(r.version(), version)
                ? "version " + plain(r.version()) + " available" : "up to date");
    }

    /** "en0 192.168.1.20/24, en5 169.254.1.2/16": adapter names (not their display names) and IPv4 only. */
    static String network(List<Diagnostics.Adapter> adapters) {
        List<String> parts = new ArrayList<>();
        for (Diagnostics.Adapter a : adapters) {
            for (Diagnostics.Ipv4 ip : a.ipv4()) {
                parts.add(plain(a.name()) + " " + ip.address() + "/" + ip.prefix());
            }
        }
        return parts.isEmpty() ? "no adapter with IPv4" : String.join(", ", parts);
    }

    static String duration(long ms) {
        long min = Math.max(0, ms) / 60_000;
        return min < 60 ? min + " min" : min / 60 + " h " + String.format(Locale.ROOT, "%02d", min % 60) + " min";
    }

    private static <T> T get(Supplier<T> s) {
        if (s == null) {
            return null;
        }
        try {
            return s.get();
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ errors from the log

    /** slf4j-simple: "[thread] WARN org.deepsymmetry.beatlink.VirtualCdj - text". */
    private static final Pattern SLF4J = Pattern.compile("\\[[^\\]]*\\] (TRACE|DEBUG|INFO|WARN|ERROR) ([\\w.$]+) - ");
    /** "java.net.SocketException" anywhere in a line. */
    private static final Pattern EXCEPTION = Pattern.compile("\\b((?:[a-z_$][\\w$]*\\.)+[A-Z][\\w$]*(?:Exception|Error|Throwable))\\b");
    /** "Mixer DJM-A9 von XY (device 33)": only the model stays. */
    private static final Pattern MIXER = Pattern.compile("Mixer (.+?) \\(device");
    private static final Pattern QUOTED = Pattern.compile("\"[^\"]*\"|'[^']*'|“[^”]*”|„[^“”]*[“”]");

    /**
     * A line of the error stream as a generic entry for the diagnostics, or null for none (stack trace lines, empty and
     * INFO lines). Never the free text of a message: from a library only level and logger, from an exception only its
     * class, from LJ Link's own messages the text before the first ": " (the fixed part LJ Link writes; what follows is
     * usually an exception message, an address or a name) without anything in quotes, plus the exception class.
     */
    static String errorText(String line) {
        if (line == null || line.isBlank() || line.startsWith("\t") || line.startsWith(" ")) {
            return null;
        }
        String t = line.strip();
        if (t.startsWith("at ") || t.startsWith("Caused by:") || t.startsWith("Suppressed:") || t.startsWith("...")) {
            return null;
        }
        Matcher slf = SLF4J.matcher(t);
        if (slf.find()) {
            String level = slf.group(1);
            if (!"WARN".equals(level) && !"ERROR".equals(level)) {
                return null;
            }
            return level + " " + slf.group(2) + exceptionSuffix(t.substring(slf.end()));
        }
        Matcher ex = EXCEPTION.matcher(t);
        if (t.startsWith("Exception in thread") || ex.lookingAt()) {
            return ex.find(0) ? "Exception " + ex.group(1) : "Exception";
        }
        int colon = t.indexOf(": ");
        String head = colon < 0 ? t : t.substring(0, colon);
        String rest = colon < 0 ? "" : t.substring(colon);
        head = QUOTED.matcher(head).replaceAll("…");
        head = MIXER.matcher(head).replaceAll(r -> "Mixer " + Matcher.quoteReplacement(model(r.group(1))) + " (device");
        head = safe(head);
        if (head.length() > 160) {
            head = head.substring(0, 159) + "…";
        }
        return head + exceptionSuffix(rest);
    }

    private static String exceptionSuffix(String text) {
        Matcher m = EXCEPTION.matcher(text);
        if (!m.find()) {
            return "";
        }
        String cls = m.group(1);
        return " (" + cls.substring(cls.lastIndexOf('.') + 1) + ")";
    }
}
