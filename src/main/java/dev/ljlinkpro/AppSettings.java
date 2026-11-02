package dev.ljlinkpro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Small settings file {@code ~/.ljlinkpro/settings.json} shared by the privacy mode and the language.
 * Setting {@code -Dljlinkpro.home=...} moves it (used by the tests).
 */
final class AppSettings {
    private AppSettings() {}

    private static Path file() {
        String home = System.getProperty("ljlinkpro.home");
        Path dir = home != null ? Path.of(home) : Path.of(System.getProperty("user.home"), ".ljlinkpro");
        return dir.resolve("settings.json");
    }

    private static synchronized ObjectNode read() {
        try {
            Path f = file();
            if (Files.exists(f)) {
                JsonNode n = Hub.JSON.readTree(Files.readString(f));
                if (n instanceof ObjectNode o) {
                    return o;
                }
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("Could not read settings: " + e);
        }
        return Hub.JSON.createObjectNode();
    }

    static synchronized boolean getBoolean(String key, boolean fallback) {
        return read().path(key).asBoolean(fallback);
    }

    static synchronized String getString(String key, String fallback) {
        JsonNode n = read().path(key);
        return n.isTextual() ? n.asText() : fallback;
    }

    static synchronized void set(String key, Object value) {
        try {
            ObjectNode o = read();
            o.putPOJO(key, value);
            Path f = file();
            Files.createDirectories(f.getParent());
            Files.writeString(f, Hub.JSON.writeValueAsString(o));
        } catch (IOException e) {
            System.err.println("Could not save settings: " + e);
        }
    }

    /** The language of the program icon menu: "en" (default) or "de". */
    static String language() {
        return "de".equals(getString("language", "en")) ? "de" : "en";
    }
}
