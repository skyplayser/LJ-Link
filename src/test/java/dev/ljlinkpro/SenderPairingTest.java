package dev.ljlinkpro;

import com.fasterxml.jackson.databind.JsonNode;
import dev.ljlinkpro.SenderInboxTest.FakePeer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Approval without club.json (protocol "Approval without club.json"): the decision on a hello, the list, the privacy. */
class SenderPairingTest {
    static final String KEY = SenderInboxTest.KEY;
    static final String ANNA = "MacBook von Anna";
    static final String IP = "192.168.1.34";
    private static final long S = 1_000_000_000L;
    static final String DEV = "0123456789abcdef0123456789abcdef";

    private Hub hub;
    private SenderInbox inbox;
    private SenderPairing pairing;
    private final AtomicLong wall = new AtomicLong(1_700_000_000_000L);
    private final List<String> log = new ArrayList<>();

    @BeforeEach
    void setUp() {
        hub = new Hub(WsClient.STALL_NANOS, false);
        pairing = SenderPairing.inMemory(wall::get);
        inbox = new SenderInbox(hub, () -> KEY, () -> "Testclub", log::add, pairing);
    }

    static String dev(int i) {
        return String.format("%08x", i).repeat(4);
    }

    static String hello(String key, String device, String name) {
        return "{\"type\":\"hello\",\"protocol\":1" + (key == null ? "" : ",\"key\":\"" + key + "\"")
                + (device == null ? "" : ",\"device\":\"" + device + "\"") + ",\"name\":\"" + name
                + "\",\"software\":\"Serato DJ Pro 5.0.0\",\"app\":\"0.4.0\",\"names\":false}";
    }

    private FakePeer ask(Object handle, String key, String device, long now) {
        FakePeer p = new FakePeer();
        inbox.open(handle, p, IP, now);
        inbox.message(handle, hello(key, device, ANNA), now);
        return p;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> requests(long now) {
        return (List<Map<String, Object>>) inbox.pairingState(now).get("requests");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> approved() {
        return (List<Map<String, Object>>) inbox.pairingState(0).get("approved");
    }

    private String requestId(long now) {
        return (String) requests(now).get(0).get("id");
    }

    private static void assertReject(FakePeer p, String reason) throws Exception {
        JsonNode last = p.last();
        assertEquals("reject", last.path("type").asText(), p.sent.toString());
        assertEquals(reason, last.path("reason").asText());
        assertEquals(SenderInbox.CLOSE_POLICY, p.closedWith);
    }

    private static String load(int deck, String id) {
        return "{\"type\":\"load\",\"deck\":" + deck + ",\"id\":\"" + id + "\",\"lengthMs\":1000,\"bpm\":128.0}";
    }

    // ---------------------------------------------------------------- the decision on a hello

    @Test
    void unknownMacWaitsAndAllowWelcomesTheSameConnection() throws Exception {
        FakePeer p = ask("a", null, DEV, 0);
        assertEquals("pending", p.last().path("type").asText());
        assertEquals(0, p.closedWith);
        assertFalse(p.large, "still the small message limit while waiting");
        assertEquals(1, requests(0).size());
        assertEquals(ANNA, requests(0).get(0).get("name"));
        assertEquals(IP, requests(0).get(0).get("address"));

        inbox.message("a", load(1, "x"), S); // deck messages before the welcome are ignored
        assertTrue(hub.allDecks().isEmpty());
        inbox.message("a", "{\"type\":\"ping\"}", S);
        assertEquals("pong", p.last().path("type").asText());

        assertEquals(SenderInbox.Allowed.WELCOMED, inbox.allow(requestId(S), 2 * S));
        JsonNode w = p.last();
        assertEquals("welcome", w.path("type").asText());
        assertEquals("s1", w.path("sender").asText());
        assertTrue(p.large);
        assertTrue(requests(2 * S).isEmpty());
        assertEquals(1, approved().size());
        assertEquals(ANNA, approved().get(0).get("name"));
        assertEquals(true, approved().get(0).get("connected"));

        inbox.message("a", load(1, "x"), 3 * S);
        assertEquals(1, hub.allDecks().size(), "deck messages work as usual after the welcome");
        assertEquals("", hub.allDecks().iterator().next().address, "an approved Mac's address does not go to tablets");
    }

    @Test
    void approvedMacIsWelcomedAtOnceAndNameAndLastSeenAreUpdated() throws Exception {
        pairing.approve(DEV, "Old name");
        long first = wall.get();
        wall.addAndGet(60_000);
        FakePeer p = ask("a", null, DEV, 0);
        assertEquals("welcome", p.last().path("type").asText());
        Map<String, Object> e = approved().get(0);
        assertEquals(ANNA, e.get("name"));
        assertEquals(first, e.get("first"));
        assertEquals(first + 60_000, e.get("last"));
    }

    @Test
    void validKeyStillWorksWithAndWithoutDevice() throws Exception {
        FakePeer a = ask("a", KEY, null, 0);
        assertEquals("welcome", a.last().path("type").asText());
        FakePeer b = ask("b", KEY, DEV, 0);
        assertEquals("welcome", b.last().path("type").asText());
        assertEquals(0, pairing.size(), "a key does not put the Mac into the list");
        assertEquals(IP, inbox.senders().get(0).get("address"));
    }

    @Test
    void wrongKeyWithDeviceTakesTheApprovalPath() throws Exception {
        FakePeer p = ask("a", "00000000000000000000000000000000", DEV, 0);
        assertEquals("pending", p.last().path("type").asText());
    }

    @Test
    void noKeyAndNoValidDeviceIsRejectedWithKey() throws Exception {
        for (String device : new String[] {null, "0123", DEV + "0", "0123456789abcdef0123456789abcdeg", ""}) {
            FakePeer p = ask("x" + device, null, device, 0);
            assertReject(p, "key");
        }
        assertTrue(requests(0).isEmpty());
    }

    @Test
    void wrongProtocolComesFirst() throws Exception {
        FakePeer p = new FakePeer();
        inbox.open("a", p, IP, 0);
        inbox.message("a", hello(null, DEV, ANNA).replace("\"protocol\":1", "\"protocol\":2"), 0);
        assertReject(p, "protocol");
    }

    @Test
    void upperCaseDeviceCountsAsTheSameMac() throws Exception {
        pairing.approve(DEV, ANNA);
        FakePeer p = ask("a", null, DEV.toUpperCase(), 0);
        assertEquals("welcome", p.last().path("type").asText());
    }

    @Test
    void denyRefusesForTenMinutes() throws Exception {
        FakePeer p = ask("a", null, DEV, 0);
        assertTrue(inbox.deny(requestId(0), S));
        assertReject(p, "denied");
        assertTrue(requests(S).isEmpty());

        FakePeer again = ask("b", null, DEV, 5 * 60 * S);
        assertReject(again, "denied");
        assertTrue(requests(5 * 60 * S).isEmpty(), "no new request within 10 minutes (no nagging)");

        FakePeer other = ask("c", null, dev(7), 5 * 60 * S);
        assertEquals("pending", other.last().path("type").asText(), "only this Mac is refused");

        FakePeer later = ask("d", null, DEV, S + SenderPairing.DENY_NANOS);
        assertEquals("pending", later.last().path("type").asText(), "after 10 minutes it may ask again");
    }

    @Test
    void fullWithFourSendersBeforeAnyRequest() throws Exception {
        for (int i = 0; i < SenderInbox.MAX_SENDERS; i++) {
            ask("k" + i, KEY, null, 0);
        }
        assertReject(ask("a", null, DEV, 0), "full");
        assertTrue(requests(0).isEmpty(), "no request when LJ Link is full");
        pairing.approve(dev(2), ANNA);
        assertReject(ask("b", null, dev(2), 0), "full");
        assertReject(ask("c", KEY, null, 0), "full");
        assertReject(ask("d", null, null, 0), "key"); // old sticks (no device) still hear "key", as before
    }

    @Test
    void moreThanTwoWaitingRequestsMakeTheNextOneFull() throws Exception {
        for (int i = 0; i <= SenderInbox.MAX_WAITING; i++) {
            assertEquals("pending", ask("w" + i, null, dev(i), 0).last().path("type").asText());
        }
        assertEquals(SenderInbox.MAX_WAITING + 1, requests(0).size());
        assertReject(ask("x", null, dev(99), 0), "full");
        assertEquals(SenderInbox.MAX_WAITING + 1, requests(0).size());
    }

    @Test
    void theSameMacAskingAgainReplacesItsOlderRequest() throws Exception {
        FakePeer first = ask("a", null, DEV, 0);
        FakePeer second = ask("b", null, DEV, S);
        assertEquals(SenderInbox.CLOSE_POLICY, first.closedWith);
        assertEquals("pending", second.last().path("type").asText());
        assertEquals(1, requests(S).size());
    }

    @Test
    void waitingRequestsDoNotUseThePlacesOfConnectionsWithoutHello() throws Exception {
        FakePeer p = ask("a", null, DEV, 0);
        for (int i = 0; i < SenderInbox.MAX_PENDING + 2; i++) {
            inbox.open("n" + i, new FakePeer(), IP, S);
        }
        assertEquals(0, p.closedWith, "a flood of connections does not push the request out");
        assertEquals(1, requests(S).size());
    }

    @Test
    void noAnswerWithin120SecondsIsATimeout() throws Exception {
        FakePeer p = ask("a", null, DEV, 0);
        for (long t = 5; t <= 120; t += 5) {
            inbox.message("a", "{\"type\":\"ping\"}", t * S);
            inbox.sweep(t * S);
        }
        assertEquals(0, p.closedWith, "pings keep the request alive for 120 s");
        inbox.message("a", "{\"type\":\"ping\"}", 121 * S);
        inbox.sweep(121 * S);
        assertReject(p, "timeout");
        assertTrue(requests(121 * S).isEmpty());
        assertFalse(pairing.isDenied(DEV, 121 * S), "a timeout is no refusal");
        assertEquals(SenderInbox.Allowed.GONE, inbox.allow("r1", 122 * S));
    }

    @Test
    void silentWhileWaitingTheRequestGoes() {
        FakePeer p = ask("a", null, DEV, 0);
        inbox.sweep(14 * S);
        assertEquals(0, p.closedWith);
        inbox.sweep(16 * S);
        assertEquals(SenderInbox.CLOSE_GOING_AWAY, p.closedWith, "15-s silence rule");
        assertTrue(requests(16 * S).isEmpty());
    }

    @Test
    void disconnectWhileWaitingTheRequestGoes() {
        ask("a", null, DEV, 0);
        inbox.closed("a");
        assertTrue(requests(0).isEmpty());
        FakePeer b = ask("b", null, dev(2), 0);
        inbox.message("b", "{\"type\":\"bye\"}", S);
        assertEquals(SenderInbox.CLOSE_NORMAL, b.closedWith);
        assertTrue(requests(S).isEmpty());
    }

    @Test
    void allowWhenFullApprovesButRejectsFull() throws Exception {
        FakePeer p = ask("a", null, DEV, 0);
        for (int i = 0; i < SenderInbox.MAX_SENDERS; i++) {
            ask("k" + i, KEY, null, 0);
        }
        assertEquals(SenderInbox.Allowed.FULL, inbox.allow(requestId(0), S));
        assertReject(p, "full");
        assertTrue(pairing.isApproved(DEV), "approved: it gets in on its next try once there is room");
    }

    @Test
    void removeDisconnectsTheApprovedMacAtOnce() throws Exception {
        FakePeer keyed = ask("k", KEY, DEV, 0);
        FakePeer p = ask("a", null, DEV, 0);
        inbox.allow(requestId(0), 0);
        inbox.message("a", load(1, "x"), S);
        assertEquals(1, approved().size());
        assertTrue(inbox.removeApproved((String) approved().get(0).get("id")));
        assertReject(p, "denied");
        assertEquals(0, keyed.closedWith, "a connection with the club key is not touched");
        assertEquals(0, pairing.size());
        assertTrue(hub.allDecks().isEmpty(), "its decks are gone");
        assertFalse(inbox.removeApproved("m999"));
        assertEquals("pending", ask("b", null, DEV, 2 * S).last().path("type").asText(), "it may ask again");
    }

    // ---------------------------------------------------------------- the list

    @Test
    void atMost32EntriesTheOneNotSeenLongestGoes() {
        for (int i = 0; i < SenderPairing.MAX_APPROVED; i++) {
            wall.addAndGet(1000);
            pairing.approve(dev(i), "Mac " + i);
        }
        wall.addAndGet(1000);
        pairing.seen(dev(0), null); // the oldest one was seen again: now dev(1) is the longest not seen
        wall.addAndGet(1000);
        pairing.approve(dev(100), "New Mac");
        assertEquals(SenderPairing.MAX_APPROVED, pairing.size());
        assertTrue(pairing.isApproved(dev(0)));
        assertFalse(pairing.isApproved(dev(1)));
        assertTrue(pairing.isApproved(dev(100)));
    }

    @Test
    void namesAreCutTo64Characters() {
        pairing.approve(DEV, "x".repeat(100) + "\u0007");
        assertEquals(64, ((String) approved().get(0).get("name")).length());
    }

    @Test
    void theApiNeverShowsThePseudonym() throws Exception {
        ask("a", null, DEV, 0);
        pairing.approve(dev(5), ANNA);
        String json = Hub.JSON.writeValueAsString(inbox.pairingState(0));
        assertFalse(json.contains(DEV), json);
        assertFalse(json.contains(dev(5)), json);
        assertTrue(json.contains(ANNA), "the name is for the local API");
    }

    @Test
    void newKeyEmptiesTheListAndRefusesEveryone() throws Exception {
        pairing.approve(dev(1), ANNA);
        FakePeer approved = ask("a", null, dev(1), 0);
        FakePeer waiting = ask("b", null, DEV, 0);
        inbox.forgetEverything();
        assertEquals(0, pairing.size());
        assertReject(approved, "key");
        assertReject(waiting, "key");
        assertTrue(requests(0).isEmpty());
        assertEquals("pending", ask("c", null, dev(1), S).last().path("type").asText(), "it must be approved again");
    }

    @Test
    void deviceIsReadOnlyWhenItIs32HexDigits() throws Exception {
        assertEquals(DEV, SenderPairing.device(Hub.JSON.readTree("{\"device\":\"" + DEV.toUpperCase() + "\"}")));
        assertNull(SenderPairing.device(Hub.JSON.readTree("{\"device\":123}")));
        assertNull(SenderPairing.device(Hub.JSON.readTree("{\"device\":\" " + DEV + "\"}")));
        assertNull(SenderPairing.device(Hub.JSON.readTree("{}")));
    }

    // ---------------------------------------------------------------- privacy

    @Test
    void nameAddressAndPseudonymNeverReachTheLogOrTheProblemReport() throws Exception {
        pairing.approve(dev(3), ANNA);
        ask("a", null, dev(3), 0); // approved
        ask("b", null, DEV, 0); // waiting, then allowed
        inbox.allow(requestId(0), S);
        ask("c", null, dev(4), S); // denied
        inbox.deny(requestId(S), 2 * S);
        ask("d", null, dev(4), 3 * S); // refused again
        ask("e", null, dev(6), 3 * S); // times out
        inbox.sweep(200 * S);
        ask("f", null, dev(8), 200 * S); // goes away while waiting
        inbox.closed("f");
        inbox.removeApproved((String) approved().get(0).get("id"));
        inbox.message("a", "{\"type\":\"bye\"}", 201 * S);
        ask("g", null, dev(9), 202 * S);
        List<String> lines = new ArrayList<>(log);
        lines.addAll(inbox.diagnostics());
        assertFalse(lines.isEmpty());
        for (String l : lines) {
            assertFalse(l.contains("Anna"), l);
            assertFalse(l.contains(IP), l);
            for (String d : new String[] {DEV, dev(3), dev(4), dev(6), dev(8), dev(9)}) {
                assertFalse(l.contains(d), l);
            }
        }
        assertTrue(inbox.diagnostics().stream().anyMatch(l -> l.equals("Approved DJ Macs: 1, approval requests waiting: 1")),
                inbox.diagnostics().toString());
    }

    // ---------------------------------------------------------------- settings and Bonjour

    @Test
    void settingsKeepOnlyPseudonymNameAndTimes() throws Exception {
        String previous = System.getProperty("ljlinkpro.home");
        System.setProperty("ljlinkpro.home", java.nio.file.Files.createTempDirectory("ljlinkpro-pairing").toString());
        try {
            SenderPairing a = SenderPairing.fromSettings();
            a.approve(DEV, ANNA);
            JsonNode saved = Hub.JSON.readTree(AppSettings.getJson(SenderPairing.APPROVED_SETTING));
            assertEquals(1, saved.size());
            List<String> fields = new ArrayList<>();
            saved.get(0).fieldNames().forEachRemaining(fields::add);
            assertEquals(List.of("device", "name", "first", "last"), fields);
            SenderPairing b = SenderPairing.fromSettings();
            assertTrue(b.isApproved(DEV), "kept over a restart");
            assertEquals(ANNA, b.list(java.util.Set.of()).get(0).get("name"));
        } finally {
            if (previous == null) {
                System.clearProperty("ljlinkpro.home");
            } else {
                System.setProperty("ljlinkpro.home", previous);
            }
        }
    }

    @Test
    void bonjourAnnouncesPairAndBooth() {
        Map<String, String> txt = Bonjour.txt("1.7.13", "0123456789abcdef");
        assertEquals("1", txt.get("pair"));
        assertEquals("0123456789abcdef", txt.get("booth"));
        assertEquals("1", txt.get("sender"));
    }
}
