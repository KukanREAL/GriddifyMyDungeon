package com.gridifymydungeon.plugin.spell;

import com.gridifymydungeon.plugin.dnd.MonsterState;

/**
 * Tracks active spell being cast by a player
 * Used between /cast and /castfinal commands
 */
public class SpellCastingState {
    private SpellData spell;
    private MonsterState targetMonster;
    private Direction8 direction;
    private int casterGridX;
    private int casterGridZ;
    private long castStartTime;

    public SpellCastingState(SpellData spell, MonsterState targetMonster, Direction8 direction,
                             int casterGridX, int casterGridZ) {
        this.spell = spell;
        this.targetMonster = targetMonster;
        this.direction = direction;
        this.casterGridX = casterGridX;
        this.casterGridZ = casterGridZ;
        this.castStartTime = System.currentTimeMillis();
    }

    public SpellData getSpell() { return spell; }
    public MonsterState getTargetMonster() { return targetMonster; }
    public Direction8 getDirection() { return direction; }
    public int getCasterGridX() { return casterGridX; }
    public int getCasterGridZ() { return casterGridZ; }
    public long getCastStartTime() { return castStartTime; }

    /**
     * Check if cast state is still valid (within 30 seconds)
     */
    public boolean isValid() {
        return (System.currentTimeMillis() - castStartTime) < 30000;
    }
}