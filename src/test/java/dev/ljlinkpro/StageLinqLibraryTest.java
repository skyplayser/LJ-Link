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

    /** A tiny Engine-like library (one track with a hot cue) as the bytes a device would send. */
    private static byte[] smallLibrary(java.nio.file.Path dir) throws Exception {
        java.nio.file.Path f = dir.resolve("source.db");
        try (java.sql.Connection c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + f);
             java.sql.Statement st = c.createStatement()) {
            st.executeUpdate("create table Track (id integer, albumArtId integer, path text)");
            st.executeUpdate("create table PerformanceData (trackId integer, trackData blob, overviewWaveFormData blob, "
                    + "beatData blob, quickCues blob, loops blob)");
            st.executeUpdate("insert into Track values (1, 0, 'Music/A/b.flac')");
            st.executeUpdate("insert into PerformanceData values (1, null, null, null, null, null)");
        }
        byte[] bytes = java.nio.file.Files.readAllBytes(f);
        java.nio.file.Files.delete(f);
        return bytes;
    }

    private static long copiesIn(java.nio.file.Path dir) throws java.io.IOException {
        try (var s = java.nio.file.Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().endsWith("-m.db")).count();
        }
    }

    @Test
    void libraryCopyIsDeletedRightAfterLoading() throws Exception {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("ljlinkpro-lib-test");
        byte[] data = smallLibrary(dir);
        try (StageLinqLibrary lib = StageLinqLibrary.open("sda1", data, dir, StageLinqLibrary.MEMORY_LIMIT)) {
            assertEquals(0, copiesIn(dir), "no copy of the DJ's library stays in the temp folder");
            org.junit.jupiter.api.Assertions.assertNotNull(lib.find("/sda1/Engine Library/Music/A/b.flac"), "still readable from memory");
            assertNull(lib.find("/sda1/Engine Library/Music/other.flac"));
        }
    }

    @Test
    void veryLargeLibraryKeepsItsCopyOnlyWhileOpen() throws Exception {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("ljlinkpro-lib-test");
        byte[] data = smallLibrary(dir);
        StageLinqLibrary lib = StageLinqLibrary.open("sda1", data, dir, 1);
        assertEquals(1, copiesIn(dir));
        org.junit.jupiter.api.Assertions.assertNotNull(lib.find("/sda1/Engine Library/Music/A/b.flac"));
        lib.close();
        assertEquals(0, copiesIn(dir), "deleted when the library is closed");
    }

    @Test
    void reconnectingNeverSharesTheTemporaryFile() throws Exception {
        // The new library opens before the old one is closed (StageLinqSource.runFiles); same source name, file kept.
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("ljlinkpro-lib-test");
        byte[] data = smallLibrary(dir);
        StageLinqLibrary first = StageLinqLibrary.open("USB 1", data, dir, 0);
        StageLinqLibrary second = StageLinqLibrary.open("USB 1", data, dir, 0);
        assertEquals(2, copiesIn(dir), "one file each");
        first.close();
        assertEquals(1, copiesIn(dir), "closing the old library leaves the new one's file alone");
        org.junit.jupiter.api.Assertions.assertNotNull(second.find("/USB 1/Engine Library/Music/A/b.flac"));
        second.close();
        assertEquals(0, copiesIn(dir));
        assertEquals(0, StageLinqLibrary.deleteLeftovers(dir));
    }

    @Test
    void uniqueNamesMatchTheCleanupPattern() throws Exception {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("ljlinkpro-lib-test");
        StageLinqLibrary lib = StageLinqLibrary.open("sda1", smallLibrary(dir), dir, 0);
        try (var s = java.nio.file.Files.list(dir)) {
            String name = s.findFirst().orElseThrow().getFileName().toString();
            org.junit.jupiter.api.Assertions.assertTrue(name.startsWith("ljlinkpro-" + ProcessHandle.current().pid() + "-")
                    && name.endsWith("-m.db"), name);
        }
        assertEquals(0, StageLinqLibrary.deleteLeftovers(dir), "this run is alive: its copy stays");
        lib.close();
    }

    @Test
    void leftoversOfEndedRunsAreRemovedAtStart() throws Exception {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("ljlinkpro-lib-test");
        java.nio.file.Path old = java.nio.file.Files.writeString(dir.resolve("ljlinkpro-sda1-m.db"), "x"); // before 1.7.8
        java.nio.file.Path ended = java.nio.file.Files.writeString(dir.resolve("ljlinkpro-" + 999_999_999 + "-sda1-m.db"), "x");
        java.nio.file.Path running = java.nio.file.Files.writeString(
                dir.resolve("ljlinkpro-" + ProcessHandle.current().pid() + "-sda1-m.db"), "x");
        java.nio.file.Path other = java.nio.file.Files.writeString(dir.resolve("something-else.db"), "x");
        assertEquals(2, StageLinqLibrary.deleteLeftovers(dir));
        org.junit.jupiter.api.Assertions.assertFalse(java.nio.file.Files.exists(old));
        org.junit.jupiter.api.Assertions.assertFalse(java.nio.file.Files.exists(ended));
        org.junit.jupiter.api.Assertions.assertTrue(java.nio.file.Files.exists(running), "a running LJ Link keeps its copy");
        org.junit.jupiter.api.Assertions.assertTrue(java.nio.file.Files.exists(other));
    }
}
