package dev.ljlinkpro;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

/**
 * Decides who may use the local API routes (settings, light desk link, diagnostics, network check). They are only for
 * the browser on the computer that runs LJ Link, never for tablets in the LAN and never for other web pages:
 * <ul>
 *   <li>the request must come from this computer (loopback address),</li>
 *   <li>the {@code Host} header must be {@code localhost}, {@code 127.0.0.1} or {@code [::1]}, with this server's port if
 *       a port is given. A web page that points its own domain at 127.0.0.1 (DNS rebinding) sends its domain here.</li>
 *   <li>an {@code Origin} header, when present, must be exactly {@code http://} plus that host. Other web pages that send
 *       requests to LJ Link (CSRF) always send their own origin.</li>
 *   <li>a {@code Sec-Fetch-Site} header, when present, must be {@code same-origin} or {@code none}; this also covers
 *       GETs that another page starts with {@code <img>} or {@code <script>} (no Origin header).</li>
 *   <li>a POST must say {@code Content-Type: application/json}. A foreign page can only send that after a CORS
 *       preflight, which LJ Link never answers.</li>
 * </ul>
 * Read-only routes for tablets and VJ programs ({@code /ws}, {@code /vjlink}, {@code /api/deck/*}, {@code /api/info},
 * {@code /api/version}, {@code /api/ping}) stay open; everything else under {@code /api/} is local (deny by default, so a
 * new route is protected unless it is added to {@link #isPublic}).
 */
final class ControlGuard {
    private ControlGuard() {}

    enum Verdict {
        OK(200),
        FORBIDDEN(403),
        UNSUPPORTED_MEDIA_TYPE(415);

        final int status;

        Verdict(int status) {
            this.status = status;
        }
    }

    private static final Set<String> LOOPBACK_NAMES = Set.of("localhost", "127.0.0.1", "[::1]");

    /** Routes that tablets in the LAN and VJ programs may use. Everything else under /api/ is local. */
    static boolean isPublic(String path) {
        return path.equals("/api/ping") || path.equals("/api/version") || path.equals("/api/info")
                || path.startsWith("/api/deck/");
    }

    /** True for the routes this guard protects. */
    static boolean isGuarded(String path) {
        return path.startsWith("/api/") && !isPublic(path);
    }

    /**
     * Checks one request to a local route.
     *
     * @param method      HTTP method, e.g. "GET"
     * @param remoteAddr  address of the client
     * @param host        the {@code Host} header (may be null)
     * @param origin      the {@code Origin} header (null when the browser sent none)
     * @param contentType the {@code Content-Type} header (may be null)
     * @param port        the port this server listens on
     */
    static Verdict check(String method, String remoteAddr, String host, String origin, String contentType, int port) {
        return check(method, remoteAddr, host, origin, null, contentType, port);
    }

    /**
     * Like {@link #check(String, String, String, String, String, int)} with the {@code Sec-Fetch-Site} header: when a
     * browser sends it, it must be {@code same-origin} (LJ Link's own page) or {@code none} (typed into the address bar,
     * bookmark). This also stops GETs that another page starts without an Origin header, e.g. with {@code <img src>}
     * ({@code /api/check}, {@code /api/link/scan}); {@code same-site} (another port on localhost) is refused too.
     */
    static Verdict check(String method, String remoteAddr, String host, String origin, String secFetchSite,
                         String contentType, int port) {
        if (!isLoopback(remoteAddr) || !isLocalHost(host, port)) {
            return Verdict.FORBIDDEN;
        }
        if (origin != null && !origin.equalsIgnoreCase("http://" + host)) {
            return Verdict.FORBIDDEN;
        }
        if (secFetchSite != null && !secFetchSite.trim().equalsIgnoreCase("same-origin")
                && !secFetchSite.trim().equalsIgnoreCase("none")) {
            return Verdict.FORBIDDEN;
        }
        if ("POST".equalsIgnoreCase(method) && !isJson(contentType)) {
            return Verdict.UNSUPPORTED_MEDIA_TYPE;
        }
        return Verdict.OK;
    }

    /** Like {@link #check} for a GET: whether this browser is the one on the LJ Link computer (shows the controls). */
    static boolean isController(String remoteAddr, String host, String origin, int port) {
        return check("GET", remoteAddr, host, origin, null, null, port) == Verdict.OK;
    }

    /**
     * Installs the guard in front of every local route of {@code app}. It runs before the route and answers 403 (wrong
     * client, host or origin) or 415 (POST without JSON content type) instead of it.
     */
    static void install(io.javalin.Javalin app) {
        app.before(ctx -> {
            if (!isGuarded(ctx.path())) {
                return;
            }
            Verdict v = check(ctx.method().name(), ctx.req().getRemoteAddr(), ctx.header("Host"), ctx.header("Origin"),
                    ctx.header("Sec-Fetch-Site"), ctx.header("Content-Type"), ctx.req().getLocalPort());
            if (v != Verdict.OK) {
                ctx.status(v.status).result("");
                ctx.skipRemainingHandlers();
            }
        });
    }

    /** {@link #isController} for a request. */
    static boolean isController(io.javalin.http.Context ctx) {
        return isController(ctx.req().getRemoteAddr(), ctx.header("Host"), ctx.header("Origin"), ctx.req().getLocalPort());
    }

    static boolean isLoopback(String remoteAddr) {
        if (remoteAddr == null || remoteAddr.isBlank()) {
            return false;
        }
        try {
            return InetAddress.getByName(remoteAddr.replaceAll("^\\[|\\]$", "")).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    /** {@code localhost}, {@code 127.0.0.1} or {@code [::1]}, optionally followed by {@code :port} of this server. */
    static boolean isLocalHost(String host, int port) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String h = host.trim().toLowerCase(Locale.ROOT);
        String name = h;
        int colon = h.lastIndexOf(':');
        if (colon > h.lastIndexOf(']')) {
            name = h.substring(0, colon);
            if (!h.substring(colon + 1).equals(Integer.toString(port))) {
                return false;
            }
        }
        return LOOPBACK_NAMES.contains(name);
    }

    static boolean isJson(String contentType) {
        if (contentType == null) {
            return false;
        }
        String type = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return type.equals("application/json");
    }
}
