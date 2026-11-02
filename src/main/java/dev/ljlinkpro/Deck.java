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
    volatile long durationMs;
    /** Last known track position in ms, or -1 if unknown; valid at the moment of the latest {@link #toJson} call. */
    volatile long positionMs = -1;
    /** Measured playback speed (1.0 = normal, negative = backwards, 0 = standing). Follows scratching. */
    volatile double velocity;
    /** Counts position samples so the {@link Hub} knows when there is something new to send. */
    volatile long positionSample;
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
        positionSample++;
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
