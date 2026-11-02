package dev.ljlinkpro;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The LJ-Link-PRO logo: a waveform whose bars shoot light beams. Same geometry as {@code web/logo.svg}.
 * Used for the menu bar / tray icon and, via {@link #main}, to generate the installer icons.
 */
final class Logo {
    private Logo() {}

    private static final int[][] BARS = {
            {76, 350, 70}, {136, 290, 130}, {196, 210, 210}, {256, 270, 150}, {316, 170, 250}, {376, 310, 110}, {436, 360, 60}};
    /** Beam top-left x, top-right x at the bar, bar top y, far top-left x, far top-right x (far end is y = 70). */
    private static final int[][] BEAMS = {
            {76, 116, 350, 28, 92}, {136, 176, 290, 106, 164}, {196, 236, 210, 186, 226}, {256, 296, 270, 232, 280},
            {316, 356, 170, 310, 356}, {376, 416, 310, 360, 424}, {436, 476, 360, 420, 490}};

    /** @param rounded draw the dark rounded-square background (app icon); false for a transparent menu bar icon */
    static BufferedImage render(int size, boolean rounded) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        double k = size / 512.0;
        g.scale(k, k);

        if (rounded) {
            g.setPaint(new GradientPaint(0, 0, new Color(0x18202d), 0, 512, new Color(0x06080c)));
            g.fill(new RoundRectangle2D.Double(0, 0, 512, 512, 224, 224));
        }

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.88f));
        for (int[] b : BEAMS) {
            Polygon p = new Polygon(new int[] {b[0], b[1], b[4], b[3]}, new int[] {b[2], b[2], 70, 70}, 4);
            g.setPaint(new GradientPaint(0, b[2], new Color(255, 226, 110, 255), 0, 70, new Color(255, 226, 110, 0)));
            g.fill(p);
        }
        g.setComposite(AlphaComposite.SrcOver);
        for (int[] b : BARS) {
            g.setPaint(new GradientPaint(0, b[1], new Color(0x7cb7ff), 0, 420, new Color(0x2563eb)));
            g.fill(new RoundRectangle2D.Double(b[0], b[1], 40, b[2], 28, 28));
        }
        g.dispose();
        return img;
    }

    /** Writes icon PNGs for the installer tooling: {@code java dev.ljlinkpro.Logo <outputDir>}. */
    public static void main(String[] args) throws IOException {
        Path dir = Path.of(args.length > 0 ? args[0] : "packaging/icons");
        Files.createDirectories(dir);
        for (int size : new int[] {16, 32, 48, 64, 128, 256, 512, 1024}) {
            javax.imageio.ImageIO.write(render(size, true), "png", dir.resolve("icon-" + size + ".png").toFile());
        }
        javax.imageio.ImageIO.write(render(64, false), "png", dir.resolve("tray.png").toFile());
    }
}
