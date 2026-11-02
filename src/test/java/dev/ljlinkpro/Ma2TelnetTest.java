package dev.ljlinkpro;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Ma2TelnetTest {
    /** A pretend grandMA2: greets like the desk and records every line it receives. */
    @Test
    void logsInOnceAndSendsTheCommandLines() throws Exception {
        List<String> lines = new CopyOnWriteArrayList<>();
        try (ServerSocket server = new ServerSocket(0)) {
            Thread desk = new Thread(() -> {
                try (Socket s = server.accept()) {
                    s.getOutputStream().write("Please login !\r\n".getBytes(StandardCharsets.UTF_8));
                    BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                    String line;
                    while ((line = in.readLine()) != null) {
                        lines.add(line);
                    }
                } catch (Exception e) {
                    // test ends
                }
            });
            desk.setDaemon(true);
            desk.start();
            Ma2Telnet telnet = new Ma2Telnet();
            telnet.send("127.0.0.1", server.getLocalPort(), "light", "pass", "SpecialMaster 3.1 At 128.0");
            telnet.send("127.0.0.1", server.getLocalPort(), "light", "pass", "SpecialMaster 3.1 At 128.5");
            long end = System.currentTimeMillis() + 3000;
            while (lines.size() < 3 && System.currentTimeMillis() < end) {
                Thread.sleep(20);
            }
            telnet.close();
        }
        assertEquals(List.of("Login \"light\" \"pass\"", "SpecialMaster 3.1 At 128.0", "SpecialMaster 3.1 At 128.5"), lines);
    }

    @Test
    void saysSoWhenTheDeskHasTelnetSwitchedOff() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            Thread desk = new Thread(() -> {
                try (Socket s = server.accept()) {
                    s.getOutputStream().write("\nRemote commandline disabled\n\r".getBytes(StandardCharsets.UTF_8));
                    Thread.sleep(1500);
                } catch (Exception e) {
                    // test ends
                }
            });
            desk.setDaemon(true);
            desk.start();
            java.io.IOException e = org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class,
                    () -> new Ma2Telnet().send("127.0.0.1", server.getLocalPort(), "a", "b", "SpecialMaster 3.1 At 120"));
            org.junit.jupiter.api.Assertions.assertTrue(e.getMessage().contains("Telnet is switched off"));
        }
    }
}
