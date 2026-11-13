package dev.ljlinkpro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.concurrent.atomic.AtomicInteger;

import io.javalin.Javalin;
import org.junit.jupiter.api.Test;

/**
 * Issue #3: with the first port in use LJ Link quit, because a Javalin instance cannot be started a second time. Uses
 * ports 8097 and 8098 only (never 8080, where the developer's LJ Link runs).
 */
class PortFallbackTest {
    private static final int PORT = 8097;

    private static String ping(int port) throws Exception {
        var c = (java.net.HttpURLConnection) java.net.URI.create("http://127.0.0.1:" + port + "/api/ping").toURL().openConnection();
        c.setConnectTimeout(1000);
        c.setReadTimeout(1000);
        try (var in = c.getInputStream()) {
            return new String(in.readAllBytes());
        }
    }

    private static Javalin app(AtomicInteger made) {
        made.incrementAndGet();
        return Javalin.create(config -> config.showJavalinBanner = false).get("/api/ping", ctx -> ctx.result("ljlinkpro"));
    }

    @Test
    void aTakenPortIsSeenBeforeJettyTriesIt() throws Exception {
        try (ServerSocket other = new ServerSocket()) {
            other.bind(new InetSocketAddress(PORT));
            assertTrue(Main.portTaken(PORT), "another program on all addresses");
        }
        try (ServerSocket other = new ServerSocket()) {
            // A program that bound only 127.0.0.1: on some systems Jetty's bind to all addresses would still succeed.
            other.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), PORT));
            assertTrue(Main.portTaken(PORT), "another program on 127.0.0.1 only");
        }
        assertFalse(Main.portTaken(PORT), "free again");
    }

    @Test
    void theNextPortGetsANewInstanceWhenJettyCannotBind() throws Exception {
        AtomicInteger made = new AtomicInteger();
        try (ServerSocket other = new ServerSocket()) {
            other.bind(new InetSocketAddress(PORT));
            // Skip the check, so Jetty itself fails on 8097 and the fallback must build a fresh instance for 8098.
            Main.Started s = Main.start(PORT, () -> app(made), p -> false);
            try {
                assertEquals(PORT + 1, s.port());
                assertEquals(2, made.get(), "one instance per port tried");
                assertEquals("ljlinkpro", ping(PORT + 1));
            } finally {
                s.app().stop();
            }
        }
    }

    @Test
    void aTakenPortIsSkippedWithoutBuildingAnInstance() throws Exception {
        AtomicInteger made = new AtomicInteger();
        try (ServerSocket other = new ServerSocket()) {
            other.bind(new InetSocketAddress(PORT));
            Main.Started s = Main.start(PORT, () -> app(made), Main::portTaken);
            try {
                assertEquals(PORT + 1, s.port());
                assertEquals(1, made.get());
                assertEquals("ljlinkpro", ping(PORT + 1));
            } finally {
                s.app().stop();
            }
        }
    }

    @Test
    void theBrowserGets127001NotLocalhost() {
        // Browsers try "localhost" as [::1] first; LJ Link (IPv4 only) cannot bind or check [::1] (QS round 2).
        assertEquals("http://127.0.0.1:8097", Main.localUrl(8097));
        assertEquals("http://localhost:8097/#move-settings=0123456789abcdef0123456789abcdef",
                Main.moveUrl(8097, "0123456789abcdef0123456789abcdef"), "once, to hand the settings over");
    }

    @Test
    void allPortsTakenIsExplained() {
        String en = Main.noFreePortText(8080, 8089, false);
        String de = Main.noFreePortText(8080, 8089, true);
        assertTrue(en.contains("8080 to 8089") && en.contains("--port"), en);
        assertTrue(de.contains("8080 bis 8089") && de.contains("--port"), de);
    }

    @Test
    void settingsMoveRunsBeforeTheSettingsAreRead() throws Exception {
        String html = KeepAwakeWebTest.resource("index.html");
        int move = html.indexOf("<script src=\"settings-move.js\">");
        assertTrue(move >= 0 && move < html.indexOf("<script src=\"i18n.js\">") && move < html.indexOf("<script src=\"app.js\">"));
        String js = KeepAwakeWebTest.resource("settings-move.js");
        assertTrue(js.contains("location.hostname === \"localhost\"") && js.contains("location.hostname !== \"127.0.0.1\""));
        assertTrue(js.indexOf("history.replaceState") < js.indexOf("fetch(\"/api/settings-move\""), "the hash goes first");
        assertTrue(js.contains("if (localStorage.getItem(k) === null)"), "settings already on 127.0.0.1 are kept");
    }
}
