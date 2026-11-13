package org.lwjglb.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class TrackBuilder {

    public static final int CAN_ROW_SIZE = 5;
    public static final float CAN_ROW_SPACING = 2.5f;

    public enum Kind {
        OBSTACLE_LOW, OBSTACLE_HIGH, CAN_ROW, GOAL,
        BUILDING_LEFT, BUILDING_RIGHT, LAMP_LEFT, LAMP_RIGHT,
        CAR_LEFT, CAR_RIGHT, TRASH_LEFT, TRASH_RIGHT,
        OBELISK_LEFT, OBELISK_RIGHT, TREE_LEFT, TREE_RIGHT
    }

    public static final class TrackEvent {
        public final float trackDist;
        public final int lane;
        public final Kind kind;
        public final float x;
        public final int style;

        public TrackEvent(float trackDist, int lane, Kind kind) {
            this(trackDist, lane, kind, Float.NaN, 0);
        }

        public TrackEvent(float trackDist, int lane, Kind kind, float x) {
            this(trackDist, lane, kind, x, 0);
        }

        public TrackEvent(float trackDist, int lane, Kind kind, float x, int style) {
            this.trackDist = trackDist;
            this.lane = lane;
            this.kind = kind;
            this.x = x;
            this.style = style;
        }
    }

    private static final float ZONE_A_END = 800f;
    private static final float ZONE_B_END = 1800f;

    private static final int ZONE_COUNT = 3;
    private static final int LANE_COUNT = 3;
    private static final long SEED = 20261113L;
    private static final int MAX_TRIES = 10;             
    private static final float FIRST_EVENT_MIN = 40f;    
    private static final float FINISH_MARGIN = 40f;      
    private static final float CAN_OBSTACLE_CLEAR = 10f; 

    private static final float DECOR_FIRST_MIN = 60f;    
    private static final float BUILDING_GAP_MIN = 19f;   
    private static final float BUILDING_GAP_MAX = 27f;   
    private static final float LAMP_GAP_MIN = 30f;       
    private static final float LAMP_GAP_MAX = 50f;
    private static final float BUILDING_X_LEFT = 45f;    
    private static final float BUILDING_X_RIGHT = 99f;   
    private static final float BUILDING_X_SPAN = 6f;
    private static final float LAMP_X_LEFT = 66f;        
    private static final float LAMP_X_RIGHT = 84f;
    private static final float LAMP_POLE_R = 0.4f;       

    public static final int HOUSE_STYLE_COUNT = 3;
    public static final float[] HOUSE_MODEL_DX = {
            1.903658f, 1.898669f, 1.833186f};
    public static final float[] HOUSE_MODEL_DY = {
            0.914719f, 0.838279f, 1.039520f};
    public static final float[] HOUSE_MODEL_DZ_FRONT = {
            0.749613f, 0.901883f, 0.949783f};
    public static final float[] HOUSE_MODEL_DZ_BACK = {
            0.748895f, 0.899088f, 0.950935f};
    public static final float[] HOUSE_MODEL_Y_MIN = {
            -0.452940f, -0.419412f, -0.521438f};
    public static final float[] HOUSE_SCALE = {
            6.303650f, 6.320216f, 6.545980f};

    private static final float TREE_HALF_U = 3f;          
    private static final float TREE_X_LEFT_MIN = BUILDING_X_LEFT;                     
    private static final float TREE_X_LEFT_MAX = BUILDING_X_LEFT + BUILDING_X_SPAN;   
    private static final float TREE_X_RIGHT_MIN = BUILDING_X_RIGHT;                   
    private static final float TREE_X_RIGHT_MAX = BUILDING_X_RIGHT + BUILDING_X_SPAN; 

    private static final float CAR_GAP_MIN = 35f;        
    private static final float CAR_GAP_MAX = 60f;
    private static final float CAR_X_LEFT = 64f;         
    private static final float CAR_X_RIGHT = 86f;        
    private static final float CAR_ROAD_HALF_W = 0.819f; 
    private static final float TRASH_PROB = 0.55f;       
    private static final float TRASH_X_LEFT = 67.3f;     
    private static final float TRASH_X_RIGHT = 82.7f;
    private static final float TRASH_ROAD_HALF_W = 0.268f; 
    private static final float OBELISK_FIRST_MIN = 1800f;  
    private static final float OBELISK_GAP_MIN = 120f;
    private static final float OBELISK_GAP_MAX = 200f;
    private static final float OBELISK_X_LEFT = 34f;     
    private static final float OBELISK_X_RIGHT = 116f;
    private static final float OBELISK_ROAD_HALF_W = 2.85f; 

    private static final float[] GAP_MIN = {30f, 24f, 19f};
    private static final float[] GAP_MAX = {45f, 34f, 27f};
    private static final float[] LOW_PROB = {0.75f, 0.50f, 0.40f};
    private static final float[] DOUBLE_PROB = {0f, 0.25f, 0.40f};
    private static final float[] CAN_GAP_MIN = {40f, 35f, 30f};
    private static final float[] CAN_GAP_MAX = {55f, 50f, 45f};
    private static final float[] ZONE_START = {0f, ZONE_A_END, ZONE_B_END};
    private static final float[] ZONE_END = {ZONE_A_END, ZONE_B_END, RunnerCore.TRACK_LENGTH};


    public static float speedAt(float travelled) {
        if (travelled < ZONE_A_END) return 17f;
        if (travelled < ZONE_B_END) return 20f;
        return 23f;
    }

    public static int zoneAt(float travelled) {
        if (travelled < ZONE_A_END) return 0;
        if (travelled < ZONE_B_END) return 1;
        return 2;
    }

    public static String zoneName(int zone) {
        if (zone == 0) return "ZONA A - RESIDENCIAL";
        if (zone == 1) return "ZONA B - CENTRO";
        return "ZONA C - PLAZA";
    }


    public List<TrackEvent> buildLayout() {
        Random rng = new Random(SEED);
        List<TrackEvent> events = new ArrayList<>();

        for (int zone = 0; zone < ZONE_COUNT; zone++) {
            float cursor = Math.max(ZONE_START[zone], FIRST_EVENT_MIN);
            while (true) {
                float dist = cursor + nextGap(rng, GAP_MIN[zone], GAP_MAX[zone]);
                if (dist > obstacleLimit(zone)) break;
                List<TrackEvent> candidate = null;
                for (int t = 0; t < MAX_TRIES; t++) {
                    if (t > 0) {
                        dist = cursor + nextGap(rng, GAP_MIN[zone], GAP_MAX[zone]);
                        if (dist > obstacleLimit(zone)) break;
                    }
                    List<TrackEvent> tryGroup = makeObstacles(rng, dist, zone);
                    if (accepts(events, tryGroup)) {
                        candidate = tryGroup;
                        break;
                    }
                }
                if (candidate != null) events.addAll(candidate);
                cursor = dist;
            }

            float canCursor = Math.max(ZONE_START[zone], FIRST_EVENT_MIN);
            while (true) {
                float dist = canCursor + nextGap(rng, CAN_GAP_MIN[zone], CAN_GAP_MAX[zone]);
                if (dist > ZONE_END[zone]) break;
                TrackEvent can = null;
                for (int t = 0; t < MAX_TRIES; t++) {
                    if (t > 0) {
                        dist = canCursor + nextGap(rng, CAN_GAP_MIN[zone], CAN_GAP_MAX[zone]);
                        if (dist > ZONE_END[zone]) break;
                    }
                    TrackEvent candidate = new TrackEvent(dist, rng.nextInt(LANE_COUNT), Kind.CAN_ROW);
                    if (accepts(events, singleton(candidate))) {
                        can = candidate;
                        break;
                    }
                }
                if (can != null) events.add(can);
                canCursor = dist;
            }
        }

        events.add(new TrackEvent(RunnerCore.TRACK_LENGTH + 3f, 1, Kind.GOAL));

        addBuildings(rng, events, true);
        addBuildings(rng, events, false);
        addLamps(rng, events);
        addCars(rng, events);
        addTrashBesideLamps(rng, events);
        addObelisks(rng, events);
        addTrees(rng, events, true);
        addTrees(rng, events, false);

        events.sort(Comparator.comparingDouble(e -> e.trackDist));
        return events;
    }

    private static void addBuildings(Random rng, List<TrackEvent> events, boolean left) {
        Kind kind = left ? Kind.BUILDING_LEFT : Kind.BUILDING_RIGHT;
        float baseX = left ? BUILDING_X_LEFT : BUILDING_X_RIGHT;
        float cursor = DECOR_FIRST_MIN;
        while (true) {
            float dist = cursor + nextGap(rng, BUILDING_GAP_MIN, BUILDING_GAP_MAX);
            if (dist > RunnerCore.TRACK_LENGTH) break;
            int style = zoneAt(dist);
            events.add(new TrackEvent(dist, 1, kind,
                    baseX + rng.nextFloat() * BUILDING_X_SPAN, style));
            cursor = dist;
        }
    }

    private static void addLamps(Random rng, List<TrackEvent> events) {
        float cursor = DECOR_FIRST_MIN;
        boolean left = true;
        while (true) {
            float dist = cursor + nextGap(rng, LAMP_GAP_MIN, LAMP_GAP_MAX);
            if (dist > RunnerCore.TRACK_LENGTH) break;
            events.add(new TrackEvent(dist, 1,
                    left ? Kind.LAMP_LEFT : Kind.LAMP_RIGHT,
                    left ? LAMP_X_LEFT : LAMP_X_RIGHT));
            left = !left;
            cursor = dist;
        }
    }

    private static void addCars(Random rng, List<TrackEvent> events) {
        float cursor = DECOR_FIRST_MIN;
        boolean left = true;
        while (true) {
            float dist = cursor + nextGap(rng, CAR_GAP_MIN, CAR_GAP_MAX);
            if (dist > RunnerCore.TRACK_LENGTH) break;
            events.add(new TrackEvent(dist, 1,
                    left ? Kind.CAR_LEFT : Kind.CAR_RIGHT,
                    left ? CAR_X_LEFT : CAR_X_RIGHT));
            left = !left;
            cursor = dist;
        }
    }

    private static void addTrashBesideLamps(Random rng, List<TrackEvent> events) {
        List<TrackEvent> lamps = new ArrayList<>();
        for (TrackEvent e : events) {
            if (e.kind == Kind.LAMP_LEFT || e.kind == Kind.LAMP_RIGHT) lamps.add(e);
        }
        for (TrackEvent lamp : lamps) {
            if (rng.nextFloat() >= TRASH_PROB) continue;
            boolean left = lamp.kind == Kind.LAMP_LEFT;
            events.add(new TrackEvent(lamp.trackDist, 1,
                    left ? Kind.TRASH_LEFT : Kind.TRASH_RIGHT,
                    left ? TRASH_X_LEFT : TRASH_X_RIGHT));
        }
    }

    private static void addObelisks(Random rng, List<TrackEvent> events) {
        float cursor = OBELISK_FIRST_MIN;
        boolean left = true;
        while (true) {
            float dist = cursor + nextGap(rng, OBELISK_GAP_MIN, OBELISK_GAP_MAX);
            if (dist > RunnerCore.TRACK_LENGTH) break;
            events.add(new TrackEvent(dist, 1,
                    left ? Kind.OBELISK_LEFT : Kind.OBELISK_RIGHT,
                    left ? OBELISK_X_LEFT : OBELISK_X_RIGHT));
            left = !left;
            cursor = dist;
        }
    }

    private static void addTrees(Random rng, List<TrackEvent> events, boolean left) {
        Kind kind = left ? Kind.TREE_LEFT : Kind.TREE_RIGHT;
        Kind houseKind = left ? Kind.BUILDING_LEFT : Kind.BUILDING_RIGHT;
        float minX = left ? TREE_X_LEFT_MIN : TREE_X_RIGHT_MIN;
        float span = (left ? TREE_X_LEFT_MAX : TREE_X_RIGHT_MAX) - minX;
        List<Float> houses = new ArrayList<>();
        for (TrackEvent e : events) {
            if (e.kind == houseKind) houses.add(e.trackDist);
        }
        int index = 0;
        for (int i = 1; i < houses.size(); i++) {
            float dist = (houses.get(i - 1) + houses.get(i)) / 2f;
            events.add(new TrackEvent(dist, 1, kind,
                    minX + rng.nextFloat() * span, index & 1));
            index++;
        }
    }

    private static float obstacleLimit(int zone) {
        return zone == ZONE_COUNT - 1
                ? RunnerCore.TRACK_LENGTH - FINISH_MARGIN
                : ZONE_END[zone];
    }

    private static float nextGap(Random rng, float min, float max) {
        return min + rng.nextFloat() * (max - min);
    }

    private static List<TrackEvent> makeObstacles(Random rng, float dist, int zone) {
        List<TrackEvent> group = new ArrayList<>();
        if (rng.nextFloat() < DOUBLE_PROB[zone]) {
            int firstLane = rng.nextInt(LANE_COUNT);
            int secondLane = (firstLane + 1 + rng.nextInt(LANE_COUNT - 1)) % LANE_COUNT;
            group.add(new TrackEvent(dist, firstLane, drawKind(rng, zone)));
            group.add(new TrackEvent(dist, secondLane, drawKind(rng, zone)));
        } else {
            group.add(new TrackEvent(dist, rng.nextInt(LANE_COUNT), drawKind(rng, zone)));
        }
        return group;
    }

    private static Kind drawKind(Random rng, int zone) {
        return rng.nextFloat() < LOW_PROB[zone] ? Kind.OBSTACLE_LOW : Kind.OBSTACLE_HIGH;
    }

    private static List<TrackEvent> singleton(TrackEvent event) {
        List<TrackEvent> list = new ArrayList<>(1);
        list.add(event);
        return list;
    }

    private static boolean isObstacle(Kind kind) {
        return kind == Kind.OBSTACLE_LOW || kind == Kind.OBSTACLE_HIGH;
    }

    public static boolean isDecor(Kind kind) {
        return kind == Kind.BUILDING_LEFT || kind == Kind.BUILDING_RIGHT
                || kind == Kind.LAMP_LEFT || kind == Kind.LAMP_RIGHT
                || kind == Kind.CAR_LEFT || kind == Kind.CAR_RIGHT
                || kind == Kind.TRASH_LEFT || kind == Kind.TRASH_RIGHT
                || kind == Kind.OBELISK_LEFT || kind == Kind.OBELISK_RIGHT
                || kind == Kind.TREE_LEFT || kind == Kind.TREE_RIGHT;
    }

    private static boolean accepts(List<TrackEvent> events, List<TrackEvent> candidate) {
        for (TrackEvent c : candidate) {
            if (events.isEmpty() && c.trackDist < FIRST_EVENT_MIN) return false;
            if (isObstacle(c.kind) && c.trackDist > RunnerCore.TRACK_LENGTH - FINISH_MARGIN) return false;
            if (isObstacle(c.kind)) {
                int blocked = 0;
                for (TrackEvent e : events) {
                    if (isObstacle(e.kind) && e.trackDist == c.trackDist) blocked++;
                }
                for (TrackEvent e : candidate) {
                    if (isObstacle(e.kind) && e.trackDist == c.trackDist) blocked++;
                }
                if (blocked > 2) return false;
            }
            if (c.kind == Kind.CAN_ROW) {
                for (TrackEvent e : events) {
                    if (isObstacle(e.kind) && e.lane == c.lane
                            && Math.abs(e.trackDist - c.trackDist) < CAN_OBSTACLE_CLEAR) {
                        return false;
                    }
                }
            }
        }
        return true;
    }


    public static void main(String[] args) {
        List<TrackEvent> layout = new TrackBuilder().buildLayout();
        boolean ok = true;

        int[] obstacles = new int[ZONE_COUNT];
        int[] cans = new int[ZONE_COUNT];
        int high = 0;
        int goals = 0;
        float goalDist = -1f;
        int goalLane = -1;
        int buildingsLeft = 0;
        int buildingsRight = 0;
        int lampsLeft = 0;
        int lampsRight = 0;
        int carsLeft = 0;
        int carsRight = 0;
        int trashLeft = 0;
        int trashRight = 0;
        int obelisksLeft = 0;
        int obelisksRight = 0;
        int treesLeft = 0;
        int treesRight = 0;
        int[] treeVariant = new int[2];     
        int[] houseStyle = new int[HOUSE_STYLE_COUNT];
        boolean houseStyleOk = true;
        boolean treeBounds = true;
        boolean decorBounds = true;
        boolean obeliskZoneC = true;
        float firstDecor = Float.MAX_VALUE;
        List<Float> obstacleDists = new ArrayList<>();
        Map<Float, Set<Integer>> blockedByDist = new HashMap<>();

        for (TrackEvent e : layout) {
            int zone = zoneAt(e.trackDist);
            if (isObstacle(e.kind)) {
                obstacles[zone]++;
                if (e.kind == Kind.OBSTACLE_HIGH) high++;
                obstacleDists.add(e.trackDist);
                blockedByDist.computeIfAbsent(e.trackDist, d -> new HashSet<>()).add(e.lane);
            } else if (e.kind == Kind.CAN_ROW) {
                cans[zone]++;
            } else if (isDecor(e.kind)) {
                firstDecor = Math.min(firstDecor, e.trackDist);
                if (e.kind == Kind.BUILDING_LEFT) {
                    buildingsLeft++;
                    if (e.x < BUILDING_X_LEFT || e.x > BUILDING_X_LEFT + BUILDING_X_SPAN) decorBounds = false;
                    if (e.style < 0 || e.style >= HOUSE_STYLE_COUNT || e.style != zone) houseStyleOk = false;
                    else houseStyle[e.style]++;
                } else if (e.kind == Kind.BUILDING_RIGHT) {
                    buildingsRight++;
                    if (e.x < BUILDING_X_RIGHT || e.x > BUILDING_X_RIGHT + BUILDING_X_SPAN) decorBounds = false;
                    if (e.style < 0 || e.style >= HOUSE_STYLE_COUNT || e.style != zone) houseStyleOk = false;
                    else houseStyle[e.style]++;
                } else if (e.kind == Kind.LAMP_LEFT) {
                    lampsLeft++;
                    if (e.x != LAMP_X_LEFT) decorBounds = false;
                } else if (e.kind == Kind.LAMP_RIGHT) {
                    lampsRight++;
                    if (e.x != LAMP_X_RIGHT) decorBounds = false;
                } else if (e.kind == Kind.CAR_LEFT) {
                    carsLeft++;
                    if (e.x != CAR_X_LEFT) decorBounds = false;
                } else if (e.kind == Kind.CAR_RIGHT) {
                    carsRight++;
                    if (e.x != CAR_X_RIGHT) decorBounds = false;
                } else if (e.kind == Kind.TRASH_LEFT) {
                    trashLeft++;
                    if (e.x != TRASH_X_LEFT) decorBounds = false;
                } else if (e.kind == Kind.TRASH_RIGHT) {
                    trashRight++;
                    if (e.x != TRASH_X_RIGHT) decorBounds = false;
                } else if (e.kind == Kind.OBELISK_LEFT) {
                    obelisksLeft++;
                    if (e.x != OBELISK_X_LEFT) decorBounds = false;
                    if (zone != ZONE_COUNT - 1) obeliskZoneC = false;   
                } else if (e.kind == Kind.OBELISK_RIGHT) {
                    obelisksRight++;
                    if (e.x != OBELISK_X_RIGHT) decorBounds = false;
                    if (zone != ZONE_COUNT - 1) obeliskZoneC = false;
                } else if (e.kind == Kind.TREE_LEFT) {   
                    treesLeft++;
                    if (e.style < 0 || e.style > 1) treeBounds = false;
                    else treeVariant[e.style]++;
                    if (e.x < TREE_X_LEFT_MIN || e.x > TREE_X_LEFT_MAX) treeBounds = false;
                } else {   
                    treesRight++;
                    if (e.style < 0 || e.style > 1) treeBounds = false;
                    else treeVariant[e.style]++;
                    if (e.x < TREE_X_RIGHT_MIN || e.x > TREE_X_RIGHT_MAX) treeBounds = false;
                }
            } else if (e.kind == Kind.GOAL) {
                goals++;
                goalDist = e.trackDist;
                goalLane = e.lane;
            } else {
                ok = false;
                System.out.println("FAIL: unknown event kind " + e.kind);
            }
        }

        System.out.printf("zone A: %d obstacles, %d can rows%n", obstacles[0], cans[0]);
        System.out.printf("zone B: %d obstacles, %d can rows%n", obstacles[1], cans[1]);
        System.out.printf("zone C: %d obstacles, %d can rows%n", obstacles[2], cans[2]);
        System.out.printf("totals: %d obstacles (%d high), %d can rows, %d goal%n",
                obstacleDists.size(), high, cans[0] + cans[1] + cans[2], goals);
        System.out.printf("decor: buildings: L %d / R %d, lamps: L %d / R %d, cars: L %d / R %d, trash: L %d / R %d, obelisks: L %d / R %d%n",
                buildingsLeft, buildingsRight, lampsLeft, lampsRight,
                carsLeft, carsRight, trashLeft, trashRight, obelisksLeft, obelisksRight);
        boolean styleAll = houseStyleOk;
        for (int s = 0; s < HOUSE_STYLE_COUNT; s++) {
            if (houseStyle[s] <= 0) styleAll = false;
        }
        System.out.printf("houses: A %d B %d C %d (style == zone, all > 0: %s)%n",
                houseStyle[0], houseStyle[1], houseStyle[2], styleAll ? "ok" : "FAIL");
        System.out.printf("trees: L %d / R %d, variants: v0 %d / v1 %d%n",
                treesLeft, treesRight, treeVariant[0], treeVariant[1]);
        boolean treeOk = treeBounds && treesLeft > 0 && treesRight > 0
                && treeVariant[0] > 0 && treeVariant[1] > 0;
        System.out.printf("tree x-bounds (intercalated house band, left [%.0f..%.0f], right [%.0f..%.0f]) + both variants used: %s%n",
                TREE_X_LEFT_MIN, TREE_X_LEFT_MAX, TREE_X_RIGHT_MIN, TREE_X_RIGHT_MAX,
                treeOk ? "ok" : "FAIL");
        ok &= treeOk;
        ok &= styleAll;

        boolean treeCountOk = treesLeft == buildingsLeft - 1
                && treesRight == buildingsRight - 1;
        System.out.printf("trees per side == buildings per side - 1 (L %d == %d, R %d == %d): %s%n",
                treesLeft, buildingsLeft - 1, treesRight, buildingsRight - 1,
                treeCountOk ? "ok" : "FAIL");
        ok &= treeCountOk;

        float maxHouseHalf = 0f;
        for (int s = 0; s < HOUSE_STYLE_COUNT; s++) {
            maxHouseHalf = Math.max(maxHouseHalf, HOUSE_MODEL_DX[s] * HOUSE_SCALE[s] / 2f);
        }
        float requiredClear = maxHouseHalf + TREE_HALF_U;
        float minTreeHouseClear = Float.MAX_VALUE;
        for (TrackEvent t : layout) {
            if (t.kind != Kind.TREE_LEFT && t.kind != Kind.TREE_RIGHT) continue;
            Kind houseKind = t.kind == Kind.TREE_LEFT ? Kind.BUILDING_LEFT : Kind.BUILDING_RIGHT;
            for (TrackEvent h : layout) {
                if (h.kind == houseKind) {
                    minTreeHouseClear = Math.min(minTreeHouseClear, Math.abs(t.trackDist - h.trackDist));
                }
            }
        }
        boolean clearanceOk = minTreeHouseClear >= requiredClear;
        System.out.printf("tree-house along-track clearance (min %.1f >= %.1f): %s%n",
                minTreeHouseClear, requiredClear, clearanceOk ? "ok" : "FAIL");
        ok &= clearanceOk;
        boolean decorOk = decorBounds
                && firstDecor >= DECOR_FIRST_MIN
                && buildingsLeft > 0 && buildingsRight > 0
                && lampsLeft > 0 && lampsRight > 0
                && carsLeft > 0 && carsRight > 0
                && trashLeft > 0 && trashRight > 0
                && obelisksLeft > 0 && obelisksRight > 0;
        System.out.printf("decor x bounds (buildings [45..51]/[99..105], lamps {66,84}) + first decor >= %.0f: %s (first %.1f)%n",
                DECOR_FIRST_MIN, decorOk ? "ok" : "FAIL", firstDecor == Float.MAX_VALUE ? -1f : firstDecor);
        ok &= decorOk;

        boolean zoneCOk = obeliskZoneC && obelisksLeft + obelisksRight > 0;
        System.out.printf("obelisks zone C only (trackDist >= %.0f): %s%n",
                OBELISK_FIRST_MIN, zoneCOk ? "ok" : "FAIL");
        ok &= zoneCOk;

        float maxFront = 0f;
        float maxBack = 0f;
        for (int s = 0; s < HOUSE_STYLE_COUNT; s++) {
            maxFront = Math.max(maxFront, HOUSE_MODEL_DZ_FRONT[s] * HOUSE_SCALE[s]);
            maxBack = Math.max(maxBack, HOUSE_MODEL_DZ_BACK[s] * HOUSE_SCALE[s]);
        }
        float[][][] segments = {
                {   
                        {BUILDING_X_LEFT - maxBack, BUILDING_X_LEFT + BUILDING_X_SPAN + maxFront},
                        {BUILDING_X_RIGHT - maxFront, BUILDING_X_RIGHT + BUILDING_X_SPAN + maxBack}},
                {   
                        {CAR_X_LEFT - CAR_ROAD_HALF_W, CAR_X_LEFT + CAR_ROAD_HALF_W},
                        {CAR_X_RIGHT - CAR_ROAD_HALF_W, CAR_X_RIGHT + CAR_ROAD_HALF_W}},
                {   
                        {TRASH_X_LEFT - TRASH_ROAD_HALF_W, TRASH_X_LEFT + TRASH_ROAD_HALF_W},
                        {TRASH_X_RIGHT - TRASH_ROAD_HALF_W, TRASH_X_RIGHT + TRASH_ROAD_HALF_W}},
                {   
                        {LAMP_X_LEFT - LAMP_POLE_R, LAMP_X_LEFT + LAMP_POLE_R},
                        {LAMP_X_RIGHT - LAMP_POLE_R, LAMP_X_RIGHT + LAMP_POLE_R}},
                {   
                        {OBELISK_X_LEFT - OBELISK_ROAD_HALF_W, OBELISK_X_LEFT + OBELISK_ROAD_HALF_W},
                        {OBELISK_X_RIGHT - OBELISK_ROAD_HALF_W, OBELISK_X_RIGHT + OBELISK_ROAD_HALF_W}},
                {   
                        {TREE_X_LEFT_MIN, TREE_X_LEFT_MAX},
                        {TREE_X_RIGHT_MIN, TREE_X_RIGHT_MAX}}
        };
        String[] segmentNames = {"houses", "cars", "trash", "lamps", "obelisks", "trees"};
        boolean segmentDisjoint = true;
        for (int a = 0; a < segments.length; a++) {
            for (int b = a + 1; b < segments.length; b++) {
                if (a == 0 && b == 5) continue;
                for (int side = 0; side < 2; side++) {
                    float[] segA = segments[a][side];
                    float[] segB = segments[b][side];
                    if (segA[0] < segB[1] && segB[0] < segA[1]) {
                        segmentDisjoint = false;
                        System.out.printf("FAIL: %s and %s overlap on side %d ([%.3f..%.3f] vs [%.3f..%.3f])%n",
                                segmentNames[a], segmentNames[b], side, segA[0], segA[1], segB[0], segB[1]);
                    }
                }
            }
        }
        System.out.printf("segment-disjoint road-dir intervals (houses/cars/trash/lamps/obelisks/trees; houses-vs-trees skipped on purpose): %s%n",
                segmentDisjoint ? "ok" : "FAIL");
        ok &= segmentDisjoint;
        for (int z = 0; z < ZONE_COUNT; z++) {
            if (obstacles[z] <= 0 || cans[z] <= 0) {
                ok = false;
                System.out.println("FAIL: zone " + z + " has no obstacles or no can rows");
            }
        }

        boolean sorted = true;
        for (int i = 1; i < layout.size(); i++) {
            if (layout.get(i).trackDist < layout.get(i - 1).trackDist) sorted = false;
        }
        ok &= sorted;
        System.out.println("sorted ascending by trackDist: " + sorted);

        obstacleDists.sort(Float::compare);
        if (obstacleDists.isEmpty()) {
            ok = false;
            System.out.println("FAIL: no obstacles");
        } else {
            float minGap = Float.MAX_VALUE;
            float prev = Float.NaN;
            for (float d : obstacleDists) {
                if (!Float.isNaN(prev) && d > prev) minGap = Math.min(minGap, d - prev);
                prev = d;
            }
            System.out.printf("min obstacle gap observed: %.1f u (doubles share a trackDist)%n", minGap);
            float lastObstacle = obstacleDists.get(obstacleDists.size() - 1);
            boolean finishClear = lastObstacle <= RunnerCore.TRACK_LENGTH - FINISH_MARGIN;
            System.out.printf("last obstacle: %.1f u (limit %.1f) -> %s%n",
                    lastObstacle, RunnerCore.TRACK_LENGTH - FINISH_MARGIN, finishClear ? "ok" : "FAIL");
            ok &= finishClear;
        }

        int maxBlocked = 0;
        for (Set<Integer> lanes : blockedByDist.values()) {
            maxBlocked = Math.max(maxBlocked, lanes.size());
        }
        System.out.printf("max lanes blocked at one trackDist: %d (must be <= 2)%n", maxBlocked);
        ok &= maxBlocked <= 2;

        boolean firstOk = !layout.isEmpty() && layout.get(0).trackDist >= FIRST_EVENT_MIN;
        System.out.printf("first event: %.1f u (must be >= 40) -> %s%n",
                layout.isEmpty() ? -1f : layout.get(0).trackDist, firstOk ? "ok" : "FAIL");
        ok &= firstOk;

        boolean goalOk = goals == 1
                && goalDist == RunnerCore.TRACK_LENGTH + 3f
                && goalLane == 1;
        System.out.printf("goal: %d at %.1f lane %d (want 1 at %.1f lane 1) -> %s%n",
                goals, goalDist, goalLane, RunnerCore.TRACK_LENGTH + 3f, goalOk ? "ok" : "FAIL");
        ok &= goalOk;

        float[][] table = {{0f, 17f}, {799f, 17f}, {800f, 20f}, {1799f, 20f}, {1800f, 23f}, {2499f, 23f}};
        StringBuilder speedLine = new StringBuilder("speedAt table:");
        for (float[] row : table) {
            float actual = speedAt(row[0]);
            boolean match = actual == row[1];
            speedLine.append(String.format(" %.0f->%.0f", row[0], actual));
            if (!match) speedLine.append("(want ").append(row[1]).append("!)");
            ok &= match;
        }
        System.out.println(speedLine);

        float estimated = 800f / speedAt(0f)
                + (ZONE_B_END - ZONE_A_END) / speedAt(ZONE_A_END)
                + (RunnerCore.TRACK_LENGTH - ZONE_B_END) / speedAt(ZONE_B_END);
        System.out.printf("estimated total time: %.1f s (limit %.0f)%n",
                estimated, RunnerCore.MAX_TIME);
        ok &= estimated < RunnerCore.MAX_TIME;

        System.out.println(ok ? "TRACKBUILDER CHECK: PASS" : "TRACKBUILDER CHECK: FAIL");
        System.exit(ok ? 0 : 1);
    }
}
