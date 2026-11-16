package dev.ljlinkpro;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static dev.ljlinkpro.KeepAwakeWebTest.resource;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks how the shared Link-Suite start animation is wired into the web page (intro/). */
class IntroWebTest {

    @Test
    void everyTextHasAGermanTranslation() throws IOException {
        String i18n = resource("i18n.js");
        for (String en : List.of("Start", "Start animation", "Skip:", "any key", "or click",
                "Shows the Link-Suite animation once when LJ Link opens in this browser (about 2.5 s; any key or a click "
                        + "skips it). With “Reduce motion” switched on in the system it only fades in.")) {
            assertTrue(i18n.contains("\"" + en + "\": \""), "no German text for: " + en);
        }
    }

    @Test
    void layerCoversFromTheFirstFrameAndTheModuleLoadsAfterTheApp() throws IOException {
        String html = resource("index.html");
        int layer = html.indexOf("<div id=\"lsi\" class=\"lsi-layer\">");
        int app = html.indexOf("<script src=\"app.js\"></script>");
        int module = html.indexOf("<script type=\"module\" src=\"intro/start.js\"></script>");
        assertTrue(layer > 0 && app > layer && module > app, "layer, then app.js, then the module");
        assertTrue(html.contains("<link rel=\"stylesheet\" href=\"intro/program-intro.css\">"));
        assertTrue(html.contains("id=\"intro-box\""), "the setting to switch it off");
    }

    @Test
    void thePageNeverStaysCovered() throws IOException {
        String app = resource("app.js");
        // Removed at once when off, on deep links and when already shown; a key, a click or 3.5 s remove it until the
        // module takes over, so a missing or broken module cannot lock the page.
        assertTrue(app.contains("settings.intro !== false"));
        assertTrue(app.contains("!location.hash.replace(\"#\", \"\")"), "no animation on deep links");
        assertTrue(app.contains("setTimeout(drop, 3500)"), "time limit");
        assertTrue(app.contains("layer.addEventListener(\"pointerdown\", tap)") && app.contains("addEventListener(\"keydown\", drop)"));
        assertTrue(app.contains("layer.addEventListener(\"click\", drop, { once: true })"), "a tap must not reach the page underneath");
        String start = resource("intro/start.js").replace("\r\n", "\n");   // Windows checks files out with CRLF
        assertTrue(start.contains("if (!hook.claim()) return;"), "takes over only a layer that is still there");
        assertTrue(start.contains("start().catch(abort)") && start.contains("} catch (e) {\n    abort();"), "errors free the page");
        assertTrue(start.contains("once: false"), "once-per-tab mark is kept by app.js");
    }

    @Test
    void copiedModuleNamesItsOriginAndAllImagesExist() throws IOException {
        assertTrue(resource("intro/program-intro.js").startsWith("// Copied unchanged from the Suite-Launcher project"));
        assertTrue(resource("intro/program-intro.css").startsWith("/* Copied unchanged from the Suite-Launcher project"));
        // What defaultImages("intro/icons/", theme, "lj") asks for.
        for (String icon : List.of("suite-symbol.svg", "suite-logo-dark.svg", "suite-logo-light.svg", "lj-logo-dark.svg",
                "lj-logo-light.svg", "lj-icon.svg", "ljl-icon.png", "vj-icon.svg", "vd-icon.svg", "net-icon.svg",
                "cr-icon.svg", "pl-icon.svg", "db-icon.svg")) {
            try (InputStream in = IntroWebTest.class.getResourceAsStream("/web/intro/icons/" + icon)) {
                assertNotNull(in, "missing intro/icons/" + icon);
            }
        }
    }
}
