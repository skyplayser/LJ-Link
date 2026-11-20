package dev.ljlinkpro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Issue #2 (XDJ-AZ with Pro DJ Link switched on, Alpha, no hardware here): the mixer assigns LJ Link one of the deck
 * numbers 1-4. The diagnostics must say so instead of "standard numbers 1-4 not used", and the app shows a grey note.
 */
class OwnNumberTest {
    /** The line from the diagnostics in issue #2 (LJ Link 1.7.12, Windows). */
    private static final String LOG_LINE = "16:56:50 [beat-link DeviceFinder receiver] INFO org.deepsymmetry.beatlink.VirtualCdj"
            + " - Mixer at address 192.168.1.143 told us to use device number 3";

    @Test
    void theMixerAssignmentIsReadFromBeatLinksLogLine() {
        ProDjLinkSource.MixerAssignment a = ProDjLinkSource.parseMixerAssignment(LOG_LINE);
        assertEquals("192.168.1.143", a.address());
        assertEquals(3, a.number());
        assertNull(ProDjLinkSource.parseMixerAssignment(
                "16:56:50 [beat-link DeviceFinder receiver] INFO org.deepsymmetry.beatlink.VirtualCdj - Mixer confirmed device assignment."));
        assertNull(ProDjLinkSource.parseMixerAssignment("Mixer at address 1.2.3.4 told us to use any device."));
        assertNull(ProDjLinkSource.parseMixerAssignment(null));
    }

    @Test
    void aNumberAssignedByTheMixerIsNamedAsOneOfTheStandardNumbers() {
        ProDjLinkSource.OwnNumber o = new ProDjLinkSource.OwnNumber(3, 8, "192.168.1.143", "XDJ-AZ");
        assertTrue(o.standard());
        assertTrue(o.byMixer());
        String text = ProDjLinkSource.ownNumberText(o, false);
        assertEquals("one of the standard numbers 1-4, assigned by the mixer XDJ-AZ at 192.168.1.143 (LJ Link asked for 8);"
                + " another player with number 3 would collide with LJ Link", text);
        assertFalse(text.contains("not used"), "the old text was wrong for 1-4");
        assertEquals("1-4, assigned by mixer XDJ-AZ 192.168.1.143 (LJ Link asked for 8)", ProDjLinkSource.ownNumberText(o, true));
    }

    @Test
    void oneToFourWithoutTheLogLineStillCountsAsAssignedByAMixer() {
        // LJ Link never asks for 1-4 and beat-link's self-assignment starts at 7: 1-4 can only come from a mixer.
        ProDjLinkSource.OwnNumber unknown = new ProDjLinkSource.OwnNumber(4, -1, null, null);
        assertTrue(unknown.byMixer());
        assertEquals("one of the standard numbers 1-4, assigned by a mixer; another player with number 4 would collide with LJ Link",
                ProDjLinkSource.ownNumberText(unknown, false));
        ProDjLinkSource.OwnNumber changed = new ProDjLinkSource.OwnNumber(9, 8, null, null);
        assertTrue(changed.byMixer(), "another number than asked for: only a mixer changes it");
        assertEquals("not one of the standard numbers 1-4, assigned by a mixer (LJ Link asked for 8)",
                ProDjLinkSource.ownNumberText(changed, false));
    }

    @Test
    void numbersFromSevenOnAreDescribedAsBefore() {
        assertEquals("not one of the standard numbers 1-4, chosen by LJ Link",
                ProDjLinkSource.ownNumberText(new ProDjLinkSource.OwnNumber(11, 11, null, null), false));
        assertEquals("1-4 not used, chosen by LJ Link",
                ProDjLinkSource.ownNumberText(new ProDjLinkSource.OwnNumber(11, 11, null, null), true));
        assertEquals("not one of the standard numbers 1-4, chosen by beat-link",
                ProDjLinkSource.ownNumberText(new ProDjLinkSource.OwnNumber(12, 0, null, null), false));
        assertEquals("not one of the standard numbers 1-4",
                ProDjLinkSource.ownNumberText(new ProDjLinkSource.OwnNumber(12, -1, null, null), false), "origin unknown");
        // XDJ-AZ in 4-deck mode (issue #2): the mixer "told us to use" the number LJ Link asked for.
        ProDjLinkSource.OwnNumber confirmed = new ProDjLinkSource.OwnNumber(8, 8, "192.168.1.143", "XDJ-AZ");
        assertFalse(confirmed.standard());
        assertEquals("not one of the standard numbers 1-4, confirmed by the mixer XDJ-AZ at 192.168.1.143",
                ProDjLinkSource.ownNumberText(confirmed, false));
    }

    @Test
    void theStateCarriesTheOwnNumberOnlyWhileItIsSet() {
        Hub hub = new Hub();
        Hub.Scope sc = hub.scope("pioneer", "pioneer", true, "Pro DJ Link");
        assertFalse(hub.stateJson().contains("\"ownNumber\""), "nothing to say without a number 1-4");
        sc.setOwnNumber(Map.of("number", 3, "byMixer", true, "address", "192.168.1.143", "name", "XDJ-AZ"));
        String json = hub.stateJson();
        assertTrue(json.contains("\"ownNumber\""));
        assertTrue(json.contains("192.168.1.143"));
        sc.setOwnNumber(null);
        assertFalse(hub.stateJson().contains("\"ownNumber\""));
    }

    @Test
    void theAppShowsTheGreyNoteAndTheXdjAzHintInBothLanguages() throws Exception {
        String i18n = KeepAwakeWebTest.resource("i18n.js");
        String js = KeepAwakeWebTest.resource("app.js");
        String html = KeepAwakeWebTest.resource("index.html");
        for (String en : List.of(
                "LJ Link uses player number {n}, assigned by the mixer {name} at {address}. Another player with number {n} would collide with LJ Link (Alpha).",
                "LJ Link uses player number {n}, assigned by a mixer. Another player with number {n} would collide with LJ Link (Alpha).",
                "XDJ-AZ: title and waveform are not arriving. In a user report they arrived when the USB stick also had the older rekordbox database (rekordbox had added it automatically; if it is missing, export the stick again). Alpha.",
                "The XDJ-AZ sends no deck data. In a user report they arrived only with Pro DJ Link switched on (XDJ-AZ settings, Deck tab; then only 2 decks). Alpha.")) {
            assertTrue(js.contains("t(\"" + en + "\""), "used in the app: " + en);
            assertTrue(i18n.contains("\"" + en + "\": \""), "German text missing for: " + en);
        }
        assertTrue(html.contains("id=\"own-number\" class=\"hint hidden\""), "a grey hint in the monitor view");
        assertTrue(html.contains("id=\"xdjaz-hint\" class=\"hint hidden\""), "a grey hint when the XDJ-AZ sends no deck data");
        assertTrue(js.contains("st.xdjAzNoStatus"));
        assertFalse(i18n.contains("an {address}"), "German: \"unter\" an address");
        assertTrue(js.contains("st.ownNumber"));
        assertTrue(js.contains("wiki-pioneer.html#xdj-az"), "the XDJ-AZ hint links to its wiki section");
        // The CDJ-3000X (and OPUS-QUAD) keep the OneLibrary text.
        assertTrue(js.contains("m.metaHint === \"onelibrary\") setWaveMsg(d, t(\"Title and waveform from this player are not supported yet"));
    }

    @Test
    void anXdjAzWithoutStatusGetsTheNoteOnlyAfterTenSecondsAndUntilAStatusArrives() {
        // 4-deck mode in issue #2: the XDJ-AZ announces 1-4 (and 33), only beat packets arrive.
        List<ProDjLinkSource.SeenDevice> az = new java.util.ArrayList<>();
        for (int n : new int[] {1, 2, 3, 4, 33}) {
            az.add(new ProDjLinkSource.SeenDevice(n, "XDJ-AZ", "192.168.1.143"));
        }
        long late = ProDjLinkSource.NO_STATUS_HINT_MS;
        assertFalse(ProDjLinkSource.xdjAzWithoutStatus(late - 1, az, java.util.Set.of()), "not too early");
        assertTrue(ProDjLinkSource.xdjAzWithoutStatus(late, az, java.util.Set.of()));
        assertFalse(ProDjLinkSource.xdjAzWithoutStatus(late, az, java.util.Set.of(1)), "status from a player: no note");
        assertFalse(ProDjLinkSource.xdjAzWithoutStatus(late, az, java.util.Set.of(2)));
        assertTrue(ProDjLinkSource.xdjAzWithoutStatus(late, az, java.util.Set.of(33)), "the mixer part is no deck");
        List<ProDjLinkSource.SeenDevice> cdj = List.of(new ProDjLinkSource.SeenDevice(1, "CDJ-3000", "192.168.1.20"));
        assertFalse(ProDjLinkSource.xdjAzWithoutStatus(late, cdj, java.util.Set.of()), "only for the XDJ-AZ");
        ProDjLinkSource.SeenDevice gone = new ProDjLinkSource.SeenDevice(1, "XDJ-AZ", "192.168.1.143");
        gone.present = false;
        assertFalse(ProDjLinkSource.xdjAzWithoutStatus(late, List.of(gone), java.util.Set.of()), "switched off: no note");
    }

    @Test
    void theStateCarriesTheXdjAzNoteOnlyWhileItIsOn() {
        Hub hub = new Hub();
        Hub.Scope sc = hub.scope("pioneer", "pioneer", true, "Pro DJ Link");
        assertFalse(hub.stateJson().contains("xdjAzNoStatus"));
        sc.setXdjAzNoStatus(true);
        assertTrue(hub.stateJson().contains("\"xdjAzNoStatus\":true"));
        sc.setXdjAzNoStatus(false);
        assertFalse(hub.stateJson().contains("xdjAzNoStatus"));
    }

    @Test
    void logLinesReachTheListenersAndAFailingListenerBreaksNothing() {
        List<String> got = new java.util.concurrent.CopyOnWriteArrayList<>();
        java.util.function.Consumer<String> failing = l -> {
            throw new IllegalStateException("listener failed");
        };
        java.util.function.Consumer<String> collecting = got::add;
        LogBuffer.addListener(failing);
        LogBuffer.addListener(collecting);
        try {
            java.io.ByteArrayOutputStream console = new java.io.ByteArrayOutputStream();
            java.io.PrintStream tee = LogBuffer.tee(new java.io.PrintStream(console, true, java.nio.charset.StandardCharsets.UTF_8));
            tee.println(LOG_LINE + " (listener test)");
            tee.flush();
            assertTrue(got.contains(LOG_LINE + " (listener test)"), "the complete line, without the line end");
            assertTrue(console.toString(java.nio.charset.StandardCharsets.UTF_8).contains("told us to use device number 3"),
                    "the console still gets the line");
        } finally {
            LogBuffer.removeListener(failing);
            LogBuffer.removeListener(collecting);
        }
    }
}
