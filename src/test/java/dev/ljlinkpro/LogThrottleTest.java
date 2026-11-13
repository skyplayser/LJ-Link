package dev.ljlinkpro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** A warning with a stack trace for every beat packet (issue #2) is shown three times a minute, then counted. */
class LogThrottleTest {
    private static final String HEAD = "[beat-link BeatFinder receiver] WARN org.deepsymmetry.beatlink.BeatFinder - Problem delivering beat announcement to TimeFinder listener";
    private static final List<String> BLOCK = List.of(HEAD,
            "java.lang.NullPointerException: Cannot read field \"precise\" because \"lastPosition\" is null",
            "\tat org.deepsymmetry.beatlink.data.TimeFinder.lambda$new$1(TimeFinder.java:600)",
            "\tat org.deepsymmetry.beatlink.BeatFinder.deliverBeat(BeatFinder.java:353)",
            "\tat java.base/java.lang.Thread.run(Unknown Source)");

    private static List<String> feed(LogBuffer.Throttle t, List<String> lines, long now) {
        List<String> out = new ArrayList<>();
        for (String l : lines) {
            out.addAll(t.accept(l, now));
        }
        return out;
    }

    @Test
    void aFloodIsShownThreeTimesAndCounted() {
        LogBuffer.Throttle t = new LogBuffer.Throttle();
        List<String> out = new ArrayList<>();
        long now = 1_000_000;
        for (int i = 0; i < 100; i++) {
            out.addAll(feed(t, BLOCK, now + i * 500));
            out.addAll(t.accept("[beat-link BeatFinder receiver] WARN org.deepsymmetry.beatlink.BeatFinder - Problem delivering beat announcement to listener", now + i * 500));
            out.addAll(t.accept("java.lang.NullPointerException", now + i * 500));
        }
        assertEquals(3 * BLOCK.size() + 3 * 2, out.size(), out.toString());
        List<String> notes = t.pending(now + 50_000);
        assertEquals(2, notes.size());
        assertTrue(notes.stream().anyMatch(n -> n.startsWith("[log] repeated 97 more times") && n.endsWith(HEAD)), notes.toString());

        // After the minute the message is shown again, with a note how often it was left out.
        List<String> later = feed(t, BLOCK, now + 61_000);
        assertTrue(later.get(0).startsWith("[log] repeated 97 more times"), later.toString());
        assertEquals(BLOCK, later.subList(1, later.size()));
    }

    @Test
    void differentMessagesAndStackTracesOfShownMessagesStay() {
        LogBuffer.Throttle t = new LogBuffer.Throttle();
        for (int i = 0; i < 10; i++) {
            String line = "Player number " + i + " was refused";
            assertEquals(List.of(line), t.accept(line, 1000 + i));
            // The same stack line after different messages is part of each of them.
            assertEquals(1, t.accept("\tat java.base/java.lang.Thread.run(Unknown Source)", 1000 + i).size());
        }
        assertTrue(t.pending(2000).isEmpty());
        assertTrue(LogBuffer.Throttle.traceLine("Caused by: java.io.IOException: closed"));
        assertTrue(LogBuffer.Throttle.exceptionLine("java.lang.NullPointerException"));
        assertTrue(!LogBuffer.Throttle.exceptionLine("Pro DJ Link failed: java.net.BindException"));
    }

    @Test
    void aForeignExceptionAfterALeftOutMessageIsKept() {
        // QS round 2: 5x the warning with its NPE, then another program's exception; it must not vanish.
        LogBuffer.Throttle t = new LogBuffer.Throttle();
        String warn = "[beat-link BeatFinder receiver] WARN org.deepsymmetry.beatlink.BeatFinder - Problem delivering beat announcement to listener";
        for (int i = 0; i < 5; i++) {
            t.accept(warn, 1000 + i);
            t.accept("java.lang.NullPointerException", 1000 + i);
        }
        assertEquals(List.of("java.io.IOException: Disk full"), t.accept("java.io.IOException: Disk full", 2000));
        assertEquals(List.of("\tat java.base/java.io.FileOutputStream.write(Unknown Source)"),
                t.accept("\tat java.base/java.io.FileOutputStream.write(Unknown Source)", 2000), "its stack trace too");

        // Directly after a left-out message: another exception than the one it had is kept as well.
        t.accept(warn, 3000);
        assertEquals(List.of("java.lang.IllegalStateException: closed"), t.accept("java.lang.IllegalStateException: closed", 3000));
        t.accept(warn, 3001);
        assertTrue(t.accept("java.lang.NullPointerException", 3001).isEmpty(), "its own exception is left out with it");

        // A plain line after a stack trace is a message again, not part of the trace.
        assertEquals(List.of("Connected"), t.accept("Connected", 3002));
        assertTrue(t.pending(4000).get(0).startsWith("[log] repeated 4 more times"), t.pending(4000).toString());
    }
}
