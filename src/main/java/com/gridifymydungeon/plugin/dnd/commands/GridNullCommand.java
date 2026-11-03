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
 * /GridNull - Revoke your current role (GM or Player)
 * Returns you to unassigned state
 * UPDATED: Uses popup notifications instead of chat messages
 */
public class GridNullCommand extends AbstractPlayerCommand {

    private final RoleManager roleManager;

    public GridNullCommand(RoleManager roleManager) {
        super("GridNull", "Revoke your current role");
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
        // Check if player has a role
        if (!roleManager.hasRole(playerRef)) {
            // WARNING: No role to revoke
            Message primary = Message.raw("You don't have a role!").color("#FFA500");
            Message secondary = Message.raw("Use /GridGM or /GridPlayer to get a role").color("#FFFFFF");
            ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Yellow", 1).toPacket();

            NotificationUtil.sendNotification(
                    playerRef.getPacketHandler(),
                    primary,
                    secondary,
                    icon,
                    NotificationStyle.Default
            );
            return;
        }

        // Determine current role
        String currentRole;
        if (roleManager.isGM(playerRef)) {
            currentRole = "Game Master";
        } else {
            int playerNum = roleManager.getPlayerNumber(playerRef);
            currentRole = "Player " + playerNum;
        }

        // Revoke role
        boolean success = roleManager.revokeRole(playerRef);

        if (success) {
            // SUCCESS: Role revoked
            Message primary = Message.raw("Role revoked: " + currentRole).color("#FF6347");
            Message secondary = Message.raw("You are now unassigned").color("#FFFFFF");
            ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Red", 1).toPacket();

            NotificationUtil.sendNotification(
                    playerRef.getPacketHandler(),
                    primary,
                    secondary,
                    icon,
                    NotificationStyle.Default
            );

            System.out.println("[Griddify] [NULL] " + playerRef.getUsername() + " revoked role: " + currentRole);
        } else {
            // ERROR: Failed to revoke
            Message primary = Message.raw("Failed to revoke role!").color("#FF0000");
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