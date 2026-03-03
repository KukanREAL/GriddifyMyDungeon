package com.gridifymydungeon.plugin.spell;

import com.gridifymydungeon.plugin.debug.DebugRoleWrapper;
import com.gridifymydungeon.plugin.dnd.RoleManager;
import com.gridifymydungeon.plugin.gridmove.GridMoveManager;
import com.gridifymydungeon.plugin.gridmove.GridPlayerState;
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
 * /cdicedmg {N}x{D} — Set dice roll damage for the current custom cast.
 *
 * Format: NxD  where N = number of dice, D = sides per die.
 * Examples:
 *   /cdicedmg 2x20   → roll 2 d20s
 *   /cdicedmg 3x6    → roll 3 d6s
 *   /cdicedmg 1x8    → roll 1 d8
 *
 * Also accepts N+D as an alias (same meaning as NxD).
 * All dice are rolled at /castfinal and summed. Combines with /cdmg flat bonus.
 */
public class CastCustomDiceCommand extends AbstractPlayerCommand {

    private final GridMoveManager gridManager;
    private final RoleManager roleManager;
    private final RequiredArg<String> diceArg;

    public CastCustomDiceCommand(GridMoveManager gridManager, RoleManager roleManager) {
        super("cdicedmg", "Set dice damage for custom cast (GM only) — format: NxD e.g. 2x20");
        this.gridManager = gridManager;
        this.roleManager = roleManager;
        this.diceArg     = this.withRequiredArg("dice", "Dice expression: NxD or N+D (e.g. 2x20)", ArgTypes.STRING);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {

        if (!DebugRoleWrapper.isGM(roleManager, playerRef)) {
            playerRef.sendMessage(Message.raw("[Griddify] GM only!").color("#FF0000"));
            return;
        }

        GridPlayerState state = gridManager.getState(playerRef);
        CustomCastState custom = state.getCustomCastState();

        if (custom == null) {
            playerRef.sendMessage(Message.raw("[Griddify] No custom cast in progress. Use /cast custom first.").color("#FF0000"));
            return;
        }

        String expr = diceArg.get(context).trim();

        // Parse NxD or N+D
        int n, d;
        try {
            String[] parts;
            if (expr.contains("x") || expr.contains("X")) {
                parts = expr.toLowerCase().split("x", 2);
            } else if (expr.contains("+")) {
                parts = expr.split("\\+", 2);
            } else {
                throw new NumberFormatException("no separator");
            }
            n = Integer.parseInt(parts[0].trim());
            d = Integer.parseInt(parts[1].trim());
        } catch (Exception e) {
            playerRef.sendMessage(Message.raw(
                    "[Griddify] Bad format! Use NxD — e.g. /cdicedmg 2x20 or 3x6").color("#FF0000"));
            return;
        }

        if (n < 1 || n > 100) {
            playerRef.sendMessage(Message.raw("[Griddify] Dice count must be 1–100.").color("#FF0000"));
            return;
        }
        if (d < 2 || d > 1000) {
            playerRef.sendMessage(Message.raw("[Griddify] Dice sides must be 2–1000.").color("#FF0000"));
            return;
        }

        custom.setDice(n, d);

        playerRef.sendMessage(Message.raw("[Griddify] Dice set: " + n + "x d" + d + ".").color("#FFD700"));
        playerRef.sendMessage(Message.raw("[Griddify] Damage so far: " + custom.damageSummary()).color("#FF6B6B"));

        if (!custom.hasTargets()) {
            playerRef.sendMessage(Message.raw("[Griddify] Walk to targets and use /casttarget, then /castfinal.").color("#87CEEB"));
        }
    }
}