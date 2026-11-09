package dev.ljlinkpro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VjLinkTest {

    @Test
    void rekordboxPhrasesMapToFiveKinds() {
        assertEquals("intro", Phrases.kindOf("HIGH", "INTRO"));
        assertEquals("up", Phrases.kindOf("HIGH", "UP"));
        assertEquals("down", Phrases.kindOf("HIGH", "DOWN"));
        assertEquals("chorus", Phrases.kindOf("MID", "CHORUS"));
        assertEquals("up", Phrases.kindOf("MID", "VERSE_3"));
        assertEquals("up", Phrases.kindOf("LOW", "VERSE_1B"));
        assertEquals("down", Phrases.kindOf("LOW", "BRIDGE"));
        assertEquals("outro", Phrases.kindOf("LOW", "OUTRO"));
    }

    @Test
    void phraseLookup() {
        Phrases p = new Phrases(new int[] {1, 65, 97}, new String[] {"intro", "up", "chorus"}, 161);
        assertEquals(0, p.indexAt(1));
        assertEquals(0, p.indexAt(64.9));
        assertEquals(1, p.indexAt(65));
        assertEquals(2, p.indexAt(160));
        assertEquals(-1, p.indexAt(161));
        assertEquals(97, p.nextStart(1));
        assertEquals(161, p.nextStart(2));
    }

    @Test
    void beatCounterStaysContinuousOverJumps() {
        int[] grid = {0, 500, 1000, 1500, 2000};
        assertEquals(0.0, VjLink.beatAt(grid, 0), 1e-9);
        assertEquals(1.5, VjLink.beatAt(grid, 750), 1e-9);
        assertEquals(4.5, VjLink.beatAt(grid, 2250), 1e-9);
        // A jump back (loop) keeps counting forward with the same phase.
        assertEquals(11.25, VjLink.jumpTo(10.5, 3.25), 1e-9);
        assertEquals(10.5, VjLink.jumpTo(10.5, 0.5), 1e-9);
    }

    @Test
    void eqAndBands() {
        assertEquals(0.0, VjLink.eqGain(-1.0), 1e-9);
        assertEquals(1.0, VjLink.eqGain(null), 1e-9);
        assertEquals(1.5, VjLink.eqGain(1.0), 1e-9);
        byte[] color = {(byte) 255, (byte) 255, 0, (byte) 128};
        double[] b = VjLink.bands(color, 0, false);
        assertEquals(1.0, b[0], 1e-9);
        assertEquals(0.0, b[1], 1e-9);
        assertEquals(128 / 255.0, b[2], 1e-9);
    }

    /** A playing master deck at 120 BPM: phrase warnings, the phrase change, a hot cue, a loop, then the deck goes away. */
    @Test
    void streamsEventsOfAPlayingDeck() throws Exception {
        Hub hub = new Hub();
        Hub.Scope scope = hub.scope("pioneer", "pioneer", true, "Pro DJ Link");
        Deck d = scope.deck(1);
        d.name = "CDJ-3000";
        d.hasTrack = true;
        d.playing = true;
        d.onAir = true;
        d.master = true;
        d.title = "Song";
        d.artist = "Artist";
        d.key = "8A";
        d.trackBpm = 120;
        d.effectiveBpm = 120;
        d.durationMs = 80_000;
        int[] times = new int[160];
        byte[] bars = new byte[160];
        for (int i = 0; i < times.length; i++) {
            times[i] = i * 500;
            bars[i] = (byte) (i % 4 + 1);
        }
        d.setBeats(times, bars);
        d.phrases = new Phrases(new int[] {1, 65, 97}, new String[] {"intro", "up", "chorus"}, 161);
        d.cues = List.of(Map.of("t", 32_000L, "hot", 2, "loop", false, "loopEnd", 0L, "color", "#ff8800"));
        d.velocity = 0;

        VjLink vj = new VjLink(hub, "test");
        List<Map<?, ?>> out = new ArrayList<>();
        java.util.function.LongConsumer at = ms -> {
            d.samplePosition(ms);
            d.velocity = 0; // the position is exactly the sample
            for (String s : vj.tick(System.nanoTime())) {
                try {
                    out.add(Hub.JSON.readValue(s, Map.class));
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        };
        for (long ms = 15_000; ms <= 33_000; ms += 33) {
            at.accept(ms);
        }
        List<Object> warnings = out.stream().filter(m -> "next".equals(m.get("kind"))).map(m -> (Object) m.get("inBeats")).toList();
        assertEquals(List.of(32, 16, 8, 4), warnings);
        assertTrue(out.stream().anyMatch(m -> "phrase".equals(m.get("kind")) && "up".equals(m.get("phrase"))));
        assertTrue(out.stream().anyMatch(m -> "cue".equals(m.get("kind")) && "B".equals(m.get("cue")) && "#ff8800".equals(m.get("color"))));
        assertFalse(out.stream().anyMatch(m -> "track".equals(m.get("kind"))), "no track event for what already played at start");

        List<Map<?, ?>> tempo = out.stream().filter(m -> "tempo".equals(m.get("t"))).toList();
        double first = ((Number) tempo.get(0).get("beat")).doubleValue();
        double last = ((Number) tempo.get(tempo.size() - 1).get("beat")).doubleValue();
        assertEquals(36.0, last - first, 0.1); // 18 s at 120 BPM
        assertNotNull(tempo.get(tempo.size() - 1).get("bar"));
        Map<?, ?> deck = (Map<?, ?>) out.stream().filter(m -> "deck".equals(m.get("t"))).toList().get(0).get("deck");
        assertEquals("Song", deck.get("title"));
        assertNotNull(deck.get("trackId"));

        // A loop: event on, the counter goes on forward although the position jumps back.
        out.clear();
        d.loop = new long[] {33_000, 35_000};
        d.loopBeats = 4;
        at.accept(34_900);
        at.accept(33_000 + 33);
        assertTrue(out.stream().anyMatch(m -> "loop".equals(m.get("kind")) && Boolean.TRUE.equals(m.get("active"))
                && Integer.valueOf(4).equals(m.get("beats"))));
        List<Map<?, ?>> t2 = out.stream().filter(m -> "tempo".equals(m.get("t"))).toList();
        assertTrue(((Number) t2.get(1).get("beat")).doubleValue() > ((Number) t2.get(0).get("beat")).doubleValue());

        out.clear();
        scope.removeDeck(1);
        at.accept(0);
        assertTrue(out.stream().anyMatch(m -> "gone".equals(m.get("t")) && Integer.valueOf(1).equals(m.get("deck"))));
    }

    @Test
    void greetingStartsWithHello() throws Exception {
        Hub hub = new Hub();
        Deck d = hub.scope("demo", "demo", true, "Demo").deck(1);
        d.hasTrack = true;
        d.master = true;
        d.playing = true;
        d.effectiveBpm = 126;
        List<String> g = new VjLink(hub, "1.2.3").greeting(System.nanoTime());
        Map<?, ?> hello = Hub.JSON.readValue(g.get(0), Map.class);
        assertEquals("hello", hello.get("t"));
        assertEquals(1, hello.get("version"));
        assertEquals("1.2.3", hello.get("appVersion"));
        assertEquals("deck", Hub.JSON.readValue(g.get(1), Map.class).get("t"));
        assertEquals("tempo", Hub.JSON.readValue(g.get(g.size() - 1), Map.class).get("t"));
    }
}
