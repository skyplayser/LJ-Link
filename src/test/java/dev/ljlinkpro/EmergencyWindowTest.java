package dev.ljlinkpro;

import org.junit.jupiter.api.Test;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** The EMERGENCY window and the chat number at the program icon (suite chat, Alpha). */
class EmergencyWindowTest {
    private static Map<String, Object> msg(String nick, String where, String app, String text) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nick", nick);
        m.put("where", where);
        m.put("app", app);
        m.put("text", text);
        m.put("n", 7L);
        return m;
    }

    @Test
    void fromReadsLikeTheBrowser() {
        assertEquals("DJ David (DJ Booth) · LJL Link", EmergencyWindow.from(msg("DJ David", "DJ Booth", "LJL Link", "x")));
        assertEquals("VJ-Link", EmergencyWindow.from(msg("", "", "VJ-Link", "x")), "no name: the program");
        assertEquals("Phil · LJ Link", EmergencyWindow.from(msg("Phil", "", "LJ Link", "x")));
        Map<String, Object> m = msg("Phil", "", "LJ Link", "x");
        m.put("at", 1_794_685_866_935L);
        assertTrue(EmergencyWindow.from(m).matches("Phil · LJ Link · \\d\\d:\\d\\d"), EmergencyWindow.from(m));
    }

    @Test
    void moreCountsTheOthersWaiting() {
        assertEquals("", EmergencyWindow.more(1), "only one: nothing waits");
        String two = EmergencyWindow.more(3);
        assertTrue(two.equals("2 more") || two.equals("2 weitere"), two);
    }

    @Test
    void textIsNeverHtmlAndOkConfirms() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a screen");
        int[] ok = {0};
        int[] all = {0};
        JDialog d = EmergencyWindow.create(msg("DJ David", "DJ Booth", "LJL Link", "<html><b>Feuer</b>"), 3, () -> ok[0]++,
                () -> all[0]++);
        try {
            assertTrue(d.isAlwaysOnTop());
            assertFalse(d.isAutoRequestFocus(), "does not take the keyboard from the light desk program");
            assertTrue(d.isUndecorated());
            assertInstanceOf(JTextArea.class, EmergencyWindow.find(d, "text"), "a text area shows tags as text");
            assertEquals("<html><b>Feuer</b>", ((JTextArea) EmergencyWindow.find(d, "text")).getText());
            assertEquals("EMERGENCY", ((JLabel) EmergencyWindow.find(d, "title")).getText());
            ((javax.swing.JButton) EmergencyWindow.find(d, "ok")).doClick();
            assertEquals(1, ok[0]);
            assertTrue(EmergencyWindow.find(d, "all").isVisible(), "three open: \"Close all\" is there");
            ((javax.swing.JButton) EmergencyWindow.find(d, "all")).doClick();
            assertEquals(1, all[0]);
        } finally {
            d.dispose();
        }
        JDialog one = EmergencyWindow.create(msg("Phil", "", "LJ Link", "x"), 1, () -> { }, () -> { });
        try {
            assertFalse(EmergencyWindow.find(one, "all").isVisible(), "one open: no \"Close all\"");
        } finally {
            one.dispose();
        }
    }

    @Test
    void programIconShowsTheUnreadNumber() {
        BufferedImage plain = Tray.icon(0);
        BufferedImage badge = Tray.icon(3);
        assertEquals(64, badge.getWidth());
        // Inside the circle, left of the digit.
        int corner = badge.getRGB(34, 18);
        assertNotEquals(plain.getRGB(34, 18), corner, "a circle at the top right");
        int r = (corner >> 16) & 0xFF;
        int g = (corner >> 8) & 0xFF;
        int b = corner & 0xFF;
        assertTrue(r < 0x50 && g > 0xA0 && b > 0xC0, "cyan, the accent colour: " + Integer.toHexString(corner));
        assertEquals(plain.getRGB(10, 50), badge.getRGB(10, 50), "the faders stay as they are");
        int lit = Tray.icon(3, true).getRGB(34, 18);
        assertTrue(((lit >> 16) & 0xFF) > 0xE0 && ((lit >> 8) & 0xFF) > 0xC0 && (lit & 0xFF) < 0x40,
                "unseen EMERGENCY: the circle is yellow in the lit half of the blinking: " + Integer.toHexString(lit));
        assertNotEquals(plain.getRGB(34, 18), Tray.icon(0, true).getRGB(34, 18), "yellow also without a number");
        assertEquals("Open suite chat (alpha)", Tray.chatLabel(false, 0));
        assertEquals("Suite-Chat öffnen (Alpha) – 3 neu", Tray.chatLabel(true, 3));
        assertEquals("Open suite chat (alpha) – 99+ new", Tray.chatLabel(false, 120));
    }
}
