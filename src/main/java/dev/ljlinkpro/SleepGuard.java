package dev.ljlinkpro;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Keeps the computer itself from falling asleep while at least one browser shows LJ Link (for example an iPad in the
 * booth while the Mac lid stays open but nobody touches it). Only on macOS, with the system tool {@code caffeinate -i
 * -w <pid>}: it prevents idle system sleep and ends by itself when this process ends, even after a crash.
 *
 * <p>The display is not kept on here: the page does that in the browser that shows it (Screen Wake Lock or the video
 * fallback), so a Mac display is only kept on when LJ Link is really shown on it. See ADR-022.
 *
 * <p>Short gaps (a browser that reconnects, a page reload) do not end the guard: it is released only after
 * {@code releaseDelayMs} without any browser.
 */
final class SleepGuard implements AutoCloseable {

    /** Starts a child process; replaced in tests. */
    interface Launcher {
        Process start(List<String> command) throws IOException;
    }

    static final long DEFAULT_RELEASE_DELAY_MS = 60_000;

    private final List<String> command;
    private final Launcher launcher;
    private final long releaseDelayMs;
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sleep-guard");
        t.setDaemon(true);
        return t;
    });

    private Process process;
    private ScheduledFuture<?> pendingRelease;
    private boolean closed;
    private int lastCount;
    private long generation;

    SleepGuard(String osName, long pid, Launcher launcher, long releaseDelayMs) {
        this.command = command(osName, pid);
        this.launcher = launcher;
        this.releaseDelayMs = releaseDelayMs;
    }

    /** The guard for this process, using the real {@code caffeinate}. */
    static SleepGuard forThisProcess() {
        return new SleepGuard(System.getProperty("os.name", ""), ProcessHandle.current().pid(),
                cmd -> new ProcessBuilder(cmd).redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD).start(),
                DEFAULT_RELEASE_DELAY_MS);
    }

    /** The command that keeps the system awake on this OS, or null when there is none (everything but macOS). */
    static List<String> command(String osName, long pid) {
        String os = osName == null ? "" : osName.toLowerCase(java.util.Locale.ROOT);
        if (os.contains("mac") || os.contains("darwin")) {
            return List.of("/usr/bin/caffeinate", "-i", "-w", Long.toString(pid));
        }
        return null;
    }

    /** True when this OS is supported at all. */
    boolean supported() {
        return command != null;
    }

    /** True while the keep-awake process runs. */
    synchronized boolean active() {
        return process != null && process.isAlive();
    }

    /**
     * Called with the number of connected browsers whenever it changes. Every call raises a generation number, so a
     * release timer that already fired but waits for the lock does nothing once a browser came back in between.
     */
    synchronized void clients(int count) {
        if (closed || command == null) {
            return;
        }
        lastCount = count;
        generation++;
        if (count > 0) {
            if (pendingRelease != null) {
                pendingRelease.cancel(false);
                pendingRelease = null;
            }
            acquire();
        } else if (process != null && pendingRelease == null) {
            if (releaseDelayMs <= 0) {
                release();
            } else {
                long scheduledFor = generation;
                pendingRelease = timer.schedule(() -> releaseIfStill(scheduledFor), releaseDelayMs, TimeUnit.MILLISECONDS);
            }
        }
    }

    /** The current generation (for tests). */
    synchronized long generation() {
        return generation;
    }

    /** Releases only when nothing changed since the release was scheduled and still no browser is connected. */
    synchronized void releaseIfStill(long scheduledFor) {
        if (scheduledFor != generation || lastCount > 0) {
            return;
        }
        pendingRelease = null;
        release();
    }

    private void acquire() {
        if (process != null && process.isAlive()) {
            return;
        }
        try {
            process = launcher.start(command);
            System.out.println("Keeping the computer awake while a browser shows LJ Link (caffeinate).");
        } catch (IOException | RuntimeException e) {
            process = null;
            System.out.println("Could not keep the computer awake: " + e.getMessage());
        }
    }

    private void release() {
        if (process != null) {
            process.destroy();
            process = null;
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        if (pendingRelease != null) {
            pendingRelease.cancel(false);
            pendingRelease = null;
        }
        release();
        timer.shutdownNow();
    }
}
