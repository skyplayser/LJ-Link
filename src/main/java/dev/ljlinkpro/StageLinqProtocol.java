package dev.ljlinkpro;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Wire format of Denon's StageLinQ, written from the protocol as described by the open source libraries
 * go-stagelinq (icedream, MIT) and StageLinq (chrisle, MIT). No code was copied. All numbers are big endian and
 * strings are a 4 byte length in bytes followed by UTF-16BE text.
 *
 * <ul>
 *   <li><b>Discovery</b> (UDP 51337): {@code "airD"}, token (16), source, action, software name, version, port.</li>
 *   <li><b>Directory service</b> (TCP, port from discovery): messages start with an int id: 0 = service announcement
 *       (token, name, port), 1 = reference/heartbeat (token, token2, long), 2 = services request (token).</li>
 *   <li><b>StateMap</b>: frames of {@code [length]["smaa"][type]...}; type 0x7d2 subscribes to a value, type 0 delivers
 *       a value as JSON.</li>
 *   <li><b>BeatInfo</b>: frames of {@code [length][type]...}; type 2 carries beat, total beats and BPM per deck
 *       followed by the sample position per deck.</li>
 * </ul>
 */
final class StageLinqProtocol {
    private StageLinqProtocol() {}

    static final int DISCOVERY_PORT = 51337;
    static final String HOWDY = "DISCOVERER_HOWDY_";
    static final String EXIT = "DISCOVERER_EXIT_";
    static final int ID_SERVICE_ANNOUNCEMENT = 0;
    static final int ID_REFERENCE = 1;
    static final int ID_SERVICES_REQUEST = 2;
    static final int SMAA_EMIT = 0;
    static final int SMAA_SUBSCRIBE = 0x7d2;
    static final int SMAA_SUBSCRIBE_RESPONSE = 0x7d1;
    static final int BEAT_EMIT = 2;
    private static final int MAX_FRAME = 8 * 1024 * 1024;
    private static final byte[] AIRD = {'a', 'i', 'r', 'D'};
    private static final byte[] SMAA = {'s', 'm', 'a', 'a'};

    record Discovery(byte[] token, String source, String action, String softwareName, String softwareVersion, int port) {}

    record StateEmit(String name, String json) {}

    record Beat(long clock, double[] beat, double[] totalBeats, double[] bpm, double[] samples) {}

    /* ---------- strings ---------- */

    static void writeString(DataOutputStream out, String s) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.UTF_16BE);
        out.writeInt(b.length);
        out.write(b);
    }

    static String readString(DataInput in) throws IOException {
        int n = in.readInt();
        if (n < 0 || n > MAX_FRAME) {
            throw new IOException("Implausible string length " + n);
        }
        byte[] b = new byte[n];
        in.readFully(b);
        return new String(b, StandardCharsets.UTF_16BE);
    }

    private static String readString(ByteBuffer buf) throws IOException {
        if (buf.remaining() < 4) {
            throw new IOException("Truncated string");
        }
        int n = buf.getInt();
        if (n < 0 || n > buf.remaining()) {
            throw new IOException("Implausible string length " + n);
        }
        byte[] b = new byte[n];
        buf.get(b);
        return new String(b, StandardCharsets.UTF_16BE);
    }

    /* ---------- discovery ---------- */

    static byte[] discovery(Discovery d) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.write(AIRD);
            out.write(d.token());
            writeString(out, d.source());
            writeString(out, d.action());
            writeString(out, d.softwareName());
            writeString(out, d.softwareVersion());
            out.writeShort(d.port());
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** @return the announcement, or null if the packet is not a StageLinQ discovery message */
    static Discovery parseDiscovery(byte[] data, int length) {
        try {
            ByteBuffer buf = ByteBuffer.wrap(data, 0, length);
            if (buf.remaining() < 20 || buf.get() != 'a' || buf.get() != 'i' || buf.get() != 'r' || buf.get() != 'D') {
                return null;
            }
            byte[] token = new byte[16];
            buf.get(token);
            String source = readString(buf);
            String action = readString(buf);
            String software = readString(buf);
            String version = readString(buf);
            if (buf.remaining() < 2) {
                return null;
            }
            return new Discovery(token, source, action, software, version, buf.getShort() & 0xFFFF);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /* ---------- directory service ---------- */

    static byte[] servicesRequest(byte[] token) {
        return build(out -> {
            out.writeInt(ID_SERVICES_REQUEST);
            out.write(token);
        });
    }

    static byte[] serviceAnnouncement(byte[] token, String service, int port) {
        return build(out -> {
            out.writeInt(ID_SERVICE_ANNOUNCEMENT);
            out.write(token);
            writeString(out, service);
            out.writeShort(port);
        });
    }

    static byte[] reference(byte[] token, byte[] token2, long reference) {
        return build(out -> {
            out.writeInt(ID_REFERENCE);
            out.write(token);
            out.write(token2);
            out.writeLong(reference);
        });
    }

    /* ---------- state map ---------- */

    static byte[] subscribe(String name, int intervalMs) {
        return frame(out -> {
            out.write(SMAA);
            out.writeInt(SMAA_SUBSCRIBE);
            writeString(out, name);
            out.writeInt(intervalMs);
        });
    }

    static byte[] stateEmit(String name, String json) {
        return frame(out -> {
            out.write(SMAA);
            out.writeInt(SMAA_EMIT);
            writeString(out, name);
            writeString(out, json);
        });
    }

    /** @return the value update, or null if the frame is something else (for example a subscription response) */
    static StateEmit parseStateFrame(byte[] payload) {
        try {
            ByteBuffer buf = ByteBuffer.wrap(payload);
            if (buf.remaining() < 8 || buf.get() != 's' || buf.get() != 'm' || buf.get() != 'a' || buf.get() != 'a') {
                return null;
            }
            if (buf.getInt() != SMAA_EMIT) {
                return null;
            }
            String name = readString(buf);
            String json = readString(buf);
            return new StateEmit(name, json);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /* ---------- beat info ---------- */

    static byte[] beatStartStream() {
        return frame(out -> out.writeInt(0));
    }

    static byte[] beatFrame(Beat b) {
        return frame(out -> {
            out.writeInt(BEAT_EMIT);
            out.writeLong(b.clock());
            out.writeInt(b.beat().length);
            for (int i = 0; i < b.beat().length; i++) {
                out.writeDouble(b.beat()[i]);
                out.writeDouble(b.totalBeats()[i]);
                out.writeDouble(b.bpm()[i]);
            }
            for (double s : b.samples()) {
                out.writeDouble(s);
            }
        });
    }

    /** @return the beat data, or null if the frame is not a beat message */
    static Beat parseBeatFrame(byte[] payload) {
        try {
            ByteBuffer buf = ByteBuffer.wrap(payload);
            if (buf.remaining() < 16 || buf.getInt() != BEAT_EMIT) {
                return null;
            }
            long clock = buf.getLong();
            int decks = buf.getInt();
            if (decks < 0 || decks > 16 || buf.remaining() < decks * 24) {
                return null;
            }
            double[] beat = new double[decks];
            double[] total = new double[decks];
            double[] bpm = new double[decks];
            for (int i = 0; i < decks; i++) {
                beat[i] = buf.getDouble();
                total[i] = buf.getDouble();
                bpm[i] = buf.getDouble();
            }
            double[] samples = new double[decks];
            // The sample positions follow the deck records; older firmware may leave them out.
            for (int i = 0; i < decks && buf.remaining() >= 8; i++) {
                samples[i] = buf.getDouble();
            }
            return new Beat(clock, beat, total, bpm, samples);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /* ---------- framing ---------- */

    /** Reads one length-prefixed frame (the length counts the payload only). */
    static byte[] readFrame(DataInput in) throws IOException {
        int n = in.readInt();
        if (n < 0 || n > MAX_FRAME) {
            throw new IOException("Implausible frame length " + n);
        }
        byte[] payload = new byte[n];
        in.readFully(payload);
        return payload;
    }

    private interface Writer {
        void write(DataOutputStream out) throws IOException;
    }

    private static byte[] build(Writer w) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            w.write(new DataOutputStream(bytes));
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] frame(Writer w) {
        byte[] payload = build(w);
        ByteBuffer out = ByteBuffer.allocate(4 + payload.length);
        out.putInt(payload.length);
        out.put(payload);
        return out.array();
    }
}
