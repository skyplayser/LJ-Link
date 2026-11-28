package dev.ljlinkpro;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Suite diagnostics v1 (ADR-046): format of SPEC.md, size, nothing personal, atomic writing, throttling, path. */
class SuiteDiagnoseTest {
    @TempDir
    Path tmp;
    private String previousHome;

    @BeforeEach
    void ownSettings() throws Exception {
        previousHome = System.getProperty("ljlinkpro.home");
        System.setProperty("ljlinkpro.home", Files.createTempDirectory("ljlinkpro-diagnose").toString());
    }

    @AfterEach
    void restoreSettings() {
        if (previousHome == null) {
            System.clearProperty("ljlinkpro.home");
        } else {
            System.setProperty("ljlinkpro.home", previousHome);
        }
    }

    /** A source whose signature and state the test sets. */
    private static final class FakeSource implements SuiteDiagnose.Source {
        final AtomicReference<String> signature = new AtomicReference<>("a");
        volatile SuiteDiagnose.State state = new SuiteDiagnose.State(Map.of("State", "monitoring"),
                List.of(new SuiteDiagnose.Check("Pro DJ Link", true, "monitoring")));
        final AtomicInteger asked = new AtomicInteger();

        @Override
        public String signature() {
            return signature.get();
        }

        @Override
        public SuiteDiagnose.State state() {
            asked.incrementAndGet();
            return state;
        }
    }

    private JsonNode read(SuiteDiagnose d) throws Exception {
        return Hub.JSON.readTree(Files.readString(d.file()));
    }

    @Test
    void theFileHasTheFieldsOfTheSpecAndSaysRunningFalseAfterTheEnd() throws Exception {
        AtomicLong now = new AtomicLong(1_795_000_000_000L);
        SuiteDiagnose d = new SuiteDiagnose(tmp, "1.7.16", new FakeSource(), now::get);
        d.error("Hub tick failed (NullPointerException)");
        assertTrue(d.tick());
        assertEquals(tmp.resolve("diagnose").resolve("lj.json"), d.file());
        JsonNode j = read(d);
        assertEquals("diagnose", j.path("suite").asText());
        assertEquals(1, j.path("v").asInt());
        assertTrue(j.path("v").isInt());
        assertEquals("lj", j.path("id").asText());
        assertEquals("LJ Link", j.path("app").asText());
        assertEquals("1.7.16", j.path("version").asText());
        assertTrue(j.path("at").asText().matches("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\dZ"), j.path("at").asText());
        assertTrue(j.path("running").asBoolean());
        assertTrue(j.path("running").isBoolean());
        assertFalse(j.path("os").asText().isBlank());
        assertTrue(List.of("arm64", "x64").contains(j.path("arch").asText()) || !j.path("arch").asText().isBlank());
        assertEquals("monitoring", j.path("summary").path("State").asText());
        JsonNode c = j.path("checks").get(0);
        assertEquals("Pro DJ Link", c.path("name").asText());
        assertTrue(c.path("ok").isBoolean());
        assertEquals("monitoring", c.path("detail").asText());
        JsonNode e = j.path("errors").get(0);
        assertEquals("Hub tick failed (NullPointerException)", e.path("text").asText());
        assertEquals(SuiteDiagnose.iso(now.get()), e.path("at").asText());

        now.addAndGet(5_000);
        d.stop();
        JsonNode end = read(d);
        assertFalse(end.path("running").asBoolean());
        assertEquals("monitoring", end.path("summary").path("State").asText(), "the last state stays");
        assertFalse(d.tick(), "nothing after the end");
        assertDoesNotThrow(d::stop);
    }

    @Test
    void theFileStaysWithin16KbAndEveryTextWithinItsLimit() throws Exception {
        FakeSource src = new FakeSource();
        Map<String, String> summary = new LinkedHashMap<>();
        List<SuiteDiagnose.Check> checks = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            summary.put("Key " + i + " " + "k".repeat(100), "äöü€".repeat(200));
            checks.add(new SuiteDiagnose.Check("Check " + i + " " + "n".repeat(100), i % 2 == 0, "ß€".repeat(300)));
        }
        src.state = new SuiteDiagnose.State(summary, checks);
        AtomicLong now = new AtomicLong(1_795_000_000_000L);
        SuiteDiagnose d = new SuiteDiagnose(tmp, "1.7.16", src, now::get);
        for (int i = 0; i < 40; i++) {
            d.error("Error number " + i + " " + "€".repeat(1000));
        }
        d.tick();
        byte[] raw = Files.readAllBytes(d.file());
        assertTrue(raw.length <= SuiteDiagnose.MAX_BYTES, "size " + raw.length);
        JsonNode j = Hub.JSON.readTree(raw);
        assertTrue(j.path("summary").size() <= 20);
        assertTrue(j.path("checks").size() <= 20);
        assertTrue(j.path("errors").size() <= 20);
        for (Iterator<Map.Entry<String, JsonNode>> it = j.path("summary").fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> e = it.next();
            assertTrue(e.getKey().length() <= SuiteDiagnose.MAX_KEY);
            assertTrue(e.getValue().asText().length() <= 200);
        }
        for (JsonNode c : j.path("checks")) {
            assertTrue(c.path("name").asText().length() <= SuiteDiagnose.MAX_KEY);
            assertTrue(c.path("detail").asText().length() <= 200);
        }
        for (JsonNode e : j.path("errors")) {
            assertTrue(e.path("text").asText().length() <= 300);
        }
        if (j.path("errors").size() > 0) {
            assertTrue(j.path("errors").get(j.path("errors").size() - 1).path("text").asText().startsWith("Error number 39"),
                    "the newest error is last and the oldest go first");
        }
        assertEquals("diagnose", j.path("suite").asText(), "the required fields are never cut");
    }

    @Test
    void theSameErrorAgainCountsUpAndMovesToTheEnd() throws Exception {
        AtomicLong now = new AtomicLong(1_795_000_000_000L);
        SuiteDiagnose d = new SuiteDiagnose(tmp, "1", new FakeSource(), now::get);
        d.error("A");
        d.error("B");
        now.addAndGet(3000);
        d.error("A");
        d.error("A");
        d.tick();
        JsonNode errs = read(d).path("errors");
        assertEquals(2, errs.size());
        assertEquals("B", errs.get(0).path("text").asText());
        assertEquals("A (3x)", errs.get(1).path("text").asText());
        assertEquals(SuiteDiagnose.iso(now.get()), errs.get(1).path("at").asText());
    }

    @Test
    void writesAtMostEvery30sWithoutChangesAndNeverTwiceWithinASecond() {
        FakeSource src = new FakeSource();
        AtomicLong now = new AtomicLong(1_795_000_000_000L);
        long t0 = now.get();
        SuiteDiagnose d = new SuiteDiagnose(tmp, "1", src, now::get);
        assertTrue(d.tick(), "at the start");
        now.set(t0 + 10_000);
        assertFalse(d.tick(), "nothing changed");
        now.set(t0 + 29_999);
        assertFalse(d.tick());
        now.set(t0 + 30_000);
        assertTrue(d.tick(), "every 30 s");
        assertEquals(2, src.asked.get(), "the full state is only built for a write");

        src.signature.set("b");
        now.set(t0 + 30_500);
        assertFalse(d.tick(), "a change within 1 s of the last write waits");
        now.set(t0 + 31_000);
        assertTrue(d.tick(), "then it is written");
        now.set(t0 + 32_000);
        assertFalse(d.tick(), "and only once");

        d.error("Art-Net timecode (BindException)");
        now.set(t0 + 32_500);
        assertTrue(d.tick(), "a new error is written soon");
        d.error("Art-Net timecode (BindException)");
        now.set(t0 + 33_000);
        assertFalse(d.tick(), "not within 1 s");
        now.set(t0 + 33_500);
        assertTrue(d.tick());
    }

    @Test
    void writesAtomicallyAndLeavesNoTempFile() throws Exception {
        FakeSource src = new FakeSource();
        AtomicLong now = new AtomicLong(1_795_000_000_000L);
        SuiteDiagnose d = new SuiteDiagnose(tmp, "1", src, now::get);
        d.tick();
        Path file = d.file();
        AtomicBoolean stop = new AtomicBoolean();
        AtomicReference<String> broken = new AtomicReference<>();
        AtomicInteger reads = new AtomicInteger();
        Thread reader = new Thread(() -> {
            while (!stop.get()) {
                try {
                    byte[] b = Files.readAllBytes(file);
                    JsonNode j = Hub.JSON.readTree(b);
                    if (!"lj".equals(j.path("id").asText())) {
                        broken.set(new String(b, StandardCharsets.UTF_8));
                    }
                    reads.incrementAndGet();
                } catch (java.nio.file.NoSuchFileException e) {
                    broken.set("file missing for a moment");
                } catch (Exception e) {
                    broken.set(e.toString());
                }
            }
        });
        reader.start();
        Map<String, String> big = new LinkedHashMap<>();
        for (int i = 0; i < 20; i++) {
            big.put("k" + i, "v".repeat(190));
        }
        for (int i = 0; i < 300; i++) {
            src.state = new SuiteDiagnose.State(i % 2 == 0 ? big : Map.of("State", "x" + i), List.of());
            src.signature.set("s" + i);
            now.addAndGet(1_000);
            d.tick();
        }
        stop.set(true);
        reader.join();
        assertNull(broken.get(), "a reader always sees a whole file");
        assertTrue(reads.get() > 0);
        assertFalse(Files.exists(file.resolveSibling("lj.json.tmp")), "no temp file is left");
        try (var list = Files.list(file.getParent())) {
            assertEquals(List.of("lj.json"), list.map(p -> p.getFileName().toString()).toList());
        }
    }

    @Test
    void aFolderThatCannotBeWrittenNeverThrows() throws Exception {
        Path notADir = tmp.resolve("Link-Suite");
        Files.writeString(notADir, "a file where the folder should be");
        SuiteDiagnose d = new SuiteDiagnose(notADir, "1", new FakeSource(), System::currentTimeMillis);
        assertDoesNotThrow(() -> d.tick());
        assertDoesNotThrow(() -> d.error("x"));
        assertDoesNotThrow(d::stop);
        assertFalse(Files.exists(notADir.resolve("diagnose")));

        Path readOnly = tmp.resolve("ro");
        Files.createDirectories(readOnly.resolve("diagnose"));
        assertTrue(readOnly.resolve("diagnose").toFile().setWritable(false));
        try {
            SuiteDiagnose r = new SuiteDiagnose(readOnly, "1", new FakeSource(), System::currentTimeMillis);
            assertDoesNotThrow(() -> r.tick());
            assertDoesNotThrow(r::stop);
        } finally {
            readOnly.resolve("diagnose").toFile().setWritable(true);
        }

        SuiteDiagnose.Source failing = new SuiteDiagnose.Source() {
            @Override
            public String signature() {
                throw new IllegalStateException("broken source");
            }

            @Override
            public SuiteDiagnose.State state() {
                throw new IllegalStateException("broken source");
            }
        };
        Files.createDirectories(tmp.resolve("f")); // the Link-Suite folder exists (launcher installed)
        SuiteDiagnose f = new SuiteDiagnose(tmp.resolve("f"), "1", failing, System::currentTimeMillis);
        assertDoesNotThrow(() -> f.tick());
        assertTrue(Files.exists(f.file()), "a broken source still gives the required fields");
    }

    @Test
    void theFolderComesFromUserHomeAndAnOverride() {
        assertEquals(Path.of("/Users/test/Library/Application Support/Link-Suite"),
                SuiteDiagnose.suiteDataDir("Mac OS X", "/Users/test", k -> null, null));
        assertEquals(Path.of("/tmp/x"), SuiteDiagnose.suiteDataDir("Mac OS X", "/Users/test", k -> null, "/tmp/x"));
        // Windows: APPDATA only when it lies inside user.home (a test instance with its own user.home keeps out).
        Path inside = SuiteDiagnose.suiteDataDir("Windows 11", "C:\\Users\\Max", k -> "APPDATA".equals(k) ? "C:\\Users\\Max\\AppData\\Roaming" : null, null);
        assertTrue(inside.toString().replace('\\', '/').equals("C:/Users/Max/AppData/Roaming/Link-Suite"), inside.toString());
        Path outside = SuiteDiagnose.suiteDataDir("Windows 11", "D:\\test\\home97", k -> "APPDATA".equals(k) ? "C:\\Users\\Max\\AppData\\Roaming" : null, null);
        assertTrue(outside.toString().replace('\\', '/').startsWith("D:/test/home97/AppData/Roaming/Link-Suite"), outside.toString());
        assertEquals(Path.of("/home/t/.config/Link-Suite"), SuiteDiagnose.suiteDataDir("Linux", "/home/t", k -> "/etc/xdg", null));
        // The running JVM: the folder lies inside user.home (or the override), never elsewhere.
        String override = System.getProperty(SuiteDiagnose.DIR_PROPERTY);
        Path here = SuiteDiagnose.suiteDataDir();
        assertTrue(override != null || here.startsWith(Path.of(System.getProperty("user.home"))), here.toString());
    }

    @Test
    void cleaningRemovesWhatNeverGoesIn() {
        String t = SuiteDiagnose.clean("C:\\Users\\PHILIP~1\\AppData\\Local\\Temp\\x.db, file:///Users/Max%20M/Music/a.mp3, "
                + "/Volumes/Kunde X/Show/b.wav, /Users/Max Muster/Desktop, max@example.com, 93.184.216.34, 2.0.0.1, "
                + "192.168.1.50, 8.8.8.8, a4:83:e7:12:34:56, fe80:0:0:0:1:2:3:4, Philips-MacBook.local, "
                + "abcdefghijklmnopqrstuvwxyzABCDEF123456, \\\\NAS\\Kunden", 2000);
        for (String bad : List.of("PHILIP", "Max", "Kunde", "example.com", "93.184", "8.8.8.8", "a4:83", "fe80", "Philips",
                "abcdefghijklmnop", "NAS", "Kunden")) {
            assertFalse(t.contains(bad), bad + " in " + t);
        }
        assertTrue(t.contains("2.0.0.1"), t);
        assertTrue(t.contains("192.168.1.50"), t);
        assertTrue(t.contains("[IP]"), t);
        String home = System.getProperty("user.home");
        assertEquals("~/x", SuiteDiagnose.clean(home + "/x", 100));
        assertEquals("~/x", SuiteDiagnose.clean(home.toUpperCase() + "/x", 100));
        assertEquals("a b", SuiteDiagnose.clean("a\tb\r\n\tat dev.ljlinkpro.Hub.tick(Hub.java:1)", 100), "the first line only");
        assertEquals("Connected to [Rechner] at 14:03:12, timecode 01:02:03:04, [IPv6] and [IPv6]",
                SuiteDiagnose.clean("Connected to Philipps-MacBook-Pro at 14:03:12, timecode 01:02:03:04, fe80::1 and 2001:db8:0:0:0:0:0:1", 200,
                        "Philipps-MacBook-Pro.local"));
        assertEquals("localhost:8000", SuiteDiagnose.clean("localhost:8000", 100, "localhost"));
        String tmpDir = System.getProperty("java.io.tmpdir");
        assertTrue(SuiteDiagnose.clean(tmpDir + "/ljl-1.db", 100).startsWith("[tmp]"), SuiteDiagnose.clean(tmpDir + "/ljl-1.db", 100));
        assertEquals("[tmp]", SuiteDiagnose.clean("/private/var/folders/ab/xyz/T/x.db", 100));
        assertEquals("[Pfad]", SuiteDiagnose.clean("D:\\Shows\\Kunde\\set.json", 100));
        assertEquals("Java 21.0.13.0.3, LJ Link 1.7.15", SuiteDiagnose.clean("Java 21.0.13.0.3, LJ Link 1.7.15", 100));
        assertEquals("from [IP].", SuiteDiagnose.clean("from 93.184.216.34.", 100));
        assertEquals("[IP]:8000", SuiteDiagnose.clean("93.184.216.34:8000", 100));
        assertEquals(10, SuiteDiagnose.clean("ab ".repeat(30), 10).length());
    }

    @Test
    void logLinesBecomeGenericEntriesWithoutTheirFreeText() {
        assertEquals("WARN org.deepsymmetry.beatlink.VirtualCdj (SocketException)",
                DiagnoseReport.errorText("[beat-link thread] WARN org.deepsymmetry.beatlink.VirtualCdj - Problem on 93.184.216.34 "
                        + "with \"Secret Title\": java.net.SocketException: closed"));
        assertNull(DiagnoseReport.errorText("[main] INFO org.deepsymmetry.beatlink.VirtualCdj - started"));
        assertNull(DiagnoseReport.errorText("\tat dev.ljlinkpro.Hub.tick(Hub.java:10)"));
        assertNull(DiagnoseReport.errorText("Caused by: java.io.IOException: /Users/max/secret"));
        assertNull(DiagnoseReport.errorText(""));
        assertEquals("Exception java.io.FileNotFoundException",
                DiagnoseReport.errorText("java.io.FileNotFoundException: /Users/max/Music/Artist - Secret Title.mp3"));
        assertEquals("Exception java.lang.IllegalStateException",
                DiagnoseReport.errorText("Exception in thread \"main\" java.lang.IllegalStateException: Secret"));
        assertEquals("Hub tick failed (NullPointerException)",
                DiagnoseReport.errorText("Hub tick failed: java.lang.NullPointerException: Cannot read \"title\""));
        assertEquals("StageLinQ library lookup failed",
                DiagnoseReport.errorText("StageLinQ library lookup failed: /Music/Engine Library/Artist - Secret Title.mp3"));
        assertEquals("Could not delete [Pfad] (IOException)",
                DiagnoseReport.errorText("Could not delete /var/folders/ab/T/ljl-1.db: java.io.IOException: busy"));
        assertEquals("Suite chat", DiagnoseReport.errorText("Suite chat: message from Philipp: Hallo zusammen"));
        assertEquals("Player … is also used by …", DiagnoseReport.errorText("Player \"7\" is also used by 'Philipp'"));
    }

    @Test
    void theErrorStreamReachesTheErrorListenersButTheNormalOutputDoesNot() {
        List<String> got = new ArrayList<>();
        java.util.function.Consumer<String> l = got::add;
        LogBuffer.addErrorListener(l);
        try {
            PrintStream sink = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
            LogBuffer.tee(sink, true).println("on the error stream");
            LogBuffer.tee(sink).println("on the normal output");
        } finally {
            LogBuffer.removeErrorListener(l);
        }
        assertEquals(List.of("on the error stream"), got);
    }

    @Test
    void theReportOfLjLinkHoldsNoTitlesNamesKeysOrPublicAddresses() throws Exception {
        Hub hub = new Hub();
        Hub.Scope pioneer = hub.scope("pioneer", "pioneer", true, "Pro DJ Link");
        pioneer.setState("monitoring", "live", "Connected");
        Deck d1 = pioneer.deck(2);
        d1.name = "CDJ-3000";
        d1.hasTrack = true;
        d1.playing = true;
        d1.title = "Secret Title";
        d1.artist = "Secret Artist";
        d1.key = "8A";
        d1.waveform = new byte[] {1};
        d1.effectiveBpm = 128;
        d1.address = "93.184.216.34";
        Hub.Scope sender = hub.persistentScope("sender-7", SenderInbox.KIND, false, "DJ Philipp Mustermann");
        sender.setState("monitoring", "live", "Connected");
        Deck d2 = sender.deck(1);
        d2.name = "rekordbox";
        d2.hasTrack = true;
        d2.title = "Another Secret";

        DiagnoseReport r = new DiagnoseReport();
        r.hub = hub;
        r.desk = () -> new LinkService.Config("ma", "192.168.1.50", 8000, "/cmd", "BPM", "operator", "Geheim-Passwort-123");
        r.deskStatus = () -> new LinkService.Status(128.0, 42, "Connection refused by 93.184.216.34", LinkService.AUTO, d1.key());
        r.ableton = () -> new AbletonLinkPeer.Status(true, true, 2, 128.0, d1.key(), "");
        r.senders = () -> List.of(Map.of("name", "DJ Philipp Mustermann", "address", "192.168.1.77"));
        r.chatOn = () -> true;
        r.adapters = () -> List.of(new Diagnostics.Adapter("en0", "Philipps WLAN", false,
                List.of(new Diagnostics.Ipv4("192.168.1.20", 24)), 1, 1));
        r.autoUpdate = () -> true;
        r.version = "1.7.15";

        AtomicLong now = new AtomicLong(1_795_000_000_000L);
        r.clock = now::get;
        r.startedAt = now.get() - 3_700_000;
        SuiteDiagnose d = new SuiteDiagnose(tmp, "1.7.15", r, now::get);
        for (String line : List.of(
                "Suite chat: message from Philipp: Hallo zusammen, wir treffen uns um 8",
                "Club key: 3f9a8b7c6d5e4f3a2b1c0d9e8f7a6b5c4d3e2f1a",
                "[t] WARN org.deepsymmetry.beatlink.data.MetadataFinder - No metadata for \"Secret Title\" by Secret Artist",
                "Bonjour on 93.184.216.34 failed: java.io.IOException: no route",
                "java.io.FileNotFoundException: /Users/philipp/Music/Secret Artist - Secret Title.mp3")) {
            String t = DiagnoseReport.errorText(line);
            if (t != null) {
                d.error(t);
            }
        }
        d.tick();
        String file = Files.readString(d.file());
        for (String bad : List.of("Secret", "Philipp", "Mustermann", "Hallo", "Geheim", "operator", "3f9a8b7c", "93.184.216.34",
                "WLAN", "192.168.1.77", "sender-7", "8A")) {
            assertFalse(file.contains(bad), bad + " in\n" + file);
        }
        JsonNode j = Hub.JSON.readTree(file);
        JsonNode s = j.path("summary");
        assertTrue(s.path("Light desk output").asText().contains("grandMA3"), s.toString());
        assertTrue(s.path("Light desk output").asText().contains("192.168.1.50:8000"), "a desk in the show network stays");
        assertTrue(s.path("Light desk output").asText().contains("deck 1"), s.toString());
        assertTrue(s.path("Sources").asText().contains("Pro DJ Link: monitoring, 1 deck"), s.toString());
        assertTrue(s.path("Sources").asText().contains("LJL Link: 1 app, 1 deck"), s.toString());
        assertTrue(s.path("Deck 1").asText().startsWith("CDJ-3000 (Pro DJ Link 2): track, playing"), s.toString());
        assertTrue(s.path("Deck 1").asText().contains("waveform yes"), s.toString());
        assertTrue(s.path("Deck 1").asText().contains("128.00 BPM measured"), s.toString());
        assertTrue(s.path("Deck 2").asText().contains("waveform no"), s.toString());
        assertEquals("1 h 01 min", s.path("Running for").asText());
        assertTrue(s.path("Network").asText().equals("en0 192.168.1.20/24"), s.toString());
        assertTrue(s.path("Options").asText().contains("suite chat on"), s.toString());
        boolean deskError = false;
        for (JsonNode c : j.path("checks")) {
            if (c.path("name").asText().equals("Light desk output")) {
                deskError = !c.path("ok").asBoolean() && c.path("detail").asText().contains("[IP]");
            }
        }
        assertTrue(deskError, j.path("checks").toString());
        assertTrue(j.path("errors").size() >= 4, j.path("errors").toString());
        assertTrue(file.getBytes(StandardCharsets.UTF_8).length <= SuiteDiagnose.MAX_BYTES);
    }

    @Test
    void theSignatureChangesWithWhatMattersButNotWithTheTempo() {
        Hub hub = new Hub();
        Hub.Scope pioneer = hub.scope("pioneer", "pioneer", true, "Pro DJ Link");
        pioneer.setState("searching", "live", "");
        DiagnoseReport r = new DiagnoseReport();
        r.hub = hub;
        String a = r.signature();
        Deck d = pioneer.deck(1);
        d.effectiveBpm = 120;
        String b = r.signature();
        assertFalse(a.equals(b), "a new deck");
        d.effectiveBpm = 121;
        assertEquals(b, r.signature(), "the tempo is no reason to write");
        d.hasTrack = true;
        assertFalse(b.equals(r.signature()), "a track was loaded");
        String c = r.signature();
        pioneer.setState("error", "live", "failed");
        assertFalse(c.equals(r.signature()), "the source failed");
    }

    // ------------------------------------------------------------------ ported from Suite-Launcher tests/diagnose.test.ts

    /** "Vorlage v1.1: clean() beim Schreiben": the same inputs and results with the same env. */
    @Test
    void templateV11CleanCasesGiveTheSameResults() {
        List<String> home = List.of("/Users/philipp");
        String tmpDir = "/var/folders/x7/abc/T";
        String host = "Philipps-MBP";
        java.util.function.Function<String, String> t = x -> SuiteDiagnose.clean(x, 300, home, tmpDir, host);
        assertEquals("Fehler in ~/Shows/a.mp4", t.apply("Fehler in /USERS/PHILIPP/Shows/a.mp4\n at f (x.js:1)"));
        assertEquals("~\\AppData\\x", t.apply("C:\\Users\\PHILIP~1\\AppData\\x"));
        assertEquals("[tmp]/y.log", t.apply("/private/var/folders/x7/abc/T/y.log"));
        assertEquals("NDI-Quelle [Rechner] (VJ)", t.apply("NDI-Quelle PHILIPPS-MBP (VJ)"));
        assertEquals("[IPv6] und [IP], Art-Net 2.0.0.1, TC 01:00:00:00",
                t.apply("fe80::1 und 93.184.216.34, Art-Net 2.0.0.1, TC 01:00:00:00"));
        assertEquals("[E-Mail] [MAC] [Freigabe] [Datei]", t.apply("max@example.com aa:bb:cc:dd:ee:ff \\\\nas\\shows file:///x"));
    }

    /** "zweite Schranke: was nie mitgehen darf, wird ersetzt (Rechtsprüfung A2)", line by line (clean keeps one line). */
    @Test
    void launcherScrubCasesAreAlsoFilteredWhenWriting() {
        List<String> lines = List.of("C:\\Users\\PHILIP~1\\AppData\\Local\\Temp\\x.log", "file:///Users/Max%20M/Shows/a.mp4",
                "/Users/max/Library/x", "/Volumes/Kunde X/Videos", "\\\\nas\\shows\\a", "max@example.com", "aa:bb:cc:dd:ee:ff",
                "fe80:0:0:0:1:2:3:4", "93.184.216.34", "2.0.0.1", "192.168.1.20", "studio-mac.local",
                "token abcdefghijklmnopqrstuvwxyz0123456789");
        StringBuilder all = new StringBuilder();
        for (String l : lines) {
            all.append(SuiteDiagnose.clean(l, 300, List.of(), "", null)).append('\n');
        }
        String t = all.toString();
        assertFalse(java.util.regex.Pattern.compile("PHILIP|Max%20M|/Users/max|Kunde X|nas\\\\shows|max@example|aa:bb|fe80|93\\.184|studio-mac|abcdefghijklmnop")
                .matcher(t).find(), t);
        assertTrue(t.contains("2.0.0.1"), t);
        assertTrue(t.contains("192.168.1.20"), t);
        assertTrue(t.contains("[IP]"), t);
    }

    /** "Vorlage für die Programme schreibt eine Datei, die der Launcher liest". */
    @Test
    void theWriterKeepsTheUserFolderOutAndEndsWithRunningFalse() throws Exception {
        String home = System.getProperty("user.home");
        FakeSource src = new FakeSource();
        src.state = new SuiteDiagnose.State(Map.of("Pfad", Path.of(home, "Shows", "x").toString()),
                List.of(new SuiteDiagnose.Check("Art-Net", true, null)));
        SuiteDiagnose d = new SuiteDiagnose(tmp, "0.1.18", src, System::currentTimeMillis);
        d.tick();
        d.error("kaputt");
        d.stop();
        String text = Files.readString(d.file());
        JsonNode j = Hub.JSON.readTree(text);
        assertFalse(j.path("running").asBoolean());
        assertEquals(Path.of("~", "Shows", "x").toString(), j.path("summary").path("Pfad").asText());
        assertFalse(text.contains(home), text);
        assertEquals("kaputt", j.path("errors").get(0).path("text").asText());
        assertEquals("", j.path("checks").get(0).path("detail").asText());
    }

    // ------------------------------------------------------------------ LJ Link: no device names, no titles

    @Test
    void onlyTheModelOfADeviceIsKeptNeverItsName() {
        assertEquals("CDJ-3000", DiagnoseReport.model("CDJ-3000 von XY"));
        assertEquals("CDJ-3000X", DiagnoseReport.model("CDJ-3000X"));
        assertEquals("XDJ-AZ", DiagnoseReport.model("XDJ-AZ"));
        assertEquals("DJM-A9", DiagnoseReport.model("Philipps DJM-A9"));
        assertEquals("Denon SC6000M", DiagnoseReport.model("Denon SC6000M"));
        assertEquals("Denon Prime 4+", DiagnoseReport.model("Denon Prime 4+"));
        assertEquals("rekordbox", DiagnoseReport.model("rekordbox"));
        assertEquals("Serato DJ Pro", DiagnoseReport.model("Serato DJ Pro 3.2.1"));
        assertEquals("TCS-SHOWKONTROL", DiagnoseReport.model("TCS-SHOWKONTROL"));
        assertEquals("other device", DiagnoseReport.model("Philipps Pult"));
        assertEquals("other device", DiagnoseReport.model("Booth Max"));
        assertEquals("other device", DiagnoseReport.model(null));
    }

    @Test
    void deviceNamesTitlesAndArtistsStayOutOfDecksChecksAndErrors() throws Exception {
        Hub hub = new Hub();
        Hub.Scope pioneer = hub.scope("pioneer", "pioneer", true, "Pro DJ Link");
        pioneer.setDevices(List.of(new Hub.DeviceInfo(1, "CDJ-3000 Booth Maxi", "192.168.1.11"),
                new Hub.DeviceInfo(33, "DJM-A9 Studio Maxi", "192.168.1.33"), new Hub.DeviceInfo(5, "Hinterzimmer-Gerät", "192.168.1.5")));
        Deck d = pioneer.deck(1);
        d.name = "CDJ-3000 Booth Maxi";
        d.hasTrack = true;
        d.title = "Geheimer Titel";
        d.artist = "Geheime Künstlerin";
        DiagnoseReport r = new DiagnoseReport();
        r.hub = hub;
        AtomicLong now = new AtomicLong(1_795_000_000_000L);
        SuiteDiagnose diag = new SuiteDiagnose(tmp, "1.7.15", r, now::get);
        r.signature(); // takes in the names seen now
        pioneer.setState("error", "live", "Pro DJ Link: Hinterzimmer-Gerät stopped while loading Geheimer Titel by Geheime Künstlerin");
        for (String line : List.of(
                "Mixer DJM-A9 Studio Maxi (device 33) is announced but sends no mixer status LJ Link can read",
                "Phrases of Geheimer Titel not readable: java.io.IOException: x",
                "Player number 5 is also used by Hinterzimmer-Gerät at 192.168.1.5: LJ Link gives it up")) {
            String t = DiagnoseReport.errorText(line);
            if (t != null) {
                diag.error(t);
            }
        }
        diag.tick();
        String file = Files.readString(diag.file());
        for (String bad : List.of("Maxi", "Booth", "Studio", "Hinterzimmer", "Geheim")) {
            assertFalse(file.contains(bad), bad + " in\n" + file);
        }
        JsonNode j = Hub.JSON.readTree(file);
        assertTrue(j.path("summary").path("Deck 1").asText().startsWith("CDJ-3000 (Pro DJ Link 1)"), j.path("summary").toString());
        assertTrue(file.contains("Mixer DJM-A9 (device 33) is announced"), file);
        assertTrue(file.contains("[Gerät]"), file);
        assertTrue(file.contains("[Titel]"), file);
    }

    @Test
    void writesNothingWithoutTheLinkSuiteFolder(@org.junit.jupiter.api.io.TempDir Path home) throws Exception {
        Path suite = home.resolve("Link-Suite");
        AtomicLong now = new AtomicLong(1_795_000_000_000L);
        SuiteDiagnose d = new SuiteDiagnose(suite, "1", new FakeSource(), now::get);
        d.tick();
        assertFalse(Files.exists(suite), "the Link-Suite folder is never created by LJ Link");
        Files.createDirectories(suite);
        now.addAndGet(40_000);
        d.tick();
        assertTrue(Files.exists(suite.resolve("diagnose").resolve("lj.json")), "written once the launcher's folder exists");
    }
}
