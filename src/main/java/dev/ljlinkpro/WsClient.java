package dev.ljlinkpro;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * One browser connection with its own outgoing queue, drained by its own sender task (ADR-024).
 * <p>
 * Sending to a WebSocket blocks until the data is written (Javalin's {@code WsContext.send} uses Jetty's blocking
 * {@code sendString}). So a slow or sleeping browser must only ever hold up its own sender, never the hub or the other
 * browsers. Messages are keyed: a new message with the key of one still waiting replaces it (the newest state wins and
 * goes to the end of the queue), so the queue can never build up a backlog of old positions. Only messages without a
 * key (one-off events) are all kept; the queue is bounded anyway.
 */
final class WsClient {
    /** Where the text goes; for a browser this is the Javalin {@code WsContext}, in tests a fake. */
    interface Sink {
        boolean isOpen();

        /** Sends one text message; may block while the receiver is slow. */
        void send(String text) throws Exception;

        /** Drops the connection at once, without a close handshake (that would wait behind a blocked send). */
        void disconnect();
    }

    /** A send that blocks this long means the browser is gone or asleep: it is disconnected and reconnects by itself. */
    static final long STALL_NANOS = 5_000_000_000L;
    /** More waiting messages than this can only mean a stuck receiver (normal is a few dozen at most). */
    static final int MAX_PENDING = 1000;

    private final Sink sink;
    private final Executor executor;
    private final LinkedHashMap<String, String> pending = new LinkedHashMap<>();
    /** Decks this browser was told are gone; late messages for them are dropped (deck numbers are never reused). */
    private final Set<Integer> goneDecks = new HashSet<>();
    private long unique;
    private boolean draining;
    private boolean closed;
    private volatile boolean sending;
    private volatile long sendingSince;
    private volatile long sent;

    WsClient(Sink sink, Executor executor) {
        this.sink = sink;
        this.executor = executor;
    }

    /** Queues a message; a waiting message with the same key is replaced by this newer one. */
    void offer(String key, String text) {
        synchronized (this) {
            if (closed) {
                return;
            }
            pending.remove(key); // re-inserted at the end: the newest value at its newest place in the order
            pending.put(key, text);
        }
        kick();
    }

    /** Queues a message for one deck (key {@code kind:deck}); dropped if this browser already heard the deck is gone. */
    void offerDeck(String kind, int deck, String text) {
        synchronized (this) {
            if (closed || goneDecks.contains(deck)) {
                return;
            }
            String key = kind + ":" + deck;
            pending.remove(key);
            pending.put(key, text);
        }
        kick();
    }

    /** Queues the removal of a deck: whatever still waits for that deck is dropped, the removal itself is never replaced. */
    void deckGone(int deck, String text) {
        synchronized (this) {
            if (closed) {
                return;
            }
            goneDecks.add(deck);
            String suffix = ":" + deck;
            pending.keySet().removeIf(k -> k.endsWith(suffix) && !k.startsWith("#"));
            pending.put("#" + (unique++), text);
        }
        kick();
    }

    private void kick() {
        boolean start;
        synchronized (this) {
            start = !draining && !closed && !pending.isEmpty();
            if (start) {
                draining = true;
            }
        }
        if (start) {
            try {
                executor.execute(this::drain);
            } catch (RuntimeException e) {
                synchronized (this) {
                    draining = false;
                }
            }
        }
    }

    private void drain() {
        while (true) {
            String text;
            synchronized (this) {
                if (closed || pending.isEmpty()) {
                    draining = false;
                    return;
                }
                Iterator<Map.Entry<String, String>> it = pending.entrySet().iterator();
                text = it.next().getValue();
                it.remove();
                sendingSince = System.nanoTime();
                sending = true;
            }
            try {
                if (!sink.isOpen()) {
                    close();
                    continue;
                }
                sink.send(text);
                sent++;
            } catch (Exception e) {
                // A broken connection: drop it; the browser reconnects by itself.
                close();
                disconnectQuietly();
            } finally {
                sending = false;
            }
        }
    }

    /** True when the current send has blocked for longer than {@code limitNanos}, or far too much is waiting. */
    boolean stalled(long nowNanos, long limitNanos) {
        if (sending && nowNanos - sendingSince > limitNanos) {
            return true;
        }
        synchronized (this) {
            return pending.size() > MAX_PENDING;
        }
    }

    /** Stops sending; waiting messages are dropped. */
    void close() {
        synchronized (this) {
            closed = true;
            pending.clear();
        }
    }

    /** Closes and drops the connection (used for a stalled browser). */
    void kill() {
        close();
        try {
            executor.execute(this::disconnectQuietly); // never on the caller's thread: the hub must not wait for it
        } catch (RuntimeException e) {
            disconnectQuietly();
        }
    }

    private void disconnectQuietly() {
        try {
            sink.disconnect();
        } catch (RuntimeException e) {
            // Already gone.
        }
    }

    synchronized boolean isClosed() {
        return closed;
    }

    synchronized int pendingCount() {
        return pending.size();
    }

    long sentCount() {
        return sent;
    }
}
