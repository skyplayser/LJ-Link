package dev.ljlinkpro;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Speaks OS2L ("Open Sound to Light", the protocol VirtualDJ uses) to a lighting program such as the Wolfmix software
 * WTOOLS or QLC+. The lighting program listens on a TCP port, this class is the audio software side: it connects and
 * sends one JSON object per message, {@code beat} on every beat and {@code btn} for buttons. Answers ({@code feedback})
 * are read and dropped. The specification gives no message delimiter; every object ends with a line feed, which
 * receivers that find the objects by their braces ignore.
 */
final class Os2lClient {
    static final int PORT = 5000;

    private Socket socket;
    private OutputStream out;
    private String connectedTo;

    synchronized boolean connected() {
        return socket != null && !socket.isClosed();
    }

    /** The beat message; {@code strength} is a percentage of the strongest beat, or negative to leave it out. */
    static String beatMessage(boolean change, long pos, double bpm, double strength) {
        return String.format(Locale.ROOT, "{\"evt\":\"beat\",\"change\":%b,\"pos\":%d,\"bpm\":%.3f%s}\n", change, pos, bpm,
                strength < 0 ? "" : String.format(Locale.ROOT, ",\"strength\":%.1f", strength));
    }

    static String buttonMessage(String name, boolean on) {
        return "{\"evt\":\"btn\",\"name\":\"" + name.replace("\\", "").replace("\"", "") + "\",\"state\":\"" + (on ? "on" : "off") + "\"}\n";
    }

    synchronized void beat(String host, int port, boolean change, long pos, double bpm) throws IOException {
        send(host, port, beatMessage(change, pos, bpm, -1));
    }

    synchronized void button(String host, int port, String name, boolean on) throws IOException {
        send(host, port, buttonMessage(name, on));
    }

    /** Sends one message, connecting first when needed. A broken connection is closed so the next call connects again. */
    synchronized void send(String host, int port, String message) throws IOException {
        String target = host + ":" + port;
        if (!connected() || !target.equals(connectedTo)) {
            connect(host, port);
            connectedTo = target;
        }
        try {
            out.write(message.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException e) {
            close();
            throw e;
        }
    }

    private void connect(String host, int port) throws IOException {
        close();
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host, port), 1500);
        s.setTcpNoDelay(true);
        socket = s;
        out = s.getOutputStream();
        Thread reader = new Thread(() -> {
            try (InputStream in = s.getInputStream()) {
                byte[] buf = new byte[1024];
                while (in.read(buf) >= 0) {
                    // feedback from the lighting program: not used
                }
            } catch (IOException e) {
                // the writer notices a broken connection
            }
            synchronized (this) {
                if (socket == s) {
                    close();
                }
            }
        }, "os2l-reader");
        reader.setDaemon(true);
        reader.start();
    }

    synchronized void close() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                // already closed
            }
        }
        socket = null;
        out = null;
        connectedTo = null;
    }
}
