package dev.ljlinkpro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.util.List;
import org.junit.jupiter.api.Test;

class AbletonLinkPeerTest {
    /** A playing deck at 120 BPM (a beat every 500 ms) whose grid starts on the given beat of the bar. */
    private static final long NOW = System.nanoTime();

    private static Deck deck(long positionMs, int firstBar) {
        Deck d = new Deck(1);
        int[] times = new int[64];
        byte[] bars = new byte[64];
        for (int i = 0; i < times.length; i++) {
            times[i] = 100 + i * 500;
            bars[i] = (byte) ((firstBar - 1 + i) % 4 + 1);
        }
        d.setBeats(times, bars);
        d.playing = true;
        d.velocity = 1.0;
        d.positionMs = positionMs;
        d.positionArrivedNanos = NOW; // the position is current, nothing to carry on
        return d;
    }

    @Test
    void deckBeatCountsBarsFromTheGrid() {
        long now = NOW;
        // Grid starts on beat 1 of a bar: 1350 ms is half way through the third beat (index 2), count 2.5.
        assertEquals(2.5, AbletonLinkPeer.deckBeat(deck(1350, 1), now) % 4, 0.05);
        // Grid starts on beat 3 of a bar: the same place is on beat 1 of the next bar, count 0.5 modulo 4.
        assertEquals(0.5, AbletonLinkPeer.deckBeat(deck(1350, 3), now) % 4, 0.05);
    }

    @Test
    void noBeatWhenStandingOrWithoutGrid() {
        long now = NOW;
        Deck standing = deck(1350, 1);
        standing.playing = false;
        assertNull(AbletonLinkPeer.deckBeat(standing, now));
        Deck before = deck(50, 1);
        assertNull(AbletonLinkPeer.deckBeat(before, now));
        Deck noGrid = deck(1350, 1);
        noGrid.setBeats(null, null);
        assertNull(AbletonLinkPeer.deckBeat(noGrid, now));
    }

    @Test
    void timelineGivesTheBeatAtAnyTime() {
        var t = new AbletonLinkPeer.Timeline(500_000, 8_000_000, 1_000_000);
        assertEquals(120.0, t.bpm(), 1e-9);
        assertEquals(8.0, t.beatsAt(1_000_000), 1e-9);
        assertEquals(10.5, t.beatsAt(2_250_000), 1e-9);
    }

    @Test
    void sessionHelpers() throws Exception {
        assertEquals(2.0, AbletonLinkPeer.median(List.of(5.0, 1.0, 2.0)), 1e-9);
        assertTrue(AbletonLinkPeer.compare(new byte[] {1, 2}, new byte[] {1, (byte) 0x80}) < 0); // unsigned bytes
        assertTrue(AbletonLinkPeer.sameNetwork(InetAddress.getByName("192.168.1.20"), InetAddress.getByName("192.168.1.99"), 24));
        assertFalse(AbletonLinkPeer.sameNetwork(InetAddress.getByName("192.168.1.20"), InetAddress.getByName("192.168.2.20"), 24));
    }
}
