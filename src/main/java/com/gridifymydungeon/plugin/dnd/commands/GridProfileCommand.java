package com.gridifymydungeon.plugin.dnd.commands;

import com.gridifymydungeon.plugin.dnd.CharacterCodec;
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
 * /gridprofile - Display current character stats and code
 * Clean view without extra instructions
 */
public class GridProfileCommand extends AbstractPlayerCommand {

    private final GridMoveManager playerManager;
    private final RoleManager roleManager;

    public GridProfileCommand(GridMoveManager playerManager, RoleManager roleManager) {
        super("gridprofile", "View your character stats and code");
        this.playerManager = playerManager;
        this.roleManager = roleManager;
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {

        // GMs cannot use this
        if (roleManager.isGM(playerRef)) {
            playerRef.sendMessage(Message.raw("GMs cannot use character profiles!").color("#FF0000"));
            return;
        }

        GridPlayerState state = playerManager.getState(playerRef);

        // Convert maxMoves to integer
        int maxMovesInt = (int) Math.round(state.maxMoves);

        // Generate code
        String code = CharacterCodec.encode(state.stats, maxMovesInt);

        // Display profile
        playerRef.sendMessage(Message.raw(""));
        playerRef.sendMessage(Message.raw("=========================================").color("#FFD700"));
        playerRef.sendMessage(Message.raw("       YOUR CHARACTER PROFILE").color("#00FFFF"));
        playerRef.sendMessage(Message.raw("=========================================").color("#FFD700"));
        playerRef.sendMessage(Message.raw(""));
        playerRef.sendMessage(Message.raw("Character Code: " + code).color("#FFD700"));
        playerRef.sendMessage(Message.raw(""));
        playerRef.sendMessage(Message.raw("Ability Scores:").color("#00BFFF"));
        playerRef.sendMessage(Message.raw("  STR: " + state.stats.strength +
                " DEX: " + state.stats.dexterity +
                " CON: " + state.stats.constitution).color("#FFFFFF"));
        playerRef.sendMessage(Message.raw("  INT: " + state.stats.intelligence +
                " WIS: " + state.stats.wisdom +
                " CHA: " + state.stats.charisma).color("#FFFFFF"));
        playerRef.sendMessage(Message.raw(""));
        playerRef.sendMessage(Message.raw("Combat Stats:").color("#00BFFF"));
        playerRef.sendMessage(Message.raw("  HP: " + state.stats.currentHP + "/" + state.stats.maxHP).color("#FFFFFF"));
        playerRef.sendMessage(Message.raw("  Armor: " + state.stats.armor).color("#FFFFFF"));
        playerRef.sendMessage(Message.raw("  Initiative: " + (state.stats.initiative >= 0 ? "+" : "") + state.stats.initiative).color("#FFFFFF"));
        playerRef.sendMessage(Message.raw("  Max Moves: " + maxMovesInt).color("#FFFFFF"));
        playerRef.sendMessage(Message.raw("  Flying: " + (state.stats.isFlying ? "Yes" : "No")).color("#FFFFFF"));
        playerRef.sendMessage(Message.raw(""));
        playerRef.sendMessage(Message.raw("=========================================").color("#FFD700"));
        playerRef.sendMessage(Message.raw(""));

        System.out.println("[Griddify] [PROFILE] " + playerRef.getUsername() + " viewed their profile");
    }
}