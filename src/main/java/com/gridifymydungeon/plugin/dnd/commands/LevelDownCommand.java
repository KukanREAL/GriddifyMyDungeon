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
 * /LevelDown - GM command to decrease all players' level by 1
 * FIXED: Iterate over getAllStates() and get playerRef from GridMoveManager
 */
public class LevelDownCommand extends AbstractPlayerCommand {
    private final GridMoveManager playerManager;
    private final RoleManager roleManager;

    public LevelDownCommand(GridMoveManager playerManager, RoleManager roleManager) {
        super("LevelDown", "Decrease all players' level by 1 (GM only)");
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

        // Level down all players - FIXED: getAllStates() returns Iterable, iterate properly
        int playersLeveled = 0;

        for (GridPlayerState state : playerManager.getAllStates()) {
            int oldLevel = state.stats.getLevel();
            if (oldLevel > 1) {
                state.stats.setLevel(oldLevel - 1);
                playersLeveled++;

                // Notify the player - FIXED: Get PlayerRef from GridMoveManager by state
                PlayerRef targetPlayer = playerManager.getPlayerRefByState(state);
                if (targetPlayer != null) {
                    Message primary = Message.raw("LEVEL DOWN").color("#FF6B6B");
                    Message secondary = Message.raw("Level " + oldLevel + " → " + state.stats.getLevel()).color("#FFFFFF");
                    ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Red", 1).toPacket();

                    NotificationUtil.sendNotification(
                            targetPlayer.getPacketHandler(),
                            primary,
                            secondary,
                            icon,
                            NotificationStyle.Default
                    );

                    // If they go below level 3, remove subclass
                    if (state.stats.getLevel() < 3 && state.stats.getSubclassType() != null) {
                        state.stats.setSubclassType(null);
                        targetPlayer.sendMessage(Message.raw("[Griddify] Subclass removed (below level 3)").color("#FF0000"));
                    }
                }
            }
        }

        // Notify GM
        Message primary = Message.raw("Level Down Complete").color("#FF6B6B");
        Message secondary = Message.raw(playersLeveled + " players leveled down").color("#FFFFFF");
        ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Red", 1).toPacket();

        NotificationUtil.sendNotification(
                executingPlayer.getPacketHandler(),
                primary,
                secondary,
                icon,
                NotificationStyle.Default
        );

        System.out.println("[Griddify] [LEVELDOWN] GM leveled down " + playersLeveled + " players");
    }
}