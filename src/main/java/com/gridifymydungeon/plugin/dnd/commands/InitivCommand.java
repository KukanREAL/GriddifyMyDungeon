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
 * /initiv - Roll initiative for all players and monsters (GM only)
 * Rolls d20 + INITIATIVE modifier for everyone
 * Initiative 0 = skip turn and reroll next round
 */
public class InitivCommand extends AbstractPlayerCommand {

    private final RoleManager roleManager;
    private final CombatManager combatManager;

    public InitivCommand(RoleManager roleManager, CombatManager combatManager) {
        super("initiv", "Roll initiative for all (GM only)");
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
            playerRef.sendMessage(Message.raw("[Griddify] Only the GM can use this command!"));
            return;
        }

        // Roll initiative for everyone
        List<CombatManager.CombatParticipant> results = combatManager.rollInitiativeOnly();

        if (results.isEmpty()) {
            playerRef.sendMessage(Message.raw("[Griddify] No participants found! Need at least 1 player or monster."));
            return;
        }

        // Display results
        playerRef.sendMessage(Message.raw(""));
        playerRef.sendMessage(Message.raw("___________________________________"));
        playerRef.sendMessage(Message.raw("       INITIATIVE ROLLS"));
        playerRef.sendMessage(Message.raw("___________________________________"));

        for (CombatManager.CombatParticipant p : results) {
            String rollInfo = p.name + ": rolled " + p.initiativeRoll +
                    " + " + p.initiativeModifier + " = " + p.totalInitiative;

            if (p.skipTurn) {
                rollInfo += " (SKIPS TURN - will reroll)";
            }

            playerRef.sendMessage(Message.raw(rollInfo));
        }

        playerRef.sendMessage(Message.raw("___________________________________"));
        playerRef.sendMessage(Message.raw("Use /combat to start combat mode"));
        playerRef.sendMessage(Message.raw(""));

        System.out.println("[Griddify] [INITIATIVE] GM rolled initiative for all participants");
    }
}
