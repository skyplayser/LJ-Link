package dev.ljlinkpro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StableBeatsTest {
    private static final long S = 1_000_000_000L;

    @Test
    void aJitterOfATenthIsNotPassedOn() {
        Deck d = new Deck(1);
        long t = 0;
        double[] raw = {137.95, 137.94, 138.02, 137.91, 137.96, 137.99, 137.93};
        d.effectiveBpm = raw[0];
        double first = d.stableBpm(t);
        for (double r : raw) {
            d.effectiveBpm = r;
            t += S / 2;
            assertEquals(first, d.stableBpm(t), 1e-9, "jitter between 137.9 and 138.0 must not change the sent tempo");
        }
    }

    @Test
    void aRealChangeGoesThroughAtOnceAndASmallOneAfterItLasts() {
        Deck d = new Deck(1);
        d.effectiveBpm = 128.0;
        assertEquals(128.0, d.stableBpm(0));
        d.effectiveBpm = 130.0;
        assertEquals(130.0, d.stableBpm(S / 10), "a jump of 2 BPM goes through at once");
        d.effectiveBpm = 130.1;
        assertEquals(130.0, d.stableBpm(S), "a tenth is held back first");
        assertEquals(130.1, d.stableBpm(S + Deck.STABLE_HOLD_NANOS), "and taken when it lasts");
    }

    @Test
    void beatPacketsGiveOneBeatEachAlignedToTheBar() {
        Deck d = new Deck(1);
        d.hasTrack = true;
        d.playing = true;
        d.velocity = 1.0;
        d.effectiveBpm = 120.0;
        Os2lBeats beats = new Os2lBeats();
        long t = 10 * S;
        int[] inBar = {3, 4, 1, 2};
        long[] expected = {2, 3, 4, 5};
        for (int i = 0; i < inBar.length; i++) {
            d.beatPacketInBar = inBar[i];
            d.beatPacketNanos = t;
            d.beatPacketCount++;
            Os2lBeats.Beat b = beats.next(d, t + 1_000_000);
            assertNotNull(b);
            assertEquals(expected[i], b.pos(), "beat " + inBar[i] + " of the bar");
            if (i == 0) {
                assertTrue(b.change(), "the first beat sets the phase");
            } else {
                assertFalse(b.change(), "later beats with a steady tempo are no change");
            }
            // Polled again before the next packet: no second beat (no double flash).
            assertNull(beats.next(d, t + 100_000_000));
            assertNull(beats.next(d, t + 400_000_000));
            t += S / 2;
        }
    }

    @Test
    void aJitteringTempoDoesNotFlagAChange() {
        Deck d = new Deck(1);
        d.hasTrack = true;
        d.playing = true;
        d.velocity = 1.0;
        Os2lBeats beats = new Os2lBeats();
        long t = 10 * S;
        double[] raw = {137.95, 137.9, 138.0, 137.9, 138.0, 137.94};
        int changes = 0;
        for (int i = 0; i < raw.length; i++) {
            d.effectiveBpm = raw[i];
            d.beatPacketInBar = i % 4 + 1;
            d.beatPacketNanos = t;
            d.beatPacketCount++;
            Os2lBeats.Beat b = beats.next(d, t + 1_000_000);
            assertNotNull(b);
            if (b.change()) {
                changes++;
            }
            t += 435_000_000L;
        }
        assertEquals(1, changes, "only the first beat is a change");
    }
}
