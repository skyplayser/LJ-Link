package dev.ljlinkpro;

import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code /sender} on a real Jetty: handshake and the message size limits of the WebSocket itself. */
class SenderSocketTest {
    private Javalin app;
    private Hub hub;
    private int port;

    @BeforeEach
    void setUp() {
        hub = new Hub(WsClient.STALL_NANOS, false);
        SenderInbox inbox = new SenderInbox(hub, () -> SenderInboxTest.KEY, () -> "Testclub");
        app = Javalin.create(c -> c.showJavalinBanner = false);
        app.ws("/sender", ws -> SenderInbox.wire(ws, inbox));
        app.start("127.0.0.1", 0);
        port = app.port();
    }

    @AfterEach
    void tearDown() {
        app.stop();
    }

    /** Collects text messages and the close code of one client connection. */
    static final class Client implements WebSocket.Listener {
        final LinkedBlockingQueue<String> texts = new LinkedBlockingQueue<>();
        final CompletableFuture<Integer> closed = new CompletableFuture<>();
        private final StringBuilder part = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            part.append(data);
            if (last) {
                texts.add(part.toString());
                part.setLength(0);
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            closed.complete(statusCode);
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            closed.complete(-1);
        }
    }

    private WebSocket open(Client c) throws Exception {
        return HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create("ws://127.0.0.1:" + port + "/sender"), c).get(5, TimeUnit.SECONDS);
    }

    private Deck waitForDeck(java.util.function.Predicate<Deck> ok) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            for (Deck d : hub.allDecks()) {
                if (ok.test(d)) {
                    return d;
                }
            }
            Thread.sleep(50);
        }
        return null;
    }

    @Test
    void bigWaveformPassesAndTooBigMessageCloses() throws Exception {
        Client c = new Client();
        WebSocket ws = open(c);
        ws.sendText(SenderInboxTest.hello(SenderInboxTest.KEY, 1, "Anna", true), true).get(5, TimeUnit.SECONDS);
        String welcome = c.texts.poll(5, TimeUnit.SECONDS);
        assertNotNull(welcome);
        assertTrue(welcome.contains("\"welcome\""), welcome);

        ws.sendText("{\"type\":\"load\",\"deck\":1,\"id\":\"a\",\"lengthMs\":2000000}", true).get(5, TimeUnit.SECONDS);
        // 300,000 entries (33 minutes at 150 per second): 1.2 MB, 1.6 MB as base64, far over Jetty's default of 64 KB.
        int entries = 300_000;
        String data = Base64.getEncoder().encodeToString(new byte[entries * 4]);
        ws.sendText("{\"type\":\"waveform\",\"deck\":1,\"id\":\"a\",\"kind\":\"detail\",\"fps\":150,\"entries\":" + entries
                + ",\"data\":\"" + data + "\"}", true).get(5, TimeUnit.SECONDS);
        Deck d = waitForDeck(x -> x.detail != null);
        assertNotNull(d, "a 1.6 MB message is accepted after the welcome");
        assertEquals(entries * 4, d.detail.length);

        ws.sendText("{\"type\":\"ping\",\"pad\":\"" + "x".repeat(SenderInbox.MAX_MESSAGE_BYTES) + "\"}", true);
        assertEquals(SenderInbox.CLOSE_TOO_LARGE, c.closed.get(5, TimeUnit.SECONDS));
        for (int i = 0; i < 100 && !hub.allDecks().isEmpty(); i++) {
            Thread.sleep(50);
        }
        assertTrue(hub.allDecks().isEmpty(), "the sender's decks go with the connection");
    }

    @Test
    void largeMessageBeforeWelcomeCloses() throws Exception {
        Client c = new Client();
        WebSocket ws = open(c);
        ws.sendText("{\"type\":\"hello\",\"pad\":\"" + "x".repeat(100_000) + "\"}", true);
        assertEquals(SenderInbox.CLOSE_TOO_LARGE, c.closed.get(5, TimeUnit.SECONDS));
    }

    @Test
    void wrongKeyGetsRejectBeforeTheClose() throws Exception {
        Client c = new Client();
        WebSocket ws = open(c);
        ws.sendText(SenderInboxTest.hello("ffeeddccbbaa99887766554433221100", 1, "Anna", true), true);
        assertEquals(SenderInbox.CLOSE_POLICY, c.closed.get(5, TimeUnit.SECONDS));
        assertEquals("{\"type\":\"reject\",\"reason\":\"key\"}", c.texts.poll(1, TimeUnit.SECONDS));
    }
}
