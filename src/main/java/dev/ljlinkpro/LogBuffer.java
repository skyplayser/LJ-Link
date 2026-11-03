package dev.ljlinkpro;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Keeps the last lines the program printed (its own messages and those of beat-link), so a problem report can include
 * them. Everything still goes to the console as before.
 */
final class LogBuffer {
    private static final int MAX_LINES = 400;
    private static final ArrayDeque<String> LINES = new ArrayDeque<>();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private LogBuffer() {}

    static void install() {
        System.setOut(tee(System.out));
        System.setErr(tee(System.err));
    }

    static List<String> lines() {
        synchronized (LINES) {
            return new ArrayList<>(LINES);
        }
    }

    private static void add(String line) {
        if (line.isBlank()) {
            return;
        }
        synchronized (LINES) {
            LINES.addLast(LocalTime.now().format(TIME) + " " + line);
            while (LINES.size() > MAX_LINES) {
                LINES.removeFirst();
            }
        }
    }

    private static PrintStream tee(PrintStream original) {
        OutputStream out = new OutputStream() {
            private final StringBuilder line = new StringBuilder();

            @Override
            public synchronized void write(int b) {
                original.write(b);
                if (b == '\n') {
                    add(line.toString());
                    line.setLength(0);
                } else if (b != '\r' && line.length() < 2000) {
                    line.append((char) b);
                }
            }

            @Override
            public synchronized void write(byte[] b, int off, int len) {
                original.write(b, off, len);
                String text = new String(b, off, len, StandardCharsets.UTF_8);
                for (char c : text.toCharArray()) {
                    if (c == '\n') {
                        add(line.toString());
                        line.setLength(0);
                    } else if (c != '\r' && line.length() < 2000) {
                        line.append(c);
                    }
                }
            }

            @Override
            public void flush() {
                original.flush();
            }
        };
        return new PrintStream(out, true, StandardCharsets.UTF_8);
    }
}
