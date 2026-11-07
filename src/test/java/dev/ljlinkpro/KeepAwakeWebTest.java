package dev.ljlinkpro;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks the web part of "Keep screen awake" (ADR-022): texts, the embedded video and the WebKit rules. */
class KeepAwakeWebTest {

    static String resource(String name) throws IOException {
        try (InputStream in = KeepAwakeWebTest.class.getResourceAsStream("/web/" + name)) {
            assertNotNull(in, name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void everyTextHasAGermanTranslation() throws IOException {
        String i18n = resource("i18n.js");
        for (String en : List.of("Screen", "Keep screen awake – Alpha", "Status: {s}", "Active – the screen stays on",
                "Active (video) – the screen stays on", "Waiting for a tap – tap the page once",
                "Paused while the page is hidden", "Not available in this browser",
                "Tap here once to keep the screen on", "Off")) {
            assertTrue(i18n.contains("\"" + en + "\": \""), "German text missing for: " + en);
        }
        assertTrue(i18n.contains("#awake-status"), "generated status text must not be translated twice");
    }

    @Test
    void embeddedVideoIsATinyMp4WithoutExternalFiles() throws IOException {
        String js = resource("keep-awake-video.js");
        Matcher m = Pattern.compile("\"data:video/mp4;base64,([A-Za-z0-9+/=]+)\"").matcher(js);
        assertTrue(m.find(), "data URI");
        byte[] mp4 = Base64.getDecoder().decode(m.group(1));
        assertTrue(mp4.length < 10_000, "video stays tiny: " + mp4.length);
        assertEquals("ftyp", new String(mp4, 4, 4, StandardCharsets.US_ASCII));
        String all = new String(mp4, StandardCharsets.ISO_8859_1);
        assertTrue(all.contains("soun"), "needs an audio track, WebKit keeps the display on only for video with audio");
        assertTrue(all.contains("vide"), "video track");
        assertFalse(js.contains("http://") || js.contains("https://"), "no external resources");
    }

    @Test
    void pageLoadsTheVideoBeforeTheApp() throws IOException {
        String html = resource("index.html");
        int video = html.indexOf("<script src=\"keep-awake-video.js\"></script>");
        int app = html.indexOf("<script src=\"app.js\"></script>");
        assertTrue(video > 0 && video < app);
        assertTrue(html.contains("id=\"awake-box\"") && html.contains("id=\"awake-hint\""));
        int header = html.indexOf("</header>");
        assertTrue(html.indexOf("id=\"awake-hint\"") > header, "the hint is a strip below the header, not in it");
        assertTrue(html.contains("Keep screen awake – Alpha"), "untested on real devices: marked Alpha");
    }

    @Test
    void fallbackVideoFollowsTheWebKitRules() throws IOException {
        String app = resource("app.js");
        int start = app.indexOf("function awakeVideo()");
        int end = app.indexOf("function playAwakeVideo(");
        assertTrue(start > 0 && end > start);
        String fn = app.substring(start, end);
        // WebKit (HTMLMediaElement::shouldDisableSleep) lets the display sleep for looping or muted video.
        assertFalse(fn.contains("\"loop\"") || fn.contains(".loop"), "no loop attribute");
        assertFalse(fn.contains("muted"), "not muted");
        assertTrue(fn.contains("playsinline"));
        assertTrue(app.contains("keepAwake: true"), "on by default");
        assertTrue(app.contains("window.isSecureContext"), "the wake lock only exists in a secure context");
        assertTrue(app.contains("\"visibilitychange\""), "requested again when the page is visible");
    }
}
