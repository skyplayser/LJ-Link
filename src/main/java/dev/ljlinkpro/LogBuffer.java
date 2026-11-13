package dev.ljlinkpro;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Keeps the last lines the program printed (its own messages and those of beat-link), so a problem report can include
 * them. Everything still goes to the console as before, except that a message repeated over and over (for example a
 * warning with its stack trace for every beat packet, issue #2) is shown {@link Throttle#SHOWN} times a minute and then
 * counted, so the diagnostics stay readable.
 */
final class LogBuffer {
    private static final int MAX_LINES = 400;
    private static final ArrayDeque<String> LINES = new ArrayDeque<>();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Throttle THROTTLE = new Throttle();

    private LogBuffer() {}

    static void install() {
        System.setOut(tee(System.out));
        System.setErr(tee(System.err));
    }

    /** The last lines, followed by a note for every message that is being counted instead of shown right now. */
    static List<String> lines() {
        List<String> out;
        synchronized (LINES) {
            out = new ArrayList<>(LINES);
        }
        String now = LocalTime.now().format(TIME);
        for (String s : THROTTLE.pending(System.currentTimeMillis())) {
            out.add(now + " " + s);
        }
        return out;
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
            /** The raw bytes of the line being written; it is printed (or held back) once it is complete. */
            private final ByteArrayOutputStream line = new ByteArrayOutputStream();

            @Override
            public synchronized void write(int b) {
                line.write(b);
                if (b == '\n') {
                    endLine();
                }
            }

            @Override
            public synchronized void write(byte[] b, int off, int len) {
                int start = off;
                for (int i = off; i < off + len; i++) {
                    if (b[i] == '\n') {
                        line.write(b, start, i + 1 - start);
                        endLine();
                        start = i + 1;
                    }
                }
                line.write(b, start, off + len - start);
            }

            private void endLine() {
                byte[] raw = line.toByteArray();
                line.reset();
                String text = new String(raw, StandardCharsets.UTF_8).replace("\r", "").replace("\n", "");
                if (text.length() > 2000) {
                    text = text.substring(0, 2000);
                }
                for (String s : THROTTLE.accept(text, System.currentTimeMillis())) {
                    if (s == text) {
                        original.write(raw, 0, raw.length); // unchanged, with the line end it had
                    } else {
                        original.println(s);
                    }
                    add(s);
                }
            }

            @Override
            public void flush() {
                original.flush();
            }
        };
        return new PrintStream(out, true, StandardCharsets.UTF_8);
    }

    /**
     * Shows a message at most {@link #SHOWN} times per {@link #WINDOW_MS} and counts the rest; its stack trace follows the
     * decision about the message. When the message comes again after the window, one line says how often it was left out.
     * A stack trace is: right after the message one exception line ("java.lang.NullPointerException: …"), then lines
     * with "\tat", "\t...", "Caused by:" or "Suppressed:". Any other line, also an exception line in another place or a
     * different exception than the one shown with this message, is a message of its own, so nothing foreign is lost.
     */
    static final class Throttle {
        static final int SHOWN = 3;
        static final long WINDOW_MS = 60_000;
        private static final int MAX_KEYS = 500;
        /** "java.lang.NullPointerException" or "java.io.IOException: Disk full". */
        private static final Pattern EXCEPTION = Pattern.compile("^([a-zA-Z_$][\\w$]*\\.)+[A-Z][\\w$]*(Exception|Error|Throwable)(: .*)?$");

        private static final class Seen {
            long since;
            int count;
            int left;
            /** The exception line that followed this message when it was shown. */
            String exception;
        }

        private final Map<String, Seen> seen = new HashMap<>();
        /** The message of the lines that follow, and whether it is being left out. */
        private Seen current;
        private boolean leaving;
        /** Right after a message: an exception line may follow. In a stack trace: "at" lines may follow. */
        private boolean afterMessage;
        private boolean inTrace;

        /** A line inside a stack trace ("\tat …", "Caused by: …"). */
        static boolean traceLine(String line) {
            return line.startsWith("\tat ") || line.startsWith("\t... ") || line.startsWith("\tCaused by: ")
                    || line.startsWith("\tSuppressed: ") || line.startsWith("Caused by: ");
        }

        static boolean exceptionLine(String line) {
            return EXCEPTION.matcher(line).matches();
        }

        /** The lines to print for this one: none, the line itself (the same String), or a note and the line. */
        synchronized List<String> accept(String line, long now) {
            if (line.isBlank()) {
                return List.of(line);
            }
            if ((afterMessage || inTrace) && traceLine(line)) {
                afterMessage = false;
                inTrace = true;
                return leaving ? List.of() : List.of(line);
            }
            if (afterMessage && current != null && exceptionLine(line)) {
                afterMessage = false;
                inTrace = true;
                if (!leaving) {
                    if (current.exception == null) {
                        current.exception = line;
                    }
                    return List.of(line);
                }
                if (line.equals(current.exception)) {
                    return List.of(); // the same exception as the times the message was shown
                }
                // Another exception than this message had: not part of what is left out, so it is a message itself.
            }
            return message(line, now);
        }

        private List<String> message(String line, long now) {
            afterMessage = true;
            inTrace = false;
            Seen s = seen.get(line);
            if (s == null || now - s.since >= WINDOW_MS) {
                List<String> out = new ArrayList<>(2);
                if (s != null && s.left > 0) {
                    out.add(note(line, s, now));
                }
                if (s == null) {
                    if (seen.size() >= MAX_KEYS) {
                        prune(now);
                    }
                    s = new Seen();
                    seen.put(line, s);
                }
                s.since = now;
                s.count = 1;
                s.left = 0;
                current = s;
                leaving = false;
                out.add(line);
                return out;
            }
            current = s;
            s.count++;
            if (s.count <= SHOWN) {
                leaving = false;
                return List.of(line);
            }
            s.left++;
            leaving = true;
            return List.of();
        }

        /** Notes for the messages being left out right now (shown with the diagnostics). */
        synchronized List<String> pending(long now) {
            List<String> out = new ArrayList<>();
            seen.forEach((line, s) -> {
                if (s.left > 0) {
                    out.add(note(line, s, now));
                }
            });
            return out;
        }

        private static String note(String line, Seen s, long now) {
            String since = LocalTime.ofInstant(Instant.ofEpochMilli(s.since), ZoneId.systemDefault()).format(TIME);
            return "[log] repeated " + s.left + " more times since " + since + " (" + Math.max(1, (now - s.since) / 1000)
                    + " s, stack traces left out): " + line;
        }

        private void prune(long now) {
            for (Iterator<Map.Entry<String, Seen>> it = seen.entrySet().iterator(); it.hasNext(); ) {
                Seen s = it.next().getValue();
                if (now - s.since >= WINDOW_MS && s.left == 0) {
                    it.remove();
                }
            }
            if (seen.size() >= MAX_KEYS) {
                seen.clear();
            }
        }
    }
}
