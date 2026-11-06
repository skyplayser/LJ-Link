package dev.ljlinkpro;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChamsysTest {
    @Test
    void aTapIsAnOscMessageWithoutArgument() {
        // "/tap" + NUL padding to 8 bytes, then the empty type tag "," + padding to 4 bytes
        byte[] expected = {'/', 't', 'a', 'p', 0, 0, 0, 0, ',', 0, 0, 0};
        assertArrayEquals(expected, LinkService.oscTap("/tap"));
        assertEquals(12, LinkService.oscTap("/tap1").length, "five characters and the NUL pad to 8 bytes, plus the type tag");
    }

    @Test
    void addressListsAreCleaned() {
        assertEquals(List.of("/tap1", "/tap2", "/tap3"), LinkService.tapAddresses(" /tap1, /tap2;/tap3 /tap1 "));
        assertEquals(List.of("/tap"), LinkService.tapAddresses(""), "empty means the default");
        assertEquals(List.of("/tap"), LinkService.tapAddresses(null));
        assertEquals(List.of("/a"), LinkService.tapAddresses("tap, /a, /"), "entries without a slash are dropped");
    }

    @Test
    void oneMessagePerAddressArrivesAtTheDesk() throws Exception {
        try (DatagramSocket desk = new DatagramSocket(0, java.net.InetAddress.getLoopbackAddress())) {
            desk.setSoTimeout(2000);
            LinkService.Config c = new LinkService.Config("chamsys", "127.0.0.1", desk.getLocalPort(), "/tap1,/tap2", "", "", "");
            new LinkService(new Hub(), false).sendTaps(c);
            List<String> got = new java.util.ArrayList<>();
            for (int i = 0; i < 2; i++) {
                DatagramPacket p = new DatagramPacket(new byte[64], 64);
                desk.receive(p);
                got.add(new String(p.getData(), 0, p.getLength(), StandardCharsets.ISO_8859_1));
            }
            assertEquals(List.of("/tap1\0\0\0,\0\0\0", "/tap2\0\0\0,\0\0\0"), got);
        }
    }
}
