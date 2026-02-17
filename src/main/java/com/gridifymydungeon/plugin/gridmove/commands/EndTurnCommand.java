package com.gridifymydungeon.plugin.gridmove.commands;

import com.gridifymydungeon.plugin.dnd.CombatManager;
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
 * /endturn - End turn and reset moves
 * UPDATED: Uses popup notifications instead of chat messages
 */
public class EndTurnCommand extends AbstractPlayerCommand {

    private final GridMoveManager manager;
    private final CombatManager combatManager;

    public EndTurnCommand(GridMoveManager manager, CombatManager combatManager) {
        super("endturn", "End your turn and reset moves");
        this.manager = manager;
        this.combatManager = combatManager;
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {

        GridPlayerState state = manager.getState(playerRef);

        if (combatManager.isCombatActive()) {
            // IN COMBAT MODE - Reset moves and advance turn
            state.resetMoves();

            // Advance to next turn
            CombatManager.CombatParticipant next = combatManager.nextTurn();

            if (next != null) {
                // SUCCESS: Turn ended, next participant
                Message primary = Message.raw("Turn ended. Moves reset.").color("#90EE90");
                Message secondary = Message.raw("Now turn: " + next.name).color("#00BFFF");
                ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Green", 1).toPacket();

                NotificationUtil.sendNotification(
                        playerRef.getPacketHandler(),
                        primary,
                        secondary,
                        icon,
                        NotificationStyle.Default
                );

                System.out.println("[Griddify] [COMBAT] Turn advanced to: " + next.name);
            } else {
                // SUCCESS: Turn ended
                Message primary = Message.raw("Turn ended. Moves reset.").color("#90EE90");
                ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Green", 1).toPacket();

                NotificationUtil.sendNotification(
                        playerRef.getPacketHandler(),
                        primary,
                        null,
                        icon,
                        NotificationStyle.Default
                );
            }

        } else {
            // OUTSIDE COMBAT MODE - Just reset moves

            if (state.hasMaxMovesSet()) {
                state.resetMoves();

                String movesText = formatMoves(state.remainingMoves) + "/" + formatMoves(state.maxMoves);

                // SUCCESS: Moves reset
                Message primary = Message.raw("Moves reset").color("#90EE90");
                Message secondary = Message.raw(movesText).color("#00BFFF");
                ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Green", 1).toPacket();

                NotificationUtil.sendNotification(
                        playerRef.getPacketHandler(),
                        primary,
                        secondary,
                        icon,
                        NotificationStyle.Default
                );

                System.out.println("[Griddify] [INFO] " + playerRef.getUsername() +
                        " reset moves: " + state.remainingMoves + "/" + state.maxMoves);
            } else {
                // INFO: Free movement mode
                Message primary = Message.raw("Free movement mode").color("#00BFFF");
                Message secondary = Message.raw("No move limits!").color("#FFFFFF");
                ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Cyan", 1).toPacket();

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

    private String formatMoves(double moves) {
        if (moves == Math.floor(moves)) {
            return String.valueOf((int) moves);
        }
        return String.format("%.1f", moves);
    }
}