package dev.ljlinkpro;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.function.IntConsumer;

/**
 * Small window shown before quitting from the program icon menu: asks for feedback (optionally with 1-5 stars) and
 * offers the website link to share. Closing it with the window's X cancels (LJ Link keeps running). Never shown for the
 * quit of an update, and never without a screen. Nothing is sent from here: "Give feedback" only opens the report window
 * of the web interface, where the user decides what happens.
 */
final class QuitDialog {
    private QuitDialog() {}

    /** Settings key: true when the user ticked "Don't show this again when quitting". */
    static final String SKIP_KEY = "skipQuitDialog";
    /** The page that is copied by "Share link". */
    static final String SHARE_URL = "https://skyplayser.github.io/LJ-Link/";

    private static JDialog open;

    static boolean skip() {
        return AppSettings.getBoolean(SKIP_KEY, false);
    }

    static void setSkip(boolean skip) {
        AppSettings.set(SKIP_KEY, skip);
    }

    /** The path that opens the report window in the web interface, with the stars preset (0 = no rating). */
    static String reportPath(int stars) {
        return stars >= 1 && stars <= 5 ? "/#report?stars=" + stars : "/#report";
    }

    /** Clicking the star that is already chosen clears the rating; any other star sets it. */
    static int toggle(int current, int clicked) {
        return current == clicked ? 0 : clicked;
    }

    private static String tr(String en, String de) {
        return "de".equals(AppSettings.language()) ? de : en;
    }

    /**
     * Quit from the menu: shows the window, or quits at once when the user switched it off or there is no screen.
     *
     * @param feedback opens the report window of the web interface with the given stars (0 = none); LJ Link stays open
     * @param quit     quits LJ Link as before
     */
    static void quitFromMenu(IntConsumer feedback, Runnable quit) {
        if (skip() || GraphicsEnvironment.isHeadless()) {
            quit.run();
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (open != null && open.isDisplayable()) {
                open.toFront();
                return;
            }
            try {
                open = create(feedback, quit);
                open.setVisible(true);
                open.toFront();
            } catch (RuntimeException e) {
                // A window that cannot be shown must never stop the user from quitting.
                System.err.println("Quit window unavailable: " + e);
                quit.run();
            }
        });
    }

    /** Builds the window (not shown yet). Package-private for the screenshot harness. */
    static JDialog create(IntConsumer feedback, Runnable quit) {
        JDialog dialog = new JDialog((java.awt.Frame) null, "LJ Link", false);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(18, 20, 16, 20));

        JLabel title = new JLabel(tr("Note", "Hinweis"));
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 4f));
        add(panel, title, 10);
        add(panel, text(tr("LJ Link is a community project. It lives on feedback and on being shared.",
                "LJ Link ist ein Community-Projekt. Es lebt von Feedback und davon, dass es geteilt wird.")), 8);
        add(panel, text(tr("Do you like our program, or is something wrong? Report a problem or request a feature, "
                        + "with a rating if you like.",
                "Gefällt dir unser Programm oder stimmt etwas nicht? Melde ein Problem oder äußere einen Wunsch, "
                        + "gern mit einer Bewertung.")), 8);
        add(panel, text(tr("If you like our program, feel free to share it with your friends. Every new user makes us happy.",
                "Wenn dir unser Programm gefällt, teile es gern mit deinen Freunden. Wir freuen uns über jeden Nutzer.")), 12);

        // Rating: five stars, optional. Click a star to choose it, click it again to clear. Each star is a toggle
        // button: the chosen one is selected (screen readers announce it as checked) and its name says "chosen".
        int[] stars = {0};
        JPanel starRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        starRow.add(new JLabel(tr("Your rating (optional):", "Deine Bewertung (optional):")));
        JToggleButton[] starButtons = new JToggleButton[5];
        Runnable paintStars = () -> {
            for (int i = 0; i < 5; i++) {
                int n = i + 1;
                starButtons[i].setIcon(new StarIcon(i < stars[0]));
                starButtons[i].setSelectedIcon(new StarIcon(i < stars[0]));
                starButtons[i].setSelected(n == stars[0]);
                starButtons[i].getAccessibleContext().setAccessibleName(starName(n, n == stars[0]));
            }
        };
        for (int i = 0; i < 5; i++) {
            int n = i + 1;
            JToggleButton b = new JToggleButton();
            b.setBorderPainted(false);
            b.setContentAreaFilled(false);
            b.setFocusPainted(true);
            b.setMargin(new java.awt.Insets(0, 0, 0, 0));
            b.setToolTipText(starName(n, false));
            b.addActionListener(e -> {
                stars[0] = toggle(stars[0], n);
                paintStars.run();
            });
            starButtons[i] = b;
            starRow.add(b);
        }
        paintStars.run();
        add(panel, starRow, 12);

        JCheckBox dontShow = new JCheckBox(tr("Don't show this again when quitting", "Beim Beenden nicht mehr anzeigen"));
        dontShow.addActionListener(e -> setSkip(dontShow.isSelected()));
        add(panel, dontShow, 10);

        JLabel status = new JLabel(" ");
        Timer clear = new Timer(3000, e -> status.setText(" "));
        clear.setRepeats(false);

        JButton feedbackButton = new JButton(tr("Give feedback", "Feedback geben"));
        feedbackButton.setToolTipText(tr("Opens the report window in the web interface. LJ Link keeps running.",
                "Öffnet das Meldefenster in der Weboberfläche. LJ Link läuft weiter."));
        feedbackButton.addActionListener(e -> {
            dialog.dispose();
            feedback.accept(stars[0]);
        });
        JButton share = new JButton(tr("Share link", "Link teilen"));
        share.setToolTipText(SHARE_URL);
        share.addActionListener(e -> {
            try {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(SHARE_URL), null);
                status.setText(tr("Link copied", "Link kopiert") + ": " + SHARE_URL);
            } catch (RuntimeException ex) {
                status.setText(SHARE_URL);
            }
            clear.restart();
        });
        JButton quitButton = new JButton(tr("Quit", "Beenden"));
        quitButton.addActionListener(e -> {
            dialog.dispose();
            quit.run();
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(feedbackButton);
        buttons.add(share);
        buttons.add(quitButton);
        add(panel, buttons, 6);
        add(panel, status, 0);

        // Escape cancels like the window's X.
        panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        panel.getActionMap().put("cancel", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dialog.dispose();
            }
        });

        JPanel root = new JPanel(new BorderLayout());
        root.add(panel, BorderLayout.CENTER);
        dialog.setContentPane(root);
        // Enter quits: that is what the user chose in the menu. "Give feedback" keeps LJ Link running.
        dialog.getRootPane().setDefaultButton(quitButton);
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent e) {
                quitButton.requestFocusInWindow();
            }
        });
        dialog.pack();
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(null);
        dialog.setAlwaysOnTop(true);
        return dialog;
    }

    /** "4 of 5 stars", with ", chosen" for the chosen one (the same words as in the web report window). */
    static String starName(int n, boolean chosen) {
        String name = tr(n + " of 5 stars", n + " von 5 Sternen");
        return chosen ? name + tr(", chosen", ", gewählt") : name;
    }

    private static void add(JPanel panel, JComponent c, int gapBelow) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(c);
        if (gapBelow > 0) {
            panel.add(Box.createVerticalStrut(gapBelow));
        }
    }

    /** A label that wraps at about 380 pixels. */
    private static JLabel text(String s) {
        String html = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        JLabel l = new JLabel("<html><body style='width:380px'>" + html + "</body></html>");
        l.setBorder(BorderFactory.createEmptyBorder());
        return l;
    }

    /** A five-pointed star drawn in code, so it looks the same on every system and font. */
    private static final class StarIcon implements Icon {
        private static final int SIZE = 26;
        private final boolean filled;

        StarIcon(boolean filled) {
            this.filled = filled;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Polygon p = new Polygon();
            double cx = x + SIZE / 2.0, cy = y + SIZE / 2.0 + 1, outer = SIZE / 2.0 - 1, inner = outer * 0.45;
            for (int i = 0; i < 10; i++) {
                double r = i % 2 == 0 ? outer : inner;
                double a = -Math.PI / 2 + i * Math.PI / 5;
                p.addPoint((int) Math.round(cx + r * Math.cos(a)), (int) Math.round(cy + r * Math.sin(a)));
            }
            Color gold = new Color(0xF5B301);
            if (filled) {
                g2.setColor(gold);
                g2.fillPolygon(p);
            }
            g2.setStroke(new BasicStroke(1.5f));
            g2.setColor(filled ? gold.darker() : new Color(0x8A8F98));
            g2.drawPolygon(p);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return SIZE;
        }

        @Override
        public int getIconHeight() {
            return SIZE;
        }
    }
}
