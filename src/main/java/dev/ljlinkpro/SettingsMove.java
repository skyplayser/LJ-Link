package dev.ljlinkpro;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * The one-time token for handing the browser settings from {@code localhost} over to {@code 127.0.0.1} (ADR-039, QS
 * round 3). Only LJ Link itself puts it into the address it opens on the first start after the update; the page on
 * 127.0.0.1 redeems it over a local route (ControlGuard: this computer, same origin, JSON). Without a valid token the
 * page imports nothing, so another web page cannot plant settings with {@code #import-settings}.
 */
final class SettingsMove {
    /** Set once the settings were handed over (or there was nothing to hand over). */
    static final String DONE_KEY = "browserSettingsMoved";
    static final long VALID_MS = 5 * 60_000;
    /** Starts that opened the move address; after {@link #MAX_ATTEMPTS} LJ Link stops trying. */
    static final String ATTEMPTS_KEY = "browserSettingsMoveAttempts";
    static final int MAX_ATTEMPTS = 2;

    /** Whether a start (with browser) opens the move address: not done yet and fewer than {@link #MAX_ATTEMPTS} tries. */
    static boolean tryMove(boolean done, int attempts) {
        return !done && attempts < MAX_ATTEMPTS;
    }

    private static final SecureRandom RANDOM = new SecureRandom();
    private static String token;
    private static long issuedAt;

    private SettingsMove() {}

    /** A new token (128 bit), valid for {@link #VALID_MS}; replaces an earlier one. */
    static synchronized String issue(long now) {
        byte[] b = new byte[16];
        RANDOM.nextBytes(b);
        token = HexFormat.of().formatHex(b);
        issuedAt = now;
        return token;
    }

    /** True exactly once for the current token, within its time. Any attempt with a wrong token does not use it up. */
    static synchronized boolean redeem(String candidate, long now) {
        if (token == null || candidate == null || now - issuedAt > VALID_MS || now < issuedAt) {
            return false;
        }
        boolean ok = MessageDigest.isEqual(token.getBytes(StandardCharsets.US_ASCII), candidate.getBytes(StandardCharsets.US_ASCII));
        if (ok) {
            token = null;
        }
        return ok;
    }

    /** A move is outstanding: a token was issued and not redeemed yet, and it has not expired. */
    static synchronized boolean pending(long now) {
        return token != null && now - issuedAt <= VALID_MS;
    }

    static boolean done() {
        return AppSettings.getBoolean(DONE_KEY, false);
    }

    static void markDone() {
        AppSettings.set(DONE_KEY, true);
    }

    /**
     * {@code POST /api/settings-move} with {@code {"token": "…"}}: answers {@code ok} (the token was valid, import now)
     * and {@code done} (nothing is or will be outstanding: the page may stop asking). Local only, like every guarded route.
     */
    static void install(io.javalin.Javalin app, java.util.function.BooleanSupplier done, Runnable markDone) {
        app.post("/api/settings-move", ctx -> {
            String candidate = "";
            try {
                candidate = Hub.JSON.readTree(ctx.body()).path("token").asText("");
            } catch (java.io.IOException | RuntimeException e) {
                // no JSON: no token
            }
            long now = System.currentTimeMillis();
            boolean ok = redeem(candidate, now);
            if (ok) {
                markDone.run();
            }
            ctx.header("Cache-Control", "no-store").json(java.util.Map.of("ok", ok, "done", done.getAsBoolean() && !pending(now)));
        });
    }

    /** For the tests. */
    static synchronized void reset() {
        token = null;
        issuedAt = 0;
    }
}
