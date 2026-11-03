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
 * /GridRestart - Reset all player roles (requires OP/admin permission)
 * Clears GM and all player assignments
 * Allows fresh role assignment for entire server
 * UPDATED: Uses popup notifications instead of chat messages
 */
public class GridRestartCommand extends AbstractPlayerCommand {

    private final RoleManager roleManager;

    public GridRestartCommand(RoleManager roleManager) {
        super("GridRestart", "Reset all player roles (OP only)");
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
        // TODO: Add proper permission check when available
        // For now, only allow if player is current GM (temporary solution)
        if (!roleManager.isGM(playerRef)) {
            // ERROR: Not authorized
            Message primary = Message.raw("Only the GM can restart roles!").color("#FF0000");
            Message secondary = Message.raw("Contact server admin").color("#FF6B6B");
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

        // Reset all roles
        roleManager.resetAllRoles();

        // SUCCESS: All roles reset
        Message primary = Message.raw("All roles have been reset!").color("#FFD700");
        Message secondary = Message.raw("Everyone can now choose new roles").color("#FFFFFF");
        ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Yellow", 1).toPacket();

        NotificationUtil.sendNotification(
                playerRef.getPacketHandler(),
                primary,
                secondary,
                icon,
                NotificationStyle.Default
        );

        System.out.println("[Griddify] [RESTART] " + playerRef.getUsername() + " reset all roles");
    }
}