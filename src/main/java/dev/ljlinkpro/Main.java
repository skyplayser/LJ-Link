package dev.ljlinkpro;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.staticfiles.Location;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Starts the local web server, opens the browser and wires the HTTP/WebSocket API. */
public final class Main {
    private Main() {}

    /** Open the interface as an app window (no tabs, no address bar) when Chrome or Edge is installed. */
    private static volatile boolean appWindow = true;

    public static void main(String[] args) {
        // Pro DJ Link is IPv4 only. On some Windows systems the dual-stack sockets report senders as IPv4-mapped IPv6
        // addresses; beat-link then no longer recognises its own announcements and shuts itself down as an "invader".
        System.setProperty("java.net.preferIPv4Stack", "true");
        LogBuffer.install();
        disableAppNap();
        int port = 8080;
        boolean openBrowser = true;
        boolean useTray = true;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--no-browser" -> openBrowser = false;
                case "--no-tray" -> useTray = false;
                case "--no-app-window" -> appWindow = false;
                case "--privacy" -> Privacy.setForThisRun(true);
                case "--help", "-h" -> {
                    System.out.println("""
                            LJ Link
                              --port N       HTTP port (default 8080)
                              --no-browser   do not open the browser automatically
                              --no-tray      do not show the menu bar / system tray icon
                              --no-app-window  open in the normal browser instead of an app window
                              --privacy      hide track titles, artists, albums and covers (this run only)
                            """);
                    return;
                }
                default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }

        // A second start (e.g. clicking the app icon again) just opens the running instance.
        for (int p = port; p < port + 10; p++) {
            if (isAppAt(p)) {
                System.out.println("LJ Link is already running on port " + p);
                if (openBrowser) {
                    openInBrowser(localUrl(p));
                }
                return;
            }
        }

        // Engine library copies that a crashed run left in the temp folder (normally deleted right after loading).
        StageLinqLibrary.deleteLeftovers();

        Hub hub = new Hub();
        Session session = new Session(hub);
        LinkService link = new LinkService(hub);
        AbletonLinkPeer ableton = new AbletonLinkPeer(hub, link);
        SleepGuard sleepGuard = SleepGuard.forThisProcess();
        hub.onClientCount(sleepGuard::clients);

        // VJ-Link interface: its own stream for VJ programs, always on (see VjLink).
        VjLink vjLink = new VjLink(hub, Updater.currentVersion());
        vjLink.start();
        final int[] boundPort = {port, port};
        Started started;
        try {
            started = start(port, () -> createApp(hub, session, link, ableton, vjLink, boundPort), Main::portTaken);
        } catch (RuntimeException e) {
            // Without a console nobody would see why LJ Link did not start (issue #3): say it in a window, too.
            System.err.println(noFreePortText(port, port + 9, false) + " (" + e.getMessage() + ")");
            if (!java.awt.GraphicsEnvironment.isHeadless()) {
                String text = noFreePortText(port, port + 9, "de".equals(AppSettings.language()));
                try {
                    javax.swing.SwingUtilities.invokeAndWait(() -> javax.swing.JOptionPane.showMessageDialog(null,
                            text, "LJ Link", javax.swing.JOptionPane.ERROR_MESSAGE));
                } catch (Exception dialog) {
                    System.err.println("Message window unavailable: " + dialog);
                }
            }
            sleepGuard.close();
            System.exit(1);
            return;
        }
        Javalin app = started.app();
        int bound = started.port();
        boundPort[0] = bound;
        Bonjour.announce(bound, Updater.currentVersion());
        String url = localUrl(bound);
        if (bound != port) {
            System.out.println("Port " + port + " is in use by another program; LJ Link uses port " + bound + " instead.");
        }
        System.out.println("LJ Link is running: " + url);
        for (String u : NetworkCheck.lanUrls(bound)) {
            System.out.println("  On the network (e.g. tablet): " + u);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(session::stop));
        Runtime.getRuntime().addShutdownHook(new Thread(sleepGuard::close));
        if (useTray) {
            boolean installed = Tray.install(url, () -> openInBrowser(url), stars -> openInBrowser(url + QuitDialog.reportPath(stars)), () -> {
                session.stop();
                sleepGuard.close();
                app.stop();
                System.exit(0);
            });
            if (installed) {
                System.out.println("Menu bar / system tray icon: click it to open the web interface, switch privacy mode or quit.");
            }
        }
        // Only after an earlier version (its page saved a language) are there browser settings on localhost to move.
        if (!SettingsMove.done() && AppSettings.getString("language", null) == null) {
            SettingsMove.markDone();
        }
        // At most MAX_MOVE_ATTEMPTS starts try the move; if it never arrives (e.g. another program on [::1] gets
        // "localhost"), LJ Link gives up on it and opens 127.0.0.1 from then on (QS round 3).
        int attempts = AppSettings.getInt(SettingsMove.ATTEMPTS_KEY, 0);
        if (!SettingsMove.done() && !SettingsMove.tryMove(false, attempts)) {
            SettingsMove.markDone();
        }
        if (openBrowser) {
            if (SettingsMove.tryMove(SettingsMove.done(), attempts)) {
                AppSettings.set(SettingsMove.ATTEMPTS_KEY, attempts + 1);
                openInBrowser(moveUrl(bound, SettingsMove.issue(System.currentTimeMillis())));
            } else {
                openInBrowser(url);
            }
        }
    }

    /** The address for the log when the browser could not be opened: no hash (no move token), always 127.0.0.1. */
    static String forLog(String url) {
        int hash = url.indexOf('#');
        String plain = hash < 0 ? url : url.substring(0, hash);
        return plain.replaceFirst("^http://localhost:", "http://127.0.0.1:");
    }

    /**
     * The address LJ Link opens and shows: 127.0.0.1, not "localhost". Browsers try "localhost" as [::1] first, and with
     * IPv4 only (see main) LJ Link can neither bind nor check [::1]: another program there (e.g. a Node.js server on
     * "localhost") would get the browser (issue #3). 127.0.0.1 always reaches LJ Link.
     */
    static String localUrl(int port) {
        return "http://127.0.0.1:" + port;
    }

    /**
     * The first start with the new address opens localhost with {@code #move-settings=<token>}: browsers keep the
     * settings per address, so that page hands them over to 127.0.0.1, which redeems the one-time token
     * (settings-move.js, {@link SettingsMove}).
     */
    static String moveUrl(int port, String token) {
        return "http://localhost:" + port + "/#move-settings=" + token;
    }

    /** Shown when every port from {@code from} to {@code to} is in use (log in English, window in the menu language). */
    static String noFreePortText(int from, int to, boolean de) {
        return de
                ? "LJ Link kann nicht starten: Die Ports " + from + " bis " + to + " sind alle von anderen Programmen belegt.\n"
                + "Schließe eines davon oder starte LJ Link mit einem anderen Port, zum Beispiel: --port 18095"
                : "LJ Link cannot start: ports " + from + " to " + to + " are all in use by other programs.\n"
                + "Close one of them or start LJ Link with another port, for example: --port 18095";
    }

    /**
     * A new server with all routes. Built once per port that is tried: a Javalin instance cannot be started a second time
     * after a failed start (issue #3).
     *
     * @param boundPort [0] the port the server runs on, [1] the port that was asked for
     */
    private static Javalin createApp(Hub hub, Session session, LinkService link, AbletonLinkPeer ableton, VjLink vjLink,
            int[] boundPort) {
        Javalin app = Javalin.create(config -> {
            config.staticFiles.add("/web", Location.CLASSPATH);
            config.showJavalinBanner = false;
        });

        app.ws("/ws", ws -> {
            ws.onConnect(hub::addClient);
            ws.onClose(hub::removeClient);
            ws.onError(hub::removeClient);
        });
        app.ws("/vjlink", ws -> {
            ws.onConnect(vjLink::addClient);
            ws.onMessage(ctx -> vjLink.onMessage(ctx, ctx.message()));
            ws.onClose(vjLink::removeClient);
            ws.onError(vjLink::removeClient);
        });

        // Every /api/ route except the read-only ones for tablets is local: only the browser on this computer may use it,
        // never another web page (CSRF, DNS rebinding). See ControlGuard.
        ControlGuard.install(app);
        SettingsMove.install(app, SettingsMove::done, SettingsMove::markDone);

        app.get("/api/ping", ctx -> ctx.result("ljlinkpro"));
        app.get("/api/diagnostics", ctx -> {
            ctx.contentType("text/plain; charset=utf-8").result(Diagnostics.report(hub, session, link, ableton));
        });
        app.get("/api/version", ctx -> {
            String current = Updater.currentVersion();
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("current", current);
            m.put("autoCheck", Updater.autoCheck());
            if (Updater.autoCheck()) {
                // Asks GitHub at most once an hour, always from this computer (also when a tablet loads the page).
                Updater.Release r = Updater.latestCached();
                m.put("update", r != null && Updater.isNewer(r.version(), current) ? r.version() : null);
            }
            ctx.json(m);
        });
        // "Check for updates automatically" (default on). Off: GitHub is only asked from the program icon menu.
        app.post("/api/updates", ctx -> control(ctx, () -> {
            try {
                AppSettings.set(Updater.AUTO_CHECK_KEY, Hub.JSON.readTree(ctx.body()).path("on").asBoolean(true));
            } catch (java.io.IOException e) {
                ctx.status(400);
            }
        }));
        // "Show note when quitting": the QuitDialog of the program icon menu (default on). Local only, like all control
        // routes (ControlGuard); a tablet neither sees nor changes it.
        // "tray": whether the program icon is shown; without it there is no quit window and the switch is hidden.
        app.get("/api/quit-note", ctx -> ctx.json(Map.of("on", !QuitDialog.skip(), "tray", Tray.installed())));
        app.post("/api/quit-note", ctx -> control(ctx, () -> {
            try {
                QuitDialog.setSkip(!Hub.JSON.readTree(ctx.body()).path("on").asBoolean(true));
            } catch (java.io.IOException e) {
                ctx.status(400);
            }
        }));
        app.get("/api/info", ctx -> ctx.json(Map.of(
                "controller", ControlGuard.isController(ctx),
                "urls", NetworkCheck.lanUrls(boundPort[0]),
                "port", boundPort[0],
                "portWanted", boundPort[1],
                "adapters", NetworkCheck.adapters())));
        app.get("/api/deck/{n}/waveform", ctx -> {
            byte[] data = hub.waveform(Integer.parseInt(ctx.pathParam("n")));
            if (data == null) {
                ctx.status(404);
            } else {
                ctx.contentType("application/octet-stream").header("Cache-Control", "no-store").result(data);
            }
        });
        app.get("/api/deck/{n}/detail", ctx -> {
            byte[] data = hub.detail(Integer.parseInt(ctx.pathParam("n")));
            if (data == null) {
                ctx.status(404);
            } else {
                ctx.contentType("application/octet-stream").header("Cache-Control", "no-store").result(data);
            }
        });
        app.get("/api/deck/{n}/beats", ctx -> {
            var beats = hub.beats(Integer.parseInt(ctx.pathParam("n")));
            if (beats == null) {
                ctx.status(404);
            } else {
                ctx.header("Cache-Control", "no-store").json(beats);
            }
        });
        app.get("/api/deck/{n}/art", ctx -> {
            byte[] data = Privacy.enabled() ? null : hub.art(Integer.parseInt(ctx.pathParam("n")));
            if (data == null) {
                ctx.status(404);
            } else {
                byte[] img = CoverImage.limited(data);
                ctx.contentType(CoverImage.contentType(img)).header("Cache-Control", "no-store").result(img);
            }
        });
        app.get("/api/deck/{n}/info", ctx -> ctx.json(Privacy.filterInfo(session.info(Integer.parseInt(ctx.pathParam("n"))))));
        app.get("/api/link", ctx -> {
            ctx.json(Map.of("config", link.config(), "status", link.status(), "decks", link.candidates(), "sendDemo", link.sendDemo(), "manualBpm", link.manualBpm()));
        });
        app.post("/api/link", ctx -> control(ctx, () -> {
            try {
                link.setConfig(ctx.bodyAsClass(LinkService.Config.class));
            } catch (java.io.IOException e) {
                ctx.status(500);
            }
        }));
        app.get("/api/link/scan", ctx -> {
            ctx.json(DeskScanner.scan());
        });
        app.post("/api/link/source", ctx -> control(ctx, () -> {
            try {
                link.setSource(Hub.JSON.readTree(ctx.body()).path("deck").asText(LinkService.AUTO));
            } catch (java.io.IOException e) {
                ctx.status(500);
            }
        }));
        app.post("/api/link/test", ctx -> control(ctx, link::sendTest));
        app.post("/api/link/senddemo", ctx -> control(ctx, () -> {
            try {
                link.setSendDemo(Hub.JSON.readTree(ctx.body()).path("on").asBoolean(false));
            } catch (java.io.IOException e) {
                ctx.status(500);
            }
        }));
        app.post("/api/link/manual", ctx -> control(ctx, () -> {
            try {
                link.setManualBpm(Hub.JSON.readTree(ctx.body()).path("bpm").asDouble(120));
            } catch (java.io.IOException e) {
                ctx.status(500);
            }
        }));
        app.get("/api/ableton", ctx -> {
            ctx.json(ableton.status());
        });
        app.post("/api/ableton", ctx -> control(ctx, () -> {
            try {
                var body = Hub.JSON.readTree(ctx.body());
                ableton.configure(body.path("enabled").asBoolean(false), body.path("phase").asBoolean(true));
            } catch (java.io.IOException e) {
                ctx.status(400);
            }
        }));
        // Control endpoints are only available on the computer running LJ Link, not to tablets in the LAN (ControlGuard).
        app.post("/api/search/start", ctx -> control(ctx, session::startSearch));
        app.post("/api/denon/start", ctx -> control(ctx, session::startSearch));
        app.post("/api/demo/start", ctx -> control(ctx, session::startDemo));
        app.post("/api/stop", ctx -> control(ctx, session::stop));
        // The language of the program icon menu follows the language chosen in the interface on this computer.
        app.post("/api/language", ctx -> control(ctx, () -> {
            String lang = "de".equals(ctx.queryParam("lang")) ? "de" : "en";
            AppSettings.set("language", lang);
            Tray.setLanguage(lang);
        }));
        app.get("/api/check", ctx -> {
            ctx.json(NetworkCheck.check(ctx.queryParam("ip")));
        });

        return app;
    }

    private static boolean isAppAt(int port) {
        try {
            java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                    java.net.URI.create(localUrl(port) + "/api/ping").toURL().openConnection();
            c.setConnectTimeout(500);
            c.setReadTimeout(500);
            try (var in = c.getInputStream()) {
                return "ljlinkpro".equals(new String(in.readAllBytes()));
            }
        } catch (IOException e) {
            return false;
        }
    }

    /** Runs a control action; ControlGuard has already checked that the request comes from this computer. */
    private static void control(Context ctx, Runnable action) {
        action.run();
        if (ctx.status().getCode() < 300) {
            ctx.status(204);
        }
    }

    /**
     * macOS "App Nap" throttles the timers of an app without a visible window (LJ Link lives in the menu bar): the demo and
     * the updates to the browsers then ran only in bursts. The per-app default {@code NSAppSleepDisabled} turns it off; it
     * is read when AppKit starts, which is after this (the menu bar icon), so it should hold from this start on, at the
     * latest from the next. Only for the installed app (jpackage sets {@code jpackage.app-path}).
     */
    private static void disableAppNap() {
        if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac")
                || System.getProperty("jpackage.app-path") == null) {
            return;
        }
        try {
            Process read = new ProcessBuilder("/usr/bin/defaults", "read", "dev.ljlinkpro", "NSAppSleepDisabled")
                    .redirectErrorStream(true).start();
            String value = new String(read.getInputStream().readAllBytes()).trim();
            read.waitFor();
            if (!"1".equals(value)) {
                Process write = new ProcessBuilder("/usr/bin/defaults", "write", "dev.ljlinkpro", "NSAppSleepDisabled",
                        "-bool", "YES").redirectErrorStream(true).start();
                write.getInputStream().readAllBytes();
                write.waitFor();
                System.out.println("App Nap switched off for LJ Link (NSAppSleepDisabled).");
            }
        } catch (java.io.IOException e) {
            System.err.println("App Nap setting failed: " + e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** The server that runs and its port. */
    record Started(Javalin app, int port) {}

    /**
     * Starts a fresh server on the first free port from {@code port} to {@code port + 9}. Ports another program uses are
     * skipped before Jetty tries them ({@code taken}); if Jetty still cannot bind, the next port gets a new instance,
     * because a Javalin instance cannot be started twice (that made LJ Link quit when 8080 was in use, issue #3).
     */
    static Started start(int port, java.util.function.Supplier<Javalin> factory, java.util.function.IntPredicate taken) {
        RuntimeException last = null;
        for (int p = port; p < port + 10; p++) {
            if (taken.test(p)) {
                System.out.println("Port " + p + " is in use by another program.");
                continue;
            }
            Javalin app = factory.get();
            try {
                app.start(p);
                return new Started(app, p);
            } catch (RuntimeException e) {
                last = e;
                System.out.println("Port " + p + " could not be used: " + e.getMessage());
                try {
                    app.stop();
                } catch (RuntimeException ignored) {
                    // nothing was started
                }
            }
        }
        throw last != null ? last : new IllegalStateException("No free port from " + port + " to " + (port + 9));
    }

    /**
     * True when another program uses the port. Jetty binds all addresses with SO_REUSEADDR, which on Windows can succeed
     * although another program listens on the port (then the browser reaches that program). So: a plain bind to all
     * addresses with Java's defaults (exclusive on Windows) must work, and nothing may answer on 127.0.0.1 (a program that
     * bound only the loopback address).
     */
    static boolean portTaken(int port) {
        try (java.net.ServerSocket s = new java.net.ServerSocket()) {
            s.bind(new java.net.InetSocketAddress(port));
        } catch (IOException e) {
            return true;
        }
        try (java.net.Socket c = new java.net.Socket()) {
            c.connect(new java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), port), 300);
            return true;
        } catch (java.net.SocketTimeoutException e) {
            return true; // a listener that does not accept (its queue is full); a free port refuses at once
        } catch (IOException e) {
            return false;
        }
    }

    private static void openInBrowser(String url) {
        if (appWindow && openAppWindow(url)) {
            return;
        }
        String os = System.getProperty("os.name", "").toLowerCase();
        List<String> cmd;
        if (os.contains("mac")) {
            cmd = List.of("open", url);
        } else if (os.contains("win")) {
            cmd = List.of("rundll32", "url.dll,FileProtocolHandler", url);
        } else {
            cmd = List.of("xdg-open", url);
        }
        try {
            new ProcessBuilder(cmd).start();
        } catch (IOException e) {
            System.out.println("Please open in your browser: " + forLog(url));
        }
    }

    /**
     * Opens the page in a Chromium based browser's app mode: its own window without tabs or address bar, using a
     * separate profile so it never mixes with the user's browsing. Returns false if no such browser was found.
     */
    private static boolean openAppWindow(String url) {
        String os = System.getProperty("os.name", "").toLowerCase();
        String profile = Path.of(System.getProperty("user.home"), ".ljlinkpro", "window-profile").toString();
        List<String> flags = List.of("--app=" + url, "--user-data-dir=" + profile, "--no-first-run",
                "--no-default-browser-check", "--window-size=1400,900");
        try {
            if (os.contains("mac")) {
                for (String app : List.of("Google Chrome", "Microsoft Edge", "Brave Browser", "Chromium")) {
                    if (Files.exists(Path.of("/Applications/" + app + ".app"))) {
                        List<String> cmd = new java.util.ArrayList<>(List.of("open", "-na", app, "--args"));
                        cmd.addAll(flags);
                        new ProcessBuilder(cmd).start();
                        return true;
                    }
                }
            } else if (os.contains("win")) {
                String pf = System.getenv().getOrDefault("ProgramFiles", "C:\\Program Files");
                String pf86 = System.getenv().getOrDefault("ProgramFiles(x86)", "C:\\Program Files (x86)");
                for (String exe : List.of(pf86 + "\\Microsoft\\Edge\\Application\\msedge.exe",
                        pf + "\\Microsoft\\Edge\\Application\\msedge.exe",
                        pf + "\\Google\\Chrome\\Application\\chrome.exe",
                        pf86 + "\\Google\\Chrome\\Application\\chrome.exe")) {
                    if (Files.exists(Path.of(exe))) {
                        List<String> cmd = new java.util.ArrayList<>(List.of(exe));
                        cmd.addAll(flags);
                        new ProcessBuilder(cmd).start();
                        return true;
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("App window unavailable: " + e);
        }
        return false;
    }
}
