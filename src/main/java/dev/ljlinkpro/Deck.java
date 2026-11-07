package dev.ljlinkpro;

import java.util.LinkedHashMap;
import java.util.Map;

/** Normalized, source-independent state of one player. Written by a source, read by the {@link Hub}. */
final class Deck {
    /** Unique over all devices; the browser and the API address decks by this number. */
    final int number;
    /** The number the device itself uses (player 1-6 on Pro DJ Link, deck 1-4 on a Denon). */
    volatile int label;
    /** Where the deck comes from, so the detail view can ask the right source. */
    volatile DeckSource source;
    volatile Hub.Scope scope;
    volatile String name = "";
    volatile String address = "";
    volatile boolean hasTrack;
    volatile boolean playing;
    volatile boolean onAir;
    volatile boolean master;
    volatile boolean synced;
    volatile String title;
    volatile String artist;
    volatile String key;
    volatile double trackBpm;
    volatile double effectiveBpm;
    /** Playback speed relative to the track (1.0 = no pitch change). */
    volatile double speed = 1.0;
    /** True for players whose position is only estimated between beats (NXS2 and older): it stalls and jumps, so while
     * such a player plays its speed is taken from the pitch instead of being measured. */
    volatile boolean coarsePosition;
    /** True while the light desk link takes its tempo from this deck. */
    volatile boolean sendingBpm;
    /** True while LJ Link leads the Ableton Link session with this deck's tempo. */
    volatile boolean sendingLink;
    volatile long durationMs;
    /** Last known track position in ms, or -1 if unknown; valid at the moment of the latest {@link #toJson} call. */
    volatile long positionMs = -1;
    /** Measured playback speed (1.0 = normal, negative = backwards, 0 = standing). Follows scratching. */
    volatile double velocity;
    /** Counts position samples so the {@link Hub} knows when there is something new to send. */
    volatile long positionSample;
    /** {@link System#nanoTime()} when the latest position sample arrived here, for predicting the position between samples. */
    volatile long positionArrivedNanos;
    volatile int loopBeats;
    /** Pitch change in percent (+0.35 = 0.35 % faster). */
    volatile double pitchPercent;
    volatile int trackNumber;
    /** True for sources that cannot deliver waveforms (yet), so the display says so instead of waiting. */
    volatile boolean noWaveformSource;
    /** Channel fader 0..1 and EQ -1..1; null when the source cannot report them (Pro DJ Link only reports ON AIR). */
    volatile Double fader;
    volatile Double eqHi;
    volatile Double eqMid;
    volatile Double eqLow;
    volatile int waveformVersion;
    volatile byte[] waveform;
    volatile int detailVersion;
    volatile byte[] art;
    /** "threeband" (layered, CDJ-3000) or "color" (RGB/blue as sent by the player). */
    volatile String previewStyle = "color";
    volatile String detailStyle = "color";
    volatile long[] loop;
    volatile int[] beatTimes;
    volatile byte[] beatBars;
    volatile int beatVersion;
    /** Beat packets of a Pioneer player: one arrives exactly on every beat it plays, with the beat within the bar (1-4, 0 unknown). */
    volatile long beatPacketCount;
    volatile long beatPacketNanos;
    volatile int beatPacketInBar;

    /** True for a tempo that is typed in or set by a program, not measured (the fixed value, an Ableton Link session,
     * the file tag of a Serato track): it has no jitter and goes out unfiltered. */
    volatile boolean exactTempo;
    /** Counts the tracks loaded on this deck; a source raises it when another track is loaded (not on unloading: no track
     * means no tempo, which resets the steadied tempo anyway).
     * Unlike {@link #beatVersion} it does not change when only the beat grid of the same track arrives. */
    volatile int trackVersion;
    // The tempo for the outputs, steadied: see stableBpm(). Guarded by this. Times are System.nanoTime() values, which
    // may be negative: no time is used as a marker, flags say whether a time is set.
    private double stable;
    private double smoothed;
    private long smoothedAt;
    private long lastCallAt;
    /** True while the raw tempo is a jump away from the sent one: since {@link #jumpSince}, and near {@link #jumpAnchor}
     * since {@link #jumpAnchorSince}. */
    private boolean jumping;
    private long jumpSince;
    private double jumpAnchor;
    private long jumpAnchorSince;
    /** The track version last seen, and whether a new track is waiting for its tempo (since {@link #newTrackSince}). */
    private int seenTrackVersion;
    private boolean newTrack;
    private long newTrackSince;
    private long stableChanges;
    /** Dead band: the sent tempo stays while the smoothed raw tempo is closer to it than this (ADR-021). */
    static final double STABLE_BAND = 0.15;
    /** A raw tempo this far from the sent one is a real change (pitch fader, new track): it goes the fast way. */
    static final double STABLE_JUMP = 0.25;
    /** Time constant of the smoothing of the raw tempo for the dead band. */
    static final double STABLE_SMOOTH_SECONDS = 0.5;
    /** A jump is taken once the raw tempo stays within {@link #STABLE_SETTLE_RANGE} for this long … */
    static final long STABLE_SETTLE_NANOS = 250_000_000L;
    static final double STABLE_SETTLE_RANGE = 0.1;
    /** … or, while the fader keeps moving, at the latest after this long. */
    static final long STABLE_MAX_WAIT_NANOS = 750_000_000L;
    /** After a new track, a tempo outside the band is taken at once for this long (the tempo may come a moment after the
     * track); also when nobody asked for the tempo for this long, a jump is taken at once (it had time to settle). */
    static final long STABLE_FRESH_NANOS = 1_000_000_000L;

    /**
     * The tempo sent to light desks and Ableton Link, rounded to 0.1 BPM. Players report their pitched tempo with a jitter
     * of a few hundredths; a true tempo of 137.95 lies on the rounding edge and flipped between 137.9 and 138.0, which
     * made the lighting program restart its beat. So the sent tempo has a dead band on the (smoothed) raw tempo instead of
     * a hold time on the rounded number:
     * <ul>
     * <li>while the smoothed raw tempo is within {@link #STABLE_BAND} of the sent tempo, nothing changes;</li>
     * <li>a slow drift beyond the band is taken (rounded smoothed value);</li>
     * <li>a jump of {@link #STABLE_JUMP} or more (pitch fader) is taken as soon as the raw tempo settles
     * ({@link #STABLE_SETTLE_NANOS}), at the latest after {@link #STABLE_MAX_WAIT_NANOS}; a short nudge is ignored;</li>
     * <li>no tempo gives 0, and the first tempo after none goes out at once; so does a tempo outside the band right after
     * a new track ({@link #trackVersion}).</li>
     * </ul>
     * Works with any calling rate: the smoothing runs on the time between calls, not on their number.
     */
    synchronized double stableBpm(long nowNanos) {
        double raw = effectiveBpm;
        int version = trackVersion;
        if (version != seenTrackVersion) {
            seenTrackVersion = version;
            newTrack = true;
            newTrackSince = nowNanos;
        }
        if (newTrack && nowNanos - newTrackSince > STABLE_FRESH_NANOS) {
            newTrack = false;
        }
        long sinceLastCall = nowNanos - lastCallAt;
        lastCallAt = nowNanos;
        if (exactTempo && raw > 0) {
            take(rounded(raw), raw, nowNanos); // no jitter: every step counts at once
            return stable;
        }
        if (raw <= 0) {
            stable = 0;
            jumping = false;
            return 0;
        }
        if (stable <= 0) {
            take(rounded(raw), raw, nowNanos);
            return stable;
        }
        if (newTrack && Math.abs(raw - stable) > STABLE_BAND) {
            take(rounded(raw), raw, nowNanos); // another track: its tempo is no fader move, nothing to wait for
            newTrack = false;
            return stable;
        }
        double dt = Math.max(0, (nowNanos - smoothedAt) / 1e9);
        smoothedAt = Math.max(smoothedAt, nowNanos);
        if (Math.abs(raw - stable) >= STABLE_JUMP) {
            if (sinceLastCall > STABLE_FRESH_NANOS) {
                take(rounded(raw), raw, nowNanos); // nobody asked for a while: the change is old, not a moving fader
                return stable;
            }
            // The smoothing pauses: a short nudge must not pull the smoothed tempo out of the band afterwards.
            if (!jumping) {
                jumping = true;
                jumpSince = nowNanos;
                jumpAnchor = raw;
                jumpAnchorSince = nowNanos;
            } else if (Math.abs(raw - jumpAnchor) >= STABLE_SETTLE_RANGE) {
                jumpAnchor = raw; // still moving
                jumpAnchorSince = nowNanos;
            }
            if (nowNanos - jumpAnchorSince >= STABLE_SETTLE_NANOS || nowNanos - jumpSince >= STABLE_MAX_WAIT_NANOS) {
                take(rounded(raw), raw, nowNanos);
            }
            return stable;
        }
        jumping = false; // near the sent tempo (again): a nudge was no change
        smoothed += (raw - smoothed) * (1 - Math.exp(-dt / STABLE_SMOOTH_SECONDS));
        if (Math.abs(smoothed - stable) > STABLE_BAND) {
            take(rounded(smoothed), smoothed, nowNanos);
        }
        return stable;
    }

    private void take(double value, double smoothFrom, long nowNanos) {
        double before = stable;
        stable = value;
        smoothed = smoothFrom;
        smoothedAt = nowNanos;
        jumping = false;
        if (before > 0 && before != value) {
            stableChanges++;
            if (sendingBpm || sendingLink) {
                System.out.println(String.format(java.util.Locale.ROOT, "Sent tempo of deck %s: %.1f -> %.1f BPM (raw %.3f)",
                        scope == null ? name : key(), before, value, effectiveBpm));
            }
        }
    }

    private static double rounded(double bpm) {
        return Math.round(bpm * 10) / 10.0;
    }

    /** The tempo last given by {@link #stableBpm} (0 when none), without updating it; for the tooltip of the display and diagnostics (the display itself shows the real tempo). */
    synchronized double sentBpm() {
        return stable;
    }

    /** How often the steadied tempo changed, for the diagnostics. */
    synchronized long stableChanges() {
        return stableChanges;
    }
    volatile int artVersion;
    volatile java.util.List<Map<String, Object>> cues = java.util.List.of();
    volatile byte[] detail;

    /** Stays the same while the device is on the network and across restarts (unlike {@link #number}). */
    String key() {
        return (scope == null ? "?" : scope.key) + ":" + label;
    }

    Deck(int number) {
        this.number = number;
    }

    /** Clears everything that belongs to the loaded track (called when a new track is loaded). */
    void clearTrack() {
        title = null;
        artist = null;
        key = null;
        trackBpm = 0;
        durationMs = 0;
        positionMs = -1;
        waveform = null;
        waveformVersion++;
        detail = null;
        detailVersion++;
        art = null;
        artVersion++;
        beatTimes = null;
        beatBars = null;
        beatVersion++;
        loop = null;
        cues = java.util.List.of();
    }

    /** Stores a new position sample; velocity is estimated from the previous sample. */
    private static final long VELOCITY_WINDOW_NANOS = 150_000_000L;
    private final java.util.ArrayDeque<long[]> history = new java.util.ArrayDeque<>();
    private long lastSampleNanos;
    private long lastSamplePos = -1;

    synchronized void samplePosition(long ms) {
        samplePosition(ms, System.nanoTime());
    }

    /** {@code now} is the time of the sample in nanoseconds; a device's own clock is better than our arrival time, which jitters. */
    synchronized void samplePosition(long ms, long now) {
        if (ms >= 0 && lastSamplePos >= 0) {
            double dt = (now - lastSampleNanos) / 1e6;
            double delta = ms - lastSamplePos;
            if (dt > 1 && Math.abs(delta) > 6 * dt + 40) {
                history.clear(); // a jump (cue, loop): the speed before it still holds
            } else if (dt > 1) {
                history.addLast(new long[] {now, ms});
                while (history.size() > 1 && now - history.peekFirst()[0] > VELOCITY_WINDOW_NANOS) {
                    history.removeFirst();
                }
                // The speed over about 150 ms: single samples of a player are jittery (NXS2 times are interpolated), a window is not.
                long[] first = history.peekFirst();
                double span = (now - first[0]) / 1e6;
                if (span >= 40) {
                    double raw = Math.max(-8, Math.min(8, (ms - first[1]) / span));
                    // Playing normally: the reported pitch is exact, the measured speed is only noise around it
                    // (NXS2 positions are interpolated and jump by up to 0.2 s). Scratching, nudging and stopping differ clearly.
                    if (playing && (coarsePosition || Math.abs(raw - speed) < 0.35)) {
                        velocity = speed;
                    } else {
                        velocity = Math.abs(raw) < 0.005 ? 0 : velocity * 0.3 + raw * 0.7;
                    }
                }
            }
            if (history.isEmpty()) {
                history.addLast(new long[] {now, ms});
            }
        } else if (ms < 0) {
            velocity = 0;
            history.clear();
        } else {
            history.addLast(new long[] {now, ms});
        }
        lastSampleNanos = now;
        lastSamplePos = ms;
        positionMs = ms;
        positionArrivedNanos = System.nanoTime();
        positionSample++;
    }

    /** The position now, carried on from the latest sample at the measured speed; -1 when unknown. */
    long predictedPositionMs(long nowNanos) {
        long ms = positionMs;
        if (ms < 0) {
            return -1;
        }
        double elapsed = (nowNanos - positionArrivedNanos) / 1e6;
        return ms + Math.round(velocity * Math.max(0, Math.min(elapsed, 500)));
    }

    void setWaveform(byte[] data) {
        waveform = data;
        waveformVersion++;
    }

    void setBeats(int[] times, byte[] bars) {
        beatTimes = times;
        beatBars = bars;
        beatVersion++;
    }

    void setArt(byte[] data) {
        art = data;
        artVersion++;
    }

    void setDetail(byte[] data) {
        detail = data;
        detailVersion++;
    }

    /** JSON-ready view. The position is left out unless {@code withPosition}, so unchanged decks can be detected. */
    Map<String, Object> toJson(boolean withPosition) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "deck");
        m.put("deck", number);
        m.put("label", label);
        m.put("coarse", coarsePosition);
        m.put("sendingBpm", sendingBpm);
        m.put("sendingLink", sendingLink);
        m.put("name", name);
        m.put("address", address);
        m.put("hasTrack", hasTrack);
        m.put("playing", playing);
        m.put("onAir", onAir);
        m.put("master", master);
        m.put("synced", synced);
        boolean hide = Privacy.enabled();
        m.put("title", hide ? null : title);
        m.put("artist", hide ? null : artist);
        m.put("key", key);
        m.put("trackBpm", trackBpm);
        m.put("effectiveBpm", effectiveBpm);
        // The steadied tempo the outputs send; the display shows the real tempo and has this one as a tooltip.
        // Asked on every tick, also without a tempo, so a pause without tempo resets it and the next tempo goes out at once.
        double steady = stableBpm(System.nanoTime());
        m.put("stableBpm", hasTrack ? steady : 0.0);
        m.put("speed", speed);
        m.put("durationMs", durationMs);
        m.put("waveformVersion", waveformVersion);
        m.put("hasWaveform", waveform != null);
        m.put("detailVersion", detailVersion);
        m.put("hasDetail", detail != null);
        m.put("previewStyle", previewStyle);
        m.put("detailStyle", detailStyle);
        m.put("loop", loop == null ? null : java.util.List.of(loop[0], loop[1]));
        m.put("hasBeats", beatTimes != null);
        m.put("beatVersion", beatVersion);
        m.put("hasArt", !hide && art != null);
        m.put("artVersion", artVersion);
        m.put("cues", cues);
        m.put("loopBeats", loopBeats);
        m.put("pitch", pitchPercent);
        m.put("noWaveform", noWaveformSource);
        m.put("fader", fader);
        m.put("eq", eqHi == null ? null : java.util.Map.of("hi", eqHi, "mid", eqMid, "low", eqLow));
        m.put("trackNumber", trackNumber);
        return m;
    }
}
