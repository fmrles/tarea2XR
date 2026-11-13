package org.lwjglb.tools;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

public final class HeightmapGenerator {

    private static final float SIZE = 150f;
    private static final float MAX_HEIGHT = 40f;
    private static final float MAX_PIXEL_COLOUR = 256f * 256f * 256f;

    private static final float PLAYER_X = 75f;
    private static final float PLAYER_Z = -75f;

    private static final String ASSETS = "src/main/java/org.lwjglb/assets/";

    private static final double CORRIDOR_HALF = 40.0;     
    private static final double HILL_TRANSITION = 30.0;   
    private static final double BACK_START = 95.0;        
    private static final double BACK_TRANSITION = 30.0;
    private static final double CREST = 26.0;             
    private static final double UNDUL = 0.25;             
    private static final double UNDUL_WAVELENGTH = 60.0;  

    private static final double ROAD_HALF = 18.0;         
    private static final double PAVEMENT_HALF = 40.0;     

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

        BufferedImage heightMap = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < n; i++) {                 
            for (int j = 0; j < n; j++) {             
                float worldX = (float) j / (n - 1) * SIZE;
                float localZ = (float) i / (n - 1) * SIZE;
                double h = heightAt(worldX, localZ);
                heightMap.setRGB(j, i, encodeGrey(h));
            }
        }
        File heightFile = new File(ASSETS + "heightMapPuerto.png");
        ImageIO.write(heightMap, "png", heightFile);

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

        double worstErr = 0;
        for (double target : new double[]{0.0, UNDUL, 13.0, CREST}) {
            int argb = encodeGrey(target);
            float back = decode(argb);
            double err = Math.abs(back - target);
            worstErr = Math.max(worstErr, err);
            System.out.printf("spot-check: target=%.3f u -> grey=%d argb=%s -> decoded=%.3f u (err=%.3f)%n",
                    target, argb & 0xFF, String.format("0x%08X", argb), back, err);
        }

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

    private static double hillNoise(double x, double z) {
        return 0.55 * Math.sin(x * 0.085 + 1.3) * Math.cos(z * 0.075 + 0.4)
                + 0.30 * Math.sin(x * 0.19 + z * 0.16 + 2.1)
                + 0.15 * Math.sin(x * 0.36 - z * 0.31 + 0.9);
    }

    private static double heightAt(double worldX, double localZ) {
        double dx = Math.abs(worldX - PLAYER_X);
        double side = smoothstep(CORRIDOR_HALF, CORRIDOR_HALF + HILL_TRANSITION, dx);
        double back = smoothstep(BACK_START, BACK_START + BACK_TRANSITION, localZ);
        double hillMask = Math.max(side, back);

        double phase = 2.0 * Math.PI * (localZ - (PLAYER_Z + SIZE)) / UNDUL_WAVELENGTH + Math.PI / 2.0;
        double undulation = UNDUL * (0.5 + 0.5 * Math.sin(phase));   
        double hills = CREST * (0.5 + 0.5 * hillNoise(worldX, localZ)); 

        return undulation * (1.0 - hillMask) + hills * hillMask;
    }


    private static int encodeGrey(double h) {
        double vExact = (h / MAX_HEIGHT + 1.0) * (MAX_PIXEL_COLOUR / 2.0) / 65793.0;
        int v = (int) Math.round(vExact);
        v = Math.max(0, Math.min(255, v));
        return 0xFF000000 | (v << 16) | (v << 8) | v;
    }

    private static float decode(int argb) {
        float height = argb;
        height += MAX_PIXEL_COLOUR / 2f;
        height /= MAX_PIXEL_COLOUR / 2f;
        height *= MAX_HEIGHT;
        return height;
    }


    private static float sampleTerrain(float[][] heights, float worldX, float worldZ) {
        float terrainX = worldX - 0f;            
        float terrainZ = worldZ - (-SIZE);       
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


    private static int blendColour(float worldX, float localZ) {
        double dx = Math.abs(worldX - PLAYER_X);
        int rgb;
        if (dx <= ROAD_HALF) {
            rgb = 0x0000FF;                          
        } else if (dx <= PAVEMENT_HALF) {
            rgb = 0xFF0000;                          
        } else if (localZ >= BACK_START) {
            rgb = 0x000000;                          
        } else {
            rgb = 0x00FF00;                          
        }
        return 0xFF000000 | rgb;
    }
}
