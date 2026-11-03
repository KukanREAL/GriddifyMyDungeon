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
 * /gridregister - Generate a character code from current stats
 * Format: AABBCC-DEF-GH (11 characters + 2 dashes)
 */
public class GridRegisterCommand extends AbstractPlayerCommand {

    private final GridMoveManager playerManager;
    private final RoleManager roleManager;

    public GridRegisterCommand(GridMoveManager playerManager, RoleManager roleManager) {
        super("gridregister", "Generate your character code");
        this.playerManager = playerManager;
        this.roleManager = roleManager;
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {

        // GMs cannot use this
        if (roleManager.isGM(playerRef)) {
            playerRef.sendMessage(Message.raw("GMs cannot register character codes!").color("#FF0000"));
            return;
        }

        GridPlayerState state = playerManager.getState(playerRef);

        // Convert maxMoves to integer (whole numbers only)
        int maxMovesInt = (int) Math.round(state.maxMoves);

        // Generate code from current stats (without spell data for now)
        String code = CharacterCodec.encode(state.stats, maxMovesInt);

        // Display to player
        playerRef.sendMessage(Message.raw(""));
        playerRef.sendMessage(Message.raw("=========================================").color("#FFD700"));
        playerRef.sendMessage(Message.raw("       YOUR CHARACTER CODE").color("#00FFFF"));
        playerRef.sendMessage(Message.raw("=========================================").color("#FFD700"));
        playerRef.sendMessage(Message.raw(""));
        playerRef.sendMessage(Message.raw("  Code: " + code).color("#FFD700"));
        playerRef.sendMessage(Message.raw(""));
        playerRef.sendMessage(Message.raw("Copy this code and save it!").color("#FFFFFF"));
        playerRef.sendMessage(Message.raw("Use /gridlogin " + code + " to restore your stats").color("#90EE90"));
        playerRef.sendMessage(Message.raw(""));
        playerRef.sendMessage(Message.raw("Current stats encoded:").color("#00BFFF"));
        playerRef.sendMessage(Message.raw("  STR: " + state.stats.strength +
                " DEX: " + state.stats.dexterity +
                " CON: " + state.stats.constitution).color("#FFFFFF"));
        playerRef.sendMessage(Message.raw("  INT: " + state.stats.intelligence +
                " WIS: " + state.stats.wisdom +
                " CHA: " + state.stats.charisma).color("#FFFFFF"));
        playerRef.sendMessage(Message.raw("  HP: " + state.stats.maxHP +
                " Armor: " + state.stats.armor +
                " Initiative: " + (state.stats.initiative >= 0 ? "+" : "") + state.stats.initiative).color("#FFFFFF"));
        playerRef.sendMessage(Message.raw("  Max Moves: " + maxMovesInt +
                " Flying: " + (state.stats.isFlying ? "Yes" : "No")).color("#FFFFFF"));
        playerRef.sendMessage(Message.raw(""));
        playerRef.sendMessage(Message.raw("Format: AABBCC-DEF-GH (11 characters)").color("#808080"));
        playerRef.sendMessage(Message.raw("=========================================").color("#FFD700"));

        System.out.println("[Griddify] [REGISTER] " + playerRef.getUsername() +
                " generated code: " + code);
    }
}