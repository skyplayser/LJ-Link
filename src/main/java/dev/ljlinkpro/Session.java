package dev.ljlinkpro;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Owns the running {@link DeckSource}s. "Search" starts every supported kind (Pro DJ Link, Denon StageLinQ,
 * Serato DJ Pro on this computer and Ableton Link), so devices of different makers show up together, and devices that
 * appear later are added by their source. Pioneer comes first: the Denon search joins once the CDJs are connected or
 * clearly absent.
 */
final class Session {
    /** How long the Denon search waits for Pioneer when no Pioneer device has announced itself. */
    private static final long PIONEER_HEAD_START_MS = 4_000;
    /** How long it waits at most while a Pioneer device was found and is being connected. */
    private static final long PIONEER_CONNECT_MS = 15_000;

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
        Thread denonLater = new Thread(() -> startAfterPioneer(pioneer, d), "denon-after-pioneer");
        denonLater.setDaemon(true);
        denonLater.start();
        java.nio.file.Path serato = SeratoSource.folder();
        if (serato != null) {
            SeratoSource s = new SeratoSource(hub.scope("serato", "serato", false, "Serato DJ Pro"), serato);
            active.add(s);
            s.start();
        }
        LinkTempoSource link = new LinkTempoSource(hub.scope("link", "link", false, "Ableton Link"));
        active.add(link);
        link.start();
    }

    /** Starts the Denon search once Pioneer is connected, failed, or has found nothing for a moment. */
    private void startAfterPioneer(Hub.Scope pioneer, DeckSource denon) {
        long start = System.currentTimeMillis();
        try {
            while ("searching".equals(pioneer.phase())) {
                long waited = System.currentTimeMillis() - start;
                long limit = pioneer.devices.isEmpty() ? PIONEER_HEAD_START_MS : PIONEER_CONNECT_MS;
                if (waited >= limit) {
                    break;
                }
                Thread.sleep(200);
            }
        } catch (InterruptedException e) {
            return;
        }
        synchronized (this) {
            if (active.contains(denon)) { // not stopped or replaced by a new search meanwhile
                denon.start();
            }
        }
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

    synchronized List<String> diagnostics() {
        List<String> out = new ArrayList<>();
        for (DeckSource s : active) {
            out.add("[" + s.getClass().getSimpleName() + "]");
            try {
                out.addAll(s.diagnostics());
            } catch (RuntimeException e) {
                out.add("  failed: " + e);
            }
        }
        if (active.isEmpty()) {
            out.add("No search running");
        }
        return out;
    }

    /** The short lines of every running source, for the summary of a problem report. */
    synchronized List<String> summary() {
        List<String> out = new ArrayList<>();
        for (DeckSource s : active) {
            try {
                out.addAll(s.summary());
            } catch (RuntimeException e) {
                out.add(s.getClass().getSimpleName() + " summary failed: " + e);
            }
        }
        if (active.isEmpty()) {
            out.add("No search running");
        }
        return out;
    }
}
