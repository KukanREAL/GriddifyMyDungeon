package com.gridifymydungeon.plugin.dnd.commands;

import com.gridifymydungeon.plugin.dnd.RoleManager;
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
 * /GridPlayer command - Assigns player as numbered Player
 * UPDATED: Uses popup notifications instead of chat messages
 */
public class GridPlayerCommand extends AbstractPlayerCommand {

    private final RoleManager roleManager;

    public GridPlayerCommand(RoleManager roleManager) {
        super("GridPlayer", "Become a numbered Player");
        this.roleManager = roleManager;
    }

    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        // Check if already has a role
        if (roleManager.isGM(playerRef)) {
            // ERROR: Already GM
            Message primary = Message.raw("You are the GM!").color("#FF0000");
            Message secondary = Message.raw("Cannot become a Player").color("#FF6B6B");
            ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Red", 1).toPacket();

            NotificationUtil.sendNotification(
                    playerRef.getPacketHandler(),
                    primary,
                    secondary,
                    icon,
                    NotificationStyle.Default
            );
            return;
        }

        // Check if already a player
        int existingNumber = roleManager.getPlayerNumber(playerRef);
        if (existingNumber > 0) {
            // WARNING: Already a player
            Message primary = Message.raw("You are already Player " + existingNumber + "!").color("#FFA500");
            ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Yellow", 1).toPacket();

            NotificationUtil.sendNotification(
                    playerRef.getPacketHandler(),
                    primary,
                    null,
                    icon,
                    NotificationStyle.Default
            );
            return;
        }

        // Assign as player
        int playerNumber = roleManager.assignPlayer(playerRef);

        if (playerNumber > 0) {
            // SUCCESS: Assigned as player
            Message primary = Message.raw("You are now Player " + playerNumber + "!").color("#00FFFF");
            Message secondary = Message.raw("Use /gridmove to start playing").color("#FFFFFF");
            ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Cyan", 1).toPacket();

            NotificationUtil.sendNotification(
                    playerRef.getPacketHandler(),
                    primary,
                    secondary,
                    icon,
                    NotificationStyle.Default
            );

            System.out.println("[Griddify] [INFO] " + playerRef.getUsername() + " is now Player " + playerNumber);
        } else {
            // ERROR: Failed to assign
            Message primary = Message.raw("Failed to assign Player role!").color("#FF0000");
            ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Red", 1).toPacket();

            NotificationUtil.sendNotification(
                    playerRef.getPacketHandler(),
                    primary,
                    null,
                    icon,
                    NotificationStyle.Default
            );
        }
    }
}