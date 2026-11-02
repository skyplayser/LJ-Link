package dev.ljlinkpro;

import org.deepsymmetry.beatlink.data.WaveformFinder.ThreeBandLayer;
import org.deepsymmetry.beatlink.data.WaveformDetail;
import org.deepsymmetry.beatlink.data.WaveformFinder.WaveformStyle;
import org.deepsymmetry.beatlink.data.WaveformPreview;

import java.awt.Color;

/**
 * Reduces a beat-link waveform preview to a style-independent format: 4 bytes per segment
 * (height 0-255, red, green, blue). The browser only needs to draw bars.
 */
final class WaveformEncoder {
    private WaveformEncoder() {}

    /** "threeband" entries are three layer heights; "color" entries are height plus RGB. */
    static String styleName(WaveformStyle style) {
        return style == WaveformStyle.THREE_BAND ? "threeband" : "color";
    }

    /** Keeps the hue but lets the strongest channel reach full brightness, like the players' neon look. */
    private static Color vivid(int r, int g, int b) {
        int max = Math.max(1, Math.max(r, Math.max(g, b)));
        return new Color(Math.min(255, r * 255 / max), Math.min(255, g * 255 / max), Math.min(255, b * 255 / max));
    }

    static byte[] encodePreview(WaveformPreview pv) {
        int n = pv.segmentCount;
        int max = Math.max(1, pv.maxHeight);
        byte[] out = new byte[n * 4];
        for (int i = 0; i < n; i++) {
            int height;
            Color color;
            if (pv.style == WaveformStyle.THREE_BAND) {
                // Cumulative layers for painting back to front: low, low+mid, everything.
                out[i * 4] = (byte) Math.min(255, pv.segmentHeight(i, ThreeBandLayer.LOW) * 255 / max);
                out[i * 4 + 1] = (byte) Math.min(255, pv.segmentHeight(i, ThreeBandLayer.MID) * 255 / max);
                out[i * 4 + 2] = (byte) Math.min(255, pv.segmentHeight(i, ThreeBandLayer.HIGH) * 255 / max);
                continue;
            } else {
                height = pv.segmentHeight(i, false);
                Color c = pv.segmentColor(i, false);
                color = pv.style == WaveformStyle.RGB ? vivid(c.getBlue(), c.getGreen(), c.getRed()) : c;
            }
            out[i * 4] = (byte) Math.min(255, height * 255 / max);
            out[i * 4 + 1] = (byte) color.getRed();
            out[i * 4 + 2] = (byte) color.getGreen();
            out[i * 4 + 3] = (byte) color.getBlue();
        }
        return out;
    }

    /**
     * Detail waveform, one entry per frame (150 per second): 4 bytes (height 0-255, red, green, blue).
     * Heights are normalized to the maximum of the waveform style.
     */
    static byte[] encodeDetail(WaveformDetail d) {
        int n = d.getFrameCount();
        boolean threeBand = d.style == WaveformStyle.THREE_BAND;
        byte[] out = new byte[n * 4];
        int[] heights = new int[n];
        int[] layers = new int[threeBand ? n * 3 : 0];
        int max = 1;
        for (int i = 0; i < n; i++) {
            int height;
            Color color;
            if (threeBand) {
                int low = d.segmentHeight(i, 1, ThreeBandLayer.LOW);
                int mid = d.segmentHeight(i, 1, ThreeBandLayer.MID);
                int high = d.segmentHeight(i, 1, ThreeBandLayer.HIGH);
                layers[i * 3] = low;
                layers[i * 3 + 1] = mid;
                layers[i * 3 + 2] = high;
                max = Math.max(max, Math.max(low, Math.max(mid, high)));
                continue;
            } else {
                height = d.segmentHeight(i, 1);
                Color c = d.segmentColor(i, 1);
                color = d.style == WaveformStyle.RGB ? vivid(c.getRed(), c.getBlue(), c.getGreen()) : c;
            }
            heights[i] = height;
            max = Math.max(max, height);
            out[i * 4 + 1] = (byte) color.getRed();
            out[i * 4 + 2] = (byte) color.getGreen();
            out[i * 4 + 3] = (byte) color.getBlue();
        }
        // Scale to the loudest frame of this track so quiet exports still fill the display.
        for (int i = 0; i < n; i++) {
            if (threeBand) {
                for (int k = 0; k < 3; k++) {
                    out[i * 4 + k] = (byte) Math.min(255, layers[i * 3 + k] * 255 / max);
                }
            } else {
                out[i * 4] = (byte) Math.min(255, heights[i] * 255 / max);
            }
        }
        return out;
    }
}
