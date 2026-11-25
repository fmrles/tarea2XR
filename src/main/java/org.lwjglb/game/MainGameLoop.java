package org.lwjglb.game;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjglb.entities.Camera;
import org.lwjglb.entities.Entity;
import org.lwjglb.entities.Light;
import org.lwjglb.entities.Player;
import org.lwjglb.fontMeshCreator.FontType;
import org.lwjglb.fontRendering.TextMaster;
import org.lwjglb.glb.GlbModel;
import org.lwjglb.models.RawModel;
import org.lwjglb.models.TexturedModel;
import org.lwjglb.normalMappingObjConverter.NormalMappedObjLoader;
import org.lwjglb.particles.ParticleMaster;
import org.lwjglb.particles.ParticleTexture;
import org.lwjglb.renderEngine.DisplayManager;
import org.lwjglb.renderEngine.Loader;
import org.lwjglb.renderEngine.MasterRenderer;
import org.lwjglb.terrains.Terrain;
import org.lwjglb.textures.ModelTexture;
import org.lwjglb.textures.TerrainTexture;
import org.lwjglb.textures.TerrainTexturePack;
import org.lwjglb.util.Config;
import org.lwjglb.util.Maths;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;

import static org.lwjgl.glfw.GLFW.*;

public class MainGameLoop {

    private static final String RAT_GLB_DIR = "src/main/resources/models/";

    private static final float PLAYER_BASE_SCALE = 0.16f;

    private static final float RAT_YAW_DEG = 180f;

    private static final float RAT_TARGET_HEIGHT_U = 1.70f;

    private static final float WELCOME_OFFSET_X_U = 3.4f;  
    private static final float WELCOME_OFFSET_Z_U = 2.2f;   

    private static final float WELCOME_YAW_OFFSET_DEG = 180f;

    private static final float TREE_QUAD_U = 6f;

    private static final Vector3f CHEESE_TINT = new Vector3f(0.90f, 0.50f, 0.00f);

    private static final Vector3f SMOKE_TINT = new Vector3f(0.60f, 0.60f, 0.60f);

    public static void main(String[] args) {
        DisplayManager.createDisplay();
        Loader loader = new Loader();
        long assetLoadStart = System.nanoTime();   
        TextMaster.init(loader);
        MasterRenderer masterRenderer = new MasterRenderer(loader);
        MasterRenderer.disableCulling();

        ParticleMaster.init(loader, masterRenderer.getProjectionMatrix());

        List<Light> lights = new ArrayList<>();
        lights.add(new Light(new Vector3f(10000, 10000, 10000), new Vector3f(1, 1, 1)));

        TerrainTexture backgroundTexture = new TerrainTexture(loader.loadTexture("grassy"));
        TerrainTexture rTexture = new TerrainTexture(loader.loadTexture("mud"));
        TerrainTexture gTexture = new TerrainTexture(loader.loadTexture("grass"));
        TerrainTexture bTexture = new TerrainTexture(loader.loadTexture("road"));
        TerrainTexturePack texturePack = new TerrainTexturePack(backgroundTexture, rTexture, gTexture, bTexture);
        TerrainTexture blendMap = new TerrainTexture(loader.loadTexture("blendMapPuerto"));
        Terrain terrain = new Terrain(0, -1, loader, texturePack, blendMap, "heightMapPuerto");
        List<Terrain> terrains = new ArrayList<>();
        terrains.add(terrain);

        TexturedModel playerModel = loader.createTexturedModel("person", "playerTexture", 10, 1);
        Player player = new Player(playerModel, new Vector3f(RunnerCore.PLAYER_X, 0, RunnerCore.PLAYER_Z),
                0, 180, 0, PLAYER_BASE_SCALE);

        String[] ratKeys = {"run", "jump", "slide", "knock", "dead", "dance"};
        String[] ratFiles = {"ratRunning.glb", "ratJump.glb", "ratSlide.glb",
                "ratKnock.glb", "ratDead.glb", "ratDance.glb"};
        String[][] ratKeywords = {{"run"}, {"jump"}, {"slide"},
                {"knock", "down"}, {"dead", "die"}, {"dance", "shake"}};

        RunnerCore.AnimIntent[] ratIntents = RunnerCore.AnimIntent.values();
        GlbModel[] ratByIntent = new GlbModel[ratIntents.length];
        float[] ratScaleByIntent = new float[ratIntents.length];

        long ratLoadStart = System.nanoTime();
        for (int i = 0; i < ratIntents.length; i++) {
            String ratPath = RAT_GLB_DIR + ratFiles[i];
            GlbModel model;
            try {
                model = GlbModel.load(ratPath);
            } catch (IOException e) {
                throw new RuntimeException("No fue posible cargar el modelo GLB usando Assimp: "
                        + ratPath, e);
            }
            model.crearShader();

            String[] anims = model.getAnimationNames();
            int selected = selectClipIndex(anims, ratKeywords[i]);
            model.setActiveAnimationIndex(selected);

            boolean oneShot = ratIntents[i] == RunnerCore.AnimIntent.KNOCK
                    || ratIntents[i] == RunnerCore.AnimIntent.DEAD;
            model.setLooping(!oneShot);

            org.joml.Vector3f bounds = model.getRestPoseSize();
            float scale = targetHeightScale(bounds);

            ratByIntent[i] = model;
            ratScaleByIntent[i] = scale;

            System.out.printf("rat[%s]: animations=%s selected='%s' (%d) height=%.2fu bounds=%.3fx%.3fx%.3f recommendedScale=%.4f scale=%.4f bones=%d meshes=%d%n",
                    ratKeys[i], Arrays.toString(anims), anims[selected], selected,
                    bounds.y * scale, bounds.x, bounds.y, bounds.z,
                    model.getRecommendedScale(), scale, model.getBoneCount(), model.getMeshCount());
        }
        long ratLoadMs = (System.nanoTime() - ratLoadStart) / 1_000_000L;
        System.out.printf("rat: loaded %d models in %d ms%n", ratIntents.length, ratLoadMs);

        String[] welcomeKeys = {"white", "brown"};
        String[] welcomeFiles = {"whiteRat.glb", "brownRat.glb"};
        String[][] welcomeKeywords = {{"dance", "funny"}, {"dance", "funny"}};
        GlbModel[] welcomeModels = new GlbModel[welcomeFiles.length];
        float[] welcomeScales = new float[welcomeFiles.length];
        String[] welcomeClips = new String[welcomeFiles.length];
        for (int i = 0; i < welcomeFiles.length; i++) {
            String welcomePath = RAT_GLB_DIR + welcomeFiles[i];
            GlbModel model;
            try {
                model = GlbModel.load(welcomePath);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load GLB model via Assimp: "
                        + welcomePath, e);
            }
            model.crearShader();

            String[] anims = model.getAnimationNames();
            int selected = selectClipIndex(anims, welcomeKeywords[i]);
            model.setActiveAnimationIndex(selected);
            model.setLooping(true);   

            org.joml.Vector3f bounds = model.getRestPoseSize();
            float scale = targetHeightScale(bounds);

            welcomeModels[i] = model;
            welcomeScales[i] = scale;
            welcomeClips[i] = anims[selected];

            System.out.printf("rat[%s]: animations=%s selected='%s' (%d) height=%.2fu bounds=%.3fx%.3fx%.3f recommendedScale=%.4f scale=%.4f bones=%d meshes=%d%n",
                    welcomeKeys[i], Arrays.toString(anims), anims[selected], selected,
                    bounds.y * scale, bounds.x, bounds.y, bounds.z,
                    model.getRecommendedScale(), scale, model.getBoneCount(), model.getMeshCount());
        }

        GlbModel ratDance = ratByIntent[RunnerCore.AnimIntent.DANCE.ordinal()];
        GlbModel ratKnock = ratByIntent[RunnerCore.AnimIntent.KNOCK.ordinal()];
        GlbModel ratDead = ratByIntent[RunnerCore.AnimIntent.DEAD.ordinal()];
        GlbModel[] ratLoopModels = {
                ratByIntent[RunnerCore.AnimIntent.RUN.ordinal()],
                ratByIntent[RunnerCore.AnimIntent.JUMP.ordinal()],
                ratByIntent[RunnerCore.AnimIntent.SLIDE.ordinal()],
                ratDance};
        RunnerCore.AnimIntent lastAnimIntent = RunnerCore.AnimIntent.RUN;

        TexturedModel obstacleModel;
        TexturedModel carYellowModel;
        TexturedModel carCyanModel;
        {
            RawModel raw = NormalMappedObjLoader.loadOBJ("crate", loader);
            ModelTexture texture = new ModelTexture(loader.loadTexture("crate"));
            texture.setShineDamper(10f);
            texture.setReflectivity(0.5f);
            texture.setNormalMap(loader.loadTexture("crateNormal"));
            obstacleModel = new TexturedModel(raw, texture);
        }
        {
            RawModel raw = NormalMappedObjLoader.loadOBJ("carYellow/carYellow", loader);
            ModelTexture texture = new ModelTexture(loader.loadTexture("carYellow/carYellow"));
            texture.setShineDamper(10f);
            texture.setReflectivity(0.5f);
            texture.setNormalMap(loader.loadTexture("carYellow/Ynormal"));
            carYellowModel = new TexturedModel(raw, texture);
        }
        {
            RawModel raw = NormalMappedObjLoader.loadOBJ("carCyan/carCyan", loader);
            ModelTexture texture = new ModelTexture(loader.loadTexture("carCyan/carCyan"));
            texture.setShineDamper(10f);
            texture.setReflectivity(0.5f);
            texture.setNormalMap(loader.loadTexture("carCyan/Cyannormal"));
            carCyanModel = new TexturedModel(raw, texture);
        }
        System.out.printf("obstacle[carYellow]: tris=%d bounds=%.6fx%.6fx%.6fu scale=%.2f baseY=%.6f texId=%d%n",
                carYellowModel.getRawModel().getVertexCount() / 3,
                1.902363f, 0.580190f, 0.879636f,
                RunnerCore.CAR_OBSTACLE_SCALE[0], RunnerCore.CAR_OBSTACLE_BASE_Y[0],
                carYellowModel.getTexture().getID());
        System.out.printf("obstacle[carCyan]: tris=%d bounds=%.6fx%.6fx%.6fu scale=%.2f baseY=%.6f texId=%d%n",
                carCyanModel.getRawModel().getVertexCount() / 3,
                1.892671f, 0.567506f, 0.958436f,
                RunnerCore.CAR_OBSTACLE_SCALE[1], RunnerCore.CAR_OBSTACLE_BASE_Y[1],
                carCyanModel.getTexture().getID());
        System.out.println("normal-maps: crate=crateNormal yellow=Ynormal cyan=Cyannormal (block 17b)");
        TexturedModel canModel = loader.createTexturedModel("cheese/cheese", "cheese/cheese", 10, 0.5f);
        System.out.printf("cheese: tris=%d bounds=%.6fx%.6fx%.6fu scale=%.5f center=%.4f texId=%d%n",
                canModel.getRawModel().getVertexCount() / 3,
                1.064413f, 1.148455f, 1.902227f,
                RunnerCore.CAN_SCALE, RunnerCore.CAN_CENTER_Y,
                canModel.getTexture().getID());
        float[] burrowPositions = {
                -0.5f, 0f, -0.5f, 0.5f, 0f, -0.5f, 0.5f, 0f, 0.5f, -0.5f, 0f, 0.5f};
        float[] burrowUvs = {0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f};
        float[] burrowNormals = {
                0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f};
        int[] burrowIndices = {0, 2, 1, 0, 3, 2};
        RawModel burrowQuad = loader.loadToVAO(
                burrowPositions,
                burrowUvs,
                burrowNormals,
                burrowIndices);
        ModelTexture burrowTexture = new ModelTexture(loader.loadTexture("burrow"));
        burrowTexture.setShineDamper(10f);
        burrowTexture.setReflectivity(0.5f);
        TexturedModel goalModel = new TexturedModel(burrowQuad, burrowTexture);
        System.out.printf("goal[burrow]: verts=%d idx=%d quad=%.1fu tex=burrow.png texId=%d%n",
                burrowPositions.length / 3, goalModel.getRawModel().getVertexCount(),
                RunnerCore.GOAL_HOLE_U, burrowTexture.getID());
        System.out.printf("welcome-rats: white=%s brown=%s scale=%.4f/%.4f offset=±%.1fu/%.1fu yaw=%.0f (block 15b)%n",
                welcomeClips[0], welcomeClips[1], welcomeScales[0], welcomeScales[1],
                WELCOME_OFFSET_X_U, WELCOME_OFFSET_Z_U,
                RAT_YAW_DEG + WELCOME_YAW_OFFSET_DEG);
        TexturedModel lampModel = loader.createTexturedModel("StreetLight", "StreetLight", 10, 0.5f);
        TexturedModel carModel = loader.createTexturedModel("rustedCar", "rustedCar", 10, 0.5f);
        TexturedModel trashModel = loader.createTexturedModel("trash", "trash", 10, 0.5f);
        TexturedModel obeliskModel = loader.createTexturedModel("obelisk", "obelisk", 10, 0.5f);

        String[] houseNames = {"houseA", "houseB", "houseC"};
        List<TexturedModel> houseModels = new ArrayList<>(houseNames.length);
        for (int s = 0; s < houseNames.length; s++) {
            String name = houseNames[s];
            TexturedModel model = loader.createTexturedModel(name + "/" + name, name + "/" + name, 10, 0.5f);
            houseModels.add(model);
            int tris = model.getRawModel().getVertexCount() / 3;
            float scale = TrackBuilder.HOUSE_SCALE[s];
            float along = TrackBuilder.HOUSE_MODEL_DX[s] * scale;                 
            float across = (TrackBuilder.HOUSE_MODEL_DZ_FRONT[s]                 
                    + TrackBuilder.HOUSE_MODEL_DZ_BACK[s]) * scale;
            float high = TrackBuilder.HOUSE_MODEL_DY[s] * scale;                  
            System.out.printf("house[%s]: tris=%d bounds=%.2fx%.2fx%.2fu (along x across x high) scale=%.4f baseY=%.6f%n",
                    name, tris, along, across, high, scale, TrackBuilder.HOUSE_MODEL_Y_MIN[s]);
        }

        RawModel treeQuad = loader.loadToVAO(
                new float[]{
                        -0.5f, 0f, 0f, 0.5f, 0f, 0f, 0.5f, 1f, 0f, -0.5f, 1f, 0f,   
                        0f, 0f, -0.5f, 0f, 0f, 0.5f, 0f, 1f, 0.5f, 0f, 1f, -0.5f}, 
                new float[]{0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f,
                        0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f},
                new float[]{0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f,
                        0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f},
                new int[]{0, 1, 2, 0, 2, 3, 4, 5, 6, 4, 6, 7});
        TexturedModel[] treeModels = new TexturedModel[2];
        for (int t = 0; t < 2; t++) {
            String cutName = "arbol" + (t + 1) + "_cut";
            ModelTexture texture = new ModelTexture(loader.loadTexture(cutName));
            texture.setShineDamper(10f);
            texture.setReflectivity(0.5f);
            treeModels[t] = new TexturedModel(treeQuad, texture);
            CutoutStats stats;
            try {
                stats = cutoutStats(cutName);
            } catch (IOException e) {
                throw new RuntimeException("cannot read " + cutName + ".png", e);
            }
            System.out.printf("tree[%d]: cutout=%.1f%% quad=%.1fx%.1fu visible=%.1fu tris=4%n",
                    t + 1, stats.removedPct(), TREE_QUAD_U, TREE_QUAD_U, stats.visibleU());
        }
        long assetLoadMs = (System.nanoTime() - assetLoadStart) / 1_000_000L;
        System.out.printf("assets: total load time %d ms%n", assetLoadMs);

        FontType font = new FontType(loader.loadTexture("candara"),
                new File(new Config().getPath() + "assets\\candara.fnt"));

        Camera camera = new Camera(player);
        GameInput input = new GameInput();
        ParticleTexture sparkTexture = new ParticleTexture(loader.loadTexture("dirt_02"), 1, CHEESE_TINT);
        ParticleTexture smokeTexture = new ParticleTexture(loader.loadTexture("smoke_10"), 1, SMOKE_TINT);
        RunnerCore core = new RunnerCore(player, obstacleModel, carYellowModel, carCyanModel,
                canModel, goalModel,
                houseModels, treeModels[0], treeModels[1], lampModel, carModel, trashModel,
                obeliskModel, terrain, font, sparkTexture, smokeTexture);
        System.out.println(core.describeLayout());

        float debugAccum = 0f;
        int debugFrames = 0;

        while (!glfwWindowShouldClose(DisplayManager.window)) {
            if (glfwGetKey(DisplayManager.window, GLFW_KEY_ESCAPE) == GLFW_PRESS) {
                glfwSetWindowShouldClose(DisplayManager.window, true);
            }

            float dt = DisplayManager.getFrameTimeSeconds();
            input.poll(dt);
            core.update(dt, input);
            RunnerCore.AnimIntent animIntent = core.getAnimIntent();
            camera.move();
            ParticleMaster.update(camera);

            debugAccum += dt;
            debugFrames++;
            if (debugAccum >= 1f) {
                System.out.printf("t=%.1fs dt=%.4f fps=%d time=%.1f travel=%.0f obs=%d cans=%d texts=%d laneX=%.1f jump=%.2f player=%s cam=%s state=%s anim=%s zone=%d spd=%.0f spark=%d impt=%d gnd=%.2f%n",
                        debugAccum, dt / debugFrames, Math.round(debugFrames / debugAccum),
                        core.getTimeLeft(), core.getTravelled(), core.getObstacleCount(),
                        core.getCans(), TextMaster.getTextCount(),
                        core.getLaneX(), core.getJumpHeight(), player.getPosition(), camera.getPosition(),
                        core.getState(), animIntent,
                        TrackBuilder.zoneAt(core.getTravelled()), TrackBuilder.speedAt(core.getTravelled()),
                        core.getSparkCount(), core.getImpactCount(), core.getGroundHeight());
                debugAccum = 0f;
                debugFrames = 0;
            }

            masterRenderer.setZone(core.getZone());
            masterRenderer.renderScene(
                    core.getDrawables(),
                    core.getNormalMapEntities(),
                    terrains,
                    lights,
                    camera,
                    new Vector4f(0, -1, 0, 100000));

            for (GlbModel ratLoop : ratLoopModels) {
                ratLoop.update(dt);
            }
            for (GlbModel welcome : welcomeModels) {
                welcome.update(dt);
            }
            if (animIntent != lastAnimIntent) {
                if (animIntent == RunnerCore.AnimIntent.DANCE) ratDance.resetAnimation();
                else if (animIntent == RunnerCore.AnimIntent.KNOCK) ratKnock.resetAnimation();
                else if (animIntent == RunnerCore.AnimIntent.DEAD) ratDead.resetAnimation();
                lastAnimIntent = animIntent;
            }
            if (animIntent == RunnerCore.AnimIntent.KNOCK
                    || animIntent == RunnerCore.AnimIntent.DEAD) {
                ratByIntent[animIntent.ordinal()].update(dt);
            }

            Matrix4f vp = new Matrix4f(masterRenderer.getProjectionMatrix()).mul(Maths.createViewMatrix(camera));
            GlbModel ratModel = ratByIntent[animIntent.ordinal()];
            Matrix4f ratMatrix = new Matrix4f()
                    .translate(player.getPosition())
                    .rotateY((float) Math.toRadians(RAT_YAW_DEG))
                    .scale(ratScaleByIntent[animIntent.ordinal()]);
            ratModel.drawGLB(ratModel, vp, ratMatrix, masterRenderer, camera);

            Vector3f welcomeAnchor = core.getGoalAnchor();
            if (welcomeAnchor != null) {
                int[] welcomeSide = {-1, +1};   
                float welcomeYaw = (float) Math.toRadians(RAT_YAW_DEG + WELCOME_YAW_OFFSET_DEG);
                for (int i = 0; i < welcomeModels.length; i++) {
                    Vector3f welcomePos = new Vector3f(
                            welcomeAnchor.x + welcomeSide[i] * WELCOME_OFFSET_X_U,
                            welcomeAnchor.y,
                            welcomeAnchor.z + WELCOME_OFFSET_Z_U);
                    Matrix4f welcomeMatrix = new Matrix4f()
                            .translate(welcomePos)
                            .rotateY(welcomeYaw)
                            .scale(welcomeScales[i]);
                    welcomeModels[i].drawGLB(welcomeModels[i], vp, welcomeMatrix, masterRenderer, camera);
                }
            }

            ParticleMaster.renderParticles(camera);
            TextMaster.render();
            DisplayManager.updateDisplay();
        }

        ParticleMaster.cleanUp();
        TextMaster.cleanUp();
        masterRenderer.cleanUp();
        for (GlbModel rat : ratByIntent) {
            rat.clean();
        }
        for (GlbModel welcome : welcomeModels) {
            welcome.clean();
        }
        loader.cleanUp();
        DisplayManager.closeDisplay();
    }

    private static int selectClipIndex(String[] anims, String[] keywords) {
        for (String keyword : keywords) {
            for (int a = 0; a < anims.length; a++) {
                if (anims[a].toLowerCase(Locale.ROOT).contains(keyword)) return a;
            }
        }
        return 0;   
    }

    private static float targetHeightScale(Vector3f restPoseSize) {
        return restPoseSize.y > 0f ? RAT_TARGET_HEIGHT_U / restPoseSize.y : 1f;
    }

    private record CutoutStats(double removedPct, float visibleU) {
    }

    private static CutoutStats cutoutStats(String name) throws IOException {
        BufferedImage img = ImageIO.read(
                new File(new Config().getPath() + "assets//" + name + ".png"));
        int w = img.getWidth();
        int h = img.getHeight();
        int removed = 0;
        int minY = h;
        int maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int alpha = (img.getRGB(x, y) >>> 24) & 0xFF;
                if (alpha == 0) {
                    removed++;
                } else if (alpha >= 128) {
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        double removedPct = 100.0 * removed / (double) (w * h);
        float visibleU = maxY < 0 ? 0f : (maxY - minY + 1) * TREE_QUAD_U / h;
        return new CutoutStats(removedPct, visibleU);
    }
}
