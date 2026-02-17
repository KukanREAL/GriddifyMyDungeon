package com.gridifymydungeon.plugin.dnd.commands;

import com.gridifymydungeon.plugin.dnd.RoleManager;
import com.gridifymydungeon.plugin.gridmove.GridMoveManager;
import com.gridifymydungeon.plugin.gridmove.GridPlayerState;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.ItemWithAllMetadata;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;

import javax.annotation.Nonnull;

/**
 * /LevelUp - GM command to increase all players' level by 1
 * FIXED: Iterate over getAllStates() and get playerRef from GridMoveManager
 */
public class LevelUpCommand extends AbstractPlayerCommand {
    private final GridMoveManager playerManager;
    private final RoleManager roleManager;

    public LevelUpCommand(GridMoveManager playerManager, RoleManager roleManager) {
        super("LevelUp", "Increase all players' level by 1 (GM only)");
        this.playerManager = playerManager;
        this.roleManager = roleManager;
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef executingPlayer, @Nonnull World world) {

        // Check if executing player is GM
        if (!roleManager.isGM(executingPlayer)) {
            Message primary = Message.raw("Only the GM can use this command!").color("#FF0000");
            ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Red", 1).toPacket();

            NotificationUtil.sendNotification(
                    executingPlayer.getPacketHandler(),
                    primary,
                    null,
                    icon,
                    NotificationStyle.Default
            );
            return;
        }

        // Level up all players - FIXED: getAllStates() returns Iterable, iterate properly
        int playersLeveled = 0;

        for (GridPlayerState state : playerManager.getAllStates()) {
            int oldLevel = state.stats.getLevel();
            if (oldLevel < 20) {
                state.stats.setLevel(oldLevel + 1);
                playersLeveled++;

                // Notify the player - FIXED: Get PlayerRef from GridMoveManager by state
                PlayerRef targetPlayer = playerManager.getPlayerRefByState(state);
                if (targetPlayer != null) {
                    Message primary = Message.raw("LEVEL UP!").color("#FFD700");
                    Message secondary = Message.raw("Level " + oldLevel + " → " + state.stats.getLevel()).color("#FFFFFF");
                    ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Yellow", 1).toPacket();

                    NotificationUtil.sendNotification(
                            targetPlayer.getPacketHandler(),
                            primary,
                            secondary,
                            icon,
                            NotificationStyle.Default
                    );

                    // Check if they can choose subclass now (level 3)
                    if (state.stats.getLevel() == 3 && state.stats.getSubclassType() == null) {
                        targetPlayer.sendMessage(Message.raw(""));
                        targetPlayer.sendMessage(Message.raw("===========================================").color("#FFD700"));
                        targetPlayer.sendMessage(Message.raw("  You can now choose a SUBCLASS!").color("#FFD700"));
                        targetPlayer.sendMessage(Message.raw("  Use: /GridSubclass <subclass name>").color("#FFFFFF"));
                        targetPlayer.sendMessage(Message.raw("===========================================").color("#FFD700"));
                        targetPlayer.sendMessage(Message.raw(""));
                    }
                }
            }
        }

        // Notify GM
        Message primary = Message.raw("Level Up Complete").color("#FFD700");
        Message secondary = Message.raw(playersLeveled + " players leveled up").color("#FFFFFF");
        ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Yellow", 1).toPacket();

        NotificationUtil.sendNotification(
                executingPlayer.getPacketHandler(),
                primary,
                secondary,
                icon,
                NotificationStyle.Default
        );

        System.out.println("[Griddify] [LEVELUP] GM leveled up " + playersLeveled + " players");
    }
}