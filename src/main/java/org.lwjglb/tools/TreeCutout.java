package org.lwjglb.tools;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayDeque;

public final class TreeCutout {

    private static final String ASSETS = "src/main/java/org.lwjglb/assets/";

    private static final int BG_R = 126;
    private static final int BG_G = 127;
    private static final int BG_B = 126;

    private static final double KEY_DIST = 35.0;
    private static final double FEATHER_DIST = 60.0;

    private static final double MIN_TRANSPARENT = 0.10;

    private static final String[] TREES = {"arbol1", "arbol2"};

    private TreeCutout() {
    }

    public static void main(String[] args) {
        boolean ok;
        try {
            ok = true;
            for (String tree : TREES) {
                ok &= cut(tree);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("TREECUTOUT CHECK: FAIL");
            System.exit(1);
            return;
        }
        System.out.println(ok ? "TREECUTOUT CHECK: PASS" : "TREECUTOUT CHECK: FAIL");
        System.exit(ok ? 0 : 1);
    }

    private static boolean cut(String name) throws Exception {
        File src = new File(ASSETS + name + ".png");
        BufferedImage img = ImageIO.read(src);
        if (img == null) {
            System.out.println("cannot read " + src.getPath());
            return false;
        }
        int w = img.getWidth();
        int h = img.getHeight();
        int n = w * h;
        int[] argb = img.getRGB(0, 0, w, h, null, 0, w);

        double[] dist = new double[n];
        for (int i = 0; i < n; i++) {
            int p = argb[i];
            double dr = ((p >> 16) & 0xFF) - BG_R;
            double dg = ((p >> 8) & 0xFF) - BG_G;
            double db = (p & 0xFF) - BG_B;
            dist[i] = Math.sqrt(dr * dr + dg * dg + db * db);
        }

        boolean[] removed = new boolean[n];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int x = 0; x < w; x++) {
            seed(w, x, 0, dist, removed, queue);
            seed(w, x, h - 1, dist, removed, queue);
        }
        for (int y = 0; y < h; y++) {
            seed(w, 0, y, dist, removed, queue);
            seed(w, w - 1, y, dist, removed, queue);
        }
        while (!queue.isEmpty()) {
            int i = queue.poll();
            int x = i % w;
            int y = i / w;
            if (x > 0) grow(i - 1, dist, removed, queue);
            if (x < w - 1) grow(i + 1, dist, removed, queue);
            if (y > 0) grow(i - w, dist, removed, queue);
            if (y < h - 1) grow(i + w, dist, removed, queue);
        }

        int[] out = new int[n];
        int keyed = 0;
        int transparent = 0;
        int minX = w;
        int minY = h;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                int alpha;
                if (removed[i]) {
                    alpha = 0;
                    keyed++;
                } else if (dist[i] <= FEATHER_DIST && touchesRemoved(x, y, w, h, removed)) {
                    double t = (dist[i] - KEY_DIST) / (FEATHER_DIST - KEY_DIST);
                    t = Math.max(0.0, Math.min(1.0, t));
                    alpha = (int) Math.round(255.0 * t);
                } else {
                    alpha = 255;
                }
                if (alpha == 0) {
                    transparent++;
                } else {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
                out[i] = (argb[i] & 0x00FFFFFF) | (alpha << 24);
            }
        }

        File dst = new File(ASSETS + name + "_cut.png");
        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        result.setRGB(0, 0, w, h, out, 0, w);
        ImageIO.write(result, "png", dst);

        int corner = argb[0];
        int centreAlpha = (out[(h / 2) * w + (w / 2)] >>> 24) & 0xFF;
        double keyedPct = 100.0 * keyed / n;
        double transparentPct = 100.0 * transparent / n;
        int boxW = maxX - minX + 1;
        int boxH = maxY - minY + 1;
        System.out.printf("%s: %dx%d src_argb=0x%08X backdrop_corner=(%d,%d,%d) "
                        + "keyed=%.1f%% transparent=%.1f%% centre_alpha=%d%n",
                name, w, h, corner, (corner >> 16) & 0xFF, (corner >> 8) & 0xFF, corner & 0xFF,
                keyedPct, transparentPct, centreAlpha);
        System.out.printf("%s: tree bbox x[%d..%d] y[%d..%d] -> %dx%d px = %.1f%% wide x %.1f%% tall "
                        + "of the image%s%n",
                name, minX, maxX, minY, maxY, boxW, boxH,
                100.0 * boxW / w, 100.0 * boxH / h,
                dst.getPath());

        boolean ok = true;
        if (transparentPct <= MIN_TRANSPARENT * 100.0) {
            System.out.printf("FAIL %s: only %.1f%% transparent (need > %.0f%%)%n",
                    name, transparentPct, MIN_TRANSPARENT * 100.0);
            ok = false;
        }
        if (centreAlpha != 255) {
            System.out.printf("FAIL %s: image centre alpha = %d (must stay opaque)%n",
                    name, centreAlpha);
            ok = false;
        }
        if (maxX < 0) {
            System.out.println("FAIL " + name + ": nothing left of the tree");
            ok = false;
        }
        System.out.printf("%s checks: transparent>10%%=%s centre_opaque=%s in_expected_30_70%%=%s%n",
                name, mark(transparentPct > MIN_TRANSPARENT * 100.0), mark(centreAlpha == 255),
                mark(transparentPct >= 30.0 && transparentPct <= 70.0));
        return ok;
    }

    private static String mark(boolean b) {
        return b ? "OK" : "BAD";
    }

    private static void seed(int w, int x, int y, double[] dist, boolean[] removed,
                             ArrayDeque<Integer> queue) {
        int i = y * w + x;
        if (dist[i] <= KEY_DIST) {
            removed[i] = true;
            queue.add(i);
        }
    }

    private static void grow(int i, double[] dist, boolean[] removed, ArrayDeque<Integer> queue) {
        if (!removed[i] && dist[i] <= KEY_DIST) {
            removed[i] = true;
            queue.add(i);
        }
    }

    private static boolean touchesRemoved(int x, int y, int w, int h, boolean[] removed) {
        for (int dy = -1; dy <= 1; dy++) {
            int ny = y + dy;
            if (ny < 0 || ny >= h) {
                continue;
            }
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx;
                if (nx < 0 || nx >= w || (dx == 0 && dy == 0)) {
                    continue;
                }
                if (removed[ny * w + nx]) {
                    return true;
                }
            }
        }
        return false;
    }
}
