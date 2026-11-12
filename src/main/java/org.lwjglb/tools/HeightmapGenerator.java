package org.lwjglb.tools;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Procedural generator for the block-6a terrain of Puerto Clara.
 *
 * <p>Headless and standalone: no LWJGL/OpenGL imports, run with plain
 * {@code java org.lwjglb.tools.HeightmapGenerator} from the project root.
 * It writes {@code assets/heightMapPuerto.png} (heights) and
 * {@code assets/blendMapPuerto.png} (urban blend) plus a self-check.
 *
 * <p>Pixel encoding mirrors {@code org.lwjglb.terrains.Terrain#getHeight}:
 * the signed ARGB int of each pixel is remapped with
 * {@code (argb + MAX_PIXEL_COLOUR/2) / (MAX_PIXEL_COLOUR/2) * MAX_HEIGHT}.
 * With alpha 255 and a grey value {@code v} (R=G=B=v) the stored int is
 * {@code -16777216 + v*65793}, so Terrain decodes it to
 * {@code (v*65793 - 8388608) / 8388608 * MAX_HEIGHT} units. Grey levels are
 * therefore 80/255 = 0.3137 u apart; the play corridor (heights in
 * [0, 0.3) u) quantises to exactly grey 128 = +0.157 u, which keeps the
 * required "min >= 0" and "corridor deviation <= 0.3 u" checks true.
 *
 * <p>World mapping (ThinMatrix convention, tile gridX=0 gridZ=-1):
 * pixel column j -> world x = j/(N-1)*150, pixel row i -> world z =
 * i/(N-1)*150 - 150. Constants mirror Terrain (SIZE, MAX_HEIGHT) and
 * RunnerCore (PLAYER_X, PLAYER_Z).
 */
public final class HeightmapGenerator {

    // --- mirrors of org.lwjglb.terrains.Terrain (private there) ---
    private static final float SIZE = 150f;
    private static final float MAX_HEIGHT = 40f;
    private static final float MAX_PIXEL_COLOUR = 256f * 256f * 256f;

    // --- mirrors of org.lwjglb.game.RunnerCore world constants ---
    private static final float PLAYER_X = 75f;
    private static final float PLAYER_Z = -75f;

    private static final String ASSETS = "src/main/java/org.lwjglb/assets/";

    // --- Puerto Clara layout ---
    private static final double CORRIDOR_HALF = 40.0;     // flat for |x-75| <= 40
    private static final double HILL_TRANSITION = 30.0;   // smoothstep width to the hills
    private static final double BACK_START = 95.0;        // local z where the back hills begin
    private static final double BACK_TRANSITION = 30.0;
    private static final double CREST = 26.0;             // hill crest ~65% of MAX_HEIGHT
    private static final double UNDUL = 0.25;             // corridor undulation, non-negative, <= 0.25 u
    private static final double UNDUL_WAVELENGTH = 60.0;  // units along z

    // blend map bands (world x distances from the road centre)
    private static final double ROAD_HALF = 18.0;         // asphalt strip   -> b channel (path)
    private static final double PAVEMENT_HALF = 40.0;     // pavement band   -> r channel (mud)

    private HeightmapGenerator() {
    }

    public static void main(String[] args) {
        boolean ok;
        try {
            ok = run();
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("HEIGHTMAP CHECK: FAIL");
            System.exit(1);
            return;
        }
        System.out.println(ok ? "HEIGHTMAP CHECK: PASS" : "HEIGHTMAP CHECK: FAIL");
        System.exit(ok ? 0 : 1);
    }

    private static boolean run() throws Exception {
        // ---- reference: the class example defines size + encoding ----
        File refFile = new File(ASSETS + "heightMapLake.png");
        BufferedImage ref = ImageIO.read(refFile);
        if (ref == null) {
            System.out.println("cannot read " + refFile.getPath());
            return false;
        }
        int n = ref.getWidth();
        System.out.println("reference heightMapLake.png: " + ref.getWidth() + "x" + ref.getHeight()
                + (n == ref.getHeight() ? " (square, ok)" : " (NOT SQUARE)"));
        boolean ok = n == ref.getHeight();

        // ---- heightmap ----
        BufferedImage heightMap = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < n; i++) {                 // row  -> local z -> world z
            for (int j = 0; j < n; j++) {             // col  -> world x
                float worldX = (float) j / (n - 1) * SIZE;
                float localZ = (float) i / (n - 1) * SIZE;
                double h = heightAt(worldX, localZ);
                heightMap.setRGB(j, i, encodeGrey(h));
            }
        }
        File heightFile = new File(ASSETS + "heightMapPuerto.png");
        ImageIO.write(heightMap, "png", heightFile);

        // ---- decode every vertex exactly like Terrain does ----
        float[][] heights = new float[n][n];
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                float h = decode(heightMap.getRGB(j, i));
                heights[j][i] = h;
                min = Math.min(min, h);
                max = Math.max(max, h);
            }
        }

        // ---- corridor deviation along the exact play path ----
        // x = lanes 71/75/79, z from PLAYER_Z-70 to PLAYER_Z+10 (RunnerCore constants)
        float maxCorridorDev = 0f;
        for (float x : new float[]{PLAYER_X - 4f, PLAYER_X, PLAYER_X + 4f}) {
            for (float z = PLAYER_Z - 70f; z <= PLAYER_Z + 10f; z += 0.5f) {
                float h = sampleTerrain(heights, x, z);
                maxCorridorDev = Math.max(maxCorridorDev, Math.abs(h));
            }
        }

        System.out.println("image size: " + n + "x" + n + " -> " + heightFile.getPath());
        System.out.printf("height range: min=%.3f u  max=%.3f u%n", min, max);
        System.out.printf("corridor max deviation: %.3f u (limit 0.300) at x=71/75/79, z=-145..-65%n",
                maxCorridorDev);
        System.out.printf("hill crest max: %.3f u (must be > 5.000)%n", max);

        // ---- encoding spot-check: round-trip a few targets through grey levels ----
        double worstErr = 0;
        for (double target : new double[]{0.0, UNDUL, 13.0, CREST}) {
            int argb = encodeGrey(target);
            float back = decode(argb);
            double err = Math.abs(back - target);
            worstErr = Math.max(worstErr, err);
            System.out.printf("spot-check: target=%.3f u -> grey=%d argb=%s -> decoded=%.3f u (err=%.3f)%n",
                    target, argb & 0xFF, String.format("0x%08X", argb), back, err);
        }

        // ---- blend map ----
        BufferedImage blendMap = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                float worldX = (float) j / (n - 1) * SIZE;
                float localZ = (float) i / (n - 1) * SIZE;
                blendMap.setRGB(j, i, blendColour(worldX, localZ));
            }
        }
        File blendFile = new File(ASSETS + "blendMapPuerto.png");
        ImageIO.write(blendMap, "png", blendFile);
        System.out.println("image size: " + n + "x" + n + " -> " + blendFile.getPath());
        System.out.printf("blend spot-check road(x=75,z=-110)=%s pavement(x=45,z=-110)=%s "
                        + "grass(x=10,z=-110)=%s back(x=10,z=-15)=%s%n",
                hex(blendColour(75f, 40f)), hex(blendColour(45f, 40f)),
                hex(blendColour(10f, 40f)), hex(blendColour(10f, 135f)));

        // ---- verdict ----
        boolean sizeOk = (n == ref.getHeight());
        boolean corridorOk = maxCorridorDev <= 0.3f;
        boolean crestOk = max > 5f;
        boolean minOk = min >= 0f;
        boolean roundTripOk = worstErr <= 0.16;
        boolean blendOk = blendColour(75f, 40f) == 0xFF0000FF
                && blendColour(45f, 40f) == 0xFFFF0000
                && blendColour(10f, 40f) == 0xFF00FF00
                && blendColour(10f, 135f) == 0xFF000000;
        System.out.printf("checks: size=%s corridor=%s crest=%s min=%s roundtrip=%s blend=%s%n",
                mark(sizeOk), mark(corridorOk), mark(crestOk), mark(minOk),
                mark(roundTripOk), mark(blendOk));
        return ok && sizeOk && corridorOk && crestOk && minOk && roundTripOk && blendOk;
    }

    private static String mark(boolean b) {
        return b ? "OK" : "BAD";
    }

    private static String hex(int argb) {
        return String.format("0x%08X", argb);
    }

    // ------------------------------------------------------------------
    // Height field
    // ------------------------------------------------------------------

    /** Smooth 0->1 ramp between e0 and e1 (Hermite). */
    private static double smoothstep(double e0, double e1, double x) {
        if (x <= e0) {
            return 0.0;
        }
        if (x >= e1) {
            return 1.0;
        }
        double t = (x - e0) / (e1 - e0);
        return t * t * (3.0 - 2.0 * t);
    }

    /**
     * Deterministic rolling-hill noise: 3 sine octaves, coefficients sum to
     * 1.0 so the result stays within [-1, 1]. No randomness -> the PNG is
     * byte-identical on every run.
     */
    private static double hillNoise(double x, double z) {
        return 0.55 * Math.sin(x * 0.085 + 1.3) * Math.cos(z * 0.075 + 0.4)
                + 0.30 * Math.sin(x * 0.19 + z * 0.16 + 2.1)
                + 0.15 * Math.sin(x * 0.36 - z * 0.31 + 0.9);
    }

    /**
     * Puerto Clara relief: flat play corridor (|x-75| <= 40) with a gentle
     * non-negative sinusoidal undulation along z (amplitude 0.25 u, never
     * below 0), rising through a 30 u smoothstep into rolling city-edge
     * hills cresting at CREST = 26 u. The back of the tile (local z >= 95,
     * behind the camera) is hilly across the full width.
     */
    private static double heightAt(double worldX, double localZ) {
        double dx = Math.abs(worldX - PLAYER_X);
        double side = smoothstep(CORRIDOR_HALF, CORRIDOR_HALF + HILL_TRANSITION, dx);
        double back = smoothstep(BACK_START, BACK_START + BACK_TRANSITION, localZ);
        double hillMask = Math.max(side, back);

        double phase = 2.0 * Math.PI * (localZ - (PLAYER_Z + SIZE)) / UNDUL_WAVELENGTH + Math.PI / 2.0;
        double undulation = UNDUL * (0.5 + 0.5 * Math.sin(phase));   // in [0, 0.25]
        double hills = CREST * (0.5 + 0.5 * hillNoise(worldX, localZ)); // in [0, 26]

        return undulation * (1.0 - hillMask) + hills * hillMask;
    }

    // ------------------------------------------------------------------
    // Encoding (mirror of Terrain.getHeight, inverted)
    // ------------------------------------------------------------------

    /**
     * Grey value for height {@code h}: solve Terrain's decode formula for
     * R=G=B=v with alpha 255 (argb = -16777216 + v*65793), clamp to the
     * valid grey range. Result stays inside Terrain's +/- MAX_HEIGHT band.
     */
    private static int encodeGrey(double h) {
        double vExact = (h / MAX_HEIGHT + 1.0) * (MAX_PIXEL_COLOUR / 2.0) / 65793.0;
        int v = (int) Math.round(vExact);
        v = Math.max(0, Math.min(255, v));
        return 0xFF000000 | (v << 16) | (v << 8) | v;
    }

    /** Byte-for-byte the formula in Terrain.getHeight (float ops included). */
    private static float decode(int argb) {
        float height = argb;
        height += MAX_PIXEL_COLOUR / 2f;
        height /= MAX_PIXEL_COLOUR / 2f;
        height *= MAX_HEIGHT;
        return height;
    }

    // ------------------------------------------------------------------
    // Faithful re-implementation of Terrain.getHeightOfTerrain (read-only)
    // ------------------------------------------------------------------

    private static float sampleTerrain(float[][] heights, float worldX, float worldZ) {
        float terrainX = worldX - 0f;            // tile gridX = 0  -> this.x = 0
        float terrainZ = worldZ - (-SIZE);       // tile gridZ = -1 -> this.z = -150
        float gridSquareSize = SIZE / ((float) heights.length - 1);
        int gridX = (int) Math.floor(terrainX / gridSquareSize);
        int gridZ = (int) Math.floor(terrainZ / gridSquareSize);
        if (gridX >= heights.length - 1 || gridZ >= heights.length - 1 || gridX < 0 || gridZ < 0) {
            return 0;
        }
        float xCoord = (terrainX % gridSquareSize) / gridSquareSize;
        float zCoord = (terrainZ % gridSquareSize) / gridSquareSize;
        float answer;
        if (xCoord <= (1 - zCoord)) {
            answer = baryCentric(
                    0, heights[gridX][gridZ], 0,
                    1, heights[gridX + 1][gridZ], 0,
                    0, heights[gridX][gridZ + 1], 1,
                    xCoord, zCoord);
        } else {
            answer = baryCentric(
                    1, heights[gridX + 1][gridZ], 0,
                    1, heights[gridX + 1][gridZ + 1], 1,
                    0, heights[gridX][gridZ + 1], 1,
                    xCoord, zCoord);
        }
        return answer;
    }

    /** Same math as Terrain.baryCentric (p1y/p2y/p3y are the heights). */
    private static float baryCentric(float p1x, float p1y, float p1z,
                                     float p2x, float p2y, float p2z,
                                     float p3x, float p3y, float p3z,
                                     float posX, float posY) {
        float det = (p2z - p3z) * (p1x - p3x) + (p3x - p2x) * (p1z - p3z);
        float l1 = ((p2z - p3z) * (posX - p3x) + (p3x - p2x) * (posY - p3z)) / det;
        float l2 = ((p3z - p1z) * (posX - p3x) + (p1x - p3x) * (posY - p3z)) / det;
        float l3 = 1.0f - l1 - l2;
        return l1 * p1y + l2 * p2y + l3 * p3y;
    }

    // ------------------------------------------------------------------
    // Urban blend map (channel semantics of terrainFragmentShader.txt:
    // r -> rTexture (mud/pavement), g -> gTexture (grass),
    // b -> bTexture (path/road), r=g=b=0 -> backgroundTexture (grassy))
    // ------------------------------------------------------------------

    private static int blendColour(float worldX, float localZ) {
        double dx = Math.abs(worldX - PLAYER_X);
        int rgb;
        if (dx <= ROAD_HALF) {
            rgb = 0x0000FF;                          // road    -> path (b)
        } else if (dx <= PAVEMENT_HALF) {
            rgb = 0xFF0000;                          // pavement -> mud  (r)
        } else if (localZ >= BACK_START) {
            rgb = 0x000000;                          // back    -> grassy background
        } else {
            rgb = 0x00FF00;                          // sides   -> grass (g)
        }
        return 0xFF000000 | rgb;
    }
}
