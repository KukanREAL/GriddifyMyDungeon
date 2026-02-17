package com.gridifymydungeon.plugin.gridmove;

import com.gridifymydungeon.plugin.dnd.EncounterManager;
import com.gridifymydungeon.plugin.dnd.MonsterState;

import java.util.Map;
import java.util.UUID;

/**
 * Detects collisions between entities on the grid
 * FIXED v2: Also checks player NPC positions, not just monsters
 */
public class CollisionDetector {

    private final GridMoveManager gridMoveManager;
    private final EncounterManager encounterManager;

    public CollisionDetector(GridMoveManager gridMoveManager, EncounterManager encounterManager) {
        this.gridMoveManager = gridMoveManager;
        this.encounterManager = encounterManager;
    }

    /**
     * Check if grid position is occupied by ANY entity (monster or player NPC).
     * Excludes the specified monster number and/or player UUID from the check.
     *
     * @param gridX            target grid X
     * @param gridZ            target grid Z
     * @param excludeMonster   monster number to exclude (-1 to not exclude any monster)
     * @param excludePlayer    player UUID to exclude (null to not exclude any player)
     * @return true if another entity is at this grid position
     */
    public boolean isPositionOccupied(int gridX, int gridZ, int excludeMonster, UUID excludePlayer) {
        // Check monsters
        for (MonsterState monster : encounterManager.getAllMonsters().values()) {
            if (monster.monsterNumber != excludeMonster) {
                if (monster.currentGridX == gridX && monster.currentGridZ == gridZ) {
                    return true;
                }
            }
        }

        // Check player NPCs
        for (Map.Entry<UUID, GridPlayerState> entry : gridMoveManager.getStateEntries()) {
            UUID playerUUID = entry.getKey();
            GridPlayerState state = entry.getValue();

            // Skip excluded player
            if (excludePlayer != null && playerUUID.equals(excludePlayer)) {
                continue;
            }

            // Skip players without active NPC
            if (state.npcEntity == null || !state.npcEntity.isValid()) {
                continue;
            }

            if (state.currentGridX == gridX && state.currentGridZ == gridZ) {
                return true;
            }
        }

        return false;
    }

    /**
     * Original overload for backward compatibility (CreatureCommand uses this)
     * Only excludes a monster number, checks all players
     */
    public boolean isPositionOccupied(int gridX, int gridZ, int excludeMonsterNumber) {
        return isPositionOccupied(gridX, gridZ, excludeMonsterNumber, null);
    }

    /**
     * Get display name of entity at position
     */
    public String getEntityNameAtPosition(int gridX, int gridZ) {
        // Check monsters
        for (MonsterState monster : encounterManager.getAllMonsters().values()) {
            if (monster.currentGridX == gridX && monster.currentGridZ == gridZ) {
                return monster.getDisplayName();
            }
        }

        // Check players
        for (Map.Entry<UUID, GridPlayerState> entry : gridMoveManager.getStateEntries()) {
            GridPlayerState state = entry.getValue();
            if (state.npcEntity != null && state.npcEntity.isValid()) {
                if (state.currentGridX == gridX && state.currentGridZ == gridZ) {
                    return "Player";
                }
            }
        }

        return null;
    }

    /**
     * Find nearest free position (checks both monsters and players)
     */
    public int[] findNearestFreePosition(int targetX, int targetZ, int maxSearchRadius) {
        if (!isPositionOccupied(targetX, targetZ, -1, null)) {
            return new int[]{targetX, targetZ};
        }

        for (int radius = 1; radius <= maxSearchRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) == radius || Math.abs(dz) == radius) {
                        int checkX = targetX + dx;
                        int checkZ = targetZ + dz;

                        if (!isPositionOccupied(checkX, checkZ, -1, null)) {
                            return new int[]{checkX, checkZ};
                        }
                    }
                }
            }
        }

        return new int[]{targetX, targetZ};
    }
}