package dev.ljlinkpro;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Receives Art-Net timecode (OpTimeCode 0x9700 on UDP 6454) from a light desk or show software, so the clock bar can
 * show the show's timecode instead of the time of day (ADR-044, Alpha). Off by default; the port is only opened while
 * it is switched on. The sender's address is never kept, logged or sent to the browsers.
 */
final class ArtNetTimecode {
    static final String SETTING = "artnetTimecode";
    static final int PORT = 6454;
    /** Without a packet for this long the clock bar falls back to the time of day. */
    static final long TIMEOUT_NANOS = 2_000_000_000L;

    /** One timecode frame. {@code type}: 0 = 24 fps (film), 1 = 25 fps (EBU), 2 = 29.97 fps drop frame, 3 = 30 fps. */
    record Frame(int hours, int minutes, int seconds, int frames, int type) {
        int fps() {
            return switch (type) {
                case 0 -> 24;
                case 1 -> 25;
                default -> 30;
            };
        }

        String rate() {
            return switch (type) {
                case 0 -> "24";
                case 1 -> "25";
                case 2 -> "29.97df";
                default -> "30";
            };
        }
    }

    /**
     * Reads an ArtTimeCode packet: "Art-Net\0", OpCode 0x9700 (little endian), protocol version (2 bytes), two bytes
     * filler / stream id, then frames, seconds, minutes, hours, type. Null for anything else or values out of range.
     */
    static Frame parse(byte[] b, int len) {
        if (b == null || len < 19 || len > b.length) {
            return null;
        }
        byte[] id = {'A', 'r', 't', '-', 'N', 'e', 't', 0};
        for (int i = 0; i < 8; i++) {
            if (b[i] != id[i]) {
                return null;
            }
        }
        if ((b[8] & 0xff) != 0x00 || (b[9] & 0xff) != 0x97) {
            return null;
        }
        int frames = b[14] & 0xff, seconds = b[15] & 0xff, minutes = b[16] & 0xff, hours = b[17] & 0xff, type = b[18] & 0xff;
        if (type > 3 || hours > 23 || minutes > 59 || seconds > 59) {
            return null;
        }
        Frame f = new Frame(hours, minutes, seconds, frames, type);
        return frames < f.fps() ? f : null;
    }

    private final int port;
    private volatile DatagramChannel channel;
    private volatile Thread thread;
    private volatile Frame last;
    private volatile long lastNanos;
    private volatile String error;
    private volatile boolean enabled;
    private boolean loggedFirst;

    ArtNetTimecode() {
        this(PORT);
    }

    ArtNetTimecode(int port) {
        this.port = port;
    }

    /** The saved choice (default off). */
    static boolean enabledSetting() {
        return AppSettings.getBoolean(SETTING, false);
    }

    /** Switches receiving on or off and saves the choice. */
    synchronized void setEnabled(boolean on) {
        AppSettings.set(SETTING, on);
        apply(on);
    }

    /** Opens or closes the port without saving (start of the program). */
    synchronized void apply(boolean on) {
        enabled = on;
        if (on && channel == null) {
            open();
        } else if (!on) {
            close();
        }
    }

    boolean enabled() {
        return enabled;
    }

    boolean running() {
        return channel != null;
    }

    /** Why the port could not be opened, or null. */
    String error() {
        return error;
    }

    private void open() {
        error = null;
        loggedFirst = false;
        try {
            DatagramChannel ch = DatagramChannel.open(StandardProtocolFamily.INET);
            ch.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            if (ch.supportedOptions().contains(StandardSocketOptions.SO_REUSEPORT)) {
                // Lets a desk program on this computer keep port 6454 too (broadcast timecode reaches both).
                ch.setOption(StandardSocketOptions.SO_REUSEPORT, true);
            }
            ch.setOption(StandardSocketOptions.SO_BROADCAST, true);
            ch.bind(new InetSocketAddress(port));
            channel = ch;
            Thread t = new Thread(() -> receive(ch), "artnet-timecode");
            t.setDaemon(true);
            thread = t;
            t.start();
            System.out.println("Art-Net timecode: listening on UDP " + port + " (Alpha)");
        } catch (IOException | RuntimeException e) {
            error = "UDP " + port + " is in use by another program";
            System.err.println("Art-Net timecode: could not open UDP " + port + ": " + e.getMessage());
        }
    }

    private void close() {
        DatagramChannel ch = channel;
        channel = null;
        thread = null;
        last = null;
        error = null;
        if (ch != null) {
            try {
                ch.close();
            } catch (IOException ignored) {
                // closing anyway
            }
        }
    }

    private void receive(DatagramChannel ch) {
        ByteBuffer buf = ByteBuffer.allocate(600);
        while (ch.isOpen()) {
            try {
                buf.clear();
                if (ch.receive(buf) == null) {
                    continue;
                }
                received(buf.array(), buf.position(), System.nanoTime());
            } catch (ClosedChannelException e) {
                return;
            } catch (IOException | RuntimeException e) {
                if (ch.isOpen()) {
                    System.err.println("Art-Net timecode: " + e.getMessage());
                }
                return;
            }
        }
    }

    /** One packet arrived (also used by the tests). Only timecode packets count; the sender is not looked at. */
    void received(byte[] data, int len, long nowNanos) {
        Frame f = parse(data, len);
        if (f == null) {
            return;
        }
        last = f;
        lastNanos = nowNanos;
        if (!loggedFirst) {
            loggedFirst = true;
            System.out.println("Art-Net timecode received");
        }
    }

    /** The newest frame if it is younger than {@link #TIMEOUT_NANOS}, else null. */
    Frame current(long nowNanos) {
        Frame f = last;
        return f != null && nowNanos - lastNanos < TIMEOUT_NANOS ? f : null;
    }

    /**
     * What the browsers get inside the {@code clock} message: {@code on} (switched on), and while timecode arrives
     * {@code h m s f}, {@code fps}, {@code rate} and {@code age} (ms since the frame arrived). Never an address.
     */
    Map<String, Object> view(long nowNanos) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("on", enabled);
        Frame f = current(nowNanos);
        if (f != null) {
            m.put("h", f.hours());
            m.put("m", f.minutes());
            m.put("s", f.seconds());
            m.put("f", f.frames());
            m.put("fps", f.fps());
            m.put("rate", f.rate());
            m.put("df", f.type() == 2);
            m.put("age", Math.max(0, (nowNanos - lastNanos) / 1_000_000));
        }
        return m;
    }

    /**
     * {@code GET/POST /api/timecode} (local only: under /api/ and not public, so {@link ControlGuard} keeps tablets and
     * other web pages out). GET: {@code on}, {@code listening}, {@code error}; POST {@code {"on":true|false}}.
     */
    static void routes(io.javalin.Javalin app, ArtNetTimecode tc) {
        app.get("/api/timecode", ctx -> ctx.header("Cache-Control", "no-store").json(tc.status()));
        app.post("/api/timecode", ctx -> {
            boolean on;
            try {
                on = Hub.JSON.readTree(ctx.body()).path("on").asBoolean(false);
            } catch (IOException | RuntimeException e) {
                ctx.status(400);
                return;
            }
            tc.setEnabled(on);
            ctx.header("Cache-Control", "no-store").json(tc.status());
        });
    }

    Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("on", enabled);
        m.put("listening", running());
        m.put("receiving", current(System.nanoTime()) != null);
        m.put("error", error);
        return m;
    }
}
