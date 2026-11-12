package org.lwjglb.game;

import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjglb.entities.Entity;
import org.lwjglb.entities.Player;
import org.lwjglb.fontMeshCreator.FontType;
import org.lwjglb.fontMeshCreator.GUIText;
import org.lwjglb.fontRendering.TextMaster;
import org.lwjglb.models.TexturedModel;
import org.lwjglb.particles.Particle;
import org.lwjglb.particles.ParticleTexture;
import org.lwjglb.terrains.Terrain;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Rules of the Pepsiman-style runner: three lanes, constant world scroll
 * towards a player fixed at z = {@link #PLAYER_Z}, jump/slide, obstacles,
 * health, cans, countdown timer and the READY/RUNNING/WIN/LOSE state machine.
 *
 * The track itself is a deterministic {@link TrackBuilder} layout built once
 * in the constructor: obstacles, can rows (cheese wedges) and the burrow-hole
 * goal are consumed from it as the world scrolls (the old random spawner is
 * gone).
 *
 * Block 4 adds pickup spark bursts: collecting a can fires a one-shot burst
 * of particles through the engine's particle pipeline
 * ({@code org.lwjglb.particles.ParticleMaster}/{@code Particle}).
 *
 * Block 5 adds collision smoke puffs: taking a hit from an obstacle fires a
 * one-shot burst of smoke particles (event-driven, same pipeline).
 *
 * Block 6b adds side decor: houses and street lights laid out by TrackBuilder,
 * streamed in further ahead (120 u), scrolling with the world and swept behind
 * the player exactly like obstacles. Decor is purely visual - it never enters
 * {@link CollisionSystem}.
 *
 * Block 6c adds three more static decor types (8 static object types in total
 * with crate, cheese pickup, burrow-hole goal, house and street light): rusted cars
 * parallel-parked at the curb in every zone, trash bins beside some street
 * lights, and obelisk monuments on the zone-C hill shoulder - all laid out by
 * TrackBuilder and streamed through the same decor path.
 *
 * Block 7: the player entity is no longer in {@link #drawables} - GameMain
 * renders it as an animated rigged rat GLB (Running clip) after renderScene.
 * This class still owns the Player for logic: collisions (this.player's
 * lane), camera follow (position/rotY) and the HUD all read it directly.
 *
 * Block 8: this class also exposes which rat animation GameMain should
 * render this frame via {@link AnimIntent}/{@link #getAnimIntent()}, so the
 * priority between WIN/LOSE, i-frames, airborne, slide and run is resolved
 * here, next to the timers and state machine that define it.
 *
 * Block 10 retheme (mouse): the barrel pickup is now the Meshy cheese wedge
 * (HUD counts QUESOS) and the vending-machine goal is a flat burrow-hole
 * decal - reaching it = "MILTON SE SALVO". Collision/trigger logic and the
 * TrackBuilder layout are unchanged; only models, scales and copy moved.
 *
 * Block 12: SOME jump-over (LOW) obstacles materialize as the two Meshy retro
 * cars (yellow/cyan) instead of the crate - the selection is index-derived at
 * materialization time (zero RNG) so TrackBuilder's layout does not change,
 * and collision stays model-independent ({@link #resolveObstacles} reads only
 * lane/z/cleared, never the model). HIGH obstacles always keep the crate.
 */
public final class RunnerCore {

    public enum State { READY, RUNNING, WIN, LOSE }

    /**
     * Block 8: which rat GLB clip GameMain renders this frame. Priority is
     * resolved in {@link #getAnimIntent()} (documented there).
     */
    public enum AnimIntent { RUN, JUMP, SLIDE, KNOCK, DEAD, DANCE }

    // --- tuning (design doc, section 2) ---
    public static final int LANE_COUNT = 3;
    public static final float LANE_WIDTH = 4.0f;
    public static final float BASE_SPEED = 17.0f;      // u/s, zone A; TrackBuilder.speedAt raises it to 20/23
    public static final float MAX_TIME = 210f;         // 3:30 countdown
    public static final float TRACK_LENGTH = 2500f;    // units to the burrow hole
    public static final int MAX_HEALTH = 3;
    public static final float CANS_PER_LIFE = 10f;

    // --- physics ---
    private static final float GRAVITY = -50f;
    private static final float JUMP_POWER = 26f;
    private static final float SLIDE_TIME = 0.6f;
    private static final float JUMP_CLEARANCE = 0.9f;   // height needed to clear a low obstacle
    private static final float LANE_SMOOTHING = 14f;

    // --- world ---
    public static final float PLAYER_X = 75f;           // centre of the terrain tile (0,-1)
    public static final float PLAYER_Z = -75f;
    private static final float SPAWN_AHEAD = 60f;
    private static final float DESPAWN_BEHIND = 12f;
    private static final float OBSTACLE_SCALE = 0.01f;  // crate.obj is 200 u wide -> 2 u

    // --- pickups (cheese): Meshy cheese wedge (block 10 A), HUD counts QUESOS ---
    // Package-visible: GameMain's "cheese:" startup line reports them.
    // Visual tune after user review ("el queso se ve muy grande"): the pickup
    // is HALVED - both the mesh scale and its float height, so the whole
    // presentation is exactly half of block 10's first pass.
    static final float CAN_SCALE = 0.52244f;   // cheese.obj dy=1.148455 u -> 0.6 u tall
    static final float CAN_CENTER_Y = -0.0004f; // model-space centre y (measured: essentially 0)
    // Centre lift above the ground: 1.14 u (block 10) halved -> 0.57 u, so the
    // bottom hangs ~0.27 u up - the cheese drifts just above the asphalt.
    private static final float CAN_HOVER = 0.57f;
    private static final float CAN_SPIN_SPEED = 90f;    // deg/s
    // can-row size/spacing live in TrackBuilder (CAN_ROW_SIZE, CAN_ROW_SPACING)
    // Footprint at CAN_SCALE: 0.99 u max extent (yaw spins the wedge, so the
    // long axis sweeps both along/across) x 0.55 u min - roughly half a lane,
    // comfortably under CAN_ROW_SPACING (2.5 u).

    // --- goal: Milton's burrow hole at the end of the track (block 10 B) ---
    // Flat XZ decal quad built by GameMain (4 verts, uv 0..1); scaled to
    // GOAL_HOLE_U x GOAL_HOLE_U and hovered GOAL_HOLE_Y above the road.
    // Package-visible: GameMain's "goal[burrow]:" startup line reports it.
    static final float GOAL_HOLE_U = 6f;              // quad 6 x 6 u  // TODO visual tune
    private static final float GOAL_HOLE_Y = 0.05f;   // just above the road (z-fight guard)

    // --- block 6b/9: side decor (Meshy houses + billboard trees, visual only) ---
    private static final float DECOR_SPAWN_AHEAD = 120f;  // decor streams in further out than obstacles
    // block 9: per-style house scale/feet come from TrackBuilder's measured
    // model constants (they must stay visible to the headless self-check too).
    // HOUSE_* are indexed by style 0..2 == zoneAt(trackDist): 0 houseA, 1 houseB, 2 houseC.
    static final float[] HOUSE_SCALE = TrackBuilder.HOUSE_SCALE;
    static final float[] HOUSE_BASE_Y = TrackBuilder.HOUSE_MODEL_Y_MIN;
    // All three Meshy facades sit at model +Z (inferred from the outward wall
    // density, see TrackBuilder), so the yaw that turns them toward the road is
    // the OLD house.obj convention: +90 on the left (front +Z -> +X), -90 on
    // the right (front -> -X).
    private static final float HOUSE_ROT_LEFT = 90f;      // facade (+Z) -> +X, faces the road from the left
    private static final float HOUSE_ROT_RIGHT = -90f;    // facade -> -X, faces the road from the right
    /**
     * Extra yaw added to BOTH house rotations because the Meshy models' front
     * axis cannot be verified from code (only inferred). 0 = front at +Z as
     * assumed above; 180 = front actually at -Z; +/-90 = facade on X, which
     * also swaps the along-track extent to HOUSE_MODEL_DZ_*.
     * TODO visual tune: the user confirms the facing in-game.
     */
    private static final float HOUSE_FRONT_YAW_OFFSET = 0f;   // TODO visual tune
    // Block 9b: 6 u quad -> ~5.8 u visible foliage, inside the 5-7 u spec band.
    // MUST stay in sync with GameMain.TREE_QUAD_U (GameMain's javadoc says so).
    private static final float TREE_SCALE = 6f;           // cutout quad: y in [0,1] -> 6 u tall billboard
    private static final float LAMP_SCALE = 0.285f;       // StreetLight.obj is 15.7 u tall -> ~4.5 u
    private static final float LAMP_BASE_Y = -0.30f;      // StreetLight.obj y_min (pole base below origin)

    // --- block 6c: parked cars, trash bins, zone-C obelisks (purely visual) ---
    private static final float CAR_SCALE = 0.266f;        // rustedCar.obj is 16.91 u long (x) -> 4.5 u along z
    private static final float CAR_BASE_Y = 0f;           // x/z re-centred on disk; y_min ~ 0.0002 (wheels down)
    private static final float CAR_ROT_Y = 90f;           // model length axis (x) -> world z (along the track)
    private static final float TRASH_SCALE = 0.15f;       // trash.obj is 4.88 u tall -> ~0.73 u
    private static final float TRASH_BASE_Y = 0.222803f;  // trash.obj y_min
    private static final float OBELISK_SCALE = 0.0265f;   // obelisk.obj is 308.1 u tall -> ~8.2 u (silhouette caps it, see TrackBuilder)
    private static final float OBELISK_BASE_Y = -1.869767f; // obelisk.obj y_min (slightly below origin)

    // --- block 12: SOME jump-over (LOW) obstacles become retro-car traffic ---
    // Measurement block (block 12, all values measured on disk):
    //   crate.obj      : 200x200x200 u, y_min = -100.000015 -> y_min*OBSTACLE_SCALE
    //                    = -1.0 (NOT ~0), so crates keep the EXACT current y-position
    //                    (yOff = 0 below - no "fix").
    //   carYellow.obj  : 9119 tris, dx=1.902363 dy=0.580190 dz=0.879636, y_min=-0.282791
    //   carCyan.obj    : 9498 tris, dx=1.892671 dy=0.567506 dz=0.958436, y_min=-0.284211
    // The car mesh's long axis is model X; CAR_YAW_DEG = 90 lays it onto world Z
    // (along the track), so across-road = dz and along-track = dx:
    //   yellow scale 2.60 -> across 2.287 u, along 4.946 u, high 1.509 u
    //   cyan   scale 2.40 -> across 2.300 u, along 4.542 u, high 1.362 u
    // across lands in the 2.1-2.4 u band (the crate footprint is ~2 u), along is
    // well under TrackBuilder's 19.1 u minimum obstacle gap.
    // CAR_OBSTACLE_BASE_Y holds the RAW model y_min (same convention as
    // HOUSE_BASE_Y / Decor: yOff = baseY * scale is computed at
    // materialization) - with the Decor sign rule `groundY - yOff` those yOff
    // values (yellow -0.735257, cyan -0.682106) RAISE the entity so the
    // tires land exactly on the road.
    // Package-visible: GameMain's "obstacle[car*]:" startup lines report them.
    // (Named CAR_OBSTACLE_* because plain CAR_SCALE/CAR_BASE_Y/CAR_ROT_Y are
    // already taken by the block 6c parked rusted car, which stays untouched.)
    static final float[] CAR_OBSTACLE_SCALE = {2.60f, 2.40f};            // 0 yellow, 1 cyan
    static final float[] CAR_OBSTACLE_BASE_Y = {-0.282791f, -0.284211f}; // raw model y_min per car
    private static final float CAR_YAW_DEG = 90f;   // model length (x) -> world z (along the track)
    /**
     * Extra yaw added on top of {@link #CAR_YAW_DEG}. The Meshy exports carry
     * no front-axis metadata, so 0 = base orientation (CAR_YAW_DEG only),
     * 180 = flipped to drive the opposite way down the road.
     * TODO visual tune: oncoming vs same-direction traffic is a visual call
     * the user confirms in-game.
     */
    private static final float CAR_FRONT_YAW_OFFSET = 0f;   // TODO visual tune

    // --- feedback ---
    private static final float INVULN_TIME = 1.2f;
    private static final float STUMBLE_TIME = 0.5f;     // world freezes briefly on impact
    private static final float MESSAGE_TIME = 0.9f;

    // --- block 4: spark burst on can pickup (event-driven, one burst per can) ---
    private static final int SPARK_BURST_COUNT = 12;
    private static final float SPARK_GRAVITY_EFFECT = 1.5f;  // falls faster than the world feel
    private static final float SPARK_SPREAD = 4f;            // horizontal speed range: +/- 2 u/s
    private static final float SPARK_UP_BASE = 2f;           // upward bias: 2..5 u/s
    private static final float SPARK_UP_RAND = 3f;
    private static final float SPARK_LIFE_BASE = 0.35f;      // 0.35..0.60 s
    private static final float SPARK_LIFE_RAND = 0.25f;
    private static final float SPARK_SCALE_BASE = 0.60f;     // 0.60..1.00 u (user: still too small at 0.40..0.65)
    private static final float SPARK_SCALE_RAND = 0.4f;
    private static final float SPARK_ORIGIN_JITTER = 0.3f;   // +/- 0.15 u per axis

    // --- block 5: smoke puff on obstacle hit (event-driven, one burst per hit; static ring texture, tinted to preserve the gray) ---
    private static final int IMPACT_BURST_COUNT = 10;
    private static final float IMPACT_GRAVITY_EFFECT = 0.1f;  // near-zero: smoke drifts, barely falls
    private static final float IMPACT_SPREAD = 3f;            // horizontal speed range: +/- 1.5 u/s
    private static final float IMPACT_UP_BASE = 1f;           // upward bias: 1..3 u/s
    private static final float IMPACT_UP_RAND = 2f;
    private static final float IMPACT_LIFE_BASE = 0.6f;       // 0.60..1.00 s
    private static final float IMPACT_LIFE_RAND = 0.4f;
    private static final float IMPACT_SCALE_BASE = 1.8f;      // 1.80..2.50 u: covers the 1.7 u player
    private static final float IMPACT_SCALE_RAND = 0.7f;
    private static final float IMPACT_ORIGIN_JITTER = 0.4f;   // +/- 0.2 u per axis
    private static final float IMPACT_Y_OFFSET = 0.85f;       // puff center at torso height (half the player)

    private static final class Obstacle {
        final Entity entity;
        final int lane;
        final boolean low;    // low  -> jump over it
        final float yOff;     // block 12: model base offset, feet glued via groundY(x,z) - yOff (0 for crates)
        float z;              // absolute world Z

        Obstacle(Entity entity, int lane, boolean low, float yOff, float z) {
            this.entity = entity;
            this.lane = lane;
            this.low = low;
            this.yOff = yOff;
            this.z = z;
        }
    }

    private static final class Pickup {
        final Entity entity;
        final int lane;
        float z;              // absolute world Z

        Pickup(Entity entity, int lane, float z) {
            this.entity = entity;
            this.lane = lane;
            this.z = z;
        }
    }

    /** Block 6b/6c: side decor entity - fixed world X, no lane, no collision. */
    private static final class Decor {
        final Entity entity;
        final float x;        // fixed world X (side of the road)
        final float yOff;     // model base offset: feet glued via groundY(x,z) - yOff
        float z;              // absolute world Z

        Decor(Entity entity, float x, float yOff, float z) {
            this.entity = entity;
            this.x = x;
            this.yOff = yOff;
            this.z = z;
        }
    }

    private final Player player;
    private final TexturedModel obstacleModel;
    private final TexturedModel[] carObstacleModels;  // block 12: 0 = yellow, 1 = cyan (LOW obstacles only)
    private final TexturedModel canModel;
    private final TexturedModel goalModel;
    private final List<TexturedModel> houseModels;  // block 9: 3 zone houses (index == style == zone)
    private final TexturedModel treeModel0;         // block 9: billboard cutout quads
    private final TexturedModel treeModel1;
    private final TexturedModel lampModel;
    private final TexturedModel carModel;       // block 6c
    private final TexturedModel trashModel;     // block 6c
    private final TexturedModel obeliskModel;   // block 6c
    private final Terrain terrain;
    private final FontType font;
    private final ParticleTexture sparkTexture;
    private final ParticleTexture smokeTexture;
    private final Random random = new Random();

    private final List<Entity> drawables = new ArrayList<>();
    // Block 17b: crate + retro-car obstacle entities render through
    // NormalMappingRenderer (the normal-mapping pass), not EntityRenderer.
    // Live list owned by RunnerCore; houses are back in drawables.
    private final List<Entity> normalMapEntities = new ArrayList<>();
    private final List<Obstacle> obstacles = new ArrayList<>();
    private final List<Pickup> pickupList = new ArrayList<>();
    private final List<Decor> decorList = new ArrayList<>();   // block 6b: swept like obstacles

    private State state = State.RUNNING;
    private int lane = 1;
    private float laneX = PLAYER_X;
    private float targetLaneX = PLAYER_X;
    private float jumpHeight;
    private float verticalSpeed;
    private boolean onGround = true;
    private float slideTimer;
    private final float baseScale;

    private float speed = BASE_SPEED;
    private float timeLeft = MAX_TIME;
    private float travelled;
    private int health = MAX_HEALTH;
    private int cans;
    private int sparkCount;    // cumulative sparks emitted this run (debug)
    private int impactCount;   // cumulative collision smoke puffs emitted this run (debug)
    private float invulnTimer;
    private float stumbleTimer;

    // --- pre-built track (one deterministic layout per RunnerCore) ---
    private final List<TrackBuilder.TrackEvent> layout;
    private int layoutCursor;
    // --- block 12: retro-car selection counters (index-derived, zero RNG) ---
    // Incremented ONLY for LOW materializations, reset in reset(). See
    // materialize() for the parity rule.
    private int lowMaterialized;   // how many LOW events have materialized (this run)
    private int carOrdinal;        // how many of them became cars (0 = first car)
    private Entity goalEntity;
    private float goalZ;
    private boolean goalSpawned;
    private int lastZone;

    private String hudMessage = "";
    private float messageTimer;
    private float messageColourR = 1f;
    private float messageColourG = 0.35f;
    private float messageColourB = 0.35f;

    private GUIText timerText;
    private GUIText statusText;
    private GUIText messageText;
    private String lastTimerString = "";
    private String lastStatusString = "";
    private String lastMessageString = "";

    /**
     * GUIText y so that the text's baseline sits at the given NDC height.
     * The font vertex shader does {@code position + translation * (2, -2)}
     * and the mesh is built with {@code properY = -2*y + 1}, which gives
     * {@code ndcBottom = 1 - 0.0534*fontSize - 2*ty}.
     */
    private static float textY(float fontSize, float ndcBottom) {
        return (1f - 0.0534f * fontSize - ndcBottom) / 2f;
    }

    /** GUIText x for a left aligned text whose left edge is at ndcLeft. */
    private static float textXLeft(float ndcLeft) {
        return (ndcLeft + 1f) / 2f;
    }

    /**
     * Block 9: decor models are injected - the three zone houses
     * ({@code houseModels}, indexed by {@link TrackBuilder.TrackEvent#style}
     * 0..2 = zone A/B/C, so {@code houseModels.get(style)}) plus the two
     * colour-keyed billboard tree quads ({@code treeModel0/1}, crossed quads
     * built by GameMain from {@code arbol1_cut/arbol2_cut}).
     *
     * Block 12: the two Meshy retro cars ({@code carYellowModel},
     * {@code carCyanModel}) ride along with the crate - SOME jump-over (LOW)
     * obstacles swap the crate for a car (selection rule in
     * {@link #materialize(TrackBuilder.TrackEvent)}); collision is untouched.
     */
    public RunnerCore(Player player, TexturedModel obstacleModel,
                      TexturedModel carYellowModel, TexturedModel carCyanModel,
                      TexturedModel canModel,
                      TexturedModel goalModel, List<TexturedModel> houseModels,
                      TexturedModel treeModel0, TexturedModel treeModel1, TexturedModel lampModel,
                      TexturedModel carModel, TexturedModel trashModel, TexturedModel obeliskModel,
                      Terrain terrain, FontType font,
                      ParticleTexture sparkTexture, ParticleTexture smokeTexture) {
        this.player = player;
        this.obstacleModel = obstacleModel;
        this.carObstacleModels = new TexturedModel[]{carYellowModel, carCyanModel};
        this.canModel = canModel;
        this.goalModel = goalModel;
        this.houseModels = houseModels;
        this.treeModel0 = treeModel0;
        this.treeModel1 = treeModel1;
        this.lampModel = lampModel;
        this.carModel = carModel;
        this.trashModel = trashModel;
        this.obeliskModel = obeliskModel;
        this.terrain = terrain;
        this.font = font;
        this.sparkTexture = sparkTexture;
        this.smokeTexture = smokeTexture;
        this.baseScale = player.getScale();
        this.player.setPosition(new Vector3f(PLAYER_X, groundY(PLAYER_X, PLAYER_Z), PLAYER_Z));
        this.player.setRotY(180f);
        // Block 7: the player is NOT added to drawables anymore — it is drawn
        // by GameMain as an animated rat GLB. The Player entity stays fully
        // alive for logic (collisions/HUD use this.player directly, camera
        // reads its position/rotY); drawables now only feeds renderScene.
        this.layout = new TrackBuilder().buildLayout();   // same track every run
    }

    /** Advances one frame: input, physics, world scroll, collisions, HUD. */
    public void update(float dt, GameInput input) {
        if (state == State.RUNNING) {
            step(dt, input);
        } else if (input.isRestartPressed()) {
            reset();
        }
        refreshHud();
    }

    /** Restores a full fresh run after WIN/LOSE (Enter or Start button). */
    private void reset() {
        for (Obstacle obstacle : obstacles) {
            // block 17b: obstacles live in normalMapEntities (remove from
            // drawables too — a remove of an absent element is a no-op)
            normalMapEntities.remove(obstacle.entity);
            drawables.remove(obstacle.entity);
        }
        obstacles.clear();
        for (Pickup pickup : pickupList) {
            drawables.remove(pickup.entity);
        }
        pickupList.clear();
        for (Decor decor : decorList) {
            drawables.remove(decor.entity);
        }
        decorList.clear();
        if (goalEntity != null) {
            drawables.remove(goalEntity);
            goalEntity = null;
        }

        state = State.RUNNING;
        lane = 1;
        laneX = PLAYER_X;
        targetLaneX = PLAYER_X;
        jumpHeight = 0f;
        verticalSpeed = 0f;
        onGround = true;
        slideTimer = 0f;
        invulnTimer = 0f;
        stumbleTimer = 0f;
        speed = TrackBuilder.speedAt(0f);
        timeLeft = MAX_TIME;
        travelled = 0f;
        health = MAX_HEALTH;
        cans = 0;
        sparkCount = 0;
        impactCount = 0;
        layoutCursor = 0;
        lowMaterialized = 0;   // block 12: fresh run -> the car/crate parity starts over
        carOrdinal = 0;
        goalSpawned = false;
        lastZone = 0;
        hudMessage = "";
        messageTimer = 0f;
        lastTimerString = "";
        lastStatusString = "";
        lastMessageString = "";
        player.setScale(baseScale);
        player.setPosition(new Vector3f(PLAYER_X, groundY(PLAYER_X, PLAYER_Z), PLAYER_Z));
    }

    private void step(float dt, GameInput input) {
        if (invulnTimer > 0f) invulnTimer -= dt;
        if (stumbleTimer > 0f) stumbleTimer -= dt;
        if (slideTimer > 0f) slideTimer -= dt;
        if (messageTimer > 0f) {
            messageTimer -= dt;
            if (messageTimer <= 0f) hudMessage = "";
        }

        timeLeft -= dt;
        if (timeLeft <= 0f) {
            timeLeft = 0f;
            lose("SE ACABO EL TIEMPO");
            return;
        }

        // --- intents ---
        int delta = input.getLaneDelta();
        if (delta != 0) {
            int next = Math.max(0, Math.min(LANE_COUNT - 1, lane + delta));
            if (next != lane) {
                lane = next;
                targetLaneX = laneXFor(lane);
            }
        }
        if (input.isJumpPressed() && onGround && slideTimer <= 0f) {
            verticalSpeed = JUMP_POWER;
            onGround = false;
        }
        if (input.isSlidePressed() && onGround && jumpHeight <= 0f) {
            slideTimer = SLIDE_TIME;
        }

        // --- lateral smoothing (lane change is never instant) ---
        laneX += (targetLaneX - laneX) * Math.min(1f, LANE_SMOOTHING * dt);

        // --- vertical ---
        if (!onGround) {
            verticalSpeed += GRAVITY * dt;
            jumpHeight += verticalSpeed * dt;
            if (jumpHeight <= 0f) {
                jumpHeight = 0f;
                verticalSpeed = 0f;
                onGround = true;
            }
        }

        // --- world scroll (the world moves, the player stays at z = PLAYER_Z) ---
        float effectiveSpeed = stumbleTimer > 0f ? 0f : speed;
        float scrolled = effectiveSpeed * dt;
        travelled += scrolled;

        if (travelled >= TRACK_LENGTH) {
            win("MILTON SE SALVO");
            return;
        }

        // --- zone speed (12 -> 14 -> 16 u/s) and zone banner ---
        speed = TrackBuilder.speedAt(travelled);
        int zone = TrackBuilder.zoneAt(travelled);
        if (zone != lastZone) {
            lastZone = zone;
            if (zone == 0) {
                showMessage(TrackBuilder.zoneName(zone), 1.6f, 0.55f, 0.85f, 1f);
            } else if (zone == 1) {
                showMessage(TrackBuilder.zoneName(zone), 1.6f, 1f, 0.85f, 0.4f);
            } else {
                showMessage(TrackBuilder.zoneName(zone), 1.6f, 1f, 0.55f, 0.35f);
            }
        }

        for (Obstacle obstacle : obstacles) {
            obstacle.z += scrolled;
        }
        for (Pickup pickup : pickupList) {
            pickup.z += scrolled;
        }
        for (Decor decor : decorList) {
            decor.z += scrolled;
        }
        if (goalEntity != null) {
            goalZ += scrolled;
            float goalX = laneXFor(1);   // the goal always sits in lane 1
            // burrow-hole decal is flat on the ground: keep it GOAL_HOLE_Y up (z-fight guard)
            goalEntity.setPosition(new Vector3f(goalX, groundY(goalX, goalZ) + GOAL_HOLE_Y, goalZ));
        }
        resolveObstacles();
        resolvePickups(dt);
        resolveDecor();

        // --- materialise the pre-built layout just ahead of the player ---
        // block 6b: decor streams in at 120 u so big side models never pop in close
        while (layoutCursor < layout.size()
                && layout.get(layoutCursor).trackDist - travelled
                        <= spawnAhead(layout.get(layoutCursor))) {
            materialize(layout.get(layoutCursor));
            layoutCursor++;
        }

        // --- place the player on the relief ---
        float y = groundY(laneX, PLAYER_Z) + jumpHeight;
        player.setPosition(new Vector3f(laneX, y, PLAYER_Z));
        player.setScale(slideTimer > 0f ? baseScale * 0.55f : baseScale);
    }

    private void resolveObstacles() {
        Iterator<Obstacle> it = obstacles.iterator();
        while (it.hasNext()) {
            Obstacle obstacle = it.next();
            if (obstacle.z > PLAYER_Z + DESPAWN_BEHIND) {
                normalMapEntities.remove(obstacle.entity);   // block 17b: no longer in drawables
                it.remove();
                continue;
            }
            float x = laneXFor(obstacle.lane);
            // block 12: - yOff mirrors Decor's feet rule (0 for crates = exact old behaviour)
            obstacle.entity.setPosition(new Vector3f(x, groundY(x, obstacle.z) - obstacle.yOff, obstacle.z));

            if (invulnTimer > 0f) {
                continue;
            }
            boolean cleared = (obstacle.low && jumpHeight > JUMP_CLEARANCE)
                    || (!obstacle.low && slideTimer > 0f);
            boolean hit = CollisionSystem.hitsSolid(obstacle.lane, lane, obstacle.z, PLAYER_Z, cleared);
            if (hit) {
                normalMapEntities.remove(obstacle.entity);   // block 17b: no longer in drawables
                it.remove();
                // block 5: one-shot smoke puff at the player's world position;
                // only reachable when invulnTimer == 0 (see the continue above)
                emitImpact(new Vector3f(laneX, groundY(laneX, PLAYER_Z) + jumpHeight + IMPACT_Y_OFFSET, PLAYER_Z));
                onHit();
            }
        }
    }

    /**
     * Turns one layout event into live entities. The exact track mapping is
     * {@code z = PLAYER_Z - (trackDist - travelled)}: when travelled equals
     * trackDist the object sits right at the player.
     */
    private void materialize(TrackBuilder.TrackEvent event) {
        float z = PLAYER_Z - (event.trackDist - travelled);
        if (TrackBuilder.isDecor(event.kind)) {
            materializeDecor(event, z);
            return;
        }
        float x = laneXFor(event.lane);
        if (event.kind == TrackBuilder.Kind.OBSTACLE_LOW
                || event.kind == TrackBuilder.Kind.OBSTACLE_HIGH) {
            boolean low = event.kind == TrackBuilder.Kind.OBSTACLE_LOW;
            TexturedModel model = obstacleModel;
            float scale = OBSTACLE_SCALE;
            float rotY = 0f;
            float yOff = 0f;   // crate path: EXACT current behaviour (crate.obj y_min*0.01 = -1, untouched)
            if (low) {
                // Block 12: SOME jump-over crates become retro-car traffic.
                // Selection is index-derived (zero RNG) and happens ONLY here at
                // materialization, so TrackBuilder's layout is untouched: count
                // LOW events as they materialize (trackDist order, stable spawn
                // order) and parity-check the 0-based counter BEFORE increment
                // -> indices 1, 3, 5 ... (the 2nd, 4th, 6th LOW) become cars,
                // the 1st LOW stays a crate. 47 LOW -> 23 cars + 24 low crates;
                // the car ordinal's parity picks the colour: even = yellow,
                // odd = cyan (23 cars -> 12 yellow / 11 cyan). Counters reset in reset().
                boolean isCar = (lowMaterialized % 2) == 1;
                lowMaterialized++;
                if (isCar) {
                    int car = carOrdinal % 2;
                    carOrdinal++;
                    model = carObstacleModels[car];
                    scale = CAR_OBSTACLE_SCALE[car];
                    yOff = CAR_OBSTACLE_BASE_Y[car] * scale;
                    rotY = CAR_YAW_DEG + CAR_FRONT_YAW_OFFSET;
                }
            }
            Entity entity = new Entity(model, new Vector3f(x, groundY(x, z) - yOff, z),
                    0, rotY, 0, scale);
            obstacles.add(new Obstacle(entity, event.lane, low, yOff, z));
            // block 17b: crates/retro cars are normal-mapped -> NM pass
            normalMapEntities.add(entity);
        } else if (event.kind == TrackBuilder.Kind.CAN_ROW) {
            for (int i = 0; i < TrackBuilder.CAN_ROW_SIZE; i++) {
                float canZ = z - i * TrackBuilder.CAN_ROW_SPACING;
                // cheese.obj's model-space centre is ~0 (measured -0.0004), so the
                // lift is just the hover constant (CAN_HOVER above the road; the
                // block 10 halving is what sets its current height).
                float y = groundY(x, canZ) + CAN_SCALE * CAN_CENTER_Y + CAN_HOVER;
                Entity entity = new Entity(canModel, new Vector3f(x, y, canZ),
                        0, random.nextFloat() * 360f, 0, CAN_SCALE);
                pickupList.add(new Pickup(entity, event.lane, canZ));
                drawables.add(entity);
            }
        } else {   // GOAL: Milton's burrow hole - reaching it means the mouse is safe
            goalZ = z;
            // flat ground decal: hover GOAL_HOLE_Y just above the road (z-fight guard)
            goalEntity = new Entity(goalModel,
                    new Vector3f(x, groundY(x, z) + GOAL_HOLE_Y, z),
                    0, 0, 0, GOAL_HOLE_U);
            drawables.add(goalEntity);
            goalSpawned = true;
        }
    }

    /**
     * Block 6b/6c/9: side decor sits at its own world X (never a lane), glued
     * to the relief with groundY and yawed per kind: house facades and parked
     * cars face/across the road, bins, obelisks and billboard trees stay
     * upright. Houses pick their Meshy house from {@code event.style} (0..2 =
     * zone A/B/C), trees their cutout quad (0/1).
     */
    private void materializeDecor(TrackBuilder.TrackEvent event, float z) {
        TexturedModel model;
        float scale;
        float baseY;
        float rotY;
        switch (event.kind) {
            case BUILDING_LEFT -> {
                model = houseModels.get(event.style);
                scale = HOUSE_SCALE[event.style];
                baseY = HOUSE_BASE_Y[event.style];
                rotY = HOUSE_ROT_LEFT + HOUSE_FRONT_YAW_OFFSET;
            }
            case BUILDING_RIGHT -> {
                model = houseModels.get(event.style);
                scale = HOUSE_SCALE[event.style];
                baseY = HOUSE_BASE_Y[event.style];
                rotY = HOUSE_ROT_RIGHT + HOUSE_FRONT_YAW_OFFSET;
            }
            case TREE_LEFT, TREE_RIGHT -> {
                model = event.style == 0 ? treeModel0 : treeModel1;
                scale = TREE_SCALE;
                baseY = 0f;      // cutout quad already sits on y = 0
                rotY = 0f;       // crossed quads read from every angle
            }
            case LAMP_LEFT, LAMP_RIGHT -> {
                model = lampModel; scale = LAMP_SCALE; baseY = LAMP_BASE_Y; rotY = 0f;
            }
            case CAR_LEFT, CAR_RIGHT -> {
                model = carModel; scale = CAR_SCALE; baseY = CAR_BASE_Y; rotY = CAR_ROT_Y;
            }
            case TRASH_LEFT, TRASH_RIGHT -> {
                model = trashModel; scale = TRASH_SCALE; baseY = TRASH_BASE_Y; rotY = 0f;
            }
            default -> {   // OBELISK_LEFT / OBELISK_RIGHT
                model = obeliskModel; scale = OBELISK_SCALE; baseY = OBELISK_BASE_Y; rotY = 0f;
            }
        }
        float yOff = baseY * scale;
        Entity entity = new Entity(model,
                new Vector3f(event.x, groundY(event.x, z) - yOff, z),
                0, rotY, 0, scale);
        decorList.add(new Decor(entity, event.x, yOff, z));
        // block 17b: houses are back in the plain EntityRenderer pass —
        // ALL decor goes to drawables (block-17 special-case reverted).
        drawables.add(entity);
    }

    /**
     * Block 6b: scroll the side decor with the world and sweep it once it is
     * behind the player - the same despawn rule obstacles use, so the
     * drawables/decor lists never grow unbounded. No collision here: decor
     * is pure scenery.
     */
    private void resolveDecor() {
        Iterator<Decor> it = decorList.iterator();
        while (it.hasNext()) {
            Decor decor = it.next();
            if (decor.z > PLAYER_Z + DESPAWN_BEHIND) {
                // block 17b: all decor lives in drawables again (houses
                // reverted from the normal-mapping pass).
                drawables.remove(decor.entity);
                it.remove();
                continue;
            }
            // scroll moved decor.z: keep the entity glued to the relief
            decor.entity.setPosition(new Vector3f(decor.x, groundY(decor.x, decor.z) - decor.yOff, decor.z));
        }
    }

    /** Spawn horizon per event: decor 120 u out, gameplay events 60 u. */
    private static float spawnAhead(TrackBuilder.TrackEvent event) {
        return TrackBuilder.isDecor(event.kind) ? DECOR_SPAWN_AHEAD : SPAWN_AHEAD;
    }

    /** Trigger collision: same lane + Z window picks the can, no matter jump height. */
    private void resolvePickups(float dt) {
        Iterator<Pickup> it = pickupList.iterator();
        while (it.hasNext()) {
            Pickup pickup = it.next();
            if (pickup.z > PLAYER_Z + DESPAWN_BEHIND) {
                drawables.remove(pickup.entity);
                it.remove();
                continue;
            }
            // scroll moved pickup.z: keep the entity glued to it
            float x = laneXFor(pickup.lane);
            float y = groundY(x, pickup.z) + CAN_SCALE * CAN_CENTER_Y + CAN_HOVER;
            pickup.entity.setPosition(new Vector3f(x, y, pickup.z));
            // spin in place so the wedge reads as a collectible
            pickup.entity.increaseRotation(0, CAN_SPIN_SPEED * dt, 0);

            if (CollisionSystem.overlapsTrigger(pickup.lane, lane, pickup.z, PLAYER_Z)) {
                Vector3f pickupPos = new Vector3f(pickup.entity.getPosition());  // can world pos
                drawables.remove(pickup.entity);
                it.remove();
                emitSparks(pickupPos);   // block 4: one-shot burst at the can
                onPickup();
            }
        }
    }

    /**
     * Block 4: one-shot burst of 12 spark particles at the collected can.
     * Event-driven only (never called from a per-frame loop): the Particle
     * constructor self-registers with ParticleMaster, which GameMain updates
     * and renders each frame.
     */
    private void emitSparks(Vector3f pos) {
        for (int i = 0; i < SPARK_BURST_COUNT; i++) {
            float x = pos.x + (random.nextFloat() - 0.5f) * SPARK_ORIGIN_JITTER;
            float y = pos.y + (random.nextFloat() - 0.5f) * SPARK_ORIGIN_JITTER;
            float z = pos.z + (random.nextFloat() - 0.5f) * SPARK_ORIGIN_JITTER;
            Vector3f velocity = new Vector3f(
                    (random.nextFloat() - 0.5f) * SPARK_SPREAD,
                    SPARK_UP_BASE + random.nextFloat() * SPARK_UP_RAND,
                    (random.nextFloat() - 0.5f) * SPARK_SPREAD);
            float lifeLength = SPARK_LIFE_BASE + random.nextFloat() * SPARK_LIFE_RAND;
            float scale = SPARK_SCALE_BASE + random.nextFloat() * SPARK_SCALE_RAND;
            float rotation = random.nextFloat() * 360f;
            new Particle(sparkTexture, new Vector3f(x, y, z), velocity,
                    SPARK_GRAVITY_EFFECT, lifeLength, rotation, scale);
            sparkCount++;
        }
    }

    /**
     * Block 5: one-shot burst of 10 smoke particles at the collision point.
     * Event-driven only (never called from a per-frame loop): fires once per
     * actual hit transition — {@link #resolveObstacles()} skips while the
     * invulnerability window is active, so no re-emits during i-frames.
     * The single-frame smoke ring texture (smoke_10.png, 1 row) is tinted by
     * the ParticleTexture so the rendered smoke keeps its previous gray.
     */
    private void emitImpact(Vector3f pos) {
        for (int i = 0; i < IMPACT_BURST_COUNT; i++) {
            float x = pos.x + (random.nextFloat() - 0.5f) * IMPACT_ORIGIN_JITTER;
            float y = pos.y + (random.nextFloat() - 0.5f) * IMPACT_ORIGIN_JITTER;
            float z = pos.z + (random.nextFloat() - 0.5f) * IMPACT_ORIGIN_JITTER;
            // world scrolls past the player: billow mostly upward/outward
            Vector3f velocity = new Vector3f(
                    (random.nextFloat() - 0.5f) * IMPACT_SPREAD,
                    IMPACT_UP_BASE + random.nextFloat() * IMPACT_UP_RAND,
                    (random.nextFloat() - 0.5f) * IMPACT_SPREAD);
            float lifeLength = IMPACT_LIFE_BASE + random.nextFloat() * IMPACT_LIFE_RAND;
            float scale = IMPACT_SCALE_BASE + random.nextFloat() * IMPACT_SCALE_RAND;
            float rotation = random.nextFloat() * 360f;
            new Particle(smokeTexture, new Vector3f(x, y, z), velocity,
                    IMPACT_GRAVITY_EFFECT, lifeLength, rotation, scale);
            impactCount++;
        }
    }

    /** Observable response: counter goes up, every 10 cans restore a life. */
    private void onPickup() {
        cans++;
        // The HUD counter is the per-can feedback; only the life milestone
        // deserves a centre-screen message (and it must not cover Milton).
        if (cans % (int) CANS_PER_LIFE == 0) {
            if (health < MAX_HEALTH) {
                health++;
                showMessage(String.format("+1 VIDA  (%d QUESOS)", cans),
                        MESSAGE_TIME * 1.5f, 0.45f, 1f, 0.45f);
            } else {
                showMessage(String.format("VIDAS AL MAXIMO  (%d QUESOS)", cans),
                        MESSAGE_TIME * 1.5f, 1f, 1f, 0.4f);
            }
        }
    }

    /** Observable response: lose a segment, blink, stumble (world stops 0.5 s). */
    private void onHit() {
        health--;
        invulnTimer = INVULN_TIME;
        stumbleTimer = STUMBLE_TIME;
        showMessage("IMPACTO!", MESSAGE_TIME, 1f, 0.35f, 0.35f);
        if (health <= 0) {
            health = 0;
            lose("MILTON ESTA AGOTADO");
        }
    }

    private void win(String message) {
        state = State.WIN;
        showMessage(message + " - ENTER PARA REINTENTAR", 999f, 0.45f, 1f, 0.45f);
    }

    private void lose(String message) {
        state = State.LOSE;
        showMessage(message + " - ENTER PARA REINTENTAR", 999f, 1f, 0.35f, 0.35f);
    }

    private void showMessage(String message, float duration) {
        showMessage(message, duration, 1f, 0.35f, 0.35f);
    }

    private void showMessage(String message, float duration, float r, float g, float b) {
        hudMessage = message;
        messageTimer = duration;
        messageColourR = r;
        messageColourG = g;
        messageColourB = b;
    }

    public static float laneXFor(int lane) {
        return PLAYER_X + (lane - (LANE_COUNT - 1) / 2f) * LANE_WIDTH;
    }

    private float groundY(float x, float z) {
        return terrain.containsPosition(x, z) ? terrain.getHeightOfTerrain(x, z) : 0f;
    }

    /** Debug accessor for GameMain's per-second {@code gnd=} field. */
    public float getGroundHeight() {
        return groundY(laneX, PLAYER_Z);
    }

    // ------------------------------------------------------------------ HUD

    private void refreshHud() {
        int seconds = (int) Math.ceil(timeLeft);
        String timer = String.format("TIEMPO  %d:%02d", seconds / 60, seconds % 60);
        if (!timer.equals(lastTimerString)) {
            lastTimerString = timer;
            if (timerText != null) timerText.remove();
            timerText = new GUIText(timer, 3f, font,
                    new Vector2f(0f, textY(3f, 0.80f)), 1f, true);
            timerText.setColour(1f, 1f, 1f);
        }

        String status = String.format("QUESOS %d   SALUD %d/%d", cans, health, MAX_HEALTH);
        if (!status.equals(lastStatusString)) {
            lastStatusString = status;
            if (statusText != null) statusText.remove();
            statusText = new GUIText(status, 1.8f, font,
                    new Vector2f(textXLeft(-0.95f), textY(1.8f, 0.78f)), 1f, false);
            statusText.setColour(1f, 1f, 0.4f);
        }

        boolean needMessage = !hudMessage.isEmpty();
        if (needMessage && !hudMessage.equals(lastMessageString)) {
            lastMessageString = hudMessage;
            if (messageText != null) messageText.remove();
            messageText = new GUIText(hudMessage, 4f, font,
                    new Vector2f(0f, textY(4f, -0.12f)), 1f, true);
            messageText.setColour(messageColourR, messageColourG, messageColourB);
        } else if (!needMessage && messageText != null) {
            messageText.remove();
            messageText = null;
            lastMessageString = "";
        }
    }

    // ------------------------------------------------------------- accessors

    public List<Entity> getDrawables() {
        return drawables;
    }

    /**
     * Block 17b: live list of crate + retro-car obstacle entities for the
     * normal-mapping pass. Owned by RunnerCore (spawn/despawn/reset maintain
     * it); callers read it only — MasterRenderer never clears the list it is
     * given. Houses are back in {@link #getDrawables()}.
     */
    public List<Entity> getNormalMapEntities() {
        return normalMapEntities;
    }

    /**
     * Block 15: world anchor (center) of Milton's burrow hole, for the two
     * welcome rats that dance at the entrance once the goal materializes.
     * Returns the LIVE position Vector3f owned by the goal Entity — callers
     * must only read it, never mutate it. Returns {@code null} while the goal
     * has not spawned yet, and again after {@code reset()} (which sets
     * {@code goalEntity = null}), so callers need no extra reset handling:
     * a null anchor simply means "don't draw the welcome rats".
     */
    public Vector3f getGoalAnchor() {
        return goalEntity != null ? goalEntity.getPosition() : null;
    }

    public State getState() {
        return state;
    }

    /**
     * Block 8: animation intent for the rendered rat GLB (see {@link AnimIntent}).
     *
     * <p>Resolved LAZILY on every call instead of being cached in a field by
     * {@link #step(float, GameInput)}: this class owns the state machine and
     * the timers, and a getter can never go stale - notably in READY, where
     * {@code step()} does not run. Priority:</p>
     * <ol>
     *   <li>WIN -&gt; DANCE (celebration loop)</li>
     *   <li>LOSE -&gt; DEAD (lose screen)</li>
     *   <li>i-frames ({@link #invulnTimer} &gt; 0) -&gt; KNOCK. Verified:
     *       {@code invulnTimer} is only ever set to a positive value in
     *       {@link #onHit()}, which is called solely from the hit transition
     *       in {@code resolveObstacles()} - the same place {@code emitImpact}
     *       fires - so i-frames always mean "a collision just happened" and
     *       cover exactly the 1.2 s window after it. (WIN/LOSE are checked
     *       first, so the remaining states are RUNNING/READY.)</li>
     *   <li>airborne -&gt; JUMP. Uses {@link #onGround} rather than
     *       {@code jumpHeight > 0}: the jump flips {@code onGround} false on
     *       the very frame it starts, while {@code jumpHeight} only grows
     *       after the next integration step.</li>
     *   <li>{@link #slideTimer} &gt; 0 -&gt; SLIDE</li>
     *   <li>otherwise -&gt; RUN (READY included: the rat runs in place, as
     *       before block 8)</li>
     * </ol>
     * {@link #reset()} restores RUNNING and zeroes every timer above, so the
     * intent returns to RUN after a restart.
     */
    public AnimIntent getAnimIntent() {
        if (state == State.WIN) return AnimIntent.DANCE;
        if (state == State.LOSE) return AnimIntent.DEAD;
        if (invulnTimer > 0f) return AnimIntent.KNOCK;   // i-frames == just got hit (onHit only)
        if (!onGround) return AnimIntent.JUMP;
        if (slideTimer > 0f) return AnimIntent.SLIDE;
        return AnimIntent.RUN;
    }

    public float getTimeLeft() {
        return timeLeft;
    }

    public int getHealth() {
        return health;
    }

    public int getCans() {
        return cans;
    }

    /** Cumulative spark particles emitted this run (debug). */
    public int getSparkCount() {
        return sparkCount;
    }

    /** Cumulative collision smoke puffs emitted this run (debug). */
    public int getImpactCount() {
        return impactCount;
    }

    public int getObstacleCount() {
        return obstacles.size();
    }

    public float getJumpHeight() {
        return jumpHeight;
    }

    public float getLaneX() {
        return laneX;
    }

    public float getTravelled() {
        return travelled;
    }

    /** Block 14: current track zone (0 = A/morning, 1 = B/day, 2 = C/night). */
    public int getZone() {
        return TrackBuilder.zoneAt(travelled);
    }

    /** One-line layout summary for the startup log (block 6b/6c/9: decor totals). */
    public String describeLayout() {
        int obstacleCount = 0;
        int highCount = 0;
        int canRowCount = 0;
        int goalCount = 0;
        int buildingCount = 0;
        int lampCount = 0;
        int carCount = 0;
        int trashCount = 0;
        int obeliskCount = 0;
        int treeCount = 0;
        int treesLeft = 0;
        int treesRight = 0;
        int[] houseStyle = new int[TrackBuilder.HOUSE_STYLE_COUNT];
        for (TrackBuilder.TrackEvent event : layout) {
            if (event.kind == TrackBuilder.Kind.OBSTACLE_LOW
                    || event.kind == TrackBuilder.Kind.OBSTACLE_HIGH) {
                obstacleCount++;
                if (event.kind == TrackBuilder.Kind.OBSTACLE_HIGH) highCount++;
            } else if (event.kind == TrackBuilder.Kind.CAN_ROW) {
                canRowCount++;
            } else if (TrackBuilder.isDecor(event.kind)) {
                if (event.kind == TrackBuilder.Kind.LAMP_LEFT
                        || event.kind == TrackBuilder.Kind.LAMP_RIGHT) {
                    lampCount++;
                } else if (event.kind == TrackBuilder.Kind.BUILDING_LEFT
                        || event.kind == TrackBuilder.Kind.BUILDING_RIGHT) {
                    buildingCount++;
                    houseStyle[event.style]++;
                } else if (event.kind == TrackBuilder.Kind.CAR_LEFT
                        || event.kind == TrackBuilder.Kind.CAR_RIGHT) {
                    carCount++;
                } else if (event.kind == TrackBuilder.Kind.TRASH_LEFT
                        || event.kind == TrackBuilder.Kind.TRASH_RIGHT) {
                    trashCount++;
                } else if (event.kind == TrackBuilder.Kind.OBELISK_LEFT
                        || event.kind == TrackBuilder.Kind.OBELISK_RIGHT) {
                    obeliskCount++;
                } else {   // TREE_LEFT / TREE_RIGHT (block 9)
                    treeCount++;
                    if (event.kind == TrackBuilder.Kind.TREE_LEFT) treesLeft++;
                    else treesRight++;
                }
            } else {
                goalCount++;
            }
        }
        return String.format("track: %d obstacles (%d high), %d can rows, %d goal, %d buildings (houses A %d B %d C %d), %d lamps, %d cars, %d trash, %d obelisks, %d trees (L %d / R %d)",
                obstacleCount, highCount, canRowCount, goalCount, buildingCount,
                houseStyle[0], houseStyle[1], houseStyle[2],
                lampCount, carCount, trashCount, obeliskCount, treeCount,
                treesLeft, treesRight);
    }

    public boolean isGamepadHintVisible(GameInput input) {
        return !input.isGamepadConnected();
    }
}
