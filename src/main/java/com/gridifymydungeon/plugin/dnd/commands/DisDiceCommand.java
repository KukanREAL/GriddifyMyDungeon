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

import javax.annotation.Nonnull;

/**
 * /DisDice <2-100> - Roll with disadvantage (2 dice, take lower)
 * Respects critical roll settings
 * WITH COLORS!
 */
public class DisDiceCommand extends AbstractPlayerCommand {
    private final CombatSettings combatSettings;
    private final RequiredArg<Integer> sidesArg;

    public DisDiceCommand(CombatSettings combatSettings) {
        super("DisDice", "Roll with disadvantage (2 dice, take lower)");
        this.combatSettings = combatSettings;
        this.sidesArg = this.withRequiredArg("sides", "Dice sides (2-100)", ArgTypes.INTEGER);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {

        int sides = sidesArg.get(context);

        // Validate range
        if (sides < 2 || sides > 100) {
            playerRef.sendMessage(Message.raw("[Griddify] Dice must be between 2 and 100 sides!").color("#FF0000"));
            return;
        }

        // Roll 2 dice
        int roll1 = (int) (Math.random() * sides) + 1;
        int roll2 = (int) (Math.random() * sides) + 1;

        // Take lower
        int result = Math.min(roll1, roll2);

        // Display results with DISADVANTAGE colors (purple/dark theme)
        playerRef.sendMessage(Message.raw(""));
        playerRef.sendMessage(Message.raw("=========================================").color("#9370DB"));
        playerRef.sendMessage(Message.raw("      DISADVANTAGE ROLL d" + sides).color("#8B008B"));
        playerRef.sendMessage(Message.raw("=========================================").color("#9370DB"));
        playerRef.sendMessage(Message.raw(""));
        playerRef.sendMessage(Message.raw("  Roll 1: " + roll1).color("#FFFFFF"));
        playerRef.sendMessage(Message.raw("  Roll 2: " + roll2).color("#FFFFFF"));
        playerRef.sendMessage(Message.raw(""));
        playerRef.sendMessage(Message.raw("  > Taking LOWER: " +
                combatSettings.formatDiceResult(result, sides)).color("#FF6347"));
        playerRef.sendMessage(Message.raw(""));

        System.out.println("[Griddify] [DISADVANTAGE] " + playerRef.getUsername() +
                " rolled d" + sides + ": " + roll1 + ", " + roll2 + " → " + result);
    }
}