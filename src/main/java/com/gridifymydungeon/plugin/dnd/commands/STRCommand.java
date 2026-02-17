package com.gridifymydungeon.plugin.dnd.commands;

import com.gridifymydungeon.plugin.dnd.CharacterStats;
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
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;

import javax.annotation.Nonnull;

/**
 * /STR - Set Strength stat
 * UPDATED: Uses popup notifications instead of chat messages
 */
public class STRCommand extends AbstractPlayerCommand {
    private final GridMoveManager playerManager;
    private final RoleManager roleManager;
    private final EncounterManager encounterManager;
    private final RequiredArg<Integer> valueArg;

    public STRCommand(GridMoveManager playerManager, RoleManager roleManager, EncounterManager encounterManager) {
        super("STR", "Set Strength stat");
        this.playerManager = playerManager;
        this.roleManager = roleManager;
        this.encounterManager = encounterManager;
        this.valueArg = this.withRequiredArg("value", "Strength value (0-30)", ArgTypes.INTEGER);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        int value = valueArg.get(context);

        if (value < 0 || value > 30) {
            // ERROR: Invalid value
            Message primary = Message.raw("Value must be 0-30!").color("#FF0000");
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

            monster.stats.strength = value;

            // SUCCESS: Stat set for monster
            Message primary = Message.raw(monster.getDisplayName() + " - Strength").color("#FF6347");
            Message secondary = Message.raw(CharacterStats.formatStatWithModifier(value)).color("#FFFFFF");
            ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Red", 1).toPacket();

            NotificationUtil.sendNotification(
                    playerRef.getPacketHandler(),
                    primary,
                    secondary,
                    icon,
                    NotificationStyle.Default
            );
        } else {
            GridPlayerState state = playerManager.getState(playerRef);
            state.stats.strength = value;

            // SUCCESS: Stat set for player
            Message primary = Message.raw("Strength").color("#FF6347");
            Message secondary = Message.raw(CharacterStats.formatStatWithModifier(value)).color("#FFFFFF");
            ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Red", 1).toPacket();

            NotificationUtil.sendNotification(
                    playerRef.getPacketHandler(),
                    primary,
                    secondary,
                    icon,
                    NotificationStyle.Default
            );
        }
    }
}