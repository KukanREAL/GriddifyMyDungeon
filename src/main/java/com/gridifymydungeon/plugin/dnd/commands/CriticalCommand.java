package com.gridifymydungeon.plugin.dnd.commands;

import com.gridifymydungeon.plugin.dnd.CombatSettings;
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
 * /Critical - Toggle critical rolls on/off (GM only)
 * UPDATED: Uses popup notifications instead of chat messages
 */
public class CriticalCommand extends AbstractPlayerCommand {
    private final RoleManager roleManager;
    private final CombatSettings combatSettings;

    public CriticalCommand(RoleManager roleManager, CombatSettings combatSettings) {
        super("Critical", "Toggle critical success/failure (GM only)");
        this.roleManager = roleManager;
        this.combatSettings = combatSettings;
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {

        // Check if player is GM
        if (!roleManager.isGM(playerRef)) {
            // ERROR: Not GM
            Message primary = Message.raw("Only the GM can use this command!").color("#FF0000");
            ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Red", 1).toPacket();

            NotificationUtil.sendNotification(
                    playerRef.getPacketHandler(),
                    primary,
                    null,
                    icon,
                    NotificationStyle.Default
            );
            return;
        }

        // Toggle critical rolls
        combatSettings.toggleCriticalRolls();

        boolean enabled = combatSettings.isCriticalRollsEnabled();

        if (enabled) {
            // SUCCESS: Critical rolls ENABLED
            Message primary = Message.raw("Critical rolls: ENABLED").color("#FFD700");
            Message secondary = Message.raw("1 = Crit Fail | Max = Crit Success").color("#FFFFFF");
            ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Yellow", 1).toPacket();

            NotificationUtil.sendNotification(
                    playerRef.getPacketHandler(),
                    primary,
                    secondary,
                    icon,
                    NotificationStyle.Default
            );
        } else {
            // INFO: Critical rolls DISABLED
            Message primary = Message.raw("Critical rolls: DISABLED").color("#808080");
            ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_White", 1).toPacket();

            NotificationUtil.sendNotification(
                    playerRef.getPacketHandler(),
                    primary,
                    null,
                    icon,
                    NotificationStyle.Default
            );
        }

        System.out.println("[Griddify] [CRITICAL] GM toggled critical rolls: " + enabled);
    }
}