package dev.ljlinkpro;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads what Serato writes into a music file (ID3 tag of MP3 and AIFF files): title, artist, BPM, key, length and the
 * Serato frames "Serato Overview" (waveform), "Serato BeatGrid" and "Serato Markers2" (cues and loops). The layouts follow
 * the open source documentation of the Serato tags (for example the "serato-tags" project); no code was copied.
 * Only the tag at the start of the file is read, never the audio.
 */
final class SeratoTags {
    record Track(String title, String artist, double bpm, String key, long lengthMs, byte[] waveform,
                 int[] beatTimes, byte[] beatBars, List<Map<String, Object>> cues) {}

    private static final int MAX_TAG = 16 * 1024 * 1024;

    private SeratoTags() {}

    /** The track, or null when the file has no readable ID3 tag (FLAC and MP4 are not read yet). */
    static Track read(Path file) throws IOException {
        byte[] tag = id3(file);
        return tag == null ? null : parse(tag);
    }

    /** The ID3 tag: at the start of an MP3, or in the "ID3 " chunk of an AIFF. */
    static byte[] id3(Path file) throws IOException {
        try (RandomAccessFile f = new RandomAccessFile(file.toFile(), "r")) {
            byte[] head = new byte[12];
            if (f.read(head) < 12) {
                return null;
            }
            if (head[0] == 'I' && head[1] == 'D' && head[2] == '3') {
                f.seek(6);
                byte[] s = new byte[4];
                f.readFully(s);
                int size = syncsafe(s, 0);
                return readAt(f, 0, 10 + size);
            }
            if (head[0] == 'F' && head[1] == 'O' && head[2] == 'R' && head[3] == 'M') {
                long pos = 12;
                long end = f.length();
                while (pos + 8 <= end) {
                    f.seek(pos);
                    byte[] h = new byte[8];
                    f.readFully(h);
                    long len = ByteBuffer.wrap(h, 4, 4).getInt() & 0xFFFFFFFFL;
                    String id = new String(h, 0, 4, StandardCharsets.US_ASCII);
                    if (id.equalsIgnoreCase("ID3 ")) {
                        return readAt(f, pos + 8, len);
                    }
                    pos += 8 + len + (len & 1);
                }
            }
            return null;
        }
    }

    private static byte[] readAt(RandomAccessFile f, long pos, long len) throws IOException {
        if (len <= 10 || len > MAX_TAG) {
            return null;
        }
        byte[] b = new byte[(int) len];
        f.seek(pos);
        f.readFully(b);
        return b;
    }

    private static int syncsafe(byte[] b, int o) {
        return (b[o] & 0x7F) << 21 | (b[o + 1] & 0x7F) << 14 | (b[o + 2] & 0x7F) << 7 | (b[o + 3] & 0x7F);
    }

    /** Parses an ID3v2.3 / 2.4 tag. Package-private for the tests. */
    static Track parse(byte[] t) {
        if (t.length < 10 || t[0] != 'I' || t[1] != 'D' || t[2] != '3') {
            return null;
        }
        int version = t[3];
        int size = Math.min(t.length, 10 + syncsafe(t, 6));
        Map<String, String> text = new LinkedHashMap<>();
        Map<String, byte[]> objects = new LinkedHashMap<>();
        int i = 10;
        while (i + 10 <= size) {
            String id = new String(t, i, 4, StandardCharsets.ISO_8859_1);
            if (id.charAt(0) == 0) {
                break; // padding
            }
            int len = version >= 4 ? syncsafe(t, i + 4) : ByteBuffer.wrap(t, i + 4, 4).getInt();
            if (len <= 0 || i + 10 + len > size) {
                break;
            }
            byte[] v = new byte[len];
            System.arraycopy(t, i + 10, v, 0, len);
            if (id.startsWith("T") && !id.equals("TXXX")) {
                text.put(id, decode(v, 1, v.length, v[0]));
            } else if (id.equals("GEOB")) {
                geob(v, objects);
            }
            i += 10 + len;
        }
        double bpm = number(text.get("TBPM"));
        long lengthMs = (long) number(text.get("TLEN"));
        byte[] overview = objects.get("Serato Overview");
        List<Integer> times = new ArrayList<>();
        List<Byte> bars = new ArrayList<>();
        beatGrid(objects.get("Serato BeatGrid"), lengthMs, times, bars);
        byte[] barArray = new byte[bars.size()];
        for (int k = 0; k < barArray.length; k++) {
            barArray[k] = bars.get(k);
        }
        return new Track(text.get("TIT2"), text.get("TPE1"), bpm, text.get("TKEY"), lengthMs, overview(overview),
                times.isEmpty() ? null : times.stream().mapToInt(Integer::intValue).toArray(), times.isEmpty() ? null : barArray,
                cues(objects.get("Serato Markers2")));
    }

    private static void geob(byte[] v, Map<String, byte[]> out) {
        int enc = v[0];
        int p = 1;
        int mimeEnd = indexOf(v, p, (byte) 0);
        p = mimeEnd + 1;
        int[] fn = terminator(v, p, enc);
        p = fn[1];
        int[] desc = terminator(v, p, enc);
        String description = decode(v, p, desc[0], (byte) enc);
        p = desc[1];
        byte[] data = new byte[Math.max(0, v.length - p)];
        System.arraycopy(v, p, data, 0, data.length);
        out.put(description, data);
    }

    /** End of a text field and the position after its terminator (one zero byte, or two for UTF-16). */
    private static int[] terminator(byte[] v, int from, int enc) {
        if (enc == 1 || enc == 2) {
            for (int i = from; i + 1 < v.length; i += 2) {
                if (v[i] == 0 && v[i + 1] == 0) {
                    return new int[] {i, i + 2};
                }
            }
            return new int[] {v.length, v.length};
        }
        int z = indexOf(v, from, (byte) 0);
        return new int[] {z, z + 1};
    }

    private static int indexOf(byte[] v, int from, byte b) {
        for (int i = from; i < v.length; i++) {
            if (v[i] == b) {
                return i;
            }
        }
        return v.length;
    }

    private static String decode(byte[] v, int from, int to, byte enc) {
        if (to <= from) {
            return "";
        }
        Charset cs = switch (enc) {
            case 1 -> StandardCharsets.UTF_16;
            case 2 -> StandardCharsets.UTF_16BE;
            case 3 -> StandardCharsets.UTF_8;
            default -> StandardCharsets.ISO_8859_1;
        };
        return new String(v, from, to - from, cs).replace("\u0000", "").trim();
    }

    private static double number(String s) {
        try {
            return s == null ? 0 : Double.parseDouble(s.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * "Serato Overview": 2 version bytes, then 240 columns of 16 pixels. A lit pixel is greater than 1; the number of lit
     * pixels is the height of the column. Result: the "color" preview format (height, red, green, blue per column).
     */
    static byte[] overview(byte[] data) {
        if (data == null || data.length < 2 + 16) {
            return null;
        }
        int columns = (data.length - 2) / 16;
        byte[] out = new byte[columns * 4];
        for (int c = 0; c < columns; c++) {
            int lit = 0;
            int sum = 0;
            for (int r = 0; r < 16; r++) {
                int v = data[2 + c * 16 + r] & 0xFF;
                if (v > 1) {
                    lit++;
                    sum += v;
                }
            }
            int height = Math.min(255, lit * 255 / 12);
            int intensity = lit == 0 ? 0 : sum / lit;
            out[c * 4] = (byte) height;
            // Serato's look: blue for quiet parts, towards white for loud ones.
            out[c * 4 + 1] = (byte) Math.min(255, 40 + intensity * 3 / 4);
            out[c * 4 + 2] = (byte) Math.min(255, 110 + intensity / 2);
            out[c * 4 + 3] = (byte) 255;
        }
        return out;
    }

    /**
     * "Serato BeatGrid": 2 version bytes, the number of markers, then markers of a float position in seconds and either
     * the beats to the next marker (all but the last) or the BPM (the last). One time per beat up to the track length.
     */
    static void beatGrid(byte[] g, long lengthMs, List<Integer> times, List<Byte> bars) {
        if (g == null || g.length < 6) {
            return;
        }
        ByteBuffer b = ByteBuffer.wrap(g);
        b.position(2);
        int count = b.getInt();
        if (count <= 0 || count > 100_000 || b.remaining() < count * 8) {
            return;
        }
        float[] pos = new float[count];
        int[] beats = new int[count];
        float lastBpm = 0;
        for (int i = 0; i < count; i++) {
            pos[i] = b.getFloat();
            if (i < count - 1) {
                beats[i] = b.getInt();
            } else {
                lastBpm = b.getFloat();
            }
        }
        int beat = 0;
        for (int i = 0; i < count - 1; i++) {
            if (beats[i] <= 0) {
                continue;
            }
            double step = (pos[i + 1] - pos[i]) / beats[i];
            for (int k = 0; k < beats[i]; k++) {
                add(times, bars, (pos[i] + k * step) * 1000, beat++);
            }
        }
        if (lastBpm > 0) {
            double step = 60.0 / lastBpm;
            double end = lengthMs > 0 ? lengthMs / 1000.0 : pos[count - 1] + 600;
            for (double t = pos[count - 1]; t <= end && times.size() < 200_000; t += step) {
                add(times, bars, t * 1000, beat++);
            }
        }
    }

    private static void add(List<Integer> times, List<Byte> bars, double ms, int beat) {
        if (ms >= 0) {
            times.add((int) Math.round(ms));
            bars.add((byte) (beat % 4 + 1));
        }
    }

    /**
     * "Serato Markers2": 2 version bytes, then base64 text. Decoded: 2 version bytes and entries of a zero-terminated name,
     * a 4 byte length and the data. CUE: 1 byte, index, position in ms (4 bytes), 1 byte, color (3 bytes), 2 bytes, name.
     * LOOP: 1 byte, index, start and end in ms (4 bytes each), ...
     */
    static List<Map<String, Object>> cues(byte[] data) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (data == null || data.length < 3) {
            return out;
        }
        String b64 = new String(data, 2, data.length - 2, StandardCharsets.US_ASCII).replaceAll("[^A-Za-z0-9+/]", "");
        b64 = b64.substring(0, b64.length() - b64.length() % 4);
        byte[] d;
        try {
            d = Base64.getDecoder().decode(b64);
        } catch (IllegalArgumentException e) {
            return out;
        }
        int i = 2;
        while (i < d.length) {
            int nameEnd = indexOf(d, i, (byte) 0);
            if (nameEnd >= d.length || nameEnd == i) {
                break;
            }
            String name = new String(d, i, nameEnd - i, StandardCharsets.US_ASCII);
            if (nameEnd + 5 > d.length) {
                break;
            }
            int len = ByteBuffer.wrap(d, nameEnd + 1, 4).getInt();
            int p = nameEnd + 5;
            if (len < 0 || p + len > d.length) {
                break;
            }
            ByteBuffer e = ByteBuffer.wrap(d, p, len);
            if (name.equals("CUE") && len >= 12) {
                e.get();
                int index = e.get() & 0xFF;
                long ms = e.getInt() & 0xFFFFFFFFL;
                e.get();
                int r = e.get() & 0xFF;
                int g = e.get() & 0xFF;
                int bl = e.get() & 0xFF;
                out.add(Map.of("t", ms, "hot", index + 1, "loop", false, "loopEnd", 0L, "color", String.format("#%02x%02x%02x", r, g, bl)));
            } else if (name.equals("LOOP") && len >= 10) {
                e.get();
                int index = e.get() & 0xFF;
                long start = e.getInt() & 0xFFFFFFFFL;
                long end = e.getInt() & 0xFFFFFFFFL;
                if (end > start && start < 0xFFFFFFFFL) {
                    out.add(Map.of("t", start, "hot", 0, "loop", true, "loopEnd", end, "color", "#ff8c00", "index", index));
                }
            }
            i = p + len;
        }
        return out;
    }
}
