package dev.ljlinkpro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

/** The diagnostics for a problem report (summary, devices, adapters) and the honest mail text of the report dialog. */
class ReportDiagnosticsTest {
    private static final List<Diagnostics.Adapter> ADAPTERS = List.of(
            new Diagnostics.Adapter("en0", "Wi-Fi", false, List.of(new Diagnostics.Ipv4("172.16.200.50", 24)), 2, 1),
            new Diagnostics.Adapter("en5", "USB LAN", false, List.of(new Diagnostics.Ipv4("169.254.131.110", 16)), 1, 1),
            new Diagnostics.Adapter("utun0", "utun0", false, List.of(), 1, 1));

    private static ProDjLinkSource source() {
        Hub hub = new Hub();
        return new ProDjLinkSource(hub.scope("pioneer", "pioneer", true, "Pro DJ Link"));
    }

    @Test
    void aMixerThatSendsNoMixerStatusStillShowsUpWithNumberNameAndAddress() {
        ProDjLinkSource s = source();
        for (int p = 1; p <= 4; p++) {
            s.noteDevice(p, "CDJ-3000X", "169.254.1." + p, true);
            s.countPacket(p, "CdjStatus");
        }
        s.noteDevice(33, "DJM-A9", "169.254.57.164", true);
        s.countPacket(33, "Beat");

        List<String> lines = s.seenDeviceLines(ip -> Diagnostics.adapterFor(ip, ADAPTERS), true);
        String all = String.join("\n", lines);
        assertTrue(all.contains("Mixer status packets received: 0"), all);
        assertTrue(all.contains("Seen device 33: DJM-A9 at 169.254.57.164 via en5, Beat 1"), all);
        assertTrue(all.contains("Seen device 1: CDJ-3000X at 169.254.1.1 via en5, CdjStatus 1"), all);
        assertTrue(all.contains("NOTE: mixer DJM-A9 (device 33) is on the network but no readable mixer status arrived from it"), all);
        assertTrue(all.indexOf("NOTE: mixer") < all.indexOf("Seen device"), "the note comes first (the mail keeps the first lines)");

        s.countPacket(33, "MixerStatus");
        all = String.join("\n", s.seenDeviceLines(ip -> "en5", true));
        assertTrue(all.contains("Mixer status packets received: 1"), all);
        assertFalse(all.contains("NOTE: mixer"), "a mixer status arrived: " + all);
    }

    @Test
    void noMixerNoteBeforeTheVirtualCdjListensAndForAMixerThatLeft() {
        ProDjLinkSource s = source();
        s.noteDevice(33, "DJM-A9", "169.254.57.164", true);
        assertFalse(String.join("\n", s.seenDeviceLines(ip -> null, false)).contains("NOTE"));

        s.noteDevice(33, "DJM-A9", "169.254.57.164", false);
        String all = String.join("\n", s.seenDeviceLines(ip -> null, true));
        assertTrue(all.contains("Seen device 33: DJM-A9 at 169.254.57.164 via no adapter in its network, left"), all);
        assertFalse(all.contains("NOTE"), all);
    }

    @Test
    void theSameNumberOnTwoAddressesIsListedTwice() {
        ProDjLinkSource s = source();
        s.noteDevice(7, "LJ Link", "169.254.131.110", true);
        s.noteDevice(7, "TCS-SHOWKONTROL", "169.254.131.110", true);
        s.noteDevice(7, "beat-link", "169.254.9.9", true);
        long seven = s.seenDeviceLines(ip -> "en5", true).stream().filter(l -> l.startsWith("Seen device 7:")).count();
        assertEquals(2, seven, "one entry per number and address");
    }

    @Test
    void adapterForPicksTheAdapterInTheDevicesNetwork() {
        assertEquals("en5", Diagnostics.adapterFor("169.254.57.164", ADAPTERS));
        assertEquals("en0", Diagnostics.adapterFor("172.16.200.9", ADAPTERS));
        assertNull(Diagnostics.adapterFor("192.168.1.10", ADAPTERS));
        assertNull(Diagnostics.adapterFor("fe80::1", ADAPTERS));
        assertNull(Diagnostics.adapterFor("not-an-address", ADAPTERS));
    }

    @Test
    void networkListsAllAdaptersButOnlyCountsIpv6() {
        List<String> lines = Diagnostics.network(ADAPTERS);
        String all = String.join("\n", lines);
        assertTrue(all.contains("en0 (Wi-Fi): [172.16.200.50/24], IPv6 2 (link-local 1)"), all);
        assertTrue(all.contains("en5 (USB LAN): [169.254.131.110/16]"), all);
        assertTrue(all.contains("Active without IPv4: utun0 IPv6 1 (link-local 1)"), all);
        assertTrue(all.contains("NOTE: an adapter has a self-assigned address"), all);
        assertFalse(all.contains("::"), "no IPv6 address is listed");
        assertTrue(all.contains("(IPv6 addresses are only counted, not listed)"), all);
        assertEquals("Adapters: en0 172.16.200.50/24, en5 169.254.131.110/16", Diagnostics.adapterSummary(ADAPTERS));
    }

    @Test
    void twoAdaptersInOneNetworkAreAWarning() {
        List<Diagnostics.Adapter> both = List.of(
                new Diagnostics.Adapter("en0", "Wi-Fi", false, List.of(new Diagnostics.Ipv4("192.168.1.20", 24)), 0, 0),
                new Diagnostics.Adapter("en5", "LAN", false, List.of(new Diagnostics.Ipv4("192.168.1.21", 24)), 0, 0));
        assertTrue(Diagnostics.networkWarnings(both).get(0).startsWith("WARNING: [en0, en5] are in the same network 192.168.1.0/24"));
    }

    @Test
    void theSummaryIsShortAndHasNoTrackTitles() {
        Hub hub = new Hub();
        Hub.Scope scope = hub.scope("pioneer", "pioneer", true, "Pro DJ Link");
        Deck d = scope.deck(1);
        d.name = "CDJ-3000X";
        d.hasTrack = true;
        d.title = "Secret Title";
        d.artist = "Secret Artist";
        List<String> lines = Diagnostics.summary(hub, new Session(hub), ADAPTERS);
        String all = String.join("\n", lines);
        assertTrue(lines.get(0).startsWith("LJ Link "), all);
        assertTrue(all.contains("Adapters: en0 172.16.200.50/24, en5 169.254.131.110/16"), all);
        assertTrue(all.contains("Source pioneer: "), all);
        assertTrue(all.contains("Deck 1 CDJ-3000X: track, 0.00 BPM, title known, no waveform, no detail, grid 0"), all);
        assertFalse(all.contains("Secret"), "no titles or artists: " + all);
        assertTrue(all.length() < 1000, "short: " + all.length());
    }

    @Test
    void theMailTextNoLongerClaimsTheFileIsAttached() throws IOException {
        String js = KeepAwakeWebTest.resource("app.js");
        String i18n = KeepAwakeWebTest.resource("i18n.js");
        assertFalse(js.contains("is attached as a file"), "a mailto link cannot attach a file");
        assertFalse(i18n.contains("hängen als Datei an"), "a mailto link cannot attach a file");
        assertTrue(js.contains("== Summary =="), "the mail takes the summary block");
        assertTrue(js.contains("showAttachBox("), "a visible box after the click");
        assertTrue(KeepAwakeWebTest.resource("index.html").contains("id=\"report-attach\""));
        for (String en : List.of(
                "PLEASE ATTACH: the file {file} from your downloads folder. The mail program does not attach it by itself.",
                "Summary of the diagnostic data (the whole data is in the file):",
                "Please attach the file to the mail yourself",
                "The most important lines are already in the mail text.",
                "If the file is missing, use “Save as file”.",
                "Saved as {file} in your downloads folder. Attach the file to your report or send it to us.")) {
            assertTrue(i18n.contains("\"" + en + "\": \""), "German text missing for: " + en);
            assertTrue(js.contains("\"" + en + "\""), "text used in app.js: " + en);
        }
        assertTrue(i18n.contains("#report-attach"), "generated box text must not be translated twice");
        assertEquals(Diagnostics.SUMMARY_HEADER, "== Summary ==");
    }
}
