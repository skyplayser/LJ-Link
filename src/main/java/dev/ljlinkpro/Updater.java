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
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleConsumer;

/**
 * Looks for a newer release on GitHub and installs it. On macOS the new app replaces the old one in place (a small
 * script waits until this program has quit, copies the app out of the disk image and starts it again); on Windows the
 * installer is started, it replaces the installed version.
 *
 * <p>Before anything is opened or installed the download is checked (see ADR "Update integrity"):
 * <ol>
 *   <li>its SHA-256 must equal the {@code digest} GitHub reports for the release asset; without a digest there is no
 *       update;</li>
 *   <li>when {@link #SIGNING_PUBLIC_KEY} is set, the file {@code <asset>.sig} next to the asset must hold a valid Ed25519
 *       signature of that SHA-256 (the 32 raw bytes), made with the developer's offline key
 *       ({@code scripts/sign-release.sh}). A missing or wrong signature means no update. While the key is still the
 *       empty placeholder only the checksum is checked and the dialog says "not signed".</li>
 * </ol>
 * Any failure deletes the download and installs nothing.
 */
final class Updater {
    private Updater() {}

    static final String REPO = "skyplayser/LJ-Link";

    /**
     * Ed25519 public key that release signatures are checked with: Base64 of the X.509 (SubjectPublicKeyInfo, DER) form,
     * 44 characters starting with "MCowBQYDK2VwAyEA". Print it with {@code scripts/sign-release.sh --public-key KEY.pem}.
     *
     * <p>PLACEHOLDER: empty until the developer has created the key pair (the private key stays offline, never in
     * GitHub). While it is empty, updates are only checked against GitHub's SHA-256 and are shown as "not signed".
     * Once it is set, every release needs {@code <asset>.sig} files, otherwise the program offers no installation.
     */
    static final String SIGNING_PUBLIC_KEY = "";

    /** Sent with every request to GitHub: program name and version, nothing else. */
    static String userAgent() {
        return "LJ-Link/" + currentVersion();
    }

    /** Settings key of "Check for updates automatically" (default on). */
    static final String AUTO_CHECK_KEY = "autoUpdateCheck";

    /** Whether the program may ask GitHub on its own (at start and for the version line). The menu item always works. */
    static boolean autoCheck() {
        return AppSettings.getBoolean(AUTO_CHECK_KEY, true);
    }
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * A release asset for this computer. {@code sha256} is the hex checksum from GitHub's {@code digest} (null when GitHub
     * gave none), {@code signatureUrl} the download address of {@code <asset>.sig} (null when there is none).
     */
    record Release(String version, String downloadUrl, String assetName, long size, String page, String sha256,
                   String signatureUrl) {}

    /** How a release is signed, for the update dialog. */
    enum SignatureState {
        /** The program has no public key yet (placeholder): only the checksum is checked. */
        NOT_CONFIGURED,
        /** The program has a key but the release has no .sig file: no installation. */
        MISSING,
        /** A .sig file is there; it is checked after the download. */
        PRESENT
    }

    static SignatureState signatureState(Release r) {
        if (!signingConfigured()) {
            return SignatureState.NOT_CONFIGURED;
        }
        return r.signatureUrl() == null ? SignatureState.MISSING : SignatureState.PRESENT;
    }

    static boolean signingConfigured() {
        return !SIGNING_PUBLIC_KEY.isBlank();
    }

    /** Thrown when a download must not be installed; the message is shown to the user. */
    static final class VerificationException extends IOException {
        VerificationException(String message) {
            super(message);
        }
    }

    private static volatile Release cached;
    private static volatile long cachedAt;

    /**
     * The newest release, asked from GitHub at most once an hour; null if unknown (offline, development build) or when
     * automatic update checks are switched off (then GitHub is not asked at all).
     */
    static synchronized Release latestCached() {
        if (!autoCheck()) {
            return null;
        }
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
                .header("User-Agent", userAgent())
                .timeout(Duration.ofSeconds(15))
                .GET().build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() == 404) {
            throw new IOException("No public release found (is the repository public?)");
        }
        if (res.statusCode() != 200) {
            throw new IOException("GitHub answered " + res.statusCode());
        }
        return pick(Hub.JSON.readTree(res.body()), assetNames());
    }

    /** The first of {@code names} that the release has as an asset, with its checksum and signature file; or null. */
    static Release pick(JsonNode json, List<String> names) {
        String version = json.path("tag_name").asText("").replaceFirst("^v", "");
        for (String wanted : names) {
            JsonNode asset = null;
            String signatureUrl = null;
            for (JsonNode a : json.path("assets")) {
                String name = a.path("name").asText();
                if (name.equals(wanted)) {
                    asset = a;
                } else if (name.equals(wanted + ".sig")) {
                    signatureUrl = a.path("browser_download_url").asText(null);
                }
            }
            if (asset != null) {
                return new Release(version, asset.path("browser_download_url").asText(), wanted, asset.path("size").asLong(),
                        json.path("html_url").asText(), sha256Of(asset.path("digest").asText(null)), signatureUrl);
            }
        }
        return null;
    }

    /** The hex SHA-256 from a GitHub digest such as {@code sha256:1995…}; null for anything else. */
    static String sha256Of(String digest) {
        if (digest == null || !digest.regionMatches(true, 0, "sha256:", 0, 7)) {
            return null;
        }
        String hex = digest.substring(7).trim().toLowerCase(Locale.ROOT);
        return hex.matches("[0-9a-f]{64}") ? hex : null;
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

    /**
     * The installers that fit this computer, as named by the release workflow, newest naming first. Releases from 1.7.8
     * on carry LJ-Link-* plus a copy under the old name LJ-Link-PRO-*, because installs older than 1.7.8 only know the
     * old name. TODO(R-06): about three releases (and at least three months) after 1.7.8, drop the copy in
     * .github/workflows/build.yml; this fallback can stay a little longer, it costs nothing.
     */
    static List<String> assetNames() {
        return assetNames(System.getProperty("os.name", ""), System.getProperty("os.arch", ""));
    }

    static List<String> assetNames(String osName, String osArch) {
        String os = osName.toLowerCase(Locale.ROOT);
        String arch = osArch.toLowerCase(Locale.ROOT);
        String suffix = os.contains("win") ? "windows.exe"
                : arch.contains("aarch64") || arch.contains("arm") ? "macos-arm64.dmg" : "macos-intel.dmg";
        return List.of("LJ-Link-" + suffix, "LJ-Link-PRO-" + suffix);
    }

    /**
     * Downloads the installer into a temporary folder and checks it (checksum, and signature when a key is set);
     * {@code progress} gets values from 0 to 1. Throws {@link VerificationException} and deletes the file when the
     * download must not be installed.
     */
    static Path download(Release r, DoubleConsumer progress) throws IOException, InterruptedException {
        // Refuse before downloading what could never be installed.
        if (r.sha256() == null) {
            throw new VerificationException("GitHub gave no checksum for " + r.assetName() + ".");
        }
        if (signatureState(r) == SignatureState.MISSING) {
            throw new VerificationException("This release is not signed (yet).");
        }
        return download(r, progress, Path.of(System.getProperty("java.io.tmpdir")));
    }

    /**
     * {@link #download(Release, DoubleConsumer)} into a new folder {@code ljlinkpro-update*} below {@code tempRoot}. On
     * any failure or interruption the (partial) file and the folder are deleted again.
     */
    static Path download(Release r, DoubleConsumer progress, Path tempRoot) throws IOException, InterruptedException {
        Path dir = Files.createTempDirectory(tempRoot, "ljlinkpro-update");
        boolean done = false;
        try {
            Path file = dir.resolve(r.assetName());
            HttpRequest req = HttpRequest.newBuilder(URI.create(r.downloadUrl()))
                    .header("User-Agent", userAgent())
                    .GET().build();
            HttpResponse<InputStream> res = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (res.statusCode() != 200) {
                res.body().close();
                throw new IOException("Download failed: " + res.statusCode());
            }
            long total = res.headers().firstValueAsLong("Content-Length").orElse(r.size());
            MessageDigest sha = sha256();
            try (InputStream in = res.body(); OutputStream out = Files.newOutputStream(file)) {
                byte[] buf = new byte[64 * 1024];
                long loaded = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedException("Download cancelled");
                    }
                    out.write(buf, 0, n);
                    sha.update(buf, 0, n);
                    loaded += n;
                    if (total > 0) {
                        progress.accept(Math.min(1, loaded / (double) total));
                    }
                }
            }
            if (r.size() > 0 && Files.size(file) != r.size()) {
                throw new IOException("Download incomplete");
            }
            byte[] signature = signingConfigured() ? fetchSignature(r.signatureUrl()) : null;
            verify(r, sha.digest(), signature, SIGNING_PUBLIC_KEY);
            done = true;
            return file;
        } finally {
            if (!done) {
                deleteTree(dir);
            }
        }
    }

    /** Deletes a folder with its files; best effort. */
    static void deleteTree(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    p.toFile().deleteOnExit();
                }
            });
        } catch (IOException | RuntimeException e) {
            // nothing left to clean up, or the temp folder is cleaned by the system
        }
    }

    /**
     * Checks a downloaded installer: its SHA-256 must equal the one from GitHub, and when {@code publicKey} is set
     * {@code signature} must be a valid Ed25519 signature of that SHA-256 (32 raw bytes).
     */
    static void verify(Release r, byte[] sha256, byte[] signature, String publicKey) throws VerificationException {
        if (r.sha256() == null) {
            throw new VerificationException("GitHub gave no checksum for " + r.assetName() + ".");
        }
        if (!MessageDigest.isEqual(HexFormat.of().parseHex(r.sha256()), sha256)) {
            throw new VerificationException("The checksum of the download does not match the release. Nothing was installed.");
        }
        if (publicKey == null || publicKey.isBlank()) {
            return;
        }
        if (signature == null) {
            throw new VerificationException("This release is not signed (yet).");
        }
        if (!verifySignature(publicKey, sha256, signature)) {
            throw new VerificationException("The signature of the download is not valid. Nothing was installed.");
        }
    }

    /** True if {@code signature} (64 raw bytes or their Base64) is a valid Ed25519 signature of {@code message}. */
    static boolean verifySignature(String publicKeyBase64, byte[] message, byte[] signature) {
        try {
            PublicKey key = KeyFactory.getInstance("Ed25519")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64.trim())));
            byte[] sig = signature;
            if (sig.length != 64) {
                sig = Base64.getMimeDecoder().decode(new String(signature, StandardCharsets.US_ASCII).trim());
            }
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(key);
            verifier.update(message);
            return verifier.verify(sig);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] fetchSignature(String url) throws IOException, InterruptedException {
        if (url == null) {
            throw new VerificationException("This release is not signed (yet).");
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", userAgent())
                .timeout(Duration.ofSeconds(15))
                .GET().build();
        HttpResponse<InputStream> res = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (res.statusCode() != 200) {
            throw new VerificationException("The signature could not be downloaded (" + res.statusCode() + ").");
        }
        try (InputStream in = res.body()) {
            byte[] sig = in.readNBytes(4096);
            if (in.read() >= 0) {
                throw new VerificationException("The signature file is too large.");
            }
            return sig;
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
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
