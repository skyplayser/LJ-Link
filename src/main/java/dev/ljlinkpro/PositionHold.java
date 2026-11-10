package dev.ljlinkpro;

import java.util.function.LongSupplier;

/**
 * Bridges short gaps in the position of one Pro DJ Link player (ADR-024).
 * <p>
 * beat-link's {@code TimeFinder.getTimeFor} returns -1 whenever it has no position entry for the player; it drops the
 * entry on a status packet without beat grid or without beat number, and on a beat packet without beat grid (checked in
 * the beat-link 8.0.0 sources). The beat-grid fallback is only accurate to the last beat (up to half a second off).
 * Switching between the two frame by frame made the waveform jump back and forth, and a single -1 stopped the measured
 * speed. So after a good position a -1 is bridged: for up to {@link #GRACE_NANOS} the last good position is carried on
 * at the deck's speed (held while paused). Only then does the beat-grid fallback take over, until TimeFinder knows the
 * position again. Without a track, after another track was loaded, or before TimeFinder ever knew the position (for
 * example when the beat port is taken by another program), the fallback is used at once, as before.
 */
final class PositionHold {
    /** How long a missing TimeFinder position is bridged by carrying on the last good one. */
    static final long GRACE_NANOS = 1_500_000_000L;

    private boolean have;
    private double ms;
    private long atNanos;
    private int trackVersion;
    private boolean missing;
    private long missingSince;

    /**
     * The position to show now.
     *
     * @param timeFinderMs  TimeFinder's position, or -1 when it does not know it
     * @param nowNanos      {@link System#nanoTime()} now
     * @param hasTrack      whether a track is loaded
     * @param trackVersion  the deck's {@link Deck#trackVersion}; another value means another track
     * @param ratePerMs     track milliseconds per real millisecond to carry the last good position on (0 while paused)
     * @param fallback      the beat-grid position (or -1), asked only when it is used
     */
    synchronized long next(long timeFinderMs, long nowNanos, boolean hasTrack, int trackVersion, double ratePerMs,
                           LongSupplier fallback) {
        if (timeFinderMs >= 0) {
            have = true;
            ms = timeFinderMs;
            atNanos = nowNanos;
            this.trackVersion = trackVersion;
            missing = false;
            return timeFinderMs;
        }
        if (!have || !hasTrack || trackVersion != this.trackVersion) {
            have = false;
            return fallback.getAsLong();
        }
        if (!missing) {
            missing = true;
            missingSince = nowNanos;
        }
        if (nowNanos - missingSince > GRACE_NANOS) {
            have = false; // a real loss: the fallback takes over until TimeFinder knows the position again
            return fallback.getAsLong();
        }
        ms = Math.max(0, ms + ratePerMs * ((nowNanos - atNanos) / 1e6));
        atNanos = nowNanos;
        return Math.round(ms);
    }

    /** True while a missing position is being bridged (for tests and diagnostics). */
    synchronized boolean bridging() {
        return have && missing;
    }
}
