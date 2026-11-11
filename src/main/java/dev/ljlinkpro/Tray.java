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
 * The icon is the LJ Link logo, drawn in code ({@link Logo}).
 */
final class Tray {
    private Tray() {}

    private static MenuItem openItem;
    private static CheckboxMenuItem privacyItem;
    private static MenuItem quitItem;
    private static MenuItem updateItem;
    private static MenuItem reportItem;
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
            menu.add(info);
            menu.addSeparator();
            menu.add(updateItem);
            menu.add(reportItem);
            menu.add(quitItem);

            TrayIcon icon = new TrayIcon(Logo.render(64, true), "LJ Link", menu);
            icon.setImageAutoSize(true);
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
