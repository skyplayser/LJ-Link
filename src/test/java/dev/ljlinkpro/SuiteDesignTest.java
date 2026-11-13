package dev.ljlinkpro;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The web interface uses the suite design tokens as VJ-Link (ADR-037), with cyan as accent and a readable day theme. */
class SuiteDesignTest {

    private static final List<String> COLOR_TOKENS = List.of("--bg", "--panel", "--raise", "--line", "--text", "--dim",
            "--accent", "--accent-text", "--live", "--live-fill", "--ok", "--warn", "--master", "--wave-bg", "--playhead");

    /** The custom properties of the first rule with exactly this selector. */
    private static Map<String, String> tokens(String css, String selector) {
        int at = css.indexOf(selector + " {");
        assertTrue(at >= 0, selector);
        String body = css.substring(css.indexOf('{', at) + 1, css.indexOf('}', at));
        Map<String, String> out = new LinkedHashMap<>();
        Matcher m = Pattern.compile("(--[\\w-]+)\\s*:\\s*([^;]+);").matcher(body);
        while (m.find()) out.put(m.group(1), m.group(2).trim());
        return out;
    }

    @Test
    void nightUsesTheSuiteTokensWithCyanAccent() throws IOException {
        Map<String, String> night = tokens(KeepAwakeWebTest.resource("style.css"), ":root");
        Map<String, String> suite = Map.ofEntries(Map.entry("--bg", "#0C0E13"), Map.entry("--panel", "#151821"),
                Map.entry("--raise", "#1E222E"), Map.entry("--line", "#2A2F3D"), Map.entry("--text", "#DCE1EA"),
                Map.entry("--dim", "#7E879A"), Map.entry("--live", "#FF3B3B"), Map.entry("--ok", "#3DDC84"),
                Map.entry("--warn", "#FFB020"), Map.entry("--sA", "#FF3B3B"), Map.entry("--sB", "#FFB020"),
                Map.entry("--sC", "#3DDC84"), Map.entry("--sD", "#22C8F0"), Map.entry("--sE", "#B45CFF"));
        suite.forEach((k, v) -> assertEquals(v, night.get(k), k));
        assertEquals("#22C8F0", night.get("--accent"), "accent from the Fader logo, not the suite pink");
        assertTrue(night.get("--mono").startsWith("ui-monospace"));
    }

    @Test
    void noLegacyVariablesAndEveryVariableIsDefined() throws IOException {
        String css = KeepAwakeWebTest.resource("style.css");
        String app = KeepAwakeWebTest.resource("app.js");
        for (String old : List.of("--muted", "--panel-2", "--bad", "--onair", "--radius")) {
            assertFalse(css.contains(old + ")") || css.contains(old + ":") || css.contains(old + ","), old + " in style.css");
            assertFalse(app.contains("\"" + old + "\""), old + " in app.js");
        }
        Set<String> defined = new TreeSet<>(tokens(css, ":root").keySet());
        Set<String> used = new TreeSet<>();
        Matcher m = Pattern.compile("var\\((--[\\w-]+)").matcher(css);
        while (m.find()) used.add(m.group(1));
        used.removeAll(defined);
        used.remove("--angle"); // set per knob by app.js, with a fallback in var()
        assertTrue(used.isEmpty(), "undefined variables: " + used);
        // The day theme overrides every color token, so nothing dark is left on light ground.
        Set<String> day = tokens(css, ":root[data-theme=\"day\"]").keySet();
        for (String k : COLOR_TOKENS) assertTrue(day.contains(k), "day theme lacks " + k);
    }

    @Test
    void textReachesWcagAaInBothThemes() throws IOException {
        String css = KeepAwakeWebTest.resource("style.css");
        Map<String, String> night = tokens(css, ":root");
        Map<String, String> day = new LinkedHashMap<>(night);
        day.putAll(tokens(css, ":root[data-theme=\"day\"]"));
        for (Map<String, String> t : List.of(night, day)) {
            for (String fg : List.of("--text", "--accent", "--ok", "--warn", "--live", "--master")) {
                for (String bg : List.of("--bg", "--panel")) {
                    double c = contrast(t.get(fg), t.get(bg));
                    assertTrue(c >= 4.5, fg + " on " + bg + " " + t.get(fg) + "/" + t.get(bg) + ": " + c);
                }
            }
            assertTrue(contrast(t.get("--dim"), t.get("--panel")) >= 4.5, "dim text on panels: " + t.get("--dim"));
            assertTrue(contrast(t.get("--accent-text"), t.get("--accent")) >= 4.5, "primary buttons");
        }
        for (String fg : List.of("--text", "--dim", "--accent", "--ok", "--warn", "--live")) {
            assertTrue(contrast(day.get(fg), day.get("--raise")) >= 4.5, "day " + fg + " on raised controls");
        }
    }

    private static Map<String, Map<String, String>> themes(String css) {
        Map<String, String> night = tokens(css, ":root");
        Map<String, String> day = new LinkedHashMap<>(night);
        day.putAll(tokens(css, ":root[data-theme=\"day\"]"));
        return Map.of("night", night, "day", day);
    }

    @Test
    void statusPillsAndBadgesAreReadableOnRaise() throws IOException {
        // The status pill ("Connected", "Demo mode", "Search running") and the badges sit on --raise.
        themes(KeepAwakeWebTest.resource("style.css")).forEach((name, t) -> {
            for (String fg : List.of("--ok", "--warn", "--accent")) {
                double c = contrast(t.get(fg), t.get("--raise"));
                assertTrue(c >= 4.5, name + " " + fg + " on --raise: " + c);
            }
        });
    }

    @Test
    void whiteTextOnRedReachesAa() throws IOException {
        String css = KeepAwakeWebTest.resource("style.css");
        themes(css).forEach((name, t) -> {
            double c = contrast("#FFFFFF", t.get("--live-fill"));
            assertTrue(c >= 4.5, name + " white on --live-fill: " + c);
        });
        // The LIVE tag and the mixer ON AIR state use the darker fill, not the suite red.
        assertTrue(rule(css, ".live-tag").contains("background: var(--live-fill); color: #fff;"));
        assertTrue(rule(css, ".mix-ch.onair .state").contains("background: var(--live-fill)"));
    }

    @Test
    void messagesOverTheWaveformKeepTheirContrast() throws IOException {
        String css = KeepAwakeWebTest.resource("style.css");
        // No opacity on the whole waveform area (it would also dim the message), only on the drawings.
        assertFalse(rule(css, ".deck-waves").contains("opacity"), ".deck-waves must not be transparent");
        assertFalse(rule(css, ".wave-msg").contains("opacity"));
        assertTrue(css.contains(".deck-waves canvas, .deck-waves .remaining { opacity: .75; }"));
        assertTrue(rule(css, ".wave-msg").contains("color: var(--dim)"));
        themes(css).forEach((name, t) -> {
            String ground = mix(t.get("--wave-bg"), t.get("--panel"), 0.75);
            for (String fg : List.of("--dim", "--accent")) {
                double c = contrast(t.get(fg), ground);
                assertTrue(c >= 4.5, name + " " + fg + " over the waveform " + ground + ": " + c);
            }
        });
    }

    @Test
    void starsAreVisibleAsGraphics() throws IOException {
        String css = KeepAwakeWebTest.resource("style.css");
        assertTrue(css.contains(".stars .star.on { color: var(--warn);"));
        assertFalse(rule(css, ".stars .star").contains("opacity"));
        themes(css).forEach((name, t) -> {
            for (String fg : List.of("--dim", "--warn")) {
                double c = contrast(t.get(fg), t.get("--panel"));
                assertTrue(c >= 3, name + " star " + fg + " on the dialog: " + c);
            }
        });
    }

    @Test
    void touchTargetsAreAtLeast44px() throws IOException {
        String css = KeepAwakeWebTest.resource("style.css");
        int at = css.indexOf("@media (pointer: coarse) {");
        assertTrue(at >= 0);
        String touch = css.substring(at);
        for (String sel : List.of("button.tool", ".tabs .tab", ".seg button", ".stars-row button.link", "button.awake-hint")) {
            Matcher m = Pattern.compile("[^{}]*\\Q" + sel + "\\E[^{}]*\\{[^}]*min-height: 44px").matcher(touch);
            assertTrue(m.find(), sel + " must be 44px high on touch screens");
        }
        assertTrue(touch.contains("label.check { min-height: 44px; }"));
        assertTrue(touch.contains("a.help-link { display: inline-flex; align-items: center; min-height: 44px; }"),
                "the help link in the report dialog stands alone, not in running text");
        assertTrue(touch.contains("#report summary { padding: 12px 0; }"));
        assertTrue(touch.contains(".setting input[type=\"range\"] { min-height: 44px; }")
                || touch.contains(", .setting input[type=\"range\"] { min-height: 44px; }"));
        // No rule in the touch block makes a target smaller again.
        assertFalse(Pattern.compile("min-height: (?:[0-3]?\\d|4[0-3])px").matcher(touch).find(), "smaller than 44px on touch");
    }

    @Test
    void baseTextIs15pxAndHints13px() throws IOException {
        String css = KeepAwakeWebTest.resource("style.css");
        assertTrue(rule(css, "html, body").contains("font: 15px/1.4 system-ui"));
        assertTrue(rule(css, ".hint").contains("font-size: 13px"));
        assertTrue(rule(css, ".tabs .tab").contains("font-size: 12px"));
        assertTrue(rule(css, ".setting > label:not(.check), .setting > .label").contains("font-size: 12px"));
    }

    @Test
    void sheetPrefersDynamicViewportHeight() throws IOException {
        // The later declaration wins: 100dvh where supported, 100vh as the fallback before it.
        String sheet = rule(KeepAwakeWebTest.resource("style.css"), ".sheet");
        assertTrue(sheet.indexOf("100vh") < sheet.indexOf("100dvh"), sheet);
    }

    /** The body of the first rule with exactly this selector (at the start of a line). */
    private static String rule(String css, String selector) {
        Matcher m = Pattern.compile("(?m)^" + Pattern.quote(selector) + " \\{([^}]*)\\}").matcher(css);
        assertTrue(m.find(), selector);
        return m.group(1);
    }

    /** Source over destination: {@code top} with opacity {@code a} over {@code bottom}. */
    static String mix(String top, String bottom, double a) {
        int t = Integer.parseInt(top.substring(1), 16), b = Integer.parseInt(bottom.substring(1), 16);
        int r = (int) Math.round(a * (t >> 16 & 255) + (1 - a) * (b >> 16 & 255));
        int g = (int) Math.round(a * (t >> 8 & 255) + (1 - a) * (b >> 8 & 255));
        int bl = (int) Math.round(a * (t & 255) + (1 - a) * (b & 255));
        return String.format("#%02X%02X%02X", r, g, bl);
    }

    @Test
    void deckSizesStayTheSame() throws IOException {
        // Live in the booth: the suite design must not shrink deck number, time, BPM or the waveform areas.
        String css = KeepAwakeWebTest.resource("style.css");
        for (String rule : List.of(".deck-num { font-size: 20px;", ".box.player b { font-size: 34px; }",
                ".box.bpm-box b { font-size: 32px;", ".elapsed { font-size: 46px; font-weight: 700;",
                ".wave.detail { height: 100px; flex: 0 0 100px; }", ".wave.overview { flex: 1; height: 56px; }",
                "grid-template-columns: 14px 400px minmax(0, 1fr);", "font-weight: 800; letter-spacing: .12em; font-size: 14px;")) {
            assertTrue(css.contains(rule), rule);
        }
    }

    private static double luminance(String hex) {
        int rgb = Integer.parseInt(hex.substring(1), 16);
        double[] c = {(rgb >> 16 & 255) / 255.0, (rgb >> 8 & 255) / 255.0, (rgb & 255) / 255.0};
        for (int i = 0; i < 3; i++) c[i] = c[i] <= 0.03928 ? c[i] / 12.92 : Math.pow((c[i] + 0.055) / 1.055, 2.4);
        return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
    }

    static double contrast(String a, String b) {
        double x = luminance(a), y = luminance(b);
        return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
    }
}
