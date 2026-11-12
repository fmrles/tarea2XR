package org.lwjglb.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Deterministic track layout for the runner: zone speeds, zone names and the
 * fixed sequence of obstacles, can rows, the vending-machine goal and, since
 * block 6b, side decor (buildings and street lights along the road; block 6c
 * adds parked cars, trash bins and zone-C obelisks).
 *
 * Pure data and logic - no LWJGL, Entity or Terrain types - so {@link #main}
 * runs headless as a self-check on the plain runtime classpath. The layout
 * seed is fixed, so every run gets the exact same track.
 *
 * Zones by track distance: A = [0,800), B = [800,1800), C = [1800,TRACK_LENGTH].
 */
public final class TrackBuilder {

    /** Cans materialised per CAN_ROW event (RunnerCore places them). */
    public static final int CAN_ROW_SIZE = 5;
    /** World distance between consecutive cans of one row. */
    public static final float CAN_ROW_SPACING = 2.5f;

    /** What a {@link TrackEvent} places on the track (blocks 6b/6c add decor kinds). */
    public enum Kind {
        OBSTACLE_LOW, OBSTACLE_HIGH, CAN_ROW, GOAL,
        BUILDING_LEFT, BUILDING_RIGHT, LAMP_LEFT, LAMP_RIGHT,
        CAR_LEFT, CAR_RIGHT, TRASH_LEFT, TRASH_RIGHT,
        OBELISK_LEFT, OBELISK_RIGHT, TREE_LEFT, TREE_RIGHT
    }

    /** One placed item, in track-distance coordinates (0 = start line). */
    public static final class TrackEvent {
        public final float trackDist;
        public final int lane;
        public final Kind kind;
        /** World X for side decor; NaN for lane-based gameplay events. */
        public final float x;
        /**
         * Block 9: which Meshy house this event uses - 0..2 for
         * {@code BUILDING_*} (0 = houseA / zone A, 1 = houseB / zone B,
         * 2 = houseC / zone C, i.e. {@code zoneAt(trackDist)}),
         * tree variant 0/1 for {@code TREE_*} (that side's tree index parity).
         * 0 for every other kind. Purely derived, never drawn from the RNG, so
         * the obstacle/can sequence above stays byte-identical.
         */
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

    // --- zone boundaries (zone C ends at RunnerCore.TRACK_LENGTH: never
    // duplicate that constant, it is the single source of truth) ---
    private static final float ZONE_A_END = 800f;
    private static final float ZONE_B_END = 1800f;

    private static final int ZONE_COUNT = 3;
    private static final int LANE_COUNT = 3;
    private static final long SEED = 20261113L;
    private static final int MAX_TRIES = 10;             // rejection sampling budget
    private static final float FIRST_EVENT_MIN = 40f;    // invariant (c)
    private static final float FINISH_MARGIN = 40f;      // invariant (d)
    private static final float CAN_OBSTACLE_CLEAR = 10f; // invariant (b)

    // --- block 6b: side decor (buildings + street lights) ---
    private static final float DECOR_FIRST_MIN = 60f;    // first decor is at trackDist >= 60
    private static final float BUILDING_GAP_MIN = 19f;   // per-side spacing: >= 19 u so a tree fits in every gap
    private static final float BUILDING_GAP_MAX = 27f;   // (19/2 = 9.5 >= house half 6 + tree half 3)
    private static final float LAMP_GAP_MIN = 30f;       // lamp spacing, sides alternate
    private static final float LAMP_GAP_MAX = 50f;
    private static final float BUILDING_X_LEFT = 45f;    // flat pavement band left, at the curb: x in [45..51]
    private static final float BUILDING_X_RIGHT = 99f;   // flat pavement band right: x in [99..105]
    private static final float BUILDING_X_SPAN = 6f;
    private static final float LAMP_X_LEFT = 66f;        // on the asphalt, close to the outer lanes
    private static final float LAMP_X_RIGHT = 84f;
    private static final float LAMP_POLE_R = 0.4f;       // pole radius at x = 66 / 84 (block 6c check)

    // --- block 9: three zone-styled Meshy houses + billboard trees ---
    // House style 0..2 == zoneAt(trackDist): zone A (trackDist < 800) uses
    // houseA (Meshy_AI_House_1), zone B (800..1800) houseB (House_2), zone C
    // (>= 1800) houseC (House_3_Warm). One house per zone, no RNG for the
    // style - the variant is data already at hand (see addBuildings).
    //
    // Measured on the normalized files in assets/house{A,B,C}/house{A,B,C}.obj
    // (v/vt/vn/f only, 7789 / 8933 / 8663 triangles). Raw bounds (model units):
    //   houseA x[-0.952644..0.951014] y[-0.452940..0.461779] z[-0.748895..0.749613]
    //   houseB x[-0.951403..0.947266] y[-0.419412..0.418867] z[-0.899088..0.901883]
    //   houseC x[-0.910666..0.922520] y[-0.521438..0.518082] z[-0.950935..0.949783]
    //
    // Front axis: the facade cannot be proven from code, so it is inferred
    // from the outward-facing wall density (door/window geometry out-triangulates
    // a blank back wall): +Z walls 1450/1166/1537 vs -Z 981/683/1122 and vs
    // +/-X 1134+945 / 315+351 / 750+754 -> facade at model +Z for all three,
    // i.e. the same convention as the old house.obj (+Z facade, yaw +/-90).
    // RunnerCore adds ONE HOUSE_FRONT_YAW_OFFSET on top of that
    // // TODO visual tune (180 flips front/back; +/-90 means the facade really
    // sits on X and the along-track extent becomes HOUSE_MODEL_DZ_* instead).
    public static final int HOUSE_STYLE_COUNT = 3;
    /** Measured model-space X extent: the along-track extent after the facade yaw. */
    public static final float[] HOUSE_MODEL_DX = {
            1.903658f, 1.898669f, 1.833186f};
    /** Measured model-space Y extent (raw height before the scale). */
    public static final float[] HOUSE_MODEL_DY = {
            0.914719f, 0.838279f, 1.039520f};
    /** Model +Z extent (facade side) -> world road-dir front of the house. */
    public static final float[] HOUSE_MODEL_DZ_FRONT = {
            0.749613f, 0.901883f, 0.949783f};
    /** Model -Z extent (blank back wall) -> world road-dir rear of the house. */
    public static final float[] HOUSE_MODEL_DZ_BACK = {
            0.748895f, 0.899088f, 0.950935f};
    /** Model-space Y minimum: the feet, used as RunnerCore's per-style baseY. */
    public static final float[] HOUSE_MODEL_Y_MIN = {
            -0.452940f, -0.419412f, -0.521438f};
    /**
     * Per-style world scale = 12 / HOUSE_MODEL_DX: the along-track extent is
     * pinned to the spec cap of 12.000 u so neighbouring houses keep 7-15 u
     * of clear air across the BUILDING_GAP_MIN 19 u .. BUILDING_GAP_MAX 27 u
     * range - enough room to intercalate one billboard tree per gap at the
     * midpoint (19/2 = 9.5 >= house half 6.0 + tree half 3.0). Kept here
     * rather than in RunnerCore so this class stays self-contained for the
     * headless self-check; RunnerCore aliases it as its per-style rendering
     * scale.
     *
     * <p><b>Why the height band could not be honoured.</b> The spec asks for a
     * final height of 7-9 u AND an along-track extent of <= 12 u. Under a
     * uniform Entity scale those two are mutually exclusive for these assets:
     * the measured aspect is dy/dx = 0.4805 / 0.4415 / 0.5671 while the band
     * needs dy/dx >= 7/12 = 0.583. The extent cap is the hard constraint (it
     * is what keeps neighbouring houses from interpenetrating; the old
     * house.obj was 9.23 u along-track at scale 0.38), so the scale is
     * extent-bound and the heights land at 5.77 / 5.30 / 6.81 u. Raising the
     * scale to reach 7 u would need extents of 14.57 / 15.85 / 12.34 u: all
     * three breach the 12 u cap (and at the old 13 u minimum gap the first
     * two interpenetrated), and at the current 19 u gap the worst case would
     * eat the intercalated tree's clearance outright (19/2 - 15.85/2 = 1.575
     * < 3 u tree half). Vertex percentiles confirm the extents are real body
     * walls, not outliers (95th percentile at 0.93/0.82/0.86 of the
     * half-width).
     */
    public static final float[] HOUSE_SCALE = {
            6.303650f, 6.320216f, 6.545980f};

    // Block 9b: billboard trees INTERCALATED with the houses on the flat
    // pavement band - one tree at the midpoint of every gap between
    // consecutive same-side houses. x uses the SAME jitter band as the houses
    // (the old hill band behind the row is gone: it made the trees slide over
    // the heightmap and look like they overran it). Constants reference the
    // BUILDING_* band so the two cannot drift apart.
    private static final float TREE_HALF_U = 3f;          // half of the 6 u quad (RunnerCore.TREE_SCALE)
    private static final float TREE_X_LEFT_MIN = BUILDING_X_LEFT;                     // 45
    private static final float TREE_X_LEFT_MAX = BUILDING_X_LEFT + BUILDING_X_SPAN;   // 51
    private static final float TREE_X_RIGHT_MIN = BUILDING_X_RIGHT;                   // 99
    private static final float TREE_X_RIGHT_MAX = BUILDING_X_RIGHT + BUILDING_X_SPAN; // 105

    // --- block 6c: parked cars, trash bins and zone-C obelisks ---
    // Road-dir footprint constants below are the measured OBJ extents at the
    // RunnerCore scales, exposed so the self-check can assert that the decor
    // x-segments of every type stay pairwise disjoint.
    private static final float CAR_GAP_MIN = 35f;        // parked-car spacing along the track
    private static final float CAR_GAP_MAX = 60f;
    private static final float CAR_X_LEFT = 64f;         // parallel-parked at the left curb
    private static final float CAR_X_RIGHT = 86f;        // rustedCar.obj re-centred: z-span 6.153 u * scale 0.266
    private static final float CAR_ROAD_HALF_W = 0.819f; // -> 1.64 u wide, inside [63..65] / [85..87]
    private static final float TRASH_PROB = 0.55f;       // share of lamps that get a bin
    private static final float TRASH_X_LEFT = 67.3f;     // beside the lamp poles, clear of the crates
    private static final float TRASH_X_RIGHT = 82.7f;
    private static final float TRASH_ROAD_HALF_W = 0.268f; // trash.obj radius 1.783 * scale 0.15
    private static final float OBELISK_FIRST_MIN = 1800f;  // zone C only
    private static final float OBELISK_GAP_MIN = 120f;
    private static final float OBELISK_GAP_MAX = 200f;
    private static final float OBELISK_X_LEFT = 34f;     // just outside the house rear line (hill shoulder)
    private static final float OBELISK_X_RIGHT = 116f;
    // obelisk.obj flares from r=85.8 at the base to r=107.5 near the top: the
    // FULL silhouette (215.05 u) must stay outside the house line 36.9 / 113.1,
    // which caps RunnerCore.OBELISK_SCALE at 0.0269 -> height ~8.2 u. The
    // spec's 10-13 u target cannot hold at x=34 with this model (a10-13 u
    // scale would poke 0.6+ u into the house envelope at every height).
    private static final float OBELISK_ROAD_HALF_W = 2.85f; // 107.525 * scale 0.0265

    // --- escalating difficulty per zone (A, B, C) ---
    private static final float[] GAP_MIN = {30f, 24f, 19f};
    private static final float[] GAP_MAX = {45f, 34f, 27f};
    private static final float[] LOW_PROB = {0.75f, 0.50f, 0.40f};
    private static final float[] DOUBLE_PROB = {0f, 0.25f, 0.40f};
    private static final float[] CAN_GAP_MIN = {40f, 35f, 30f};
    private static final float[] CAN_GAP_MAX = {55f, 50f, 45f};
    private static final float[] ZONE_START = {0f, ZONE_A_END, ZONE_B_END};
    private static final float[] ZONE_END = {ZONE_A_END, ZONE_B_END, RunnerCore.TRACK_LENGTH};

    // ------------------------------------------------------------------ zones

    /** Zone speed: 17 u/s in A, 20 in B, 23 in C (tuned up from design's 12 -> 14 -> 16). */
    public static float speedAt(float travelled) {
        if (travelled < ZONE_A_END) return 17f;
        if (travelled < ZONE_B_END) return 20f;
        return 23f;
    }

    /** Zone index (0 = A, 1 = B, 2 = C) for a track distance. */
    public static int zoneAt(float travelled) {
        if (travelled < ZONE_A_END) return 0;
        if (travelled < ZONE_B_END) return 1;
        return 2;
    }

    /** Spanish HUD banner for a zone index. */
    public static String zoneName(int zone) {
        if (zone == 0) return "ZONA A - RESIDENCIAL";
        if (zone == 1) return "ZONA B - CENTRO";
        return "ZONA C - PLAZA";
    }

    // ---------------------------------------------------------------- layout

    /**
     * Builds the whole track with a fixed seed, sorted ascending by track
     * distance. Obstacles escalate per zone (gap, low/double ratio) and can
     * rows keep their own gap; the GOAL sits 3 u past the finish line.
     */
    public List<TrackEvent> buildLayout() {
        Random rng = new Random(SEED);
        List<TrackEvent> events = new ArrayList<>();

        for (int zone = 0; zone < ZONE_COUNT; zone++) {
            // --- obstacles ---
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

            // --- can rows ---
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

        // invariant (e): exactly one goal, 3 u past the finish, centre lane
        events.add(new TrackEvent(RunnerCore.TRACK_LENGTH + 3f, 1, Kind.GOAL));

        // block 6b: side decor AFTER all gameplay events, drawing from the SAME
        // rng instance, so the tested obstacle/can sequence stays byte-identical
        addBuildings(rng, events, true);
        addBuildings(rng, events, false);
        addLamps(rng, events);
        // block 6c: appended after the lamps (still the same rng instance, so
        // everything generated above - obstacles, cans, buildings, lamps - is
        // untouched)
        addCars(rng, events);
        addTrashBesideLamps(rng, events);
        addObelisks(rng, events);
        // block 9: billboard trees, appended LAST so every rng draw above -
        // and therefore the whole obstacle/can layout - stays byte-identical
        addTrees(rng, events, true);
        addTrees(rng, events, false);

        events.sort(Comparator.comparingDouble(e -> e.trackDist));
        return events;
    }

    /**
     * Block 6b/9: one building per side every 19-27 u (block 9b widened the
     * gap from 13-21 u so every gap can host an intercalated billboard tree),
     * glued to the flat pavement band (left x in [45..51], right x in [99..105],
     * at the curb).
     * Decor carries lane = 1 (unused) and its world x in {@link TrackEvent#x}.
     *
     * <p>Block 9: each building also gets its house style - just
     * {@code zoneAt(dist)}, one house per zone (A/B/C) - derived from data
     * already at hand, so no extra RNG draw is consumed and the layout above
     * is untouched.
     */
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

    /**
     * Block 6b: street lights on the pavement edges (x = 60 left / 90 right),
     * one every 30-50 u with the sides alternating L, R, L, R...
     */
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

    /**
     * Block 6c: rusted cars parallel-parked at the curb, alternating sides
     * every 35-60 u, all zones. Fixed lane centres (64 left / 86 right) keep
     * the 4.5 u long body inside the [63..65] / [85..87] segments.
     */
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

    /**
     * Block 6c: a trash bin shares the trackDist and side of a lamp event;
     * the shared seeded RNG keeps ~55% of the lamps (one draw per lamp,
     * appended after every lamp is placed, so earlier draws are unaffected).
     */
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

    /**
     * Block 6c: obelisk monuments in zone C only (trackDist >= 1800),
     * alternating sides every 120-200 u on the low hill shoulder
     * (x = 34 left / 116 right, just outside the house rear line).
     */
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

    /**
     * Block 9b: ONE tree at the exact midpoint of every gap between
     * consecutive same-side houses, x drawn from the same jitter band as the
     * houses ({@code minX + rng * span}), variant = this side's tree index
     * parity (0/1 -> the first/second cutout quad). No cursor and no gap
     * draws: the midpoints come from the {@code BUILDING_*} trackDists, which
     * were appended earlier in insertion order (increasing trackDist - the
     * sort happens only after every generator has run). Appended after every
     * other generator, so all earlier RNG draws - and therefore the whole
     * obstacle/can layout - stay byte-identical; the draws below only shift
     * the trees themselves.
     */
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

    /** Last legal obstacle distance: 40 u before the finish in zone C. */
    private static float obstacleLimit(int zone) {
        return zone == ZONE_COUNT - 1
                ? RunnerCore.TRACK_LENGTH - FINISH_MARGIN
                : ZONE_END[zone];
    }

    private static float nextGap(Random rng, float min, float max) {
        return min + rng.nextFloat() * (max - min);
    }

    /** One single-lane obstacle, or a 2-lane double (always 1 free lane). */
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

    /** True for side-decor kinds (buildings, street lights and block 6c scenery). */
    public static boolean isDecor(Kind kind) {
        return kind == Kind.BUILDING_LEFT || kind == Kind.BUILDING_RIGHT
                || kind == Kind.LAMP_LEFT || kind == Kind.LAMP_RIGHT
                || kind == Kind.CAR_LEFT || kind == Kind.CAR_RIGHT
                || kind == Kind.TRASH_LEFT || kind == Kind.TRASH_RIGHT
                || kind == Kind.OBELISK_LEFT || kind == Kind.OBELISK_RIGHT
                || kind == Kind.TREE_LEFT || kind == Kind.TREE_RIGHT;
    }

    /**
     * Layout invariants for a candidate group:
     * (a) at most 2 lanes blocked at one trackDist,
     * (b) a can row keeps >= 10 u of track distance from obstacles in its lane,
     * (c) the first event is at trackDist >= 40,
     * (d) no obstacle past TRACK_LENGTH - 40.
     */
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

    // ----------------------------------------------------------- self-check

    /** Headless self-check: prints the layout summary, exits 0/1. */
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
        int[] treeVariant = new int[2];     // 0/1 cutout quad usage
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
                // block 6b/6c: decor must never touch the obstacle/can invariants
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
                    if (zone != ZONE_COUNT - 1) obeliskZoneC = false;   // block 6c: zone C only
                } else if (e.kind == Kind.OBELISK_RIGHT) {
                    obelisksRight++;
                    if (e.x != OBELISK_X_RIGHT) decorBounds = false;
                    if (zone != ZONE_COUNT - 1) obeliskZoneC = false;
                } else if (e.kind == Kind.TREE_LEFT) {   // block 9: billboard trees
                    treesLeft++;
                    if (e.style < 0 || e.style > 1) treeBounds = false;
                    else treeVariant[e.style]++;
                    if (e.x < TREE_X_LEFT_MIN || e.x > TREE_X_LEFT_MAX) treeBounds = false;
                } else {   // TREE_RIGHT
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

        // block 9b: every gap between consecutive same-side houses hosts exactly
        // one tree -> trees per side == buildings per side - 1
        boolean treeCountOk = treesLeft == buildingsLeft - 1
                && treesRight == buildingsRight - 1;
        System.out.printf("trees per side == buildings per side - 1 (L %d == %d, R %d == %d): %s%n",
                treesLeft, buildingsLeft - 1, treesRight, buildingsRight - 1,
                treeCountOk ? "ok" : "FAIL");
        ok &= treeCountOk;

        // block 9b: along-track clearance between every tree and every house on
        // its own side. Worst-case house half-extent = max over styles of
        // HOUSE_MODEL_DX[s] * HOUSE_SCALE[s] / 2 (== 6.0, since the scale is
        // defined as 12 / HOUSE_MODEL_DX), plus the 3 u tree half.
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

        // block 6c: obelisks are zone-C-only monuments
        boolean zoneCOk = obeliskZoneC && obelisksLeft + obelisksRight > 0;
        System.out.printf("obelisks zone C only (trackDist >= %.0f): %s%n",
                OBELISK_FIRST_MIN, zoneCOk ? "ok" : "FAIL");
        ok &= zoneCOk;

        // block 6c: road-dir x-interval of every decor type (built from the
        // lane constants + measured footprint extents) must be pairwise disjoint
        // block 9: the house envelope is the WORST case over the three styles
        // (45 - max back / 51 + max front, mirrored on the right).
        // block 9b: the (houses, trees) pair is SKIPPED below - trees sit in
        // the SAME pavement band as the houses on purpose (they are
        // intercalated between them along track); their correctness is covered
        // by the tree-house along-track clearance check above. All other pairs,
        // including the trees entry vs cars/trash/lamps/obelisks, stay checked.
        float maxFront = 0f;
        float maxBack = 0f;
        for (int s = 0; s < HOUSE_STYLE_COUNT; s++) {
            maxFront = Math.max(maxFront, HOUSE_MODEL_DZ_FRONT[s] * HOUSE_SCALE[s]);
            maxBack = Math.max(maxBack, HOUSE_MODEL_DZ_BACK[s] * HOUSE_SCALE[s]);
        }
        float[][][] segments = {
                {   // houses: block 6b/9 envelope, per side (facade toward the road)
                        {BUILDING_X_LEFT - maxBack, BUILDING_X_LEFT + BUILDING_X_SPAN + maxFront},
                        {BUILDING_X_RIGHT - maxFront, BUILDING_X_RIGHT + BUILDING_X_SPAN + maxBack}},
                {   // parked cars
                        {CAR_X_LEFT - CAR_ROAD_HALF_W, CAR_X_LEFT + CAR_ROAD_HALF_W},
                        {CAR_X_RIGHT - CAR_ROAD_HALF_W, CAR_X_RIGHT + CAR_ROAD_HALF_W}},
                {   // trash bins
                        {TRASH_X_LEFT - TRASH_ROAD_HALF_W, TRASH_X_LEFT + TRASH_ROAD_HALF_W},
                        {TRASH_X_RIGHT - TRASH_ROAD_HALF_W, TRASH_X_RIGHT + TRASH_ROAD_HALF_W}},
                {   // lamp poles
                        {LAMP_X_LEFT - LAMP_POLE_R, LAMP_X_LEFT + LAMP_POLE_R},
                        {LAMP_X_RIGHT - LAMP_POLE_R, LAMP_X_RIGHT + LAMP_POLE_R}},
                {   // obelisks (full silhouette)
                        {OBELISK_X_LEFT - OBELISK_ROAD_HALF_W, OBELISK_X_LEFT + OBELISK_ROAD_HALF_W},
                        {OBELISK_X_RIGHT - OBELISK_ROAD_HALF_W, OBELISK_X_RIGHT + OBELISK_ROAD_HALF_W}},
                {   // billboard trees (intercalated in the house band; the 6 u
                        // quad's own +-3 u half-width is NOT included here - the
                        // (houses, trees) pair is skipped anyway by design)
                        {TREE_X_LEFT_MIN, TREE_X_LEFT_MAX},
                        {TREE_X_RIGHT_MIN, TREE_X_RIGHT_MAX}}
        };
        String[] segmentNames = {"houses", "cars", "trash", "lamps", "obelisks", "trees"};
        boolean segmentDisjoint = true;
        for (int a = 0; a < segments.length; a++) {
            for (int b = a + 1; b < segments.length; b++) {
                // block 9b: houses (0) vs trees (5) overlap laterally ON PURPOSE -
                // the trees are intercalated in the same pavement band, between
                // the houses along track. Covered by the clearance check above.
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

        // sorted ascending + min gap between consecutive obstacles
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
            // doubles share one trackDist by design: measure between distinct ones
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

        // speedAt boundary table
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

        // estimated total time: 800/17 + 1000/20 + 700/23 = 127.5 s < 210 s
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
