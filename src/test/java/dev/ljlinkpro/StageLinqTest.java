package dev.ljlinkpro;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

class StageLinqTest {
    private static final byte[] DEVICE_TOKEN = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};

    @Test
    void discoveryMessageSurvivesARoundTrip() {
        byte[] packet = StageLinqProtocol.discovery(new StageLinqProtocol.Discovery(
                DEVICE_TOKEN, "prime4plus", StageLinqProtocol.HOWDY, "JP13", "2.3.1", 51_111));
        StageLinqProtocol.Discovery d = StageLinqProtocol.parseDiscovery(packet, packet.length);
        assertNotNull(d);
        assertArrayEquals(DEVICE_TOKEN, d.token());
        assertEquals("prime4plus", d.source());
        assertEquals(StageLinqProtocol.HOWDY, d.action());
        assertEquals("JP13", d.softwareName());
        assertEquals("2.3.1", d.softwareVersion());
        assertEquals(51_111, d.port());
    }

    @Test
    void foreignPacketsAreIgnored() {
        byte[] junk = "hello world, this is not StageLinQ".getBytes();
        assertNull(StageLinqProtocol.parseDiscovery(junk, junk.length));
        assertNull(StageLinqProtocol.parseDiscovery(new byte[] {'a', 'i', 'r', 'D', 1}, 5));
    }

    @Test
    void stateFramesCarryTheNameAndTheJson() throws IOException {
        byte[] frame = StageLinqProtocol.stateEmit("/Engine/Deck1/Track/SongName", "{\"type\":8,\"string\":\"Test Song\"}");
        byte[] payload = StageLinqProtocol.readFrame(new DataInputStream(new java.io.ByteArrayInputStream(frame)));
        StageLinqProtocol.StateEmit e = StageLinqProtocol.parseStateFrame(payload);
        assertNotNull(e);
        assertEquals("/Engine/Deck1/Track/SongName", e.name());
        assertTrue(e.json().contains("Test Song"));
        // a subscription is a state frame of another type and must not be mistaken for a value
        byte[] sub = StageLinqProtocol.subscribe("/Engine/Deck1/Play", 0);
        assertNull(StageLinqProtocol.parseStateFrame(StageLinqProtocol.readFrame(
                new DataInputStream(new java.io.ByteArrayInputStream(sub)))));
    }

    @Test
    void beatFramesCarryBeatBpmAndSamplesPerDeck() throws IOException {
        StageLinqProtocol.Beat in = new StageLinqProtocol.Beat(99, new double[] {12.5, 3}, new double[] {400, 300},
                new double[] {128, 90}, new double[] {1_323_000, 44_100});
        byte[] payload = StageLinqProtocol.readFrame(
                new DataInputStream(new java.io.ByteArrayInputStream(StageLinqProtocol.beatFrame(in))));
        StageLinqProtocol.Beat out = StageLinqProtocol.parseBeatFrame(payload);
        assertNotNull(out);
        assertEquals(99, out.clock());
        assertEquals(2, out.beat().length);
        assertEquals(12.5, out.beat()[0]);
        assertEquals(90, out.bpm()[1]);
        assertEquals(1_323_000, out.samples()[0]);
    }

    @Test
    void valuesFromTheDeviceBecomeDeckState() throws Exception {
        Hub hub = new Hub();
        Hub.Scope scope = hub.scope("test", "denon", false, "Denon");
        StageLinqSource source = new StageLinqSource(scope, new byte[16], "Denon", null);
        source.applyState("/Engine/Deck2/Track/SongLoaded", Hub.JSON.readTree("{\"type\":1,\"state\":true}"));
        source.applyState("/Engine/Deck2/Track/SongName", Hub.JSON.readTree("{\"type\":8,\"string\":\"Night Drive\"}"));
        source.applyState("/Engine/Deck2/Track/TrackLength", Hub.JSON.readTree("{\"type\":10,\"value\":10826550}"));
        source.applyState("/Engine/Deck2/Track/CurrentBPM", Hub.JSON.readTree("{\"type\":0,\"value\":120.0}"));
        source.applyState("/Engine/Deck2/CurrentBPM", Hub.JSON.readTree("{\"type\":0,\"value\":126.0}"));
        source.applyState("/Mixer/CH2faderPosition", Hub.JSON.readTree("{\"type\":0,\"value\":0.75}"));
        Deck d = scope.deck(2);
        assertTrue(d.hasTrack);
        assertEquals("Night Drive", d.title);
        assertEquals(245_500, d.durationMs, 1, "10826550 samples at 44100 Hz");
        assertEquals(126.0, d.effectiveBpm);
        assertEquals(5.0, d.pitchPercent, 0.001);
        assertEquals(0.75, d.fader);
        assertTrue(d.onAir, "a channel with the fader up counts as on air");
    }

    @Test
    void loopsAreOnlyShownWhenThePositionsLookLikeMillisecondsInsideTheTrack() throws Exception {
        Hub hub = new Hub();
        Hub.Scope scope = hub.scope("test", "denon", false, "Denon");
        StageLinqSource source = new StageLinqSource(scope, new byte[16], "Denon", null);
        source.applyState("/Engine/Deck1/Track/SongLoaded", Hub.JSON.readTree("{\"type\":1,\"state\":true}"));
        source.applyState("/Engine/Deck1/Track/TrackLength", Hub.JSON.readTree("{\"type\":10,\"value\":11025000}")); // 250 s
        source.applyState("/Engine/Deck1/Track/CurrentLoopInPosition", Hub.JSON.readTree("{\"type\":14,\"value\":60000.0}"));
        source.applyState("/Engine/Deck1/Track/CurrentLoopOutPosition", Hub.JSON.readTree("{\"type\":14,\"value\":67500.0}"));
        source.applyState("/Engine/Deck1/Track/LoopEnableState", Hub.JSON.readTree("{\"type\":1,\"state\":false}"));
        assertNull(scope.deck(1).loop, "a loop that is switched off is not shown");
        source.applyState("/Engine/Deck1/Track/LoopEnableState", Hub.JSON.readTree("{\"type\":1,\"state\":true}"));
        assertArrayEquals(new long[] {60_000, 67_500}, scope.deck(1).loop);
        // positions that cannot be milliseconds of this track (here: samples) must not produce a bogus loop
        source.applyState("/Engine/Deck1/Track/CurrentLoopInPosition", Hub.JSON.readTree("{\"type\":14,\"value\":2646000.0}"));
        source.applyState("/Engine/Deck1/Track/CurrentLoopOutPosition", Hub.JSON.readTree("{\"type\":14,\"value\":2976750.0}"));
        assertNull(scope.deck(1).loop);
    }

    @Test
    void aFakeDenonDeviceFillsTheDecksOverTheNetwork() throws Exception {
        try (FakeDenon fake = new FakeDenon()) {
            Hub hub = new Hub();
        Hub.Scope scope = hub.scope("test", "denon", false, "Denon");
            StageLinqSource source = new StageLinqSource(scope, new byte[16], "Denon", null);
            try {
                source.connectDevice("127.0.0.1", fake.directoryPort(), DEVICE_TOKEN);
                waitFor(() -> scope.deck(1).hasTrack && scope.deck(1).positionMs > 0 && scope.deck(1).fader != null);
                Deck d = scope.deck(1);
                assertEquals("Test Song", d.title);
                assertEquals("Test Artist", d.artist);
                assertEquals(128.0, d.effectiveBpm);
                assertTrue(d.playing);
                assertEquals(0.8, d.fader);
                assertEquals(30_000, d.positionMs, 5, "30 s at 44.1 kHz");
                assertEquals("monitoring", hub.phase());
            } finally {
                source.stop();
            }
        }
    }

    private static void waitFor(BooleanSupplier condition) throws InterruptedException {
        long end = System.currentTimeMillis() + 5000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < end) {
            Thread.sleep(25);
        }
        assertTrue(condition.getAsBoolean(), "the decks were not filled in time");
    }

    /** Plays a Denon device: a directory service that offers StateMap and BeatInfo, and both services. */
    private static final class FakeDenon implements AutoCloseable {
        private final ServerSocket directory = new ServerSocket(0);
        private final ServerSocket stateMap = new ServerSocket(0);
        private final ServerSocket beatInfo = new ServerSocket(0);

        FakeDenon() throws IOException {
            daemon(this::serveDirectory);
            daemon(this::serveStateMap);
            daemon(this::serveBeatInfo);
        }

        int directoryPort() {
            return directory.getLocalPort();
        }

        private static void daemon(Runnable r) {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.start();
        }

        private void serveDirectory() {
            try (Socket s = directory.accept()) {
                DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
                DataOutputStream out = new DataOutputStream(s.getOutputStream());
                assertEquals(StageLinqProtocol.ID_SERVICES_REQUEST, in.readInt());
                in.readFully(new byte[16]);
                out.write(StageLinqProtocol.serviceAnnouncement(DEVICE_TOKEN, "StateMap", stateMap.getLocalPort()));
                out.write(StageLinqProtocol.serviceAnnouncement(DEVICE_TOKEN, "BeatInfo", beatInfo.getLocalPort()));
                out.write(StageLinqProtocol.reference(DEVICE_TOKEN, new byte[16], 0));
                out.flush();
                while (in.read() >= 0) {
                    // the client's heartbeat
                }
            } catch (IOException e) {
                // closed when the test ends
            }
        }

        private void serveStateMap() {
            try (Socket s = stateMap.accept()) {
                DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
                DataOutputStream out = new DataOutputStream(s.getOutputStream());
                assertEquals(StageLinqProtocol.ID_SERVICE_ANNOUNCEMENT, in.readInt());
                in.readFully(new byte[16]);
                assertEquals("StateMap", StageLinqProtocol.readString(in));
                in.readUnsignedShort();
                StageLinqProtocol.readFrame(in); // the first subscription
                out.write(StageLinqProtocol.stateEmit("/Engine/Deck1/Track/SongLoaded", "{\"type\":1,\"state\":true}"));
                out.write(StageLinqProtocol.stateEmit("/Engine/Deck1/Track/SongName", "{\"type\":8,\"string\":\"Test Song\"}"));
                out.write(StageLinqProtocol.stateEmit("/Engine/Deck1/Track/ArtistName", "{\"type\":8,\"string\":\"Test Artist\"}"));
                out.write(StageLinqProtocol.stateEmit("/Engine/Deck1/CurrentBPM", "{\"type\":0,\"value\":128.0}"));
                out.write(StageLinqProtocol.stateEmit("/Engine/Deck1/Play", "{\"type\":1,\"state\":true}"));
                out.write(StageLinqProtocol.stateEmit("/Mixer/CH1faderPosition", "{\"type\":0,\"value\":0.8}"));
                out.flush();
                while (in.read() >= 0) {
                    // further subscriptions
                }
            } catch (IOException e) {
                // closed when the test ends
            }
        }

        private void serveBeatInfo() {
            try (Socket s = beatInfo.accept()) {
                DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
                DataOutputStream out = new DataOutputStream(s.getOutputStream());
                assertEquals(StageLinqProtocol.ID_SERVICE_ANNOUNCEMENT, in.readInt());
                in.readFully(new byte[16]);
                assertEquals("BeatInfo", StageLinqProtocol.readString(in));
                in.readUnsignedShort();
                byte[] start = StageLinqProtocol.readFrame(in);
                assertEquals(4, start.length, "the start-stream request");
                out.write(StageLinqProtocol.beatFrame(new StageLinqProtocol.Beat(1, new double[] {64}, new double[] {512},
                        new double[] {128}, new double[] {44_100.0 * 30})));
                out.flush();
                while (in.read() >= 0) {
                    // nothing else is expected
                }
            } catch (IOException e) {
                // closed when the test ends
            }
        }

        @Override
        public void close() throws IOException {
            directory.close();
            stateMap.close();
            beatInfo.close();
        }
    }
}
