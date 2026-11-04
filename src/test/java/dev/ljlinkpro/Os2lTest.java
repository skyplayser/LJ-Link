package dev.ljlinkpro;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Os2lTest {
    private static Deck deck(double bpm, int beats) {
        Hub hub = new Hub();
        Deck d = hub.scope("test", "pioneer", true, "Test").deck(1);
        d.hasTrack = true;
        d.playing = true;
        d.effectiveBpm = bpm;
        d.speed = 1.0;
        int[] times = new int[beats];
        byte[] bars = new byte[beats];
        for (int i = 0; i < beats; i++) {
            times[i] = (int) Math.round(i * 60_000.0 / bpm);
            bars[i] = (byte) (i % 4 + 1);
        }
        d.setBeats(times, bars);
        return d;
    }

    @Test
    void messagesAreTheOs2lJsonWithOneObjectPerLine() {
        assertEquals("{\"evt\":\"beat\",\"change\":true,\"pos\":42,\"bpm\":120.000}\n", Os2lClient.beatMessage(true, 42, 120, -1));
        assertEquals("{\"evt\":\"beat\",\"change\":false,\"pos\":3,\"bpm\":127.500,\"strength\":80.0}\n", Os2lClient.beatMessage(false, 3, 127.5, 80));
        assertEquals("{\"evt\":\"btn\",\"name\":\"drop\",\"state\":\"on\"}\n", Os2lClient.buttonMessage("drop", true));
    }

    @Test
    void aPlayingDeckGivesOneBeatPerGridBeatAndBarsStartAtMultiplesOfFour() {
        Deck d = deck(120, 64);
        Os2lBeats beats = new Os2lBeats();
        long t0 = System.nanoTime();
        List<Os2lBeats.Beat> got = new ArrayList<>();
        // The player reports its position at 10 ms steps, starting 100 ms into the first beat (too late to send it); the clock is simulated.
        for (long ms = 100; ms < 4100; ms += 10) {
            d.velocity = 1.0;
            d.positionMs = ms;
            d.positionArrivedNanos = t0 + ms * 1_000_000L;
            Os2lBeats.Beat b = beats.next(d, t0 + ms * 1_000_000L);
            if (b != null) {
                got.add(b);
            }
        }
        // beats at 500, 1000 ... 4000; beat 0 is skipped because it began 100 ms before the first sample
        assertEquals(8, got.size());
        assertEquals(1, got.get(0).pos());
        assertEquals(4, got.get(3).pos());
        assertEquals(0, got.get(3).pos() % 4);
        assertTrue(got.get(0).change(), "the first beat after a start tells the lights to find the phase");
        assertFalse(got.get(1).change());
        assertEquals(120.0, got.get(1).bpm(), 0.001);
    }

    @Test
    void aStandingDeckAndAJumpAreHandled() {
        Deck d = deck(120, 64);
        Os2lBeats beats = new Os2lBeats();
        long t0 = System.nanoTime();
        d.velocity = 0;
        d.positionMs = 1000;
        d.positionArrivedNanos = t0;
        assertNull(beats.next(d, t0), "no beats while the deck stands");
        // play from 1000, then jump to 20 000 (a cue)
        d.velocity = 1.0;
        int sent = 0;
        boolean changeAfterJump = false;
        for (long ms = 1000; ms < 3000; ms += 10) {
            d.positionMs = ms;
            d.positionArrivedNanos = t0 + ms * 1_000_000L;
            if (beats.next(d, t0 + ms * 1_000_000L) != null) {
                sent++;
            }
        }
        assertTrue(sent >= 3);
        for (long ms = 20_000; ms < 20_600; ms += 10) {
            d.positionMs = ms;
            d.positionArrivedNanos = t0 + ms * 1_000_000L;
            Os2lBeats.Beat b = beats.next(d, t0 + ms * 1_000_000L);
            if (b != null && b.change()) {
                changeAfterJump = true;
            }
        }
        assertTrue(changeAfterJump, "a jump is announced as a change");
    }

    @Test
    void aDeckWithoutPositionRunsFreeAtItsTempo() {
        Hub hub = new Hub();
        Deck d = hub.scope("serato", "serato", true, "Serato").deck(1);
        d.hasTrack = true;
        d.effectiveBpm = 120;
        Os2lBeats beats = new Os2lBeats();
        long t0 = System.nanoTime();
        int count = 0;
        for (long ms = 0; ms < 2000; ms += 5) {
            if (beats.next(d, t0 + ms * 1_000_000L) != null) {
                count++;
            }
        }
        assertEquals(4, count, "120 BPM is one beat every 500 ms");
    }

    @Test
    void theClientConnectsOnceSendsLinesAndConnectsAgainAfterABreak() throws Exception {
        List<String> lines = new CopyOnWriteArrayList<>();
        try (ServerSocket server = new ServerSocket(0)) {
            Thread receiver = new Thread(() -> {
                try {
                    for (int i = 0; i < 2; i++) {
                        try (Socket s = server.accept()) {
                            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                            lines.add(in.readLine());
                            if (i == 1) {
                                lines.add(in.readLine());
                            }
                        }
                    }
                } catch (Exception e) {
                    // test ends
                }
            });
            receiver.setDaemon(true);
            receiver.start();
            Os2lClient client = new Os2lClient();
            client.beat("127.0.0.1", server.getLocalPort(), true, 0, 128);
            assertTrue(client.connected());
            long end = System.currentTimeMillis() + 3000;
            while (lines.isEmpty() && System.currentTimeMillis() < end) {
                Thread.sleep(20);
            }
            assertEquals("{\"evt\":\"beat\",\"change\":true,\"pos\":0,\"bpm\":128.000}", lines.get(0));
            // the receiver closed the first connection: a later send fails or reconnects, and the one after reaches the second accept
            Thread.sleep(100);
            for (int i = 0; i < 5 && lines.size() < 3; i++) {
                try {
                    client.beat("127.0.0.1", server.getLocalPort(), false, i + 1, 128);
                } catch (java.io.IOException e) {
                    // the broken connection is reported once and dropped
                }
                Thread.sleep(150);
            }
            end = System.currentTimeMillis() + 3000;
            while (lines.size() < 2 && System.currentTimeMillis() < end) {
                Thread.sleep(20);
            }
            assertNotNull(lines.get(1));
            assertTrue(lines.get(1).contains("\"evt\":\"beat\""));
        }
    }
}
