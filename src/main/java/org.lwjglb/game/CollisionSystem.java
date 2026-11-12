package org.lwjglb.game;

/**
 * Geometry tests for the two collision kinds required by the assignment:
 * solids (obstacles that damage the runner) and triggers (cans that are
 * collected on contact).
 *
 * Both tests work in world space along Z. The player is fixed at
 * {@link RunnerCore#PLAYER_Z} and lanes are already resolved to lane
 * indices by the callers, so a collision only needs the lane equality,
 * the Z distance window and the runner's vertical state.
 */
public final class CollisionSystem {

    /** Half depth of the runner's collision window against a solid. */
    public static final float SOLID_HALF_DEPTH = 1.3f;

    /** Half depth of the runner's trigger window against a pickup. */
    public static final float TRIGGER_HALF_DEPTH = 1.1f;

    private CollisionSystem() {
    }

    /**
     * Solid collision: same lane, overlapping depth window and not cleared.
     * A low obstacle is cleared by jumping above it, a high obstacle by
     * sliding under it; the caller resolves that rule into {@code cleared}.
     *
     * @return true when the runner takes a hit.
     */
    public static boolean hitsSolid(int solidLane, int playerLane,
                                    float solidZ, float playerZ, boolean cleared) {
        if (solidLane != playerLane || cleared) {
            return false;
        }
        return Math.abs(solidZ - playerZ) < SOLID_HALF_DEPTH;
    }

    /**
     * Trigger collision: same lane and overlapping depth window. Cans are
     * collected at any height (on the ground or mid-jump), so no vertical
     * state is involved.
     *
     * @return true when the runner picks the trigger up.
     */
    public static boolean overlapsTrigger(int triggerLane, int playerLane,
                                          float triggerZ, float playerZ) {
        if (triggerLane != playerLane) {
            return false;
        }
        return Math.abs(triggerZ - playerZ) < TRIGGER_HALF_DEPTH;
    }
}
