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

    /** The case of the Wolfmix test: the true tempo lies on the rounding edge and the readings swing around it. */
    @Test
    void aTempoSwingingAroundTheRoundingEdgeNeverChanges() {
        for (double start : new double[] {137.90, 137.95, 138.00}) {
            Deck d = new Deck(1);
            d.effectiveBpm = start;
            long t = 0;
            double first = d.stableBpm(t);
            java.util.Random random = new java.util.Random(7);
            // Five minutes of status packets every 200 ms, with the outputs asking every 10 ms in between.
            for (int i = 0; i < 1500; i++) {
                d.effectiveBpm = 137.95 + (random.nextDouble() * 2 - 1) * 0.05;
                for (int k = 0; k < 20; k++) {
                    t += S / 100;
                    assertEquals(first, d.stableBpm(t), 1e-9, "137.95 +- 0.05 must never change the sent tempo (start " + start + ")");
                }
            }
            assertEquals(0, d.stableChanges());
        }
    }

    @Test
    void aPitchJumpOfOneBpmGoesThroughWithinASecond() {
        Deck d = new Deck(1);
        d.effectiveBpm = 128.0;
        assertEquals(128.0, d.stableBpm(0));
        long t = 0;
        while (t < 2 * S) {
            t += S / 100;
            d.stableBpm(t);
        }
        long jumpAt = t;
        d.effectiveBpm = 129.0;
        long tookAt = -1;
        while (t < jumpAt + 2 * S) {
            t += S / 100;
            if (d.stableBpm(t) == 129.0) {
                tookAt = t;
                break;
            }
        }
        assertTrue(tookAt > 0 && tookAt - jumpAt <= S, "a jump of +1 BPM is sent within 1 s, took " + (tookAt - jumpAt) / 1e6 + " ms");
        assertEquals(1, d.stableChanges(), "in one step, without values in between");
    }

    @Test
    void aMovingFaderIsFollowedAndAShortNudgeIsNot() {
        Deck d = new Deck(1);
        d.effectiveBpm = 128.0;
        d.stableBpm(0);
        long t = 0;
        // A nudge: 2 BPM faster for 150 ms, then back.
        d.effectiveBpm = 130.0;
        for (int i = 0; i < 15; i++) {
            t += S / 100;
            assertEquals(128.0, d.stableBpm(t), "a short nudge is no tempo change");
        }
        d.effectiveBpm = 128.0;
        for (int i = 0; i < 300; i++) {
            t += S / 100;
            assertEquals(128.0, d.stableBpm(t), "and leaves nothing behind");
        }
        // The fader moves on steadily, 1 BPM per second: the sent tempo follows within the longest wait.
        for (int i = 0; i < 300; i++) {
            t += S / 100;
            d.effectiveBpm = 128.0 + i / 100.0;
            double sent = d.stableBpm(t);
            if (i > 100) {
                assertTrue(d.effectiveBpm - sent < 1.0, "follows a moving fader, sent " + sent + " at " + d.effectiveBpm);
            }
        }
    }

    @Test
    void aSlowDriftBeyondTheBandIsTaken() {
        Deck d = new Deck(1);
        d.effectiveBpm = 137.95;
        long t = 0;
        double first = d.stableBpm(t);
        java.util.Random random = new java.util.Random(3);
        // 0.01 BPM per second for 40 s, with jitter: from 137.95 to 138.35.
        for (int i = 0; i < 4000; i++) {
            t += S / 100;
            d.effectiveBpm = 137.95 + i / 10000.0 + (random.nextDouble() * 2 - 1) * 0.03;
            d.stableBpm(t);
        }
        double last = d.stableBpm(t);
        assertTrue(last - first > 0.15, "a drift of 0.4 BPM is taken over, sent " + last + " after " + first);
        assertTrue(Math.abs(last - 138.35) <= Deck.STABLE_BAND + 0.05, "and the sent tempo is near the true one: " + last);
    }

    @Test
    void aRealChangeGoesThroughQuicklyAndATenthIsKeptBack() {
        Deck d = new Deck(1);
        d.effectiveBpm = 128.0;
        assertEquals(128.0, d.stableBpm(0));
        d.effectiveBpm = 130.0;
        assertEquals(128.0, d.stableBpm(S / 10), "a jump waits until the tempo settles");
        assertEquals(130.0, d.stableBpm(S / 10 + Deck.STABLE_SETTLE_NANOS), "then it goes through");
        d.effectiveBpm = 130.1;
        assertEquals(130.0, d.stableBpm(S), "a tenth stays in the band");
        assertEquals(130.0, d.stableBpm(60 * S), "for good");
        d.effectiveBpm = 0;
        assertEquals(0.0, d.stableBpm(61 * S), "no tempo, nothing sent");
        d.effectiveBpm = 125.0;
        assertEquals(125.0, d.stableBpm(62 * S), "a new tempo after none is taken at once");
    }

    @Test
    void aTypedTempoGoesOutUnfiltered() {
        Deck d = new Deck(1);
        d.exactTempo = true;
        d.effectiveBpm = 120.0;
        assertEquals(120.0, d.stableBpm(0));
        d.effectiveBpm = 120.1;
        assertEquals(120.1, d.stableBpm(S / 100), "a typed step of a tenth is sent at once");
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
            assertEquals(138.0, b.bpm(), 1e-9, "every beat carries the same tempo");
            if (b.change()) {
                changes++;
            }
            t += 435_000_000L;
        }
        assertEquals(1, changes, "only the first beat is a change");
    }

    @Test
    void anAbletonLinkSessionTempoGoesOutInTenths() {
        Deck d = new Deck(1);
        LinkTempoSource.apply(d, new AbletonLink.State(120.0, true, 1));
        assertEquals(120.0, d.stableBpm(0));
        LinkTempoSource.apply(d, new AbletonLink.State(120.1, true, 1));
        assertEquals(120.1, d.stableBpm(S / 4), "a step of a tenth in another Link app is passed on at once");
        LinkTempoSource.apply(d, new AbletonLink.State(120.2, true, 1));
        assertEquals(120.2, d.stableBpm(S / 2));
        assertEquals(2, d.stableChanges());
    }

    /** System.nanoTime() has an arbitrary origin and may be negative; no time may serve as a marker. */
    @Test
    void aJumpIsTakenAlsoWhenTheClockIsNegative() {
        Deck d = new Deck(1);
        long t = -50 * S;
        d.effectiveBpm = 128.0;
        assertEquals(128.0, d.stableBpm(t));
        d.effectiveBpm = 130.0;
        long jumpAt = t;
        double sent = 128.0;
        while (t < jumpAt + S && sent != 130.0) {
            t += S / 100;
            sent = d.stableBpm(t);
        }
        assertEquals(130.0, sent, "a jump goes through with a negative clock too");
        assertTrue(t - jumpAt <= S / 2, "and as quickly as with a positive one: " + (t - jumpAt) / 1e6 + " ms");
    }

    @Test
    void aNewTrackOrATempoAfterNoneIsTakenAtOnce() {
        Deck d = new Deck(1);
        d.hasTrack = true;
        d.effectiveBpm = 128.0;
        long t = 0;
        assertEquals(128.0, d.stableBpm(t));
        // The tempo goes to 0 (track unloaded, no BPM known) and is asked for meanwhile, as the display tick does.
        d.effectiveBpm = 0;
        t += S / 10;
        d.toJson(false);
        assertEquals(0.0, d.sentBpm(), "no tempo resets the sent one");
        d.effectiveBpm = 140.0;
        assertEquals(140.0, d.stableBpm(t + S / 10), "the first tempo after none goes out without waiting");
        // Another track with another tempo: no settling time.
        t += S;
        d.trackVersion++;
        d.effectiveBpm = 124.0;
        assertEquals(124.0, d.stableBpm(t), "a new track goes out at once");
        // The tempo of the new track may come a moment after the track.
        t += S;
        d.trackVersion++;
        assertEquals(124.0, d.stableBpm(t));
        d.effectiveBpm = 126.0;
        assertEquals(126.0, d.stableBpm(t + S / 10), "also when its tempo comes a moment later");
        // Not asked for a long time (the deck was not followed): the old jump is taken at once as well.
        d.effectiveBpm = 131.0;
        assertEquals(131.0, d.stableBpm(t + 10 * S));
        // A new track with a tempo within the band keeps the sent tempo (no flip on the rounding edge).
        d.trackVersion++;
        d.effectiveBpm = 131.08;
        assertEquals(131.0, d.stableBpm(t + 10 * S + S / 10));
    }

    @Test
    void aTrackChangeOnTheLeadingDeckIsOneChangeForOs2l() {
        Deck d = new Deck(1);
        d.hasTrack = true;
        d.playing = true;
        d.velocity = 1.0;
        d.effectiveBpm = 128.0;
        Os2lBeats beats = new Os2lBeats();
        long t = 10 * S;
        int changes = 0;
        for (int i = 0; i < 12; i++) {
            if (i == 4) {
                // Another track with another tempo, its beat grid arrives a beat later.
                d.trackVersion++;
                d.effectiveBpm = 140.0;
            }
            if (i == 6) {
                d.setBeats(new int[] {0, 500, 1000}, new byte[] {1, 2, 3});
            }
            d.beatPacketInBar = i % 4 + 1;
            d.beatPacketNanos = t;
            d.beatPacketCount++;
            Os2lBeats.Beat b = beats.next(d, t + 1_000_000);
            assertNotNull(b);
            assertEquals(i < 4 ? 128.0 : 140.0, b.bpm(), 1e-9, "beat " + i + " carries the tempo of its track at once");
            if (b.change()) {
                changes++;
                assertTrue(i == 0 || i == 4, "change only at the start and at the new track, not at beat " + i);
            }
            t += S / 2;
        }
        assertEquals(2, changes, "the start and the new track, each once");
    }
}
