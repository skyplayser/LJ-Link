package dev.ljlinkpro;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Development aid, not part of the product: pretends to be a Denon device on this computer so the Denon support can
 * be tried without hardware. Two decks, one playing, a moving fader.
 * Run: {@code java -cp build/classes/java/main:build/classes/java/test dev.ljlinkpro.FakeDenonMain}
 */
public final class FakeDenonMain {
    private static final byte[] TOKEN = {9, 8, 7, 6, 5, 4, 3, 2, 1, 0, 1, 2, 3, 4, 5, 6};
    private static final long START = System.nanoTime();

    public static void main(String[] args) throws Exception {
        ServerSocket directory = new ServerSocket(0);
        ServerSocket stateMap = new ServerSocket(0);
        ServerSocket beatInfo = new ServerSocket(0);
        daemon(() -> acceptAll(directory, s -> directory(s, stateMap.getLocalPort(), beatInfo.getLocalPort())));
        daemon(() -> acceptAll(stateMap, FakeDenonMain::stateMap));
        daemon(() -> acceptAll(beatInfo, FakeDenonMain::beatInfo));
        System.out.println("Fake Denon device announcing; directory port " + directory.getLocalPort());

        byte[] howdy = StageLinqProtocol.discovery(new StageLinqProtocol.Discovery(
                TOKEN, "fake-prime4plus", StageLinqProtocol.HOWDY, "Prime 4+ (fake)", "3.0.0", directory.getLocalPort()));
        try (DatagramSocket udp = new DatagramSocket()) {
            while (true) {
                udp.send(new DatagramPacket(howdy, howdy.length, InetAddress.getLoopbackAddress(), StageLinqProtocol.DISCOVERY_PORT));
                Thread.sleep(1000);
            }
        }
    }

    private interface Handler {
        void handle(Socket s) throws Exception;
    }

    private static void acceptAll(ServerSocket server, Handler h) {
        while (!server.isClosed()) {
            try {
                Socket s = server.accept();
                daemon(() -> {
                    try (s) {
                        h.handle(s);
                    } catch (Exception e) {
                        // the client went away
                    }
                });
            } catch (IOException e) {
                return;
            }
        }
    }

    private static void directory(Socket s, int statePort, int beatPort) throws Exception {
        DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
        DataOutputStream out = new DataOutputStream(s.getOutputStream());
        in.readInt();
        in.readFully(new byte[16]);
        out.write(StageLinqProtocol.serviceAnnouncement(TOKEN, "StateMap", statePort));
        out.write(StageLinqProtocol.serviceAnnouncement(TOKEN, "BeatInfo", beatPort));
        out.write(StageLinqProtocol.reference(TOKEN, new byte[16], 0));
        out.flush();
        daemon(() -> {
            try {
                while (true) {
                    out.write(StageLinqProtocol.reference(TOKEN, new byte[16], 0));
                    out.flush();
                    Thread.sleep(250);
                }
            } catch (Exception e) {
                // closed
            }
        });
        while (in.read() >= 0) {
            // client heartbeat
        }
    }

    private static void stateMap(Socket s) throws Exception {
        DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
        DataOutputStream out = new DataOutputStream(s.getOutputStream());
        in.readInt();
        in.readFully(new byte[16]);
        StageLinqProtocol.readString(in);
        in.readUnsignedShort();
        StageLinqProtocol.readFrame(in);
        String[][] decks = {{"1", "Fake Song One", "Fake Artist A", "126.0", "true"}, {"2", "Fake Song Two", "Fake Artist B", "128.0", "false"}};
        for (String[] d : decks) {
            String p = "/Engine/Deck" + d[0];
            out.write(StageLinqProtocol.stateEmit(p + "/Track/SongLoaded", "{\"type\":1,\"state\":true}"));
            out.write(StageLinqProtocol.stateEmit(p + "/Track/SongName", "{\"type\":8,\"string\":\"" + d[1] + "\"}"));
            out.write(StageLinqProtocol.stateEmit(p + "/Track/ArtistName", "{\"type\":8,\"string\":\"" + d[2] + "\"}"));
            out.write(StageLinqProtocol.stateEmit(p + "/Track/TrackLength", "{\"type\":0,\"value\":10584000.0}"));
            out.write(StageLinqProtocol.stateEmit(p + "/Track/CurrentBPM", "{\"type\":0,\"value\":124.0}"));
            out.write(StageLinqProtocol.stateEmit(p + "/CurrentBPM", "{\"type\":0,\"value\":" + d[3] + "}"));
            out.write(StageLinqProtocol.stateEmit(p + "/Play", "{\"type\":1,\"state\":" + d[4] + "}"));
            out.write(StageLinqProtocol.stateEmit(p + "/DeckIsMaster", "{\"type\":1,\"state\":" + d[4] + "}"));
        }
        out.flush();
        while (true) {
            double t = (System.nanoTime() - START) / 1e9;
            out.write(StageLinqProtocol.stateEmit("/Mixer/CH1faderPosition", "{\"type\":0,\"value\":" + (0.55 + 0.4 * Math.sin(t / 2)) + "}"));
            out.write(StageLinqProtocol.stateEmit("/Mixer/CH2faderPosition", "{\"type\":0,\"value\":0.0}"));
            out.flush();
            Thread.sleep(100);
        }
    }

    private static void beatInfo(Socket s) throws Exception {
        DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
        DataOutputStream out = new DataOutputStream(s.getOutputStream());
        in.readInt();
        in.readFully(new byte[16]);
        StageLinqProtocol.readString(in);
        in.readUnsignedShort();
        StageLinqProtocol.readFrame(in);
        while (true) {
            double t = (System.nanoTime() - START) / 1e9;
            double samples1 = 44_100.0 * (30 + t);
            out.write(StageLinqProtocol.beatFrame(new StageLinqProtocol.Beat((long) (t * 1e9), new double[] {(30 + t) * 2.1, 40},
                    new double[] {500, 500}, new double[] {126, 128}, new double[] {samples1, 44_100.0 * 75})));
            out.flush();
            Thread.sleep(33);
        }
    }

    private static void daemon(Runnable r) {
        Thread t = new Thread(r);
        t.setDaemon(true);
        t.start();
    }
}
