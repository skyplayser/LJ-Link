package dev.ljlinkpro;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Club key, club name and club.json for LJL Link; the routes stay local (ControlGuard), /sender is open. */
class SenderClubTest {
    private String previousHome;

    @BeforeEach
    void setUp() throws Exception {
        previousHome = System.getProperty("ljlinkpro.home");
        System.setProperty("ljlinkpro.home", Files.createTempDirectory("ljlinkpro-sender-test").toString());
    }

    @AfterEach
    void tearDown() {
        if (previousHome == null) {
            System.clearProperty("ljlinkpro.home");
        } else {
            System.setProperty("ljlinkpro.home", previousHome);
        }
    }

    @Test
    void keyIsCreatedOnceAndKept() {
        String key = SenderClub.key();
        assertTrue(key.matches("[0-9a-f]{32}"), key);
        assertEquals(key, SenderClub.key());
        assertEquals(key, AppSettings.getString(SenderClub.KEY_SETTING, null), "saved in the settings file");
    }

    @Test
    void brokenSavedKeyIsReplaced() {
        AppSettings.set(SenderClub.KEY_SETTING, "short");
        assertTrue(SenderClub.key().matches("[0-9a-f]{32}"));
    }

    @Test
    void renewGivesAnotherKey() {
        String old = SenderClub.key();
        String fresh = SenderClub.renewKey();
        assertNotEquals(old, fresh);
        assertEquals(fresh, SenderClub.key());
        assertFalse(SenderClub.matches(fresh, old));
    }

    @Test
    void boothIdIsCreatedOnceKeptAndRenewed() {
        String booth = SenderClub.booth();
        assertTrue(booth.matches("[0-9a-f]{16}"), booth);
        assertEquals(booth, SenderClub.booth());
        assertEquals(booth, AppSettings.getString(SenderClub.BOOTH_SETTING, null), "saved in the settings file");
        String fresh = SenderClub.renewBooth();
        assertNotEquals(booth, fresh);
        assertEquals(fresh, SenderClub.booth());
        AppSettings.set(SenderClub.BOOTH_SETTING, "XYZ");
        assertTrue(SenderClub.booth().matches("[0-9a-f]{16}"), "a broken saved id is replaced");
    }

    @Test
    void keysAreRandom() {
        assertNotEquals(SenderClub.newKeyValue(), SenderClub.newKeyValue());
    }

    @Test
    void matchIsExact() {
        String k = "00112233445566778899aabbccddeeff";
        assertTrue(SenderClub.matches(k, k));
        assertFalse(SenderClub.matches(k, k.substring(0, 31)));
        assertFalse(SenderClub.matches(k, k + "0"));
        assertFalse(SenderClub.matches(k, ""));
        assertFalse(SenderClub.matches(k, null));
        assertFalse(SenderClub.matches("", ""), "no key, no match");
    }

    @Test
    void clubNameFallsBackToTheComputerName() {
        assertFalse(SenderClub.nameSet());
        assertEquals(SenderClub.hostName(), SenderClub.name());
        SenderClub.setName("  Testclub\n ");
        assertTrue(SenderClub.nameSet());
        assertEquals("Testclub", SenderClub.name());
        SenderClub.setName("x".repeat(100));
        assertEquals(SenderClub.MAX_NAME, SenderClub.name().length());
        SenderClub.setName(" ");
        assertFalse(SenderClub.nameSet());
    }

    @Test
    void clubJsonHasTheProtocolFields() throws Exception {
        Map<String, Object> m = SenderClub.clubJson("Testclub", List.of("192.168.1.20"), 8081, "abc");
        assertEquals("{\"version\":1,\"club\":\"Testclub\",\"hosts\":[\"192.168.1.20\"],\"port\":8081,\"key\":\"abc\"}",
                Hub.JSON.writeValueAsString(m));
    }

    @Test
    void hostsAreIpv4WithoutSelfAssigned() {
        for (String h : SenderClub.hosts()) {
            assertTrue(h.matches("\\d+\\.\\d+\\.\\d+\\.\\d+"), h);
            assertFalse(h.startsWith("169.254.") || h.startsWith("127."), h);
        }
    }

    @Test
    void cleanCountsCodePoints() {
        assertEquals("ab", SenderClub.clean("a\u0000b\u001b", 10));
        assertEquals("🎧🎧", SenderClub.clean("🎧🎧🎧", 2), "no half emoji");
        assertEquals("", SenderClub.clean(null, 5));
    }

    @Test
    void senderRoutesAreLocalAndTheSocketIsNotUnderApi() {
        assertTrue(ControlGuard.isGuarded("/api/sender"));
        assertTrue(ControlGuard.isGuarded("/api/sender/club.json"));
        assertTrue(ControlGuard.isGuarded("/api/sender/key"));
        assertTrue(ControlGuard.isGuarded("/api/sender/club"));
        assertFalse(ControlGuard.isGuarded("/sender"));
    }

    @Test
    void bonjourAnnouncesTheSender() {
        Map<String, String> txt = Bonjour.txt("1.0.0", "0123456789abcdef");
        assertEquals("1", txt.get("sender"));
        assertEquals("/sender", txt.get("senderpath"));
        assertEquals("1", txt.get("vjlink"), "VJ-Link stays as it was");
        assertEquals("/vjlink", txt.get("path"));
    }
}
