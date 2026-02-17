package com.gridifymydungeon.plugin.dnd.commands;

import com.gridifymydungeon.plugin.dnd.CombatManager;
import com.gridifymydungeon.plugin.dnd.RoleManager;
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
 * /combat - Start combat mode (GM only)
 * Rolls initiative and establishes turn order
 * Only current participant can move
 * Use /endturn to advance to next participant
 * WITH COLORS!
 */
public class CombatCommand extends AbstractPlayerCommand {

    private final RoleManager roleManager;
    private final CombatManager combatManager;

    public CombatCommand(RoleManager roleManager, CombatManager combatManager) {
        super("combat", "Start/end combat mode (GM only)");
        this.roleManager = roleManager;
        this.combatManager = combatManager;
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
            playerRef.sendMessage(Message.raw("[Griddify] Only the GM can use this command!").color("#FF0000"));
            return;
        }

        // Toggle combat mode
        if (combatManager.isCombatActive()) {
            // End combat
            combatManager.endCombat();

            playerRef.sendMessage(Message.raw(""));
            playerRef.sendMessage(Message.raw("=========================================").color("#FFD700"));
            playerRef.sendMessage(Message.raw("           COMBAT ENDED").color("#90EE90"));
            playerRef.sendMessage(Message.raw("=========================================").color("#FFD700"));
            playerRef.sendMessage(Message.raw(""));
            playerRef.sendMessage(Message.raw("All participants can now move freely").color("#FFFFFF"));
            playerRef.sendMessage(Message.raw(""));

            System.out.println("[Griddify] [COMBAT] Combat mode ended by GM");

        } else {
            // Start combat
            List<CombatManager.CombatParticipant> turnOrder = combatManager.startCombat();

            if (turnOrder.isEmpty()) {
                playerRef.sendMessage(Message.raw("[Griddify] Cannot start combat - no valid participants!").color("#FF0000"));
                playerRef.sendMessage(Message.raw("[Griddify] All participants rolled 0 initiative (skipping turn).").color("#FFA500"));
                return;
            }

            // Display turn order
            playerRef.sendMessage(Message.raw(""));
            playerRef.sendMessage(Message.raw("=========================================").color("#FFD700"));
            playerRef.sendMessage(Message.raw("          COMBAT STARTED").color("#FF0000"));
            playerRef.sendMessage(Message.raw("=========================================").color("#FFD700"));
            playerRef.sendMessage(Message.raw(""));
            playerRef.sendMessage(Message.raw("TURN ORDER (by initiative):").color("#00BFFF"));
            playerRef.sendMessage(Message.raw(""));

            for (int i = 0; i < turnOrder.size(); i++) {
                CombatManager.CombatParticipant p = turnOrder.get(i);
                String marker = (i == 0) ? "> " : "  ";
                String turnInfo = marker + (i + 1) + ". " + p.name + " (Initiative: " + p.totalInitiative + ")";

                if (i == 0) {
                    playerRef.sendMessage(Message.raw(turnInfo).color("#00FF00"));
                } else {
                    playerRef.sendMessage(Message.raw(turnInfo).color("#FFFFFF"));
                }
            }

            playerRef.sendMessage(Message.raw(""));
            playerRef.sendMessage(Message.raw("=========================================").color("#FFD700"));

            CombatManager.CombatParticipant first = turnOrder.get(0);
            playerRef.sendMessage(Message.raw(""));
            playerRef.sendMessage(Message.raw("CURRENT TURN: " + first.name).color("#00FFFF"));
            playerRef.sendMessage(Message.raw(""));
            playerRef.sendMessage(Message.raw("Rules:").color("#00BFFF"));
            playerRef.sendMessage(Message.raw("  * Only current participant can move").color("#FFFFFF"));
            playerRef.sendMessage(Message.raw("  * Use /endturn to advance to next").color("#FFFFFF"));
            playerRef.sendMessage(Message.raw(""));

            System.out.println("[Griddify] [COMBAT] Combat mode started - " + turnOrder.size() + " participants");
        }
    }
}