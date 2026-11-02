package dev.ljlinkpro;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Client of the StageLinQ FileTransfer service: lists the sources of a device (SD card, USB stick) and downloads files such
 * as the Engine library database. Read only. Frames are {@code [length]["fltx"][code][id]...}; requests use the codes 0x7d1
 * (file size), 0x7d2 (sources), 0x7d4 (open a transfer), 0x7d5 (request chunks) and 0x7d6 (transfer done).
 */
final class StageLinqFiles implements Closeable {
    private static final int CHUNK = 4096;
    private static final int ID_FILE_STAT = 1;
    private static final int ID_END = 2;
    private static final int ID_SOURCES = 3;
    private static final int ID_TRANSFER = 4;
    private static final int ID_CHUNK = 5;

    private final Socket socket = new Socket();
    private final DataInputStream in;
    private final DataOutputStream out;

    StageLinqFiles(String host, int port, byte[] token) throws IOException {
        socket.connect(new InetSocketAddress(host, port), 5000);
        socket.setSoTimeout(30_000);
        in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        out = new DataOutputStream(socket.getOutputStream());
        out.write(StageLinqProtocol.serviceAnnouncement(token, "FileTransfer", socket.getLocalPort()));
        out.flush();
    }

    /** Names of the media on the device, for example the SD card or a USB stick. */
    List<String> sources() throws IOException {
        send(0x7d2, w -> w.writeInt(0));
        Message m = expect(ID_SOURCES);
        ByteBuffer b = ByteBuffer.wrap(m.body);
        int count = b.getInt();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            names.add(readString(b));
        }
        return names;
    }

    /** Size of a file in bytes, or 0 if it does not exist. */
    long size(String path) throws IOException {
        send(0x7d1, w -> StageLinqProtocol.writeString(w, path));
        Message m = expect(ID_FILE_STAT);
        return m.body.length >= 53 ? ByteBuffer.wrap(m.body).getInt(49) & 0xFFFFFFFFL : 0;
    }

    /** Downloads a whole file. */
    byte[] read(String path) throws IOException {
        send(0x7d4, w -> {
            StageLinqProtocol.writeString(w, path);
            w.writeInt(0);
        });
        ByteBuffer t = ByteBuffer.wrap(expect(ID_TRANSFER).body);
        t.getInt();
        long size = t.getInt() & 0xFFFFFFFFL;
        int txid = t.getInt();
        if (size == 0) {
            return new byte[0];
        }
        int chunks = (int) ((size + CHUNK - 1) / CHUNK);
        byte[] data = new byte[(int) size];
        send(0x7d5, w -> {
            w.writeInt(0);
            w.writeInt(txid);
            w.writeInt(0);
            w.writeInt(0);
            w.writeInt(0);
            w.writeInt(chunks - 1);
        });
        long received = 0;
        while (received < size) {
            Message m = next();
            if (m.id != ID_CHUNK) {
                continue;
            }
            ByteBuffer c = ByteBuffer.wrap(m.body);
            c.getInt();
            long offset = c.getInt() & 0xFFFFFFFFL;
            int length = c.getInt();
            if (length < 0 || length > c.remaining()) {
                throw new IOException("Broken chunk");
            }
            int at = offset < size ? (int) offset : (int) received;
            int n = (int) Math.min(length, size - at);
            c.get(data, at, n);
            received += n;
        }
        send(0x7d6, w -> { });
        return data;
    }

    private record Message(int id, byte[] body) {}

    private interface Body {
        void write(DataOutputStream w) throws IOException;
    }

    private void send(int code, Body body) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream w = new DataOutputStream(bytes);
        w.write("fltx".getBytes(StandardCharsets.US_ASCII));
        w.writeInt(0);
        w.writeInt(code);
        body.write(w);
        byte[] payload = bytes.toByteArray();
        synchronized (out) {
            out.writeInt(payload.length);
            out.write(payload);
            out.flush();
        }
    }

    /** The next regular message; the device also sends time code messages in between, which are skipped. */
    private Message next() throws IOException {
        while (true) {
            ByteBuffer b = ByteBuffer.wrap(StageLinqProtocol.readFrame(in));
            if (b.remaining() < 12 || b.get() != 'f' || b.get() != 'l' || b.get() != 't' || b.get() != 'x') {
                continue;
            }
            if (b.getInt() != 0) {
                continue; // time code
            }
            int id = b.getInt();
            byte[] body = new byte[b.remaining()];
            b.get(body);
            return new Message(id, body);
        }
    }

    private Message expect(int id) throws IOException {
        for (int i = 0; i < 200; i++) {
            Message m = next();
            if (m.id == id) {
                return m;
            }
            if (m.id == ID_END && id != ID_END) {
                continue;
            }
        }
        throw new IOException("No answer of type " + id);
    }

    private static String readString(ByteBuffer b) {
        int n = b.getInt();
        byte[] raw = new byte[n];
        b.get(raw);
        return new String(raw, StandardCharsets.UTF_16BE);
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
