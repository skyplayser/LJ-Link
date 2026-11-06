package dev.ljlinkpro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class DaslightTest {
    private static float receiveValue(DatagramSocket desk, String address) throws Exception {
        DatagramPacket p = new DatagramPacket(new byte[64], 64);
        desk.receive(p);
        ByteBuffer b = ByteBuffer.wrap(p.getData(), 0, p.getLength());
        byte[] a = new byte[8];
        b.get(a);
        assertEquals(address, new String(a, 0, address.length(), java.nio.charset.StandardCharsets.ISO_8859_1));
        byte[] tag = new byte[4];
        b.get(tag);
        assertEquals(",f", new String(tag, 0, 2, java.nio.charset.StandardCharsets.ISO_8859_1), "a value is sent, not an empty message");
        return b.getFloat();
    }

    @Test
    void aTapIsAPressOf255AndARelease() throws Exception {
        try (DatagramSocket desk = new DatagramSocket(0, java.net.InetAddress.getLoopbackAddress())) {
            desk.setSoTimeout(2000);
            LinkService.Config c = new LinkService.Config("daslight", "127.0.0.1", desk.getLocalPort(), "/tap", "", "", "");
            new LinkService(new Hub(), false).sendTaps(c);
            assertEquals(255f, receiveValue(desk, "/tap"));
            assertEquals(0f, receiveValue(desk, "/tap"));
        }
    }

    @Test
    void daslightCountsAsATapType() {
        assertTrue(LinkService.isTap("daslight"));
        assertTrue(LinkService.isTap("chamsys"));
        assertEquals(false, LinkService.isTap("osc"));
    }
}
