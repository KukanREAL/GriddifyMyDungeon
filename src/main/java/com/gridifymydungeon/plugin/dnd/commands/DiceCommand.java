package com.gridifymydungeon.plugin.dnd.commands;

import com.gridifymydungeon.plugin.dnd.CombatSettings;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.EventTitleUtil;

import javax.annotation.Nonnull;

/**
 * /dice <2-100> - Roll a dice
 * UPDATED: Uses EventTitleUtil for dramatic dice roll reveals - SHOWS TO ALL PLAYERS
 */
public class DiceCommand extends AbstractPlayerCommand {

    private final CombatSettings combatSettings;
    private final RequiredArg<Integer> sidesArg;

    public DiceCommand(CombatSettings combatSettings) {
        super("dice", "Roll a dice (d2 to d100)");
        this.combatSettings = combatSettings;
        this.sidesArg = this.withRequiredArg("sides", "Number of sides (2-100)", ArgTypes.INTEGER);
    }

    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        int sides = sidesArg.get(context);

        // Validate range
        if (sides < 2 || sides > 100) {
            playerRef.sendMessage(Message.raw("[Griddify] Dice must be between 2 and 100 sides!").color("#FF0000"));
            return;
        }

        // Roll the dice
        int roll = (int) (Math.random() * sides) + 1;

        // Check for critical results
        boolean isCriticalSuccess = combatSettings.isCriticalSuccess(roll, sides);
        boolean isCriticalFailure = combatSettings.isCriticalFailure(roll);
        boolean isMajor = isCriticalSuccess || isCriticalFailure;

        // Build messages with appropriate colors
        Message primaryTitle;
        Message secondaryTitle;
        float duration;
        float fadeInDuration;
        float fadeOutDuration;

        if (isCriticalSuccess) {
            // CRITICAL SUCCESS - Gold, dramatic, longer display
            primaryTitle = Message.raw("*** CRITICAL SUCCESS ***").color("#FFD700");
            secondaryTitle = Message.raw("--== ROLLED " + roll + " ==-- on d" + sides).color("#FFFFFF");
            duration = 5.0f;
            fadeInDuration = 0.5f;
            fadeOutDuration = 1.5f;
        } else if (isCriticalFailure) {
            // CRITICAL FAILURE - Red, dramatic, longer display
            primaryTitle = Message.raw("!!! CRITICAL FAILURE !!!").color("#FF0000");
            secondaryTitle = Message.raw("--== ROLLED " + roll + " ==-- on d" + sides).color("#FF6B6B");
            duration = 5.0f;
            fadeInDuration = 0.5f;
            fadeOutDuration = 1.5f;
        } else {
            // Normal roll - Blue, quick display
            primaryTitle = Message.raw("--== ROLLED " + roll + " ==--").color("#00BFFF");
            secondaryTitle = Message.raw("d" + sides).color("#87CEEB");
            duration = 3.0f;
            fadeInDuration = 0.3f;
            fadeOutDuration = 1.0f;
        }

        // Show event title to ALL PLAYERS in the world
        EventTitleUtil.showEventTitleToWorld(
                primaryTitle,
                secondaryTitle,
                isMajor,
                null,
                duration,
                fadeInDuration,
                fadeOutDuration,
                store
        );

        System.out.println("[Griddify] [DICE] " + playerRef.getUsername() +
                " rolled d" + sides + ": " + roll + (isMajor ? " (CRITICAL)" : ""));
    }
}