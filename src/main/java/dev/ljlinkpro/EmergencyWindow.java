package dev.ljlinkpro;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * The EMERGENCY message of the suite chat (Alpha, docs/suite-chat.md) outside the browser: LJ Link often runs only as
 * the program icon, so an EMERGENCY from another program opens this window at the bottom right of the screen, always
 * on top, with a yellow blinking border, until "OK". Several wait in line ("2 more", "Close all"). "OK" here or in the
 * browser confirms it in both places ({@link SuiteChat#ack}). The room limits how many there can be (3 per program in
 * 60 s, 20 open).
 *
 * <p>The window does not take the keyboard focus when it appears (the LJ may be typing on a light desk program);
 * a click on it does. The text is shown in a text area, never in a label: a label would render "&lt;html&gt;…" as
 * HTML. No sound (the computer's output may be the mixer).
 */
final class EmergencyWindow {
    private EmergencyWindow() {}

    static final Color YELLOW = new Color(0xFF, 0xD6, 0x0A);
    private static final Color PANEL = new Color(0x14, 0x17, 0x1F);
    private static final Color TEXT = new Color(0xF2, 0xF4, 0xF8);
    private static final Color DIM = new Color(0x9A, 0xA3, 0xB5);
    private static final int WIDTH = 380;

    /** Only touched on the Swing thread. */
    private static JDialog window;
    private static Timer blink;
    private static long showing = -1;

    /** Shows the oldest open EMERGENCY of {@code chat}, or closes the window when none is open. Any thread. */
    static void update(SuiteChat chat) {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                show(chat, chat.alerts());
            } catch (RuntimeException e) {
                System.err.println("Emergency window unavailable: " + e); // never the text (privacy)
            }
        });
    }

    private static String tr(String en, String de) {
        return "de".equals(AppSettings.language()) ? de : en;
    }

    private static void show(SuiteChat chat, List<Map<String, Object>> open) {
        if (open.isEmpty()) {
            close();
            return;
        }
        Map<String, Object> m = open.get(0);
        long n = (Long) m.get("n");
        if (window != null && window.isDisplayable() && showing == n) {
            ((JLabel) find(window, "more")).setText(more(open.size()));
            find(window, "all").setVisible(open.size() > 1);
            window.getRootPane().putClientProperty("upTo", newest(open));
            return;
        }
        close();
        JDialog[] made = new JDialog[1];
        window = create(m, open.size(), () -> chat.ack(n),
                () -> chat.ackAll((Long) made[0].getRootPane().getClientProperty("upTo")));
        made[0] = window;
        window.getRootPane().putClientProperty("upTo", newest(open));
        showing = n;
        place(window);
        window.setVisible(true);
        blink = new Timer(500, e -> {
            if (window != null) {
                JPanel root = (JPanel) window.getContentPane();
                boolean on = Boolean.TRUE.equals(root.getClientProperty("lit"));
                root.putClientProperty("lit", !on);
                root.setBorder(border(!on));
            }
        });
        if (!reducedMotion()) {
            blink.start();
        }
    }

    /** The number of the newest EMERGENCY shown ("Close all" closes up to it, not one that arrives meanwhile). */
    static long newest(List<Map<String, Object>> open) {
        long max = 0;
        for (Map<String, Object> m : open) {
            max = Math.max(max, (Long) m.get("n"));
        }
        return max;
    }

    private static void close() {
        if (blink != null) {
            blink.stop();
            blink = null;
        }
        if (window != null) {
            window.dispose();
            window = null;
        }
        showing = -1;
    }

    static String more(int open) {
        return open > 1 ? tr((open - 1) + " more", (open - 1) + " weitere") : "";
    }

    /** "DJ David (DJ Booth) · LJL Link · 21:04", like the browser. */
    static String from(Map<String, Object> m) {
        String nick = String.valueOf(m.getOrDefault("nick", ""));
        String where = String.valueOf(m.getOrDefault("where", ""));
        String app = String.valueOf(m.getOrDefault("app", ""));
        String who = (nick.isEmpty() ? (app.isEmpty() ? "?" : app) : nick) + (where.isEmpty() ? "" : " (" + where + ")")
                + (!nick.isEmpty() && !app.isEmpty() ? " · " + app : "");
        Object at = m.get("at");
        if (at instanceof Number a) {
            who += " · " + DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
                    .format(Instant.ofEpochMilli(a.longValue()));
        }
        return who;
    }

    private static Border border(boolean lit) {
        return new CompoundBorder(new LineBorder(lit ? YELLOW : PANEL, 4), new EmptyBorder(12, 16, 14, 16));
    }

    /**
     * Builds the window (not shown). Package-private for tests and screenshots.
     *
     * @param all "Close all", shown while more than one EMERGENCY is open
     */
    static JDialog create(Map<String, Object> m, int open, Runnable ok, Runnable all) {
        JDialog d = new JDialog((java.awt.Frame) null, "EMERGENCY – LJ Link", false);
        d.setUndecorated(true);
        d.setAlwaysOnTop(true);
        d.setAutoRequestFocus(false);
        d.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE); // only "OK" closes it
        d.getAccessibleContext().setAccessibleDescription(String.valueOf(m.get("text")));

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(PANEL);
        root.setBorder(border(true));
        root.putClientProperty("lit", true);

        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("EMERGENCY");
        title.setName("title");
        title.setForeground(YELLOW);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        add(col, title, 6);

        JTextArea who = area(from(m), DIM, 12f, Font.PLAIN);
        who.setName("from");
        add(col, who, 6);

        JTextArea text = area(String.valueOf(m.get("text")), TEXT, 18f, Font.BOLD);
        text.setName("text");
        add(col, text, 10);

        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        JLabel more = new JLabel(more(open));
        more.setName("more");
        more.setForeground(DIM);
        row.add(more, BorderLayout.WEST);
        JButton button = new JButton("OK");
        button.setName("ok");
        button.setFont(button.getFont().deriveFont(Font.BOLD, 14f));
        button.setBackground(YELLOW);
        button.setForeground(Color.BLACK);
        button.setOpaque(true);
        button.setBorderPainted(false);
        button.setFocusPainted(true);
        button.addActionListener(e -> ok.run());
        JButton closeAll = new JButton(tr("Close all", "Alle schließen"));
        closeAll.setName("all");
        closeAll.setFont(closeAll.getFont().deriveFont(Font.PLAIN, 12f));
        closeAll.setForeground(TEXT);
        closeAll.setContentAreaFilled(false);
        closeAll.setBorder(new CompoundBorder(new LineBorder(DIM, 1), new EmptyBorder(3, 8, 3, 8)));
        closeAll.addActionListener(e -> all.run());
        closeAll.setVisible(open > 1);
        JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(closeAll);
        buttons.add(button);
        row.add(buttons, BorderLayout.EAST);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        col.add(row);

        root.add(col, BorderLayout.CENTER);
        d.setContentPane(root);
        d.getRootPane().setDefaultButton(button);
        // Width fixed, height from the text (wrapped).
        text.setSize(WIDTH - 40, Short.MAX_VALUE);
        who.setSize(WIDTH - 40, Short.MAX_VALUE);
        d.pack();
        d.setSize(WIDTH, d.getPreferredSize().height);
        return d;
    }

    private static JTextArea area(String s, Color color, float size, int style) {
        JTextArea a = new JTextArea(s); // plain text, never HTML
        a.setEditable(false);
        a.setFocusable(false);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setOpaque(false);
        a.setForeground(color);
        a.setFont(new JLabel().getFont().deriveFont(style, size));
        a.setBorder(null);
        return a;
    }

    private static void add(JPanel col, javax.swing.JComponent c, int gap) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        col.add(c);
        col.add(Box.createVerticalStrut(gap));
    }

    /** Bottom right of the main screen, above the Dock / task bar. */
    private static void place(JDialog d) {
        GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                .getDefaultConfiguration();
        Rectangle b = gc.getBounds();
        Insets in = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        d.setLocation(b.x + b.width - in.right - d.getWidth() - 16, b.y + b.height - in.bottom - d.getHeight() - 16);
    }

    private static volatile boolean motionReduced;
    private static volatile long motionCheckedAt = Long.MIN_VALUE;

    /**
     * "Reduce motion" in the system settings: then the window and the program icon stay yellow instead of blinking.
     * macOS: read from the accessibility settings at most once a minute; elsewhere: not known (blinks).
     */
    static boolean reducedMotion() {
        long now = System.nanoTime();
        if (motionCheckedAt != Long.MIN_VALUE && now - motionCheckedAt < 60_000_000_000L) {
            return motionReduced;
        }
        motionCheckedAt = now;
        if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac")) {
            try {
                Process p = new ProcessBuilder("defaults", "read", "com.apple.universalaccess", "reduceMotion")
                        .redirectErrorStream(true).start();
                if (p.waitFor(1, java.util.concurrent.TimeUnit.SECONDS)) {
                    motionReduced = "1".equals(new String(p.getInputStream().readAllBytes()).trim());
                } else {
                    p.destroy();
                }
            } catch (java.io.IOException | RuntimeException e) {
                // not readable: blinks
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return motionReduced;
    }

    static Component find(java.awt.Container c, String name) {
        for (Component k : c.getComponents()) {
            if (name.equals(k.getName())) {
                return k;
            }
            if (k instanceof java.awt.Container sub) {
                Component f = find(sub, name);
                if (f != null) {
                    return f;
                }
            }
        }
        return null;
    }
}
