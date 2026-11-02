package dev.ljlinkpro;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LinkSourceTest {
    private static Deck deck(Hub.Scope scope, int label, double bpm, boolean master, boolean onAir, boolean playing) {
        Deck d = scope.deck(label);
        d.hasTrack = true;
        d.effectiveBpm = bpm;
        d.master = master;
        d.onAir = onAir;
        d.playing = playing;
        return d;
    }

    @Test
    void autoPrefersTheMasterThatIsLiveAndAManualChoiceWins() throws Exception {
        Hub hub = new Hub();
        Hub.Scope cdjs = hub.scope("pioneer", "pioneer", true, "Pro DJ Link");
        Hub.Scope denon = hub.scope("denon-x", "denon", false, "Denon Prime 4+");
        deck(cdjs, 3, 150, true, false, false);
        Deck live = deck(denon, 1, 174, true, true, true);
        Deck other = deck(cdjs, 4, 130, false, false, true);
        LinkService link = new LinkService(hub, false);
        String saved = link.source();
        try {
            link.setSource(LinkService.AUTO);
            assertEquals(live, link.leadingDeck(), "the master that is live and playing");
            link.setSource(other.key());
            assertEquals(other, link.leadingDeck(), "a chosen deck is used even when another one is live");
            other.effectiveBpm = 0;
            assertNull(link.leadingDeck(), "a chosen deck without tempo sends nothing");
        } finally {
            link.setSource(saved);
        }
    }
}
