package dev.ljlinkpro;

import java.util.List;

/**
 * The phrases of a track: where each starts (beat number, 1-based like the beat grid) and what it is.
 * Kinds are the five of rekordbox's "high" mood (intro, up, chorus, down, outro); the "mid" and "low" moods are mapped
 * onto them (verses are "up", the bridge is "down").
 */
record Phrases(int[] startBeats, String[] kinds, int endBeat) {

    static final List<String> KINDS = List.of("intro", "up", "chorus", "down", "outro");

    Phrases {
        if (startBeats.length != kinds.length) {
            throw new IllegalArgumentException("one kind per phrase");
        }
    }

    /** Index of the phrase that contains the beat (1-based beat number, may have a fraction), or -1 before the first / after the end. */
    int indexAt(double beat) {
        if (startBeats.length == 0 || beat < startBeats[0] || endBeat > 0 && beat >= endBeat) {
            return -1;
        }
        int lo = 0;
        int hi = startBeats.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (startBeats[mid] <= beat) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }

    /** The beat where the next phrase starts (the end beat after the last one), or -1. */
    int nextStart(int index) {
        if (index < 0) {
            return -1;
        }
        return index + 1 < startBeats.length ? startBeats[index + 1] : endBeat > 0 ? endBeat : -1;
    }

    /** rekordbox phrase names to the five kinds. Unknown names give "up" (something in the middle of a track). */
    static String kindOf(String mood, String name) {
        String n = name.toUpperCase(java.util.Locale.ROOT);
        if (n.startsWith("INTRO")) {
            return "intro";
        }
        if (n.startsWith("OUTRO")) {
            return "outro";
        }
        if (n.startsWith("CHORUS")) {
            return "chorus";
        }
        if (n.equals("DOWN") || n.startsWith("BRIDGE")) {
            return "down";
        }
        return "up"; // UP, VERSE_1 .. VERSE_6, VERSE_1B ...
    }

    /** From the song structure (PSSI) of a rekordbox analysis file; null when it has no phrases. */
    static Phrases of(org.deepsymmetry.cratedigger.pdb.RekordboxAnlz.SongStructureTag tag) {
        if (tag == null || tag.body() == null || tag.body().entries() == null || tag.body().entries().isEmpty()) {
            return null;
        }
        var body = tag.body();
        String mood = String.valueOf(body.mood());
        var entries = body.entries();
        int n = entries.size();
        int[] starts = new int[n];
        String[] kinds = new String[n];
        for (int i = 0; i < n; i++) {
            var e = entries.get(i);
            starts[i] = e.beat();
            Object kind = e.kind();
            String name;
            if (kind instanceof org.deepsymmetry.cratedigger.pdb.RekordboxAnlz.PhraseHigh h) {
                name = String.valueOf(h.id());
            } else if (kind instanceof org.deepsymmetry.cratedigger.pdb.RekordboxAnlz.PhraseMid m) {
                name = String.valueOf(m.id());
            } else if (kind instanceof org.deepsymmetry.cratedigger.pdb.RekordboxAnlz.PhraseLow l) {
                name = String.valueOf(l.id());
            } else {
                name = "UP";
            }
            kinds[i] = kindOf(mood, name);
        }
        return new Phrases(starts, kinds, body.endBeat());
    }
}
