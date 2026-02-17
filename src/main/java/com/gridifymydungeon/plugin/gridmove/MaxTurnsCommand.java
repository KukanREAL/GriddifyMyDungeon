package com.gridifymydungeon.plugin.gridmove;

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
 * /maxmoves <number> - Sets maximum moves for player
 * UPDATED: Uses popup notifications instead of chat messages
 */
public class MaxTurnsCommand extends AbstractPlayerCommand {

    private final GridMoveManager manager;
    private final RequiredArg<Double> maxMovesArg;

    public MaxTurnsCommand(GridMoveManager manager) {
        super("maxmoves", "Set maximum moves (can be changed anytime)");
        this.manager = manager;
        this.maxMovesArg = this.withRequiredArg("moves", "Maximum number of moves", ArgTypes.DOUBLE);
    }

    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        GridPlayerState state = manager.getState(playerRef);

        // Check if GridMove is active
        if (state.npcEntity == null || !state.npcEntity.isValid()) {
            // ERROR: GridMove not active
            Message primary = Message.raw("You must activate /gridmove first!").color("#FF0000");
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

        double maxMoves = maxMovesArg.get(context);

        // Validate input
        if (maxMoves <= 0) {
            // ERROR: Invalid value
            Message primary = Message.raw("Max moves must be greater than 0!").color("#FF0000");
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

        // Set max moves
        state.setMaxMoves(maxMoves);

        String maxMovesText = formatMoves(maxMoves);
        String remainingText = formatMoves(state.remainingMoves);

        // SUCCESS: Max moves set
        Message primary = Message.raw("Max moves set to " + maxMovesText).color("#90EE90");
        Message secondary = Message.raw("Remaining: " + remainingText + "/" + maxMovesText).color("#00BFFF");
        ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Green", 1).toPacket();

        NotificationUtil.sendNotification(
                playerRef.getPacketHandler(),
                primary,
                secondary,
                icon,
                NotificationStyle.Default
        );

        System.out.println("[MaxMoves] [INFO] Player " + playerRef.getUsername() + " set max moves to " + maxMoves);
    }

    private String formatMoves(double moves) {
        if (moves == Math.floor(moves)) {
            return String.valueOf((int) moves);
        } else {
            return String.format("%.1f", moves);
        }
    }
}