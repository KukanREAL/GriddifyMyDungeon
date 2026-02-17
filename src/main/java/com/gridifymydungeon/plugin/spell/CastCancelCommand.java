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

/**
 * /CastCancel - Cancel a prepared spell and clear the red overlay.
 * Does NOT immediately unfreeze the NPC. The NPC stays at its locked position
 * and unfreezes naturally when the player walks back to the same grid cell
 * (same behaviour as a collision-freeze).
 */
public class CastCancelCommand extends AbstractPlayerCommand {
    private final GridMoveManager playerManager;
    private final SpellVisualManager visualManager;

    public CastCancelCommand(GridMoveManager playerManager, SpellVisualManager visualManager) {
        super("CastCancel", "Cancel a prepared spell");
        this.playerManager = playerManager;
        this.visualManager = visualManager;
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {

        GridPlayerState state = playerManager.getState(playerRef);

        if (state.getSpellCastingState() == null) {
            playerRef.sendMessage(Message.raw("[Griddify] No spell prepared to cancel.").color("#FFA500"));
            return;
        }

        String spellName = state.getSpellCastingState().getSpell().getName();

        // Clear the spell state — this lets the normal collision-freeze unfreeze logic
        // handle it: the NPC stays put and the player walks back to it to unfreeze.
        state.clearSpellCastingState();
        visualManager.clearSpellVisuals(playerRef.getUuid(), world);

        playerRef.sendMessage(Message.raw("[Griddify] " + spellName + " cancelled.").color("#FFA500"));
        playerRef.sendMessage(Message.raw("[Griddify] Walk back to your NPC to unfreeze it.").color("#AAAAAA"));
        System.out.println("[Griddify] [CASTCANCEL] " + playerRef.getUsername() + " cancelled " + spellName);
    }
}