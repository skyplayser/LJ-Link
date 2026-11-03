package dev.ljlinkpro;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.function.DoubleConsumer;

/**
 * Looks for a newer release on GitHub and installs it. On macOS the new app replaces the old one in place (a small
 * script waits until this program has quit, copies the app out of the disk image and starts it again); on Windows the
 * installer is started, it replaces the installed version.
 */
final class Updater {
    private Updater() {}

    static final String REPO = "skyplayser/LJ-Link";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    record Release(String version, String downloadUrl, String assetName, long size, String page) {}

    private static volatile Release cached;
    private static volatile long cachedAt;

    /** The newest release, asked from GitHub at most once an hour; null if unknown (offline, development build). */
    static synchronized Release latestCached() {
        long now = System.currentTimeMillis();
        if (now - cachedAt > 3_600_000L) {
            cachedAt = now;
            try {
                cached = latest();
            } catch (Exception e) {
                cached = null;
            }
        }
        return cached;
    }

    /** The version of this program, from the jar manifest; "dev" when started from the sources. */
    static String currentVersion() {
        String v = Updater.class.getPackage().getImplementationVersion();
        return v == null || v.isBlank() ? "dev" : v;
    }

    /** The newest release with an installer for this computer, or null if there is none. */
    static Release latest() throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + REPO + "/releases/latest"))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "LJ-Link-PRO/" + currentVersion())
                .timeout(Duration.ofSeconds(15))
                .GET().build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() == 404) {
            throw new IOException("No public release found (is the repository public?)");
        }
        if (res.statusCode() != 200) {
            throw new IOException("GitHub answered " + res.statusCode());
        }
        JsonNode json = Hub.JSON.readTree(res.body());
        String version = json.path("tag_name").asText("").replaceFirst("^v", "");
        String wanted = assetName();
        for (JsonNode a : json.path("assets")) {
            if (a.path("name").asText().equals(wanted)) {
                return new Release(version, a.path("browser_download_url").asText(), wanted, a.path("size").asLong(),
                        json.path("html_url").asText());
            }
        }
        return null;
    }

    /** True if {@code candidate} is newer than {@code current}; a development build is never offered an update. */
    static boolean isNewer(String candidate, String current) {
        if (current == null || current.equals("dev") || candidate == null || candidate.isBlank()) {
            return false;
        }
        String[] a = candidate.split("[.\\-]");
        String[] b = current.split("[.\\-]");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? number(a[i]) : 0;
            int y = i < b.length ? number(b[i]) : 0;
            if (x != y) {
                return x > y;
            }
        }
        return false;
    }

    private static int number(String s) {
        try {
            return Integer.parseInt(s.replaceAll("\\D", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** The installer that fits this computer, as named by the release workflow. */
    static String assetName() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return "LJ-Link-PRO-windows.exe";
        }
        return arch.contains("aarch64") || arch.contains("arm") ? "LJ-Link-PRO-macos-arm64.dmg" : "LJ-Link-PRO-macos-intel.dmg";
    }

    /** Downloads the installer into a temporary folder; {@code progress} gets values from 0 to 1. */
    static Path download(Release r, DoubleConsumer progress) throws IOException, InterruptedException {
        Path dir = Files.createTempDirectory("ljlinkpro-update");
        Path file = dir.resolve(r.assetName());
        HttpRequest req = HttpRequest.newBuilder(URI.create(r.downloadUrl()))
                .header("User-Agent", "LJ-Link-PRO/" + currentVersion())
                .GET().build();
        HttpResponse<InputStream> res = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (res.statusCode() != 200) {
            throw new IOException("Download failed: " + res.statusCode());
        }
        long total = res.headers().firstValueAsLong("Content-Length").orElse(r.size());
        try (InputStream in = res.body(); OutputStream out = Files.newOutputStream(file)) {
            byte[] buf = new byte[64 * 1024];
            long done = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                done += n;
                if (total > 0) {
                    progress.accept(Math.min(1, done / (double) total));
                }
            }
        }
        if (r.size() > 0 && Files.size(file) != r.size()) {
            throw new IOException("Download incomplete");
        }
        return file;
    }

    /**
     * Starts the installation and returns; the caller quits the program right after. On macOS a script replaces the
     * running app once it has quit and opens the new one; outside an app bundle (development) the disk image is opened.
     */
    static void install(Path installer) throws IOException {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            new ProcessBuilder(installer.toString()).start(); // keeps running after this program has quit
            return;
        }
        Path app = runningAppBundle();
        if (app == null) {
            new ProcessBuilder("open", installer.toString()).start();
            return;
        }
        Path script = installer.resolveSibling("update.sh");
        Files.writeString(script, """
                #!/bin/bash
                # Replaces LJ Link with the new version once the running program has quit.
                PID="$1"; DMG="$2"; APP="$3"
                for i in $(seq 1 120); do kill -0 "$PID" 2>/dev/null || break; sleep 0.5; done
                MNT="$(mktemp -d)"
                if ! hdiutil attach -nobrowse -readonly -mountpoint "$MNT" "$DMG" >/dev/null; then open "$DMG"; exit 1; fi
                SRC="$(ls -d "$MNT"/*.app | head -1)"
                if [ -n "$SRC" ]; then
                  rm -rf "$APP.old"
                  if mv "$APP" "$APP.old" && ditto "$SRC" "$APP"; then rm -rf "$APP.old"; else rm -rf "$APP"; mv "$APP.old" "$APP"; fi
                fi
                hdiutil detach "$MNT" -quiet
                open "$APP"
                """);
        new ProcessBuilder("/bin/bash", script.toString(), Long.toString(ProcessHandle.current().pid()),
                installer.toString(), app.toString())
                .redirectOutput(installer.resolveSibling("update.log").toFile())
                .redirectErrorStream(true)
                .start();
    }

    /** The .app folder this program runs from, or null when it is not a packaged app. */
    static Path runningAppBundle() {
        String launcher = System.getProperty("jpackage.app-path");
        if (launcher == null) {
            return null;
        }
        // .../LJ Link.app/Contents/MacOS/LJ Link (installs from before the rename keep the folder LJ-Link-PRO.app)
        Path p = Path.of(launcher).getParent();
        for (int i = 0; i < 3 && p != null; i++) {
            if (p.getFileName() != null && p.getFileName().toString().endsWith(".app")) {
                return p;
            }
            p = p.getParent();
        }
        return null;
    }
}
