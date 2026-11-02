package dev.ljlinkpro;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Privacy mode: hides track title, artist, album and cover. It is switched only from the program icon (menu bar /
 * system tray) or with {@code --privacy}; there is deliberately no web endpoint to change it. The data is removed on the
 * server, so nothing leaks to other devices in the network.
 */
final class Privacy {
    private Privacy() {}

    static final String HIDDEN = "hidden (privacy mode)";
    private static final Set<String> HIDDEN_ROWS = Set.of(
            "Title", "Artist", "Album", "Genre", "Label", "Remixer", "Original artist", "Comment");
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile boolean enabled = load();

    static boolean enabled() {
        return enabled;
    }

    /** Switches the mode, remembers it for the next start and tells the listeners (the web clients). */
    static void set(boolean on) {
        enabled = on;
        save();
        notifyListeners();
    }

    /** Switches the mode for this run only (command line flag). */
    static void setForThisRun(boolean on) {
        enabled = on;
        notifyListeners();
    }

    static void addListener(Runnable listener) {
        LISTENERS.add(listener);
    }

    private static void notifyListeners() {
        for (Runnable r : LISTENERS) {
            try {
                r.run();
            } catch (RuntimeException e) {
                System.err.println("Privacy listener failed: " + e);
            }
        }
    }

    private static boolean load() {
        return AppSettings.getBoolean("privacy", false);
    }

    private static void save() {
        AppSettings.set("privacy", enabled);
    }

    /** Removes everything that names a track from the player detail sections (rows, cue comments). */
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> filterInfo(List<Map<String, Object>> sections) {
        if (!enabled) {
            return sections;
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> sec : sections) {
            Map<String, Object> copy = new LinkedHashMap<>(sec);
            List<Map<String, String>> rows = new ArrayList<>();
            for (Map<String, String> row : (List<Map<String, String>>) sec.get("rows")) {
                String k = row.get("k");
                String v = row.get("v");
                if (HIDDEN_ROWS.contains(k)) {
                    v = HIDDEN;
                } else if (k.startsWith("Hot cue") || k.startsWith("Memory point")) {
                    v = v.replaceAll("\\s+\".*\"$", "");
                }
                rows.add(Map.of("k", k, "v", v));
            }
            copy.put("rows", rows);
            out.add(copy);
        }
        return out;
    }
}
