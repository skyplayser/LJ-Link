package dev.ljlinkpro;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static dev.ljlinkpro.KeepAwakeWebTest.resource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks how the shared "Get Link-Suite" hint card is wired into the start page (suite/). */
class LauncherHintWebTest {

    @Test
    void routesAreLocalOnly() {
        // Tablets must neither see the card nor make this computer open a browser.
        assertTrue(ControlGuard.isGuarded("/api/suite/launcher"));
        assertTrue(ControlGuard.isGuarded("/api/suite/launcher-page"));
    }

    @Test
    void theButtonOpensOnlyTheFixedPage() throws IOException {
        assertEquals("https://skyplayser.github.io/Link-Suite/", Main.LAUNCHER_PAGE);
        String hint = resource("suite/hint.js");
        assertTrue(hint.contains("fetch(\"/api/suite/launcher-page\", { method: \"POST\", headers: { \"Content-Type\": \"application/json\" }"));
        assertFalse(hint.contains("https://"), "the page never opens an address itself");
        assertFalse(hint.contains("window.open"));
    }

    @Test
    void cardSitsUnderTheStepsAndWaitsForTheAnimation() throws IOException {
        String html = resource("index.html");
        int steps = html.indexOf("</ol>", html.indexOf("<ol class=\"steps\">"));
        int slot = html.indexOf("<div id=\"launcher-hint\" class=\"launcher-hint\"></div>");
        assertTrue(steps > 0 && slot > steps, "slot right under the three steps");
        assertTrue(html.indexOf("<script type=\"module\" src=\"suite/hint.js\"></script>") > html.indexOf("<script src=\"app.js\"></script>"));
        assertTrue(html.contains("<link rel=\"stylesheet\" href=\"suite/launcher-hint.css\">"));
        String hint = resource("suite/hint.js");
        assertTrue(hint.contains("after: intro ? intro.done : true"), "fades in only after the start animation");
        assertTrue(hint.contains("hidden: ui.setting(\"hideLauncherHint\") === true"), "\"Don't show again\" is kept");
        assertTrue(resource("app.js").contains("hideLauncherHint: false"));
        assertTrue(resource("i18n.js").contains("#launcher-hint, script, style\""), "the card brings its own texts");
    }

    @Test
    void copiedModuleNamesItsOrigin() throws IOException {
        assertTrue(resource("suite/launcher-hint.js").startsWith("// Copied unchanged from the Suite-Launcher project"));
        assertTrue(resource("suite/launcher-hint.css").startsWith("/* Copied unchanged from the Suite-Launcher project"));
        try (InputStream in = getClass().getResourceAsStream("/web/suite/icons/link-suite-icon.svg")) {
            assertNotNull(in);
        }
    }
}
