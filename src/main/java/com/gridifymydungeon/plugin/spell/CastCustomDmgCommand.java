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
 * /cdmg {number} — Set flat bonus damage for the current custom cast.
 *
 * Must be used after /cast custom and before /castfinal.
 * Can be combined with /cdicedmg — both values are added together at /castfinal.
 */
public class CastCustomDmgCommand extends AbstractPlayerCommand {

    private final GridMoveManager gridManager;
    private final RoleManager roleManager;
    private final RequiredArg<Integer> amountArg;

    public CastCustomDmgCommand(GridMoveManager gridManager, RoleManager roleManager) {
        super("cdmg", "Set flat damage for custom cast (GM only)");
        this.gridManager = gridManager;
        this.roleManager = roleManager;
        this.amountArg   = this.withRequiredArg("amount", "Flat damage amount", ArgTypes.INTEGER);
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

        int amount = amountArg.get(context);
        if (amount < 0) {
            playerRef.sendMessage(Message.raw("[Griddify] Damage must be 0 or higher.").color("#FF0000"));
            return;
        }

        custom.setFlatDamage(amount);

        playerRef.sendMessage(Message.raw("[Griddify] Flat damage set to " + amount + ".").color("#FFD700"));
        playerRef.sendMessage(Message.raw("[Griddify] Damage so far: " + custom.damageSummary()).color("#FF6B6B"));

        if (!custom.hasTargets()) {
            playerRef.sendMessage(Message.raw("[Griddify] Walk to targets and use /casttarget, then /castfinal.").color("#87CEEB"));
        }
    }
}