package dev.ljlinkpro;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Serato DJ Pro on this computer. Serato has no public live interface, but it writes a log while it runs
 * ({@code Music/_Serato_/Logs}); every track loaded to a deck shows up there with the deck and the file. LJ Link follows
 * that log and reads title, artist, BPM, key, length, waveform overview, beat grid and cues from the tags Serato wrote into
 * the file ({@link SeratoTags}). Serato does not reveal the play position, so there is no playhead for these decks.
 */
final class SeratoSource implements DeckSource {
    /** A deck gets a track: {@code cache:///<file>:deck_<n>_instrumental?sample_rate=<rate>&length=<samples>}. */
    static final Pattern LOAD = Pattern.compile("cache://(/.+?):deck_(\\d+)_instrumental\\?sample_rate=(\\d+)&length=(\\d+)");

    private final Hub.Scope hub;
    private final Path logs;
    private volatile boolean stopped;
    private Path logFile;
    private long offset;
    private final Map<Integer, String> loaded = new HashMap<>();

    SeratoSource(Hub.Scope hub, Path seratoFolder) {
        this.hub = hub;
        this.logs = seratoFolder.resolve("Logs");
        hub.setSource(this);
    }

    /** The Serato folder of this user, or null if Serato was never used here. */
    static Path folder() {
        Path p = Path.of(System.getProperty("user.home"), "Music", "_Serato_");
        return Files.isDirectory(p) ? p : null;
    }

    static boolean seratoRunning() {
        return ProcessHandle.allProcesses().anyMatch(p -> p.info().command().map(c -> c.contains("Serato DJ Pro")).orElse(false));
    }

    @Override
    public void start() {
        hub.setState("idle", "live", "");
        Thread t = new Thread(() -> {
            while (!stopped) {
                try {
                    poll();
                } catch (IOException | RuntimeException e) {
                    // read again next time
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "serato-log");
        t.setDaemon(true);
        t.start();
    }

    private void poll() throws IOException {
        if (!seratoRunning()) {
            if (!loaded.isEmpty() || logFile != null) {
                loaded.clear();
                logFile = null;
                hub.removeAllDecks();
                hub.setState("idle", "live", "");
            }
            return;
        }
        Path newest = newestLog();
        if (newest == null) {
            return;
        }
        if (!newest.equals(logFile)) {
            logFile = newest; // Serato started again: read its new log from the beginning
            offset = 0;
            loaded.clear();
            hub.removeAllDecks();
        }
        long size = Files.size(logFile);
        if (size < offset) {
            offset = 0;
        }
        if (size == offset) {
            return;
        }
        byte[] chunk;
        try (RandomAccessFile f = new RandomAccessFile(logFile.toFile(), "r")) {
            f.seek(offset);
            chunk = new byte[(int) Math.min(size - offset, 8 * 1024 * 1024)];
            f.readFully(chunk);
        }
        int lastNewline = -1;
        for (int i = chunk.length - 1; i >= 0; i--) {
            if (chunk[i] == '\n') {
                lastNewline = i;
                break;
            }
        }
        if (lastNewline < 0) {
            return; // wait for the rest of the line
        }
        offset += lastNewline + 1;
        String text = new String(chunk, 0, lastNewline + 1, StandardCharsets.UTF_8);
        Matcher m = LOAD.matcher(text);
        while (m.find()) {
            int deck = Integer.parseInt(m.group(2)) + 1;
            String file = m.group(1);
            long rate = Long.parseLong(m.group(3));
            long samples = Long.parseLong(m.group(4));
            if (!file.equals(loaded.get(deck))) {
                loaded.put(deck, file);
                apply(deck, Path.of(file), rate > 0 ? samples * 1000 / rate : 0);
            }
        }
        if (!loaded.isEmpty() && !"monitoring".equals(hub.phase())) {
            hub.setState("monitoring", "live", "Connected");
        }
    }

    private Path newestLog() throws IOException {
        if (!Files.isDirectory(logs)) {
            return null;
        }
        Path newest = null;
        long time = 0;
        try (Stream<Path> files = Files.list(logs)) {
            for (Path p : (Iterable<Path>) files::iterator) {
                if (p.getFileName().toString().endsWith(".log")) {
                    long m = Files.getLastModifiedTime(p).toMillis();
                    if (m > time) {
                        time = m;
                        newest = p;
                    }
                }
            }
        }
        return newest;
    }

    private void apply(int n, Path file, long durationMs) {
        Deck d = hub.deck(n);
        d.clearTrack();
        d.trackVersion++;
        d.exactTempo = true; // the BPM of the file tag: no pitch, no jitter
        d.name = "Serato DJ Pro";
        d.hasTrack = true;
        d.playing = false;
        d.positionMs = -1;
        d.durationMs = durationMs;
        String name = file.getFileName().toString();
        d.title = name.replaceFirst("\\.[^.]+$", "");
        SeratoTags.Track t = null;
        try {
            t = SeratoTags.read(file);
        } catch (IOException | RuntimeException e) {
            // no tags: the file name stays the title
        }
        if (t != null) {
            if (t.title() != null && !t.title().isBlank()) {
                d.title = t.title();
            }
            d.artist = t.artist();
            d.key = t.key();
            d.trackBpm = t.bpm();
            d.effectiveBpm = t.bpm();
            if (d.durationMs <= 0) {
                d.durationMs = t.lengthMs();
            }
            d.previewStyle = "color";
            d.setWaveform(t.waveform());
            d.setBeats(t.beatTimes(), t.beatBars());
            d.cues = t.cues();
        }
        d.noWaveformSource = d.waveform == null;
    }

    @Override
    public void stop() {
        stopped = true;
    }

    @Override
    public java.util.List<String> diagnostics() {
        return java.util.List.of("Serato running: " + seratoRunning() + ", log " + (logFile == null ? "none" : logFile.getFileName())
                + ", decks with a track: " + loaded.keySet());
    }
}
