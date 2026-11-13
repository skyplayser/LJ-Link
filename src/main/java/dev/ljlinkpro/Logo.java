package dev.ljlinkpro;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The LJ Link logo "Fader" (suite style, chosen 2026-11-13): a dark rounded tile with the letters "LJ" in Inter Display
 * ExtraBold and five console faders whose caps carry the five suite colours. Same geometry as {@code web/logo.svg} and the
 * installer icons in {@code packaging/LJ-Link.*} (design units: a 1024 grid, the tile spans 101.5 to 922.5).
 *
 * <p>The letters are stored as outlines ({@link #LETTERS}, taken from the Inter font, SIL Open Font License 1.1, see
 * THIRD_PARTY_NOTICES.md), so no font is shipped. {@link #icon} draws the full app icon, {@link #tray} a simplified
 * version for the menu bar / system tray (tile filling the image, only the five faders, no letters, since "LJ" is
 * unreadable at 16-22 px). The tray icon is a coloured image, not a macOS template icon (it never was one).
 * {@link #main} writes the icon PNGs for the installer tooling.
 */
final class Logo {
    private Logo() {}

    /** Tile: x, y, size and corner radius on the 1024 grid. */
    static final double TILE_X = 101.5, TILE_SIZE = 821, TILE_RADIUS = 196;
    static final Color TILE_TOP = new Color(0x1A1E2A), TILE_BOTTOM = new Color(0x0D1016), TILE_EDGE = new Color(0x2A2F3D);
    static final Color LETTER = new Color(0xF2F4F8), RAIL = new Color(0x272C39);
    /** Fader cap colours (suite colours) and cap levels (0 = top of the travel, 1 = bottom). */
    static final Color[] CAPS = {new Color(0xFF3B3B), new Color(0xFFB020), new Color(0x3DDC84), new Color(0x22C8F0), new Color(0xB45CFF)};
    static final double[] LEVELS = {0.62, 0.28, 0.78, 0.45, 0.16};

    /**
     * "LJ" as outlines on the 1024 grid: Inter Display ExtraBold, 320 px, centred at x = 512, baseline y = 520, letter
     * spacing -4 px. Identical to the path in {@code web/logo.svg} (checked by LogoTest).
     */
    static final String LETTERS = "M348.8 520L348.8 287.2L405.1 287.2L405.1 472.2L508.2 472.2L508.2 520L348.8 520Z"
            + "M598.1 523.6Q560.6 523.6 539.7 504Q518.9 484.4 518.9 447.8L518.9 430.8L571.2 430.8L571.2 448.3"
            + "Q571.2 462 578.2 469.4Q585.1 476.7 597.3 476.7Q609.5 476.7 616.4 469.4Q623.3 462 623.3 448.3"
            + "L623.3 287.2L679.5 287.2L679.5 449.2Q679.5 485 658.1 504.3Q636.7 523.6 598.1 523.6Z";

    /** The full app icon (as packaging/LJ-Link.png): tile with margin, letters and faders. */
    static BufferedImage icon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = graphics(img);
        g.scale(size / 1024.0, size / 1024.0);
        tile(g);
        g.setColor(LETTER);
        g.fill(path(LETTERS));
        faders(g, 230, 600, 564, 220);
        g.dispose();
        return img;
    }

    /** The menu bar / tray icon: the tile fills the image (100 to 924 on the grid), only the five faders. */
    static BufferedImage tray(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = graphics(img);
        g.scale(size / 824.0, size / 824.0);
        g.translate(-100, -100);
        tile(g);
        faders(g, 214, 236, 596, 552);
        g.dispose();
        return img;
    }

    private static Graphics2D graphics(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g;
    }

    private static void tile(Graphics2D g) {
        Shape tile = new RoundRectangle2D.Double(TILE_X, TILE_X, TILE_SIZE, TILE_SIZE, 2 * TILE_RADIUS, 2 * TILE_RADIUS);
        g.setPaint(new GradientPaint(100, 100, TILE_TOP, 924, 924, TILE_BOTTOM));
        g.fill(tile);
        g.setColor(TILE_EDGE);
        g.setStroke(new BasicStroke(3));
        g.draw(tile);
    }

    /** Five faders in the box (x, y, w, h): thin dark rails over the full height, coloured caps at {@link #LEVELS}. */
    static void faders(Graphics2D g, double x, double y, double w, double h) {
        double s = w / 5.6, gap = (w - 5 * s) / 4, rail = Math.max(2, s * 0.2), cap = Math.max(4, h * 0.24);
        double r = Math.min(s, cap) * 0.28;
        for (int i = 0; i < 5; i++) {
            double left = x + i * (s + gap), cx = left + s / 2;
            g.setColor(RAIL);
            g.fill(new RoundRectangle2D.Double(cx - rail / 2, y, rail, h, rail, rail));
            g.setColor(CAPS[i]);
            g.fill(new RoundRectangle2D.Double(left, y + (h - cap) * LEVELS[i], s, cap, 2 * r, 2 * r));
        }
    }

    private static final Pattern TOKEN = Pattern.compile("[MLQCZ]|-?\\d+(?:\\.\\d+)?");

    /** Parses the absolute SVG path commands M, L, Q, C and Z (enough for {@link #LETTERS}). */
    static Path2D path(String d) {
        Path2D.Double p = new Path2D.Double(Path2D.WIND_NON_ZERO);
        Matcher m = TOKEN.matcher(d);
        java.util.List<String> t = new java.util.ArrayList<>();
        while (m.find()) t.add(m.group());
        for (int i = 0; i < t.size(); ) {
            String c = t.get(i++);
            double[] a = new double[6];
            int n = switch (c) {
                case "M", "L" -> 2;
                case "Q" -> 4;
                case "C" -> 6;
                case "Z" -> 0;
                default -> throw new IllegalArgumentException("Unsupported path token " + c);
            };
            for (int k = 0; k < n; k++) a[k] = Double.parseDouble(t.get(i++));
            switch (c) {
                case "M" -> p.moveTo(a[0], a[1]);
                case "L" -> p.lineTo(a[0], a[1]);
                case "Q" -> p.quadTo(a[0], a[1], a[2], a[3]);
                case "C" -> p.curveTo(a[0], a[1], a[2], a[3], a[4], a[5]);
                default -> p.closePath();
            }
        }
        return p;
    }

    /**
     * Writes icon PNGs for the installer tooling: {@code java dev.ljlinkpro.Logo <outputDir>}. The shipped
     * {@code packaging/LJ-Link.*} come from the logo package (rendered in a browser); these PNGs have the same geometry.
     */
    public static void main(String[] args) throws IOException {
        Path dir = Path.of(args.length > 0 ? args[0] : "packaging/icons");
        Files.createDirectories(dir);
        for (int size : new int[] {16, 32, 48, 64, 128, 256, 512, 1024}) {
            javax.imageio.ImageIO.write(icon(size), "png", dir.resolve("icon-" + size + ".png").toFile());
        }
        javax.imageio.ImageIO.write(tray(64), "png", dir.resolve("tray.png").toFile());
    }
}
