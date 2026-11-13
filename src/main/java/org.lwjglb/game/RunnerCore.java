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

public final class RunnerCore {

    public enum State { READY, RUNNING, WIN, LOSE }

    public enum AnimIntent { RUN, JUMP, SLIDE, KNOCK, DEAD, DANCE }

    public static final int LANE_COUNT = 3;
    public static final float LANE_WIDTH = 4.0f;
    public static final float BASE_SPEED = 17.0f;      
    public static final float MAX_TIME = 210f;         
    public static final float TRACK_LENGTH = 2500f;    
    public static final int MAX_HEALTH = 3;
    public static final float CANS_PER_LIFE = 10f;

    private static final float GRAVITY = -50f;
    private static final float JUMP_POWER = 26f;
    private static final float SLIDE_TIME = 0.6f;
    private static final float JUMP_CLEARANCE = 0.9f;   
    private static final float LANE_SMOOTHING = 14f;

    public static final float PLAYER_X = 75f;           
    public static final float PLAYER_Z = -75f;
    private static final float SPAWN_AHEAD = 60f;
    private static final float DESPAWN_BEHIND = 12f;
    private static final float OBSTACLE_SCALE = 0.01f;  

    static final float CAN_SCALE = 0.52244f;   
    static final float CAN_CENTER_Y = -0.0004f; 
    private static final float CAN_HOVER = 0.57f;
    private static final float CAN_SPIN_SPEED = 90f;    

    static final float GOAL_HOLE_U = 6f;              
    private static final float GOAL_HOLE_Y = 0.05f;   

    private static final float DECOR_SPAWN_AHEAD = 120f;  
    static final float[] HOUSE_SCALE = TrackBuilder.HOUSE_SCALE;
    static final float[] HOUSE_BASE_Y = TrackBuilder.HOUSE_MODEL_Y_MIN;
    private static final float HOUSE_ROT_LEFT = 90f;      
    private static final float HOUSE_ROT_RIGHT = -90f;    
    private static final float HOUSE_FRONT_YAW_OFFSET = 0f;   
    private static final float TREE_SCALE = 6f;           
    private static final float LAMP_SCALE = 0.285f;       
    private static final float LAMP_BASE_Y = -0.30f;      

    private static final float CAR_SCALE = 0.266f;        
    private static final float CAR_BASE_Y = 0f;           
    private static final float CAR_ROT_Y = 90f;           
    private static final float TRASH_SCALE = 0.15f;       
    private static final float TRASH_BASE_Y = 0.222803f;  
    private static final float OBELISK_SCALE = 0.0265f;   
    private static final float OBELISK_BASE_Y = -1.869767f; 

    static final float[] CAR_OBSTACLE_SCALE = {2.60f, 2.40f};            
    static final float[] CAR_OBSTACLE_BASE_Y = {-0.282791f, -0.284211f}; 
    private static final float CAR_YAW_DEG = 90f;   
    private static final float CAR_FRONT_YAW_OFFSET = 0f;   

    private static final float INVULN_TIME = 1.2f;
    private static final float STUMBLE_TIME = 0.5f;     
    private static final float MESSAGE_TIME = 0.9f;

    private static final int SPARK_BURST_COUNT = 12;
    private static final float SPARK_GRAVITY_EFFECT = 1.5f;  
    private static final float SPARK_SPREAD = 4f;            
    private static final float SPARK_UP_BASE = 2f;           
    private static final float SPARK_UP_RAND = 3f;
    private static final float SPARK_LIFE_BASE = 0.35f;      
    private static final float SPARK_LIFE_RAND = 0.25f;
    private static final float SPARK_SCALE_BASE = 0.60f;     
    private static final float SPARK_SCALE_RAND = 0.4f;
    private static final float SPARK_ORIGIN_JITTER = 0.3f;   

    private static final int IMPACT_BURST_COUNT = 10;
    private static final float IMPACT_GRAVITY_EFFECT = 0.1f;  
    private static final float IMPACT_SPREAD = 3f;            
    private static final float IMPACT_UP_BASE = 1f;           
    private static final float IMPACT_UP_RAND = 2f;
    private static final float IMPACT_LIFE_BASE = 0.6f;       
    private static final float IMPACT_LIFE_RAND = 0.4f;
    private static final float IMPACT_SCALE_BASE = 1.8f;      
    private static final float IMPACT_SCALE_RAND = 0.7f;
    private static final float IMPACT_ORIGIN_JITTER = 0.4f;   
    private static final float IMPACT_Y_OFFSET = 0.85f;       

    private static final class Obstacle {
        final Entity entity;
        final int lane;
        final boolean low;    
        final float yOff;     
        float z;              

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
        float z;              

        Pickup(Entity entity, int lane, float z) {
            this.entity = entity;
            this.lane = lane;
            this.z = z;
        }
    }

    private static final class Decor {
        final Entity entity;
        final float x;        
        final float yOff;     
        float z;              

        Decor(Entity entity, float x, float yOff, float z) {
            this.entity = entity;
            this.x = x;
            this.yOff = yOff;
            this.z = z;
        }
    }

    private final Player player;
    private final TexturedModel obstacleModel;
    private final TexturedModel[] carObstacleModels;  
    private final TexturedModel canModel;
    private final TexturedModel goalModel;
    private final List<TexturedModel> houseModels;  
    private final TexturedModel treeModel0;         
    private final TexturedModel treeModel1;
    private final TexturedModel lampModel;
    private final TexturedModel carModel;       
    private final TexturedModel trashModel;     
    private final TexturedModel obeliskModel;   
    private final Terrain terrain;
    private final FontType font;
    private final ParticleTexture sparkTexture;
    private final ParticleTexture smokeTexture;
    private final Random random = new Random();

    private final List<Entity> drawables = new ArrayList<>();
    private final List<Entity> normalMapEntities = new ArrayList<>();
    private final List<Obstacle> obstacles = new ArrayList<>();
    private final List<Pickup> pickupList = new ArrayList<>();
    private final List<Decor> decorList = new ArrayList<>();   

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
    private int sparkCount;    
    private int impactCount;   
    private float invulnTimer;
    private float stumbleTimer;

    private final List<TrackBuilder.TrackEvent> layout;
    private int layoutCursor;
    private int lowMaterialized;   
    private int carOrdinal;        
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

    private static float textY(float fontSize, float ndcBottom) {
        return (1f - 0.0534f * fontSize - ndcBottom) / 2f;
    }

    private static float textXLeft(float ndcLeft) {
        return (ndcLeft + 1f) / 2f;
    }

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
        this.layout = new TrackBuilder().buildLayout();   
    }

    public void update(float dt, GameInput input) {
        if (state == State.RUNNING) {
            step(dt, input);
        } else if (input.isRestartPressed()) {
            reset();
        }
        refreshHud();
    }

    private void reset() {
        for (Obstacle obstacle : obstacles) {
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
        lowMaterialized = 0;   
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

        laneX += (targetLaneX - laneX) * Math.min(1f, LANE_SMOOTHING * dt);

        if (!onGround) {
            verticalSpeed += GRAVITY * dt;
            jumpHeight += verticalSpeed * dt;
            if (jumpHeight <= 0f) {
                jumpHeight = 0f;
                verticalSpeed = 0f;
                onGround = true;
            }
        }

        float effectiveSpeed = stumbleTimer > 0f ? 0f : speed;
        float scrolled = effectiveSpeed * dt;
        travelled += scrolled;

        if (travelled >= TRACK_LENGTH) {
            win("MILTON SE SALVO");
            return;
        }

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
            float goalX = laneXFor(1);   
            goalEntity.setPosition(new Vector3f(goalX, groundY(goalX, goalZ) + GOAL_HOLE_Y, goalZ));
        }
        resolveObstacles();
        resolvePickups(dt);
        resolveDecor();

        while (layoutCursor < layout.size()
                && layout.get(layoutCursor).trackDist - travelled
                        <= spawnAhead(layout.get(layoutCursor))) {
            materialize(layout.get(layoutCursor));
            layoutCursor++;
        }

        float y = groundY(laneX, PLAYER_Z) + jumpHeight;
        player.setPosition(new Vector3f(laneX, y, PLAYER_Z));
        player.setScale(slideTimer > 0f ? baseScale * 0.55f : baseScale);
    }

    private void resolveObstacles() {
        Iterator<Obstacle> it = obstacles.iterator();
        while (it.hasNext()) {
            Obstacle obstacle = it.next();
            if (obstacle.z > PLAYER_Z + DESPAWN_BEHIND) {
                normalMapEntities.remove(obstacle.entity);   
                it.remove();
                continue;
            }
            float x = laneXFor(obstacle.lane);
            obstacle.entity.setPosition(new Vector3f(x, groundY(x, obstacle.z) - obstacle.yOff, obstacle.z));

            if (invulnTimer > 0f) {
                continue;
            }
            boolean cleared = (obstacle.low && jumpHeight > JUMP_CLEARANCE)
                    || (!obstacle.low && slideTimer > 0f);
            boolean hit = CollisionSystem.hitsSolid(obstacle.lane, lane, obstacle.z, PLAYER_Z, cleared);
            if (hit) {
                normalMapEntities.remove(obstacle.entity);   
                it.remove();
                emitImpact(new Vector3f(laneX, groundY(laneX, PLAYER_Z) + jumpHeight + IMPACT_Y_OFFSET, PLAYER_Z));
                onHit();
            }
        }
    }

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
            float yOff = 0f;   
            if (low) {
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
            normalMapEntities.add(entity);
        } else if (event.kind == TrackBuilder.Kind.CAN_ROW) {
            for (int i = 0; i < TrackBuilder.CAN_ROW_SIZE; i++) {
                float canZ = z - i * TrackBuilder.CAN_ROW_SPACING;
                float y = groundY(x, canZ) + CAN_SCALE * CAN_CENTER_Y + CAN_HOVER;
                Entity entity = new Entity(canModel, new Vector3f(x, y, canZ),
                        0, random.nextFloat() * 360f, 0, CAN_SCALE);
                pickupList.add(new Pickup(entity, event.lane, canZ));
                drawables.add(entity);
            }
        } else {   
            goalZ = z;
            goalEntity = new Entity(goalModel,
                    new Vector3f(x, groundY(x, z) + GOAL_HOLE_Y, z),
                    0, 0, 0, GOAL_HOLE_U);
            drawables.add(goalEntity);
            goalSpawned = true;
        }
    }

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
                baseY = 0f;      
                rotY = 0f;       
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
            default -> {   
                model = obeliskModel; scale = OBELISK_SCALE; baseY = OBELISK_BASE_Y; rotY = 0f;
            }
        }
        float yOff = baseY * scale;
        Entity entity = new Entity(model,
                new Vector3f(event.x, groundY(event.x, z) - yOff, z),
                0, rotY, 0, scale);
        decorList.add(new Decor(entity, event.x, yOff, z));
        drawables.add(entity);
    }

    private void resolveDecor() {
        Iterator<Decor> it = decorList.iterator();
        while (it.hasNext()) {
            Decor decor = it.next();
            if (decor.z > PLAYER_Z + DESPAWN_BEHIND) {
                drawables.remove(decor.entity);
                it.remove();
                continue;
            }
            decor.entity.setPosition(new Vector3f(decor.x, groundY(decor.x, decor.z) - decor.yOff, decor.z));
        }
    }

    private static float spawnAhead(TrackBuilder.TrackEvent event) {
        return TrackBuilder.isDecor(event.kind) ? DECOR_SPAWN_AHEAD : SPAWN_AHEAD;
    }

    private void resolvePickups(float dt) {
        Iterator<Pickup> it = pickupList.iterator();
        while (it.hasNext()) {
            Pickup pickup = it.next();
            if (pickup.z > PLAYER_Z + DESPAWN_BEHIND) {
                drawables.remove(pickup.entity);
                it.remove();
                continue;
            }
            float x = laneXFor(pickup.lane);
            float y = groundY(x, pickup.z) + CAN_SCALE * CAN_CENTER_Y + CAN_HOVER;
            pickup.entity.setPosition(new Vector3f(x, y, pickup.z));
            pickup.entity.increaseRotation(0, CAN_SPIN_SPEED * dt, 0);

            if (CollisionSystem.overlapsTrigger(pickup.lane, lane, pickup.z, PLAYER_Z)) {
                Vector3f pickupPos = new Vector3f(pickup.entity.getPosition());  
                drawables.remove(pickup.entity);
                it.remove();
                emitSparks(pickupPos);   
                onPickup();
            }
        }
    }

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

    private void emitImpact(Vector3f pos) {
        for (int i = 0; i < IMPACT_BURST_COUNT; i++) {
            float x = pos.x + (random.nextFloat() - 0.5f) * IMPACT_ORIGIN_JITTER;
            float y = pos.y + (random.nextFloat() - 0.5f) * IMPACT_ORIGIN_JITTER;
            float z = pos.z + (random.nextFloat() - 0.5f) * IMPACT_ORIGIN_JITTER;
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

    private void onPickup() {
        cans++;
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

    public float getGroundHeight() {
        return groundY(laneX, PLAYER_Z);
    }


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


    public List<Entity> getDrawables() {
        return drawables;
    }

    public List<Entity> getNormalMapEntities() {
        return normalMapEntities;
    }

    public Vector3f getGoalAnchor() {
        return goalEntity != null ? goalEntity.getPosition() : null;
    }

    public State getState() {
        return state;
    }

    public AnimIntent getAnimIntent() {
        if (state == State.WIN) return AnimIntent.DANCE;
        if (state == State.LOSE) return AnimIntent.DEAD;
        if (invulnTimer > 0f) return AnimIntent.KNOCK;   
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

    public int getSparkCount() {
        return sparkCount;
    }

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

    public int getZone() {
        return TrackBuilder.zoneAt(travelled);
    }

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
                } else {   
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
