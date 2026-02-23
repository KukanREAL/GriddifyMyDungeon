package com.gridifymydungeon.plugin.spell;

import com.gridifymydungeon.plugin.dnd.EncounterManager;
import com.gridifymydungeon.plugin.dnd.MonsterState;
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
 * /polyrevert {monster#}
 *
 * Reverts a polymorphed monster back to its original form early.
 * Auto-revert at 10 turns also handled by PolymorphManager.tickTurn().
 */
public class PolyrevertCommand extends AbstractPlayerCommand {

    private final EncounterManager encounterManager;
    private final PolymorphManager polymorphManager;
    private final RequiredArg<Integer> numberArg;

    public PolyrevertCommand(EncounterManager em, PolymorphManager pm) {
        super("polyrevert", "Revert a polymorphed monster: /polyrevert {monster#}");
        this.encounterManager = em;
        this.polymorphManager = pm;
        this.numberArg = this.withRequiredArg("number", "Monster number to revert", ArgTypes.INTEGER);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {

        int num = numberArg.get(context);

        MonsterState ms = encounterManager.getMonsters().stream()
                .filter(m -> m.monsterNumber == num)
                .findFirst().orElse(null);

        if (ms == null) {
            playerRef.sendMessage(Message.raw(
                    "[Griddify] No monster #" + num + " found.").color("#FF0000"));
            return;
        }

        boolean ok = polymorphManager.revertMonster(ms, world, playerRef, false);
        if (!ok) {
            playerRef.sendMessage(Message.raw(
                    "[Griddify] Monster #" + num + " is not currently polymorphed.").color("#FFA500"));
        }
    }
}