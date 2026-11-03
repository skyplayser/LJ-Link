package dev.ljlinkpro;

/**
 * Shows the tempo of an Ableton Link session as a deck of its own, so it can be watched and sent to the light desk
 * (SEND BPM). It appears as soon as any program in the network (Serato, Traktor, rekordbox, Ableton Live …) shares
 * Link, and disappears when the session ends.
 */
final class LinkTempoSource implements DeckSource {
    private final Hub.Scope hub;
    private final AbletonLink link = new AbletonLink();
    private volatile boolean stopped;

    LinkTempoSource(Hub.Scope hub) {
        this.hub = hub;
        hub.setSource(this);
    }

    @Override
    public void start() {
        hub.setState("idle", "live", "");
        link.start();
        Thread t = new Thread(() -> {
            while (!stopped) {
                AbletonLink.State s = link.state();
                if (s == null) {
                    hub.removeAllDecks();
                    hub.setState("idle", "live", "");
                } else {
                    Deck d = hub.deck(1);
                    d.name = "Ableton Link";
                    d.noWaveformSource = true;
                    d.hasTrack = true;
                    d.title = "Ableton Link";
                    d.artist = s.peers() == 1 ? "1 program" : s.peers() + " programs";
                    d.trackBpm = s.bpm();
                    d.effectiveBpm = s.bpm();
                    d.playing = s.playing() == null || s.playing();
                    d.positionMs = -1;
                    if (!"monitoring".equals(hub.phase())) {
                        hub.setState("monitoring", "live", "Connected");
                    }
                }
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "link-tempo");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void stop() {
        stopped = true;
        link.stop();
    }
}
