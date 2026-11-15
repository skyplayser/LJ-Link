package dev.ljlinkpro;

import java.awt.AWTException;
import java.awt.CheckboxMenuItem;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;

/**
 * Menu bar icon (macOS) / notification area icon (Windows). Offers "open" and "quit"; "quit" first shows the
 * {@link QuitDialog} (feedback and share), unless the user switched it off.
 * The icon is the simplified LJ Link logo (the five faders on the dark tile), drawn in code ({@link Logo#tray}).
 */
final class Tray {
    private Tray() {}

    private static MenuItem openItem;
    private static CheckboxMenuItem privacyItem;
    private static MenuItem quitItem;
    private static MenuItem updateItem;
    private static MenuItem reportItem;
    private static MenuItem chatItem;
    /** Suite chat (Alpha): on, and how many messages arrived since the browser last showed the chat. */
    private static volatile boolean chatOn;
    private static volatile int chatUnread;
    /** An unseen EMERGENCY: the circle blinks yellow (Swing thread only). */
    private static boolean chatUrgent;
    private static javax.swing.Timer blink;
    private static boolean blinkLit;
    private static TrayIcon trayIcon;
    private static volatile Updater.Release available;
    private static volatile boolean installed;

    /** True once the icon is in the menu bar / notification area (then "Quit" can show the {@link QuitDialog}). */
    static boolean installed() {
        return installed;
    }

    /** Menu texts in English (default) and German. */
    static void setLanguage(String lang) {
        boolean de = "de".equals(lang);
        if (openItem != null) {
            openItem.setLabel(de ? "Weboberfläche öffnen" : "Open web interface");
            privacyItem.setLabel(de ? "Privatsphäre-Modus (Titel, Album, Cover ausblenden)"
                    : "Privacy mode (hide title, album, cover)");
            quitItem.setLabel(de ? "Beenden" : "Quit");
            reportItem.setLabel(de ? "Problem melden oder Wunsch äußern …" : "Report a problem or request a feature …");
            updateLabel();
            chatLabel();
        }
    }

    private static void updateLabel() {
        if (updateItem == null) {
            return;
        }
        boolean de = "de".equals(AppSettings.language());
        Updater.Release r = available;
        updateItem.setLabel(r != null
                ? (de ? "Update auf Version " + r.version() + " installieren …" : "Install update to version " + r.version() + " …")
                : (de ? "Nach Updates suchen …" : "Check for updates …") + " (" + Updater.currentVersion() + ")");
    }

    static String chatLabel(boolean de, int unread) {
        String base = de ? "Suite-Chat öffnen (Alpha)" : "Open suite chat (alpha)";
        if (unread <= 0) {
            return base;
        }
        return base + " – " + (unread > 99 ? "99+" : String.valueOf(unread)) + (de ? " neu" : " new");
    }

    private static void chatLabel() {
        if (chatItem != null) {
            chatItem.setLabel(chatLabel("de".equals(AppSettings.language()), chatUnread));
            chatItem.setEnabled(chatOn);
        }
    }

    /**
     * Suite chat changed: the menu item says how many messages are new, and the icon gets a cyan circle with the
     * number (like the chat button in the browser). While an EMERGENCY is unseen the circle blinks yellow, about once
     * a second, until the chat is opened in the browser. Any thread.
     */
    static void chat(boolean on, int unread, boolean urgent) {
        chatOn = on;
        chatUnread = on ? Math.max(0, unread) : 0;
        java.awt.EventQueue.invokeLater(() -> {
            chatUrgent = on && urgent;
            chatLabel();
            // "Reduce motion": steady yellow, no blinking.
            if (chatUrgent && EmergencyWindow.reducedMotion()) {
                if (blink != null) {
                    blink.stop();
                    blink = null;
                }
                blinkLit = true;
            } else if (chatUrgent && blink == null) {
                blinkLit = true;
                blink = new javax.swing.Timer(500, e -> {
                    blinkLit = !blinkLit;
                    paintIcon();
                });
                blink.start();
            } else if (!chatUrgent && blink != null) {
                blink.stop();
                blink = null;
            }
            paintIcon();
        });
    }

    private static void paintIcon() {
        if (trayIcon != null) {
            trayIcon.setImage(icon(chatUnread, chatUrgent && blinkLit));
        }
    }

    static java.awt.image.BufferedImage icon(int unread) {
        return icon(unread, false);
    }

    /**
     * The tray icon, with the unread number of the suite chat at the top right when there is one; {@code yellow}: the
     * circle is yellow (the lit half of the EMERGENCY blinking), also without a number.
     */
    static java.awt.image.BufferedImage icon(int unread, boolean yellow) {
        java.awt.image.BufferedImage img = Logo.tray(64);
        if (unread <= 0 && !yellow) {
            return img;
        }
        java.awt.Graphics2D g = img.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int d = 34;
        g.setColor(new java.awt.Color(0x0D, 0x10, 0x16));
        g.fillOval(64 - d - 2, 0, d + 2, d + 2);
        g.setColor(yellow ? EmergencyWindow.YELLOW : new java.awt.Color(0x22, 0xC8, 0xF0));
        g.fillOval(64 - d - 1, 1, d, d);
        String t = unread <= 0 ? "!" : unread > 9 ? "9+" : String.valueOf(unread);
        g.setColor(java.awt.Color.BLACK);
        g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, t.length() > 1 ? 18 : 24));
        java.awt.FontMetrics fm = g.getFontMetrics();
        g.drawString(t, 64 - d - 1 + (d - fm.stringWidth(t)) / 2, 1 + (d - fm.getHeight()) / 2 + fm.getAscent());
        g.dispose();
        return img;
    }

    /** A newer version was found by the check at start: say so once and offer it in the menu. */
    private static void found(Updater.Release r) {
        boolean first = available == null;
        available = r;
        java.awt.EventQueue.invokeLater(Tray::updateLabel);
        if (first && trayIcon != null) {
            boolean de = "de".equals(AppSettings.language());
            trayIcon.displayMessage("LJ Link", de ? "Version " + r.version() + " ist verfügbar. Installieren über das Menü."
                    : "Version " + r.version() + " is available. Install it from the menu.", TrayIcon.MessageType.INFO);
        }
    }

    /**
     * @param report opens the report window of the web interface with the given stars preset (0 = no rating)
     * @param quit   quits at once; used as is for the quit of an update, the menu item shows {@link QuitDialog} first
     * @return true if the icon was installed; false if the platform has no system tray (then run without one).
     */
    static boolean install(String url, Runnable open, java.util.function.IntConsumer report, Runnable quit) {
        return install(url, open, report, quit, open);
    }

    /** @param openChat opens the web interface with the suite chat open */
    static boolean install(String url, Runnable open, java.util.function.IntConsumer report, Runnable quit,
            Runnable openChat) {
        if (!SystemTray.isSupported()) {
            return false;
        }
        try {
            PopupMenu menu = new PopupMenu();
            openItem = new MenuItem();
            openItem.addActionListener(e -> open.run());
            privacyItem = new CheckboxMenuItem("", Privacy.enabled());
            privacyItem.addItemListener(e -> Privacy.set(privacyItem.getState()));
            Privacy.addListener(() -> privacyItem.setState(Privacy.enabled()));
            MenuItem info = new MenuItem(url);
            info.setEnabled(false);
            quitItem = new MenuItem();
            quitItem.addActionListener(e -> QuitDialog.quitFromMenu(report, quit));
            reportItem = new MenuItem();
            reportItem.addActionListener(e -> report.accept(0));
            chatItem = new MenuItem();
            chatItem.addActionListener(e -> openChat.run());
            updateItem = new MenuItem();
            updateItem.addActionListener(e -> {
                Updater.Release r = available;
                if (r != null) {
                    Thread t = new Thread(() -> {
                        try {
                            UpdateDialog.offer(r, quit);
                        } catch (Exception ex) {
                            System.err.println("Update failed: " + ex);
                        }
                    }, "update");
                    t.setDaemon(true);
                    t.start();
                } else {
                    UpdateDialog.check(false, quit, Tray::found);
                }
            });
            setLanguage(AppSettings.language());
            menu.add(openItem);
            menu.add(privacyItem);
            menu.add(chatItem);
            menu.add(info);
            menu.addSeparator();
            menu.add(updateItem);
            menu.add(reportItem);
            menu.add(quitItem);

            TrayIcon icon = new TrayIcon(icon(chatUnread, chatUrgent), "LJ Link", menu);
            icon.setImageAutoSize(true);
            // The address with its port: not always 8080 (another program may use it, issue #3).
            icon.setToolTip("LJ Link – " + url.replaceFirst("^https?://", ""));
            // Double click on Windows opens the interface; on macOS the menu opens on a single click.
            icon.addActionListener(e -> open.run());
            SystemTray.getSystemTray().add(icon);
            trayIcon = icon;
            installed = true;
            UpdateDialog.checkLater(quit, Tray::found);
            return true;
        } catch (AWTException | RuntimeException e) {
            System.err.println("Tray icon unavailable: " + e);
            return false;
        }
    }
}
