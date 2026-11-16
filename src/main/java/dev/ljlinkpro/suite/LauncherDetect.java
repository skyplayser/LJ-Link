package dev.ljlinkpro.suite;

// Copied unchanged from the Suite-Launcher project, design/launcher-hinweis/ (module 1.0, 16 Nov 2026).
// Do not edit here: changes belong in the Suite-Launcher, then copy again.

// Link-Suite · Erkennung „Ist der Launcher Link-Suite installiert?“ (Baustein 1.0, 16.11.2026) – Vorlage für LJ Link.
// Java 21, nur JDK. Lokal, ohne Netz, ohne Adminrechte, Ziel < 200 ms. Gleiche Regeln wie launcher-detect.ts:
// macOS: /Applications und ~/Applications (Info.plist, Bundle-ID de.nextphilm.linksuite), sonst mdfind mit Zeitlimit.
// Windows: Standardordner von electron-builder, dann Registry HKCU, HKLM als Rückfall (reg.exe, Zeitlimit).
// Tests: LauncherDetectTest.java (JUnit 5, wie LJ Link build.gradle).

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LauncherDetect {
    public static final String BUNDLE_ID = "de.nextphilm.linksuite";
    public static final String PRODUCT = "Link-Suite";
    private static final String UNINSTALL = "Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall";

    /** Ergebnis; path/version können null sein. via: path | mdfind | registry | none | unsupported. */
    public record Info(boolean installed, String path, String version, String via, long ms) {
        /** Für die Weboberfläche (/api/suite/launcher): kleines JSON ohne Bibliothek. */
        public String toJson() {
            return "{\"installed\":" + installed + ",\"version\":" + str(version) + ",\"via\":" + str(via) + "}";
        }
        private static String str(String s) { return s == null ? "null" : "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
    }

    /** Zugriff auf das System – für Tests austauschbar. */
    public interface Env {
        String os();                                  // System.getProperty("os.name")
        String home();
        String env(String name);
        boolean exists(Path p);
        Optional<String> read(Path p);
        /** Programm mit Zeitlimit; leer bei Fehler oder Zeitüberschreitung */
        Optional<String> run(List<String> cmd, long timeoutMs);
    }

    public static Env systemEnv() {
        return new Env() {
            public String os() { return System.getProperty("os.name", ""); }
            public String home() { return System.getProperty("user.home", ""); }
            public String env(String n) { return System.getenv(n); }
            public boolean exists(Path p) { return Files.exists(p); }
            public Optional<String> read(Path p) {
                try { return Optional.of(Files.readString(p, StandardCharsets.UTF_8)); }
                catch (IOException | RuntimeException e) {
                    // Binär-plist ist kein UTF-8: dann als „da, ohne Version“ melden
                    try { byte[] b = Files.readAllBytes(p); return Optional.of(new String(b, StandardCharsets.ISO_8859_1)); }
                    catch (IOException e2) { return Optional.empty(); }
                }
            }
            public Optional<String> run(List<String> cmd, long timeoutMs) {
                try {
                    Process pr = new ProcessBuilder(cmd).redirectErrorStream(true).start();
                    if (!pr.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) { pr.destroyForcibly(); return Optional.empty(); }
                    if (pr.exitValue() != 0) return Optional.empty();
                    return Optional.of(new String(pr.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
                } catch (IOException e) { return Optional.empty(); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); return Optional.empty(); }
            }
        };
    }

    public static Info detect() { return detect(systemEnv(), true); }

    /** Wirft nie; im Zweifel installed=false. search=false: kein mdfind/reg. */
    public static Info detect(Env e, boolean search) {
        long t0 = System.nanoTime();
        try {
            String os = e.os().toLowerCase();
            if (os.contains("mac")) {
                for (Path app : List.of(Path.of("/Applications", PRODUCT + ".app"), Path.of(e.home(), "Applications", PRODUCT + ".app"))) {
                    String[] ok = checkApp(e, app);
                    if (ok != null) return info(t0, true, app.toString(), ok[0], "path");
                }
                if (search) {
                    String out = e.run(List.of("/usr/bin/mdfind", "kMDItemCFBundleIdentifier == '" + BUNDLE_ID + "'"), 150).orElse("");
                    for (String line : out.split("\n")) {
                        String p = line.trim();
                        if (!p.endsWith(".app") || p.contains("/.Trash/") || p.startsWith("/Volumes/")) continue;
                        String[] ok = checkApp(e, Path.of(p));
                        if (ok != null) return info(t0, true, p, ok[0], "mdfind");
                    }
                }
                return info(t0, false, null, null, "none");
            }
            if (os.contains("win")) {
                String hit = null;
                for (String base : new String[]{e.env("LOCALAPPDATA") == null ? null : e.env("LOCALAPPDATA") + "\\Programs", e.env("ProgramFiles")}) {
                    if (base != null && e.exists(Path.of(base, PRODUCT, PRODUCT + ".exe"))) { hit = base + "\\" + PRODUCT; break; }
                }
                if (search || hit == null) {
                    for (String hive : new String[]{"HKCU", "HKLM"}) {
                        String out = e.run(List.of("reg", "query", hive + "\\" + UNINSTALL, "/s", "/f", PRODUCT, "/d"), 400).orElse("");
                        List<Map<String, String>> found = parseRegQuery(out);
                        if (found.isEmpty()) continue;
                        Map<String, String> f = found.get(0);
                        String loc = f.get("InstallLocation");
                        if (hit == null && (loc == null || !e.exists(Path.of(loc, PRODUCT + ".exe")))) continue;   // Reste ohne Programm
                        return info(t0, true, hit != null ? hit : loc, f.get("DisplayVersion"), "registry");
                    }
                }
                return hit != null ? info(t0, true, hit, null, "path") : info(t0, false, null, null, "none");
            }
            return info(t0, false, null, null, "unsupported");
        } catch (RuntimeException ex) {
            return info(t0, false, null, null, "none");
        }
    }

    /** null = keine passende App; sonst {version oder null}. */
    static String[] checkApp(Env e, Path app) {
        Optional<String> xml = e.read(app.resolve("Contents").resolve("Info.plist"));
        if (xml.isEmpty()) return null;
        String s = xml.get();
        if (s.startsWith("bplist")) return new String[]{null};                 // binär: Name passt, Version unbekannt
        String id = plistValue(s, "CFBundleIdentifier");
        if (!BUNDLE_ID.equals(id)) return null;
        return new String[]{plistValue(s, "CFBundleShortVersionString")};
    }

    static String plistValue(String xml, String key) {
        Matcher m = Pattern.compile("<key>" + Pattern.quote(key) + "</key>\\s*<string>([^<]*)</string>").matcher(xml);
        return m.find() ? m.group(1).trim() : null;
    }

    private static final Pattern NAME = Pattern.compile("^Link-Suite(\\s+\\d[\\w.\\-]*)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern VALUE = Pattern.compile("^\\s+(\\S+)\\s+REG_\\w+\\s+(.*)$");

    /** Ausgabe von reg query … /s /f Link-Suite /d → Einträge mit DisplayName „Link-Suite“ bzw. „Link-Suite 0.3.0“. */
    static List<Map<String, String>> parseRegQuery(String out) {
        List<Map<String, String>> res = new ArrayList<>();
        Map<String, String> cur = new HashMap<>();
        for (String raw : (out + "\nHKEY_END").split("\\r?\\n")) {
            String line = raw.stripTrailing();
            if (line.startsWith("HKEY_")) {
                String name = cur.get("DisplayName");
                if (cur.containsKey("key") && name != null && NAME.matcher(name.trim()).matches()) res.add(cur);
                cur = new HashMap<>();
                cur.put("key", line.trim());
                continue;
            }
            Matcher m = VALUE.matcher(line);
            if (m.matches()) cur.put(m.group(1), m.group(2).trim());
        }
        return res;
    }

    private static Info info(long t0, boolean ok, String path, String version, String via) {
        return new Info(ok, path, version, via, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0));
    }

    /** Schnelltest von Hand: java LauncherDetect.java */
    public static void main(String[] args) { System.out.println(detect()); }
}
