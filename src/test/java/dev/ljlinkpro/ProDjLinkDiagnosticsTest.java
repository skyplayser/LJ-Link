package dev.ljlinkpro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

import org.deepsymmetry.beatlink.CdjStatus;
import org.junit.jupiter.api.Test;

class ProDjLinkDiagnosticsTest {
    /** A CDJ status packet of the usual length with everything zero except the header, name and player number. */
    private static CdjStatus status(int player) throws Exception {
        byte[] b = new byte[0x11c];
        byte[] header = {0x51, 0x73, 0x70, 0x74, 0x31, 0x57, 0x6d, 0x4a, 0x4f, 0x4c};
        System.arraycopy(header, 0, b, 0, header.length);
        b[0x0a] = 0x0a;
        byte[] name = "XDJ-1000MK2".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(name, 0, b, 0x0b, name.length);
        b[0x21] = (byte) player;
        int payload = b.length - 36;
        b[0x22] = (byte) (payload >> 8);
        b[0x23] = (byte) payload;
        b[0x24] = (byte) player;
        return new CdjStatus(new DatagramPacket(b, b.length, InetAddress.getLoopbackAddress(), 50002));
    }

    @Test
    void theTempoDiagnosticsDoNotBringBackTheDeckOfAPlayerThatLeft() throws Exception {
        Hub hub = new Hub();
        Hub.Scope scope = hub.scope("pioneer", "pioneer", true, "Pro DJ Link");
        ProDjLinkSource source = new ProDjLinkSource(scope);
        source.lastStatus.put(2, status(2));
        scope.deck(2);
        scope.removeDeck(2); // the player was switched off, its status is still there

        java.util.List<String> lines = source.tempoLines();
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("no deck"), lines.get(0));
        assertNull(scope.existingDeck(2), "asking for the diagnostics creates no deck");
        assertTrue(hub.allDecks().isEmpty(), "and the browser gets none");

        source.forgetPlayer(2);
        assertTrue(source.tempoLines().isEmpty(), "a player that left is not listed any more");
    }
}
