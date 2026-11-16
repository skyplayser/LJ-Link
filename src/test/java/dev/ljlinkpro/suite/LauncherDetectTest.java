package dev.ljlinkpro.suite;

// Copied unchanged from the Suite-Launcher project, design/launcher-hinweis/ (module 1.0, 16 Nov 2026).
// Do not edit here: changes belong in the Suite-Launcher, then copy again.

// Tests zu LauncherDetect (JUnit 5, wie LJ Link build.gradle: org.junit.jupiter:junit-jupiter 5.11.4).
// Nach src/test/java/dev/ljlinkpro/suite/ kopieren; ./gradlew test.

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

class LauncherDetectTest {
    static String plist(String id, String v) {
        return "<?xml version=\"1.0\"?><plist><dict><key>CFBundleIdentifier</key>\n<string>" + id
            + "</string><key>CFBundleShortVersionString</key>\n<string>" + v + "</string></dict></plist>";
    }

    static final class Fake implements LauncherDetect.Env {
        final String os; final Map<String, String> files = new HashMap<>(); final Map<String, String> runs = new HashMap<>();
        final Map<String, String> env = new HashMap<>(); final List<String> calls = new ArrayList<>();
        Fake(String os) { this.os = os; }
        public String os() { return os; }
        public String home() { return "/Users/dj"; }
        public String env(String n) { return env.get(n); }
        public boolean exists(Path p) { return files.containsKey(p.toString().replace('\\', '/')); }
        public Optional<String> read(Path p) { return Optional.ofNullable(files.get(p.toString().replace('\\', '/'))); }
        public Optional<String> run(List<String> cmd, long t) {
            String c = String.join(" ", cmd); calls.add(c);
            return runs.entrySet().stream().filter(x -> c.contains(x.getKey())).map(Map.Entry::getValue).findFirst();
        }
    }

    static final String REG = """
        HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\5f2a1c0e-1111-5b3e-9c3e-aaaaaaaaaaaa
            DisplayName    REG_SZ    Link-Suite 0.3.0
            DisplayVersion    REG_SZ    0.3.0
            InstallLocation    REG_SZ    C:/Users/dj/AppData/Local/Programs/Link-Suite

        HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\Other
            DisplayName    REG_SZ    Link-Suite Helper Tools

        End of search: 4 match(es) found.
        """;

    @Test void macApplications() {
        Fake e = new Fake("Mac OS X");
        e.files.put("/Applications/Link-Suite.app/Contents/Info.plist", plist(LauncherDetect.BUNDLE_ID, "0.3.0"));
        var r = LauncherDetect.detect(e, true);
        assertTrue(r.installed()); assertEquals("0.3.0", r.version()); assertEquals("path", r.via()); assertTrue(e.calls.isEmpty());
    }

    @Test void macForeignBundleId() {
        Fake e = new Fake("Mac OS X");
        e.files.put("/Applications/Link-Suite.app/Contents/Info.plist", plist("com.example.other", "1.0"));
        assertFalse(LauncherDetect.detect(e, false).installed());
    }

    @Test void macSpotlightSkipsTrashAndDmg() {
        Fake e = new Fake("Mac OS X");
        e.files.put("/Users/dj/Tools/Link-Suite.app/Contents/Info.plist", plist(LauncherDetect.BUNDLE_ID, "0.2.0"));
        e.runs.put("mdfind", "/Users/dj/.Trash/Link-Suite.app\n/Volumes/Link-Suite/Link-Suite.app\n/Users/dj/Tools/Link-Suite.app\n");
        var r = LauncherDetect.detect(e, true);
        assertEquals("mdfind", r.via()); assertEquals("/Users/dj/Tools/Link-Suite.app", r.path());
    }

    @Test void macNothing() {
        var r = LauncherDetect.detect(new Fake("Mac OS X"), true);
        assertFalse(r.installed()); assertEquals("none", r.via());
    }

    @Test void regParseOnlyExactName() {
        var r = LauncherDetect.parseRegQuery(REG);
        assertEquals(1, r.size()); assertEquals("0.3.0", r.get(0).get("DisplayVersion"));
    }

    @Test void windowsHkcuWithExe() {
        Fake e = new Fake("Windows 11");
        e.runs.put("HKCU", REG);
        e.files.put("C:/Users/dj/AppData/Local/Programs/Link-Suite/Link-Suite.exe", "");
        var r = LauncherDetect.detect(e, true);
        assertTrue(r.installed()); assertEquals("registry", r.via()); assertEquals("0.3.0", r.version());
    }

    @Test void windowsRegistryLeftoverWithoutExe() {
        Fake e = new Fake("Windows 11");
        e.runs.put("HKCU", REG);
        assertFalse(LauncherDetect.detect(e, true).installed());
    }

    @Test void linuxUnsupported() {
        assertEquals("unsupported", LauncherDetect.detect(new Fake("Linux"), true).via());
    }

    @Test void json() {
        assertEquals("{\"installed\":true,\"version\":\"0.3.0\",\"via\":\"path\"}", new LauncherDetect.Info(true, "/x", "0.3.0", "path", 1).toJson());
    }

    static boolean realMac() { return Files.exists(Path.of("/Applications/Link-Suite.app")); }

    @Test @EnabledIf("realMac") void realUnder200ms() {
        var r = LauncherDetect.detect();
        assertTrue(r.installed()); assertTrue(r.ms() < 200, "zu langsam: " + r.ms());
    }
}
