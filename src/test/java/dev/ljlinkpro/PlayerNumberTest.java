package dev.ljlinkpro;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import org.deepsymmetry.beatlink.Beat;
import org.deepsymmetry.beatlink.DeviceAnnouncement;
import org.junit.jupiter.api.Test;

/**
 * Issue #2 (XDJ-AZ, Alpha, no hardware): LJ Link's own player number, beat packets without a status, and the same beat
 * packet twice (two adapters in the players' network).
 */
class PlayerNumberTest {
    private static final byte[] HEADER = {0x51, 0x73, 0x70, 0x74, 0x31, 0x57, 0x6d, 0x4a, 0x4f, 0x4c};

    private static DeviceAnnouncement announcement(String name, int number, String address) throws Exception {
        byte[] b = new byte[0x36];
        System.arraycopy(HEADER, 0, b, 0, HEADER.length);
        b[0x0a] = 0x06;
        byte[] n = name.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(n, 0, b, 0x0c, n.length);
        b[0x24] = (byte) number;
        return new DeviceAnnouncement(new DatagramPacket(b, b.length, InetAddress.getByName(address), 50000));
    }

    static Beat beat(int player, int inBar, String address) throws Exception {
        byte[] b = new byte[0x60];
        System.arraycopy(HEADER, 0, b, 0, HEADER.length);
        b[0x0a] = 0x28;
        byte[] n = "XDJ-AZ".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(n, 0, b, 0x0b, n.length);
        b[0x21] = (byte) player;
        b[0x55] = 0x10; // pitch 0x100000 = normal speed
        b[0x5a] = (byte) (12800 >> 8); // 128.00 BPM
        b[0x5b] = (byte) (12800 & 0xff);
        b[0x5c] = (byte) inBar;
        return new Beat(new DatagramPacket(b, b.length, InetAddress.getByName(address), 50001));
    }

    @Test
    void ownNumberSkipsEveryNumberSeenAndTheXdjAzSeven() {
        assertEquals(7, ProDjLinkSource.chooseOwnNumber(Set.of(), Set.of(), Set.of()), "nothing seen: 7 as beat-link");
        assertEquals(7, ProDjLinkSource.chooseOwnNumber(Set.of(1, 2, 3, 4, 33), Set.of("CDJ-3000", "DJM-A9"), Set.of()));
        // The diagnostics of issue #2: an XDJ-AZ as 1-4, 7 and 33.
        assertEquals(8, ProDjLinkSource.chooseOwnNumber(Set.of(1, 2, 3, 4, 7, 33), Set.of("XDJ-AZ"), Set.of()));
        // The XDJ-AZ is assumed to take 7 also when it was not seen announcing it yet.
        assertEquals(8, ProDjLinkSource.chooseOwnNumber(Set.of(1, 2, 3, 4, 33), Set.of("XDJ-AZ "), Set.of()));
        assertEquals(10, ProDjLinkSource.chooseOwnNumber(Set.of(7, 9), Set.of(), Set.of(8)), "numbers given up are skipped");
        assertEquals(0, ProDjLinkSource.chooseOwnNumber(Set.of(7, 8, 9, 10, 11, 12, 13, 14, 15), Set.of(), Set.of()),
                "all taken: beat-link decides");
    }

    @Test
    void aDeviceWithLjLinksNumberIsAConflict() throws Exception {
        List<DeviceAnnouncement> az = List.of(announcement("XDJ-AZ", 1, "192.168.1.143"),
                announcement("XDJ-AZ", 7, "192.168.1.143"), announcement("XDJ-AZ", 33, "192.168.1.143"));
        assertSame(az.get(1), ProDjLinkSource.numberTakenBy(7, az));
        assertNull(ProDjLinkSource.numberTakenBy(8, az));
        assertNull(ProDjLinkSource.numberTakenBy(0, az), "0 = not chosen yet");
    }

    @Test
    void aBeatBeforeAnyStatusDoesNotThrow() throws Exception {
        Hub hub = new Hub();
        ProDjLinkSource s = new ProDjLinkSource(hub.scope("pioneer", "pioneer", true, "Pro DJ Link"));
        Beat b = beat(2, 3, "192.168.1.143");
        assertEquals(0, ProDjLinkSource.beatInBar(b, null), "no status: the beat in the bar is unknown");
        assertDoesNotThrow(() -> s.onBeat(b));
        Deck d = hub.scope("pioneer", "pioneer", true, "Pro DJ Link").deck(2);
        assertEquals(1, d.beatPacketCount);
        assertEquals(0, d.beatPacketInBar);
    }

    @Test
    void theSameBeatTwiceCountsOnce() throws Exception {
        Hub hub = new Hub();
        Hub.Scope sc = hub.scope("pioneer", "pioneer", true, "Pro DJ Link");
        ProDjLinkSource s = new ProDjLinkSource(sc);
        s.onBeat(beat(1, 1, "192.168.1.143"));
        s.onBeat(beat(1, 1, "192.168.1.143")); // the copy from the second adapter, microseconds later
        assertEquals(1, sc.deck(1).beatPacketCount, "a copy is not a second beat for the light desk");
        s.onBeat(beat(1, 2, "192.168.1.143"));
        assertEquals(2, sc.deck(1).beatPacketCount, "the next beat counts");

        Beat first = beat(3, 4, "192.168.1.143");
        Beat copy = beat(3, 4, "192.168.1.143");
        assertTrue(ProDjLinkSource.isDuplicateBeat(first, 1_000_000_000L, copy, 1_000_500_000L));
        assertFalse(ProDjLinkSource.isDuplicateBeat(first, 1_000_000_000L, copy, 1_000_000_000L + ProDjLinkSource.DUPLICATE_BEAT_NANOS),
                "the same beat in the bar after 30 ms is a new beat (a loop of one beat)");
        assertFalse(ProDjLinkSource.isDuplicateBeat(first, 1_000_000_000L, beat(3, 4, "192.168.1.144"), 1_000_500_000L),
                "another device with the same number is not a copy");
        assertFalse(ProDjLinkSource.isDuplicateBeat(null, 0, copy, 1_000_500_000L));
    }
}
