package com.gridifymydungeon.plugin.dnd.commands;

import com.gridifymydungeon.plugin.dnd.EncounterManager;
import com.gridifymydungeon.plugin.dnd.MonsterState;
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
 * /flying - Toggle flying mode on/off (for flying spells and custom enemies)
 * UPDATED: Uses popup notifications instead of chat messages
 */
public class FlyingCommand extends AbstractPlayerCommand {
    private final GridMoveManager playerManager;
    private final RoleManager roleManager;
    private final EncounterManager encounterManager;

    public FlyingCommand(GridMoveManager playerManager, RoleManager roleManager, EncounterManager encounterManager) {
        super("flying", "Toggle flying mode");
        this.playerManager = playerManager;
        this.roleManager = roleManager;
        this.encounterManager = encounterManager;
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {

        if (roleManager.isGM(playerRef)) {
            MonsterState monster = encounterManager.getControlledMonster();
            if (monster == null) {
                // ERROR: No monster controlled
                Message primary = Message.raw("Control a monster first!").color("#FF0000");
                Message secondary = Message.raw("Use /control <number>").color("#FF6B6B");
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

            // Toggle flying for monster
            monster.stats.isFlying = !monster.stats.isFlying;
            monster.isFlying = monster.stats.isFlying;

            if (monster.stats.isFlying) {
                // SUCCESS: Flying ON
                Message primary = Message.raw(monster.getDisplayName() + " - Flying mode: ON").color("#00FFFF");
                Message secondary = Message.raw("Can move through air").color("#90EE90");
                ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Cyan", 1).toPacket();

                NotificationUtil.sendNotification(
                        playerRef.getPacketHandler(),
                        primary,
                        secondary,
                        icon,
                        NotificationStyle.Default
                );
            } else {
                // INFO: Flying OFF
                Message primary = Message.raw(monster.getDisplayName() + " - Flying mode: OFF").color("#FFFFFF");
                ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_White", 1).toPacket();

                NotificationUtil.sendNotification(
                        playerRef.getPacketHandler(),
                        primary,
                        null,
                        icon,
                        NotificationStyle.Default
                );
            }

            System.out.println("[Griddify] [FLYING] " + monster.getDisplayName() +
                    " flying: " + monster.stats.isFlying);

        } else {
            GridPlayerState state = playerManager.getState(playerRef);

            // Toggle flying for player
            state.stats.isFlying = !state.stats.isFlying;

            if (state.stats.isFlying) {
                // SUCCESS: Flying ON
                Message primary = Message.raw("Flying mode: ON").color("#00FFFF");
                Message secondary = Message.raw("You can now move through the air!").color("#90EE90");
                ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Cyan", 1).toPacket();

                NotificationUtil.sendNotification(
                        playerRef.getPacketHandler(),
                        primary,
                        secondary,
                        icon,
                        NotificationStyle.Default
                );
            } else {
                // INFO: Flying OFF
                Message primary = Message.raw("Flying mode: OFF").color("#FFFFFF");
                ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_White", 1).toPacket();

                NotificationUtil.sendNotification(
                        playerRef.getPacketHandler(),
                        primary,
                        null,
                        icon,
                        NotificationStyle.Default
                );
            }

            System.out.println("[Griddify] [FLYING] " + playerRef.getUsername() +
                    " flying: " + state.stats.isFlying);
        }
    }
}