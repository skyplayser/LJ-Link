package dev.ljlinkpro;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Suite diagnostics v1 (ADR-046): keeps {@code diagnose/lj.json} in the Link-Suite data folder (next to
 * {@code suite.json}) up to date, so the help assistant of the Link-Suite launcher can show it and, only when the user
 * ticks the box, send it along. Format and rules: Suite-Launcher {@code design/diagnose/SPEC.md}; this is the Java
 * version of its {@code diagnose-writer.ts}. No port and no network service: only a file.
 *
 * <p>Written at the start, at the end ({@code running: false}), when something important changes (the signature of the
 * {@link Source}, or a new error) and otherwise at most every 30 s; never more often than once a second. Written
 * atomically (temp file, then rename). It never throws: when the folder cannot be written, LJ Link runs on without
 * diagnostics.
 *
 * <p>What never goes in (SPEC.md): names of people, chat texts, passwords, keys, user folders, public IP and MAC
 * addresses, computer names, track titles. The content is built without them ({@link DiagnoseReport}); every text also
 * goes through {@link #clean} as a second check.
 */
final class SuiteDiagnose {
    static final String ID = "lj";
    static final String APP = "LJ Link";
    static final int MAX_BYTES = 16 * 1024;
    static final int MAX_ITEMS = 20;
    static final int MAX_KEY = 60;
    static final int MAX_VALUE = 200;
    static final int MAX_ERROR = 300;
    static final long MIN_GAP_MS = 1_000;
    static final long REFRESH_MS = 30_000;
    static final long POLL_MS = 2_000;
    /** Overrides the Link-Suite data folder (tests, test instances); the file then goes to {@code <dir>/diagnose/lj.json}. */
    static final String DIR_PROPERTY = "ljlinkpro.suiteDir";

    /** One self-check of the program. */
    record Check(String name, boolean ok, String detail) {}

    /** The state for the file: summary (text to text) and checks. */
    record State(Map<String, String> summary, List<Check> checks) {
        static State empty() {
            return new State(Map.of(), List.of());
        }
    }

    /** Where the state comes from: a cheap signature that changes with everything important, and the full state. */
    interface Source {
        String signature();

        State state();
    }

    private static final class Err {
        final String text;
        int count;
        long atMs;

        Err(String text) {
            this.text = text;
        }
    }

    private final Path dir;
    private final Path file;
    private final String version;
    private final Source source;
    private final LongSupplier clock;
    private final Object lock = new Object();
    private final Object fileLock = new Object();
    /** The last errors, oldest first; the same text again moves to the end and counts up. Guarded by lock. */
    private final LinkedHashMap<String, Err> errors = new LinkedHashMap<>();
    private boolean errorsChanged;
    private boolean running = true;
    private boolean written;
    private long lastWrite;
    private String lastSignature;
    private State lastState = State.empty();
    private ScheduledExecutorService timer;
    private Consumer<String> logListener;

    SuiteDiagnose(Path suiteDir, String version, Source source, LongSupplier clock) {
        this.dir = suiteDir.resolve("diagnose");
        this.file = dir.resolve(ID + ".json");
        this.version = version;
        this.source = source;
        this.clock = clock;
    }

    /**
     * Starts the diagnostics for this program run: writes the file now, then checks every {@link #POLL_MS} and takes
     * the warnings and errors of the error stream ({@link LogBuffer}). Never throws; returns null when it cannot start.
     */
    static SuiteDiagnose start(String version, Source source) {
        try {
            SuiteDiagnose d = new SuiteDiagnose(suiteDataDir(), version, source, System::currentTimeMillis);
            d.tick();
            d.logListener = line -> {
                String text = DiagnoseReport.errorText(line);
                if (text != null) {
                    d.error(text);
                }
            };
            LogBuffer.addErrorListener(d.logListener);
            ScheduledExecutorService t = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread th = new Thread(r, "suite-diagnose");
                th.setDaemon(true);
                th.setPriority(Thread.MIN_PRIORITY);
                return th;
            });
            t.scheduleWithFixedDelay(d::tickQuietly, POLL_MS, POLL_MS, TimeUnit.MILLISECONDS);
            d.timer = t;
            return d;
        } catch (RuntimeException e) {
            return null; // without diagnostics: never disturb the show
        }
    }

    Path file() {
        return file;
    }

    /** The Link-Suite data folder of this user (see {@link #suiteDataDir(String, String, Function, String)}). */
    static Path suiteDataDir() {
        return suiteDataDir(System.getProperty("os.name", ""), System.getProperty("user.home", ""), System::getenv,
                System.getProperty(DIR_PROPERTY));
    }

    /**
     * macOS {@code ~/Library/Application Support/Link-Suite}, Windows {@code %APPDATA%\Link-Suite}, otherwise
     * {@code $XDG_CONFIG_HOME/Link-Suite} or {@code ~/.config/Link-Suite}; {@code override} wins. Everything is taken
     * from {@code user.home}: APPDATA and XDG_CONFIG_HOME count only when they lie inside it, so a test instance with
     * its own {@code -Duser.home} never writes into the real folder of the user (and so never over the running app's file).
     */
    static Path suiteDataDir(String osName, String userHome, Function<String, String> env, String override) {
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        String os = osName.toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String appData = env.apply("APPDATA");
            return inside(appData, userHome) ? Path.of(appData, "Link-Suite") : Path.of(userHome, "AppData", "Roaming", "Link-Suite");
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return Path.of(userHome, "Library", "Application Support", "Link-Suite");
        }
        String xdg = env.apply("XDG_CONFIG_HOME");
        return inside(xdg, userHome) ? Path.of(xdg, "Link-Suite") : Path.of(userHome, ".config", "Link-Suite");
    }

    private static boolean inside(String path, String home) {
        if (path == null || path.isBlank() || home == null || home.isBlank()) {
            return false;
        }
        String p = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        String h = home.replace('\\', '/').toLowerCase(Locale.ROOT);
        h = h.endsWith("/") ? h : h + "/";
        return p.startsWith(h);
    }

    /** Remembers a warning or an error (the last {@link #MAX_ITEMS}); cheap, so it may be called for every log line. */
    void error(String text) {
        String t = clean(text, MAX_ERROR);
        if (t.isEmpty()) {
            return;
        }
        long now = clock.getAsLong();
        synchronized (lock) {
            if (!running) {
                return;
            }
            Err e = errors.remove(t);
            if (e == null) {
                e = new Err(t);
            }
            e.count++;
            e.atMs = now;
            errors.put(t, e);
            while (errors.size() > MAX_ITEMS) {
                Iterator<String> it = errors.keySet().iterator();
                it.next();
                it.remove();
            }
            errorsChanged = true;
        }
    }

    private void tickQuietly() {
        try {
            tick();
        } catch (RuntimeException e) {
            // ignored on purpose: the next tick tries again
        }
    }

    /**
     * Writes the file when it is due: the first time, after {@link #REFRESH_MS}, or when the signature changed or an
     * error came in, but never within {@link #MIN_GAP_MS} of the last write. Returns whether it wrote (or tried to).
     */
    boolean tick() {
        long now = clock.getAsLong();
        String sig = signature();
        synchronized (lock) {
            if (!running || !due(now, sig)) {
                return false;
            }
        }
        // The source is asked outside the lock: it may print (and so call error() on another thread).
        State st = state();
        String text;
        synchronized (lock) {
            if (!running || !due(now, sig)) {
                return false;
            }
            lastState = st;
            lastSignature = sig;
            errorsChanged = false;
            written = true;
            lastWrite = now;
            text = render(now, true);
        }
        write(text);
        return true;
    }

    private boolean due(long now, String sig) {
        if (!written) {
            return true;
        }
        if (now - lastWrite < MIN_GAP_MS) {
            return false;
        }
        return now - lastWrite >= REFRESH_MS || errorsChanged || !Objects.equals(sig, lastSignature);
    }

    /** At the end of the program: writes {@code running: false} right away (with the last state). Only once. */
    void stop() {
        String text;
        synchronized (lock) {
            if (!running) {
                return;
            }
            running = false;
            text = render(clock.getAsLong(), false);
        }
        if (logListener != null) {
            LogBuffer.removeErrorListener(logListener);
        }
        if (timer != null) {
            timer.shutdownNow();
        }
        write(text);
    }

    private String signature() {
        try {
            return source.signature();
        } catch (RuntimeException e) {
            return lastSignature;
        }
    }

    private State state() {
        try {
            State s = source.state();
            return s == null ? State.empty() : s;
        } catch (RuntimeException e) {
            return lastState;
        }
    }

    /** The file content (guarded by lock): the fields of SPEC.md, every text cleaned and the whole at most 16 KB. */
    private String render(long now, boolean isRunning) {
        ObjectNode doc = Hub.JSON.createObjectNode();
        doc.put("suite", "diagnose");
        doc.put("v", 1);
        doc.put("id", ID);
        doc.put("app", APP);
        doc.put("version", clean(version, 40));
        doc.put("at", iso(now));
        doc.put("running", isRunning);
        doc.put("os", osName(System.getProperty("os.name", ""), System.getProperty("os.version", "")));
        doc.put("arch", arch(System.getProperty("os.arch", "")));
        ObjectNode summary = doc.putObject("summary");
        int n = 0;
        for (Map.Entry<String, String> e : lastState.summary().entrySet()) {
            if (n++ >= MAX_ITEMS) {
                break;
            }
            summary.put(clean(e.getKey(), MAX_KEY), clean(e.getValue(), MAX_VALUE));
        }
        ArrayNode checks = doc.putArray("checks");
        for (Check c : lastState.checks().stream().limit(MAX_ITEMS).toList()) {
            ObjectNode o = checks.addObject();
            o.put("name", clean(c.name(), MAX_KEY));
            o.put("ok", c.ok());
            o.put("detail", clean(c.detail(), MAX_VALUE));
        }
        ArrayNode errs = doc.putArray("errors");
        for (Err e : errors.values()) {
            ObjectNode o = errs.addObject();
            o.put("at", iso(e.atMs));
            String suffix = e.count > 1 ? " (" + e.count + "x)" : "";
            o.put("text", clean(e.text, MAX_ERROR - suffix.length()) + suffix);
        }
        String text = toText(doc);
        // Too big: the oldest errors go first, then the last checks, then the last summary lines.
        while (text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            if (!errs.isEmpty()) {
                errs.remove(0);
            } else if (!checks.isEmpty()) {
                checks.remove(checks.size() - 1);
            } else if (!summary.isEmpty()) {
                List<String> keys = new ArrayList<>();
                summary.fieldNames().forEachRemaining(keys::add);
                summary.remove(keys.get(keys.size() - 1));
            } else {
                break;
            }
            text = toText(doc);
        }
        return text;
    }

    private static String toText(ObjectNode doc) {
        try {
            return Hub.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(doc) + "\n";
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** {@code <id>.json.tmp}, then renamed over {@code <id>.json}; any failure is swallowed. */
    private void write(String text) {
        synchronized (fileLock) {
            Path tmp = dir.resolve(ID + ".json.tmp");
            // Only where the launcher lives: the Link-Suite folder is never created here, only diagnose/ inside it (SPEC 1.2).
            if (!Files.isDirectory(dir.getParent())) {
                return;
            }
            try {
                Files.createDirectories(dir);
                Files.writeString(tmp, text, StandardCharsets.UTF_8);
                try {
                    Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException | RuntimeException e) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException | RuntimeException ignored) {
                    // without diagnostics: never disturb the show
                }
            }
        }
    }

    static String iso(long ms) {
        return Instant.ofEpochMilli(ms).truncatedTo(ChronoUnit.SECONDS).toString();
    }

    /** "macOS 15.1", "Windows 11 (10.0)", "Linux 6.8". */
    static String osName(String name, String version) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.contains("mac")) {
            return "macOS " + version;
        }
        if (n.contains("win")) {
            return name + " (" + version + ")";
        }
        return name + " " + version;
    }

    /** "arm64" or "x64" as in Node; anything else as Java reports it. */
    static String arch(String javaArch) {
        return switch (javaArch) {
            case "aarch64", "arm64" -> "arm64";
            case "amd64", "x86_64" -> "x64";
            default -> javaArch;
        };
    }

    // ------------------------------------------------------------------ cleaning (first check, see SPEC.md)

    private static final List<Map.Entry<Pattern, Function<Matcher, String>>> RULES = new ArrayList<>();
    private static final Pattern CLOCK_TIME = Pattern.compile("^\\d{1,2}(?::\\d{2}){1,3}$");
    /** The name of this computer, learnt once in the background ({@link #learnHostName}); null until then. */
    private static volatile String hostName = envHostName();

    private static void rule(String regex, String replacement) {
        RULES.add(Map.entry(Pattern.compile(regex), m -> replacement));
    }

    static {
        // The rules of clean() in the Suite-Launcher template diagnose-writer.ts v1.1 (28 Nov 2026, own project,
        // EPL-2.0), with the same placeholders; user and volume names with spaces are taken up to the next separator,
        // and an IPv4 address inside a longer dotted number (a version such as 21.0.13.0.3) is left alone.
        rule("(?i)file:/{1,3}[^\\s\"'<>)]*", "[Datei]");
        rule("(?i)\\b[A-Za-z]:[\\\\/]+(?:Users|Dokumente und Einstellungen|Documents and Settings)[\\\\/]+[^\\\\/\"']+", "~");
        rule("/(?:Users|home)/[^/\"']+", "~");
        rule("/private/var/folders/[^\\s\"']*", "[tmp]");
        rule("/Volumes/[^/\"']+", "/Volumes/[Laufwerk]");
        rule("\\\\\\\\[^\\\\\\s\"']+\\\\[^\\\\\\s\"']+", "[Freigabe]");
        rule("\\b[A-Za-z]:[\\\\/][^\\s\"']*", "[Pfad]");
        rule("[\\w.+-]+@[\\w-]+\\.[\\w.-]+", "[E-Mail]");
        rule("(?i)\\b[0-9a-f]{2}(?:[:-][0-9a-f]{2}){5}\\b", "[MAC]");
        RULES.add(Map.entry(Pattern.compile("(?i)(?<![\\w:.])[0-9a-f]*(?::[0-9a-f]*){2,}(?![\\w:])"), m -> {
            String v = m.group();
            boolean ipv6 = (v.contains("::") || v.split(":", -1).length >= 6) && !CLOCK_TIME.matcher(v).matches();
            return ipv6 ? "[IPv6]" : v; // times and timecodes stay
        }));
        RULES.add(Map.entry(Pattern.compile("(?<![\\d.])\\d{1,3}(?:\\.\\d{1,3}){3}(?!\\.?\\d)"),
                m -> showNet(m.group()) ? m.group() : "[IP]"));
        rule("(?i)\\b[\\w-]+\\.(?:local|lan|fritz\\.box)\\b", "[Rechner]");
        rule("\\b[A-Za-z0-9_\\-]{32,}\\b", "[Kennung]");
    }

    /**
     * Show network: private, loopback, link-local and Art-Net (2.x, 10.x), as the launcher allows them (isShowIp of the
     * template); everything else counts as public and is replaced.
     */
    static boolean showNet(String ip) {
        String[] p = ip.split("\\.");
        int a = Integer.parseInt(p[0]);
        int b = Integer.parseInt(p[1]);
        if (a > 255 || b > 255 || Integer.parseInt(p[2]) > 255 || Integer.parseInt(p[3]) > 255) {
            return true; // not an address (a version number or the like)
        }
        return a == 10 || a == 2 || a == 127 || a == 172 && b >= 16 && b <= 31 || a == 192 && b == 168 || a == 169 && b == 254;
    }

    private static String envHostName() {
        for (String k : new String[] {"COMPUTERNAME", "HOSTNAME"}) {
            String v = System.getenv(k);
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    /** Asks the system for the computer name once (may take a moment without DNS, so never on a printing thread). */
    static void learnHostName() {
        try {
            String h = java.net.InetAddress.getLocalHost().getHostName();
            if (h != null && !h.isBlank()) {
                hostName = h.trim();
            }
        } catch (IOException | RuntimeException e) {
            // the .local rule still catches most names
        }
    }

    /**
     * The first line only (no stack trace), temp folder as [tmp], user folder as ~ (in any case), computer name as
     * [Rechner], nothing from the "never" list (SPEC.md), at most {@code max} characters.
     */
    static String clean(String s, int max) {
        return clean(s, max, hostName);
    }

    static String clean(String s, int max, String host) {
        return clean(s, max, homes(), System.getProperty("java.io.tmpdir", ""), host);
    }

    /** {@link #clean(String, int)} with the user folders, temp folder and computer name given (tests, like the template's env). */
    static String clean(String s, int max, List<String> homeDirs, String tmpDir, String host) {
        if (s == null) {
            return "";
        }
        String t = s.split("\\R", 2)[0].replace('\t', ' ');
        String tmp = tmpDir == null ? "" : tmpDir;
        tmp = tmp.endsWith("/") || tmp.endsWith("\\") ? tmp.substring(0, tmp.length() - 1) : tmp;
        if (tmp.length() > 3) {
            t = replaceIgnoreCase(t, tmp.startsWith("/var/") ? "/private" + tmp : tmp, "[tmp]");
            t = replaceIgnoreCase(t, tmp, "[tmp]");
        }
        for (String home : homeDirs) {
            if (home != null && home.length() > 1) {
                t = replaceIgnoreCase(t, home, "~");
            }
        }
        if (host != null) {
            String h = host.replaceFirst("(?i)\\.local$", "");
            if (h.length() > 2 && !"localhost".equalsIgnoreCase(h)) {
                t = Pattern.compile("(?<![\\w-])" + Pattern.quote(h) + "(?![\\w-])", Pattern.CASE_INSENSITIVE).matcher(t)
                        .replaceAll("[Rechner]");
            }
        }
        for (Map.Entry<Pattern, Function<Matcher, String>> r : RULES) {
            Matcher m = r.getKey().matcher(t);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                m.appendReplacement(sb, Matcher.quoteReplacement(r.getValue().apply(m)));
            }
            m.appendTail(sb);
            t = sb.toString();
        }
        t = t.strip();
        if (t.length() > max) {
            int cut = Math.max(0, max - 1);
            if (cut > 0 && Character.isHighSurrogate(t.charAt(cut - 1))) {
                cut--;
            }
            t = t.substring(0, cut) + "…";
        }
        return t;
    }

    /** Every path-like word (two separators or more) as [Pfad], for texts that come from exceptions or the log. */
    static String withoutPaths(String s) {
        return s == null ? "" : s.replaceAll("(?:[A-Za-z]:)?[\\\\/][^\\s:\"'()<>\\[\\]]*[\\\\/][^\\s:\"'()<>\\[\\]]*", "[Pfad]");
    }

    private static List<String> homes() {
        List<String> out = new ArrayList<>();
        for (String h : new String[] {System.getProperty("user.home"), System.getenv("HOME"), System.getenv("USERPROFILE")}) {
            if (h != null && h.length() > 3 && !out.contains(h)) {
                out.add(h.endsWith("/") || h.endsWith("\\") ? h.substring(0, h.length() - 1) : h);
            }
        }
        return out;
    }

    private static String replaceIgnoreCase(String text, String what, String with) {
        if (what == null || what.isEmpty()) {
            return text;
        }
        return Pattern.compile(Pattern.quote(what), Pattern.CASE_INSENSITIVE).matcher(text).replaceAll(Matcher.quoteReplacement(with));
    }
}
