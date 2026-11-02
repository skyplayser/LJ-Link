package dev.ljlinkpro;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Deflater;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StageLinqLibraryTest {
    /** Packs bytes like Qt's qCompress: 4 byte big endian length, then a zlib stream. */
    private static byte[] pack(byte[] raw) {
        Deflater deflater = new Deflater();
        deflater.setInput(raw);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(ByteBuffer.allocate(4).putInt(raw.length).array());
        byte[] buf = new byte[1024];
        while (!deflater.finished()) {
            out.write(buf, 0, deflater.deflate(buf));
        }
        return out.toByteArray();
    }

    @Test
    void overviewWaveformBecomesLayeredEntries() {
        ByteBuffer b = ByteBuffer.allocate(24 + 6);
        b.putLong(2).putLong(2).putDouble(1000);
        b.put(new byte[] {10, 20, 5, 100, 50, 25});
        byte[] bands = StageLinqLibrary.bands(pack(b.array()));
        assertArrayEquals(new byte[] {25, 51, 12, (byte) 255, 127, 63}, bands);
        byte[] wave = StageLinqLibrary.waveform(pack(b.array()));
        assertEquals(8, wave.length);
        assertEquals(255, wave[4] & 0xFF);
        assertEquals(255, wave[5] & 0xFF);
    }

    @Test
    void beatGridMarkersBecomeOneTimePerBeat() {
        ByteBuffer b = ByteBuffer.allocate(8 + 8 + 1 + 8 + 2 * 24);
        b.putDouble(44100).putDouble(441000).put((byte) 1).putLong(2);
        b.order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putDouble(0).putLong(0).putInt(4).putInt(0);
        b.putDouble(88200).putLong(4).putInt(0).putInt(0);
        List<Integer> times = new ArrayList<>();
        List<Byte> bars = new ArrayList<>();
        StageLinqLibrary.beats(pack(b.array()), times, bars);
        assertEquals(List.of(0, 500, 1000, 1500, 2000), times);
        assertEquals(List.of((byte) 1, (byte) 2, (byte) 3, (byte) 4, (byte) 1), bars);
    }

    @Test
    void brokenBlobsGiveNothing() {
        assertNull(StageLinqLibrary.waveform(new byte[] {0, 0, 1, 0, 1, 2, 3}));
        assertNull(StageLinqLibrary.waveform(null));
    }
}
