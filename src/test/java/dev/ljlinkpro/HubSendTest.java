package dev.ljlinkpro;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sending to browsers: one slow browser must not hold up the others, and nothing may pile up (ADR-024). */
class HubSendTest {
    /** Records what it gets. */
    static class FakeSink implements WsClient.Sink {
        final List<String> got = new CopyOnWriteArrayList<>();
        final AtomicBoolean disconnected = new AtomicBoolean();

        @Override
        public boolean isOpen() {
            return !disconnected.get();
        }

        @Override
        public void send(String text) throws Exception {
            got.add(text);
        }

        @Override
        public void disconnect() {
            disconnected.set(true);
        }

        long count(String fragment) {
            return got.stream().filter(s -> s.contains(fragment)).count();
        }
    }

    /** Blocks in its first send until it is disconnected, like a browser on a sleeping iPad. */
    static class HangingSink extends FakeSink {
        final CountDownLatch released = new CountDownLatch(1);
        final AtomicInteger attempts = new AtomicInteger();

        @Override
        public void send(String text) throws Exception {
            attempts.incrementAndGet();
            if (!released.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("never released");
            }
            throw new java.io.IOException("connection closed");
        }

        @Override
        public void disconnect() {
            super.disconnect();
            released.countDown();
        }
    }

    private static void waitFor(java.util.function.BooleanSupplier ok, long ms) throws InterruptedException {
        long end = System.nanoTime() + ms * 1_000_000;
        while (!ok.getAsBoolean() && System.nanoTime() < end) {
            Thread.sleep(5);
        }
    }

    @Test
    void aHangingBrowserDoesNotHoldUpTheOthers() throws Exception {
        Hub hub = new Hub(WsClient.STALL_NANOS, false); // no ticker: only this test sends positions
        Hub.Scope sc = hub.scope("demo", "demo", true, "Demo");
        Deck d = sc.deck(1);
        HangingSink slow = new HangingSink();
        FakeSink fast = new FakeSink();
        hub.addClient("slow", slow);
        hub.addClient("fast", fast);
        try {
            waitFor(() -> slow.attempts.get() > 0, 2000);
            assertEquals(1, slow.attempts.get(), "the slow browser hangs in its first send");
            for (int i = 0; i < 200; i++) {
                d.samplePosition(1000 + i * 20L);
                hub.positionTick();
            }
            waitFor(() -> fast.got.stream().anyMatch(s -> s.contains("\"p\":4980,")), 2000);
            assertTrue(fast.got.stream().anyMatch(s -> s.contains("\"p\":4980,")), "the other browser got the newest position at once");
        } finally {
            slow.disconnect();
        }
    }

    @Test
    void aWaitingBrowserOnlyKeepsTheNewestMessageOfEachKind() {
        List<Runnable> tasks = new ArrayList<>();
        FakeSink sink = new FakeSink();
        WsClient c = new WsClient(sink, tasks::add); // nothing runs until we say so: the browser is "slow"
        for (int i = 0; i < 500; i++) {
            c.offerDeck("pos", 1, "pos1-" + i);
            c.offerDeck("pos", 2, "pos2-" + i);
            c.offer("mixer", "mixer-" + i);
        }
        assertEquals(3, c.pendingCount(), "no backlog: one position per deck and one mixer");
        c.deckGone(2, "gone2");
        c.offerDeck("deck", 2, "late deck 2");
        assertEquals(3, c.pendingCount(), "deck 2: the position went, the removal stays, a late message is dropped");
        assertEquals(1, tasks.size(), "one sender task per browser");
        tasks.get(0).run();
        assertEquals(List.of("pos1-499", "mixer-499", "gone2"), sink.got);
    }

    @Test
    void aStalledBrowserIsDisconnectedAndNoLongerCounted() throws Exception {
        Hub hub = new Hub(200_000_000L, false); // no ticker: the test drops stalled browsers itself
        List<Integer> counts = new CopyOnWriteArrayList<>();
        hub.onClientCount(counts::add);
        HangingSink slow = new HangingSink();
        FakeSink fast = new FakeSink();
        hub.addClient("slow", slow);
        hub.addClient("fast", fast);
        try {
            waitFor(() -> slow.attempts.get() > 0, 2000);
            Thread.sleep(300);
            hub.dropStalledClients();
            waitFor(slow.disconnected::get, 2000); // the hub disconnects on a sender thread, never on its own
            assertTrue(slow.disconnected.get(), "the hanging browser was disconnected");
            assertEquals(1, hub.clientCount());
            assertFalse(fast.disconnected.get());
            assertEquals(List.of(1, 2, 1), counts, "the client count (sleep guard) follows");
            hub.removeClient("slow"); // Javalin's onClose afterwards: nothing more happens
            assertEquals(List.of(1, 2, 1), counts);
        } finally {
            slow.disconnect();
        }
    }

    @Test
    void anUnchangedPositionIsNotSentFiftyTimesASecond() {
        // No ticker, senders run at once on this thread, and the test sets the time: nothing depends on scheduling.
        Hub hub = new Hub(WsClient.STALL_NANOS, false, Runnable::run);
        Hub.Scope sc = hub.scope("demo", "demo", true, "Demo");
        Deck d = sc.deck(1);
        long start = System.nanoTime();
        d.samplePosition(5000, start);
        FakeSink sink = new FakeSink();
        hub.addClient("b", sink);
        assertEquals(1, sink.count("\"type\":\"pos\""), "the snapshot on connect");
        for (int i = 0; i < 20; i++) {
            long now = start + i * 20_000_000L;
            d.samplePosition(5000, now); // a paused deck: the same position every 20 ms
            hub.positionTick(now);
        }
        // The first tick sends (nothing was sent by the tick before); the rest is unchanged within the keepalive.
        assertEquals(2, sink.count("\"type\":\"pos\""));
        hub.positionTick(start + 19 * 20_000_000L + Hub.POSITION_KEEPALIVE_NANOS);
        assertEquals(3, sink.count("\"type\":\"pos\""), "the keepalive once a second");
        long later = start + 1_500_000_000L;
        d.samplePosition(5100, later);
        hub.positionTick(later);
        assertEquals(1, sink.count("\"p\":5100,"), "a new position goes out");
        String last = sink.got.stream().filter(s -> s.contains("\"p\":5100,")).findFirst().orElseThrow();
        assertTrue(last.matches(".*\"s\":\\d+,\"t\":-?\\d+,\"a\":\\d+}"), "sequence, server time and age: " + last);
    }

    @Test
    void noPositionIsKeptForARemovedDeck() {
        Hub hub = new Hub(WsClient.STALL_NANOS, false, Runnable::run);
        Hub.Scope sc = hub.scope("demo", "demo", true, "Demo");
        Deck d = sc.deck(1);
        d.samplePosition(1000);
        FakeSink sink = new FakeSink();
        hub.addClient("b", sink);
        hub.positionTick();
        sc.removeDeck(1);
        hub.positionTick();
        assertEquals(0, hub.sentPositionCount(), "nothing left for the removed deck");
        assertEquals(1, sink.count("\"type\":\"deckGone\""));
    }
}
