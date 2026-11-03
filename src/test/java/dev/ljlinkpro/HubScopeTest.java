package dev.ljlinkpro;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HubScopeTest {
    @Test
    void decksOfDifferentDevicesGetOwnNumbers() {
        Hub hub = new Hub();
        Hub.Scope cdjs = hub.scope("pioneer", "pioneer", true, "Pro DJ Link");
        Hub.Scope denon = hub.scope("denon-1", "denon", false, "Denon Prime 4+");
        Deck a = cdjs.deck(1);
        Deck b = denon.deck(1);
        assertNotEquals(a.number, b.number, "player 1 and deck 1 are different decks");
        assertEquals(1, a.label);
        assertEquals(1, b.label);
        assertEquals(a, cdjs.deck(1), "the same device number gives the same deck");
    }

    @Test
    void aDeviceThatLeavesTakesItsDecksAndItsStateWithIt() {
        Hub hub = new Hub();
        Hub.Scope denon = hub.scope("denon-1", "denon", false, "Denon Prime 4+");
        denon.setState("monitoring", "live", "Connected");
        int n = denon.deck(2).number;
        assertEquals("monitoring", hub.phase());
        denon.close();
        assertNull(hub.deckById(n));
        assertEquals("idle", hub.phase());
    }

    @Test
    void monitoringWinsOverSearching() {
        Hub hub = new Hub();
        Hub.Scope pioneer = hub.scope("pioneer", "pioneer", true, "Pro DJ Link");
        Hub.Scope denon = hub.scope("denon", "denon", false, "Denon");
        pioneer.setState("searching", "live", "");
        denon.setState("monitoring", "live", "");
        assertEquals("monitoring", hub.phase());
        denon.setState("searching", "live", "");
        assertEquals("searching", hub.phase());
    }

    @Test
    void whileSearchingPioneerSpeaksFirst() {
        Hub hub = new Hub();
        Hub.Scope pioneer = hub.scope("pioneer", "pioneer", true, "Pro DJ Link");
        Hub.Scope denon = hub.scope("denon", "denon", false, "Denon");
        denon.setState("searching", "live", "Searching for Denon devices (StageLinQ) …");
        assertTrue(hub.stateJson().contains("\"message\":\"Searching for Denon devices"), "only Denon has a message yet");
        pioneer.setState("searching", "live", "Searching for Pioneer CDJs (Pro DJ Link) …");
        assertTrue(hub.stateJson().contains("\"message\":\"Searching for Pioneer CDJs"));
    }

    @Test
    void aFailedPioneerSearchIsShownWhileDenonIsStillSearched() {
        Hub hub = new Hub();
        Hub.Scope pioneer = hub.scope("pioneer", "pioneer", true, "Pro DJ Link");
        Hub.Scope denon = hub.scope("denon", "denon", false, "Denon");
        denon.setState("searching", "live", "Searching for Denon devices (StageLinQ) …");
        pioneer.setState("error", "live", "Pro DJ Link: boom");
        assertEquals("searching", hub.phase());
        assertTrue(hub.stateJson().contains("\"searchWarnings\":[\"Pro DJ Link: boom\"]"));
    }
}
