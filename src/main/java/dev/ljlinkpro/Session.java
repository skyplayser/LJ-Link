package dev.ljlinkpro;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Owns the running {@link DeckSource}s. "Search" starts every supported kind at once (Pro DJ Link and Denon StageLinQ),
 * so devices of different makers show up together, and devices that appear later are added by their source.
 */
final class Session {
    private final Hub hub;
    private final List<DeckSource> active = new ArrayList<>();

    Session(Hub hub) {
        this.hub = hub;
    }

    /** Looks for all supported devices. Whatever is found, now or later, is added to the view. */
    synchronized void startSearch() {
        stopActive();
        Hub.Scope pioneer = hub.scope("pioneer", "pioneer", true, "Pro DJ Link");
        ProDjLinkSource p = new ProDjLinkSource(pioneer);
        Hub.Scope denon = hub.scope("denon", "denon", false, "Denon");
        StageLinqDiscovery d = new StageLinqDiscovery(hub, denon);
        active.add(p);
        active.add(d);
        p.start();
        d.start();
    }

    synchronized void startDemo() {
        stopActive();
        MockSource m = new MockSource(hub.scope("demo", "demo", true, "Demo"));
        active.add(m);
        m.start();
    }

    /** Detail rows of a deck, asked from the source that owns it (the number is the global deck number). */
    synchronized List<Map<String, Object>> info(int deckNumber) {
        Deck d = hub.deckById(deckNumber);
        if (d == null || d.source == null) {
            return List.of();
        }
        return d.source.info(d.label);
    }

    synchronized void stop() {
        stopActive();
        hub.setIdle();
    }

    private void stopActive() {
        for (DeckSource s : active) {
            try {
                s.stop();
            } catch (RuntimeException e) {
                System.err.println("Stopping source failed: " + e);
            }
        }
        active.clear();
        hub.reset();
    }
}
