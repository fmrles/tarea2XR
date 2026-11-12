package org.lwjglb.tools;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Block 14 tool: converts each equirectangular panorama in {@code skybox_assets}
 * into the six cubemap faces consumed by {@code Loader.loadCubeMap} (order
 * +X, -X, +Y, -Y, +Z, -Z = Right, Left, Top, Bottom, Back, Front).
 *
 * <p>Face orientation follows the OpenGL cubemap convention (the same the skybox
 * shader relies on when sampling with a world-space direction). For a face pixel
 * with normalized (u,v) in [-1,1]^2 (u: left&rarr;right, v: top&rarr;bottom, v=+1 at
 * the top row), the ray direction is:
 *
 * <pre>
 *   Right (+X): dir = ( 1,  v, -u)     Left  (-X): dir = (-1,  v,  u)
 *   Top   (+Y): dir = ( u,  1, -v)     Bottom(-Y): dir = ( u, -1,  v)
 *   Back  (+Z): dir = ( u,  v,  1)     Front (-Z): dir = (-u,  v, -1)
 * </pre>
 *
 * <p>Equirectangular sampling: {@code lon = atan2(z, x)},
 * {@code uTex = lon/(2*PI) + 0.5} (wrapped horizontally), {@code vTex = (PI/2 - asin(y)) / PI}
 * (0 = zenith = top row), bilinear interpolation with horizontal wrap. Output is
 * opaque RGB, {@value #FACE_SIZE}x{@value #FACE_SIZE} per face, named
 * {@code skyboxMorning*|skyboxDay*|skyboxNight*} + {@code Right|Left|Top|Bottom|Back|Front}.
 * Legacy faces ({@code right.png}, {@code night*}, ...) are never touched.
 *
 * <p>Self-check: re-reads every written PNG (existence, 18-file count, size, opaque
 * pixels), round-trips a pixel grid through the equirect sampler, and validates the
 * six face-centre colours against direct panorama texel reads.
 */
public final class EquirectToCube {

    private static final String ASSETS = "src/main/java/org.lwjglb/skybox_assets/";
    private static final int FACE_SIZE = 1024;
    /** Self-check grid stride over the face (8x8 samples per face). */
    private static final int STEP = FACE_SIZE / 8;

    private static final String[] PANORAMAS = { "skybox-morning", "skybox-day", "skybox-night" };
    private static final String[] PREFIXES = { "skyboxMorning", "skyboxDay", "skyboxNight" };
    /** Faces in Loader.loadCubeMap order: +X, -X, +Y, -Y, +Z, -Z. */
    private static final String[] SUFFIXES = { "Right", "Left", "Top", "Bottom", "Back", "Front" };

    private EquirectToCube() {
    }

    public static void main(String[] args) {
        boolean ok = true;
        File dir = new File(ASSETS);
        for (int p = 0; p < PANORAMAS.length; p++) {
            try {
                File src = new File(dir, PANORAMAS[p] + ".png");
                BufferedImage panorama = ImageIO.read(src);
                if (panorama == null) {
                    throw new IllegalStateException("cannot read " + src);
                }
                int w = panorama.getWidth();
                int h = panorama.getHeight();
                if (w != 2 * h) {
                    throw new IllegalStateException(PANORAMAS[p] + " is not equirectangular: " + w + "x" + h);
                }
                int[] pano = panorama.getRGB(0, 0, w, h, null, 0, w);
                for (int f = 0; f < SUFFIXES.length; f++) {
                    BufferedImage face = renderFace(pano, w, h, f);
                    File out = new File(dir, PREFIXES[p] + SUFFIXES[f] + ".png");
                    ImageIO.write(face, "png", out);
                }
                System.out.println("wrote 6 faces at " + FACE_SIZE + "x" + FACE_SIZE
                        + " for " + PANORAMAS[p] + " (panorama " + w + "x" + h + ")");
            } catch (Exception e) {
                ok = false;
                System.out.println("FAIL: generating " + PANORAMAS[p] + ": " + e);
            }
        }
        ok &= checkOutputs();
        System.out.println(ok ? "EQUIRECTTOCUBE CHECK: PASS" : "EQUIRECTTOCUBE CHECK: FAIL");
        System.exit(ok ? 0 : 1);
    }

    /** Renders one cubemap face by casting the per-pixel ray into the panorama. */
    private static BufferedImage renderFace(int[] pano, int w, int h, int face) {
        BufferedImage img = new BufferedImage(FACE_SIZE, FACE_SIZE, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < FACE_SIZE; y++) {
            float v = 1f - 2f * (y + 0.5f) / FACE_SIZE; // +1 at the top row
            for (int x = 0; x < FACE_SIZE; x++) {
                float u = 2f * (x + 0.5f) / FACE_SIZE - 1f; // -1 at the left column
                img.setRGB(x, y, sampleEquirect(pano, w, h, faceDir(face, u, v)));
            }
        }
        return img;
    }

    /** Ray direction for a face pixel; (u,v) in [-1,1]^2, v=+1 is the top row. */
    private static float[] faceDir(int face, float u, float v) {
        return switch (face) {
            case 0 -> new float[] { 1f, v, -u };   // Right  +X
            case 1 -> new float[] { -1f, v, u };   // Left   -X
            case 2 -> new float[] { u, 1f, -v };   // Top    +Y
            case 3 -> new float[] { u, -1f, v };   // Bottom -Y
            case 4 -> new float[] { u, v, 1f };    // Back   +Z
            default -> new float[] { -u, v, -1f }; // Front  -Z
        };
    }

    /** Bilinear equirectangular sample of {@code dir}; returns packed 0xRRGGBB. */
    private static int sampleEquirect(int[] pano, int w, int h, float[] dir) {
        float len = (float) Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]);
        float x = dir[0] / len;
        float y = dir[1] / len;
        float z = dir[2] / len;
        float lon = (float) Math.atan2(z, x);          // [-PI, PI], 0 = +X
        float lat = (float) Math.asin(Math.max(-1f, Math.min(1f, y)));
        float s = lon / ((float) Math.PI * 2f) + 0.5f; // [0,1), wraps
        float t = 0.5f - lat / (float) Math.PI;        // 0 = zenith (top row)
        float fx = s * w - 0.5f;
        float fy = t * h - 0.5f;
        int x0 = (int) Math.floor(fx);
        int y0 = (int) Math.floor(fy);
        float wx = fx - x0;
        float wy = fy - y0;
        int x1 = (x0 + 1) % w;
        x0 = ((x0 % w) + w) % w;                       // horizontal wrap
        int y0c = Math.max(0, Math.min(h - 1, y0));    // vertical clamp
        int y1c = Math.max(0, Math.min(h - 1, y0 + 1));
        int c00 = pano[y0c * w + x0];
        int c10 = pano[y0c * w + x1];
        int c01 = pano[y1c * w + x0];
        int c11 = pano[y1c * w + x1];
        int r = Math.round(blend(c00 >> 16, c10 >> 16, c01 >> 16, c11 >> 16, wx, wy));
        int g = Math.round(blend((c00 >> 8) & 255, (c10 >> 8) & 255, (c01 >> 8) & 255, (c11 >> 8) & 255, wx, wy));
        int b = Math.round(blend(c00 & 255, c10 & 255, c01 & 255, c11 & 255, wx, wy));
        return (r << 16) | (g << 8) | b;
    }

    private static float blend(int c00, int c10, int c01, int c11, float wx, float wy) {
        return (1 - wx) * (1 - wy) * c00 + wx * (1 - wy) * c10 + (1 - wx) * wy * c01 + wx * wy * c11;
    }

    /** Re-reads all outputs and runs the self-check; returns true on PASS. */
    private static boolean checkOutputs() {
        boolean ok = true;
        int count = 0;
        int maxDiff = 0;
        for (int p = 0; p < PANORAMAS.length; p++) {
            try {
                BufferedImage panoImg = ImageIO.read(new File(ASSETS, PANORAMAS[p] + ".png"));
                int pw = panoImg.getWidth();
                int ph = panoImg.getHeight();
                int[] pano = panoImg.getRGB(0, 0, pw, ph, null, 0, pw);
                for (int f = 0; f < SUFFIXES.length; f++) {
                    File out = new File(ASSETS, PREFIXES[p] + SUFFIXES[f] + ".png");
                    if (!out.isFile()) {
                        System.out.println("FAIL: missing " + out.getName());
                        ok = false;
                        continue;
                    }
                    count++;
                    BufferedImage face = ImageIO.read(out);
                    if (face.getWidth() != FACE_SIZE || face.getHeight() != FACE_SIZE) {
                        System.out.println("FAIL: " + out.getName() + " is "
                                + face.getWidth() + "x" + face.getHeight() + ", expected "
                                + FACE_SIZE + "x" + FACE_SIZE);
                        ok = false;
                        continue;
                    }
                    // Round trip: grid pixel must equal the equirect sample of its own ray.
                    for (int y = 0; y < FACE_SIZE; y += STEP) {
                        float v = 1f - 2f * (y + 0.5f) / FACE_SIZE;
                        for (int x = 0; x < FACE_SIZE; x += STEP) {
                            float u = 2f * (x + 0.5f) / FACE_SIZE - 1f;
                            int expected = sampleEquirect(pano, pw, ph, faceDir(f, u, v));
                            int actual = face.getRGB(x, y);
                            int diff = channelDiff(expected, actual);
                            maxDiff = Math.max(maxDiff, diff);
                            if (diff > 2) {
                                System.out.println("FAIL: round-trip " + out.getName()
                                        + " at (" + x + "," + y + ") diff=" + diff);
                                ok = false;
                            }
                            if ((actual >>> 24) != 255) {
                                System.out.println("FAIL: non-opaque pixel in " + out.getName());
                                ok = false;
                            }
                        }
                    }
                    // Semantic: face centre must show the direct panorama texel of its axis.
                    int expected = directTexel(pano, pw, ph, f);
                    int actual = face.getRGB(FACE_SIZE / 2, FACE_SIZE / 2);
                    int diff = channelDiff(expected, actual);
                    maxDiff = Math.max(maxDiff, diff);
                    if (diff > 8) {
                        System.out.println("FAIL: centre mismatch " + out.getName() + " diff=" + diff);
                        ok = false;
                    }
                }
            } catch (Exception e) {
                System.out.println("FAIL: checking " + PANORAMAS[p] + ": " + e);
                ok = false;
            }
        }
        if (count != 18) {
            System.out.println("FAIL: expected 18 face files, found " + count);
            ok = false;
        }
        System.out.println("self-check: 18 faces at " + FACE_SIZE + "x" + FACE_SIZE
                + ", round-trip max channel diff = " + maxDiff);
        return ok;
    }

    /** Direct panorama texel for a face centre: +X mid, -X wrap, +Y top, -Y bottom, +Z 3/4, -Z 1/4. */
    private static int directTexel(int[] pano, int w, int h, int face) {
        int midRow = h / 2;
        switch (face) {
            case 0:
                return pano[midRow * w + w / 2];                    // +X: lon 0 -> mid column
            case 1: {
                int a = pano[midRow * w];                           // -X: lon PI -> wraps to column 0
                int b = pano[midRow * w + w - 1];
                int r = ((a >> 16) + (b >> 16)) / 2;
                int g = (((a >> 8) & 255) + ((b >> 8) & 255)) / 2;
                int bl = ((a & 255) + (b & 255)) / 2;
                return (r << 16) | (g << 8) | bl;
            }
            case 2:
                return pano[w / 2];                                 // +Y: zenith -> top row
            case 3:
                return pano[(h - 1) * w + w / 2];                   // -Y: nadir -> bottom row
            case 4:
                return pano[midRow * w + (3 * w) / 4];              // +Z: lon PI/2 -> 3/4 column
            default:
                return pano[midRow * w + w / 4];                    // -Z: lon -PI/2 -> 1/4 column
        }
    }

    private static int channelDiff(int a, int b) {
        int dr = Math.abs(((a >> 16) & 255) - ((b >> 16) & 255));
        int dg = Math.abs(((a >> 8) & 255) - ((b >> 8) & 255));
        int db = Math.abs((a & 255) - (b & 255));
        return Math.max(dr, Math.max(dg, db));
    }
}
