package dev.ljlinkpro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.deepsymmetry.beatlink.CdjStatus;
import org.deepsymmetry.beatlink.MixerStatus;
import org.junit.jupiter.api.Test;

/** Mixer by announcement, the metadata hint, ShowKontrol and a clean stop (round 2 of the DJM-A9 report; Alpha, no hardware). */
class ProDjLinkRound2Test {
    private static final byte[] HEADER = {0x51, 0x73, 0x70, 0x74, 0x31, 0x57, 0x6d, 0x4a, 0x4f, 0x4c};

    private static byte[] packet(int length, int type, String name, int number) {
        byte[] b = new byte[length];
        System.arraycopy(HEADER, 0, b, 0, HEADER.length);
        b[0x0a] = (byte) type;
        byte[] n = name.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(n, 0, b, 0x0b, n.length);
        b[0x21] = (byte) number;
        int payload = b.length - 36;
        b[0x22] = (byte) (payload >> 8);
        b[0x23] = (byte) payload;
        b[0x24] = (byte) number;
        return b;
    }

    private static CdjStatus status(int player, String model) throws Exception {
        byte[] b = packet(0x11c, 0x0a, model, player);
        return new CdjStatus(new DatagramPacket(b, b.length, InetAddress.getLoopbackAddress(), 50002));
    }

    private static MixerStatus mixerStatus(String name, int number) throws Exception {
        byte[] b = packet(0x38, 0x29, name, number);
        return new MixerStatus(new DatagramPacket(b, b.length, InetAddress.getLoopbackAddress(), 50002));
    }

    private static Hub.Scope scope(Hub hub) {
        return hub.scope("pioneer", "pioneer", true, "Pro DJ Link");
    }

    @Test
    void aMixerIsRecognisedByItsAnnouncementAndSaysWhenItSendsNoStatus() throws Exception {
        Hub hub = new Hub();
        Hub.Scope sc = scope(hub);
        ProDjLinkSource s = new ProDjLinkSource(sc);
        s.noteDevice(33, "DJM-A9", "169.254.57.164", true);
        assertEquals(true, sc.mixerBase.get("present"));
        assertEquals("DJM-A9", sc.mixerBase.get("name"));
        assertEquals(33, sc.mixerBase.get("number"));
        assertEquals("waiting", sc.mixerBase.get("statusPackets"));

        s.checkMixerData(false);
        assertEquals("waiting", sc.mixerBase.get("statusPackets"), "not before the virtual CDJ listens");
        s.checkMixerData(true);
        assertEquals("none", sc.mixerBase.get("statusPackets"));
        assertTrue(hub.diagnostics().stream().anyMatch(l -> l.contains("mixer DJM-A9")), "the source line names the mixer");

        s.onUpdate(mixerStatus("DJM-A9", 33));
        assertEquals("ok", sc.mixerBase.get("statusPackets"));
        assertTrue(sc.mixerBase.containsKey("bpm"), "the mixer status adds tempo and master");

        s.noteDevice(33, "DJM-A9", "169.254.57.164", false);
        assertEquals(false, sc.mixerBase.get("present"), "a mixer that left is gone");
    }

    @Test
    void whatCountsAsAMixer() {
        assertTrue(ProDjLinkSource.isMixer(33, "DJM-A9"));
        assertTrue(ProDjLinkSource.isMixer(34, "something"));
        assertTrue(ProDjLinkSource.isMixer(17, "DJM-900NXS2"));
        assertFalse(ProDjLinkSource.isMixer(1, "CDJ-3000X"));
        assertFalse(ProDjLinkSource.isMixer(17, "rekordbox"));
        assertFalse(ProDjLinkSource.isMixer(41, null));
    }

    @Test
    void theMetadataHintComesAfterThirtySecondsAndNamesOneLibraryModels() {
        long late = ProDjLinkSource.METADATA_HINT_MS;
        assertNull(ProDjLinkSource.metadataHint("CDJ-3000X", true, false, late - 1), "not too early");
        assertEquals("onelibrary", ProDjLinkSource.metadataHint("CDJ-3000X", true, false, late));
        assertEquals("onelibrary", ProDjLinkSource.metadataHint("XDJ-AZ", true, false, late));
        assertEquals("onelibrary", ProDjLinkSource.metadataHint("OPUS-QUAD", true, false, late));
        assertEquals("missing", ProDjLinkSource.metadataHint("CDJ-3000", true, false, late));
        assertNull(ProDjLinkSource.metadataHint("CDJ-3000X", true, true, late), "the title arrived");
        assertNull(ProDjLinkSource.metadataHint("CDJ-3000X", false, false, late), "unanalysed tracks have their own text");
        assertFalse(ProDjLinkSource.oneLibrary("CDJ-3000"));
    }

    @Test
    void showKontrolOnTheNetworkGivesAQuietNoteAndADiagnosticsLine() {
        Hub hub = new Hub();
        ProDjLinkSource s = new ProDjLinkSource(scope(hub));
        s.ownAddress = ip -> false; // ShowKontrol on another computer
        s.noteDevice(5, "TCS-SHOWKONTROL", "169.254.131.110", true);
        assertTrue(hub.stateJson().contains(ProDjLinkSource.SHOWKONTROL_NOTE), hub.stateJson());
        assertTrue(s.seenDeviceLines(ip -> "en5", true).stream().anyMatch(l -> l.startsWith("NOTE: ShowKontrol (TCS-SHOWKONTROL)")));
        assertTrue(ProDjLinkSource.isShowKontrol("ShowKontrol 4"));
        assertFalse(ProDjLinkSource.isShowKontrol("CDJ-3000"));

        s.noteDevice(5, "TCS-SHOWKONTROL", "169.254.131.110", false);
        assertFalse(hub.stateJson().contains("ShowKontrol"), "the note goes with it");
    }

    @Test
    void showKontrolOnThisComputerStaysNotedAfterBeatLinkStartsIgnoringItsAddress() {
        Hub hub = new Hub();
        ProDjLinkSource s = new ProDjLinkSource(scope(hub));
        s.ownAddress = "169.254.131.110"::equals;
        s.noteDevice(5, "TCS-SHOWKONTROL", "169.254.131.110", true);
        assertTrue(hub.stateJson().contains(ProDjLinkSource.SHOWKONTROL_LOCAL_NOTE), "shown already while searching: " + hub.stateJson());
        // After joining, beat-link ignores announcements from its own address: ShowKontrol seems to leave.
        s.noteDevice(5, "TCS-SHOWKONTROL", "169.254.131.110", false);
        assertTrue(hub.stateJson().contains(ProDjLinkSource.SHOWKONTROL_LOCAL_NOTE), "kept for the whole search");
        List<String> lines = s.seenDeviceLines(ip -> "en5", true);
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("NOTE: ShowKontrol (TCS-SHOWKONTROL) announced itself as device 5 from this computer")),
                String.join("\n", lines));
        assertTrue(s.seenDeviceLines(ip -> "en5", true, true).stream().anyMatch(l -> l.startsWith("NOTE: ShowKontrol on this computer")));
        s.stop();
        assertFalse(hub.stateJson().contains("ShowKontrol"), "gone with the search");
    }

    /** encodeURIComponent of JavaScript. */
    private static String uri(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20").replace("%21", "!").replace("%27", "'")
                .replace("%28", "(").replace("%29", ")").replace("%7E", "~");
    }

    @Test
    void theNotesAndTheSeenDevicesFitIntoTheMailWithARealisticDescription() throws Exception {
        Hub hub = new Hub();
        Hub.Scope sc = scope(hub);
        hub.scope("denon", "denon", true, "Engine DJ"); // still searching, nothing found
        hub.scope("link", "link", false, "Ableton Link");
        ProDjLinkSource s = new ProDjLinkSource(sc);
        s.ownAddress = "169.254.131.110"::equals;
        for (int p = 1; p <= 4; p++) {
            s.noteDevice(p, "CDJ-3000X", "169.254.20." + (10 + p), true);
            for (int i = 0; i < 12345; i++) {
                s.countPacket(p, i % 10 == 0 ? "Beat" : "CdjStatus");
            }
            s.lastStatus.put(p, status(p, "CDJ-3000X"));
            Deck d = sc.deck(p);
            d.name = "CDJ-3000X";
            d.hasTrack = true;
            d.effectiveBpm = 128;
            d.metaHint = "onelibrary";
        }
        s.noteDevice(33, "DJM-A9", "169.254.57.164", true);
        s.noteDevice(5, "TCS-SHOWKONTROL", "169.254.131.110", true);
        s.noteDevice(5, "TCS-SHOWKONTROL", "169.254.131.110", false);
        List<Diagnostics.Adapter> adapters = List.of(
                new Diagnostics.Adapter("en0", "Wi-Fi", false, List.of(new Diagnostics.Ipv4("172.16.200.50", 24)), 0, 0),
                new Diagnostics.Adapter("en5", "USB LAN", false, List.of(new Diagnostics.Ipv4("169.254.131.110", 16)), 0, 0));
        List<String> source = new java.util.ArrayList<>(s.summary(true, adapters));
        // As with a running virtual CDJ and mounted sticks (not running in the test).
        source.replaceAll(l -> l.startsWith("VirtualCdj running") ? "Own player number 7 (1-4 not used), adapter en5 169.254.131.110"
                : l.startsWith("Metadata:") ? "Metadata: MetadataFinder on, CrateDigger on, media 1 usb no db, 2 usb no db, 3 usb no db, 4 usb no db" : l);
        List<String> lines = Diagnostics.summary(hub, source, adapters);

        // The mail text of app.js (shortMailBody), German, a description of about 150 characters and a rating.
        String text = "Der DJM-A9 wird nicht erkannt, und bei vier CDJ-3000X kommen keine Titel und Waveforms. ShowKontrol lief parallel auf dem Mac, WLAN war auch an.";
        assertTrue(text.length() >= 140, "about 150 characters: " + text.length());
        String subject = "LJ Link: " + text.substring(0, 80);
        String head = "BITTE ANHÄNGEN: die Datei LJ-Link-report-2026-11-13-07-04-48.txt aus deinem Download-Ordner. Das Mailprogramm hängt sie nicht selbst an."
                + "\n\nBewertung: 2 von 5 Sternen\n\n" + text + "\n\nKurzfassung der Diagnosedaten (vollständig in der Datei):\n";
        int fit = 0;
        for (int n = lines.size(); n > 0; n--) {
            String body = head + String.join("\n", lines.subList(0, n)) + (n < lines.size() ? "\n…" : "");
            if (("mailto:fehlermelden@nextphilm.de?subject=" + uri(subject) + "&body=" + uri(body)).length() <= 2000) {
                fit = n;
                break;
            }
        }
        List<String> kept = lines.subList(0, fit);
        System.out.println("Mail summary: " + fit + " of " + lines.size() + " lines\n" + String.join("\n", kept));
        String all = String.join("\n", lines);
        assertTrue(kept.stream().anyMatch(l -> l.startsWith("NOTE: mixer DJM-A9")), "mixer note kept: " + fit + " of " + lines.size() + "\n" + all);
        assertTrue(kept.stream().anyMatch(l -> l.startsWith("NOTE: ShowKontrol on this computer")), all);
        assertTrue(kept.stream().anyMatch(l -> l.startsWith("Own player number 7")), all);
        assertTrue(kept.stream().anyMatch(l -> l.startsWith("Seen 33 DJM-A9")), "seen mixer kept: " + fit + "\n" + all);
        assertTrue(lines.indexOf(lines.stream().filter(l -> l.startsWith("Source denon")).findFirst().orElseThrow())
                > lines.indexOf(lines.stream().filter(l -> l.startsWith("Deck 4")).findFirst().orElseThrow()), "empty sources last");
    }

    @Test
    void afterStopNothingRunsAndLateUpdatesBringNoDeckBack() throws Exception {
        Hub hub = new Hub();
        Hub.Scope sc = scope(hub);
        ProDjLinkSource s = new ProDjLinkSource(sc);
        s.noteDevice(5, "TCS-SHOWKONTROL", "169.254.131.110", true);
        s.stop();
        s.stop(); // twice (quit and shutdown hook) is fine
        assertTrue(ProDjLinkSource.beatLinkStopped(), "virtual CDJ, finders and DeviceFinder are stopped");
        assertFalse(hub.stateJson().contains("ShowKontrol"), "the note goes on stop");

        s.onUpdate(status(2, "CDJ-3000X"));
        assertNull(sc.existingDeck(2), "a status that arrives after stop creates no deck");
        assertTrue(hub.allDecks().isEmpty());
    }

    @Test
    void crateDiggerProblemsAreTakenFromTheLogWithTheirCause() {
        List<String> log = List.of(
                "10:00:00 [main] INFO org.deepsymmetry.beatlink.data.CrateDigger - CrateDigger starting because x has.",
                "10:00:01 [pool-1] WARN org.deepsymmetry.beatlink.data.CrateDigger - Problem fetching rekordbox database for media SlotReference[player:1, slot:USB_SLOT], will not offer metadata for it.",
                "10:00:01 java.io.IOException: File not found: /C/PIONEER/rekordbox/export.pdb",
                "10:00:01 \tat org.deepsymmetry.Foo.bar(Foo.java:1)",
                "10:00:02 [pool-1] WARN org.deepsymmetry.beatlink.data.CrateDigger - Attempt to fetch file x from 2 to y failed, tries left: 2",
                "10:00:02 something else");
        List<String> out = ProDjLinkSource.crateDiggerProblems(log, 8);
        assertEquals(2, out.size(), String.join("\n", out));
        assertTrue(out.get(0).contains("USB_SLOT") && out.get(0).contains("| 10:00:01 java.io.IOException: File not found"), out.get(0));
        assertFalse(out.get(1).contains("|"), "no cause line after the second one");
        assertEquals(1, ProDjLinkSource.crateDiggerProblems(log, 1).size(), "only the last ones");
    }

    @Test
    void quittingWaitsForBonjourOnlyBriefly() {
        long t0 = System.nanoTime();
        int open = Bonjour.closeAll(List.of(() -> { }, () -> {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }), 300);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertEquals(1, open, "the slow one is left behind");
        assertTrue(ms < 2000, "returns after the time limit: " + ms + " ms");
        assertEquals(0, Bonjour.closeAll(List.of(() -> { }, () -> { }), 1000));
    }

    @Test
    void newTextsAreTranslated() throws Exception {
        String i18n = KeepAwakeWebTest.resource("i18n.js");
        String js = KeepAwakeWebTest.resource("app.js");
        for (String en : List.of("Not supported", "No track data",
                "Title and waveform from this player are not supported yet: LJ Link cannot read the newer OneLibrary format of this model (Alpha). BPM, time and beats still work.",
                "Title and waveform are not arriving from this player.", "What can I do?", "Help",
                "Mixer recognised (Alpha), but it sends no data LJ Link can read (tempo, master). ON AIR per channel comes from the players.",
                ProDjLinkSource.SHOWKONTROL_NOTE)) {
            assertTrue(i18n.contains("\"" + en + "\": \""), "German text missing for: " + en);
        }
        assertTrue(js.contains("st.notes"), "the notes of the state are shown");
        assertTrue(js.contains("$(\"search-notes\")") && KeepAwakeWebTest.resource("index.html").contains("id=\"search-notes\""),
                "also while searching");
        assertTrue(i18n.contains("\"" + ProDjLinkSource.SHOWKONTROL_LOCAL_NOTE + "\": \""));
        assertTrue(i18n.contains("\"The position of the faders and the EQ knobs are not sent and cannot be shown here.\": \""));
        Map<String, Object> deck = new Deck(1).toJson(false);
        assertTrue(deck.containsKey("metaHint"));
    }
}
