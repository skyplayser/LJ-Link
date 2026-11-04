package dev.ljlinkpro;

/**
 * Turns the state of the leading deck into OS2L beats: one event when a beat of the track passes. The beat comes from the
 * beat grid and the predicted position when the deck has both; a deck without a position (Serato, Ableton Link) runs freely
 * at its tempo instead. {@code pos} counts the beats of the track so that it is a multiple of 4 on the first beat of a bar.
 */
final class Os2lBeats {
    record Beat(boolean change, long pos, double bpm) {}

    /** A tempo jump of this many BPM counts as a change for the lighting program. */
    private static final double CHANGE_BPM = 0.5;

    private String deckKey;
    private int trackVersion = -1;
    private long lastBeat = Long.MIN_VALUE;
    private double lastBpm;
    private long lastPosition = -1;
    /** Free-running mode: when the next beat is due. */
    private long nextFreeNanos;
    private long freePos;
    private boolean pendingChange = true;

    void reset() {
        deckKey = null;
        lastBeat = Long.MIN_VALUE;
        pendingChange = true;
    }

    /** The beat due now, or null. Called every few milliseconds. */
    Beat next(Deck deck, long nowNanos) {
        if (deck == null || !deck.hasTrack || deck.effectiveBpm <= 0) {
            reset();
            return null;
        }
        String key = deck.key();
        if (!key.equals(deckKey) || deck.beatVersion != trackVersion) {
            deckKey = key;
            trackVersion = deck.beatVersion;
            lastBeat = Long.MIN_VALUE;
            lastPosition = -1;
            nextFreeNanos = 0;
            pendingChange = true;
        }
        double bpm = deck.effectiveBpm;
        if (Math.abs(bpm - lastBpm) >= CHANGE_BPM) {
            pendingChange = true;
        }
        long position = deck.predictedPositionMs(nowNanos);
        int[] times = deck.beatTimes;
        Beat beat = position >= 0 && times != null && times.length > 1 ? gridBeat(deck, times, position, bpm) : freeBeat(deck, bpm, nowNanos, position);
        if (beat != null) {
            lastBpm = bpm;
            pendingChange = false;
        }
        return beat;
    }

    private Beat gridBeat(Deck deck, int[] times, long position, double bpm) {
        if (deck.velocity <= 0.05) {
            lastPosition = position;
            return null; // standing, or backwards
        }
        int i = java.util.Arrays.binarySearch(times, (int) position);
        if (i < 0) {
            i = -i - 2;
        }
        if (i < 0) {
            lastPosition = position;
            return null; // before the first beat of the grid
        }
        if (lastPosition >= 0 && Math.abs(position - lastPosition) > 400) {
            pendingChange = true; // a jump (cue, loop, seek): the lighting program should find the phase again
            lastBeat = Long.MIN_VALUE;
        }
        lastPosition = position;
        if (i == lastBeat) {
            return null;
        }
        boolean first = lastBeat == Long.MIN_VALUE;
        long previous = lastBeat;
        lastBeat = i;
        if (first || i < previous || i - previous > 1) {
            // the first beat after a start or jump is only sent when it has just begun, a late one would be off the beat
            if (position - times[i] > 60) {
                pendingChange = true;
                return null;
            }
        }
        byte[] bars = deck.beatBars;
        int shift = bars != null && bars.length > 0 ? Math.floorMod(bars[0] - 1, 4) : 0;
        return new Beat(pendingChange, i + shift, bpm);
    }

    private Beat freeBeat(Deck deck, double bpm, long nowNanos, long position) {
        if (position >= 0 && deck.velocity <= 0.05) {
            return null; // a deck that knows its position and stands
        }
        long interval = Math.round(60e9 / bpm);
        if (nextFreeNanos == 0) {
            nextFreeNanos = nowNanos;
            freePos = 0;
        }
        if (nowNanos < nextFreeNanos) {
            return null;
        }
        nextFreeNanos += interval;
        if (nextFreeNanos < nowNanos) {
            nextFreeNanos = nowNanos + interval; // late by more than a beat: do not fire a burst
            pendingChange = true;
        }
        return new Beat(pendingChange, freePos++, bpm);
    }
}
