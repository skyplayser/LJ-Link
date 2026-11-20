package dev.ljlinkpro;

import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The routes of the approval on a real Jetty behind {@link ControlGuard}: a tablet or another web page neither sees the
 * requests, names or addresses nor allows, denies, removes or makes a new key.
 */
class SenderPairingRoutesTest {
    private static final String JSON = "application/json";
    private Javalin app;
    private int port;
    private SenderInbox inbox;
    private String previousHome;
    private final AtomicInteger announced = new AtomicInteger();

    @BeforeEach
    void setUp() throws IOException {
        previousHome = System.getProperty("ljlinkpro.home");
        System.setProperty("ljlinkpro.home", Files.createTempDirectory("ljlinkpro-pairing-routes").toString());
        inbox = new SenderInbox(new Hub(WsClient.STALL_NANOS, false), SenderClub::key, () -> "Testclub", l -> { },
                SenderPairing.inMemory(System::currentTimeMillis));
        app = Javalin.create(c -> c.showJavalinBanner = false);
        ControlGuard.install(app);
        SenderPairing.routes(app, inbox, announced::incrementAndGet);
        app.start("127.0.0.1", 0);
        port = app.port();
    }

    @AfterEach
    void tearDown() {
        app.stop();
        if (previousHome == null) {
            System.clearProperty("ljlinkpro.home");
        } else {
            System.setProperty("ljlinkpro.home", previousHome);
        }
    }

    /** Status line and body of a raw request (the Host header can be set, unlike with Java's HTTP client). */
    private String[] send(String method, String path, String host, String origin, String body) throws IOException {
        try (Socket s = new Socket("127.0.0.1", port)) {
            StringBuilder req = new StringBuilder(method + " " + path + " HTTP/1.1\r\nHost: " + host + "\r\nConnection: close\r\n");
            if (origin != null) {
                req.append("Origin: ").append(origin).append("\r\n");
            }
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            req.append("Content-Type: ").append(JSON).append("\r\nContent-Length: ").append(b.length).append("\r\n\r\n");
            OutputStream out = s.getOutputStream();
            out.write(req.toString().getBytes(StandardCharsets.US_ASCII));
            out.write(b);
            out.flush();
            String all = new String(s.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int split = all.indexOf("\r\n\r\n");
            return new String[] {all.substring(9, 12), split < 0 ? "" : all.substring(split + 4)};
        }
    }

    private void waitingRequest() {
        SenderInboxTest.FakePeer p = new SenderInboxTest.FakePeer();
        inbox.open("a", p, SenderPairingTest.IP, System.nanoTime());
        inbox.message("a", SenderPairingTest.hello(null, SenderPairingTest.DEV, SenderPairingTest.ANNA), System.nanoTime());
    }

    @Test
    void newRoutesAreLocal() {
        for (String path : new String[] {"/api/sender/pairing", "/api/sender/allow", "/api/sender/deny",
                "/api/sender/approved/remove", "/api/sender/key"}) {
            assertTrue(ControlGuard.isGuarded(path), path);
        }
    }

    @Test
    void tabletsAndOtherPagesGetNothing() throws IOException {
        waitingRequest();
        String self = "localhost:" + port;
        String[] tablet = {"192.168.1.10:" + port, "evil.example:" + port};
        for (String host : tablet) {
            String[] r = send("GET", "/api/sender/pairing", host, null, "");
            assertEquals("403", r[0], host);
            assertFalse(r[1].contains("Anna"), r[1]);
            assertFalse(r[1].contains(SenderPairingTest.IP), r[1]);
            assertEquals("403", send("POST", "/api/sender/allow", host, null, "{\"id\":\"r1\"}")[0]);
            assertEquals("403", send("POST", "/api/sender/deny", host, null, "{\"id\":\"r1\"}")[0]);
            assertEquals("403", send("POST", "/api/sender/approved/remove", host, null, "{\"id\":\"m1\"}")[0]);
            assertEquals("403", send("POST", "/api/sender/key", host, null, "{}")[0]);
        }
        assertEquals("403", send("POST", "/api/sender/allow", self, "http://evil.example", "{\"id\":\"r1\"}")[0], "CSRF");
        assertEquals("403", send("GET", "/api/sender/pairing", self, "http://evil.example", "")[0]);
        assertEquals(1, ((java.util.List<?>) inbox.pairingState(System.nanoTime()).get("requests")).size(),
                "the request is still waiting");
        assertEquals(0, announced.get());
    }

    @Test
    void thisComputerSeesAndAnswersTheRequest() throws IOException {
        waitingRequest();
        String self = "localhost:" + port;
        String[] state = send("GET", "/api/sender/pairing", self, null, "");
        assertEquals("200", state[0]);
        assertTrue(state[1].contains("Anna") && state[1].contains(SenderPairingTest.IP), state[1]);
        assertFalse(state[1].contains(SenderPairingTest.DEV), "no pseudonym, also not locally");
        String[] allowed = send("POST", "/api/sender/allow", self, "http://" + self, "{\"id\":\"r1\"}");
        assertEquals("200", allowed[0]);
        assertTrue(allowed[1].contains("welcomed"), allowed[1]);
        assertEquals("404", send("POST", "/api/sender/allow", self, null, "{\"id\":\"r1\"}")[0], "already answered");
        assertEquals("404", send("POST", "/api/sender/deny", self, null, "{\"id\":\"r1\"}")[0]);
        assertEquals("204", send("POST", "/api/sender/approved/remove", self, null, "{\"id\":\"m1\"}")[0]);
        assertEquals("404", send("POST", "/api/sender/approved/remove", self, null, "{\"id\":\"m1\"}")[0]);
    }

    @Test
    void newKeyRenewsKeyAndBoothEmptiesTheListAndAnnounces() throws IOException {
        String key = SenderClub.key();
        String booth = SenderClub.booth();
        inbox.pairing().approve(SenderPairingTest.DEV, SenderPairingTest.ANNA);
        String[] r = send("POST", "/api/sender/key", "localhost:" + port, null, "{}");
        assertEquals("200", r[0]);
        assertNotEquals(key, SenderClub.key());
        assertTrue(r[1].contains(SenderClub.key()));
        assertNotEquals(booth, SenderClub.booth());
        assertTrue(SenderClub.booth().matches("[0-9a-f]{16}"));
        assertEquals(0, inbox.pairing().size());
        assertEquals(1, announced.get(), "Bonjour announces the new booth id");
    }
}
