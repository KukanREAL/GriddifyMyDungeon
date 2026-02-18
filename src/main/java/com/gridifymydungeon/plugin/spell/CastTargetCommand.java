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
import java.util.HashSet;
import java.util.Set;

/**
 * /CastTarget — For multi-target spells (e.g. Magic Missile, Bless).
 *
 * Walk to the first target cell and type /CastTarget to lock it in.
 * Repeat for each target. A red grid shows confirmed cells + current cursor.
 * Once all targets are confirmed the overlay shows all of them simultaneously.
 * Then /CastFinal fires the spell against every confirmed target.
 */
public class CastTargetCommand extends AbstractPlayerCommand {

    private final GridMoveManager playerManager;
    private final SpellVisualManager visualManager;

    public CastTargetCommand(GridMoveManager playerManager, SpellVisualManager visualManager) {
        super("CastTarget", "Confirm a target cell for a multi-target spell");
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

        SpellData spell = castState.getSpell();
        if (!spell.isMultiTarget()) {
            playerRef.sendMessage(Message.raw(
                    "[Griddify] " + spell.getName() + " is not a multi-target spell. Use /CastFinal.").color("#FF0000"));
            return;
        }

        // Check range from caster
        int aimX = castState.getAimGridX();
        int aimZ = castState.getAimGridZ();
        int dist = SpellPatternCalculator.getDistance(
                castState.getCasterGridX(), castState.getCasterGridZ(), aimX, aimZ);
        if (spell.getRangeGrids() > 0 && dist > spell.getRangeGrids()) {
            playerRef.sendMessage(Message.raw(
                    "[Griddify] Out of range! (" + dist + " / " + spell.getRangeGrids() + " grids)").color("#FF0000"));
            return;
        }

        boolean added = castState.confirmTarget();
        int confirmed = castState.getConfirmedTargetCount();
        int max = spell.getMaxTargets();

        if (!added) {
            playerRef.sendMessage(Message.raw(
                    "[Griddify] Target already selected, or all " + max + " targets already confirmed.").color("#FFA500"));
            return;
        }

        // Refresh overlay: show all confirmed targets + current cursor cell
        final Set<SpellPatternCalculator.GridCell> allCells = new HashSet<>();
        for (SpellCastingState.GridCell c : castState.getConfirmedTargets()) {
            allCells.add(new SpellPatternCalculator.GridCell(c.x, c.z));
        }
        // Also add current cursor so the player can see where they are
        allCells.add(new SpellPatternCalculator.GridCell(aimX, aimZ));
        final float refY = castState.getCasterY();
        world.execute(() -> visualManager.showSpellArea(playerRef.getUuid(), allCells, world, refY));

        if (confirmed >= max) {
            playerRef.sendMessage(Message.raw(
                    "[Griddify] All " + max + " targets confirmed! Use /CastFinal to fire.").color("#00FF7F"));
        } else {
            playerRef.sendMessage(Message.raw(
                    "[Griddify] Target " + confirmed + "/" + max + " confirmed. Walk to next target.").color("#FFD700"));
        }
    }
}