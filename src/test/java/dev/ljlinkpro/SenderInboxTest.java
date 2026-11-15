package dev.ljlinkpro;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The input for LJL Link (protocol version 1): handshake, deck data, limits, timeout. */
class SenderInboxTest {
    static final String KEY = "00112233445566778899aabbccddeeff";
    private static final long S = 1_000_000_000L;

    /** A sender connection that records what LJ Link sends and whether it closed it. */
    static final class FakePeer implements SenderInbox.Peer {
        final List<String> sent = new ArrayList<>();
        int closedWith;
        boolean large;

        @Override
        public void send(String text) {
            sent.add(text);
        }

        @Override
        public void close(int code, String reason) {
            closedWith = code;
        }

        @Override
        public void allowLargeMessages() {
            large = true;
        }

        JsonNode last() throws Exception {
            return Hub.JSON.readTree(sent.get(sent.size() - 1));
        }
    }

    private Hub hub;
    private SenderInbox inbox;
    private final List<String> log = new ArrayList<>();

    @BeforeEach
    void setUp() {
        hub = new Hub(WsClient.STALL_NANOS, false);
        inbox = new SenderInbox(hub, () -> KEY, () -> "Testclub", log::add);
    }

    static String hello(String key, int protocol, String name, boolean names) {
        return "{\"type\":\"hello\",\"protocol\":" + protocol + (key == null ? "" : ",\"key\":\"" + key + "\"")
                + ",\"name\":\"" + name + "\",\"software\":\"Serato DJ Pro 5.0.0\",\"app\":\"0.1.0\",\"names\":" + names + "}";
    }

    private FakePeer connect(Object handle, boolean names) {
        FakePeer p = new FakePeer();
        inbox.open(handle, p, "192.168.1.30", 0);
        inbox.message(handle, hello(KEY, 1, "MacBook von Anna", names), 0);
        return p;
    }

    private static String load(int deck, String id, String title) {
        return "{\"type\":\"load\",\"deck\":" + deck + ",\"id\":\"" + id + "\",\"lengthMs\":243533,\"bpm\":130.0,\"key\":\"6A\""
                + (title == null ? "" : ",\"title\":\"" + title + "\",\"artist\":\"will.i.am\"") + "}";
    }

    private static String waveform(int deck, String id, String kind, int entries, byte[] data) {
        return "{\"type\":\"waveform\",\"deck\":" + deck + ",\"id\":\"" + id + "\",\"kind\":\"" + kind + "\""
                + ("detail".equals(kind) ? ",\"fps\":150" : "") + ",\"entries\":" + entries
                + ",\"data\":\"" + Base64.getEncoder().encodeToString(data) + "\"}";
    }

    private Deck deck(int label) {
        for (Deck d : hub.allDecks()) {
            if (d.label == label) {
                return d;
            }
        }
        return null;
    }

    @Test
    void validHelloIsWelcomed() throws Exception {
        FakePeer p = connect("a", true);
        JsonNode w = p.last();
        assertEquals("welcome", w.path("type").asText());
        assertEquals(1, w.path("protocol").asInt());
        assertEquals("Testclub", w.path("club").asText());
        assertEquals("s1", w.path("sender").asText());
        assertEquals(0, p.closedWith);
        assertTrue(p.large, "after the welcome messages up to 2 MB are allowed");
        assertEquals(1, inbox.senders().size());
        assertEquals("MacBook von Anna", inbox.senders().get(0).get("name"));
    }

    @Test
    void wrongOrMissingKeyIsRejected() throws Exception {
        for (String key : new String[] {"00112233445566778899aabbccddeefe", "", null, KEY.toUpperCase()}) {
            FakePeer p = new FakePeer();
            inbox.open(key + "x", p, "192.168.1.30", 0);
            inbox.message(key + "x", hello(key, 1, "Anna", true), 0);
            assertEquals("reject", p.last().path("type").asText());
            assertEquals("key", p.last().path("reason").asText());
            assertEquals(SenderInbox.CLOSE_POLICY, p.closedWith);
            assertFalse(p.large);
        }
        assertTrue(inbox.senders().isEmpty());
        assertTrue(hub.allDecks().isEmpty());
    }

    @Test
    void otherProtocolIsRejected() throws Exception {
        for (String hello : new String[] {hello(KEY, 2, "Anna", true), hello(KEY, 0, "Anna", true),
                "{\"type\":\"hello\",\"key\":\"" + KEY + "\"}", "{\"type\":\"hello\",\"protocol\":\"1\",\"key\":\"" + KEY + "\"}"}) {
            FakePeer p = new FakePeer();
            inbox.open(hello, p, "192.168.1.30", 0);
            inbox.message(hello, hello, 0);
            assertEquals("reject", p.last().path("type").asText(), hello);
            assertEquals("protocol", p.last().path("reason").asText(), hello);
            assertEquals(SenderInbox.CLOSE_POLICY, p.closedWith);
        }
    }

    @Test
    void fifthSenderIsRejectedAsFull() throws Exception {
        for (int i = 1; i <= SenderInbox.MAX_SENDERS; i++) {
            assertEquals("s" + i, connect("c" + i, false).last().path("sender").asText());
        }
        FakePeer fifth = connect("c5", false);
        assertEquals("reject", fifth.last().path("type").asText());
        assertEquals("full", fifth.last().path("reason").asText());
        assertEquals(SenderInbox.CLOSE_POLICY, fifth.closedWith);
        // One leaves: its number is free again.
        inbox.closed("c2");
        assertEquals("s2", connect("c6", false).last().path("sender").asText());
    }

    @Test
    void atMostTwoWaitingConnectionsPerAddressTheOldestGoes() {
        FakePeer first = new FakePeer();
        FakePeer second = new FakePeer();
        FakePeer third = new FakePeer();
        inbox.open("p1", first, "192.168.1.66", 0);
        inbox.open("p2", second, "192.168.1.66", 1);
        inbox.open("p3", third, "192.168.1.66", 2);
        assertEquals(SenderInbox.CLOSE_POLICY, first.closedWith, "the oldest of this address makes room");
        assertEquals(0, second.closedWith);
        assertEquals(0, third.closedWith, "a sender reconnecting quickly is not kept out by its own earlier attempts");
        inbox.message("p3", hello(KEY, 1, "Anna", true), 2);
        assertEquals(0, third.closedWith);
        inbox.message("p1", hello(KEY, 1, "Anna", true), 2); // the closed one counts no more
        assertEquals(1, inbox.senders().size());
        inbox.closed("p3");
        // Another address is not affected.
        FakePeer p = new FakePeer();
        inbox.open("a", p, "192.168.1.30", 3);
        inbox.message("a", hello(KEY, 1, "Anna", true), 3);
        assertEquals(0, p.closedWith);
        assertEquals(1, inbox.senders().size());
    }

    @Test
    void validSenderGetsInAlthoughAllWaitingPlacesAreTaken() throws Exception {
        List<FakePeer> waiting = new ArrayList<>();
        for (int i = 0; i < SenderInbox.MAX_PENDING; i++) {
            FakePeer w = new FakePeer();
            waiting.add(w);
            inbox.open("p" + i, w, "10.0.0." + (i / 2), i); // 2 per address, 4 addresses
        }
        FakePeer real = new FakePeer();
        inbox.open("real", real, "192.168.1.30", 100);
        assertEquals(SenderInbox.CLOSE_POLICY, waiting.get(0).closedWith, "the oldest waiting connection makes room");
        assertEquals(0, waiting.get(1).closedWith);
        inbox.message("real", hello(KEY, 1, "Anna", true), 101);
        assertEquals("welcome", real.last().path("type").asText());
        assertEquals(0, real.closedWith);
    }

    @Test
    void deckMessagesBeforeWelcomeAreIgnored() {
        FakePeer p = new FakePeer();
        inbox.open("a", p, "192.168.1.30", 0);
        inbox.message("a", load(1, "a81f", "Scream"), 0);
        inbox.message("a", "{\"type\":\"ping\"}", 0);
        assertTrue(hub.allDecks().isEmpty());
        assertTrue(p.sent.isEmpty(), "no pong before the welcome");
        inbox.message("a", hello(KEY, 1, "Anna", true), 0);
        assertTrue(hub.allDecks().isEmpty(), "the early load is not replayed");
    }

    @Test
    void loadCreatesNamedDeck() {
        connect("a", true);
        inbox.message("a", load(2, "a81f", "Scream & Shout"), 0);
        Deck d = deck(2);
        assertNotNull(d);
        assertTrue(d.hasTrack);
        assertEquals(243533, d.durationMs);
        assertEquals(130.0, d.trackBpm);
        assertEquals("6A", d.key);
        assertEquals("Scream & Shout", d.title);
        assertEquals("will.i.am", d.artist);
        assertFalse(d.titleHidden);
        assertEquals("threeband", d.previewStyle);
        assertEquals("threeband", d.detailStyle);
        assertEquals(SenderInbox.KIND, d.scope.kind);
        assertEquals("Serato DJ Pro 5.0.0", d.scope.groupName, "the decks show the software, not the computer's name");
        assertEquals("Serato DJ Pro 5.0.0", d.name, "the computer's name stays out of the deck name (problem reports)");
        assertEquals(2, d.label);
    }

    @Test
    void deckOutsideOneToFourIsIgnored() {
        connect("a", true);
        inbox.message("a", load(0, "x", null), 0);
        inbox.message("a", load(5, "x", null), 0);
        inbox.message("a", "{\"type\":\"load\",\"deck\":\"1\",\"id\":\"x\"}", 0);
        assertTrue(hub.allDecks().isEmpty());
    }

    @Test
    void waveformsAreStoredForTheCurrentTrack() {
        connect("a", true);
        inbox.message("a", load(1, "a81f", null), 0);
        byte[] overview = new byte[400 * 4];
        overview[0] = 10;
        overview[1] = 20;
        overview[2] = 30;
        inbox.message("a", waveform(1, "a81f", "overview", 400, overview), 0);
        byte[] detail = new byte[1500 * 4];
        detail[5] = 99;
        inbox.message("a", waveform(1, "a81f", "detail", 1500, detail), 0);
        Deck d = deck(1);
        assertArrayEquals(overview, d.waveform);
        assertArrayEquals(detail, d.detail);
        assertEquals("threeband", d.detailStyle);
    }

    @Test
    void waveformOfAnotherTrackIsIgnored() {
        connect("a", true);
        inbox.message("a", load(1, "old1", null), 0);
        inbox.message("a", load(1, "new2", null), 0);
        inbox.message("a", waveform(1, "old1", "overview", 4, new byte[16]), 0);
        inbox.message("a", waveform(1, "old1", "detail", 4, new byte[16]), 0);
        inbox.message("a", waveform(2, "new2", "overview", 4, new byte[16]), 0); // no track on deck 2
        assertNull(deck(1).waveform, "a late waveform of the old track must not show on the new one");
        assertNull(deck(1).detail);
        assertNull(deck(2));
    }

    @Test
    void waveformLengthMustBeEntriesTimesFour() {
        connect("a", true);
        inbox.message("a", load(1, "a81f", null), 0);
        inbox.message("a", waveform(1, "a81f", "overview", 400, new byte[399 * 4]), 0);
        inbox.message("a", waveform(1, "a81f", "overview", 400, new byte[400 * 4 + 1]), 0);
        inbox.message("a", waveform(1, "a81f", "overview", 0, new byte[0]), 0);
        inbox.message("a", "{\"type\":\"waveform\",\"deck\":1,\"id\":\"a81f\",\"kind\":\"overview\",\"entries\":1,\"data\":\"!!!\"}", 0);
        inbox.message("a", "{\"type\":\"waveform\",\"deck\":1,\"id\":\"a81f\",\"kind\":\"other\",\"entries\":1,\"data\":\"AAAAAA==\"}", 0);
        // A detail waveform at another resolution cannot be drawn.
        inbox.message("a", "{\"type\":\"waveform\",\"deck\":1,\"id\":\"a81f\",\"kind\":\"detail\",\"fps\":100,\"entries\":1,\"data\":\"AAAAAA==\"}", 0);
        assertNull(deck(1).waveform);
        assertNull(deck(1).detail);
        inbox.message("a", waveform(1, "a81f", "overview", 400, new byte[400 * 4]), 0);
        assertNotNull(deck(1).waveform);
    }

    @Test
    void namesOffNeverStoresTitles() {
        connect("a", false);
        inbox.message("a", load(1, "a81f", "Scream & Shout"), 0);
        Deck d = deck(1);
        assertNull(d.title);
        assertNull(d.artist);
        assertTrue(d.titleHidden);
        assertEquals(true, d.toJson(false).get("titleHidden"));
        assertEquals(130.0, d.trackBpm, "tempo and waveforms still come");
    }

    @Test
    void namesSwitchedOffRemovesTitlesAtOnce() {
        connect("a", true);
        inbox.message("a", load(1, "a", "One"), 0);
        inbox.message("a", load(2, "b", "Two"), 0);
        assertEquals("One", deck(1).title);
        inbox.message("a", "{\"type\":\"names\",\"on\":false}", 0);
        assertNull(deck(1).title);
        assertNull(deck(2).artist);
        assertTrue(deck(1).titleHidden);
        inbox.message("a", load(1, "c", "Three"), 0);
        assertNull(deck(1).title, "still off for the next track");
        inbox.message("a", "{\"type\":\"names\",\"on\":true}", 0);
        inbox.message("a", load(1, "d", "Four"), 0);
        assertEquals("Four", deck(1).title);
    }

    @Test
    void silentSenderTimesOutAndItsDecksGo() {
        FakePeer p = connect("a", true);
        inbox.message("a", load(1, "a", "One"), 0);
        inbox.message("a", "{\"type\":\"ping\"}", 10 * S);
        assertTrue(p.sent.get(p.sent.size() - 1).contains("pong"));
        inbox.sweep(20 * S);
        assertNotNull(deck(1), "the ping at 10 s keeps it alive until 25 s");
        inbox.sweep(25 * S + 1);
        assertTrue(hub.allDecks().isEmpty());
        assertEquals(SenderInbox.CLOSE_GOING_AWAY, p.closedWith);
        assertTrue(inbox.senders().isEmpty());
        assertEquals("idle", hub.phase(), "the scope is gone, too");
    }

    @Test
    void connectionWithoutHelloTimesOutAfterThreeSeconds() {
        FakePeer p = new FakePeer();
        inbox.open("a", p, "192.168.1.30", 0);
        inbox.message("a", "{\"type\":\"ping\"}", 2 * S); // no welcome: does not keep it alive
        inbox.sweep(3 * S);
        assertEquals(0, p.closedWith);
        inbox.sweep(3 * S + 1);
        assertEquals(SenderInbox.CLOSE_GOING_AWAY, p.closedWith);
    }

    @Test
    void refusalsAreLoggedOncePerMinuteAndReason() {
        for (int i = 0; i < 50; i++) {
            FakePeer p = new FakePeer();
            inbox.open("k" + i, p, "192.168.1." + i, i * S / 10);
            inbox.message("k" + i, hello("wrong", 1, "X", true), i * S / 10);
            assertEquals(SenderInbox.reject("key"), p.sent.get(0));
        }
        FakePeer p = new FakePeer();
        inbox.open("proto", p, "192.168.1.200", 5 * S);
        inbox.message("proto", hello(KEY, 7, "X", true), 5 * S);
        assertEquals(2, log.size(), log.toString());
        assertTrue(log.get(0).endsWith("refused: key"), log.get(0));
        assertTrue(log.get(1).endsWith("refused: protocol"), log.get(1));
        inbox.sweep(30 * S);
        assertEquals(2, log.size(), "still within the minute");
        inbox.sweep(61 * S);
        assertEquals(3, log.size(), log.toString());
        assertTrue(log.get(2).contains("49 more refused (key)"), log.get(2));
        inbox.sweep(200 * S);
        assertEquals(3, log.size(), "nothing new to report");
    }

    @Test
    void logLineWithoutSoftwareReadsCleanly() {
        FakePeer p = new FakePeer();
        inbox.open("a", p, "192.168.1.30", 0);
        inbox.message("a", "{\"type\":\"hello\",\"protocol\":1,\"key\":\"" + KEY + "\"}", 0);
        assertEquals("LJL Link s1 connected from 192.168.1.30 (software not given, titles hidden)", log.get(0));
    }

    @Test
    void sendersDoNotSetThePhaseOfTheSearch() throws Exception {
        connect("a", true);
        inbox.message("a", load(1, "a", "One"), 0);
        assertEquals("idle", hub.phase(), "start page, search and Disconnect work as without a sender");
        JsonNode st = Hub.JSON.readTree(hub.stateJson());
        assertEquals("idle", st.path("phase").asText());
        assertEquals(1, st.path("senders").asInt());
        assertEquals("", st.path("message").asText(), "the sender's state is not the search's message");
        Hub.Scope search = hub.scope("pioneer", "pioneer", true, "Pro DJ Link");
        search.setState("searching", "live", "Looking for players");
        assertEquals("searching", hub.phase());
        assertEquals("Looking for players", Hub.JSON.readTree(hub.stateJson()).path("message").asText());
        search.setState("monitoring", "live", "Connected");
        assertEquals("monitoring", hub.phase());
        inbox.closed("a");
        assertEquals(0, Hub.JSON.readTree(hub.stateJson()).path("senders").asInt());
    }

    @Test
    void persistentScopeIsPersistentFromTheStart() {
        Hub.Scope sc = hub.persistentScope("sender-s9", SenderInbox.KIND, false, "X");
        assertTrue(sc.persistent);
        hub.reset();
        assertTrue(sc == hub.persistentScope("sender-s9", SenderInbox.KIND, false, "X"), "a reset does not remove it");
        assertFalse(hub.scope("demo", "demo", true, "Demo").persistent);
    }

    @Test
    void pingIsAnsweredWithPong() throws Exception {
        FakePeer p = connect("a", true);
        inbox.message("a", "{\"type\":\"ping\"}", 0);
        assertEquals("pong", p.last().path("type").asText());
    }

    @Test
    void messageOverTwoMegabytesCloses() {
        FakePeer p = connect("a", true);
        inbox.message("a", load(1, "a", "One"), 0);
        String big = "{\"type\":\"ping\",\"pad\":\"" + "x".repeat(SenderInbox.MAX_MESSAGE_BYTES) + "\"}";
        inbox.message("a", big, 0);
        assertEquals(SenderInbox.CLOSE_TOO_LARGE, p.closedWith);
        assertTrue(hub.allDecks().isEmpty());
    }

    @Test
    void largeMessageBeforeWelcomeCloses() {
        FakePeer p = new FakePeer();
        inbox.open("a", p, "192.168.1.30", 0);
        inbox.message("a", "{\"type\":\"hello\",\"pad\":\"" + "x".repeat(SenderInbox.PENDING_MESSAGE_BYTES) + "\"}", 0);
        assertEquals(SenderInbox.CLOSE_TOO_LARGE, p.closedWith);
    }

    @Test
    void byeAndCloseRemoveDecks() {
        FakePeer p = connect("a", true);
        inbox.message("a", load(1, "a", "One"), 0);
        inbox.message("a", "{\"type\":\"bye\"}", 0);
        assertTrue(hub.allDecks().isEmpty());
        assertEquals(SenderInbox.CLOSE_NORMAL, p.closedWith);

        connect("b", true);
        inbox.message("b", load(3, "a", "One"), 0);
        inbox.closed("b");
        assertTrue(hub.allDecks().isEmpty());
    }

    @Test
    void unloadEmptiesTheDeck() {
        connect("a", true);
        inbox.message("a", load(1, "a", "One"), 0);
        inbox.message("a", waveform(1, "a", "overview", 1, new byte[4]), 0);
        inbox.message("a", "{\"type\":\"unload\",\"deck\":1}", 0);
        Deck d = deck(1);
        assertFalse(d.hasTrack);
        assertNull(d.title);
        assertNull(d.waveform);
        assertEquals(0, d.effectiveBpm);
        inbox.message("a", waveform(1, "a", "overview", 1, new byte[4]), 0);
        assertNull(d.waveform, "no track: nothing to attach a waveform to");
    }

    @Test
    void positionGoesToTheDeck() {
        connect("a", true);
        inbox.message("a", load(1, "a", null), 0);
        inbox.message("a", "{\"type\":\"position\",\"deck\":1,\"id\":\"a\",\"ms\":61234,\"playing\":true,\"source\":\"screen\"}", 0);
        assertEquals(61234, deck(1).positionMs);
        assertTrue(deck(1).playing);
        inbox.message("a", "{\"type\":\"position\",\"deck\":1,\"id\":\"old\",\"ms\":5,\"playing\":false}", 0);
        assertEquals(61234, deck(1).positionMs, "position of another track is ignored");
        inbox.message("a", "{\"type\":\"position\",\"deck\":1,\"id\":\"a\",\"ms\":99999999,\"playing\":false}", 0);
        assertEquals(243533, deck(1).positionMs, "never past the end");
    }

    @Test
    void longNameIsCutAndCleaned() {
        FakePeer p = new FakePeer();
        inbox.open("a", p, "192.168.1.30", 0);
        inbox.message("a", hello(KEY, 1, "\\u0007" + "A".repeat(100), true), 0);
        String name = (String) inbox.senders().get(0).get("name");
        assertEquals("A".repeat(SenderInbox.MAX_NAME), name);
        inbox.message("a", load(1, "a", null), 0);
        assertEquals("Serato DJ Pro 5.0.0", deck(1).scope.groupName, "the computer's name never reaches the decks");
    }

    @Test
    void sameTrackAgainKeepsWaveformAndPosition() {
        connect("a", false);
        inbox.message("a", load(1, "a", null), 0);
        inbox.message("a", "{\"type\":\"waveform\",\"deck\":1,\"id\":\"a\",\"kind\":\"overview\",\"style\":\"threeband\",\"entries\":1,\"data\":\"AQIDAA==\"}", 0);
        inbox.message("a", "{\"type\":\"position\",\"deck\":1,\"id\":\"a\",\"ms\":5000,\"playing\":false}", 0);
        int version = deck(1).trackVersion;
        inbox.message("a", "{\"type\":\"names\",\"on\":true}", 0);
        inbox.message("a", load(1, "a", "Scream & Shout"), 0);
        assertEquals("Scream & Shout", deck(1).title, "titles back");
        assertNotNull(deck(1).waveform, "the waveform stays");
        assertEquals(5000, deck(1).positionMs, "the playhead stays");
        assertEquals(version, deck(1).trackVersion, "not a new track");
        inbox.message("a", load(1, "b", null), 0);
        assertEquals(-1, deck(1).positionMs, "another id is a new track");
        assertNull(deck(1).waveform);
    }

    @Test
    void positionCarriesTheSendersSpeed() {
        connect("a", true);
        inbox.message("a", load(1, "a", null), 0);
        inbox.message("a", "{\"type\":\"position\",\"deck\":1,\"id\":\"a\",\"ms\":1000,\"playing\":true,\"speed\":0.98}", 0);
        assertEquals(0.98, deck(1).positionSnapshot().velocity(), 1e-9, "a single sample already runs at the measured speed");
        inbox.message("a", "{\"type\":\"position\",\"deck\":1,\"id\":\"a\",\"ms\":1250,\"playing\":true}", 0);
        assertEquals(1.0, deck(1).positionSnapshot().velocity(), 1e-9, "without speed: the file's own speed");
        inbox.message("a", "{\"type\":\"position\",\"deck\":1,\"id\":\"a\",\"ms\":1250,\"playing\":false,\"speed\":1.0}", 0);
        assertEquals(0.0, deck(1).positionSnapshot().velocity(), 1e-9, "paused: stands still");
        assertTrue(deck(1).coarsePosition, "0.1 s steps are smoothed in the browser");
    }

    @Test
    void positionNullMeansUnknown() {
        connect("a", true);
        inbox.message("a", load(1, "a", null), 0);
        inbox.message("a", "{\"type\":\"position\",\"deck\":1,\"id\":\"a\",\"ms\":61234,\"playing\":true}", 0);
        assertEquals(61234, deck(1).positionMs);
        assertTrue(deck(1).playing);
        inbox.message("a", "{\"type\":\"position\",\"deck\":1,\"id\":\"a\",\"ms\":null,\"playing\":false}", 0);
        assertEquals(-1, deck(1).positionMs, "Serato hides the deck: no playhead");
        assertFalse(deck(1).playing);
    }

    @Test
    void secondSenderWithTheSameSoftwareGetsANumber() {
        inbox.open("a", new FakePeer(), "192.168.1.30", 0);
        inbox.message("a", hello(KEY, 1, "Mac A", true), 0);
        inbox.open("b", new FakePeer(), "192.168.1.31", 0);
        inbox.message("b", hello(KEY, 1, "Mac B", true), 0);
        inbox.message("a", load(1, "a", null), 0);
        inbox.message("b", load(1, "b", null), 0);
        java.util.Set<String> groups = new java.util.HashSet<>();
        for (Deck d : hub.allDecks()) if (SenderInbox.KIND.equals(d.scope.kind)) groups.add(d.scope.groupName);
        assertEquals(java.util.Set.of("Serato DJ Pro 5.0.0", "Serato DJ Pro 5.0.0 2"), groups);
    }

    @Test
    void searchAndStopKeepSenderDecks() {
        connect("a", true);
        inbox.message("a", load(1, "a", "One"), 0);
        Hub.Scope demo = hub.scope("demo", "demo", true, "Demo");
        demo.deck(1);
        assertEquals(2, hub.allDecks().size());
        hub.reset(); // what Session does on "Search", "Demo" and "Stop"
        assertEquals(1, hub.allDecks().size());
        assertEquals(SenderInbox.KIND, deck(1).scope.kind);
        inbox.message("a", load(2, "b", "Two"), 0);
        assertEquals(2, hub.allDecks().size(), "the sender's scope still works after the reset");
    }

    @Test
    void newKeyDisconnectsEverySender() throws Exception {
        FakePeer p = connect("a", true);
        inbox.message("a", load(1, "a", "One"), 0);
        inbox.disconnectAll();
        assertEquals("reject", p.last().path("type").asText());
        assertEquals("key", p.last().path("reason").asText());
        assertEquals(SenderInbox.CLOSE_POLICY, p.closedWith);
        assertTrue(hub.allDecks().isEmpty());
    }

    @Test
    void brokenAndUnknownMessagesAreIgnored() {
        FakePeer p = connect("a", true);
        inbox.message("a", "not json", 0);
        inbox.message("a", "[1,2]", 0);
        inbox.message("a", "{\"type\":\"pair\",\"code\":\"1234\"}", 0);
        inbox.message("a", "{\"type\":\"setMaster\",\"deck\":1}", 0);
        assertEquals(0, p.closedWith);
        assertEquals(1, inbox.senders().size());
    }

    @Test
    void problemReportHasTheSendersWithoutNamesTitlesOrKey() throws Exception {
        String previousHome = System.getProperty("ljlinkpro.home");
        System.setProperty("ljlinkpro.home", java.nio.file.Files.createTempDirectory("ljlinkpro-sender-report").toString());
        try {
            connect("a", false);
            connect("b", true);
            inbox.message("b", load(1, "a", "Secret Title"), 0);
            inbox.open("w", new FakePeer(), "192.168.1.31", 0);
            LinkService link = new LinkService(hub, false);
            String text = Diagnostics.report(hub, new Session(hub), link, new AbletonLinkPeer(hub, link), inbox);
            assertTrue(text.contains("== LJL Link (Alpha) =="), text);
            assertTrue(text.contains("Sender s1 from 192.168.1.30: Serato DJ Pro 5.0.0, app 0.1.0, titles hidden by the DJ"), text);
            assertTrue(text.contains("titles allowed, decks with a track [1]"), text);
            assertTrue(text.contains("Connections without hello: 1"), text);
            assertFalse(text.contains("Anna"), text);
            assertFalse(text.contains("Secret Title"), text);
            assertFalse(text.contains(KEY), text);
        } finally {
            if (previousHome == null) {
                System.clearProperty("ljlinkpro.home");
            } else {
                System.setProperty("ljlinkpro.home", previousHome);
            }
        }
    }
}
