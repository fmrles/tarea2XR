package org.lwjglb.renderEngine;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjglb.entities.Camera;
import org.lwjglb.entities.Entity;
import org.lwjglb.entities.Light;
import org.lwjglb.models.TexturedModel;
import org.lwjglb.normalMappingRenderer.NormalMappingRenderer;
import org.lwjglb.shaders.StaticShader;
import org.lwjglb.shaders.TerrainShader;
import org.lwjglb.skybox.SkyboxRenderer;
import org.lwjglb.terrains.Terrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

public class MasterRenderer {

    private static final float FOV = 70;
    private static final float NEAR_PLANE = 0.1f;
    private static final float FAR_PLANE = 1000;

    public static final float RED = 0.3f;
    public static final float GREEN = 0.2f;
    public static final float BLUE = 0.8f;

    private static final float FOG_A_R = 0.96f, FOG_A_G = 0.79f, FOG_A_B = 0.70f;
    private static final float FOG_B_R = 0.65f, FOG_B_G = 0.72f, FOG_B_B = 0.95f;
    private static final float FOG_C_R = 0.15f, FOG_C_G = 0.19f, FOG_C_B = 0.36f;

    private static final float ZONE_FADE_SECONDS = 1.2f;

    private final float[] fogFrom = new float[3];
    private final float[] fogTo = new float[3];

    private Matrix4f projectionMatrix;

    private EntityRenderer entityRenderer;
    private StaticShader staticShader = new StaticShader();

    private TerrainRenderer terrainRenderer;
    private TerrainShader terrainShader = new TerrainShader();

    private NormalMappingRenderer normalMapRenderer;

    private Map<TexturedModel,List<Entity>> entities = new HashMap<TexturedModel,List<Entity>>();
    private Map<TexturedModel,List<Entity>> normalMapEntities = new HashMap<TexturedModel,List<Entity>>();
    private List<Terrain> terrains = new ArrayList<Terrain>();

    private SkyboxRenderer skyboxRenderer;

    private float fogR = FOG_A_R;
    private float fogG = FOG_A_G;
    private float fogB = FOG_A_B;

    private int zoneFrom = 0;
    private int zoneTo = 0;
    private float zoneBlend = 1f;

    public void setZone(int zone) {
        if (zone == zoneTo) {
            return;
        }
        zoneFrom = zoneTo;
        zoneTo = zone;
        zoneBlend = 0f;
        System.out.printf(Locale.ROOT, "SKYBOX FADE zone %d->%d blend %.2f (%.1fs)%n",
                zoneFrom, zoneTo, zoneBlend, ZONE_FADE_SECONDS);
    }

    private void updateZoneFade() {
        zoneBlend = Math.min(1f,
                zoneBlend + DisplayManager.getFrameTimeSeconds() / ZONE_FADE_SECONDS);
        fogOfZone(zoneFrom, fogFrom);
        fogOfZone(zoneTo, fogTo);
        fogR = fogFrom[0] + (fogTo[0] - fogFrom[0]) * zoneBlend;
        fogG = fogFrom[1] + (fogTo[1] - fogFrom[1]) * zoneBlend;
        fogB = fogFrom[2] + (fogTo[2] - fogFrom[2]) * zoneBlend;
    }

    private static void fogOfZone(int zone, float[] out) {
        switch (zone) {
            case 1 -> { out[0] = FOG_B_R; out[1] = FOG_B_G; out[2] = FOG_B_B; }
            case 2 -> { out[0] = FOG_C_R; out[1] = FOG_C_G; out[2] = FOG_C_B; }
            default -> { out[0] = FOG_A_R; out[1] = FOG_A_G; out[2] = FOG_A_B; }
        }
    }

    public MasterRenderer(Loader loader){
        enableCulling();
        createProjectionMatrix();
        entityRenderer = new EntityRenderer(staticShader,projectionMatrix);
        terrainRenderer = new TerrainRenderer(terrainShader,projectionMatrix);
        normalMapRenderer = new NormalMappingRenderer(projectionMatrix);
        skyboxRenderer = new SkyboxRenderer(loader, projectionMatrix);
    }

    public Matrix4f getProjectionMatrix() {
        return projectionMatrix;
    }

    public void renderScene(
            List<Entity> entities,
            List<Entity> normalEntities,
            List<Terrain> terrains,
            List<Light> lights,
            Camera camera,
            Vector4f clipPlane) {
        for (Terrain terrain : terrains) {
            processTerrain(terrain);
        }
        for (Entity entity : entities) {
            processEntity(entity);
        }
        for(Entity entity : normalEntities) {
            processNormalMapEntity(entity);
        }
        render(lights, camera, clipPlane);
    }

    public static void enableCulling() {
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glCullFace(GL11.GL_BACK);
    }

    public static void disableCulling() {
        GL11.glDisable(GL11.GL_CULL_FACE);
    }

    public void render(List<Light> lights,Camera camera, Vector4f clipPlane){
        updateZoneFade();
        prepare();
        staticShader.start();
        staticShader.loadClipPlane(clipPlane);
        staticShader.loadSkyColour(fogR, fogG, fogB);
        staticShader.loadLights(lights);
        staticShader.loadViewMatrix(camera);
        entityRenderer.render(entities);
        staticShader.stop();
        normalMapRenderer.render(normalMapEntities, clipPlane, lights, camera, fogR, fogG, fogB);
        terrainShader.start();
        terrainShader.loadClipPlane(clipPlane);
        terrainShader.loadSkyColour(fogR, fogG, fogB);
        terrainShader.loadLights(lights);
        terrainShader.loadViewMatrix(camera);
        terrainRenderer.render(terrains);
        terrainShader.stop();
        skyboxRenderer.render(camera, fogR, fogG, fogB, zoneFrom, zoneTo, zoneBlend);
        terrains.clear();
        entities.clear();
        normalMapEntities.clear();
    }

    public void processTerrain(Terrain terrain){
        terrains.add(terrain);
    }

    public void processEntity(Entity entity){
        TexturedModel entityModel = entity.getModel();
        List<Entity> batch = entities.get(entityModel);
        if (batch!=null) {
            batch.add(entity);
        } else {
            List<Entity> newBatch = new ArrayList<Entity>();
            newBatch.add(entity);
            entities.put(entityModel, newBatch);
        }
    }

    public void processNormalMapEntity(Entity entity){
        TexturedModel entityModel = entity.getModel();
        List<Entity> batch = normalMapEntities.get(entityModel);
        if(batch!=null){
            batch.add(entity);
        }else{
            List<Entity> newBatch = new ArrayList<Entity>();
            newBatch.add(entity);
            normalMapEntities.put(entityModel, newBatch);
        }
    }

    public void cleanUp(){
        staticShader.cleanUp();
        terrainShader.cleanUp();
        normalMapRenderer.cleanUp();
    }

    public void prepare() {
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glClearColor(fogR, fogG, fogB, 1);
    }

	private void createProjectionMatrix() {
        float aspectRatio = (float) DisplayManager.getWindowWidth() / (float) DisplayManager.getWindowHeight();
        projectionMatrix = new Matrix4f().perspective(
                (float) Math.toRadians(FOV),
                aspectRatio,
                NEAR_PLANE,
                FAR_PLANE
        );

    }

    public StaticShader getShader() {
        return staticShader;
    }
}
