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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Clock bar and Art-Net timecode (ADR-044): parser, defaults, timeout, the clock message and the guarded route. */
class ArtNetTimecodeTest {
    private String previousHome;

    @BeforeEach
    void setUp() throws IOException {
        previousHome = System.getProperty("ljlinkpro.home");
        System.setProperty("ljlinkpro.home", Files.createTempDirectory("ljlinkpro-timecode").toString());
    }

    @AfterEach
    void tearDown() {
        if (previousHome == null) {
            System.clearProperty("ljlinkpro.home");
        } else {
            System.setProperty("ljlinkpro.home", previousHome);
        }
    }

    static byte[] packet(int h, int m, int s, int f, int type) {
        byte[] b = new byte[19];
        byte[] id = "Art-Net".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(id, 0, b, 0, id.length);
        b[8] = 0x00;
        b[9] = (byte) 0x97;
        b[11] = 14;
        b[14] = (byte) f;
        b[15] = (byte) s;
        b[16] = (byte) m;
        b[17] = (byte) h;
        b[18] = (byte) type;
        return b;
    }

    @Test
    void parsesAllFourTypes() {
        ArtNetTimecode.Frame f = ArtNetTimecode.parse(packet(1, 2, 3, 24, 1), 19);
        assertEquals(new ArtNetTimecode.Frame(1, 2, 3, 24, 1), f);
        assertEquals(25, f.fps());
        assertEquals("25", f.rate());
        assertEquals("24", ArtNetTimecode.parse(packet(0, 0, 0, 23, 0), 19).rate());
        assertEquals("29.97df", ArtNetTimecode.parse(packet(23, 59, 59, 29, 2), 19).rate());
        assertEquals(30, ArtNetTimecode.parse(packet(0, 0, 0, 29, 3), 19).fps());
    }

    @Test
    void rejectsInvalidPackets() {
        assertNull(ArtNetTimecode.parse(packet(0, 0, 0, 0, 1), 18), "too short");
        assertNull(ArtNetTimecode.parse(null, 19));
        byte[] wrongId = packet(0, 0, 0, 0, 1);
        wrongId[0] = 'X';
        assertNull(ArtNetTimecode.parse(wrongId, 19));
        byte[] dmx = packet(0, 0, 0, 0, 1);
        dmx[9] = 0x50; // OpDmx
        assertNull(ArtNetTimecode.parse(dmx, 19));
        assertNull(ArtNetTimecode.parse(packet(0, 0, 0, 25, 1), 19), "frame 25 at 25 fps");
        assertNull(ArtNetTimecode.parse(packet(0, 0, 0, 24, 0), 19), "frame 24 at 24 fps");
        assertNull(ArtNetTimecode.parse(packet(24, 0, 0, 0, 1), 19));
        assertNull(ArtNetTimecode.parse(packet(0, 60, 0, 0, 1), 19));
        assertNull(ArtNetTimecode.parse(packet(0, 0, 60, 0, 1), 19));
        assertNull(ArtNetTimecode.parse(packet(0, 0, 0, 0, 4), 19), "unknown type");
    }

    @Test
    void timecodeIsOffByDefaultAndClockIsOn() throws IOException {
        assertFalse(ArtNetTimecode.enabledSetting());
        ArtNetTimecode tc = new ArtNetTimecode(0);
        assertEquals(false, tc.view(0).get("on"));
        assertFalse(tc.running(), "the port stays closed while it is off");
        String app = KeepAwakeWebTest.resource("app.js");
        assertTrue(app.contains("clock: true };"), "the clock bar is on by default");
        String i18n = KeepAwakeWebTest.resource("i18n.js");
        for (String en : new String[] {"Clock and timecode", "Show clock above the waveforms", "Time", "no timecode",
                "Show timecode from the network (Art-Net) – Alpha", "Timecode is arriving"}) {
            assertTrue(i18n.contains("\"" + en + "\": \""), "German text missing for: " + en);
        }
        assertTrue(KeepAwakeWebTest.resource("index.html").contains("(Art-Net) – Alpha"));
    }

    @Test
    void frameTimesOutAfterTwoSecondsAndViewHasNoAddress() {
        ArtNetTimecode tc = new ArtNetTimecode(0);
        tc.received(packet(10, 20, 30, 12, 1), 19, 1_000_000_000L);
        Map<String, Object> v = tc.view(1_500_000_000L);
        assertEquals(10, v.get("h"));
        assertEquals(12, v.get("f"));
        assertEquals(25, v.get("fps"));
        assertEquals(500L, v.get("age"));
        assertFalse(v.toString().contains("127.0.0.1") || v.containsKey("ip") || v.containsKey("from"));
        assertNotNull(tc.current(2_999_000_000L));
        assertNull(tc.current(3_000_000_000L), "2 s without timecode: back to the clock");
        tc.received(new byte[] {1, 2, 3}, 3, 3_100_000_000L);
        assertNull(tc.current(3_100_000_000L), "other packets do not count");
    }

    @Test
    void hubSendsServerTimeOncePerSecondAndOnEveryNewFrame() {
        Hub hub = new Hub(WsClient.STALL_NANOS, false);
        ArtNetTimecode tc = new ArtNetTimecode(0);
        hub.timecode(tc);
        String first = hub.clockTick(1_000_000_000L, 1_700_000_000_000L);
        assertTrue(first.contains("\"type\":\"clock\"") && first.contains("\"w\":1700000000000") && first.contains("\"z\":"));
        assertNull(hub.clockTick(1_500_000_000L, 1_700_000_000_500L), "nothing new within a second");
        tc.received(packet(0, 0, 1, 2, 1), 19, 1_550_000_000L);
        String withTc = hub.clockTick(1_600_000_000L, 1_700_000_000_600L);
        assertNotNull(withTc, "a new frame is sent at once");
        assertTrue(withTc.contains("\"f\":2"));
        assertNotNull(hub.clockTick(2_700_000_000L, 1_700_000_001_700L));
    }

    @Test
    void routeIsOnlyForThisComputer() throws IOException {
        assertTrue(ControlGuard.isGuarded("/api/timecode"));
        assertFalse(ControlGuard.isPublic("/api/timecode"));
        Javalin app = Javalin.create(c -> c.showJavalinBanner = false);
        ControlGuard.install(app);
        ArtNetTimecode tc = new ArtNetTimecode(0);
        ArtNetTimecode.routes(app, tc);
        app.start("127.0.0.1", 0);
        try {
            int port = app.port();
            assertEquals("403", send(port, "POST", "192.168.1.20:" + port, "{\"on\":true}"), "tablet host");
            assertEquals("403", send(port, "GET", "evil.example:" + port, ""), "rebinding host");
            assertFalse(tc.enabled());
            assertEquals("200", send(port, "GET", "localhost:" + port, ""));
            assertEquals("200", send(port, "POST", "localhost:" + port, "{\"on\":false}"));
            assertFalse(ArtNetTimecode.enabledSetting());
        } finally {
            app.stop();
            tc.apply(false);
        }
    }

    private static String send(int port, String method, String host, String body) throws IOException {
        try (Socket s = new Socket("127.0.0.1", port)) {
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            String req = method + " /api/timecode HTTP/1.1\r\nHost: " + host + "\r\nConnection: close\r\n"
                    + "Content-Type: application/json\r\nContent-Length: " + b.length + "\r\n\r\n";
            OutputStream out = s.getOutputStream();
            out.write(req.getBytes(StandardCharsets.US_ASCII));
            out.write(b);
            out.flush();
            return new String(s.getInputStream().readAllBytes(), StandardCharsets.UTF_8).substring(9, 12);
        }
    }
}
