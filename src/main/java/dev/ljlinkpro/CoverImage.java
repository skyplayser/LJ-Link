package dev.ljlinkpro;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.WeakHashMap;
import javax.imageio.ImageIO;

/** Covers as served over HTTP: the right image content type, and at most {@link #MAX_SIDE} px (larger ones are scaled down once). */
final class CoverImage {
    static final int MAX_SIDE = 1000;
    private static final Map<byte[], byte[]> SCALED = new WeakHashMap<>();

    private CoverImage() {}

    static String contentType(byte[] data) {
        if (data.length >= 8 && (data[0] & 0xff) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
            return "image/png";
        }
        if (data.length >= 3 && (data[0] & 0xff) == 0xFF && (data[1] & 0xff) == 0xD8) {
            return "image/jpeg";
        }
        if (data.length >= 6 && data[0] == 'G' && data[1] == 'I' && data[2] == 'F') {
            return "image/gif";
        }
        if (data.length >= 12 && data[0] == 'R' && data[1] == 'I' && data[8] == 'W' && data[9] == 'E') {
            return "image/webp";
        }
        return "application/octet-stream";
    }

    /** The cover, scaled down to {@link #MAX_SIDE} px if it is larger (as JPEG); unreadable images are passed on as they are. */
    static synchronized byte[] limited(byte[] data) {
        byte[] cached = SCALED.get(data);
        if (cached != null) {
            return cached;
        }
        byte[] out = data;
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
            if (img != null && Math.max(img.getWidth(), img.getHeight()) > MAX_SIDE) {
                double f = MAX_SIDE / (double) Math.max(img.getWidth(), img.getHeight());
                int w = Math.max(1, (int) Math.round(img.getWidth() * f));
                int h = Math.max(1, (int) Math.round(img.getHeight() * f));
                BufferedImage small = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                var g = small.createGraphics();
                g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(img, 0, 0, w, h, null);
                g.dispose();
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ImageIO.write(small, "jpg", bytes);
                out = bytes.toByteArray();
            }
        } catch (IOException | RuntimeException e) {
            // keep the original
        }
        SCALED.put(data, out);
        return out;
    }
}
