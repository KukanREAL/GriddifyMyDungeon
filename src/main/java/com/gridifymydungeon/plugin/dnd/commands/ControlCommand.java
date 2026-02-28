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
 * /control <number> - GM controls a monster.
 * /control 0       - Stop controlling.
 *
 * FIX #8: /Grid (gmMapOverlayActive=true) must NEVER be removed when the GM
 * switches monsters via /control. Only BFS overlays (monster/player range tiles)
 * are cleaned up. The static /grid map stays until the GM types /grid again.
 */
public class ControlCommand extends AbstractPlayerCommand {

    private final RoleManager roleManager;
    private final EncounterManager encounterManager;
    private final GridMoveManager gridMoveManager;
    private final RequiredArg<Integer> monsterNumberArg;

    public ControlCommand(RoleManager roleManager, EncounterManager encounterManager,
                          GridMoveManager gridMoveManager) {
        super("control", "Control a monster by number (GM only)");
        this.roleManager = roleManager;
        this.encounterManager = encounterManager;
        this.gridMoveManager = gridMoveManager;
        this.monsterNumberArg = this.withRequiredArg("number", "Monster number to control (0 to stop)", ArgTypes.INTEGER);
    }

    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        if (!roleManager.isGM(playerRef)) {
            Message primary = Message.raw("Only the GM can use this command!").color("#FF0000");
            ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Red", 1).toPacket();
            NotificationUtil.sendNotification(playerRef.getPacketHandler(), primary, null, icon, NotificationStyle.Default);
            return;
        }

        int monsterNumber = monsterNumberArg.get(context);

        // Handle /control 0 - stop controlling
        if (monsterNumber == 0) {
            MonsterState currentMonster = encounterManager.getControlledMonster();

            if (currentMonster == null) {
                Message primary = Message.raw("You are not controlling any monster!").color("#FFA500");
                ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Yellow", 1).toPacket();
                NotificationUtil.sendNotification(playerRef.getPacketHandler(), primary, null, icon, NotificationStyle.Default);
                return;
            }

            world.execute(() -> {
                String monsterName = currentMonster.getDisplayName();

                // FIX #8: Only remove BFS overlay, preserve /grid map
                cleanUpGMBFSOverlay(playerRef, world);

                MonsterEntityController.updateHologramText(world, currentMonster, String.valueOf(currentMonster.monsterNumber));
                encounterManager.stopControl();

                Message primary = Message.raw("Stopped controlling " + monsterName).color("#00BFFF");
                ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Cyan", 1).toPacket();
                NotificationUtil.sendNotification(playerRef.getPacketHandler(), primary, null, icon, NotificationStyle.Default);

                System.out.println("[Griddify] [CONTROL] GM stopped controlling " + monsterName);
            });
            return;
        }

        // Handle /control <number> - start controlling
        MonsterState monster = encounterManager.getMonsterByNumber(monsterNumber);

        if (monster == null) {
            Message primary = Message.raw("Monster #" + monsterNumber + " not found!").color("#FF0000");
            ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Red", 1).toPacket();
            NotificationUtil.sendNotification(playerRef.getPacketHandler(), primary, null, icon, NotificationStyle.Default);
            return;
        }

        world.execute(() -> {
            MonsterState previousMonster = encounterManager.getControlledMonster();
            if (previousMonster != null) {
                MonsterEntityController.updateHologramText(world, previousMonster, String.valueOf(previousMonster.monsterNumber));
            }

            // FIX #8: Only remove BFS overlay, preserve /grid map
            cleanUpGMBFSOverlay(playerRef, world);

            boolean success = encounterManager.setControlled(monsterNumber);

            if (success) {
                MonsterEntityController.updateHologramText(world, monster, "Moving " + monster.monsterNumber);

                String movesText = (int) monster.remainingMoves + "/" + (int) monster.maxMoves;

                Message primary = Message.raw("Now controlling: " + monster.getDisplayName()).color("#00FFFF");
                Message secondary = Message.raw("Moves: " + movesText).color("#FFFFFF");
                ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Cyan", 1).toPacket();
                NotificationUtil.sendNotification(playerRef.getPacketHandler(), primary, secondary, icon, NotificationStyle.Default);

                System.out.println("[Griddify] [CONTROL] GM controlling " + monster.getDisplayName() +
                        " (moves: " + monster.remainingMoves + "/" + monster.maxMoves + ")");
            } else {
                Message primary = Message.raw("Failed to control monster #" + monsterNumber).color("#FF0000");
                ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Red", 1).toPacket();
                NotificationUtil.sendNotification(playerRef.getPacketHandler(), primary, null, icon, NotificationStyle.Default);
            }
        });
    }

    /**
     * FIX #8: Remove only BFS overlays (monster/player range tiles).
     * If the GM has /grid active (gmMapOverlayActive == true), that static map
     * is NEVER touched — it persists until the GM types /grid again.
     */
    private void cleanUpGMBFSOverlay(PlayerRef playerRef, World world) {
        GridPlayerState gmState = gridMoveManager.getState(playerRef);
        if (gmState == null) return;

        // FIX #8: Static /grid map — never remove it here
        if (gmState.gmMapOverlayActive) {
            System.out.println("[Control] /grid map preserved (gmMapOverlayActive=true)");
            return;
        }

        // Only remove BFS range overlay
        if (gmState.gridOverlayEnabled) {
            Store<EntityStore> entityStore = world.getEntityStore().getStore();
            for (com.hypixel.hytale.component.Ref<EntityStore> gridEntity : gmState.gridOverlay) {
                if (gridEntity != null && gridEntity.isValid()) {
                    try {
                        entityStore.removeEntity(gridEntity, com.hypixel.hytale.component.RemoveReason.REMOVE);
                    } catch (Exception ignored) {}
                }
            }
            gmState.gridOverlay.clear();
            gmState.gridOverlayEnabled = false;
            System.out.println("[Control] Cleaned up GM BFS overlay");
        }
    }
}