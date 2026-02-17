package com.gridifymydungeon.plugin.gridmove.commands;

import com.gridifymydungeon.plugin.dnd.EncounterManager;
import com.gridifymydungeon.plugin.dnd.MonsterState;
import com.gridifymydungeon.plugin.dnd.RoleManager;
import com.gridifymydungeon.plugin.gridmove.CollisionDetector;
import com.gridifymydungeon.plugin.gridmove.GridMoveManager;
import com.gridifymydungeon.plugin.gridmove.GridOverlayManager;
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
 * /gridon - Show grid overlay
 * UPDATED: Uses popup notifications instead of chat messages
 */
public class GridOnCommand extends AbstractPlayerCommand {

    private final GridMoveManager gridMoveManager;
    private final CollisionDetector collisionDetector;
    private final EncounterManager encounterManager;
    private final RoleManager roleManager;

    public GridOnCommand(GridMoveManager gridMoveManager, CollisionDetector collisionDetector,
                         EncounterManager encounterManager, RoleManager roleManager) {
        super("gridon", "Show grid overlay");
        this.gridMoveManager = gridMoveManager;
        this.collisionDetector = collisionDetector;
        this.encounterManager = encounterManager;
        this.roleManager = roleManager;
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

            GridPlayerState gmState = gridMoveManager.getState(playerRef);
            gmState.currentGridX = monster.currentGridX;
            gmState.currentGridZ = monster.currentGridZ;
            gmState.npcY = monster.spawnY;
            gmState.remainingMoves = monster.remainingMoves;
            gmState.maxMoves = monster.maxMoves;

            if (gmState.gridOverlayEnabled) {
                // WARNING: Already active
                Message primary = Message.raw("Grid overlay already active").color("#FFA500");
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

            world.execute(() -> {
                // Spawn grid overlay
                GridOverlayManager.spawnGridOverlay(world, gmState, collisionDetector, null);

                // SUCCESS: Grid enabled for monster
                Message primary = Message.raw("Grid overlay enabled!").color("#90EE90");
                Message secondary = Message.raw("Showing " + monster.getDisplayName() + "'s range").color("#FFFFFF");
                ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Green", 1).toPacket();

                NotificationUtil.sendNotification(
                        playerRef.getPacketHandler(),
                        primary,
                        secondary,
                        icon,
                        NotificationStyle.Default
                );
            });

            System.out.println("[Griddify] [GRIDON] GM enabled overlay for " + monster.getDisplayName());
            return;
        }

        GridPlayerState state = gridMoveManager.getState(playerRef);

        if (state.npcEntity == null || !state.npcEntity.isValid()) {
            // ERROR: GridMove not active
            Message primary = Message.raw("Activate /gridmove first!").color("#FF0000");
            Message secondary = Message.raw("You need to spawn your character").color("#FF6B6B");
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

        if (state.gridOverlayEnabled) {
            // WARNING: Already active
            Message primary = Message.raw("Grid overlay already active").color("#FFA500");
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

        world.execute(() -> {
            // Spawn grid overlay
            GridOverlayManager.spawnGridOverlay(world, state, collisionDetector, playerRef.getUuid());

            String movesText = formatMoves(state.remainingMoves) + "/" + formatMoves(state.maxMoves);

            // SUCCESS: Grid enabled
            Message primary = Message.raw("Grid overlay enabled!").color("#90EE90");
            Message secondary = Message.raw("Moves: " + movesText).color("#00BFFF");
            ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Green", 1).toPacket();

            NotificationUtil.sendNotification(
                    playerRef.getPacketHandler(),
                    primary,
                    secondary,
                    icon,
                    NotificationStyle.Default
            );
        });

        System.out.println("[Griddify] [GRIDON] " + playerRef.getUsername() + " enabled grid overlay");
    }

    private String formatMoves(double moves) {
        if (moves == Math.floor(moves)) {
            return String.valueOf((int) moves);
        }
        return String.format("%.1f", moves);
    }
}