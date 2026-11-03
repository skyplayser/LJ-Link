package dev.ljlinkpro;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeratoTest {
    @Test
    void theLogTellsWhichFileIsOnWhichDeck() {
        String line = "I20261103 07:26:52.241403 38584192 :0] [Audio] Creating audio cache store resource for "
                + "'cache:///Users/dj/Musik/Track One.aif:deck_0_instrumental?sample_rate=44100&length=441000'";
        Matcher m = SeratoSource.LOAD.matcher(line);
        assertTrue(m.find());
        assertEquals("/Users/dj/Musik/Track One.aif", m.group(1));
        assertEquals("0", m.group(2));
        assertEquals("441000", m.group(4));
    }

    @Test
    void theBeatGridGivesOneTimePerBeat() {
        ByteBuffer g = ByteBuffer.allocate(2 + 4 + 8 + 8);
        g.put((byte) 1).put((byte) 0).putInt(2);
        g.putFloat(1.0f).putInt(4);       // from 1 s, 4 beats to the next marker
        g.putFloat(3.0f).putFloat(120f);  // last marker at 3 s, 120 BPM onwards
        List<Integer> times = new ArrayList<>();
        List<Byte> bars = new ArrayList<>();
        SeratoTags.beatGrid(g.array(), 4000, times, bars);
        assertEquals(List.of(1000, 1500, 2000, 2500, 3000, 3500, 4000), times);
        assertEquals((byte) 1, bars.get(0));
        assertEquals((byte) 1, bars.get(4));
    }

    @Test
    void theOverviewHeightCountsLitPixels() {
        byte[] data = new byte[2 + 16];
        java.util.Arrays.fill(data, (byte) 1);
        for (int r = 5; r < 11; r++) {
            data[2 + r] = (byte) 80;
        }
        byte[] w = SeratoTags.overview(data);
        assertNotNull(w);
        assertEquals(6 * 255 / 12, w[0] & 0xFF);
    }

    @Test
    void abletonLinkAnnouncementsCarryTheTempo() {
        ByteBuffer b = ByteBuffer.allocate(8 + 1 + 1 + 2 + 8 + 8 + 24);
        b.put(new byte[] {'_', 'a', 's', 'd', 'p', '_', 'v', 1}).put((byte) 1).put((byte) 5).putShort((short) 0);
        b.put(new byte[] {1, 2, 3, 4, 5, 6, 7, 8});
        b.putInt(('t' << 24) | ('m' << 16) | ('l' << 8) | 'n').putInt(24).putLong(500_000).putLong(0).putLong(0);
        Object[] m = AbletonLink.parse(b.array(), b.capacity());
        assertNotNull(m);
        assertEquals(120.0, (Double) m[1], 1e-9);
    }
}
