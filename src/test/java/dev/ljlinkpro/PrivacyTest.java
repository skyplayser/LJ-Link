package dev.ljlinkpro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PrivacyTest {
    private static Path home;

    @BeforeAll
    static void useTemporaryHome() throws Exception {
        home = Files.createTempDirectory("ljlinkpro-test");
        System.setProperty("ljlinkpro.home", home.toString());
    }

    @AfterEach
    void reset() {
        Privacy.setForThisRun(false);
    }

    private static Deck loadedDeck() {
        Deck d = new Deck(1);
        d.hasTrack = true;
        d.title = "Secret Title";
        d.artist = "Secret Artist";
        d.setArt(new byte[] {1, 2, 3});
        return d;
    }

    @Test
    void showsEverythingWhenOff() {
        Map<String, Object> json = loadedDeck().toJson(false);
        assertEquals("Secret Title", json.get("title"));
        assertEquals("Secret Artist", json.get("artist"));
        assertEquals(true, json.get("hasArt"));
    }

    @Test
    void removesTitleArtistAndCoverWhenOn() {
        Privacy.setForThisRun(true);
        Map<String, Object> json = loadedDeck().toJson(false);
        assertNull(json.get("title"));
        assertNull(json.get("artist"));
        assertEquals(false, json.get("hasArt"));
        assertEquals(true, json.get("hasTrack"), "the deck itself must stay visible");
    }

    @Test
    void masksTrackRowsAndCueCommentsInTheDetailView() {
        Privacy.setForThisRun(true);
        List<Map<String, Object>> sections = List.of(Map.of("title", "Track", "rows", List.of(
                Map.of("k", "Title", "v", "Secret Title"),
                Map.of("k", "Album", "v", "Secret Album"),
                Map.of("k", "Key", "v", "Am"),
                Map.of("k", "Hot cue A", "v", "00:15.6  \"Drop name\""))));
        @SuppressWarnings("unchecked")
        List<Map<String, String>> rows = (List<Map<String, String>>) Privacy.filterInfo(sections).get(0).get("rows");
        assertEquals(Privacy.HIDDEN, rows.get(0).get("v"));
        assertEquals(Privacy.HIDDEN, rows.get(1).get("v"));
        assertEquals("Am", rows.get(2).get("v"), "technical values stay");
        assertEquals("00:15.6", rows.get(3).get("v"));
    }

    @Test
    void rememberedAfterRestart() throws Exception {
        Privacy.set(true);
        String saved = Files.readString(home.resolve("settings.json"));
        assertTrue(saved.contains("\"privacy\":true"), saved);
        Privacy.set(false);
        assertFalse(Files.readString(home.resolve("settings.json")).contains("true"));
    }

    @Test
    void tellsListenersWhenChanged() {
        int[] calls = {0};
        Privacy.addListener(() -> calls[0]++);
        Privacy.set(true);
        Privacy.set(false);
        assertTrue(calls[0] >= 2);
    }
}
