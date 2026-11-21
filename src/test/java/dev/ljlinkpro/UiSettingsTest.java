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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Display settings on the LJ Link computer (ADR-045): defaults, validation, restart, and who may change them. */
class UiSettingsTest {
    private String previousHome;
    private Javalin app;

    @BeforeEach
    void home() throws IOException {
        previousHome = System.getProperty("ljlinkpro.home");
        System.setProperty("ljlinkpro.home", Files.createTempDirectory("ljlinkpro-ui-settings").toString());
    }

    @AfterEach
    void restore() {
        if (app != null) {
            app.stop();
        }
        if (previousHome == null) {
            System.clearProperty("ljlinkpro.home");
        } else {
            System.setProperty("ljlinkpro.home", previousHome);
        }
    }

    @Test
    void defaultsMatchThePageAndNothingIsStoredAtFirst() throws IOException {
        assertEquals(0, UiSettings.load().size(), "the page's own defaults apply until something is chosen");
        String app = KeepAwakeWebTest.resource("app.js");
        assertTrue(app.contains("clockSize: 28, infoMode: \"compact\", infoWidth: 0, clock: true };"));
        assertEquals(28, UiSettings.DEFAULTS.get("clockSize"));
        assertEquals("compact", UiSettings.DEFAULTS.get("infoMode"));
        assertEquals(0, UiSettings.DEFAULTS.get("infoWidth"));
        assertTrue(app.contains("const SERVER_KEYS = [\"zoom\", \"mode\", \"theme\", \"view\", \"keepAwake\", \"intro\", \"clock\", "
                + "\"clockSize\", \"infoMode\", \"infoWidth\"];"), "language and hint cards stay per browser");
        assertTrue(UiSettings.DEFAULTS.keySet().containsAll(java.util.List.of("zoom", "mode", "theme", "view", "keepAwake",
                "intro", "clock", "clockSize", "infoMode", "infoWidth")));
        String i18n = KeepAwakeWebTest.resource("i18n.js");
        for (String en : new String[] {"Track info", "Compact", "Expanded", "Clock size", "Width of the track info"}) {
            assertTrue(i18n.contains("\"" + en + "\": \""), "German text missing for: " + en);
        }
    }

    @Test
    void onlyValidValuesAreKeptAndSurviveARestart() throws IOException {
        UiSettings.save(Hub.JSON.readTree("{\"clockSize\":96,\"infoMode\":\"wide\",\"infoWidth\":610,\"zoom\":8,"
                + "\"theme\":\"day\",\"clock\":false,\"lang\":\"de\",\"__proto__\":{\"x\":1},\"view\":\"huge\",\"mode\":7}"));
        UiSettings.save(Hub.JSON.readTree("{\"clockSize\":500,\"infoWidth\":120,\"zoom\":10}"));
        // A restart reads settings.json again (AppSettings has no cache).
        String file = Files.readString(java.nio.file.Path.of(System.getProperty("ljlinkpro.home"), "settings.json"));
        assertTrue(file.contains("\"ui\":"), file);
        var s = UiSettings.load();
        assertEquals(96, s.get("clockSize").asInt(), "160 px at most: the old value stays");
        assertEquals(610, s.get("infoWidth").asInt(), "400 px at least");
        assertEquals("wide", s.get("infoMode").asText());
        assertEquals(10, s.get("zoom").asInt(), "merged: later changes win");
        assertEquals("day", s.get("theme").asText());
        assertFalse(s.get("clock").asBoolean());
        for (String dropped : new String[] {"lang", "__proto__", "view", "mode"}) {
            assertFalse(s.has(dropped), dropped);
        }
    }

    @Test
    void tabletsMayReadButNotChange() throws IOException {
        app = Javalin.create(c -> c.showJavalinBanner = false);
        ControlGuard.install(app);
        UiSettings.install(app);
        app.start("127.0.0.1", 0);
        String self = "127.0.0.1:" + app.port();
        String tablet = "192.168.1.20:" + app.port();

        assertEquals("403", send("POST", tablet, null, "{\"clockSize\":100}")[0], "a tablet in the LAN");
        assertEquals("403", send("POST", self, "http://evil.example", "{\"clockSize\":100}")[0], "another web page");
        assertEquals(0, UiSettings.load().size());

        String[] read = send("GET", tablet, null, "");
        assertEquals("200", read[0], "tablets read the defaults of the computer");
        assertTrue(read[1].contains("\"controller\":false"), read[1]);

        assertEquals("200", send("POST", self, "http://" + self, "{\"clockSize\":100}")[0]);
        assertEquals(100, UiSettings.load().get("clockSize").asInt());
        String[] own = send("GET", self, "http://" + self, "");
        assertTrue(own[1].contains("\"controller\":true") && own[1].contains("\"clockSize\":100"), own[1]);
        assertTrue(ControlGuard.isGuarded("POST", UiSettings.PATH));
        assertFalse(ControlGuard.isGuarded("GET", UiSettings.PATH));
    }

    /** Status and body of a raw request (the Host header can be set, unlike with Java's HTTP client). */
    private String[] send(String method, String host, String origin, String body) throws IOException {
        try (Socket s = new Socket("127.0.0.1", app.port())) {
            StringBuilder req = new StringBuilder(method + " " + UiSettings.PATH + " HTTP/1.1\r\nHost: " + host
                    + "\r\nConnection: close\r\n");
            if (origin != null) {
                req.append("Origin: ").append(origin).append("\r\n");
            }
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            req.append("Content-Type: application/json\r\nContent-Length: ").append(b.length).append("\r\n\r\n");
            OutputStream out = s.getOutputStream();
            out.write(req.toString().getBytes(StandardCharsets.US_ASCII));
            out.write(b);
            out.flush();
            String all = new String(s.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int split = all.indexOf("\r\n\r\n");
            return new String[] {all.substring(9, 12), split < 0 ? "" : all.substring(split + 4)};
        }
    }
}
