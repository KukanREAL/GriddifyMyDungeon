package com.gridifymydungeon.plugin.spell;

import com.gridifymydungeon.plugin.dnd.EncounterManager;
import com.gridifymydungeon.plugin.dnd.MonsterState;
import com.gridifymydungeon.plugin.dnd.RoleManager;
import com.gridifymydungeon.plugin.spell.MonsterType;
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
import java.util.List;

/**
 * /ListSpells - Show all actions available to the caller.
 *
 * - For regular players: shows their class spells/abilities.
 * - For GM controlling a monster with no class: shows 3 basic monster attacks.
 * - For GM controlling a monster WITH a class: shows that class spells + 3 basic attacks.
 * - For GM not controlling: shows basic monster attacks as reference.
 */
public class ListSpellsCommand extends AbstractPlayerCommand {

    private final GridMoveManager playerManager;
    private final RoleManager roleManager;
    private final EncounterManager encounterManager;

    public ListSpellsCommand(GridMoveManager playerManager, RoleManager roleManager, EncounterManager encounterManager) {
        super("ListSpells", "Show all spells and attacks available to you");
        this.playerManager = playerManager;
        this.roleManager = roleManager;
        this.encounterManager = encounterManager;
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {

        GridPlayerState state = playerManager.getState(playerRef);

        // ---- GM controlling a monster ----
        if (roleManager.isGM(playerRef)) {
            MonsterState controlled = encounterManager.getControlledMonster();
            if (controlled != null) {
                showMonsterActions(playerRef, controlled);
            } else {
                playerRef.sendMessage(Message.raw("[Griddify] Control a monster first with /control <#>").color("#FFA500"));
                playerRef.sendMessage(Message.raw("[Griddify] Each monster has unique attacks shown here once controlled.").color("#CCCCCC"));
            }
            return;
        }

        // ---- Regular player ----
        if (state.stats.getClassType() == null) {
            playerRef.sendMessage(Message.raw("[Griddify] Choose a class first! Use /GridClass").color("#FF0000"));
            return;
        }

        ClassType classType = state.stats.getClassType();
        SubclassType subclass = state.stats.getSubclassType();
        int level = state.stats.getLevel();

        List<SpellData> available = SpellDatabase.getAvailableSpells(classType, subclass, level);

        header(playerRef, "YOUR ACTIONS - " + classType.getDisplayName() + "  Lv." + level);
        playerRef.sendMessage(Message.raw("  Spell Slots: " + state.stats.getRemainingSpellSlots() + "/" + state.stats.getSpellSlots()).color("#FFA500"));
        playerRef.sendMessage(Message.raw(""));

        int base = 0, sub = 0;
        playerRef.sendMessage(Message.raw("  BASE CLASS SPELLS/ABILITIES:").color("#87CEEB"));
        for (SpellData spell : available) {
            if (spell.isBaseClassSpell()) { printSpell(playerRef, spell); base++; }
        }
        if (base == 0) playerRef.sendMessage(Message.raw("    (none available yet)").color("#808080"));
        playerRef.sendMessage(Message.raw(""));

        if (subclass != null) {
            playerRef.sendMessage(Message.raw("  SUBCLASS: " + subclass.getDisplayName()).color("#9370DB"));
            for (SpellData spell : available) {
                if (spell.isSubclassSpell()) { printSpell(playerRef, spell); sub++; }
            }
            if (sub == 0) playerRef.sendMessage(Message.raw("    (none available yet)").color("#808080"));
            playerRef.sendMessage(Message.raw(""));
        }

        footer(playerRef, available.size());
    }

    private void showMonsterActions(PlayerRef playerRef, MonsterState monster) {
        ClassType mClass = monster.stats.getClassType();
        MonsterType mType = monster.monsterType;

        header(playerRef, "MONSTER ACTIONS - " + monster.getDisplayName() +
                (mClass != null ? "  [" + mClass.getDisplayName() + "]" : ""));
        playerRef.sendMessage(Message.raw(""));

        // Show this monster's specific attacks
        List<SpellData> monsterAttacks = mType != null
                ? SpellDatabase.getAttacksForMonsterType(mType)
                : java.util.Collections.emptyList();

        if (!monsterAttacks.isEmpty()) {
            playerRef.sendMessage(Message.raw("  ATTACKS (" + monster.monsterName + "):").color("#FF8C00"));
            for (SpellData atk : monsterAttacks) {
                printSpell(playerRef, atk);
            }
        } else {
            playerRef.sendMessage(Message.raw("  No specific attacks registered for this monster type.").color("#808080"));
            playerRef.sendMessage(Message.raw("  Use /Cast Scratch, Hit, or Bow_Shot for generic attacks.").color("#808080"));
        }
        playerRef.sendMessage(Message.raw(""));

        // If the monster has a player-class, show those spells too
        if (mClass != null) {
            SubclassType mSub = monster.stats.getSubclassType();
            int mLevel = Math.max(1, monster.stats.getLevel());
            List<SpellData> classSpells = SpellDatabase.getAvailableSpells(mClass, mSub, mLevel);

            playerRef.sendMessage(Message.raw("  CLASS SPELLS [" + mClass.getDisplayName() + "]:").color("#87CEEB"));
            int count = 0;
            for (SpellData spell : classSpells) {
                printSpell(playerRef, spell);
                count++;
            }
            if (count == 0) playerRef.sendMessage(Message.raw("    (none at level " + mLevel + ")").color("#808080"));
            playerRef.sendMessage(Message.raw(""));
            footer(playerRef, monsterAttacks.size() + count);
        } else {
            footer(playerRef, monsterAttacks.size());
        }
    }

    private void header(PlayerRef p, String title) {
        p.sendMessage(Message.raw(""));
        p.sendMessage(Message.raw("=========================================").color("#FFD700"));
        p.sendMessage(Message.raw("  " + title).color("#FFD700"));
        p.sendMessage(Message.raw("=========================================").color("#FFD700"));
    }

    private void footer(PlayerRef p, int total) {
        p.sendMessage(Message.raw("  Total: " + total + " actions available").color("#FFFFFF"));
        p.sendMessage(Message.raw("  Use /Cast <name> to prepare a spell/attack").color("#00FF00"));
        p.sendMessage(Message.raw("=========================================").color("#FFD700"));
    }

    private void printSpell(PlayerRef p, SpellData spell) {
        String cost = spell.getSlotCost() > 0 ? " (Lv." + spell.getSpellLevel() + " | " + spell.getSlotCost() + " slots)" : " (cantrip)";
        String range = " | range " + (spell.getRangeGrids() > 0 ? spell.getRangeGrids() + "g" : "touch");
        p.sendMessage(Message.raw("    - " + spell.getName() + cost + range).color("#FFFFFF"));
        if (spell.getDamageDice() != null) {
            p.sendMessage(Message.raw("      Dmg: " + spell.getDamageDice() + " " + spell.getDamageType().name().toLowerCase()
                    + " | " + spell.getPattern().name().toLowerCase()).color("#FF6B6B"));
        } else {
            p.sendMessage(Message.raw("      Effect | " + spell.getPattern().name().toLowerCase()).color("#90EE90"));
        }
        p.sendMessage(Message.raw("      " + spell.getDescription()).color("#D3D3D3"));
        p.sendMessage(Message.raw(""));
    }
}