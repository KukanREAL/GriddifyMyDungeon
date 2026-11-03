package com.gridifymydungeon.plugin.spell;

import com.gridifymydungeon.plugin.dnd.EncounterManager;
import com.gridifymydungeon.plugin.dnd.MonsterState;
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
 * /polyform {Bear|Dire_Wolf|Rex|Feran_Windwalker|Spider}
 *
 * Used after /cast polymorph → /casttarget → /castfinal sets the pending target.
 * Applies the chosen creature form to whatever creature is at the target cell.
 *
 * To undo early, use: /polyrevert {monster#}
 */
public class PolyformCommand extends AbstractPlayerCommand {

    private final GridMoveManager gridMoveManager;
    private final EncounterManager encounterManager;
    private final PolymorphManager polymorphManager;
    private final RequiredArg<String> formArg;

    public PolyformCommand(GridMoveManager gm, EncounterManager em, PolymorphManager pm) {
        super("polyform",
                "Choose polymorph form: /polyform {Bear|Dire_Wolf|Rex|Feran_Windwalker|Spider}");
        this.gridMoveManager = gm;
        this.encounterManager = em;
        this.polymorphManager = pm;
        this.formArg = this.withRequiredArg("form",
                "Form: Bear, Dire_Wolf, Rex, Feran_Windwalker, Spider", ArgTypes.STRING);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {

        String input = formArg.get(context);

        PolymorphManager.Form form = PolymorphManager.Form.parse(input);
        if (form == null) {
            playerRef.sendMessage(Message.raw(
                    "[Griddify] Unknown form '" + input + "'. Options: "
                            + PolymorphManager.Form.listNames()).color("#FF0000"));
            return;
        }

        // Check for a pending polymorph target stored by /castfinal
        GridPlayerState casterState = gridMoveManager.getState(playerRef);
        SpellCastingState cs = casterState.getSpellCastingState();

        if (cs == null || cs.getPendingPolymorphTarget() == null) {
            playerRef.sendMessage(Message.raw(
                            "[Griddify] No pending polymorph target. Use /cast polymorph → /casttarget → /castfinal first.")
                    .color("#FF0000"));
            return;
        }

        SpellCastingState.GridCell target = cs.getPendingPolymorphTarget();
        cs.clearPendingPolymorphTarget();

        // Find monster at target cell first
        MonsterState monster = encounterManager.getMonsters().stream()
                .filter(m -> m.isAlive()
                        && m.currentGridX == target.x
                        && m.currentGridZ == target.z)
                .findFirst().orElse(null);

        if (monster != null) {
            polymorphManager.polymorphMonster(monster, form, world, playerRef);
            return;
        }

        // Fall back to player NPCs
        for (GridPlayerState ps : gridMoveManager.getAllStates()) {
            if (ps.npcEntity != null && ps.npcEntity.isValid()
                    && ps.currentGridX == target.x
                    && ps.currentGridZ == target.z) {
                polymorphManager.polymorphPlayer(ps, form, world, playerRef);
                return;
            }
        }

        playerRef.sendMessage(Message.raw(
                "[Griddify] No living creature at (" + target.x + ", " + target.z
                        + ") to polymorph.").color("#FF0000"));
    }
}