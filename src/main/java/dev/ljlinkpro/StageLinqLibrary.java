package dev.ljlinkpro;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Reads the Engine library (m.db, SQLite) that Denon devices keep on their media: overview waveform, beat grid, cues
 * and loops per track. Formats as documented by the open source project libdjinterop (LGPL, not used as code): every
 * blob is a zlib stream behind a 4 byte big endian length (Qt's qCompress).
 */
final class StageLinqLibrary implements AutoCloseable {
    record Cue(long ms, int hot, String label, String color, boolean loop, long loopEndMs) {}

    record TrackData(byte[] waveform, byte[] bands, int[] beatTimes, byte[] beatBars, List<Map<String, Object>> cues, byte[] art) {}

    /** Libraries up to this size are copied into memory, so the temporary file can be deleted right away. */
    static final long MEMORY_LIMIT = 256L * 1024 * 1024;
    private static final String TEMP_PREFIX = "ljlinkpro-";
    private static final String TEMP_SUFFIX = "-m.db";

    private final Connection db;
    /** The temporary copy that stays while the library is open (only for very large libraries), else null. */
    private final Path keptFile;

    StageLinqLibrary(Path file) throws SQLException {
        db = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
        keptFile = null;
    }

    private StageLinqLibrary(Connection db, Path keptFile) {
        this.db = db;
        this.keptFile = keptFile;
    }

    /**
     * Looks a track up by the path the device reports. The library stores it relative to "Engine Library"
     * (for example {@code Music/Artist/Album/file.flac}), the device reports it with a prefix in front.
     */
    TrackData find(String devicePath) throws SQLException {
        if (devicePath == null || devicePath.isBlank()) {
            return null;
        }
        String norm = devicePath.replace('\\', '/');
        int at = norm.indexOf("Engine Library/");
        String relative = at >= 0 ? norm.substring(at + "Engine Library/".length()) : norm;
        String sql = "select t.id, t.albumArtId, p.trackData, p.overviewWaveFormData, p.beatData, p.quickCues, p.loops "
                + "from Track t join PerformanceData p on p.trackId = t.id where t.path = ?";
        try (PreparedStatement st = db.prepareStatement(sql)) {
            st.setString(1, relative);
            try (ResultSet rs = st.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                double sampleRate = sampleRate(rs.getBytes(3));
                List<Integer> times = new ArrayList<>();
                List<Byte> bars = new ArrayList<>();
                beats(rs.getBytes(5), times, bars);
                byte[] barArray = new byte[bars.size()];
                for (int i = 0; i < barArray.length; i++) {
                    barArray[i] = bars.get(i);
                }
                return new TrackData(waveform(rs.getBytes(4)), bands(rs.getBytes(4)), times.stream().mapToInt(Integer::intValue).toArray(), barArray,
                        cueMaps(cues(rs.getBytes(6), rs.getBytes(7), sampleRate)), art(rs.getInt(2)));
            }
        }
    }

    private byte[] art(int id) {
        if (id <= 1) {
            return null;
        }
        try (PreparedStatement st = db.prepareStatement("select albumArt from AlbumArt where id = ?")) {
            st.setInt(1, id);
            try (ResultSet rs = st.executeQuery()) {
                byte[] b = rs.next() ? rs.getBytes(1) : null;
                boolean jpeg = b != null && b.length > 4 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8;
                boolean png = b != null && b.length > 4 && (b[0] & 0xFF) == 0x89 && b[1] == 'P';
                return jpeg || png ? b : null;
            }
        } catch (SQLException e) {
            return null;
        }
    }

    /* ---------- blobs ---------- */

    static byte[] unpack(byte[] blob) {
        if (blob == null || blob.length < 5) {
            return null;
        }
        int size = ByteBuffer.wrap(blob).getInt();
        if (size < 0 || size > 64 * 1024 * 1024) {
            return null;
        }
        Inflater inf = new Inflater();
        try {
            inf.setInput(blob, 4, blob.length - 4);
            ByteArrayOutputStream out = new ByteArrayOutputStream(size);
            byte[] buf = new byte[8192];
            while (!inf.finished()) {
                int n = inf.inflate(buf);
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) {
                    break;
                }
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } catch (DataFormatException e) {
            return null;
        } finally {
            inf.end();
        }
    }

    private static double sampleRate(byte[] trackData) {
        byte[] d = unpack(trackData);
        return d != null && d.length >= 8 ? ByteBuffer.wrap(d).getDouble() : 44100;
    }

    /**
     * Overview waveform: a 24 byte header (count, count, samples per entry) and three bytes per entry (low, mid, high).
     * Result in the format of the {@code threeband} preview: cumulative layers of 4 bytes per segment.
     */
    static byte[] waveform(byte[] blob) {
        byte[] d = unpack(blob);
        if (d == null || d.length < 27) {
            return null;
        }
        int n = entries(d);
        int max = 1;
        for (int i = 0; i < n * 3; i++) {
            max = Math.max(max, d[24 + i] & 0xFF);
        }
        byte[] out = new byte[n * 4];
        for (int i = 0; i < n; i++) {
            int low = (d[24 + i * 3] & 0xFF) * 255 / max;
            int mid = (d[24 + i * 3 + 1] & 0xFF) * 255 / max;
            int high = (d[24 + i * 3 + 2] & 0xFF) * 255 / max;
            out[i * 4] = (byte) low;
            out[i * 4 + 1] = (byte) Math.max(low, mid);
            out[i * 4 + 2] = (byte) Math.max(Math.max(low, mid), high);
        }
        return out;
    }

    /**
     * Number of waveform entries: the header states it, the data holds one more entry at the end that is not part of the
     * track (it showed up as a spike at the very end of the detail view).
     */
    private static int entries(byte[] d) {
        int stored = (d.length - 24) / 3;
        long declared = ByteBuffer.wrap(d).getLong();
        return declared > 0 && declared < stored ? (int) declared : stored;
    }

    /** The overview bands (low, mid, high) as 3 bytes per entry, scaled to the loudest value. */
    static byte[] bands(byte[] blob) {
        byte[] d = unpack(blob);
        if (d == null || d.length < 27) {
            return null;
        }
        int n = entries(d);
        int max = 1;
        for (int i = 0; i < n * 3; i++) {
            max = Math.max(max, d[24 + i] & 0xFF);
        }
        byte[] out = new byte[n * 3];
        for (int i = 0; i < n * 3; i++) {
            out[i] = (byte) ((d[24 + i] & 0xFF) * 255 / max);
        }
        return out;
    }

    /**
     * The overview resampled to the 150 frames per second of the detail view: smooth, but not more detailed than the source.
     * The detail view paints the outermost layer first, so the layers are the loudest band, the mid and high part, and the high band.
     */
    static byte[] detailFrom(byte[] bands, long durationMs) {
        if (bands == null || bands.length < 6 || durationMs <= 0) {
            return null;
        }
        int n = bands.length / 3;
        int frames = (int) (durationMs * 150 / 1000);
        byte[] out = new byte[frames * 4];
        for (int f = 0; f < frames; f++) {
            double x = (double) f / frames * n - 0.5;
            int a = Math.max(0, Math.min(n - 1, (int) Math.floor(x)));
            int b = Math.min(n - 1, a + 1);
            double w = Math.max(0, Math.min(1, x - a));
            double[] v = new double[3];
            for (int k = 0; k < 3; k++) {
                v[k] = (bands[a * 3 + k] & 0xFF) * (1 - w) + (bands[b * 3 + k] & 0xFF) * w;
            }
            out[f * 4] = (byte) Math.round(Math.max(v[0], Math.max(v[1], v[2])));
            out[f * 4 + 1] = (byte) Math.round((v[1] + v[2]) / 2);
            out[f * 4 + 2] = (byte) Math.round(v[2]);
        }
        return out;
    }

    /** Beat grid: markers (sample offset, beat number, beats until the next marker) interpolated to one time per beat. */
    static void beats(byte[] blob, List<Integer> times, List<Byte> bars) {
        byte[] d = unpack(blob);
        if (d == null || d.length < 25) {
            return;
        }
        ByteBuffer b = ByteBuffer.wrap(d);
        double rate = b.getDouble();
        double totalSamples = b.getDouble();
        b.get();
        long count = b.getLong();
        if (rate < 8000 || count < 2 || count > 100_000 || b.remaining() < count * 24) {
            return;
        }
        b.order(ByteOrder.LITTLE_ENDIAN);
        double[] offset = new double[(int) count];
        long[] number = new long[(int) count];
        int[] span = new int[(int) count];
        for (int i = 0; i < count; i++) {
            offset[i] = b.getDouble();
            number[i] = b.getLong();
            span[i] = b.getInt();
            b.getInt();
        }
        for (int i = 0; i < count - 1; i++) {
            long beats = number[i + 1] - number[i];
            if (beats <= 0) {
                continue;
            }
            double step = (offset[i + 1] - offset[i]) / beats;
            for (long k = 0; k < beats; k++) {
                double ms = (offset[i] + k * step) * 1000.0 / rate;
                if (ms >= 0) {
                    times.add((int) Math.round(ms));
                    bars.add((byte) (Math.floorMod(number[i] + k, 4) + 1));
                }
            }
        }
        // The last marker ends the grid.
        double lastMs = offset[(int) count - 1] * 1000.0 / rate;
        if (lastMs >= 0 && (times.isEmpty() || lastMs > times.get(times.size() - 1))) {
            times.add((int) Math.round(lastMs));
            bars.add((byte) (Math.floorMod(number[(int) count - 1], 4) + 1));
        }
    }

    /** Hot cues (8 slots) and stored loops; the colors are ARGB in the library. */
    static List<Cue> cues(byte[] quickCues, byte[] loops, double rate) {
        List<Cue> list = new ArrayList<>();
        byte[] q = unpack(quickCues);
        if (q != null && q.length >= 8) {
            ByteBuffer b = ByteBuffer.wrap(q);
            long count = b.getLong();
            for (int i = 0; i < count && i < 16 && b.remaining() > 13; i++) {
                int len = b.get() & 0xFF;
                if (b.remaining() < len + 12) {
                    break;
                }
                byte[] text = new byte[len];
                b.get(text);
                double pos = b.getDouble();
                int argb = b.getInt();
                if (pos >= 0) {
                    list.add(new Cue(Math.round(pos * 1000.0 / rate), i + 1, new String(text, java.nio.charset.StandardCharsets.UTF_8),
                            String.format("#%06x", argb & 0xFFFFFF), false, 0));
                }
            }
        }
        byte[] l = unpack(loops);
        if (l != null && l.length >= 8) {
            ByteBuffer b = ByteBuffer.wrap(l).order(ByteOrder.LITTLE_ENDIAN);
            long count = b.getLong();
            for (int i = 0; i < count && i < 16 && b.remaining() > 1; i++) {
                int len = b.get() & 0xFF;
                if (b.remaining() < len + 22) {
                    break;
                }
                byte[] text = new byte[len];
                b.get(text);
                double start = b.getDouble();
                double end = b.getDouble();
                boolean startSet = b.get() != 0;
                boolean endSet = b.get() != 0;
                b.getInt();
                if (startSet && endSet && end > start) {
                    list.add(new Cue(Math.round(start * 1000.0 / rate), 0, new String(text, java.nio.charset.StandardCharsets.UTF_8),
                            "#ff8c00", true, Math.round(end * 1000.0 / rate)));
                }
            }
        }
        return list;
    }

    /**
     * Opens the downloaded library. The bytes go to a temporary file (SQLite needs one) that is copied into an in-memory
     * database and deleted again straight away, so the DJ's library does not stay on this computer. Only a library larger
     * than {@link #MEMORY_LIMIT} is read from the file; that file is deleted when the library is closed, at the latest
     * when the program quits, and leftovers from a crash are removed at the next start ({@link #deleteLeftovers}).
     */
    static StageLinqLibrary open(String source, byte[] data) throws IOException, SQLException {
        return open(source, data, Path.of(System.getProperty("java.io.tmpdir")), MEMORY_LIMIT);
    }

    static StageLinqLibrary open(String source, byte[] data, Path tempDir, long memoryLimit) throws IOException, SQLException {
        // A unique name per copy: reconnecting (the new library opens before the old one closes) and two devices with the
        // same source name never share a file. The prefix keeps the pattern that deleteLeftovers looks for.
        Path file = Files.createTempFile(tempDir, TEMP_PREFIX + ProcessHandle.current().pid() + "-"
                + source.replaceAll("[^A-Za-z0-9]+", "_") + "-", TEMP_SUFFIX);
        Files.write(file, data);
        file.toFile().deleteOnExit(); // fallback only
        boolean keep = data.length > memoryLimit;
        try {
            if (keep) {
                return new StageLinqLibrary(DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath()), file);
            }
            Connection memory = DriverManager.getConnection("jdbc:sqlite::memory:");
            try (java.sql.Statement st = memory.createStatement()) {
                // sqlite-jdbc command: copies the whole database file into the in-memory database.
                st.executeUpdate("restore from '" + file.toAbsolutePath().toString().replace("'", "''") + "'");
            } catch (SQLException e) {
                memory.close();
                throw e;
            }
            return new StageLinqLibrary(memory, null);
        } finally {
            if (!keep) {
                Files.deleteIfExists(file);
            }
        }
    }

    /**
     * Deletes temporary library copies ({@code ljlinkpro-<pid>-<source>-<random>-m.db}) that a program which is no longer running
     * left behind, for example after a crash. Copies of a running LJ Link (another instance) stay. Returns how many were
     * deleted.
     */
    static int deleteLeftovers(Path tempDir) {
        int deleted = 0;
        try (var files = Files.newDirectoryStream(tempDir, TEMP_PREFIX + "*" + TEMP_SUFFIX)) {
            for (Path f : files) {
                String name = f.getFileName().toString();
                String middle = name.substring(TEMP_PREFIX.length(), name.length() - TEMP_SUFFIX.length());
                String pid = middle.contains("-") ? middle.substring(0, middle.indexOf('-')) : "";
                // Names from before 1.7.8 have no process number; they are always leftovers.
                try {
                    boolean running = pid.matches("\\d{1,9}")
                            && ProcessHandle.of(Long.parseLong(pid)).map(ProcessHandle::isAlive).orElse(false);
                    if (!running && Files.deleteIfExists(f)) {
                        deleted++;
                    }
                } catch (IOException | RuntimeException e) {
                    System.err.println("Could not delete " + f + ": " + e);
                }
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("Could not clean up old library copies: " + e);
        }
        return deleted;
    }

    static int deleteLeftovers() {
        return deleteLeftovers(Path.of(System.getProperty("java.io.tmpdir")));
    }

    /** Cues in the shape the display expects. */
    static List<Map<String, Object>> cueMaps(List<Cue> cues) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Cue c : cues) {
            out.add(Map.of("t", c.ms(), "hot", c.hot(), "loop", c.loop(), "loopEnd", c.loopEndMs(), "color", c.color()));
        }
        return out;
    }

    @Override
    public void close() {
        try {
            db.close();
        } catch (SQLException e) {
            // closing is best effort
        }
        if (keptFile != null) {
            try {
                Files.deleteIfExists(keptFile);
            } catch (IOException e) {
                // deleteOnExit and the cleanup at the next start remove it
            }
        }
    }
}
