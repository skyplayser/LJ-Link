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
                    apply(hub.deck(1), s);
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

    /** Shows the session on its deck. */
    static void apply(Deck d, AbletonLink.State s) {
        d.name = "Ableton Link";
        d.noWaveformSource = true;
        d.exactTempo = true; // the session tempo is set by a program and has no jitter: every step counts (ADR-021)
        d.hasTrack = true;
        d.title = "Ableton Link";
        d.artist = s.peers() == 1 ? "1 program" : s.peers() + " programs";
        d.trackBpm = s.bpm();
        d.effectiveBpm = s.bpm();
        d.playing = s.playing() == null || s.playing();
        d.positionMs = -1;
    }

    @Override
    public void stop() {
        stopped = true;
        link.stop();
    }

    @Override
    public java.util.List<String> diagnostics() {
        AbletonLink.State st = link.state();
        return java.util.List.of("Ableton Link: " + (st == null ? "no session seen" : st.peers() + " peer(s), "
                + String.format(java.util.Locale.ROOT, "%.2f", st.bpm()) + " BPM"));
    }
}
