package dev.ljlinkpro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Display settings of the web page (zoom, view, theme, clock, clock size, deck info width …), kept on the LJ Link
 * computer under {@code "ui"} in {@code settings.json} (ADR-045). The browser's own storage depends on the address
 * (127.0.0.1, localhost, another port or IP) and can be cleared; this copy survives both.
 * <ul>
 *   <li>{@code GET /api/ui-settings} is open to tablets ({@link ControlGuard#isPublic(String, String)}): it holds only
 *       display choices, nothing personal. It also says whether the asking browser has control.</li>
 *   <li>{@code POST /api/ui-settings} only from the browser on this computer ({@link ControlGuard}).</li>
 * </ul>
 * Only known keys with valid values are stored; anything else is dropped.
 */
final class UiSettings {
    static final String KEY = "ui";
    static final String PATH = "/api/ui-settings";

    /** The defaults of the web page (app.js DEFAULTS); the server stores only what was chosen. */
    static final Map<String, Object> DEFAULTS = Map.of("zoom", 12, "mode", "rgb", "theme", "night", "view", "both",
            "keepAwake", true, "intro", true, "clock", true, "clockSize", 28, "infoMode", "compact", "infoWidth", 0);

    private UiSettings() {}

    /** Only the known keys with valid values. */
    static ObjectNode clean(JsonNode in) {
        ObjectNode out = Hub.JSON.createObjectNode();
        if (in == null || !in.isObject()) {
            return out;
        }
        intIn(in, out, "zoom", 4, 40);
        oneOf(in, out, "mode", List.of("rgb", "blue"));
        oneOf(in, out, "theme", List.of("night", "day"));
        oneOf(in, out, "view", List.of("both", "detail", "overview"));
        for (String b : List.of("keepAwake", "intro", "clock")) {
            if (in.path(b).isBoolean()) {
                out.put(b, in.get(b).asBoolean());
            }
        }
        intIn(in, out, "clockSize", 20, 160);
        oneOf(in, out, "infoMode", List.of("compact", "wide"));
        // 0 = the default width of the chosen mode; otherwise pixels (the page keeps it between 400 px and 60 %).
        JsonNode w = in.path("infoWidth");
        if (w.isInt() && (w.asInt() == 0 || w.asInt() >= 400 && w.asInt() <= 4000)) {
            out.put("infoWidth", w.asInt());
        }
        return out;
    }

    private static void intIn(JsonNode in, ObjectNode out, String key, int min, int max) {
        JsonNode n = in.path(key);
        if (n.isInt() && n.asInt() >= min && n.asInt() <= max) {
            out.put(key, n.asInt());
        }
    }

    private static void oneOf(JsonNode in, ObjectNode out, String key, List<String> allowed) {
        JsonNode n = in.path(key);
        if (n.isTextual() && allowed.contains(n.asText())) {
            out.put(key, n.asText());
        }
    }

    /** The stored settings (only chosen keys; empty before the first change). */
    static synchronized ObjectNode load() {
        String json = AppSettings.getJson(KEY);
        if (json == null) {
            return Hub.JSON.createObjectNode();
        }
        try {
            return clean(Hub.JSON.readTree(json));
        } catch (IOException e) {
            return Hub.JSON.createObjectNode();
        }
    }

    /** Merges the valid keys of {@code in} into the stored settings and returns them. */
    static synchronized ObjectNode save(JsonNode in) {
        ObjectNode merged = load();
        merged.setAll(clean(in));
        AppSettings.set(KEY, merged);
        return merged;
    }

    /** Routes; call after {@link ControlGuard#install}. */
    static void install(io.javalin.Javalin app) {
        app.get(PATH, ctx -> ctx.header("Cache-Control", "no-store")
                .json(Map.of("controller", ControlGuard.isController(ctx), "settings", load())));
        app.post(PATH, ctx -> {
            try {
                ctx.json(save(Hub.JSON.readTree(ctx.body())));
            } catch (IOException e) {
                ctx.status(400);
            }
        });
    }
}
