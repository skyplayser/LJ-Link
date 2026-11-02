package dev.ljlinkpro;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Command line of a grandMA2 (console or onPC) over Telnet. The grandMA2 has no OSC input; its remote command line is
 * reached on TCP port 30000 once Telnet is switched on (Setup, Console, Global Settings, Telnet: Login Enabled), and every
 * connection has to log in as a user of the show first. The connection is kept open and made again when it breaks.
 */
final class Ma2Telnet {
    static final int PORT = 30000;

    private Socket socket;
    private OutputStream out;
    private String connectedTo;
    private volatile boolean closed = true;
    /** The last error the desk answered with (for example missing user rights), or null. */
    private volatile String deskError;

    String deskError() {
        return deskError;
    }

    /** Sends one command line, connecting and logging in first when needed. */
    synchronized void send(String host, int port, String user, String password, String command) throws IOException {
        String target = host + ":" + port + ":" + user + ":" + password;
        if (closed || !target.equals(connectedTo)) {
            connect(host, port, user, password);
            connectedTo = target;
        }
        try {
            write(command);
        } catch (IOException e) {
            close();
            throw e;
        }
    }

    private void connect(String host, int port, String user, String password) throws IOException {
        close();
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host, port), 3000);
        s.setTcpNoDelay(true);
        socket = s;
        out = s.getOutputStream();
        closed = false;
        // The greeting tells whether the remote command line is switched on at all.
        String greeting = readFor(s, 800);
        if (greeting.toLowerCase().contains("disabled")) {
            close();
            throw new IOException("Telnet is switched off on the desk (Setup, Console, Global Settings, Telnet: Login Enabled)");
        }
        if (user != null && !user.isBlank()) {
            write("Login \"" + user.replace("\"", "") + "\" \"" + (password == null ? "" : password.replace("\"", "")) + "\"");
            String answer = readFor(s, 800).toLowerCase();
            if (answer.contains("fail") || answer.contains("wrong") || answer.contains("invalid") || answer.contains("denied")) {
                close();
                throw new IOException("Login failed: check user and password (case sensitive)");
            }
        }
        // From now on the answers (prompts, echoes) are read and dropped so the desk's buffer never fills up.
        Thread reader = new Thread(() -> {
            try (InputStream in = s.getInputStream()) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) >= 0) {
                    // Shown in the log, so a command the desk rejects can be found (it answers with an error text).
                    String text = new String(buf, 0, n, StandardCharsets.ISO_8859_1).replaceAll("\u001B\\[[0-9;]*[A-Za-z]", "").trim();
                    if (!text.isEmpty()) {
                        System.out.println("grandMA2: " + text.replaceAll("\\s*[\r\n]+\\s*", " | "));
                        java.util.regex.Matcher m = java.util.regex.Pattern.compile("Error #\\d+:[^\r\n|]*").matcher(text);
                        if (m.find()) {
                            deskError = m.group().trim();
                        } else if (text.contains("Executing")) {
                            deskError = null;
                        }
                    }
                }
            } catch (IOException e) {
                // the connection ended
            }
            closed = true;
        }, "ma2-telnet-reader");
        reader.setDaemon(true);
        reader.start();
    }

    /** Collects what the desk sends within {@code ms}. */
    private static String readFor(Socket s, int ms) throws IOException {
        StringBuilder text = new StringBuilder();
        long end = System.currentTimeMillis() + ms;
        byte[] buf = new byte[4096];
        InputStream in = s.getInputStream();
        while (System.currentTimeMillis() < end) {
            s.setSoTimeout((int) Math.max(1, end - System.currentTimeMillis()));
            try {
                int n = in.read(buf);
                if (n < 0) {
                    break;
                }
                text.append(new String(buf, 0, n, StandardCharsets.ISO_8859_1));
            } catch (java.net.SocketTimeoutException e) {
                break;
            }
        }
        s.setSoTimeout(0);
        return text.toString();
    }

    private void write(String line) throws IOException {
        out.write((line + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    synchronized void close() {
        closed = true;
        connectedTo = null;
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                // already closed
            }
            socket = null;
        }
    }
}
