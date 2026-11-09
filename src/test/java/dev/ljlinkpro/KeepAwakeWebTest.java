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
                "Tap here once to keep the screen on", "Off",
                "The screen may turn off – tap here to restart keep-awake", "Starting the video …",
                "Video stopped – please tap the page", "Way: {s}", "video", "none", "Video time running: {s}",
                "yes", "no", "Last stopped: {s}", "never",
                "On an iPad or iPhone without HTTPS this is a stopgap. More reliable: in the iPad settings set "
                        + "“Display & Brightness → Auto-Lock: Never” (with the charger connected). Also switch Low Power Mode off: "
                        + "on the iPhone it shortens Auto-Lock to 30 seconds, probably on the iPad too.",
                "Stops this device from dimming or locking the screen while LJ Link is shown, like a playing video. "
                        + "On an iPad opened over the network, tap the page once; LJ Link then tries to keep the screen on. "
                        + "On a Mac LJ Link also keeps the computer from falling asleep while a browser shows it.")) {
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

    @Test
    void fallbackVideoIsRenderedInsideTheViewport() throws IOException {
        String css = resource("style.css");
        Matcher m = Pattern.compile("\\.keep-awake-video \\{([^}]*)\\}").matcher(css);
        assertTrue(m.find(), ".keep-awake-video rule");
        String rule = m.group(1).replaceAll("\\s+", " ");
        // iOS may treat an invisible or covered video differently: a real 2x2 px element on top, nearly transparent.
        assertFalse(rule.matches(".*opacity: ?0(;|\\s|$).*"), "not opacity 0: " + rule);
        assertTrue(rule.contains("opacity: 0.01"), rule);
        assertTrue(rule.contains("width: 2px") && rule.contains("height: 2px"), rule);
        assertTrue(rule.contains("position: fixed") && rule.contains("left: 0") && rule.contains("bottom: 0"), rule);
        assertFalse(rule.contains("z-index: -"), "not behind the page: " + rule);
        assertFalse(rule.contains("display: none") || rule.contains("visibility: hidden"), rule);
        assertTrue(rule.contains("pointer-events: none"), rule);
    }

    @Test
    void videoStatusFollowsTheRealPlayback() throws IOException {
        String app = resource("app.js");
        assertTrue(app.contains("function checkAwakeVideo()"), "currentTime is checked");
        int check = app.indexOf("function checkAwakeVideo()");
        String fn = app.substring(check, app.indexOf("function startAwakeCheck()"));
        assertTrue(fn.contains("currentTime"), "active only while the time moves");
        assertTrue(fn.contains("awakeVideoStopped("), "a standing video is reported");
        int play = app.indexOf("function playAwakeVideo(");
        String playFn = app.substring(play, app.indexOf("function updateAwake("));
        assertFalse(playFn.contains("setAwake(\"video\")"), "play() resolving alone is not \"active\"");
        assertTrue(app.contains("\"pageshow\""), "checked again when back from the back/forward cache");
        assertTrue(app.contains("awake.state === \"stalled\")) updateAwake(true)"), "a tap starts a stopped video again");
        assertTrue(app.contains("navigator.userAgent"), "user agent in the diagnostics tooltip");
        String html = resource("index.html");
        assertTrue(html.contains("id=\"awake-fallback\"") && html.contains("id=\"awake-hint-again\""));
    }

    @Test
    void startingEndsAfterTwoChecksWithoutMovementAndTheTimerStops() throws IOException {
        String app = resource("app.js");
        assertTrue(app.contains("const AWAKE_START_CHECKS = 2;"), "\"starting\" has a time limit of 2 checks");
        String fn = app.substring(app.indexOf("function checkAwakeVideo()"), app.indexOf("function startAwakeCheck()"));
        assertTrue(fn.contains("++awake.still >= AWAKE_START_CHECKS") && fn.contains("awakeVideoStopped(\"time never started\")"),
                "a video that never moves ends in \"stalled\"");
        String set = app.substring(app.indexOf("function setAwake("), app.indexOf("function awakeDiagLine("));
        assertTrue(set.contains("startAwakeCheck(); else stopAwakeCheck();"), "timer only while starting or active");
        assertTrue(app.contains("clearInterval(awake.check)") && app.contains("awake.check = null"));
        String start = app.substring(app.indexOf("function startAwakeCheck()"), app.indexOf("function stopAwakeCheck()"));
        assertTrue(start.contains("if (awake.check) return;"), "never two timers");
        String rel = app.substring(app.indexOf("function releaseAwake()"), app.indexOf("function onAwakeTap()"));
        assertTrue(rel.contains("stopAwakeCheck()"), "stopped when switched off");
        String upd = app.substring(app.indexOf("function updateAwake("), app.indexOf("function releaseAwake()"));
        assertTrue(upd.contains("awake.progressing ? \"video\" : \"starting\""), "a running video keeps \"active\"");
    }
}
