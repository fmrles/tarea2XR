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

/**
 * Entry point of the Tarea 2 runner ("Entrega Relampago").
 *
 * Block 1: playable core on boxes — three lanes, constant world scroll,
 * gamepad with keyboard fallback, jump/slide, obstacles, HUD and the
 * READY/RUNNING/WIN/LOSE state machine. Assets and TrackBuilder come later.
 *
 * Block 7: the player entity is no longer drawn from person.obj — it is
 * rendered as an animated rigged rat GLB (Running clip only) after
 * renderScene; the Player entity itself still drives camera/collision/HUD.
 *
 * Block 8: SIX rat GLB clip variants are loaded eagerly (run, jump, slide,
 * knock-down, dead, dance) and exactly ONE is rendered per frame, selected
 * by RunnerCore.getAnimIntent() — see the block-8 playback notes below.
 *
 * Run with: org.lwjglb.game.GameMain
 */
public class GameMain {

    // --- block 8: context-aware animated rigged rat player (6 GLB clips) ---
    /** Directory for every rat clip variant (relative to the project root). */
    private static final String RAT_GLB_DIR = "src/main/resources/models/";

    /**
     * Must equal the scale passed to the Player constructor below: RunnerCore
     * snapshots it as {@code baseScale} and the slide still shrinks the
     * LOGIC entity to 55% of it ({@code player.setScale(slideTimer > 0 ?
     * baseScale*0.55 : baseScale)}). Since block 8 the rat rendering no
     * longer mirrors that shrink (the slide_light animation poses the body
     * at full scale), but the Player constructor still needs this constant.
     */
    private static final float PLAYER_BASE_SCALE = 0.16f;

    /**
     * Yaw applied to the rat GLB, degrees. Mixamo/MBGLTF characters commonly
     * face +z in model space, and the old person.obj player used rotY=180 to
     * face down-track (track forward = -z; the camera sits at +z behind the
     * player). You cannot verify facing from code. Reused for all six block-8
     * variants (same rig, same export).
     * TODO visual tune: set to 0 if the rat runs towards the camera.
     */
    private static final float RAT_YAW_DEG = 180f;

    /**
     * Block 8: rendered height (u) targeted for EVERY rat clip variant —
     * the old player's 1.7 u (spec band: 1.6–1.8 u). Per-variant scale =
     * this / restPoseSize.y; for ratRunning (bounds y = 2.210) that equals
     * block 7's {@code recommendedScale * 0.4816} (1.5972 × 0.4816 = 0.7692
     * → 2.210 × 0.7692 ≈ 1.70 u), so the RUN model renders exactly as before.
     */
    private static final float RAT_TARGET_HEIGHT_U = 1.70f;

    /**
     * Block 15: the two welcome rats waiting at Tibo's burrow hole dance
     * relative to the goal anchor (hole center). The hole is a 6x6 u quad
     * (spans +-3 u), so X places the rats just OUTSIDE the lateral hole edges;
     * Z puts them at the near edge on the +z side — the hole scrolls in from
     * more-negative z (track forward = -z) toward the player at PLAYER_Z, and
     * the camera sits behind the player at +z, so +z is the side that faces
     * the incoming player and the camera.
     */
    private static final float WELCOME_OFFSET_X_U = 3.4f;  // TODO visual tune
    private static final float WELCOME_OFFSET_Z_U = 2.2f;   // TODO visual tune

    /**
     * Block 15: yaw offset for the welcome rats. RAT_YAW_DEG (180) makes the
     * player rat face down-track (-z, away from the camera); the welcome rats
     * wait AT the hole and face the INCOMING player (+z), i.e. the opposite
     * facing: player yaw + 180. Derived as an offset so flipping RAT_YAW_DEG
     * (its own TODO visual tune) keeps the two parties facing each other.
     * Sign reasoning: the hole approaches from -z, the player always stands
     * on its +z side; verify with the eye at the end of a run.
     * TODO visual tune: set to 0 if they dance with their backs to the player.
     */
    private static final float WELCOME_YAW_OFFSET_DEG = 180f;

    /**
     * Block 9: world size (u) of one billboard tree quad's side. The cut-out
     * PNGs are square, so the quad is TREE_QUAD_U x TREE_QUAD_U; the visible
     * foliage height is that times the silhouette's image-space height
     * fraction (reported by the {@code tree[]} startup line): 6 u quad ->
     * ~5.8 u visible, inside the 5-7 u spec band. Must stay in
     * sync with RunnerCore.TREE_SCALE.
     */
    private static final float TREE_QUAD_U = 6f;

    /**
     * Tint for the pickup spark burst (cheese crumbs). Derived from the
     * average opaque pixel of assets/cheese/cheese.png = (230.5, 126.6, 0.3)
     * -> /255 = (0.904, 0.496, 0.001) -> normalized to max channel 1 ->
     * (0.90, 0.50, 0.00). The dirt_02.png debris chunks are near-neutral
     * white (avg opaque RGB ~196.6), so multiplying by this tint reads as
     * orange cheese crumbs.
     */
    private static final Vector3f CHEESE_TINT = new Vector3f(0.90f, 0.50f, 0.00f);

    /**
     * Tint for the obstacle-hit smoke puff. The previous Smoke45Frames atlas
     * rendered at avg opaque RGB 106.2; the replacement smoke_10.png renders
     * at avg opaque RGB 176.6, so the linear multiply that preserves the
     * current gray is 106.2 / 176.6 = 0.60 on every channel (alpha is
     * untouched by the shader).
     */
    private static final Vector3f SMOKE_TINT = new Vector3f(0.60f, 0.60f, 0.60f);

    public static void main(String[] args) {
        DisplayManager.createDisplay();
        Loader loader = new Loader();
        long assetLoadStart = System.nanoTime();   // block 9: total asset load time
        TextMaster.init(loader);
        MasterRenderer masterRenderer = new MasterRenderer(loader);
        // The class models (person.obj, crate.obj) are not guaranteed to share
        // the engine's winding order, so keep back-face culling off.
        MasterRenderer.disableCulling();

        // --- particle pipeline (block 4: one-shot pickup spark bursts) ---
        ParticleMaster.init(loader, masterRenderer.getProjectionMatrix());

        // --- lighting ---
        List<Light> lights = new ArrayList<>();
        lights.add(new Light(new Vector3f(10000, 10000, 10000), new Vector3f(1, 1, 1)));

        // --- terrain (block 6a: own "Puerto Clara" heightmap + urban blend map,
        // generated by org.lwjglb.tools.HeightmapGenerator; block 11: the b slot
        // is the asphalt street road.png (renamed from Meshy "Road Straight No
        // Sidewalk"), corridor-mapped by roadU0/roadSpanU/roadVRepeat in the
        // terrain fragment shader — see TerrainRenderer's constants) ---
        TerrainTexture backgroundTexture = new TerrainTexture(loader.loadTexture("grassy"));
        TerrainTexture rTexture = new TerrainTexture(loader.loadTexture("mud"));
        TerrainTexture gTexture = new TerrainTexture(loader.loadTexture("grass"));
        TerrainTexture bTexture = new TerrainTexture(loader.loadTexture("road"));
        TerrainTexturePack texturePack = new TerrainTexturePack(backgroundTexture, rTexture, gTexture, bTexture);
        TerrainTexture blendMap = new TerrainTexture(loader.loadTexture("blendMapPuerto"));
        Terrain terrain = new Terrain(0, -1, loader, texturePack, blendMap, "heightMapPuerto");
        List<Terrain> terrains = new ArrayList<>();
        terrains.add(terrain);

        // --- actors ---
        // person.obj is 10.6 units tall, crate.obj is 200 units wide:
        // scale them down to a human (1.7 u) and a road crate (2 u).
        // Block 7: person.obj is no longer DRAWN — the player renders as the
        // animated rat GLB below. playerModel stays because the Player
        // constructor still needs a TexturedModel and the entity keeps
        // driving camera, collision and HUD logic (logic-only now).
        TexturedModel playerModel = loader.createTexturedModel("person", "playerTexture", 10, 1);
        Player player = new Player(playerModel, new Vector3f(RunnerCore.PLAYER_X, 0, RunnerCore.PLAYER_Z),
                0, 180, 0, PLAYER_BASE_SCALE);

        // --- block 8: six context-aware animated rat GLBs (Assimp skinning
        // via the engine's GlbModel, proven in class with chaos.glb; load
        // paths relative to the project root). Arrays are ordered by
        // RunnerCore.AnimIntent ordinal so index == intent.ordinal().
        // Clip selection: lowercase contains, primary keyword first, then the
        // secondary one, documented fallback = first clip (index 0).
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
            // Block 15b: selection factored into selectClipIndex() with
            // identical order/semantics so the welcome pair below reuses ONE
            // selector (no second copy).
            int selected = selectClipIndex(anims, ratKeywords[i]);
            model.setActiveAnimationIndex(selected);

            // KNOCK/DEAD are one-shots: hold their final pose at the end of
            // the clip instead of restarting (looping default = old behavior).
            boolean oneShot = ratIntents[i] == RunnerCore.AnimIntent.KNOCK
                    || ratIntents[i] == RunnerCore.AnimIntent.DEAD;
            model.setLooping(!oneShot);

            // Per-variant scale so every rat renders RAT_TARGET_HEIGHT_U tall
            // even if a variant's rest-pose bounds differ from ratRunning's
            // (2.880 x 2.210 x 1.555); when they match, this reproduces
            // block 7's ratScale exactly (see RAT_TARGET_HEIGHT_U).
            org.joml.Vector3f bounds = model.getRestPoseSize();
            // Block 15b: normalization factored into targetHeightScale() — the
            // welcome pair below uses the SAME code path.
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

        // --- block 15b: the two DISTINCT welcome-rat models (white + brown) ---
        // Separate GlbModel instances (NOT ratByIntent entries): they feed the
        // burrow-hole welcome pair only and never join ratLoopModels or the
        // player's 6-clip set (>= 3 animated models in scene: these two + the
        // player rat). Original Meshy exports, renamed on disk into
        // RAT_GLB_DIR (Meshy attribution):
        //   white: assets\Meshy_AI_White_Rat_Rigged_20261112_animated_mixamo_FunnyDancing_01.glb
        //   brown: assets\Meshy_AI_Brown_Rat_Rigged_20261112_animated_mixamo_FunnyDancing_01.glb
        // Clip selection + height normalization reuse the block-8 helpers:
        // keyword preference {"dance","funny"} matches FunnyDancing_01 because
        // selectClipIndex lowercases the clip name (case-insensitive contains);
        // single-clip files -> fallback would be clip 0 anyway.
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
            model.setLooping(true);   // the celebration dances forever

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
        // Looping models advance EVERY frame (stays warm, no resume hitch);
        // the one-shots advance only while their intent is active.
        GlbModel[] ratLoopModels = {
                ratByIntent[RunnerCore.AnimIntent.RUN.ordinal()],
                ratByIntent[RunnerCore.AnimIntent.JUMP.ordinal()],
                ratByIntent[RunnerCore.AnimIntent.SLIDE.ordinal()],
                ratDance};
        RunnerCore.AnimIntent lastAnimIntent = RunnerCore.AnimIntent.RUN;

        // Block 17b: crates + the two retro cars (block 12) load through
        // NormalMappedObjLoader (per-vertex tangents) with normal maps so they
        // render through NormalMappingRenderer: course crateNormal.png, Meshy
        // exports carYellow/Ynormal.png + carCyan/Cyannormal.png. Shine/reflect
        // stay 10/0.5 (createTexturedModel's values). Collision is unaffected
        // (RunnerCore's selection happens at materialization only).
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
        // Block 12: startup report - tris from the ACTUAL loaded RawModel,
        // bounds the measured OBJ extents (loader keeps only positions in the
        // VAO), scale/baseY from RunnerCore so the printed numbers are the
        // ones the game uses (baseY = raw model y_min, house-line convention).
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
        // Block 10 A: the collectible is now Tibo's cheese wedge (Meshy export
        // normalized on disk to v/vt/vn/f only, 8968 tris, 2K texture) - the
        // barrel.obj placeholder stays on disk, unloaded (like house.obj).
        TexturedModel canModel = loader.createTexturedModel("cheese/cheese", "cheese/cheese", 10, 0.5f);
        // Block 10 B5: startup report - tris comes from the ACTUAL loaded
        // RawModel; bounds are the measured cheese.obj extents (loader keeps
        // only positions in the VAO, no bounds API), scale/centre from
        // RunnerCore so the printed numbers are the ones the game uses.
        System.out.printf("cheese: tris=%d bounds=%.6fx%.6fx%.6fu scale=%.5f center=%.4f texId=%d%n",
                canModel.getRawModel().getVertexCount() / 3,
                1.064413f, 1.148455f, 1.902227f,
                RunnerCore.CAN_SCALE, RunnerCore.CAN_CENTER_Y,
                canModel.getTexture().getID());
        // Block 10 B: the goal is Tibo's burrow hole - ONE flat quad in the XZ
        // plane (4 verts at y=0 spanning +/-0.5, normals up, 6 indices) that
        // RunnerCore scales to GOAL_HOLE_U (6 u) and glues just above the road.
        // The fragment shader discards a < 0.5, so burrow.png's dithered alpha
        // cuts a ragged organic silhouette out of the quad.
        // block 10 B: goal decal geometry - ONE quad in the XZ plane (y = 0),
        // seen from above (+Y); 4 verts spanning +/-0.5, normals (0,1,0), 6 indices.
        float[] burrowPositions = {
                -0.5f, 0f, -0.5f, 0.5f, 0f, -0.5f, 0.5f, 0f, 0.5f, -0.5f, 0f, 0.5f};
        float[] burrowUvs = {0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f};
        float[] burrowNormals = {
                0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f};
        int[] burrowIndices = {0, 1, 2, 0, 2, 3};
        RawModel burrowQuad = loader.loadToVAO(
                burrowPositions,
                // Texture.load() uploads stbi_load's buffer as-is (no vertical
                // flip), so GL t=0 is the PNG's TOP row. The burrow design is
                // radially symmetric about its centre, so any v orientation maps
                // the hole onto the same disc - we keep the tree-quad convention
                // (near/ground edge takes v=1) and the choice is harmless here.
                burrowUvs,
                burrowNormals,
                burrowIndices);
        ModelTexture burrowTexture = new ModelTexture(loader.loadTexture("burrow"));
        // same shine/reflect as createTexturedModel(..., 10, 0.5f)
        burrowTexture.setShineDamper(10f);
        burrowTexture.setReflectivity(0.5f);
        TexturedModel goalModel = new TexturedModel(burrowQuad, burrowTexture);
        // Block 10 B5: startup report - verts/idx from the ACTUAL arrays the
        // RawModel was built from (Loader records indices.length as its
        // vertexCount, i.e. idx), quad size from RunnerCore.GOAL_HOLE_U.
        System.out.printf("goal[burrow]: verts=%d idx=%d quad=%.1fu tex=burrow.png texId=%d%n",
                burrowPositions.length / 3, goalModel.getRawModel().getVertexCount(),
                RunnerCore.GOAL_HOLE_U, burrowTexture.getID());
        // Block 15b: welcome rats report — two DISTINCT models (white on the
        // -X side, brown on the +X side), each with its own normalized scale
        // (constants used by the draw in the render loop below; same printf
        // style as the goal[burrow] line).
        // yaw = RAT_YAW_DEG + WELCOME_YAW_OFFSET_DEG (180+180=360 == 0: the
        // welcome rats face +z, opposite the down-track player rat).
        System.out.printf("welcome-rats: white=%s brown=%s scale=%.4f/%.4f offset=±%.1fu/%.1fu yaw=%.0f (block 15b)%n",
                welcomeClips[0], welcomeClips[1], welcomeScales[0], welcomeScales[1],
                WELCOME_OFFSET_X_U, WELCOME_OFFSET_Z_U,
                RAT_YAW_DEG + WELCOME_YAW_OFFSET_DEG);
        // Street light and (block 6c) rusted car / trash bin / obelisk models
        // from Pepsico-man repo (MIT): https://github.com/Abd-ELrahmanHamza/Pepsico-man
        // (every OBJ normalized on disk - all v/vt/vn moved before the faces,
        // quads/ngons fan-triangulated and g/o/usemtl/mtllib/s/l/# stripped,
        // because OBJFileLoader stops at the first non-f line after faces
        // begin and reads only 3 verts per f line; rustedCar.png downscaled
        // 4096->1024 on disk and rustedCar.obj x/z re-centred - rigid
        // translation, same geometry).
        TexturedModel lampModel = loader.createTexturedModel("StreetLight", "StreetLight", 10, 0.5f);
        TexturedModel carModel = loader.createTexturedModel("rustedCar", "rustedCar", 10, 0.5f);
        TexturedModel trashModel = loader.createTexturedModel("trash", "trash", 10, 0.5f);
        TexturedModel obeliskModel = loader.createTexturedModel("obelisk", "obelisk", 10, 0.5f);

        // --- block 9: three zone-styled Meshy houses (loader-ready files:
        // v/vt/vn/f only, 7789/8933/8663 triangles, 2048^2 textures) + two
        // colour-keyed billboard trees. House index s (0..2) is TrackBuilder's
        // event.style = zoneAt(trackDist): houseA (zone A), houseB (zone B),
        // houseC (zone C, the "Warm" model).
        // Block 17b: the block-17 house normal maps were reverted per user
        // request (houses back to the plain diffuse path); house{A,B,C}/normal.png
        // moved to the asset backup outside the project.
        String[] houseNames = {"houseA", "houseB", "houseC"};
        List<TexturedModel> houseModels = new ArrayList<>(houseNames.length);
        for (int s = 0; s < houseNames.length; s++) {
            String name = houseNames[s];
            TexturedModel model = loader.createTexturedModel(name + "/" + name, name + "/" + name, 10, 0.5f);
            houseModels.add(model);
            int tris = model.getRawModel().getVertexCount() / 3;
            float scale = TrackBuilder.HOUSE_SCALE[s];
            float along = TrackBuilder.HOUSE_MODEL_DX[s] * scale;                 // along the track
            float across = (TrackBuilder.HOUSE_MODEL_DZ_FRONT[s]                 // across the road
                    + TrackBuilder.HOUSE_MODEL_DZ_BACK[s]) * scale;
            float high = TrackBuilder.HOUSE_MODEL_DY[s] * scale;                  // vertical
            System.out.printf("house[%s]: tris=%d bounds=%.2fx%.2fx%.2fu (along x across x high) scale=%.4f baseY=%.6f%n",
                    name, tris, along, across, high, scale, TrackBuilder.HOUSE_MODEL_Y_MIN[s]);
        }

        // --- block 9: billboard trees = two crossed quads (8 verts, 12
        // indices), y in [0,1] so TREE_SCALE = 6 u tall, textured with the
        // colour-keyed cut-outs (arbol{1,2}_cut.png, generated by
        // org.lwjglb.tools.TreeCutout). Normals point up so both quads get
        // the same sky light; the fragment shader discards a < 0.5, which is
        // what makes the cut-out silhouette work.
        RawModel treeQuad = loader.loadToVAO(
                new float[]{
                        -0.5f, 0f, 0f, 0.5f, 0f, 0f, 0.5f, 1f, 0f, -0.5f, 1f, 0f,   // quad in the XY plane
                        0f, 0f, -0.5f, 0f, 0f, 0.5f, 0f, 1f, 0.5f, 0f, 1f, -0.5f}, // quad in the ZY plane
                // Texture.load() uploads stbi_load's buffer as-is (no vertical
                // flip), so GL t=0 is the PNG's TOP row while the quad's ground
                // verts are y=0: those take v=1 (image bottom) to keep the tree
                // upright. The OBJ path does the same with its `1 - vt.y`.
                new float[]{0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f,
                        0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f},
                new float[]{0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f,
                        0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f},
                new int[]{0, 1, 2, 0, 2, 3, 4, 5, 6, 4, 6, 7});
        TexturedModel[] treeModels = new TexturedModel[2];
        for (int t = 0; t < 2; t++) {
            String cutName = "arbol" + (t + 1) + "_cut";
            ModelTexture texture = new ModelTexture(loader.loadTexture(cutName));
            // same shine/reflect as createTexturedModel(..., 10, 0.5f)
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

        // --- HUD font ---
        FontType font = new FontType(loader.loadTexture("candara"),
                new File(new Config().getPath() + "assets\\candara.fnt"));

        Camera camera = new Camera(player);
        GameInput input = new GameInput();
        // Block 4: spark burst texture for can pickups — dirt_02.png white
        // debris chunks tinted with the cheese color (single frame -> 1 row)
        ParticleTexture sparkTexture = new ParticleTexture(loader.loadTexture("dirt_02"), 1, CHEESE_TINT);
        // Block 5: smoke puff texture for obstacle collisions — smoke_10.png
        // single ring puff, tinted to keep the previous rendered gray (1 row)
        ParticleTexture smokeTexture = new ParticleTexture(loader.loadTexture("smoke_10"), 1, SMOKE_TINT);
        RunnerCore core = new RunnerCore(player, obstacleModel, carYellowModel, carCyanModel,
                canModel, goalModel,
                houseModels, treeModels[0], treeModels[1], lampModel, carModel, trashModel,
                obeliskModel, terrain, font, sparkTexture, smokeTexture);
        System.out.println(core.describeLayout());

        // --- debug: one line per simulated second ---
        float debugAccum = 0f;
        int debugFrames = 0;

        // --- main loop ---
        while (!glfwWindowShouldClose(DisplayManager.window)) {
            if (glfwGetKey(DisplayManager.window, GLFW_KEY_ESCAPE) == GLFW_PRESS) {
                glfwSetWindowShouldClose(DisplayManager.window, true);
            }

            float dt = DisplayManager.getFrameTimeSeconds();
            input.poll(dt);
            core.update(dt, input);
            // Block 8: which rat clip to render this frame — resolved by
            // RunnerCore (it owns the state machine and the timers).
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

            // Block 14: zone drives the skybox cubemap + fog (hard switch at
            // the zone boundaries) before anything is rendered this frame.
            masterRenderer.setZone(core.getZone());
            masterRenderer.renderScene(
                    core.getDrawables(),
                    // Block 17b: crate + retro-car obstacles render through
                    // NormalMappingRenderer. The list is owned by RunnerCore
                    // (MasterRenderer only clears its internal map, never
                    // this list).
                    core.getNormalMapEntities(),
                    terrains,
                    lights,
                    camera,
                    new Vector4f(0, -1, 0, 100000));

            // --- block 8: context-aware rat animation, slot unchanged from
            // block 7 (after renderScene, before the particles).
            // Looping models (RUN/JUMP/SLIDE/DANCE) advance EVERY frame, even
            // while another clip is shown, so switching intent never resumes a
            // frozen clip (no hitch). Entering DANCE restarts the celebration
            // from the top; entering a one-shot (KNOCK/DEAD) restarts its clip
            // and it then advances ONLY while that intent is active — its
            // GlbModel is non-looping, so the last pose is held for the rest
            // of the 1.2 s i-frames window (KNOCK) / the whole lose screen
            // (DEAD). State and timers live in RunnerCore, untouched here.
            for (GlbModel ratLoop : ratLoopModels) {
                ratLoop.update(dt);
            }
            // Block 15b: the two welcome models (white/brown) are ALWAYS
            // animated while loaded — they celebrate whenever the goal exists.
            // Own instances + own update loop: NOT part of ratLoopModels (that
            // array feeds the player's clip set), and no resetAnimation() calls
            // here — each dance clip loops on its own, so WIN's
            // ratDance.resetAnimation() below only touches the player's shared
            // instance and never these two.
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
            // Block 8: render ONLY the model for the current intent. The old
            // 55% slideFactor is gone — slide_light poses the body now, so
            // every state draws at full per-variant scale (RunnerCore still
            // shrinks the logic-only Player entity; it no longer affects
            // rendering). Same yaw (RAT_YAW_DEG) for all six: same rig/export.
            GlbModel ratModel = ratByIntent[animIntent.ordinal()];
            Matrix4f ratMatrix = new Matrix4f()
                    .translate(player.getPosition())
                    .rotateY((float) Math.toRadians(RAT_YAW_DEG))
                    .scale(ratScaleByIntent[animIntent.ordinal()]);
            // drawGLB switches to the GLB shader and restores glUseProgram(0);
            // it rebuilds vp from masterRenderer/camera itself (the vp passed
            // in is overwritten there — kept for API symmetry with the
            // MainGameLoop precedent).
            ratModel.drawGLB(ratModel, vp, ratMatrix, masterRenderer, camera);

            // Block 15b: two DISTINCT dancing rats (whiteRat + brownRat, own
            // GlbModel instances) flank the burrow-hole entrance from the
            // moment the goal exists (getGoalAnchor() == null before the goal
            // spawns and after reset() — nothing else to guard). One matrix per
            // model, array-driven: white on the -X side (side -1), brown on
            // the +X side (side +1); offsets/yaw/anchor math unchanged from
            // block 15 (same matrix convention as the player rat above:
            // translate to the feet position — the anchor's y already carries
            // groundY + RunnerCore's 0.05 z-fight hover). Scale is each
            // model's OWN RAT_TARGET_HEIGHT_U-normalized scale, NOT
            // ratScaleByIntent: these are separate instances, so the player's
            // clip set and WIN's ratDance.resetAnimation() never touch them.
            // They advance in their OWN update loop above (always animated
            // while loaded) — do NOT call update()/resetAnimation() here or
            // they'd double-advance; the dance clips loop, so nothing needs
            // snapping on WIN.
            // They are GLBs (not Entity/TexturedModel), so they must NOT be
            // added to drawables/renderScene — drawGLB handles their shader.
            Vector3f welcomeAnchor = core.getGoalAnchor();
            if (welcomeAnchor != null) {
                int[] welcomeSide = {-1, +1};   // index 0 = white (-X), 1 = brown (+X)
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
        // GlbModel.clean() frees the GL resources it owns: mesh VAOs/VBOs and
        // GLB-embedded textures (it does NOT delete the shader program — that
        // lives until process exit, harmless). GL context still alive here.
        // Block 8: all six rat variants, before loader.cleanUp() (block-7 rule).
        for (GlbModel rat : ratByIntent) {
            rat.clean();
        }
        // Block 15b: the two welcome models (own instances, same GL-context rule).
        for (GlbModel welcome : welcomeModels) {
            welcome.clean();
        }
        loader.cleanUp();
        DisplayManager.closeDisplay();
    }

    /**
     * Block 15b: keyword-based active-clip selection, factored out of the
     * block-8 rat load so the welcome pair (white/brown) reuses the SAME
     * selector — one implementation, no second copy. Order: for each keyword
     * in preference order, the first clip whose name contains it, comparing
     * LOWERCASED names (case-insensitive — so {@code FunnyDancing_01} matches
     * {@code dance}). Documented fallback: clip 0 (single-clip files land there
     * anyway).
     *
     * @param anims    clip names from {@link GlbModel#getAnimationNames()}
     * @param keywords lowercase keywords, primary preference first
     * @return index of the selected clip (0 when nothing matches)
     */
    private static int selectClipIndex(String[] anims, String[] keywords) {
        for (String keyword : keywords) {
            for (int a = 0; a < anims.length; a++) {
                if (anims[a].toLowerCase(Locale.ROOT).contains(keyword)) return a;
            }
        }
        return 0;   // documented fallback: first clip
    }

    /**
     * Block 15b: per-variant scale so a rat model renders
     * {@link #RAT_TARGET_HEIGHT_U} units tall regardless of its rest-pose
     * bounds — factored out of the block-8 rat load so the welcome pair uses
     * the SAME normalization code path (previously inlined as
     * {@code bounds.y > 0 ? RAT_TARGET_HEIGHT_U / bounds.y : 1}).
     *
     * @param restPoseSize model bounds from {@link GlbModel#getRestPoseSize()}
     * @return scale factor (1 when the bounds are degenerate on Y)
     */
    private static float targetHeightScale(Vector3f restPoseSize) {
        return restPoseSize.y > 0f ? RAT_TARGET_HEIGHT_U / restPoseSize.y : 1f;
    }

    /** Alpha statistics of one cut-out tree PNG. */
    private record CutoutStats(double removedPct, float visibleU) {
    }

    /**
     * Block 9: side-effect-free stats of a colour-keyed tree PNG, so the
     * startup line reports how much of the {@link #TREE_QUAD_U} quad the
     * silhouette really fills. {@code removedPct} = fully transparent pixels
     * (what TreeCutout keyed away); {@code visibleU} = image-space height of
     * the pixels the entity fragment shader keeps (it discards a < 0.5, so
     * the cut is taken at alpha >= 128).
     */
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
