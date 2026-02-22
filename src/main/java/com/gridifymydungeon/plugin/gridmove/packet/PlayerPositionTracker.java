package com.gridifymydungeon.plugin.gridmove.packet;

import com.gridifymydungeon.plugin.dnd.CombatManager;
import com.gridifymydungeon.plugin.dnd.EncounterManager;
import com.gridifymydungeon.plugin.dnd.PlayerEntityController;
import com.gridifymydungeon.plugin.dnd.RoleManager;
import com.gridifymydungeon.plugin.gridmove.CollisionDetector;
import com.gridifymydungeon.plugin.gridmove.GridMoveManager;
import com.gridifymydungeon.plugin.gridmove.GridOverlayManager;
import com.gridifymydungeon.plugin.gridmove.GridPlayerState;
import com.gridifymydungeon.plugin.spell.SpellCastingState;
import com.gridifymydungeon.plugin.spell.SpellPatternCalculator;
import com.gridifymydungeon.plugin.spell.SpellVisualManager;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tracks player position changes and handles grid movement
 * FIXED: Movement only costs moves during combat. Outside combat, free movement
 *        but /gridon still shows max range (uses maxMoves since remainingMoves stays full).
 * FIXED: Out-of-range spell cancel no longer teleports the NPC to the player.
 */
public class PlayerPositionTracker {

    private static final double GRID_SIZE = 2.0;
    private static final long MOVE_COOLDOWN_MS = 200;

    private final GridMoveManager gridMoveManager;
    private final RoleManager roleManager;
    private final EncounterManager encounterManager;
    private final CombatManager combatManager;
    private final CollisionDetector collisionDetector;

    private final Map<PlayerRef, Long> lastMoveTime = new HashMap<>();
    private final AtomicBoolean movePending = new AtomicBoolean(false);

    // Injected after construction (to avoid circular dependency)
    private SpellVisualManager spellVisualManager = null;

    public void setSpellVisualManager(SpellVisualManager svm) {
        this.spellVisualManager = svm;
    }

    public PlayerPositionTracker(GridMoveManager gridMoveManager, RoleManager roleManager,
                                 EncounterManager encounterManager, CombatManager combatManager,
                                 CollisionDetector collisionDetector) {
        this.gridMoveManager = gridMoveManager;
        this.roleManager = roleManager;
        this.encounterManager = encounterManager;
        this.combatManager = combatManager;
        this.collisionDetector = collisionDetector;
    }

    public void onPlayerMove(PlayerRef playerRef, World world, Vector3d newPosition) {
        if (roleManager.isGM(playerRef)) {
            return;
        }

        GridPlayerState state = gridMoveManager.getState(playerRef);
        if (state == null || state.npcEntity == null || !state.npcEntity.isValid()) {
            return;
        }

        long now = System.currentTimeMillis();
        Long lastMove = lastMoveTime.get(playerRef);
        if (lastMove != null && (now - lastMove) < MOVE_COOLDOWN_MS) {
            return;
        }

        int[] newGrid = computeGridSnap(newPosition.getX(), newPosition.getZ(),
                state.currentGridX, state.currentGridZ);
        int newGridX = newGrid[0];
        int newGridZ = newGrid[1];

        // Auto-unfreeze only applies to collision-freeze, NOT spell-casting freeze.
        boolean isCasting = state.getSpellCastingState() != null && state.getSpellCastingState().isValid();

        // If a cast state exists but has EXPIRED, clean up visuals and state now.
        if (state.getSpellCastingState() != null && !state.getSpellCastingState().isValid()) {
            state.clearSpellCastingState();
            state.unfreeze();
            world.execute(() -> {
                spellVisualManager.clearSpellVisuals(playerRef.getUuid(), world);
                spellVisualManager.clearRangeOverlay(playerRef.getUuid(), world);
            });
            playerRef.sendMessage(com.hypixel.hytale.server.core.Message.raw(
                    "[Griddify] Spell casting timed out — cancelled.").color("#FF6347"));
            // isCasting is now false; fall through to normal movement below
            isCasting = false;
        }

        if (!isCasting && state.isFrozen) {
            if ("post_cast".equals(state.freezeReason)) {
                // After casting: NPC stays put. Unfreeze only when player walks back to the NPC's cell.
                if (newGridX == state.frozenGridX && newGridZ == state.frozenGridZ) {
                    state.unfreeze();
                    playerRef.sendMessage(com.hypixel.hytale.server.core.Message.raw(
                            "[Griddify] NPC unfrozen — ready to move!").color("#00FF7F"));
                }
            } else if (newGridX == state.currentGridX && newGridZ == state.currentGridZ) {
                // Collision-freeze: player walked back to NPC cell — unfreeze
                state.unfreeze();
                playerRef.sendMessage(com.hypixel.hytale.server.core.Message.raw("[GridMove] NPC unfrozen!"));
            }
        }

        // While casting: update player position (rotates CONE/LINE/WALL direction live),
        // refresh the red overlay, and cancel targeted spells if the player walks out of range.
        if (isCasting && spellVisualManager != null) {
            com.gridifymydungeon.plugin.spell.SpellCastingState castState = state.getSpellCastingState();
            if (castState != null && (castState.getAimGridX() != newGridX || castState.getAimGridZ() != newGridZ)) {
                com.gridifymydungeon.plugin.spell.SpellPattern pattern = castState.getSpell().getPattern();

                // NPC-origin directional patterns: player walks AROUND the NPC to rotate, no range limit
                boolean isDirectional = (pattern == com.gridifymydungeon.plugin.spell.SpellPattern.CONE ||
                        pattern == com.gridifymydungeon.plugin.spell.SpellPattern.LINE ||
                        pattern == com.gridifymydungeon.plugin.spell.SpellPattern.WALL ||
                        pattern == com.gridifymydungeon.plugin.spell.SpellPattern.SELF ||
                        pattern == com.gridifymydungeon.plugin.spell.SpellPattern.AURA);

                if (!isDirectional) {
                    // Targeted spell — warn once if out of range, but DON'T cancel.
                    // /CastTarget and /CastFinal will refuse to fire while flag is set.
                    int dist = SpellPatternCalculator.getDistance(
                            castState.getCasterGridX(), castState.getCasterGridZ(), newGridX, newGridZ);
                    int range = castState.getSpell().getRangeGrids();
                    boolean nowOut = range > 0 && dist > range;
                    boolean newlyOut = castState.setOutOfRange(nowOut);
                    if (newlyOut) {
                        playerRef.sendMessage(com.hypixel.hytale.server.core.Message.raw(
                                "[Griddify] Out of range! (" + dist + "/" + range
                                        + " grids) — return to range to confirm.").color("#FFA500"));
                    }
                }

                // If still casting after the range check, update position and redraw overlay.
                // Skip if the player already confirmed a pattern with /CastTarget — the overlay
                // should stay frozen on the confirmed cells regardless of where they walk.
                if (state.getSpellCastingState() != null && !castState.hasConfirmedCells()) {
                    castState.updatePlayerPosition(newGridX, newGridZ); // updates direction for CONE/LINE/WALL
                    final int px = newGridX, pz = newGridZ;
                    final float py = (float) newPosition.getY();
                    final com.gridifymydungeon.plugin.spell.Direction8 dir = castState.getDirection();
                    final com.gridifymydungeon.plugin.spell.SpellData spellData = castState.getSpell();
                    final int cx = castState.getCasterGridX(), cz = castState.getCasterGridZ();
                    // Capture confirmed targets so we can keep them visible for multi-target spells
                    final java.util.List<SpellCastingState.GridCell> confirmed =
                            new java.util.ArrayList<>(castState.getConfirmedTargets());
                    world.execute(() -> {
                        java.util.Set<SpellPatternCalculator.GridCell> cells =
                                com.gridifymydungeon.plugin.spell.CastCommand.computeOverlay(
                                        pattern, dir, cx, cz, spellData, px, pz);
                        // Also keep previously confirmed target cells visible
                        for (SpellCastingState.GridCell c : confirmed) {
                            cells.add(new SpellPatternCalculator.GridCell(c.x, c.z));
                        }
                        System.out.println("[Griddify] [CASTOVERLAY] " + spellData.getName()
                                + " pattern=" + pattern.name()
                                + " dir=" + dir.name()
                                + " caster=(" + cx + "," + cz + ")"
                                + " player=(" + px + "," + pz + ")"
                                + " cells=" + cells.size());
                        spellVisualManager.showSpellArea(playerRef.getUuid(), cells, world, py);
                    });
                }
            }
        }

        if (newGridX != state.currentGridX || newGridZ != state.currentGridZ) {
            lastMoveTime.put(playerRef, now);
            handleGridMovement(playerRef, state, newGridX, newGridZ, newPosition.getY(), world);
        }

        state.lastPlayerPosition = newPosition;
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

    private void handleGridMovement(PlayerRef playerRef, GridPlayerState state,
                                    int newGridX, int newGridZ, double playerY, World world) {

        // During spell casting: NPC stays frozen, only the spell indicator moves (handled above)
        SpellCastingState castState = state.getSpellCastingState();
        if (castState != null && castState.isValid()) {
            // Don't move the NPC — just silently return; spell indicator already updated above
            return;
        }

        if (state.isFrozen) {
            playerRef.sendMessage(com.hypixel.hytale.server.core.Message.raw(
                    "[GridMove] NPC frozen! Return to (" + state.currentGridX + ", " + state.currentGridZ + ")"));
            return;
        }

        if (combatManager.isCombatActive() && !combatManager.isPlayerTurn(playerRef)) {
            playerRef.sendMessage(com.hypixel.hytale.server.core.Message.raw("[Griddify] Not your turn!"));
            return;
        }

        // Collision check
        if (collisionDetector.isPositionOccupied(newGridX, newGridZ, -1, playerRef.getUuid())) {
            String occupant = collisionDetector.getEntityNameAtPosition(newGridX, newGridZ);
            state.freeze("Blocked by " + (occupant != null ? occupant : "entity"));
            playerRef.sendMessage(com.hypixel.hytale.server.core.Message.raw(
                    "[GridMove] Blocked! Position occupied by: " + (occupant != null ? occupant : "entity")));
            return;
        }

        double moveCost = calculateMoveCost(state.currentGridX, state.currentGridZ, newGridX, newGridZ);

        // FIXED: Only consume moves during combat. Outside combat = free movement.
        boolean inCombat = combatManager.isCombatActive();

        if (inCombat && state.hasMaxMovesSet()) {
            if (!state.hasMovesRemaining(moveCost)) {
                if (!state.noMovesMessageShown) {
                    playerRef.sendMessage(com.hypixel.hytale.server.core.Message.raw(
                            "[Griddify] No moves remaining! (" + formatMoves(state.remainingMoves) +
                                    "/" + formatMoves(state.maxMoves) + ")"));
                    state.noMovesMessageShown = true;
                }
                return;
            }
            state.consumeMoves(moveCost);
            playerRef.sendMessage(com.hypixel.hytale.server.core.Message.raw(
                    "[Griddify] Moves: " + formatMoves(state.remainingMoves) + "/" + formatMoves(state.maxMoves)));
        }

        if (!movePending.compareAndSet(false, true)) {
            if (inCombat && state.hasMaxMovesSet()) {
                state.remainingMoves += moveCost;
            }
            return;
        }

        final int oldGridX = state.currentGridX;
        final int oldGridZ = state.currentGridZ;

        world.execute(() -> {
            try {
                boolean success = PlayerEntityController.checkHeightAndTeleport(
                        world, state, newGridX, newGridZ, playerY, playerRef);

                if (success) {
                    state.currentGridX = newGridX;
                    state.currentGridZ = newGridZ;

                    float yaw = calculateFacingYaw(oldGridX, oldGridZ, newGridX, newGridZ);
                    PlayerEntityController.setNpcYaw(world, state, yaw);

                    gridMoveManager.moveDirectionHolograms(world, state);

                    // Refresh grid overlay (only meaningful in combat when moves are consumed)
                    if (state.gridOverlayEnabled) {
                        GridOverlayManager.refreshGridOverlay(
                                world, state, collisionDetector, playerRef.getUuid());
                    }
                } else {
                    // Refund moves on failed teleport (only in combat)
                    if (inCombat && state.hasMaxMovesSet()) {
                        state.remainingMoves += moveCost;
                    }
                }
            } finally {
                movePending.set(false);
            }
        });
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

    private String formatMoves(double moves) {
        if (moves == Math.floor(moves)) {
            return String.valueOf((int) moves);
        }
        return String.format("%.1f", moves);
    }
}