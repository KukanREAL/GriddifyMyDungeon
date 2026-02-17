package com.gridifymydungeon.plugin.spell;

import com.gridifymydungeon.plugin.gridmove.GridMoveManager;
import com.gridifymydungeon.plugin.gridmove.GridPlayerState;
import com.gridifymydungeon.plugin.spell.ClassType;
import com.gridifymydungeon.plugin.spell.SpellData;
import com.gridifymydungeon.plugin.spell.SpellDatabase;
import com.gridifymydungeon.plugin.spell.SubclassType;
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
 * /ListSpells - Show all spells available to player
 * Shows both base class spells and subclass spells
 */
public class ListSpellsCommand extends AbstractPlayerCommand {
    private final GridMoveManager playerManager;

    public ListSpellsCommand(GridMoveManager playerManager) {
        super("ListSpells", "Show all spells available to you");
        this.playerManager = playerManager;
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {

        GridPlayerState state = playerManager.getState(playerRef);

        // Check if player has a class
        if (state.stats.getClassType() == null) {
            playerRef.sendMessage(Message.raw("[Griddify] Choose a class first! Use /GridClass").color("#FF0000"));
            return;
        }

        ClassType classType = state.stats.getClassType();
        SubclassType subclass = state.stats.getSubclassType();
        int playerLevel = state.stats.getLevel();

        // Get all available spells
        List<SpellData> availableSpells = SpellDatabase.getAvailableSpells(classType, subclass, playerLevel);

        if (availableSpells.isEmpty()) {
            playerRef.sendMessage(Message.raw("[Griddify] No spells available yet!").color("#FF0000"));
            return;
        }

        // Display header
        playerRef.sendMessage(Message.raw(""));
        playerRef.sendMessage(Message.raw("═══════════════════════════════════════").color("#FFD700"));
        playerRef.sendMessage(Message.raw("  YOUR AVAILABLE SPELLS").color("#FFD700"));
        playerRef.sendMessage(Message.raw("═══════════════════════════════════════").color("#FFD700"));
        playerRef.sendMessage(Message.raw("  Class: " + classType.getDisplayName()).color("#87CEEB"));

        if (subclass != null) {
            playerRef.sendMessage(Message.raw("  Subclass: " + subclass.getDisplayName()).color("#9370DB"));
        } else {
            playerRef.sendMessage(Message.raw("  Subclass: None (unlock at level 3)").color("#808080"));
        }

        playerRef.sendMessage(Message.raw("  Level: " + playerLevel).color("#00FF00"));
        playerRef.sendMessage(Message.raw("  Spell Slots: " + state.stats.getRemainingSpellSlots() +
                " / " + state.stats.getSpellSlots()).color("#FFA500"));
        playerRef.sendMessage(Message.raw("═══════════════════════════════════════").color("#FFD700"));
        playerRef.sendMessage(Message.raw(""));

        // Group spells by type
        int baseClassCount = 0;
        int subclassCount = 0;

        // First show base class spells
        playerRef.sendMessage(Message.raw("  ▸ BASE CLASS SPELLS:").color("#87CEEB"));
        for (SpellData spell : availableSpells) {
            if (spell.isBaseClassSpell()) {
                displaySpell(playerRef, spell);
                baseClassCount++;
            }
        }

        if (baseClassCount == 0) {
            playerRef.sendMessage(Message.raw("    (None available)").color("#808080"));
        }

        playerRef.sendMessage(Message.raw(""));

        // Then show subclass spells
        if (subclass != null) {
            playerRef.sendMessage(Message.raw("  ▸ SUBCLASS SPELLS:").color("#9370DB"));
            for (SpellData spell : availableSpells) {
                if (spell.isSubclassSpell()) {
                    displaySpell(playerRef, spell);
                    subclassCount++;
                }
            }

            if (subclassCount == 0) {
                playerRef.sendMessage(Message.raw("    (None available yet)").color("#808080"));
            }
        }

        // Footer
        playerRef.sendMessage(Message.raw(""));
        playerRef.sendMessage(Message.raw("═══════════════════════════════════════").color("#FFD700"));
        playerRef.sendMessage(Message.raw("  Total: " + availableSpells.size() + " spells available").color("#FFFFFF"));
        playerRef.sendMessage(Message.raw("  Use /Cast <spell name> to prepare a spell").color("#00FF00"));
        playerRef.sendMessage(Message.raw("═══════════════════════════════════════").color("#FFD700"));
    }

    /**
     * Display a single spell
     */
    private void displaySpell(PlayerRef playerRef, SpellData spell) {
        StringBuilder sb = new StringBuilder();
        sb.append("    • ");
        sb.append(spell.getName());

        // Add level/cost info
        if (spell.getSlotCost() > 0) {
            sb.append(" (Level ").append(spell.getSpellLevel());
            sb.append(", Cost: ").append(spell.getSlotCost()).append(" slots)");
        } else {
            sb.append(" (Cantrip/Ability)");
        }

        playerRef.sendMessage(Message.raw(sb.toString()).color("#FFFFFF"));

        // Show brief description
        if (spell.getDamageDice() != null) {
            playerRef.sendMessage(Message.raw("      → " + spell.getDamageDice() + " " +
                    spell.getDamageType().name().toLowerCase() + " damage").color("#FF6B6B"));
        }

        playerRef.sendMessage(Message.raw("      → " + spell.getDescription()).color("#D3D3D3"));
        playerRef.sendMessage(Message.raw(""));
    }
}