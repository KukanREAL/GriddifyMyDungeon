package com.gridifymydungeon.plugin.gridmove.commands;

import com.gridifymydungeon.plugin.dnd.CombatManager;
import com.gridifymydungeon.plugin.dnd.commands.CombatCommand;
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
import java.util.List;

/**
 * /endturn - End turn, reset moves, advance to next.
 * Sends a popup to the current player AND broadcasts the updated turn order to everyone.
 */
public class EndTurnCommand extends AbstractPlayerCommand {

    private final GridMoveManager manager;
    private final CombatManager combatManager;
    private final CombatCommand combatCommand; // used for broadcasting turn order

    public EndTurnCommand(GridMoveManager manager, CombatManager combatManager, CombatCommand combatCommand) {
        super("endturn", "End your turn and reset moves");
        this.manager = manager;
        this.combatManager = combatManager;
        this.combatCommand = combatCommand;
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {

        GridPlayerState state = manager.getState(playerRef);

        if (combatManager.isCombatActive()) {
            state.resetMoves();
            CombatManager.CombatParticipant next = combatManager.nextTurn();

            // Popup for the person who just ended their turn
            String nextName = (next != null) ? next.name : "???";
            Message primary = Message.raw("Turn ended!").color("#90EE90");
            Message secondary = Message.raw("Now: " + nextName).color("#00BFFF");
            ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Green", 1).toPacket();
            NotificationUtil.sendNotification(playerRef.getPacketHandler(), primary, secondary, icon, NotificationStyle.Default);

            // Broadcast updated turn order to everyone
            List<CombatManager.CombatParticipant> order = combatManager.getTurnOrder();
            int currentIndex = combatManager.getCurrentTurnIndex();
            combatCommand.broadcastTurnOrder(order, currentIndex, "TURN CHANGE");

            System.out.println("[Griddify] [COMBAT] Turn advanced to: " + nextName);

        } else {
            if (state.hasMaxMovesSet()) {
                state.resetMoves();
                String movesText = formatMoves(state.remainingMoves) + "/" + formatMoves(state.maxMoves);
                Message primary = Message.raw("Moves reset").color("#90EE90");
                Message secondary = Message.raw(movesText).color("#00BFFF");
                ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Green", 1).toPacket();
                NotificationUtil.sendNotification(playerRef.getPacketHandler(), primary, secondary, icon, NotificationStyle.Default);
            } else {
                Message primary = Message.raw("Free movement mode").color("#00BFFF");
                Message secondary = Message.raw("No move limits!").color("#FFFFFF");
                ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Cyan", 1).toPacket();
                NotificationUtil.sendNotification(playerRef.getPacketHandler(), primary, secondary, icon, NotificationStyle.Default);
            }
        }
    }

    private String formatMoves(double moves) {
        if (moves == Math.floor(moves)) return String.valueOf((int) moves);
        return String.format("%.1f", moves);
    }
}