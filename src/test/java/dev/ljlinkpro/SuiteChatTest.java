package dev.ljlinkpro;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The suite chat room (docs/suite-chat.md) without a network: what one program sends goes to the others by hand. */
class SuiteChatTest {
    private static final long S = 1_000_000_000L;

    /** Records what a program sends (with the delay it asked for). */
    static final class Wire implements SuiteChat.Out {
        final List<String> sent = new ArrayList<>();
        final List<Long> delays = new ArrayList<>();

        @Override
        public void send(String json, long delayMs) {
            sent.add(json);
            delays.add(delayMs);
        }

        String type(int i) throws Exception {
            return Hub.JSON.readTree(sent.get(i)).path("t").asText();
        }

        /** Hands everything sent so far to {@code to} and forgets it. */
        void deliver(SuiteChat to, long now) {
            for (String s : new ArrayList<>(sent)) {
                to.receive(s, now);
            }
            sent.clear();
            delays.clear();
        }
    }

    private static SuiteChat chat(String peer, String app, Wire w) {
        return new SuiteChat(peer, app, w, () -> 1_794_000_000_000L);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> messages(SuiteChat c) {
        return (List<Map<String, Object>>) c.state(0).get("messages");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> peers(SuiteChat c) {
        return (List<Map<String, Object>>) c.state(0).get("peers");
    }

    @Test
    void joiningSaysHereAndAsksForTheHistoryLeavingSaysBye() throws Exception {
        Wire w = new Wire();
        SuiteChat a = chat("aaaaaaaa01", "LJ Link", w);
        a.setNick("Phil", 0);
        a.setOn(true, 0);
        assertEquals("here", w.type(0));
        JsonNode here = Hub.JSON.readTree(w.sent.get(0));
        assertEquals("chat", here.path("suite").asText());
        assertEquals(1, here.path("v").asInt());
        assertEquals("Phil", here.path("nick").asText());
        assertEquals("LJ Link", here.path("app").asText());
        assertEquals("history?", w.type(1));
        a.setOn(false, S);
        assertEquals("bye", w.type(2));
        assertFalse(a.post("Hallo", 2 * S), "off: nothing is sent");
        assertEquals(3, w.sent.size());
    }

    @Test
    void aMessageNeedsANicknameIsSentTwiceAndShownOnce() throws Exception {
        Wire wa = new Wire();
        Wire wb = new Wire();
        SuiteChat a = chat("aaaaaaaa01", "LJ Link", wa);
        SuiteChat b = chat("bbbbbbbb02", "LJL Link", wb);
        a.setOn(true, 0);
        b.setOn(true, 0);
        wa.sent.clear();
        assertFalse(a.post("Hallo", S), "without a nickname you only read (the program asks first)");
        a.setNick("Phil", S);
        wa.sent.clear();
        wa.delays.clear();
        assertTrue(a.post("Drop in 30 s", S));
        assertEquals(2, wa.sent.size(), "every message twice");
        assertEquals(List.of(0L, 300L), wa.delays);
        wa.deliver(b, S);
        List<Map<String, Object>> got = messages(b);
        assertEquals(1, got.size(), "the second copy is dropped");
        assertEquals("Phil", got.get(0).get("nick"));
        assertEquals("LJ Link", got.get(0).get("app"));
        assertEquals("Drop in 30 s", got.get(0).get("text"));
        assertEquals(false, got.get(0).get("self"));
        assertEquals(true, messages(a).get(0).get("self"), "one's own message is in one's own list");
    }

    @Test
    void whoIsInTheRoom() throws Exception {
        Wire wa = new Wire();
        Wire wb = new Wire();
        SuiteChat a = chat("aaaaaaaa01", "LJ Link", wa);
        SuiteChat b = chat("bbbbbbbb02", "VJ-Link", wb);
        a.setOn(true, 0);
        b.setNick("Lena", 0);
        b.setOn(true, 0);
        wb.deliver(a, 0);
        assertEquals(1, peers(a).size());
        assertEquals("Lena", peers(a).get(0).get("nick"));
        assertEquals("VJ-Link", peers(a).get(0).get("app"));
        a.tick(16 * S);
        assertTrue(peers(a).isEmpty(), "silent for 15 s: gone");
        b.setNick("Lena", 17 * S);
        wb.deliver(a, 17 * S);
        assertEquals(1, peers(a).size());
        b.setOn(false, 18 * S);
        wb.deliver(a, 18 * S);
        assertTrue(peers(a).isEmpty(), "bye: gone at once");
        wa.sent.clear();
        a.tick(19 * S);
        assertTrue(wa.sent.isEmpty(), "the last presence was at 16 s");
        a.tick(21 * S);
        assertEquals("here", wa.type(0), "presence again after 5 s");
    }

    @Test
    void aLatecomerGetsTheHistory() throws Exception {
        Wire wa = new Wire();
        Wire wb = new Wire();
        SuiteChat a = chat("aaaaaaaa01", "LJ Link", wa);
        a.setNick("Phil", 0);
        a.setOn(true, 0);
        for (int i = 0; i < 25; i++) {
            a.post("m" + i, i * S);
        }
        wa.sent.clear();
        wa.delays.clear();
        SuiteChat b = chat("bbbbbbbb02", "LJL Link", wb);
        b.setOn(true, 30 * S);
        wb.deliver(a, 30 * S);
        assertEquals(SuiteChat.HISTORY_ANSWER, wa.sent.size(), "the last 20 messages again");
        assertEquals(20L, wa.delays.get(0), "spaced out");
        wa.deliver(b, 30 * S);
        assertEquals(SuiteChat.HISTORY_ANSWER, messages(b).size());
        assertEquals("m5", messages(b).get(0).get("text"));
    }

    @Test
    void limitsAndCleaning() throws Exception {
        Wire wb = new Wire();
        SuiteChat b = chat("bbbbbbbb02", "LJL Link", wb);
        b.setOn(true, 0);
        String head = "{\"suite\":\"chat\",\"v\":1,\"t\":\"msg\",\"peer\":\"cccccccc03\",\"nick\":\"";
        for (int i = 0; i < SuiteChat.BURST + 3; i++) {
            b.receive(head + "X\",\"id\":\"cccccccc03-" + i + "\",\"text\":\"spam " + i + "\",\"at\":1}", S + i);
        }
        assertEquals(SuiteChat.BURST, messages(b).size(), "at most 10 per peer in 10 s");
        b.receive(head + "N".repeat(40) + "\",\"id\":\"cccccccc03-100\",\"text\":\"" + "x".repeat(900)
                + " \\u202Eumgedreht\\nZeile\",\"at\":1}", 20 * S);
        Map<String, Object> last = messages(b).get(messages(b).size() - 1);
        assertEquals(SuiteChat.MAX_NICK, ((String) last.get("nick")).length());
        assertEquals(SuiteChat.MAX_TEXT, ((String) last.get("text")).length());
        b.receive(head + "N\",\"id\":\"cccccccc03-101\",\"text\":\"a\\u202Eb\\nc\",\"at\":1}", 21 * S);
        assertEquals("ab c", messages(b).get(messages(b).size() - 1).get("text"), "direction marks go, line breaks become spaces");
        int n = messages(b).size();
        b.receive("{\"suite\":\"chat\",\"v\":1,\"t\":\"msg\",\"peer\":\"bbbbbbbb02\",\"id\":\"bbbbbbbb02-9\",\"text\":\"echo\"}", 22 * S);
        b.receive("{\"suite\":\"chat\",\"v\":2,\"t\":\"msg\",\"peer\":\"dddddddd04\",\"id\":\"d-1\",\"text\":\"v2\"}", 22 * S);
        b.receive("{\"suite\":\"other\",\"v\":1,\"t\":\"msg\",\"peer\":\"dddddddd04\",\"id\":\"d-2\",\"text\":\"x\"}", 22 * S);
        b.receive("{\"suite\":\"chat\",\"v\":1,\"t\":\"msg\",\"peer\":\"BAD ID\",\"id\":\"d-3\",\"text\":\"x\"}", 22 * S);
        b.receive("{\"suite\":\"chat\",\"v\":1,\"t\":\"msg\",\"peer\":\"dddddddd04\",\"id\":\"d-4\",\"text\":\"  \"}", 22 * S);
        b.receive("not json", 22 * S);
        b.receive("{\"suite\":\"chat\",\"v\":1,\"t\":\"msg\",\"peer\":\"dddddddd04\",\"id\":\"d-5\",\"text\":\"" + "y".repeat(9000) + "\"}", 22 * S);
        assertEquals(n, messages(b).size(), "own echo, other versions, other suites, bad ids, empty, broken, too large: dropped");
        for (int i = 0; i < SuiteChat.MAX_KEPT + 30; i++) {
            String from = "eeeeeeee" + String.format("%02d", i % 40);
            b.receive("{\"suite\":\"chat\",\"v\":1,\"t\":\"msg\",\"peer\":\"" + from + "\",\"id\":\"" + from + "-" + i
                    + "\",\"text\":\"t\"}", (100 + i) * S);
        }
        assertEquals(SuiteChat.MAX_KEPT, messages(b).size(), "memory stays bounded");
        assertTrue(peers(b).size() <= SuiteChat.MAX_PEERS);
    }

    @Test
    void sinceAndProblemReport() throws Exception {
        Wire w = new Wire();
        SuiteChat a = chat("aaaaaaaa01", "LJ Link", w);
        a.setNick("Geheimname", 0);
        a.setOn(true, 0);
        a.post("geheime Nachricht", S);
        a.post("zweite", 2 * S);
        long first = (long) messages(a).get(0).get("n");
        @SuppressWarnings("unchecked")
        List<Object> newer = (List<Object>) a.state(first).get("messages");
        assertEquals(1, newer.size());
        String d = a.diagnostics();
        assertFalse(d.contains("geheim") || d.contains("Geheim"), d);
        assertTrue(d.contains("on") && d.contains("2 messages"), d);
    }

    @Test
    void positionAndEmergency() throws Exception {
        Wire wa = new Wire();
        Wire wb = new Wire();
        SuiteChat a = chat("aaaaaaaa01", "LJL Link", wa);
        SuiteChat b = chat("bbbbbbbb02", "LJ Link", wb);
        a.setOn(true, 0);
        b.setOn(true, 0);
        wb.sent.clear();
        a.setProfile("DJ David", "DJ Booth", 0);
        JsonNode here = Hub.JSON.readTree(wa.sent.get(wa.sent.size() - 1));
        assertEquals("DJ Booth", here.path("where").asText());
        assertTrue(a.post("Feuer an der Bar!", true, S));
        JsonNode msg = Hub.JSON.readTree(wa.sent.get(wa.sent.size() - 1));
        assertTrue(msg.path("urgent").asBoolean(false));
        assertEquals("DJ Booth", msg.path("where").asText());
        wa.deliver(b, S);
        Map<String, Object> got = messages(b).get(0);
        assertEquals(true, got.get("urgent"));
        assertEquals("DJ Booth", got.get("where"));
        assertEquals("DJ Booth", peers(b).get(0).get("where"));
        // A latecomer gets it as history: not urgent any more (no pop-up for something long past).
        Wire wc = new Wire();
        SuiteChat c = chat("cccccccc03", "VJ-Link", wc);
        c.setOn(true, 2 * S);
        wc.deliver(a, 2 * S);
        wa.deliver(c, 2 * S);
        assertEquals(false, messages(c).get(0).get("urgent"));
        assertTrue(a.post("normal", false, 3 * S));
        assertFalse(Hub.JSON.readTree(wa.sent.get(wa.sent.size() - 1)).has("urgent"), "only marked when urgent");
    }

    @Test
    void emergencyStaysOpenUntilOkAnywhereAndUnreadCountsLiveMessages() throws Exception {
        Wire wa = new Wire();
        Wire wb = new Wire();
        SuiteChat a = chat("aaaaaaaa01", "LJL Link", wa);
        SuiteChat b = chat("bbbbbbbb02", "LJ Link", wb);
        int[] told = {0};
        b.setListener(() -> told[0]++);
        a.setProfile("DJ David", "DJ Booth", 0);
        a.setOn(true, 0);
        b.setOn(true, 0);
        int start = told[0];
        assertTrue(a.post("Hallo", S));
        assertTrue(a.post("Feuer an der Bar!", true, S));
        assertTrue(a.post("Zweiter Notfall", true, S));
        wa.deliver(b, S);
        assertEquals(3, b.unread(), "three live messages, each copy counted once");
        assertTrue(told[0] > start, "the window and the program icon are told");
        List<Map<String, Object>> open = b.alerts();
        assertEquals(2, open.size());
        assertEquals("Feuer an der Bar!", open.get(0).get("text"), "oldest first");
        assertEquals("DJ David", open.get(0).get("nick"));
        @SuppressWarnings("unchecked")
        List<Long> inState = (List<Long>) b.state(0).get("alerts");
        assertEquals(List.of((Long) open.get(0).get("n"), (Long) open.get(1).get("n")), inState, "the browser sees the same");
        b.ack((long) open.get(0).get("n"));
        assertEquals(1, b.alerts().size(), "OK in the browser or the window: gone from both");
        assertEquals("Zweiter Notfall", b.alerts().get(0).get("text"));
        b.ack(999_999);
        assertEquals(1, b.alerts().size(), "an unknown number changes nothing");
        b.read();
        assertEquals(0, b.unread());
        assertEquals(0, b.state(0).get("unread"));
        // Own messages are neither unread nor an emergency for oneself.
        b.setNick("Phil", 2 * S);
        assertTrue(b.post("selbst", true, 2 * S));
        assertEquals(0, b.unread());
        assertEquals(1, b.alerts().size());
        // Switching the chat off closes the window and the number.
        b.setOn(false, 3 * S);
        assertTrue(b.alerts().isEmpty());
        assertEquals(0, b.unread());
    }

    @Test
    void historyIsNeitherUnreadNorAnEmergency() throws Exception {
        Wire wa = new Wire();
        SuiteChat a = chat("aaaaaaaa01", "LJL Link", wa);
        a.setNick("DJ David", 0);
        a.setOn(true, 0);
        assertTrue(a.post("Feuer an der Bar!", true, S));
        assertTrue(a.post("normal", S));
        wa.sent.clear(); // sent before the latecomer joined: it never got these live copies
        Wire wc = new Wire();
        SuiteChat c = chat("cccccccc03", "LJ Link", wc);
        c.setOn(true, 2 * S);
        wc.deliver(a, 2 * S);
        wa.deliver(c, 2 * S);
        assertEquals(2, messages(c).size(), "the history arrived");
        assertEquals(0, c.unread(), "history answers do not count as new");
        assertTrue(c.alerts().isEmpty(), "and never open an EMERGENCY window");
    }

    @Test
    void problemReportCountsOpenEmergenciesWithoutText() throws Exception {
        Wire wa = new Wire();
        Wire wb = new Wire();
        SuiteChat a = chat("aaaaaaaa01", "LJL Link", wa);
        SuiteChat b = chat("bbbbbbbb02", "LJ Link", wb);
        a.setNick("Geheimname", 0);
        a.setOn(true, 0);
        b.setOn(true, 0);
        a.post("geheimer Notfall", true, S);
        wa.deliver(b, S);
        String d = b.diagnostics();
        assertTrue(d.contains("1 emergencies open"), d);
        assertFalse(d.contains("geheim") || d.contains("Geheim"), d);
    }

    private static String msg(String peer, int i, boolean urgent) {
        return "{\"suite\":\"chat\",\"v\":1,\"t\":\"msg\",\"peer\":\"" + peer + "\",\"id\":\"" + peer + "-" + i
                + "\",\"nick\":\"X\",\"text\":\"t" + i + "\"" + (urgent ? ",\"urgent\":true" : "") + ",\"at\":1}";
    }

    @Test
    void historyIsAnsweredAtMostOnceIn10sAndOnlyWithMessages() throws Exception {
        Wire w = new Wire();
        SuiteChat a = chat("aaaaaaaa01", "LJ Link", w);
        a.setNick("Phil", 0);
        a.setOn(true, 0);
        w.sent.clear();
        String ask = "{\"suite\":\"chat\",\"v\":1,\"t\":\"history?\",\"peer\":\"cccccccc03\"}";
        a.receive(ask, S);
        assertTrue(w.sent.isEmpty(), "nothing to tell: no answer");
        for (int i = 0; i < 25; i++) {
            a.post("m" + i, 2 * S + i);
        }
        w.sent.clear();
        a.receive(ask, 3 * S);
        assertEquals(SuiteChat.HISTORY_ANSWER, w.sent.size(), "the last 20");
        w.sent.clear();
        for (int i = 0; i < 1000; i++) {
            a.receive(ask.replace("cccccccc03", "dddddd" + String.format("%04d", i)), 4 * S + i);
        }
        assertTrue(w.sent.isEmpty(), "within 10 s nobody gets an answer, whoever asks");
        a.receive(ask, 13 * S + 1);
        assertEquals(SuiteChat.HISTORY_ANSWER, w.sent.size(), "after 10 s again");
    }

    @Test
    void sendQueueIsBounded() {
        SuiteChat.Net net = new SuiteChat.Net();
        for (int i = 0; i < 1000; i++) {
            net.send("{}", 60_000);
        }
        assertEquals(SuiteChat.MAX_QUEUED, net.queued(), "a flood never fills the memory");
    }

    @Test
    void atMostThreeEmergenciesPerPeerInAMinuteAndTwentyOpen() throws Exception {
        Wire w = new Wire();
        SuiteChat b = chat("bbbbbbbb02", "LJ Link", w);
        b.setOn(true, 0);
        for (int i = 1; i <= 5; i++) {
            b.receive(msg("cccccccc03", i, true), S * i);
        }
        assertEquals(3, b.alerts().size(), "3 per peer in 60 s");
        assertEquals(5, messages(b).size(), "the others still arrive, as normal messages");
        assertEquals(false, messages(b).get(4).get("urgent"));
        b.receive(msg("cccccccc03", 6, true), 70 * S);
        assertEquals(4, b.alerts().size(), "a minute later again");
        for (int p = 0; p < 30; p++) {
            b.receive(msg("pppppppp" + String.format("%02d", p), 1, true), 80 * S + p);
        }
        assertEquals(SuiteChat.MAX_OPEN_ALERTS, b.alerts().size(), "at most 20 open");
        long newest = (Long) b.alerts().get(b.alerts().size() - 1).get("n");
        b.ackAll(newest - 1);
        assertEquals(1, b.alerts().size(), "close all: only up to what was shown, a later one stays");
        b.ackAll(newest);
        assertTrue(b.alerts().isEmpty(), "close all");
    }

    @Test
    void anIdBelongsToItsSender() throws Exception {
        Wire w = new Wire();
        SuiteChat b = chat("bbbbbbbb02", "LJ Link", w);
        b.setOn(true, 0);
        b.receive("{\"suite\":\"chat\",\"v\":1,\"t\":\"msg\",\"peer\":\"xxxxxxxx09\",\"id\":\"cccccccc03-1\",\"text\":\"fake\"}", S);
        assertTrue(messages(b).isEmpty(), "someone else's id is dropped");
        b.receive(msg("cccccccc03", 1, false), 2 * S);
        assertEquals(1, messages(b).size(), "so the real message with that id still arrives");
    }

    @Test
    void historyKeepsTheSendersTimeWithinLimitsLiveMessagesTheArrival() throws Exception {
        Wire w = new Wire();
        SuiteChat b = chat("bbbbbbbb02", "LJ Link", w);
        b.setOn(true, 0);
        String[] bad = {"1e300", "-5", "9300000000000000000", "4102444800001", "\"12\"", "1.5"};
        for (int i = 0; i < bad.length; i++) {
            b.receive("{\"suite\":\"chat\",\"v\":1,\"t\":\"msg\",\"peer\":\"cccccccc03\",\"id\":\"cccccccc03-" + i
                    + "\",\"text\":\"t\",\"old\":true,\"at\":" + bad[i] + "}", S / 10 * (i + 1));
        }
        b.receive(oldMsg("cccccccc03", 99).replace("\"at\":1,", "\"at\":4102444800000,"), S);
        List<Map<String, Object>> got = messages(b);
        assertEquals(bad.length + 1, got.size());
        assertTrue(got.stream().allMatch(m -> (long) m.get("at") == 1_794_000_000_000L || (long) m.get("at") == 4_102_444_800_000L),
                "absurd history times become the own clock, the year 2100 itself stays");
        assertEquals(1, got.stream().filter(m -> (long) m.get("at") == 4_102_444_800_000L).count());
        // A live message: always the time it arrived here, whatever the sender's clock says.
        b.receive(msg("dddddddd04", 1, false).replace("\"at\":1}", "\"at\":4000000000000}"), 2 * S);
        assertEquals(1_794_000_000_000L, messages(b).get(messages(b).size() - 1).get("at"));
    }

    private static String oldMsg(String peer, int i) {
        return msg(peer, i, false).replace("\"at\":1}", "\"at\":1,\"old\":true}");
    }

    @Test
    void historyNeverBringsSomeoneBackIntoTheRoom() throws Exception {
        Wire w = new Wire();
        SuiteChat b = chat("bbbbbbbb02", "LJ Link", w);
        b.setOn(true, 0);
        b.receive(oldMsg("gonegone01", 1), 10 * S);
        assertEquals(1, messages(b).size(), "the message is shown");
        assertTrue(peers(b).isEmpty(), "but its sender is not in the room");
        // Someone in the room: a history message neither keeps them in it nor changes their name.
        b.receive("{\"suite\":\"chat\",\"v\":1,\"t\":\"here\",\"peer\":\"cccccccc03\",\"nick\":\"Lena\",\"app\":\"VJ-Link\"}", 20 * S);
        b.receive(oldMsg("cccccccc03", 1), 30 * S);
        b.tick(36 * S);
        assertTrue(peers(b).isEmpty(), "silent for 16 s: gone, although a history message came at 30 s");
    }

    @Test
    void historyOfStrangersIsLimitedOutsideTheOwnWindow() throws Exception {
        Wire w = new Wire();
        SuiteChat b = chat("bbbbbbbb02", "LJ Link", w);
        b.setOn(true, 0);
        for (int i = 0; i < 20; i++) {
            b.receive(oldMsg("ssssssss" + String.format("%02d", i), 1), S + i); // own 5-s window: all taken
        }
        assertEquals(20, messages(b).size());
        for (int i = 0; i < 30; i++) {
            b.receive(oldMsg("tttttttt" + String.format("%02d", i), 1), 10 * S + i);
        }
        assertEquals(30, messages(b).size(), "outside it: 10 in 10 s from strangers, together");
        b.receive(oldMsg("tttttttt00", 1), 10 * S + 100);
        b.receive(oldMsg("tttttttt29", 1), 21 * S);
        assertEquals(31, messages(b).size(), "a duplicate is not counted; 10 s later again");
    }

    @Test
    void moreThanTwentyOpenStayNormalMessages() throws Exception {
        Wire w = new Wire();
        SuiteChat b = chat("bbbbbbbb02", "LJ Link", w);
        b.setOn(true, 0);
        for (int p = 0; p < 25; p++) {
            b.receive(msg("pppppppp" + String.format("%02d", p), 1, true), S + p);
        }
        List<Map<String, Object>> open = b.alerts();
        assertEquals(20, open.size());
        assertEquals((Long) messages(b).get(0).get("n"), (Long) open.get(0).get("n"), "the oldest is not pushed out");
        assertEquals(false, messages(b).get(24).get("urgent"), "the 21st and later are normal messages");
    }

    @Test
    void anUnseenEmergencyBlinksUntilTheChatIsOpened() throws Exception {
        Wire w = new Wire();
        SuiteChat b = chat("bbbbbbbb02", "LJ Link", w);
        b.setNick("Phil", 0);
        b.setOn(true, 0);
        b.receive(msg("cccccccc03", 1, false), S);
        assertFalse(b.urgentUnread(), "a normal message: only the number");
        b.receive(msg("cccccccc03", 2, true), 2 * S);
        assertTrue(b.urgentUnread());
        assertEquals(true, b.state(0).get("urgentUnread"));
        b.ack((long) b.alerts().get(0).get("n"));
        assertTrue(b.urgentUnread(), "OK on the pop-up does not stop the blinking");
        b.read();
        assertFalse(b.urgentUnread(), "opening the chat does");
        b.receive(msg("cccccccc03", 3, true).replace("\"at\":1}", "\"at\":1,\"old\":true}"), 3 * S);
        assertFalse(b.urgentUnread(), "never from the history");
        b.post("selbst", true, 4 * S);
        assertFalse(b.urgentUnread(), "never one's own");
        b.receive(msg("cccccccc03", 4, true), 5 * S);
        b.setOn(false, 6 * S);
        assertFalse(b.urgentUnread(), "chat off: no blinking");
    }

    @Test
    void withoutAHistoryAnswerANewcomerAsksOnceMoreAfter12s() throws Exception {
        Wire w = new Wire();
        SuiteChat a = chat("aaaaaaaa01", "LJ Link", w);
        a.setOn(true, 0);
        w.sent.clear();
        a.tick(11 * S);
        assertFalse(w.sent.stream().anyMatch(x -> x.contains("history?")), "not before 12 s");
        a.tick(12 * S);
        assertEquals(1, w.sent.stream().filter(x -> x.contains("history?")).count(), "asks again");
        w.sent.clear();
        a.tick(30 * S);
        a.tick(60 * S);
        assertFalse(w.sent.stream().anyMatch(x -> x.contains("history?")), "exactly once");

        Wire w2 = new Wire();
        SuiteChat b = chat("bbbbbbbb02", "LJ Link", w2);
        b.setOn(true, 0);
        b.receive(oldMsg("cccccccc03", 1), S);
        w2.sent.clear();
        b.tick(13 * S);
        assertFalse(w2.sent.stream().anyMatch(x -> x.contains("history?")), "a history answer came: no second ask");
    }

    @Test
    void separatorsAndArabicLetterMark() {
        assertEquals("a b c", SuiteChat.text("a\u2028b\u2029c", 50));
        assertEquals("ab", SuiteChat.text("a\u061Cb", 50));
    }

    @Test
    void switchingOffForgetsTheMessages() throws Exception {
        Wire w = new Wire();
        SuiteChat b = chat("bbbbbbbb02", "LJ Link", w);
        b.setNick("Phil", 0);
        b.setOn(true, 0);
        b.post("geheim", S);
        b.receive(msg("cccccccc03", 1, false), S);
        assertEquals(2, messages(b).size());
        b.setOn(false, 2 * S);
        assertTrue(messages(b).isEmpty(), "off: nothing readable any more");
        b.setOn(true, 3 * S);
        b.receive(oldMsg("cccccccc03", 1), 3 * S);
        assertEquals(1, messages(b).size(), "on again: the history comes anew (the id is not remembered as seen)");
    }

    @Test
    void idsCarryARandomTailAndHistoryNamesTheAuthorBeforeTheFirstDash() throws Exception {
        Wire w = new Wire();
        SuiteChat a = chat("aaaaaaaa01", "LJ Link", w);
        a.setNick("Phil", 0);
        a.setOn(true, 0);
        a.post("eins", S);
        String id = Hub.JSON.readTree(w.sent.get(w.sent.size() - 1)).path("id").asText();
        assertTrue(id.matches("aaaaaaaa01-1[0-9a-z]{8}"), id);
        // Someone else's message with the new id format, sent on as history: the author stays valid.
        a.receive("{\"suite\":\"chat\",\"v\":1,\"t\":\"msg\",\"peer\":\"q8w2e4r5t6\",\"id\":\"q8w2e4r5t6-3abcdefgh\","
                + "\"text\":\"von LJL\",\"at\":1}", 2 * S);
        w.sent.clear();
        a.receive("{\"suite\":\"chat\",\"v\":1,\"t\":\"history?\",\"peer\":\"cccccccc03\"}", 3 * S);
        assertTrue(w.sent.stream().anyMatch(x -> x.contains("\"peer\":\"q8w2e4r5t6\"") && x.contains("q8w2e4r5t6-3abcdefgh")),
                "peer = everything before the first dash");
        Wire wc = new Wire();
        SuiteChat c = chat("cccccccc03", "VJ-Link", wc);
        c.setOn(true, 3 * S);
        w.deliver(c, 3 * S);
        assertEquals(2, messages(c).size(), "and the newcomer takes both");
    }

    @Test
    void strictTypes() throws Exception {
        Wire w = new Wire();
        SuiteChat b = chat("bbbbbbbb02", "LJ Link", w);
        b.setOn(true, 0);
        String head = "{\"suite\":\"chat\",\"v\":1,\"t\":\"msg\",\"peer\":\"cccccccc03\",";
        b.receive(head + "\"id\":\"cccccccc03-1\",\"text\":\"a\",\"urgent\":1}", S);
        b.receive(head + "\"id\":\"cccccccc03-2\",\"text\":\"b\",\"urgent\":\"true\"}", S);
        assertTrue(b.alerts().isEmpty(), "urgent only as a real true");
        b.receive(head + "\"id\":\"cccccccc03-3\",\"text\":12345}", S);
        b.receive(head + "\"id\":\"cccccccc03-4\",\"text\":[\"x\"]}", S);
        b.receive("{\"suite\":\"chat\",\"v\":\"1\",\"t\":\"msg\",\"peer\":\"cccccccc03\",\"id\":\"cccccccc03-5\",\"text\":\"v\"}", S);
        assertEquals(2, messages(b).size(), "a text that is not a string counts as missing; v must be the number 1");
        b.receive(head + "\"id\":\"cccccccc03-6\",\"text\":\"c\",\"nick\":42}", S);
        assertEquals("", messages(b).get(2).get("nick"), "a nick that is not a string counts as missing");
    }

    @Test
    void unicodeSpacesAtTheEndsGo() {
        assertEquals("a\u00A0b", SuiteChat.text("\u3000\u00A0a\u00A0b\u2003 ", 50));
        assertEquals("", SuiteChat.text("\u2003\u3000", 50), "only spaces: empty, dropped");
    }

    @Test
    void chatPortCanBeMovedForTests() {
        assertEquals(SuiteChat.PORT, SuiteChat.port(), "the real room by default");
        System.setProperty("ljlink.chatPort", "47699");
        try {
            assertEquals(47699, SuiteChat.port());
            System.setProperty("ljlink.chatPort", "nonsense");
            assertEquals(SuiteChat.PORT, SuiteChat.port());
        } finally {
            System.clearProperty("ljlink.chatPort");
        }
    }

    @Test
    void offAndOnIsVisibleToTheBrowserAndMessagesCarryTheirId() throws Exception {
        Wire w = new Wire();
        SuiteChat b = chat("bbbbbbbb02", "LJ Link", w);
        b.setOn(true, 0);
        b.receive(msg("cccccccc03", 1, false), S);
        Object first = b.state(0).get("epoch");
        assertEquals("cccccccc03-1", messages(b).get(0).get("id"), "the browser lists a message once, by its id");
        b.setOn(false, 2 * S);
        b.setOn(true, 2 * S + 1);
        assertNotEquals(first, b.state(0).get("epoch"), "a tab that missed the off/on starts its list anew");
    }
}
