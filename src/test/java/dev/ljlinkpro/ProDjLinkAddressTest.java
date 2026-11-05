package dev.ljlinkpro;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.Inet6Address;
import java.net.InetAddress;
import org.junit.jupiter.api.Test;

class ProDjLinkAddressTest {
    @Test
    void mappedAddressBecomesIpv4() throws Exception {
        // As reported by a Windows tester: 0:0:0:0:0:ffff:a9fe:7e8%15 is the CDJ at 169.254.7.232.
        byte[] b = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xff, (byte) 0xff, (byte) 169, (byte) 254, 7, (byte) 232};
        assertEquals("169.254.7.232", ProDjLinkSource.ip(Inet6Address.getByAddress(null, b, 15)));
    }

    @Test
    void plainAddressesStayAsTheyAre() throws Exception {
        assertEquals("192.168.1.20", ProDjLinkSource.ip(InetAddress.getByName("192.168.1.20")));
        assertEquals("fe80:0:0:0:0:0:0:1", ProDjLinkSource.ip(InetAddress.getByName("fe80::1")));
    }
}
