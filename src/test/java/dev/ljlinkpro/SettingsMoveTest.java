package dev.ljlinkpro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * QS round 2, R2-1: another web page sent the browser to {@code http://127.0.0.1:PORT/#import-settings=…} and planted
 * settings (hidden decks, screen may sleep). Now 127.0.0.1 imports only after the server accepted the one-time token
 * LJ Link put into the address it opened. Port 8097 only.
 */
class SettingsMoveTest {
    private static final int PORT = 8097;
    private static final String SELF = "http://127.0.0.1:" + PORT;
    private final HttpClient http = HttpClient.newHttpClient();

    @AfterEach
    void reset() {
        SettingsMove.reset();
    }

    private HttpResponse<String> post(String origin, String contentType, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(SELF + "/api/settings-move"))
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (origin != null) {
            b.header("Origin", origin);
        }
        if (contentType != null) {
            b.header("Content-Type", contentType);
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static boolean field(String json, String name) {
        Matcher m = Pattern.compile("\"" + name + "\"\\s*:\\s*(true|false)").matcher(json);
        assertTrue(m.find(), json);
        return Boolean.parseBoolean(m.group(1));
    }

    @Test
    void theTokenWorksOnceAndOnlyForTheOwnPage() throws Exception {
        AtomicBoolean done = new AtomicBoolean();
        Javalin app = Javalin.create(c -> c.showJavalinBanner = false);
        ControlGuard.install(app);
        SettingsMove.install(app, done::get, () -> done.set(true));
        app.start(PORT);
        try {
            String token = SettingsMove.issue(System.currentTimeMillis());
            assertTrue(token.matches("[0-9a-f]{32}"), token);

            // The attack of R2-1: a page sends the browser to 127.0.0.1 with made-up data and no or a guessed token.
            HttpResponse<String> none = post(SELF, "application/json", "{\"token\":\"\"}");
            assertEquals(200, none.statusCode());
            assertFalse(field(none.body(), "ok"), "no token: nothing is imported");
            assertFalse(field(none.body(), "done"), "the real move is still outstanding");
            assertFalse(field(post(SELF, "application/json", "{\"token\":\"00000000000000000000000000000000\"}").body(), "ok"));
            // A foreign page cannot redeem it itself (it would not know the token anyway): wrong origin, no JSON.
            assertEquals(403, post("http://evil.example", "application/json", "{\"token\":\"" + token + "\"}").statusCode());
            assertEquals(415, post(SELF, "text/plain", "{\"token\":\"" + token + "\"}").statusCode());
            assertFalse(done.get());

            // LJ Link's own page: once.
            HttpResponse<String> own = post(SELF, "application/json", "{\"token\":\"" + token + "\"}");
            assertTrue(field(own.body(), "ok"));
            assertTrue(field(own.body(), "done"), "after the move the page sets its marker");
            assertTrue(done.get());
            assertFalse(field(post(SELF, "application/json", "{\"token\":\"" + token + "\"}").body(), "ok"), "only once");
        } finally {
            app.stop();
        }
    }

    @Test
    void theTokenExpires() {
        String token = SettingsMove.issue(1_000_000);
        assertFalse(SettingsMove.redeem(token, 1_000_000 + SettingsMove.VALID_MS + 1));
        String next = SettingsMove.issue(2_000_000);
        assertFalse(SettingsMove.redeem(token, 2_000_001), "an older token is replaced");
        assertFalse(SettingsMove.redeem(null, 2_000_001));
        assertTrue(SettingsMove.redeem(next, 2_000_001));
    }

    @Test
    void theImportTakesOnlyKnownSettings() throws Exception {
        String js = KeepAwakeWebTest.resource("settings-move.js");
        assertFalse(js.contains("Object.assign"), "no merge of foreign data");
        assertTrue(js.contains("if (res.ok === true)"), "imports only after the server accepted the token");
        assertTrue(js.contains("localStorage.setItem(MARK, \"1\")"), "a marker stops later imports");
        // Allow-list with value checks (zoom range, fixed words, booleans), keys looked up with hasOwnProperty.
        for (String check : new String[] {"s.zoom >= 4 && s.zoom <= 40", "s.mode === \"rgb\" || s.mode === \"blue\"",
                "typeof s.keepAwake === \"boolean\"", "Object.prototype.hasOwnProperty.call(o, k)"}) {
            assertTrue(js.contains(check), check);
        }
        String app = KeepAwakeWebTest.resource("app.js");
        assertFalse(app.contains("Object.assign(settings"), "app.js copies only the known settings from storage");
    }

    @Test
    void atMostTwoStartsTryTheMove() {
        // QS round 3: with another program on [::1] the move never arrives; it must not open that program forever.
        boolean done = false;
        int attempts = 0;
        int movesOpened = 0;
        for (int start = 0; start < 5; start++) {
            if (!done && !SettingsMove.tryMove(false, attempts)) {
                done = true; // Main sets browserSettingsMoved
            }
            if (SettingsMove.tryMove(done, attempts)) {
                attempts++;
                movesOpened++;
            }
        }
        assertEquals(2, movesOpened);
        assertTrue(done, "then LJ Link opens 127.0.0.1 directly");
        assertFalse(SettingsMove.tryMove(true, 0), "a finished move is never tried again");
    }

    @Test
    void theLogNeverShowsTheToken() {
        assertEquals("http://127.0.0.1:8097/", Main.forLog("http://localhost:8097/#move-settings=0123456789abcdef0123456789abcdef"));
        assertEquals("http://127.0.0.1:8097", Main.forLog("http://127.0.0.1:8097"));
        assertEquals("http://127.0.0.1:8097/", Main.forLog("http://127.0.0.1:8097/#report"));
    }
}
