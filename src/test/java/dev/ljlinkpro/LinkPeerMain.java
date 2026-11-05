package dev.ljlinkpro;

/**
 * Development aid, not part of the product: leads an Ableton Link session from the demo decks, presented as Pioneer
 * decks so that they may lead. Prints the session and the deck beat twice a second with the wall clock, so a second
 * Link program can be compared against it.
 * Run: {@code java -Dljlinkpro.home=/tmp/x -cp build/classes/java/main:build/classes/java/test:<libs> dev.ljlinkpro.LinkPeerMain}
 */
public final class LinkPeerMain {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.net.preferIPv4Stack", "true");
        Hub hub = new Hub();
        MockSource demo = new MockSource(hub.scope("pioneer", "pioneer", true, "Pro DJ Link"));
        demo.start();
        LinkService desk = new LinkService(hub, false);
        AbletonLinkPeer peer = new AbletonLinkPeer(hub, desk);
        peer.configure(true, !"nophase".equals(args.length > 0 ? args[0] : ""));
        while (true) {
            Thread.sleep(500);
            Deck lead = desk.leadingDeck();
            Double beat = lead == null ? null : AbletonLinkPeer.deckBeat(lead, System.nanoTime());
            AbletonLinkPeer.Status s = peer.status();
            System.out.printf(java.util.Locale.ROOT, "%.3f deck %s %.2f BPM beat %s | session %.3f BPM, %d peers %s%n",
                    System.currentTimeMillis() / 1000.0, lead == null ? "-" : lead.key(), lead == null ? 0 : lead.effectiveBpm,
                    beat == null ? "-" : String.format(java.util.Locale.ROOT, "%.3f", beat % 4), s.bpm(), s.peers(), s.note());
        }
    }
}
