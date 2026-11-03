package com.gridifymydungeon.plugin.spell;

/**
 * 8-directional enum for spell casting
 * N, NE, E, SE, S, SW, W, NW
 */
public enum Direction8 {
    NORTH(0, -1, 0f),      // W key
    NORTHEAST(1, -1, 45f), // W+D keys
    EAST(1, 0, 90f),       // D key
    SOUTHEAST(1, 1, 135f), // S+D keys
    SOUTH(0, 1, 180f),     // S key
    SOUTHWEST(-1, 1, 225f),// S+A keys
    WEST(-1, 0, 270f),     // A key
    NORTHWEST(-1, -1, 315f);// W+A keys

    private final int deltaX;
    private final int deltaZ;
    private final float yaw;

    Direction8(int deltaX, int deltaZ, float yaw) {
        this.deltaX = deltaX;
        this.deltaZ = deltaZ;
        this.yaw = yaw;
    }

    public int getDeltaX() { return deltaX; }
    public int getDeltaZ() { return deltaZ; }
    public float getYaw() { return yaw; }

    /**
     * Get direction from player yaw
     */
    public static Direction8 fromYaw(float yaw) {
        // Normalize yaw to 0-360
        yaw = ((yaw % 360) + 360) % 360;

        if (yaw >= 337.5f || yaw < 22.5f) return SOUTH;
        if (yaw >= 22.5f && yaw < 67.5f) return SOUTHWEST;
        if (yaw >= 67.5f && yaw < 112.5f) return WEST;
        if (yaw >= 112.5f && yaw < 157.5f) return NORTHWEST;
        if (yaw >= 157.5f && yaw < 202.5f) return NORTH;
        if (yaw >= 202.5f && yaw < 247.5f) return NORTHEAST;
        if (yaw >= 247.5f && yaw < 292.5f) return EAST;
        return SOUTHEAST;
    }
}