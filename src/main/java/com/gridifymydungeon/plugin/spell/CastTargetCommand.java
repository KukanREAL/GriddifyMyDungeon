package com.gridifymydungeon.plugin.spell;

import com.gridifymydungeon.plugin.gridmove.GridMoveManager;
import com.gridifymydungeon.plugin.gridmove.GridPlayerState;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Set;

/**
 * /CastTarget — Confirm the current aim cell (or pattern snapshot) as a target.
 *
 * Can be called multiple times:
 *   - Each call appends one entry to confirmedTargets.
 *   - Targeting the SAME cell again is allowed: each duplicate spawns a new Grid_Spell
 *     that is 0.2 smaller (first = 1.0, second = 0.8, third = 0.6, …)
 *   - For multi-target spells (Magic Missile etc.): capped at spell.maxTargets.
 *   - For CONE/LINE/WALL: snapshots the entire pattern on first call, subsequent calls
 *     are ignored (the whole pattern fires together).
 *   - The overlay freezes (stops following the player) after the first /CastTarget.
 *
 * Blocked if the player is currently outside the spell's range.
 */
public class CastTargetCommand extends AbstractPlayerCommand {

    private final GridMoveManager playerManager;
    private final SpellVisualManager visualManager;

    public CastTargetCommand(GridMoveManager playerManager, SpellVisualManager visualManager) {
        super("CastTarget", "Confirm the current aim cell as a target");
        this.playerManager = playerManager;
        this.visualManager = visualManager;
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {

        GridPlayerState state = playerManager.getState(playerRef);
        SpellCastingState castState = state.getSpellCastingState();

        if (castState == null || !castState.isValid()) {
            playerRef.sendMessage(Message.raw("[Griddify] No spell being cast. Use /Cast first.").color("#FF0000"));
            return;
        }

        if (castState.isOutOfRange()) {
            int dist = SpellPatternCalculator.getDistance(
                    castState.getCasterGridX(), castState.getCasterGridZ(),
                    castState.getAimGridX(), castState.getAimGridZ());
            playerRef.sendMessage(Message.raw("[Griddify] Out of range! ("
                    + dist + "/" + castState.getSpell().getRangeGrids()
                    + " grids) — return to range first.").color("#FF0000"));
            return;
        }

        SpellData spell = castState.getSpell();
        SpellPattern pattern = spell.getPattern();
        int aimX = castState.getAimGridX();
        int aimZ = castState.getAimGridZ();

        // ── CONE / LINE / WALL: snapshot the whole pattern once, ignore further calls ──
        boolean isDirectionalPattern = pattern == SpellPattern.CONE
                || pattern == SpellPattern.LINE
                || pattern == SpellPattern.WALL;
        if (isDirectionalPattern) {
            if (castState.hasConfirmedCells()) {
                playerRef.sendMessage(Message.raw(
                        "[Griddify] " + spell.getName() + " already locked. Use /CastFinal to fire.").color("#FFA500"));
                return;
            }
            Set<SpellPatternCalculator.GridCell> cells = CastCommand.computeOverlay(
                    pattern, castState.getDirection(),
                    castState.getCasterGridX(), castState.getCasterGridZ(),
                    spell, aimX, aimZ);
            castState.setConfirmedCells(cells, aimX, aimZ);
            final float refY = castState.getCasterY();
            world.execute(() -> visualManager.showSpellArea(playerRef.getUuid(), cells, world, refY));
            playerRef.sendMessage(Message.raw("[Griddify] " + spell.getName()
                    + " locked — " + cells.size() + " cells. Use /CastFinal to fire.").color("#00FF7F"));
            return;
        }

        // ── All other patterns (SINGLE_TARGET, SPHERE, CUBE, CYLINDER, MULTI) ──
        int max = spell.getMaxTargets();

        // Hard cap — same for every spell (1 for Fire Bolt, 3 for Magic Missile, etc.)
        if (castState.getConfirmedTargetCount() >= max) {
            playerRef.sendMessage(Message.raw(
                    "[Griddify] Already confirmed " + max + "/" + max
                            + " targets. Use /CastFinal to fire.").color("#FFA500"));
            return;
        }

        // Always recompute cells from current aim position.
        Set<SpellPatternCalculator.GridCell> cells = CastCommand.computeOverlay(
                pattern, castState.getDirection(),
                castState.getCasterGridX(), castState.getCasterGridZ(),
                spell, aimX, aimZ);
        castState.setConfirmedCells(cells, aimX, aimZ);

        // Append this target BEFORE redrawing so all stacked tiles are replayed correctly
        castState.confirmTarget();
        int totalConfirmed = castState.getConfirmedTargetCount();
        int hitCount = castState.getTargetCountAt(aimX, aimZ);

        // Compute hit counts for ALL previously confirmed unique cells so we can re-add their tiles
        final java.util.Map<String, Integer> hitCountMap = new java.util.LinkedHashMap<>();
        for (SpellCastingState.GridCell c : castState.getConfirmedTargets()) {
            String key = c.x + "," + c.z;
            hitCountMap.put(key, hitCountMap.getOrDefault(key, 0) + 1);
        }

        final float refY = castState.getCasterY();
        final Set<SpellPatternCalculator.GridCell> finalCells = cells;
        final java.util.Map<String, Integer> finalHitCounts = hitCountMap;

        world.execute(() -> {
            // showSpellArea clears all visuals then draws base overlay for the current aim cell
            visualManager.showSpellArea(playerRef.getUuid(), finalCells, world, refY);
            // Re-add stacked tiles for ALL confirmed cells (including this one)
            for (java.util.Map.Entry<String, Integer> e : finalHitCounts.entrySet()) {
                String[] parts = e.getKey().split(",");
                int cx = Integer.parseInt(parts[0]), cz = Integer.parseInt(parts[1]);
                SpellPatternCalculator.GridCell cell = new SpellPatternCalculator.GridCell(cx, cz);
                for (int i = 1; i <= e.getValue(); i++) {
                    visualManager.addStackedSpellTile(playerRef.getUuid(), cell, i, world, refY);
                }
            }
        });

        // Unified feedback for all spell types
        String hitNote = hitCount > 1
                ? " [x" + hitCount + " on this cell, scale "
                + String.format("%.1f", Math.max(0.2f, 1.0f - (hitCount - 1) * 0.2f)) + "]"
                : "";
        if (totalConfirmed >= max) {
            playerRef.sendMessage(Message.raw(
                    "[Griddify] " + totalConfirmed + "/" + max + " confirmed" + hitNote
                            + " — ready! Use /CastFinal.").color("#00FF7F"));
        } else {
            playerRef.sendMessage(Message.raw(
                    "[Griddify] " + totalConfirmed + "/" + max + " confirmed" + hitNote
                            + " — walk to next target and /CastTarget, or /CastFinal now.").color("#FFD700"));
        }
    }
}