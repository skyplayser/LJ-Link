package dev.ljlinkpro;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Short gaps of TimeFinder's position are bridged instead of switching to the beat grid and back (ADR-024). */
class PositionHoldTest {
    private static final long MS = 1_000_000L;
    /** The beat-grid fallback: on the last beat, here 600 ms away from the true position. */
    private static final long GRID = 9_400;

    @Test
    void aShortGapCarriesTheLastPositionOnInsteadOfJumpingToTheBeatGrid() {
        PositionHold h = new PositionHold();
        assertEquals(10_000, h.next(10_000, 0, true, 1, 1.0, () -> GRID));
        assertEquals(10_020, h.next(-1, 20 * MS, true, 1, 1.0, () -> GRID), "carried on at the speed, not the beat grid");
        assertTrue(h.bridging());
        assertEquals(10_040, h.next(-1, 40 * MS, true, 1, 1.0, () -> GRID));
        assertEquals(10_060, h.next(10_060, 60 * MS, true, 1, 1.0, () -> GRID), "TimeFinder is back");
        assertFalse(h.bridging());
    }

    @Test
    void aLongGapFallsBackToTheBeatGrid() {
        PositionHold h = new PositionHold();
        h.next(10_000, 0, true, 1, 1.0, () -> GRID);
        h.next(-1, 20 * MS, true, 1, 1.0, () -> GRID);
        long t = 20 * MS + PositionHold.GRACE_NANOS;
        assertEquals(11_520, h.next(-1, t, true, 1, 1.0, () -> GRID), "still bridged at the end of the grace period");
        assertEquals(GRID, h.next(-1, t + 20 * MS, true, 1, 1.0, () -> GRID), "a real loss: the beat grid takes over");
        assertEquals(GRID, h.next(-1, t + 40 * MS, true, 1, 1.0, () -> GRID), "and stays until TimeFinder is back");
    }

    @Test
    void aPausedDeckIsHeld() {
        PositionHold h = new PositionHold();
        h.next(5_000, 0, true, 1, 0, () -> GRID);
        assertEquals(5_000, h.next(-1, 500 * MS, true, 1, 0, () -> GRID));
    }

    @Test
    void noBridgingWithoutTrackAfterAnotherTrackOrWithoutAnyGoodPosition() {
        PositionHold h = new PositionHold();
        assertEquals(GRID, h.next(-1, 0, true, 1, 1.0, () -> GRID), "TimeFinder never knew it (beat port taken): as before");
        h.next(10_000, 20 * MS, true, 1, 1.0, () -> GRID);
        assertEquals(-1, h.next(-1, 40 * MS, false, 1, 1.0, () -> -1), "track ejected");
        h.next(10_000, 60 * MS, true, 1, 1.0, () -> GRID);
        assertEquals(GRID, h.next(-1, 80 * MS, true, 2, 1.0, () -> GRID), "another track: the old position must not run on");
    }

    @Test
    void deckSpeedSurvivesTheBridgedGap() {
        Deck d = new Deck(1);
        d.playing = true;
        d.speed = 1.0;
        PositionHold h = new PositionHold();
        long now = 0;
        for (int i = 0; i < 20; i++) {
            now += 20 * MS;
            d.samplePosition(h.next(10_000 + i * 20L, now, true, 0, 1.0, () -> GRID), now);
        }
        assertEquals(1.0, d.velocity, 1e-9);
        for (int i = 0; i < 5; i++) {
            now += 20 * MS;
            d.samplePosition(h.next(-1, now, true, 0, d.velocity, () -> GRID), now);
        }
        assertEquals(1.0, d.velocity, 1e-9, "a short -1 no longer stops the deck");
        assertTrue(d.positionMs > 10_000);
    }
}
