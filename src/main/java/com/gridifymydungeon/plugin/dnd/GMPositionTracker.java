package com.gridifymydungeon.plugin.dnd;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.gridifymydungeon.plugin.dnd.commands.MonsterEntityController;
import com.gridifymydungeon.plugin.gridmove.CollisionDetector;
import com.gridifymydungeon.plugin.gridmove.GridMoveManager;
import com.gridifymydungeon.plugin.gridmove.GridOverlayManager;
import com.gridifymydungeon.plugin.gridmove.GridPlayerState;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tracks GM position when controlling monsters
 * FIXED: Flying scans ground (no wall clip), height >= 3.0f, grid overlay refresh
 */
public class GMPositionTracker {

    private static final double GRID_SIZE = 2.0;
    private static final long MOVE_COOLDOWN_MS = 250;

    private final EncounterManager encounterManager;
    private final RoleManager roleManager;
    private final CombatManager combatManager;
    private final CollisionDetector collisionDetector;
    private final GridMoveManager gridMoveManager;

    private final Map<PlayerRef, Long> lastMoveTime = new HashMap<>();
    private final AtomicBoolean movePending = new AtomicBoolean(false);

    public GMPositionTracker(EncounterManager encounterManager, RoleManager roleManager,
                             CombatManager combatManager, CollisionDetector collisionDetector,
                             GridMoveManager gridMoveManager) {
        this.encounterManager = encounterManager;
        this.roleManager = roleManager;
        this.combatManager = combatManager;
        this.collisionDetector = collisionDetector;
        this.gridMoveManager = gridMoveManager;
    }

    public void onGMMove(PlayerRef playerRef, World world, Vector3d newPosition) {
        if (!roleManager.isGM(playerRef)) {
            return;
        }

        MonsterState monster = encounterManager.getControlledMonster();
        if (monster == null) {
            return;
        }

        long now = System.currentTimeMillis();
        Long lastMove = lastMoveTime.get(playerRef);
        if (lastMove != null && (now - lastMove) < MOVE_COOLDOWN_MS) {
            return;
        }

        int[] newGrid = computeGridSnap(newPosition.getX(), newPosition.getZ(),
                monster.currentGridX, monster.currentGridZ);
        int newGridX = newGrid[0];
        int newGridZ = newGrid[1];

        if (monster.isFrozen && newGridX == monster.currentGridX && newGridZ == monster.currentGridZ) {
            monster.unfreeze();
            playerRef.sendMessage(Message.raw("[Griddify] Monster unfrozen!"));
        }

        if (newGridX != monster.currentGridX || newGridZ != monster.currentGridZ) {
            lastMoveTime.put(playerRef, now);
            handleMonsterMovement(playerRef, monster, newGridX, newGridZ, newPosition.getY(), world);
        }

        monster.lastGMPosition = newPosition;
    }

    // --- Diagonal-aware grid snap ---

    private int[] computeGridSnap(double posX, double posZ, int currentGridX, int currentGridZ) {
        int hystX = hysteresisSnap(posX, currentGridX);
        int hystZ = hysteresisSnap(posZ, currentGridZ);
        int rawX = (int) Math.floor(posX / GRID_SIZE);
        int rawZ = (int) Math.floor(posZ / GRID_SIZE);

        boolean xTriggered = hystX != currentGridX;
        boolean zTriggered = hystZ != currentGridZ;
        boolean xCrossed = rawX != currentGridX;
        boolean zCrossed = rawZ != currentGridZ;

        if (xTriggered && zTriggered) {
            return new int[]{hystX, hystZ};
        }
        if (xTriggered) {
            return zCrossed ? new int[]{hystX, rawZ} : new int[]{hystX, currentGridZ};
        }
        if (zTriggered) {
            return xCrossed ? new int[]{rawX, hystZ} : new int[]{currentGridX, hystZ};
        }
        return new int[]{currentGridX, currentGridZ};
    }

    private int hysteresisSnap(double worldPos, int currentGrid) {
        double currentCenter = currentGrid * GRID_SIZE + (GRID_SIZE / 2.0);
        double zoneMin = currentCenter - GRID_SIZE;
        double zoneMax = currentCenter + GRID_SIZE;

        if (worldPos >= zoneMin && worldPos < zoneMax) {
            return currentGrid;
        }
        return (int) Math.floor(worldPos / GRID_SIZE);
    }

    // --- Movement handling ---

    private void handleMonsterMovement(PlayerRef playerRef, MonsterState monster,
                                       int newGridX, int newGridZ, double gmY, World world) {

        if (monster.isFrozen) {
            playerRef.sendMessage(Message.raw("[Griddify] Monster frozen! Return to (" +
                    monster.currentGridX + ", " + monster.currentGridZ + ")"));
            return;
        }

        if (combatManager.isCombatActive() && !combatManager.isMonsterTurn(monster.monsterNumber)) {
            playerRef.sendMessage(Message.raw("[Griddify] Not monster's turn!"));
            return;
        }

        // Collision check
        if (collisionDetector.isPositionOccupied(newGridX, newGridZ, monster.monsterNumber, null)) {
            String occupant = collisionDetector.getEntityNameAtPosition(newGridX, newGridZ);
            monster.freeze("Blocked by " + (occupant != null ? occupant : "entity"));
            playerRef.sendMessage(Message.raw("[Griddify] Blocked! Position occupied by: " +
                    (occupant != null ? occupant : "entity")));
            return;
        }

        double moveCost = calculateMoveCost(monster.currentGridX, monster.currentGridZ, newGridX, newGridZ);

        if (combatManager.isCombatActive()) {
            if (!monster.hasMovesRemaining(moveCost)) {
                playerRef.sendMessage(Message.raw("[Griddify] " + monster.getDisplayName() +
                        " no moves! (" + (int) monster.remainingMoves + "/" + (int) monster.maxMoves + ")"));
                return;
            }
        }

        if (!movePending.compareAndSet(false, true)) {
            return;
        }

        final int oldGridX = monster.currentGridX;
        final int oldGridZ = monster.currentGridZ;

        world.execute(() -> {
            try {
                // FLYING: Still scan for ground (avoid clipping into walls), skip height limit
                if (monster.isFlying || monster.stats.isFlying) {
                    Float newGroundY = MonsterEntityController.scanForGroundPublic(world, newGridX, newGridZ, (float) gmY);

                    if (newGroundY == null) {
                        playerRef.sendMessage(Message.raw("[Griddify] Can't fly there - no valid landing!"));
                        return;
                    }

                    if (combatManager.isCombatActive()) {
                        monster.consumeMoves(moveCost);
                        playerRef.sendMessage(Message.raw("[Griddify] " + monster.getDisplayName() +
                                " moves: " + (int) monster.remainingMoves + "/" + (int) monster.maxMoves));
                    }

                    MonsterEntityController.teleportMonsterToY(world, monster, newGridX, newGridZ, newGroundY);
                    monster.currentGridX = newGridX;
                    monster.currentGridZ = newGridZ;

                    float yaw = calculateFacingYaw(oldGridX, oldGridZ, newGridX, newGridZ);
                    MonsterEntityController.setMonsterYaw(world, monster, yaw);

                    refreshGMGridOverlay(playerRef, monster, world);
                    return;
                }

                // Non-flying: ground scan + height check
                Float newGroundY = MonsterEntityController.scanForGroundPublic(world, newGridX, newGridZ, (float) gmY);

                if (newGroundY == null) {
                    monster.freeze("No ground");
                    playerRef.sendMessage(Message.raw("[Griddify] No ground!"));
                    return;
                }

                float heightDiff = newGroundY - monster.spawnY;

                // FIXED: >= 3.0f (strictly under 3 blocks climb, 2 OK, 3 NOT OK)
                if (heightDiff >= 3.0f) {
                    monster.freeze("Too steep (+" + (int) heightDiff + ")");
                    playerRef.sendMessage(Message.raw("[Griddify] Too steep! (+" + (int) heightDiff + ")"));
                    return;
                }

                if (heightDiff < -4.0f) {
                    monster.freeze("Too steep (" + (int) heightDiff + ")");
                    playerRef.sendMessage(Message.raw("[Griddify] Too steep! (" + (int) heightDiff + ")"));
                    return;
                }

                if (combatManager.isCombatActive()) {
                    monster.consumeMoves(moveCost);
                    playerRef.sendMessage(Message.raw("[Griddify] " + monster.getDisplayName() +
                            " moves: " + (int) monster.remainingMoves + "/" + (int) monster.maxMoves));
                }

                MonsterEntityController.teleportMonsterToY(world, monster, newGridX, newGridZ, newGroundY);

                monster.currentGridX = newGridX;
                monster.currentGridZ = newGridZ;

                float yaw = calculateFacingYaw(oldGridX, oldGridZ, newGridX, newGridZ);
                MonsterEntityController.setMonsterYaw(world, monster, yaw);

                refreshGMGridOverlay(playerRef, monster, world);

            } finally {
                movePending.set(false);
            }
        });
    }

    /**
     * Refresh the GM's grid overlay after a monster move.
     */
    private void refreshGMGridOverlay(PlayerRef playerRef, MonsterState monster, World world) {
        GridPlayerState gmState = gridMoveManager.getState(playerRef);
        if (gmState == null || !gmState.gridOverlayEnabled) {
            return;
        }

        gmState.currentGridX = monster.currentGridX;
        gmState.currentGridZ = monster.currentGridZ;
        gmState.npcY = monster.spawnY;
        gmState.remainingMoves = monster.remainingMoves;
        gmState.maxMoves = monster.maxMoves;

        GridOverlayManager.refreshGridOverlay(world, gmState, collisionDetector, null);
    }

    // --- Utilities ---

    private float calculateFacingYaw(int fromX, int fromZ, int toX, int toZ) {
        int dx = toX - fromX;
        int dz = toZ - fromZ;
        return (float) Math.atan2(-dx, -dz);
    }

    private double calculateMoveCost(int fromX, int fromZ, int toX, int toZ) {
        int dx = Math.abs(toX - fromX);
        int dz = Math.abs(toZ - fromZ);
        return (dx > 0 && dz > 0) ? 1.5 : 1.0;
    }
}