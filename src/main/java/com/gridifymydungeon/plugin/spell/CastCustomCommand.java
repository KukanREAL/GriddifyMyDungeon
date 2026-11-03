package com.gridifymydungeon.plugin.spell;

import com.gridifymydungeon.plugin.debug.DebugRoleWrapper;
import com.gridifymydungeon.plugin.dnd.EncounterManager;
import com.gridifymydungeon.plugin.dnd.RoleManager;
import com.gridifymydungeon.plugin.gridmove.GridMoveManager;
import com.gridifymydungeon.plugin.gridmove.GridPlayerState;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * /cast custom — Begin a custom monster attack for the GM.
 *
 * Starts the custom cast flow. After this command the GM must set at least one
 * of /cdmg or /cdicedmg, then use /casttarget (unlimited range) and /castfinal.
 *
 * No Grid_Range overlay is shown — custom attacks have unlimited range.
 * No spell slot is consumed. NPC/monster is NOT frozen (GM chooses targets freely).
 */
public class CastCustomCommand extends AbstractPlayerCommand {

    private final GridMoveManager gridManager;
    private final EncounterManager encounterManager;
    private final SpellVisualManager visualManager;
    private final RoleManager roleManager;

    public CastCustomCommand(GridMoveManager gridManager, EncounterManager encounterManager,
                             SpellVisualManager visualManager, RoleManager roleManager) {
        super("cast custom", "Begin a custom monster attack (GM only)");
        this.gridManager     = gridManager;
        this.encounterManager = encounterManager;
        this.visualManager   = visualManager;
        this.roleManager     = roleManager;
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

        // Cancel any existing cast/custom cast cleanly
        if (state.getSpellCastingState() != null) {
            state.clearSpellCastingState();
            world.execute(() -> {
                visualManager.clearSpellVisuals(playerRef.getUuid(), world);
                visualManager.clearRangeOverlay(playerRef.getUuid(), world);
            });
        }
        state.clearCustomCastState();

        // Resolve caster position from controlled monster or GM NPC
        int casterGridX, casterGridZ;
        float casterY;
        com.gridifymydungeon.plugin.dnd.MonsterState monster = encounterManager.getControlledMonster();
        if (monster != null) {
            casterGridX = monster.currentGridX;
            casterGridZ = monster.currentGridZ;
            casterY     = monster.spawnY;
        } else {
            casterGridX = state.currentGridX;
            casterGridZ = state.currentGridZ;
            casterY     = state.npcY;
        }

        CustomCastState customState = new CustomCastState(casterGridX, casterGridZ, casterY);
        state.setCustomCastState(customState);

        playerRef.sendMessage(Message.raw("[Griddify] ★ CUSTOM ATTACK started!").color("#FF69B4"));
        playerRef.sendMessage(Message.raw("[Griddify] Set damage with one or both of:").color("#FFD700"));
        playerRef.sendMessage(Message.raw("[Griddify]   /cdmg {number}          — flat bonus damage").color("#AAAAAA"));
        playerRef.sendMessage(Message.raw("[Griddify]   /cdicedmg {N}x{D}       — roll N dice of D sides (e.g. 2x20, 3x6)").color("#AAAAAA"));
        playerRef.sendMessage(Message.raw("[Griddify] Then walk to each target and use /casttarget (unlimited range).").color("#87CEEB"));
        playerRef.sendMessage(Message.raw("[Griddify] Fire with /castfinal | Cancel with /castcancel").color("#00FF00"));

        System.out.println("[Griddify] [CUSTOM] " + playerRef.getUsername()
                + " started custom attack at (" + casterGridX + "," + casterGridZ + ")");
    }
}