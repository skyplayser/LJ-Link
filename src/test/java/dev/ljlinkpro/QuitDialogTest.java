package dev.ljlinkpro;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.accessibility.AccessibleState;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** The quit window of the program icon (feedback, stars, share link) and the rating in the web report window. */
class QuitDialogTest {
    private static String previousHome;

    @BeforeAll
    static void useTemporaryHome() throws Exception {
        previousHome = System.getProperty("ljlinkpro.home");
        System.setProperty("ljlinkpro.home", Files.createTempDirectory("ljlinkpro-quit-test").toString());
    }

    @AfterAll
    static void restoreHome() {
        if (previousHome == null) {
            System.clearProperty("ljlinkpro.home");
        } else {
            System.setProperty("ljlinkpro.home", previousHome);
        }
    }

    @Test
    void dontShowAgainIsOffByDefaultAndSaved() throws IOException {
        assertFalse(QuitDialog.skip(), "shown by default");
        QuitDialog.setSkip(true);
        assertTrue(QuitDialog.skip());
        String file = Files.readString(Path.of(System.getProperty("ljlinkpro.home"), "settings.json"));
        assertTrue(file.contains("\"" + QuitDialog.SKIP_KEY + "\":true"), file);
        QuitDialog.setSkip(false);
        assertFalse(QuitDialog.skip());
    }

    @Test
    void whenSwitchedOffQuitHappensAtOnceWithoutWindow() {
        QuitDialog.setSkip(true);
        try {
            AtomicInteger quits = new AtomicInteger();
            AtomicInteger feedback = new AtomicInteger();
            QuitDialog.quitFromMenu(s -> feedback.incrementAndGet(), quits::incrementAndGet);
            assertEquals(1, quits.get(), "quits on the calling thread, no window");
            assertEquals(0, feedback.get());
        } finally {
            QuitDialog.setSkip(false);
        }
    }

    @Test
    void reportLinkCarriesTheStars() {
        assertEquals("/#report", QuitDialog.reportPath(0));
        assertEquals("/#report?stars=1", QuitDialog.reportPath(1));
        assertEquals("/#report?stars=4", QuitDialog.reportPath(4));
        assertEquals("/#report?stars=5", QuitDialog.reportPath(5));
        assertEquals("/#report", QuitDialog.reportPath(6));
        assertEquals("/#report", QuitDialog.reportPath(-1));
    }

    @Test
    void clickingTheChosenStarAgainClearsTheRating() {
        assertEquals(3, QuitDialog.toggle(0, 3));
        assertEquals(5, QuitDialog.toggle(3, 5));
        assertEquals(0, QuitDialog.toggle(4, 4));
    }

    @Test
    void shareLinkIsTheWebsite() {
        assertEquals("https://skyplayser.github.io/LJ-Link/", QuitDialog.SHARE_URL);
    }

    @Test
    void updateQuitNeverShowsTheWindow() throws IOException {
        // The update hands the plain quit to UpdateDialog; only the "Quit" menu item goes through QuitDialog.
        String update = Files.readString(Path.of("src/main/java/dev/ljlinkpro/UpdateDialog.java"), StandardCharsets.UTF_8);
        assertFalse(update.contains("QuitDialog"));
        String tray = Files.readString(Path.of("src/main/java/dev/ljlinkpro/Tray.java"), StandardCharsets.UTF_8);
        assertTrue(tray.contains("quitItem.addActionListener(e -> QuitDialog.quitFromMenu(report, quit))"));
        assertTrue(tray.contains("UpdateDialog.offer(r, quit)") && tray.contains("UpdateDialog.check(false, quit,")
                && tray.contains("UpdateDialog.checkLater(quit,"));
    }

    @Test
    void settingsSwitchIsLocalAndTranslated() throws IOException {
        assertTrue(ControlGuard.isGuarded("/api/quit-note"), "only the computer that runs LJ Link may switch it");
        String i18n = KeepAwakeWebTest.resource("i18n.js");
        assertTrue(i18n.contains("\"Show note when quitting – Alpha\": \"Hinweis beim Beenden anzeigen – Alpha\""));
        assertTrue(i18n.contains("\"Quit\": \"Beenden\""));
        String html = KeepAwakeWebTest.resource("index.html");
        assertTrue(html.contains("<div class=\"setting hidden\" id=\"quit-note-setting\">"), "hidden until /api/info says controller");
        assertTrue(html.contains("id=\"quit-note-box\""));
        String app = KeepAwakeWebTest.resource("app.js");
        assertTrue(app.contains("post(\"/api/quit-note\", { on: $(\"quit-note-box\").checked })"));
        assertTrue(app.contains("$(\"quit-note-setting\").classList.toggle(\"hidden\", q.tray !== true);"),
                "only shown when the program icon is installed");
        String main = Files.readString(Path.of("src/main/java/dev/ljlinkpro/Main.java"), StandardCharsets.UTF_8);
        assertTrue(main.contains("Map.of(\"on\", !QuitDialog.skip(), \"tray\", Tray.installed())"));
        assertTrue(main.contains("QuitDialog.setSkip(!Hub.JSON.readTree(ctx.body()).path(\"on\").asBoolean(true))"),
                "the switch shows the note when on, so it stores the opposite in skipQuitDialog");
    }

    @Test
    void reportWindowHasTheRatingInBothLanguages() throws IOException {
        String i18n = KeepAwakeWebTest.resource("i18n.js");
        for (String en : List.of("Report a problem or request a feature", "How do you like LJ Link? (optional)",
                "Clear rating", "{n} of 5 stars", "Rating: {n} of 5 stars",
                "Describe the problem or your wish. For a problem: which devices, what you did, what you see.",
                "Please describe the problem or your wish first, or choose a rating.")) {
            assertTrue(i18n.contains("\"" + en + "\": \""), "German text missing for: " + en);
        }
        assertTrue(i18n.contains("\"Rating: {n} of 5 stars\": \"Bewertung: {n} von 5 Sternen\""));
        assertTrue(i18n.contains("\"Report a problem, request a feature or rate LJ Link. Only what you send yourself is sent.\": \""));
        assertTrue(i18n.contains("#report-stars"), "star labels are set by app.js, not by the static translation");

        String html = KeepAwakeWebTest.resource("index.html");
        assertTrue(html.contains("id=\"report-stars\" class=\"stars\" role=\"radiogroup\""));
        for (int n = 1; n <= 5; n++) {
            assertTrue(html.contains("role=\"radio\" aria-checked=\"false\" data-n=\"" + n + "\""), "star " + n);
        }

        String app = KeepAwakeWebTest.resource("app.js");
        assertTrue(app.contains("h.match(/stars=([1-5])/)"), "#report?stars=N presets the rating");
        assertTrue(app.contains("if (reportStars) parts.push(ratingLine());"), "rating line comes first in the report");
        assertFalse(app.contains("Please describe the problem first."), "a rating alone may be sent");
        assertFalse(app.contains("labels=bug"), "wishes and ratings are not bugs: no label in the GitHub link");
        assertTrue(app.contains("b.onclick = () => setStars(n);"), "like a radio: clicking the chosen star keeps it");
    }

    @Test
    void enterQuitsAndTheChosenStarIsAnnounced() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        AtomicInteger quits = new AtomicInteger();
        AtomicInteger feedback = new AtomicInteger(-1);
        SwingUtilities.invokeAndWait(() -> {
            JDialog d = QuitDialog.create(feedback::set, quits::incrementAndGet);
            try {
                List<AbstractButton> buttons = new ArrayList<>();
                collect(d.getContentPane(), buttons);
                JButton def = d.getRootPane().getDefaultButton();
                assertTrue(def.getText().equals("Quit") || def.getText().equals("Beenden"), def.getText());
                List<JToggleButton> stars = buttons.stream().filter(b -> b instanceof JToggleButton && !(b instanceof javax.swing.JCheckBox))
                        .map(b -> (JToggleButton) b).toList();
                assertEquals(5, stars.size());
                stars.get(3).doClick();
                for (int i = 0; i < 5; i++) {
                    boolean chosen = i == 3;
                    assertEquals(chosen, stars.get(i).isSelected(), "star " + (i + 1));
                    assertEquals(chosen, stars.get(i).getAccessibleContext().getAccessibleStateSet()
                            .contains(AccessibleState.CHECKED), "star " + (i + 1));
                    assertEquals(QuitDialog.starName(i + 1, chosen), stars.get(i).getAccessibleContext().getAccessibleName());
                }
                stars.get(3).doClick();
                assertTrue(stars.stream().noneMatch(JToggleButton::isSelected), "clicking it again clears it");
                def.doClick();
            } finally {
                d.dispose();
            }
        });
        assertEquals(1, quits.get(), "Enter (the default button) quits");
        assertEquals(-1, feedback.get());
    }

    private static void collect(Container c, List<AbstractButton> out) {
        for (Component k : c.getComponents()) {
            if (k instanceof AbstractButton b) {
                out.add(b);
            }
            if (k instanceof Container cc) {
                collect(cc, out);
            }
        }
    }

    @Test
    void deckNumberFollowsTheTheme() throws IOException {
        // Was white on white in the day theme.
        String css = KeepAwakeWebTest.resource("style.css");
        assertTrue(css.contains(".deck-num { font-size: 20px; font-weight: 800; color: var(--text); }"));
    }
}
