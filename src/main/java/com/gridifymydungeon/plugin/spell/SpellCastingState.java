package com.gridifymydungeon.plugin.spell;

import com.gridifymydungeon.plugin.dnd.MonsterState;

/**
 * Tracks the spell being prepared between /cast and /castfinal.
 * aimGridX/Z are updated in real time by PlayerPositionTracker as the player walks to aim.
 */
public class SpellCastingState {
    private final SpellData spell;
    private final Direction8 direction;
    private final int casterGridX;
    private final int casterGridZ;
    private final long castStartTime;

    // Updated each time the player moves while casting
    private int aimGridX;
    private int aimGridZ;

    public SpellCastingState(SpellData spell, MonsterState ignoredTarget,
                             Direction8 direction, int casterGridX, int casterGridZ) {
        this.spell = spell;
        this.direction = direction;
        this.casterGridX = casterGridX;
        this.casterGridZ = casterGridZ;
        // Aim starts at caster position
        this.aimGridX = casterGridX;
        this.aimGridZ = casterGridZ;
        this.castStartTime = System.currentTimeMillis();
    }

    public SpellData getSpell() { return spell; }
    public Direction8 getDirection() { return direction; }
    public int getCasterGridX() { return casterGridX; }
    public int getCasterGridZ() { return casterGridZ; }

    /** Called by PlayerPositionTracker each time the player steps to a new grid cell while casting */
    public void updateAim(int x, int z) { this.aimGridX = x; this.aimGridZ = z; }
    public int getAimGridX() { return aimGridX; }
    public int getAimGridZ() { return aimGridZ; }

    /** Cast state expires after 60 seconds */
    public boolean isValid() {
        return (System.currentTimeMillis() - castStartTime) < 60000;
    }
}