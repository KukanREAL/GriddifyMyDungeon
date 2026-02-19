package com.gridifymydungeon.plugin.gridmove.commands;

import com.gridifymydungeon.plugin.dnd.CombatManager;
import com.gridifymydungeon.plugin.dnd.commands.CombatCommand;
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
import java.util.List;

/**
 * /endturn - End turn, reset moves, advance to next.
 *
 * On turn change (combat active):
 *   - Removes all /gridon BFS overlays from players whose turn just ended.
 *     (Static /grid overlays are preserved — gmMapOverlayActive flag distinguishes them.)
 *   - The new current-turn player's overlay is re-spawned automatically so they
 *     immediately see their movement range.
 */
public class EndTurnCommand extends AbstractPlayerCommand {

    private final GridMoveManager manager;
    private final CombatManager combatManager;
    private final CombatCommand combatCommand;
    private final CollisionDetector collisionDetector;

    public EndTurnCommand(GridMoveManager manager, CombatManager combatManager,
                          CombatCommand combatCommand, CollisionDetector collisionDetector) {
        super("endturn", "End your turn and reset moves");
        this.manager = manager;
        this.combatManager = combatManager;
        this.combatCommand = combatCommand;
        this.collisionDetector = collisionDetector;
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

            // --- Overlay management ---
            // Remove BFS overlays from ALL players (but not static /grid maps).
            // Then re-spawn for the new current-turn player only.
            world.execute(() -> {
                for (GridPlayerState ps : manager.getAllStates()) {
                    if (ps.gridOverlayEnabled && !ps.gmMapOverlayActive) {
                        // BFS overlay — remove it (it belongs to the previous turn player)
                        GridOverlayManager.removeGridOverlay(world, ps);
                    }
                }

                // Spawn fresh overlay for the new turn's player (if they are a player, not a monster)
                if (next != null && next.isPlayer) {
                    // Find the matching GridPlayerState by UUID (it's stored in playerRef on each state)
                    for (GridPlayerState ps : manager.getAllStates()) {
                        if (ps.playerRef != null
                                && next.playerUUID != null
                                && ps.playerRef.getUuid().equals(next.playerUUID)
                                && ps.npcEntity != null && ps.npcEntity.isValid()) {
                            GridOverlayManager.spawnPlayerGridOverlay(world, ps, collisionDetector,
                                    ps.playerRef.getUuid());
                            ps.playerRef.sendMessage(
                                    Message.raw("[Griddify] Your turn! Grid overlay active  —  "
                                                    + formatMoves(ps.remainingMoves) + "/" + formatMoves(ps.maxMoves) + " moves")
                                            .color("#00BFFF"));
                            break;
                        }
                    }
                }
            });

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