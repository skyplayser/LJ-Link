package dev.ljlinkpro;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SleepGuardTest {

    /** A process that only records whether it was ended. */
    static final class FakeProcess extends Process {
        volatile boolean alive = true;

        @Override public OutputStream getOutputStream() { return new ByteArrayOutputStream(); }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public InputStream getErrorStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public int waitFor() { return 0; }
        @Override public int exitValue() { return 0; }
        @Override public void destroy() { alive = false; }
        @Override public boolean isAlive() { return alive; }
    }

    final List<List<String>> started = new ArrayList<>();
    final List<FakeProcess> processes = new ArrayList<>();

    SleepGuard guard(String os, long delayMs) {
        return new SleepGuard(os, 4711, cmd -> {
            started.add(cmd);
            FakeProcess p = new FakeProcess();
            processes.add(p);
            return p;
        }, delayMs);
    }

    @Test
    void commandOnlyOnMac() {
        assertEquals(List.of("/usr/bin/caffeinate", "-i", "-w", "4711"), SleepGuard.command("Mac OS X", 4711));
        assertNull(SleepGuard.command("Windows 11", 4711));
        assertNull(SleepGuard.command("Linux", 4711));
        assertNull(SleepGuard.command(null, 4711));
    }

    @Test
    void startsOnceForSeveralBrowsersAndStopsWhenTheLastLeaves() {
        try (SleepGuard g = guard("Mac OS X", 0)) {
            g.clients(1);
            g.clients(2);
            assertEquals(1, started.size());
            assertTrue(g.active());
            g.clients(1);
            assertTrue(g.active());
            g.clients(0);
            assertFalse(g.active());
            assertFalse(processes.get(0).alive);
            g.clients(1);
            assertEquals(2, started.size());
        }
        assertFalse(processes.get(1).alive, "close ends the process");
    }

    @Test
    void shortGapKeepsTheGuard() throws Exception {
        try (SleepGuard g = guard("Mac OS X", 200)) {
            g.clients(1);
            g.clients(0);
            assertTrue(g.active(), "still active during the grace time");
            g.clients(1);
            Thread.sleep(400);
            assertTrue(g.active(), "a reconnect cancels the release");
            assertEquals(1, started.size());
            g.clients(0);
            Thread.sleep(400);
            assertFalse(g.active(), "released after the grace time");
        }
    }

    @Test
    void doesNothingOnOtherSystems() {
        try (SleepGuard g = guard("Windows 11", 0)) {
            assertFalse(g.supported());
            g.clients(1);
            assertFalse(g.active());
            assertTrue(started.isEmpty());
        }
    }

    @Test
    void nothingStartsAfterClose() {
        SleepGuard g = guard("Mac OS X", 0);
        g.close();
        g.clients(1);
        assertTrue(started.isEmpty());
    }

    @Test
    void lateReleaseTimerDoesNotEndTheGuardOfANewBrowser() {
        // Timer fired and waits for the lock while a browser connects: its run must do nothing afterwards.
        try (SleepGuard g = guard("Mac OS X", 3_600_000)) {
            g.clients(1);
            g.clients(0);
            long scheduledFor = g.generation();
            g.clients(1);
            g.releaseIfStill(scheduledFor);
            assertTrue(g.active(), "a browser is connected again");
            assertEquals(1, started.size());
            g.clients(0);
            g.releaseIfStill(scheduledFor);
            assertTrue(g.active(), "an old timer never releases a newer gap");
            g.releaseIfStill(g.generation());
            assertFalse(g.active(), "the current timer releases");
        }
    }
}
