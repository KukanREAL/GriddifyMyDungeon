package com.gridifymydungeon.plugin.spell;

import com.gridifymydungeon.plugin.dnd.EncounterManager;
import com.gridifymydungeon.plugin.dnd.MonsterState;
import com.gridifymydungeon.plugin.dnd.RoleManager;
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
 * For players: NPC stays frozen until player walks back to it.
 * For GM: monster is unfrozen immediately so the GM can keep moving it.
 */
public class CastCancelCommand extends AbstractPlayerCommand {
    private final GridMoveManager playerManager;
    private final SpellVisualManager visualManager;
    private final EncounterManager encounterManager;
    private final RoleManager roleManager;

    public CastCancelCommand(GridMoveManager playerManager, SpellVisualManager visualManager,
                             EncounterManager encounterManager, RoleManager roleManager) {
        super("CastCancel", "Cancel a prepared spell");
        this.playerManager = playerManager;
        this.visualManager = visualManager;
        this.encounterManager = encounterManager;
        this.roleManager = roleManager;
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

        state.clearSpellCastingState();
        world.execute(() -> visualManager.clearSpellVisuals(playerRef.getUuid(), world));

        // For GM: unfreeze monster immediately so it can keep moving
        if (roleManager.isGM(playerRef)) {
            MonsterState monster = encounterManager.getControlledMonster();
            if (monster != null && monster.isFrozen && "casting".equals(monster.freezeReason)) {
                monster.unfreeze();
            }
            state.unfreeze();
            playerRef.sendMessage(Message.raw("[Griddify] " + spellName + " cancelled. Monster unfrozen.").color("#FFA500"));
        } else {
            // Player: NPC stays frozen at its current position.
            // Re-freeze with "post_cast" so the player must walk BACK to the NPC cell to unfreeze.
            state.freeze("post_cast");
            playerRef.sendMessage(Message.raw("[Griddify] " + spellName + " cancelled.").color("#FFA500"));
            playerRef.sendMessage(Message.raw("[Griddify] Walk back to your NPC at ("
                    + state.frozenGridX + ", " + state.frozenGridZ + ") to unfreeze it.").color("#AAAAAA"));
        }

        System.out.println("[Griddify] [CASTCANCEL] " + playerRef.getUsername() + " cancelled " + spellName);
    }
}