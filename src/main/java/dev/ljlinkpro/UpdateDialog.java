package dev.ljlinkpro;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/** "Check for updates" from the program icon: asks, downloads with a progress bar, installs and quits. */
final class UpdateDialog {
    private UpdateDialog() {}

    private static final AtomicBoolean RUNNING = new AtomicBoolean();

    private static boolean de() {
        return "de".equals(AppSettings.language());
    }

    private static String tr(String en, String de) {
        return de() ? de : en;
    }

    /** {@code quiet}: only speak up when there is an update (used for the check at start). */
    static void check(boolean quiet, Runnable quit, java.util.function.Consumer<Updater.Release> found) {
        if (!RUNNING.compareAndSet(false, true)) {
            return;
        }
        Thread t = new Thread(() -> {
            try {
                Updater.Release r = Updater.latest();
                String current = Updater.currentVersion();
                if (r == null || !Updater.isNewer(r.version(), current)) {
                    if (!quiet) {
                        message(current.equals("dev")
                                ? tr("This is a development build; updates are only offered to installed versions.",
                                     "Das ist eine Entwicklungsversion; Updates gibt es nur für installierte Versionen.")
                                : tr("LJ-Link-PRO " + current + " is up to date.", "LJ-Link-PRO " + current + " ist aktuell."));
                    }
                    return;
                }
                if (found != null) {
                    found.accept(r);
                }
                if (quiet) {
                    return;
                }
                offer(r, quit);
            } catch (Exception e) {
                if (!quiet) {
                    message(tr("Could not check for updates: ", "Updates konnten nicht geprüft werden: ") + e.getMessage());
                }
            } finally {
                RUNNING.set(false);
            }
        }, "update-check");
        t.setDaemon(true);
        t.start();
    }

    /** Asks whether to install {@code r}; on yes downloads, installs and quits. Runs on a background thread. */
    static void offer(Updater.Release r, Runnable quit) throws Exception {
        int[] answer = new int[1];
        SwingUtilities.invokeAndWait(() -> answer[0] = JOptionPane.showConfirmDialog(null,
                tr("Version " + r.version() + " is available (installed: " + Updater.currentVersion() + ").\n"
                                + "Install now? LJ-Link-PRO quits and starts again with the new version.",
                        "Version " + r.version() + " ist verfügbar (installiert: " + Updater.currentVersion() + ").\n"
                                + "Jetzt installieren? LJ-Link-PRO beendet sich und startet mit der neuen Version neu."),
                "LJ-Link-PRO", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE));
        if (answer[0] != JOptionPane.YES_OPTION) {
            return;
        }
        JProgressBar bar = new JProgressBar(0, 1000);
        bar.setStringPainted(true);
        JDialog[] dialog = new JDialog[1];
        SwingUtilities.invokeAndWait(() -> {
            JPanel panel = new JPanel(new BorderLayout(0, 10));
            panel.setBorder(new EmptyBorder(16, 16, 16, 16));
            panel.add(new JLabel(tr("Downloading version " + r.version() + " …", "Lade Version " + r.version() + " herunter …")), BorderLayout.NORTH);
            panel.add(bar, BorderLayout.CENTER);
            dialog[0] = new JDialog((java.awt.Frame) null, "LJ-Link-PRO", false);
            dialog[0].setContentPane(panel);
            dialog[0].setSize(380, 120);
            dialog[0].setLocationRelativeTo(null);
            dialog[0].setAlwaysOnTop(true);
            dialog[0].setVisible(true);
        });
        try {
            Path file = Updater.download(r, p -> SwingUtilities.invokeLater(() -> bar.setValue((int) (p * 1000))));
            Updater.install(file);
            SwingUtilities.invokeLater(() -> dialog[0].dispose());
            if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
                message(tr("The installer starts now. Follow it; LJ-Link-PRO quits.", "Der Installer startet jetzt. Folge ihm; LJ-Link-PRO beendet sich."));
            }
            quit.run();
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> dialog[0].dispose());
            message(tr("The update failed: ", "Das Update ist fehlgeschlagen: ") + e.getMessage() + "\n" + r.page());
        }
    }

    private static void message(String text) {
        try {
            SwingUtilities.invokeAndWait(() -> JOptionPane.showMessageDialog(null, text, "LJ-Link-PRO", JOptionPane.INFORMATION_MESSAGE));
        } catch (Exception e) {
            System.out.println(text);
        }
    }

    /** Run once a little after the start: tells the icon when a newer version exists. */
    static void checkLater(Runnable quit, java.util.function.Consumer<Updater.Release> found) {
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(15_000);
            } catch (InterruptedException e) {
                return;
            }
            check(true, quit, found);
        }, "update-check-later");
        t.setDaemon(true);
        t.start();
    }
}
