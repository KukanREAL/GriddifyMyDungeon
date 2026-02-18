package com.gridifymydungeon.plugin.dnd.commands;

import com.gridifymydungeon.plugin.dnd.CombatManager;
import com.gridifymydungeon.plugin.dnd.RoleManager;
import com.gridifymydungeon.plugin.gridmove.GridMoveManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * /combat - Start/end combat mode (GM only).
 * Broadcasts full turn order to ALL players in chat.
 */
public class CombatCommand extends AbstractPlayerCommand {

    private final RoleManager roleManager;
    private final CombatManager combatManager;
    private final GridMoveManager gridMoveManager;

    public CombatCommand(RoleManager roleManager, CombatManager combatManager, GridMoveManager gridMoveManager) {
        super("combat", "Start/end combat mode (GM only)");
        this.roleManager = roleManager;
        this.combatManager = combatManager;
        this.gridMoveManager = gridMoveManager;
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {

        if (!roleManager.isGM(playerRef)) {
            playerRef.sendMessage(Message.raw("[Griddify] Only the GM can use this command!").color("#FF0000"));
            return;
        }

        if (combatManager.isCombatActive()) {
            combatManager.endCombat();
            broadcast("[Griddify] ========================================= COMBAT ENDED =========================================", "#90EE90");
            broadcast("[Griddify] All participants can now move freely.", "#FFFFFF");
            System.out.println("[Griddify] [COMBAT] Combat mode ended by GM");
        } else {
            List<CombatManager.CombatParticipant> turnOrder = combatManager.startCombat();
            if (turnOrder.isEmpty()) {
                playerRef.sendMessage(Message.raw("[Griddify] Cannot start combat - no valid participants!").color("#FF0000"));
                return;
            }
            broadcastTurnOrder(turnOrder, 0, "COMBAT STARTED!", combatManager.getRoundNumber());
            System.out.println("[Griddify] [COMBAT] Combat mode started - " + turnOrder.size() + " participants");
        }
    }

    /** Send the full turn order list to every player in the game. */
    public void broadcastTurnOrder(List<CombatManager.CombatParticipant> turnOrder, int currentIndex, String header) {
        broadcastTurnOrder(turnOrder, currentIndex, header, combatManager.getRoundNumber());
    }

    public void broadcastTurnOrder(List<CombatManager.CombatParticipant> turnOrder, int currentIndex, String header, int round) {
        List<PlayerRef> all = gridMoveManager.getAllPlayerRefs();
        for (PlayerRef p : all) {
            sendTurnOrderTo(p, turnOrder, currentIndex, header, round);
        }
    }

    private void sendTurnOrderTo(PlayerRef p, List<CombatManager.CombatParticipant> turnOrder, int currentIndex, String header, int round) {
        p.sendMessage(Message.raw(""));
        p.sendMessage(Message.raw("=========================================").color("#FFD700"));
        p.sendMessage(Message.raw("   " + header + (round > 0 ? "  [Round " + round + "]" : "")).color("#FF4500"));
        p.sendMessage(Message.raw("=========================================").color("#FFD700"));
        p.sendMessage(Message.raw("TURN ORDER:").color("#00BFFF"));
        p.sendMessage(Message.raw(""));

        for (int i = 0; i < turnOrder.size(); i++) {
            CombatManager.CombatParticipant cp = turnOrder.get(i);
            boolean isCurrent = (i == currentIndex);
            String marker = isCurrent ? "> " : "  ";
            String line = marker + (i + 1) + ". " + cp.name + "  (init: " + cp.totalInitiative + ")";
            p.sendMessage(Message.raw(line).color(isCurrent ? "#00FF00" : "#CCCCCC"));
        }

        p.sendMessage(Message.raw(""));
        CombatManager.CombatParticipant current = turnOrder.get(currentIndex);
        p.sendMessage(Message.raw("NOW: " + current.name + " - use /endturn when done").color("#00FFFF"));
        p.sendMessage(Message.raw("=========================================").color("#FFD700"));
        p.sendMessage(Message.raw(""));
    }

    private void broadcast(String text, String color) {
        for (PlayerRef p : gridMoveManager.getAllPlayerRefs()) {
            p.sendMessage(Message.raw(text).color(color));
        }
    }
}