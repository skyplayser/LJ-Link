package dev.ljlinkpro;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}
