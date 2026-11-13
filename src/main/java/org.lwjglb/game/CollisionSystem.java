package org.lwjglb.game;

public final class CollisionSystem {

    public static final float SOLID_HALF_DEPTH = 1.3f;

    public static final float TRIGGER_HALF_DEPTH = 1.1f;

    private CollisionSystem() {
    }

    public static boolean hitsSolid(int solidLane, int playerLane,
                                    float solidZ, float playerZ, boolean cleared) {
        if (solidLane != playerLane || cleared) {
            return false;
        }
        return Math.abs(solidZ - playerZ) < SOLID_HALF_DEPTH;
    }

    public static boolean overlapsTrigger(int triggerLane, int playerLane,
                                          float triggerZ, float playerZ) {
        if (triggerLane != playerLane) {
            return false;
        }
        return Math.abs(triggerZ - playerZ) < TRIGGER_HALF_DEPTH;
    }
}
