package dev.ljlinkpro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** The "Fader" logo: code, web SVG and installer icons show the same picture. */
class LogoTest {

    private static String svg() throws Exception {
        try (InputStream in = Logo.class.getResourceAsStream("/web/logo.svg")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void svgUsesTheSameLettersAndColours() throws Exception {
        String svg = svg();
        assertTrue(svg.contains("d=\"" + Logo.LETTERS + "\""), "logo.svg letters differ from Logo.LETTERS");
        for (Color c : Logo.CAPS) {
            assertTrue(svg.contains(String.format(Locale.ROOT, "#%06X", c.getRGB() & 0xFFFFFF)), "cap colour missing: " + c);
        }
        assertTrue(svg.contains("viewBox=\"100 100 824 824\""), "the tile should fill the SVG");
        assertTrue(!svg.contains("<text") && !svg.contains("@font-face"), "no font in the SVG, only outlines");
    }

    @Test
    void lettersParseIntoTwoGlyphsInsideTheTile() {
        var bounds = Logo.path(Logo.LETTERS).getBounds2D();
        assertEquals(348.8, bounds.getMinX(), 0.01);
        assertEquals(679.5, bounds.getMaxX(), 0.01);
        assertEquals(287.2, bounds.getMinY(), 0.01);
        assertEquals(523.6, bounds.getMaxY(), 0.01);
        // Centred on x = 512 within a few pixels (J is wider than L; centring is by advance width, as in the browser).
        assertEquals(512, bounds.getCenterX(), 4);
    }

    @Test
    void iconMatchesThePackagedInstallerIcon() throws Exception {
        File packaged = new File("packaging/LJ-Link.png");
        BufferedImage ref = ImageIO.read(packaged);
        BufferedImage me = Logo.icon(ref.getWidth());
        long sum = 0;
        int far = 0;
        for (int y = 0; y < ref.getHeight(); y++) {
            for (int x = 0; x < ref.getWidth(); x++) {
                int p = ref.getRGB(x, y), q = me.getRGB(x, y), max = 0;
                for (int s = 0; s < 32; s += 8) max = Math.max(max, Math.abs(((p >> s) & 255) - ((q >> s) & 255)));
                sum += max;
                if (max > 64) far++;
            }
        }
        double mean = sum / (double) (ref.getWidth() * ref.getHeight());
        // Only anti-aliasing and gradient dithering differ (measured: mean 0.48, 158 pixels).
        assertTrue(mean < 2, "mean difference " + mean);
        assertTrue(far < 2000, "pixels far off: " + far);
    }

    @Test
    void trayIconShowsTheFiveCapsOnTheTile() {
        BufferedImage img = Logo.tray(64);
        assertEquals(0, img.getRGB(0, 0) >>> 24, "corners stay transparent");
        assertEquals(255, img.getRGB(32, 6) >>> 24, "the tile fills the image");
        for (Color c : Logo.CAPS) {
            boolean found = false;
            for (int y = 0; y < 64 && !found; y++) {
                for (int x = 0; x < 64 && !found; x++) found = (img.getRGB(x, y) & 0xFFFFFF) == (c.getRGB() & 0xFFFFFF);
            }
            assertTrue(found, "cap colour missing in the tray icon: " + c);
        }
    }

    @Test
    void mainWritesTheIconSet() throws Exception {
        Path dir = Files.createTempDirectory("ljlink-logo");
        Logo.main(new String[] {dir.toString()});
        for (int size : new int[] {16, 32, 48, 64, 128, 256, 512, 1024}) {
            BufferedImage img = ImageIO.read(dir.resolve("icon-" + size + ".png").toFile());
            assertEquals(size, img.getWidth());
        }
        assertEquals(64, ImageIO.read(dir.resolve("tray.png").toFile()).getWidth());
    }
}
