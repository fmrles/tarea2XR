package org.lwjglb.tools;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

public final class BurrowTexture {

    private static final String ASSETS = "src/main/java/org.lwjglb/assets/";

    private static final int SIZE = 512;
    private static final int R = 240;

    private static final double HOLE_END = 0.42;
    private static final double RIM_END = 0.62;
    private static final double MOUND_END = 0.78;

    private static final double MIN_TRANSPARENT = 0.10;
    private static final double MIN_DIRT = 0.05;
    private static final int MAX_CENTER_CHANNEL = 90;

    private static final long SEED = 20261111L;

    private BurrowTexture() {
    }

    public static void main(String[] args) {
        boolean ok;
        try {
            ok = generate();
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("BURROWTEXTURE CHECK: FAIL");
            System.exit(1);
            return;
        }
        System.out.println(ok ? "BURROWTEXTURE CHECK: PASS" : "BURROWTEXTURE CHECK: FAIL");
        System.exit(ok ? 0 : 1);
    }

    private static boolean generate() throws Exception {
        Random rng = new Random(SEED);
        double holeEndPx = HOLE_END * R;
        double rimEndPx = RIM_END * R;
        double moundEndPx = MOUND_END * R;
        double cx = SIZE / 2.0;
        double cy = SIZE / 2.0;

        BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        int[] argb = new int[SIZE * SIZE];

        int holePx = 0;
        int rimPx = 0;
        int moundPx = 0;
        int transparentPx = 0;
        int dirtPx = 0;

        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                double dx = x + 0.5 - cx;
                double dy = y + 0.5 - cy;
                double r = Math.sqrt(dx * dx + dy * dy);

                int rgb;
                int alpha;
                if (r <= holeEndPx) {
                    double t = r / holeEndPx;
                    int nr = noise(rng, 8);
                    int ng = noise(rng, 8);
                    int nb = noise(rng, 8);
                    int rr = clamp((int) Math.round(10 + t * 35 + nr));
                    int gg = clamp((int) Math.round(8 + t * 24 + ng));
                    int bb = clamp((int) Math.round(6 + t * 16 + nb));
                    rgb = (rr << 16) | (gg << 8) | bb;
                    alpha = 255;
                    holePx++;
                } else if (r <= rimEndPx) {
                    int n = noise(rng, 15);
                    int rr = clamp(110 + n);
                    int gg = clamp(78 + n);
                    int bb = clamp(48 + n);
                    rgb = (rr << 16) | (gg << 8) | bb;
                    alpha = 255;
                    rimPx++;
                } else if (r <= moundEndPx) {
                    int n = noise(rng, 15);
                    int rr = clamp(150 + n);
                    int gg = clamp(115 + n);
                    int bb = clamp(75 + n);
                    rgb = (rr << 16) | (gg << 8) | bb;
                    double t = (r - rimEndPx) / (moundEndPx - rimEndPx);
                    double keep = rng.nextDouble();
                    alpha = keep > t ? 255 : 0;
                    if (alpha == 0) {
                        transparentPx++;
                    } else {
                        moundPx++;
                    }
                } else {
                    rgb = 0;
                    alpha = 0;
                    transparentPx++;
                }

                if (alpha != 0) {
                    int brightness = (((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF)) / 3;
                    boolean rGreater = ((rgb >> 16) & 0xFF) > ((rgb >> 8) & 0xFF)
                            && ((rgb >> 8) & 0xFF) > (rgb & 0xFF);
                    if (rGreater && brightness >= 60 && brightness <= 200) {
                        dirtPx++;
                    }
                }
                argb[y * SIZE + x] = (alpha << 24) | rgb;
            }
        }

        img.setRGB(0, 0, SIZE, SIZE, argb, 0, SIZE);
        File dst = new File(ASSETS + "burrow.png");
        ImageIO.write(img, "png", dst);

        int n = SIZE * SIZE;
        double holePct = 100.0 * holePx / n;
        double rimPct = 100.0 * rimPx / n;
        double transparentPct = 100.0 * transparentPx / n;
        double dirtPct = 100.0 * dirtPx / n;

        int centre = argb[(SIZE / 2) * SIZE + (SIZE / 2)];
        int centreA = (centre >>> 24) & 0xFF;
        int centreR = (centre >> 16) & 0xFF;
        int centreG = (centre >> 8) & 0xFF;
        int centreB = centre & 0xFF;
        int cornerA = (argb[0] >>> 24) & 0xFF;

        System.out.printf("burrow: %dx%d R=%d hole=%.1f%% rim=%.1f%% mound=%.1f%% transparent=%.1f%% dirt=%.1f%% "
                        + "centre=(%d,%d,%d) a=%d corner_a=%d%n",
                SIZE, SIZE, R, holePct, rimPct, 100.0 * moundPx / n, transparentPct, dirtPct,
                centreR, centreG, centreB, centreA, cornerA);

        boolean ok = true;
        if (transparentPct <= MIN_TRANSPARENT * 100.0) {
            System.out.printf("FAIL: only %.1f%% transparent (need > %.0f%%)%n",
                    transparentPct, MIN_TRANSPARENT * 100.0);
            ok = false;
        }
        boolean cornerTransparent = cornerA == 0;
        if (!cornerTransparent) {
            System.out.printf("FAIL: corner alpha = %d (must be 0)%n", cornerA);
            ok = false;
        }
        boolean centerOpaque = centreA == 255;
        boolean centerDark = centreR < MAX_CENTER_CHANNEL
                && centreG < MAX_CENTER_CHANNEL
                && centreB < MAX_CENTER_CHANNEL;
        if (!centerOpaque) {
            System.out.printf("FAIL: centre alpha = %d (must be 255)%n", centreA);
            ok = false;
        }
        if (!centerDark) {
            System.out.printf("FAIL: centre (%d,%d,%d) not dark (all channels < %d)%n",
                    centreR, centreG, centreB, MAX_CENTER_CHANNEL);
            ok = false;
        }
        boolean dirtPresent = dirtPct > MIN_DIRT * 100.0;
        if (!dirtPresent) {
            System.out.printf("FAIL: only %.1f%% dirt pixels (need > %.0f%%)%n",
                    dirtPct, MIN_DIRT * 100.0);
            ok = false;
        }
        System.out.printf("checks: transparent>10%%=%s corner_transparent=%s center_opaque=%s "
                        + "center_dark=%s dirt>5%%=%s%n",
                mark(transparentPct > MIN_TRANSPARENT * 100.0), mark(cornerTransparent),
                mark(centerOpaque), mark(centerDark), mark(dirtPresent));
        return ok;
    }

    private static int noise(Random rng, int amp) {
        return rng.nextInt(amp * 2 + 1) - amp;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static String mark(boolean b) {
        return b ? "OK" : "BAD";
    }
}
