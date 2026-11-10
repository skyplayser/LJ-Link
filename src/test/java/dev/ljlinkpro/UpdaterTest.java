package dev.ljlinkpro;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdaterTest {
    private static String previousHome;

    @BeforeAll
    static void useTemporaryHome() throws Exception {
        previousHome = System.getProperty("ljlinkpro.home");
        System.setProperty("ljlinkpro.home", Files.createTempDirectory("ljlinkpro-updater-test").toString());
    }

    @AfterAll
    static void restoreHome() {
        if (previousHome == null) {
            System.clearProperty("ljlinkpro.home");
        } else {
            System.setProperty("ljlinkpro.home", previousHome);
        }
    }

    @Test
    void versionsAreComparedNumerically() {
        assertTrue(Updater.isNewer("1.2.0", "1.1.1"));
        assertTrue(Updater.isNewer("1.10.0", "1.9.3"), "10 is more than 9, not less as text");
        assertTrue(Updater.isNewer("2.0", "1.9.9"));
        assertFalse(Updater.isNewer("1.1.1", "1.1.1"));
        assertFalse(Updater.isNewer("1.1.0", "1.1.1"));
        assertFalse(Updater.isNewer("1.2.0", "dev"), "development builds are never updated");
    }

    @Test
    void theInstallerFitsTheComputerNewNameFirst() {
        assertEquals(List.of("LJ-Link-macos-arm64.dmg", "LJ-Link-PRO-macos-arm64.dmg"), Updater.assetNames("Mac OS X", "aarch64"));
        assertEquals(List.of("LJ-Link-macos-intel.dmg", "LJ-Link-PRO-macos-intel.dmg"), Updater.assetNames("Mac OS X", "x86_64"));
        assertEquals(List.of("LJ-Link-windows.exe", "LJ-Link-PRO-windows.exe"), Updater.assetNames("Windows 11", "amd64"));
        List<String> here = Updater.assetNames();
        assertTrue(here.get(0).startsWith("LJ-Link-") && !here.get(0).startsWith("LJ-Link-PRO-"));
    }

    @Test
    void userAgentHasNoOldName() {
        assertTrue(Updater.userAgent().startsWith("LJ-Link/"));
        assertFalse(Updater.userAgent().contains("PRO"));
    }

    private static final String SUM = "199594ca2eb8a5690b857eaf388ea64ced6024d27d410f1348258a30597d036c";

    private static JsonNode release(String... assetNames) throws Exception {
        StringBuilder assets = new StringBuilder();
        for (String n : assetNames) {
            if (assets.length() > 0) {
                assets.append(',');
            }
            assets.append("{\"name\":\"").append(n).append("\",\"size\":10,\"digest\":\"sha256:").append(SUM)
                    .append("\",\"browser_download_url\":\"https://example.invalid/").append(n).append("\"}");
        }
        return Hub.JSON.readTree("{\"tag_name\":\"v1.7.8\",\"html_url\":\"https://example.invalid/r\",\"assets\":[" + assets + "]}");
    }

    @Test
    void newNameIsPreferredAndOldNameIsTheFallback() throws Exception {
        List<String> names = Updater.assetNames("Mac OS X", "aarch64");
        Updater.Release both = Updater.pick(release("LJ-Link-PRO-macos-arm64.dmg", "LJ-Link-macos-arm64.dmg",
                "LJ-Link-macos-arm64.dmg.sig"), names);
        assertEquals("LJ-Link-macos-arm64.dmg", both.assetName());
        assertEquals("1.7.8", both.version());
        assertEquals(SUM, both.sha256());
        assertEquals("https://example.invalid/LJ-Link-macos-arm64.dmg.sig", both.signatureUrl());

        Updater.Release old = Updater.pick(release("LJ-Link-PRO-macos-arm64.dmg"), names);
        assertEquals("LJ-Link-PRO-macos-arm64.dmg", old.assetName());
        assertNull(old.signatureUrl());

        assertNull(Updater.pick(release("LJ-Link-windows.exe"), names));
    }

    @Test
    void digestsAreParsedStrictly() {
        assertEquals(SUM, Updater.sha256Of("sha256:" + SUM));
        assertEquals(SUM, Updater.sha256Of("SHA256:" + SUM.toUpperCase()));
        assertNull(Updater.sha256Of(null));
        assertNull(Updater.sha256Of("sha512:" + SUM));
        assertNull(Updater.sha256Of("sha256:1234"));
    }

    private static Updater.Release releaseFor(byte[] content, String sigUrl) throws Exception {
        String hex = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        return new Updater.Release("1.7.8", "https://example.invalid/x", "LJ-Link-macos-arm64.dmg", content.length,
                "https://example.invalid/r", hex, sigUrl);
    }

    @Test
    void checksumMustMatchTheRelease() throws Exception {
        byte[] content = "installer".getBytes(StandardCharsets.UTF_8);
        byte[] sha = MessageDigest.getInstance("SHA-256").digest(content);
        Updater.Release r = releaseFor(content, null);
        assertDoesNotThrow(() -> Updater.verify(r, sha, null, ""), "without a key only the checksum counts");

        byte[] tampered = MessageDigest.getInstance("SHA-256").digest("evil".getBytes(StandardCharsets.UTF_8));
        assertThrows(Updater.VerificationException.class, () -> Updater.verify(r, tampered, null, ""));

        Updater.Release noDigest = new Updater.Release("1.7.8", "u", "n", 1, "p", null, null);
        assertThrows(Updater.VerificationException.class, () -> Updater.verify(noDigest, sha, null, ""));
    }

    @Test
    void signatureIsCheckedWhenAKeyIsSet() throws Exception {
        // A throwaway key pair, only for this test.
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String publicKey = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        assertTrue(publicKey.startsWith("MCowBQYDK2VwAyEA"), "X.509 form, as scripts/sign-release.sh prints it");

        byte[] content = "installer".getBytes(StandardCharsets.UTF_8);
        byte[] sha = MessageDigest.getInstance("SHA-256").digest(content);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(pair.getPrivate());
        signer.update(sha);
        byte[] sig = signer.sign();
        Updater.Release r = releaseFor(content, "https://example.invalid/x.sig");

        assertDoesNotThrow(() -> Updater.verify(r, sha, sig, publicKey));
        byte[] base64Sig = Base64.getEncoder().encode(sig);
        assertDoesNotThrow(() -> Updater.verify(r, sha, base64Sig, publicKey), "a Base64 .sig is accepted too");

        assertThrows(Updater.VerificationException.class, () -> Updater.verify(r, sha, null, publicKey), "missing signature");
        byte[] broken = sig.clone();
        broken[3] ^= 1;
        assertThrows(Updater.VerificationException.class, () -> Updater.verify(r, sha, broken, publicKey), "wrong signature");

        KeyPair other = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String otherKey = Base64.getEncoder().encodeToString(other.getPublic().getEncoded());
        assertThrows(Updater.VerificationException.class, () -> Updater.verify(r, sha, sig, otherKey), "signed with another key");
        assertFalse(Updater.verifySignature("not a key", sha, sig));
    }

    @Test
    void placeholderKeyMeansNotSigned() throws Exception {
        // The shipped key is a placeholder until the developer creates the real key pair (see Updater.SIGNING_PUBLIC_KEY).
        Updater.Release r = releaseFor(new byte[] {1}, "https://example.invalid/x.sig");
        if (Updater.signingConfigured()) {
            assertEquals(Updater.SignatureState.PRESENT, Updater.signatureState(r));
            assertEquals(Updater.SignatureState.MISSING, Updater.signatureState(releaseFor(new byte[] {1}, null)));
        } else {
            assertEquals(Updater.SignatureState.NOT_CONFIGURED, Updater.signatureState(r));
        }
    }

    @Test
    void noRequestToGitHubWhenAutomaticChecksAreOff() {
        try {
            AppSettings.set(Updater.AUTO_CHECK_KEY, false);
            assertFalse(Updater.autoCheck());
            assertNull(Updater.latestCached(), "switched off: no release is asked for");
        } finally {
            AppSettings.set(Updater.AUTO_CHECK_KEY, true);
        }
        assertTrue(Updater.autoCheck());
    }

    @Test
    void automaticChecksAreOnByDefault() throws Exception {
        Path settings = Path.of(System.getProperty("ljlinkpro.home"), "settings.json");
        String before = Files.exists(settings) ? Files.readString(settings) : null;
        try {
            Files.deleteIfExists(settings);
            assertTrue(Updater.autoCheck());
        } finally {
            if (before != null) {
                Files.writeString(settings, before);
            }
        }
    }

    // ---------------------------------------------------------------- download cleanup with a local server

    private static long entries(Path dir) throws Exception {
        try (var st = Files.list(dir)) {
            return st.count();
        }
    }

    @Test
    void failedDownloadsLeaveNothingBehind() throws Exception {
        byte[] content = "the installer".getBytes(StandardCharsets.UTF_8);
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok", ex -> {
            ex.sendResponseHeaders(200, content.length);
            ex.getResponseBody().write(content);
            ex.close();
        });
        server.createContext("/missing", ex -> {
            ex.sendResponseHeaders(500, -1);
            ex.close();
        });
        server.createContext("/cut", ex -> {
            ex.sendResponseHeaders(200, 100_000); // promises more than it sends
            ex.getResponseBody().write(content);
            ex.getResponseBody().flush();
            ex.close();
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        Path root = Files.createTempDirectory("ljlinkpro-download-test");
        try {
            String hex = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
            String wrong = "0".repeat(64);
            java.util.function.BiFunction<String, String, Updater.Release> rel = (path, sum) -> new Updater.Release("9.9.9",
                    base + path, "LJ-Link-macos-arm64.dmg", 0, "page", sum, null);

            assertThrows(Updater.VerificationException.class, () -> Updater.download(rel.apply("/ok", wrong), p -> {}, root));
            assertEquals(0, entries(root), "wrong checksum: file and folder deleted");

            assertThrows(java.io.IOException.class, () -> Updater.download(rel.apply("/missing", hex), p -> {}, root));
            assertEquals(0, entries(root), "HTTP error: folder deleted");

            assertThrows(java.io.IOException.class, () -> Updater.download(rel.apply("/cut", hex), p -> {}, root));
            assertEquals(0, entries(root), "connection lost during the download: partial file deleted");

            if (!Updater.signingConfigured()) {
                Path ok = Updater.download(rel.apply("/ok", hex), p -> {}, root);
                assertTrue(Files.exists(ok));
                Updater.deleteTree(ok.getParent());
                assertEquals(0, entries(root));
            }
        } finally {
            server.stop(0);
        }
    }
}
