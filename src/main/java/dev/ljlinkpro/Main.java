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
                    openInBrowser("http://localhost:" + p);
                }
                return;
            }
        }

        Hub hub = new Hub();
        Session session = new Session(hub);
        LinkService link = new LinkService(hub);
        AbletonLinkPeer ableton = new AbletonLinkPeer(hub, link);

        Javalin app = Javalin.create(config -> {
            config.staticFiles.add("/web", Location.CLASSPATH);
            config.showJavalinBanner = false;
        });

        app.ws("/ws", ws -> {
            ws.onConnect(hub::addClient);
            ws.onClose(hub::removeClient);
            ws.onError(hub::removeClient);
        });

        final int[] boundPort = {port};
        app.get("/api/ping", ctx -> ctx.result("ljlinkpro"));
        app.get("/api/diagnostics", ctx -> {
            if (!isLocal(ctx)) {
                ctx.status(403);
                return;
            }
            ctx.contentType("text/plain; charset=utf-8").result(Diagnostics.report(hub, session, link, ableton));
        });
        app.get("/api/version", ctx -> {
            Updater.Release r = Updater.latestCached();
            String current = Updater.currentVersion();
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("current", current);
            m.put("update", r != null && Updater.isNewer(r.version(), current) ? r.version() : null);
            ctx.json(m);
        });
        app.get("/api/info", ctx -> ctx.json(Map.of(
                "controller", isLocal(ctx),
                "urls", NetworkCheck.lanUrls(boundPort[0]),
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
                ctx.header("Cache-Control", "no-store").result(data);
            }
        });
        app.get("/api/deck/{n}/info", ctx -> ctx.json(Privacy.filterInfo(session.info(Integer.parseInt(ctx.pathParam("n"))))));
        app.get("/api/link", ctx -> {
            if (!isLocal(ctx)) {
                ctx.status(403);
                return;
            }
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
            if (!isLocal(ctx)) {
                ctx.status(403);
                return;
            }
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
            if (!isLocal(ctx)) {
                ctx.status(403);
                return;
            }
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
        // Control endpoints are only available on the computer running LJ Link, not to tablets in the LAN.
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
            if (!isLocal(ctx)) {
                ctx.status(403);
                return;
            }
            ctx.json(NetworkCheck.check(ctx.queryParam("ip")));
        });

        int bound = start(app, port);
        boundPort[0] = bound;
        String url = "http://localhost:" + bound;
        System.out.println("LJ Link is running: " + url);
        for (String u : NetworkCheck.lanUrls(bound)) {
            System.out.println("  On the network (e.g. tablet): " + u);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(session::stop));
        if (useTray) {
            boolean installed = Tray.install(url, () -> openInBrowser(url), () -> openInBrowser(url + "/#report"), () -> {
                session.stop();
                app.stop();
                System.exit(0);
            });
            if (installed) {
                System.out.println("Menu bar / system tray icon: click it to open the web interface, switch privacy mode or quit.");
            }
        }
        if (openBrowser) {
            openInBrowser(url);
        }
    }

    private static boolean isAppAt(int port) {
        try {
            java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                    java.net.URI.create("http://localhost:" + port + "/api/ping").toURL().openConnection();
            c.setConnectTimeout(500);
            c.setReadTimeout(500);
            try (var in = c.getInputStream()) {
                return "ljlinkpro".equals(new String(in.readAllBytes()));
            }
        } catch (IOException e) {
            return false;
        }
    }

    private static void control(Context ctx, Runnable action) {
        if (!isLocal(ctx)) {
            ctx.status(403);
            return;
        }
        action.run();
        ctx.status(204);
    }

    private static boolean isLocal(Context ctx) {
        try {
            String addr = ctx.req().getRemoteAddr().replaceAll("^\\[|\\]$", "");
            return java.net.InetAddress.getByName(addr).isLoopbackAddress();
        } catch (java.net.UnknownHostException e) {
            return false;
        }
    }

    /** Binds the first free port starting at {@code port}. */
    private static int start(Javalin app, int port) {
        for (int p = port; p < port + 10; p++) {
            try {
                app.start(p);
                return p;
            } catch (RuntimeException e) {
                if (p == port + 9) {
                    throw e;
                }
            }
        }
        throw new IllegalStateException("No free port");
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
            System.out.println("Please open in your browser: " + url);
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
