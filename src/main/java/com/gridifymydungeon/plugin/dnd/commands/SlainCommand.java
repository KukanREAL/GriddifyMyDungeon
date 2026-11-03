package com.gridifymydungeon.plugin.dnd.commands;

import com.gridifymydungeon.plugin.dnd.EncounterManager;
import com.gridifymydungeon.plugin.dnd.commands.MonsterEntityController;
import com.gridifymydungeon.plugin.dnd.MonsterState;
import com.gridifymydungeon.plugin.dnd.RoleManager;
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
 * /slain <number> - Remove a monster from the encounter (GM only)
 * UPDATED: Uses popup notifications instead of chat messages
 */
public class SlainCommand extends AbstractPlayerCommand {

    private final EncounterManager encounterManager;
    private final RoleManager roleManager;
    private final RequiredArg<Integer> monsterNumberArg;

    public SlainCommand(EncounterManager encounterManager, RoleManager roleManager) {
        super("slain", "Remove a slain monster (GM only)");
        this.encounterManager = encounterManager;
        this.roleManager = roleManager;
        this.monsterNumberArg = this.withRequiredArg("number", "Monster number to remove", ArgTypes.INTEGER);
    }

    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
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

        int monsterNumber = monsterNumberArg.get(context);

        // Check if monster exists
        MonsterState monster = encounterManager.getMonster(monsterNumber);
        if (monster == null) {
            // ERROR: Monster not found
            Message primary = Message.raw("Monster #" + monsterNumber + " not found!").color("#FF0000");
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

        String monsterName = monster.getDisplayName();

        // Despawn monster entity and hologram
        world.execute(() -> {
            // Use MonsterEntityController to properly despawn
            MonsterEntityController.despawnMonster(world, monster);

            // Remove from encounter manager
            encounterManager.removeMonster(monsterNumber);

            // If this was the controlled monster, release control
            if (encounterManager.getControlledMonster() == monster) {
                encounterManager.releaseControl();

                // INFO: Control released (secondary notification)
                Message controlPrimary = Message.raw("Control released").color("#FFA500");
                Message controlSecondary = Message.raw("Monster was slain").color("#FFFFFF");
                ItemWithAllMetadata controlIcon = new ItemStack("Ingredient_Crystal_Yellow", 1).toPacket();

                NotificationUtil.sendNotification(
                        playerRef.getPacketHandler(),
                        controlPrimary,
                        controlSecondary,
                        controlIcon,
                        NotificationStyle.Default
                );
            }

            // SUCCESS: Monster slain
            Message primary = Message.raw(monsterName + " has been slain!").color("#8B0000");
            Message secondary = Message.raw("Monster #" + monsterNumber + " removed").color("#FFFFFF");
            ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Red", 1).toPacket();

            NotificationUtil.sendNotification(
                    playerRef.getPacketHandler(),
                    primary,
                    secondary,
                    icon,
                    NotificationStyle.Default
            );

            System.out.println("[Griddify] [SLAIN] " + monsterName + " removed by GM");
        });
    }
}