package dev.ljlinkpro;

import io.javalin.Javalin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.ljlinkpro.ControlGuard.Verdict.FORBIDDEN;
import static dev.ljlinkpro.ControlGuard.Verdict.OK;
import static dev.ljlinkpro.ControlGuard.Verdict.UNSUPPORTED_MEDIA_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The local API only answers the browser on this computer, not other web pages (CSRF, DNS rebinding) or tablets. */
class ControlGuardTest {
    private static final String JSON = "application/json";

    @Test
    void ownPageOnThisComputerIsAllowed() {
        assertEquals(OK, ControlGuard.check("POST", "127.0.0.1", "localhost:8080", "http://localhost:8080", JSON, 8080));
        assertEquals(OK, ControlGuard.check("POST", "127.0.0.1", "127.0.0.1:8080", null, JSON + "; charset=UTF-8", 8080));
        assertEquals(OK, ControlGuard.check("POST", "0:0:0:0:0:0:0:1", "[::1]:8080", "http://[::1]:8080", JSON, 8080));
        assertEquals(OK, ControlGuard.check("GET", "127.0.0.1", "LOCALHOST:8080", null, null, 8080));
        assertEquals(OK, ControlGuard.check("GET", "127.0.0.1", "localhost", null, null, 8080));
    }

    @Test
    void foreignHostIsRefused() {
        // DNS rebinding: a foreign name that resolves to 127.0.0.1 still sends its own name.
        assertEquals(FORBIDDEN, ControlGuard.check("GET", "127.0.0.1", "evil.example:8080", null, null, 8080));
        assertEquals(FORBIDDEN, ControlGuard.check("GET", "127.0.0.1", "localhost.evil.example", null, null, 8080));
        assertEquals(FORBIDDEN, ControlGuard.check("GET", "127.0.0.1", "localhost:9999", null, null, 8080));
        assertEquals(FORBIDDEN, ControlGuard.check("GET", "127.0.0.1", null, null, null, 8080));
        assertEquals(FORBIDDEN, ControlGuard.check("GET", "127.0.0.1", "", null, null, 8080));
    }

    @Test
    void foreignOriginIsRefused() {
        // CSRF: another page (also a local file or another port) always sends its own origin.
        assertEquals(FORBIDDEN, ControlGuard.check("POST", "127.0.0.1", "localhost:8080", "https://evil.example", JSON, 8080));
        assertEquals(FORBIDDEN, ControlGuard.check("POST", "127.0.0.1", "localhost:8080", "http://localhost:3000", JSON, 8080));
        assertEquals(FORBIDDEN, ControlGuard.check("POST", "127.0.0.1", "localhost:8080", "null", JSON, 8080));
        assertEquals(FORBIDDEN, ControlGuard.check("POST", "127.0.0.1", "localhost:8080", "http://127.0.0.1:8080", JSON, 8080),
                "origin must match the host the request went to");
    }

    @Test
    void foreignFetchSiteIsRefusedAlsoForGets() {
        // <img src="http://localhost:8080/api/check?ip=..."> on another page: no Origin, but Sec-Fetch-Site cross-site.
        assertEquals(FORBIDDEN, ControlGuard.check("GET", "127.0.0.1", "localhost:8080", null, "cross-site", null, 8080));
        assertEquals(FORBIDDEN, ControlGuard.check("GET", "127.0.0.1", "localhost:8080", null, "same-site", null, 8080),
                "another port on localhost is same-site, not same-origin");
        assertEquals(OK, ControlGuard.check("GET", "127.0.0.1", "localhost:8080", null, "same-origin", null, 8080));
        assertEquals(OK, ControlGuard.check("GET", "127.0.0.1", "localhost:8080", null, "none", null, 8080));
        assertEquals(OK, ControlGuard.check("GET", "127.0.0.1", "localhost:8080", null, null, null, 8080), "older browsers, curl");
        assertEquals(OK, ControlGuard.check("POST", "127.0.0.1", "localhost:8080", "http://localhost:8080", "same-origin", JSON, 8080));
    }

    @Test
    void postWithoutJsonIsRefused() {
        assertEquals(UNSUPPORTED_MEDIA_TYPE, ControlGuard.check("POST", "127.0.0.1", "localhost:8080", null, null, 8080));
        assertEquals(UNSUPPORTED_MEDIA_TYPE, ControlGuard.check("POST", "127.0.0.1", "localhost:8080", null, "text/plain", 8080));
        assertEquals(UNSUPPORTED_MEDIA_TYPE,
                ControlGuard.check("POST", "127.0.0.1", "localhost:8080", null, "application/x-www-form-urlencoded", 8080));
    }

    @Test
    void tabletsCannotControl() {
        assertEquals(FORBIDDEN, ControlGuard.check("POST", "192.168.1.20", "localhost:8080", null, JSON, 8080));
        assertEquals(FORBIDDEN, ControlGuard.check("GET", "192.168.1.20", "192.168.1.10:8080", null, null, 8080));
        assertFalse(ControlGuard.isController("192.168.1.20", "192.168.1.10:8080", null, 8080));
        assertTrue(ControlGuard.isController("127.0.0.1", "localhost:8080", null, 8080));
    }

    @Test
    void readOnlyRoutesForTabletsStayOpen() {
        for (String open : new String[] {"/api/ping", "/api/version", "/api/info", "/api/deck/1/waveform", "/api/deck/2/info",
                "/ws", "/vjlink", "/", "/index.html", "/legal/index.html"}) {
            assertFalse(ControlGuard.isGuarded(open), open);
        }
        for (String local : new String[] {"/api/link", "/api/link/scan", "/api/link/test", "/api/diagnostics", "/api/check",
                "/api/ableton", "/api/stop", "/api/language", "/api/updates", "/api/quit-note", "/api/some-new-route"}) {
            assertTrue(ControlGuard.isGuarded(local), local);
        }
    }

    // ---------------------------------------------------------------- the guard in a real server

    private static Javalin app;
    private static int port;
    private static final AtomicInteger RAN = new AtomicInteger();

    @BeforeAll
    static void startServer() {
        app = Javalin.create(c -> c.showJavalinBanner = false);
        ControlGuard.install(app);
        app.post("/api/link/test", ctx -> {
            RAN.incrementAndGet();
            ctx.status(204);
        });
        app.get("/api/version", ctx -> ctx.result("open"));
        app.get("/api/check", ctx -> {
            RAN.incrementAndGet();
            ctx.result("checked");
        });
        app.start(0);
        port = app.port();
    }

    @AfterAll
    static void stopServer() {
        app.stop();
    }

    /** Sends a raw request (Java's HTTP client does not let a test set the Host header) and returns the status code. */
    private static int send(String method, String path, String host, String origin, String contentType) throws IOException {
        return send(method, path, host, origin, null, contentType);
    }

    private static int send(String method, String path, String host, String origin, String fetchSite, String contentType)
            throws IOException {
        try (Socket s = new Socket("127.0.0.1", port)) {
            StringBuilder req = new StringBuilder(method + " " + path + " HTTP/1.1\r\nHost: " + host + "\r\nConnection: close\r\n");
            if (fetchSite != null) {
                req.append("Sec-Fetch-Site: ").append(fetchSite).append("\r\n");
            }
            if (origin != null) {
                req.append("Origin: ").append(origin).append("\r\n");
            }
            if (contentType != null) {
                req.append("Content-Type: ").append(contentType).append("\r\n");
            }
            req.append("Content-Length: 2\r\n\r\n{}");
            OutputStream out = s.getOutputStream();
            out.write(req.toString().getBytes(StandardCharsets.US_ASCII));
            out.flush();
            InputStream in = s.getInputStream();
            String status = new String(in.readNBytes(12), StandardCharsets.US_ASCII);
            return Integer.parseInt(status.substring(9, 12));
        }
    }

    @Test
    void serverRefusesBeforeTheRouteRuns() throws IOException {
        int before = RAN.get();
        String self = "localhost:" + port;
        assertEquals(403, send("POST", "/api/link/test", "evil.example:" + port, null, JSON));
        assertEquals(403, send("POST", "/api/link/test", self, "http://evil.example", JSON));
        assertEquals(415, send("POST", "/api/link/test", self, null, "text/plain"));
        assertEquals(415, send("POST", "/api/link/test", self, null, null));
        assertEquals(before, RAN.get(), "the action never ran");

        assertEquals(204, send("POST", "/api/link/test", self, "http://" + self, JSON));
        assertEquals(before + 1, RAN.get());
        assertEquals(200, send("GET", "/api/version", "evil.example", null, null), "read-only routes stay open");
        assertEquals(200, send("GET", "/api/version", "evil.example", null, "cross-site", null), "also from other pages");

        int beforeCheck = RAN.get();
        assertEquals(403, send("GET", "/api/check?ip=example.org", self, null, "cross-site", null), "<img> from another page");
        assertEquals(403, send("GET", "/api/check?ip=example.org", self, null, "same-site", null));
        assertEquals(beforeCheck, RAN.get(), "the check never ran");
        assertEquals(200, send("GET", "/api/check?ip=example.org", self, null, "same-origin", null));
    }
}
