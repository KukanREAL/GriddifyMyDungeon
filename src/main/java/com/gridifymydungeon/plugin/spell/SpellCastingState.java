package com.gridifymydungeon.plugin.spell;

import com.gridifymydungeon.plugin.dnd.MonsterState;

/**
 * Tracks the spell being prepared between /cast and /castfinal.
 *
 * For NPC-origin directional spells (CONE, LINE, WALL):
 *   - NPC is frozen at casterGridX/Z
 *   - direction updates live based on where the player body stands relative to the NPC
 *   - aimGridX/Z tracks the player's current body position (used to derive direction)
 *
 * For targeted spells (SINGLE_TARGET, SPHERE, CUBE, etc.):
 *   - NPC is frozen at casterGridX/Z
 *   - aimGridX/Z is the aimed target cell (player body position)
 *
 * For self spells (SELF, AURA):
 *   - No freeze, fires immediately.
 */
public class SpellCastingState {
    private final SpellData spell;
    private final int casterGridX;
    private final int casterGridZ;
    private final long castStartTime;

    // For directional spells: direction updates as player walks around NPC
    private Direction8 direction;

    // Player's current body grid position (aim for targeted, position for directional)
    private int aimGridX;
    private int aimGridZ;

    public SpellCastingState(SpellData spell, MonsterState ignoredTarget,
                             Direction8 initialDirection, int casterGridX, int casterGridZ) {
        this.spell = spell;
        this.direction = initialDirection;
        this.casterGridX = casterGridX;
        this.casterGridZ = casterGridZ;
        this.aimGridX = casterGridX;
        this.aimGridZ = casterGridZ;
        this.castStartTime = System.currentTimeMillis();
    }

    public SpellData getSpell() { return spell; }
    public int getCasterGridX() { return casterGridX; }
    public int getCasterGridZ() { return casterGridZ; }
    public Direction8 getDirection() { return direction; }

    /**
     * Called by PlayerPositionTracker when the player body moves.
     * Updates aim position and — for directional spells — recalculates direction
     * from the player's position relative to the frozen NPC.
     */
    public void updatePlayerPosition(int playerGridX, int playerGridZ) {
        this.aimGridX = playerGridX;
        this.aimGridZ = playerGridZ;

        SpellPattern pattern = spell.getPattern();
        if (pattern == SpellPattern.CONE || pattern == SpellPattern.LINE || pattern == SpellPattern.WALL) {
            this.direction = directionFromRelativePosition(playerGridX, playerGridZ);
        }
    }

    /**
     * Derive the 8-directional facing from where the player body is relative to the NPC.
     * If the player is on the same cell as the NPC, keep existing direction.
     */
    private Direction8 directionFromRelativePosition(int playerGridX, int playerGridZ) {
        int dx = playerGridX - casterGridX;
        int dz = playerGridZ - casterGridZ;

        if (dx == 0 && dz == 0) return direction; // same cell, keep current

        // Normalise to -1/0/1
        int sx = Integer.compare(dx, 0);
        int sz = Integer.compare(dz, 0);

        for (Direction8 d : Direction8.values()) {
            if (d.getDeltaX() == sx && d.getDeltaZ() == sz) return d;
        }
        return direction; // fallback
    }

    public int getAimGridX() { return aimGridX; }
    public int getAimGridZ() { return aimGridZ; }

    /** Cast state expires after 60 seconds */
    public boolean isValid() {
        return (System.currentTimeMillis() - castStartTime) < 60000;
    }
}