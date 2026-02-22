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
import java.util.Set;

/**
 * /CastTarget — Snapshot the current spell pattern and lock it in.
 *
 * Works for ALL patterns:
 *   SINGLE_TARGET : confirms the one cell under the player → /CastFinal fires at it
 *   CONE/LINE/WALL: confirms the entire facing cone/line as currently shown → /CastFinal fires it
 *   SPHERE/CUBE   : confirms the full area centred on the player → /CastFinal fires it
 *   MULTI-TARGET  : for spells like Magic Missile — call /CastTarget once per target cell
 *                   (up to spell.maxTargets), then /CastFinal
 *
 * Blocked if the player is currently outside the spell's range.
 * The player can then move freely to watch the effect, then /CastFinal from anywhere.
 */
public class CastTargetCommand extends AbstractPlayerCommand {

    private final GridMoveManager playerManager;
    private final SpellVisualManager visualManager;

    public CastTargetCommand(GridMoveManager playerManager, SpellVisualManager visualManager) {
        super("CastTarget", "Lock in the current spell pattern");
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

        // Block if out of range
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
        int max  = spell.getMaxTargets();

        // ── Multi-target (Magic Missile etc.) ───────────────────────────────
        // Each /CastTarget call confirms one additional cell until maxTargets is reached.
        if (spell.isMultiTarget()) {
            boolean added = castState.confirmTarget();
            int confirmed = castState.getConfirmedTargetCount();

            if (!added) {
                if (confirmed >= max) {
                    playerRef.sendMessage(Message.raw(
                            "[Griddify] All " + max + " targets confirmed. Use /CastFinal.").color("#FFA500"));
                } else {
                    playerRef.sendMessage(Message.raw(
                            "[Griddify] Already selected this cell. Walk to a different cell.").color("#FFA500"));
                }
                return;
            }

            // Build the display set (SpellPatternCalculator.GridCell for the overlay)
            Set<SpellPatternCalculator.GridCell> display = new java.util.HashSet<>();
            for (SpellCastingState.GridCell c : castState.getConfirmedTargets()) {
                display.add(new SpellPatternCalculator.GridCell(c.x, c.z));
            }
            display.add(new SpellPatternCalculator.GridCell(aimX, aimZ));
            final float refY = castState.getCasterY();

            // Snapshot into confirmedCells. setConfirmedCells resets confirmedTargets to {aimX,aimZ},
            // so we re-populate it with all confirmed points so the staggered missile loop works.
            castState.setConfirmedCells(display, aimX, aimZ);
            castState.getConfirmedTargets().clear();
            for (SpellPatternCalculator.GridCell c : castState.getConfirmedCells()) {
                castState.getConfirmedTargets().add(new SpellCastingState.GridCell(c.x, c.z));
            }

            world.execute(() -> visualManager.showSpellArea(playerRef.getUuid(), display, world, refY));

            if (confirmed >= max) {
                playerRef.sendMessage(Message.raw(
                        "[Griddify] All " + max + " targets confirmed! Use /CastFinal.").color("#00FF7F"));
            } else {
                playerRef.sendMessage(Message.raw(
                        "[Griddify] Target " + confirmed + "/" + max
                                + " confirmed. Walk to next target and /CastTarget again.").color("#FFD700"));
            }
            return;
        }

        // ── All other patterns: snapshot the current computed overlay ────────
        // Compute the exact same cells that are currently highlighted (mirrors PlayerPositionTracker logic)
        Set<SpellPatternCalculator.GridCell> cells = CastCommand.computeOverlay(
                pattern, castState.getDirection(),
                castState.getCasterGridX(), castState.getCasterGridZ(),
                spell, aimX, aimZ);

        castState.setConfirmedCells(cells, aimX, aimZ);

        // Keep the overlay showing the locked pattern
        final Set<SpellPatternCalculator.GridCell> displayCells = cells;
        final float refY = castState.getCasterY();
        world.execute(() -> visualManager.showSpellArea(playerRef.getUuid(), displayCells, world, refY));

        // Describe what was locked
        String patternDesc;
        switch (pattern) {
            case CONE:  patternDesc = "cone (" + cells.size() + " cells)"; break;
            case LINE:  patternDesc = "line (" + cells.size() + " cells)"; break;
            case WALL:  patternDesc = "wall (" + cells.size() + " cells)"; break;
            case SPHERE: case CUBE: case CYLINDER:
                patternDesc = "area (" + cells.size() + " cells)"; break;
            default:    patternDesc = "target at (" + aimX + ", " + aimZ + ")"; break;
        }
        playerRef.sendMessage(Message.raw("[Griddify] " + spell.getName() + " locked — "
                + patternDesc + ". Use /CastFinal to fire.").color("#00FF7F"));
    }
}