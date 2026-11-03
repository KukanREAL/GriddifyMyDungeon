package com.gridifymydungeon.plugin.dnd.commands;

import com.gridifymydungeon.plugin.gridmove.GridMoveManager;
import com.gridifymydungeon.plugin.gridmove.GridPlayerState;
import com.gridifymydungeon.plugin.spell.ClassType;
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

/**
 * /GridSubclasses — Lists all subclasses available to the player's current class.
 *
 * - Shows only subclasses matching the player's class (e.g. Wizard → Evocation, Abjuration, Divination).
 * - Reminds the player of the level 3 requirement.
 * - If the player has no class set, prompts them to use /GridClass first.
 */
public class GridSubclassesCommand extends AbstractPlayerCommand {

    private final GridMoveManager playerManager;

    public GridSubclassesCommand(GridMoveManager playerManager) {
        super("GridSubclasses", "List subclasses available for your current class");
        this.playerManager = playerManager;
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {

        GridPlayerState state = playerManager.getState(playerRef);
        ClassType classType = state.stats.getClassType();

        if (classType == null) {
            playerRef.sendMessage(Message.raw("[Griddify] No class selected. Use /GridClass <class> first.").color("#FF0000"));
            return;
        }

        int level = state.stats.getLevel();
        SubclassType current = state.stats.getSubclassType();

        playerRef.sendMessage(Message.raw("").color("#FFFFFF"));
        playerRef.sendMessage(Message.raw("=========================================").color("#9370DB"));
        playerRef.sendMessage(Message.raw("  SUBCLASSES — " + classType.getDisplayName()
                + "  (Lv." + level + ")").color("#9370DB"));
        playerRef.sendMessage(Message.raw("=========================================").color("#9370DB"));

        if (level < 3) {
            playerRef.sendMessage(Message.raw("  Subclasses unlock at level 3.").color("#FFA500"));
            playerRef.sendMessage(Message.raw("  You need " + (3 - level) + " more level(s).").color("#FFA500"));
            playerRef.sendMessage(Message.raw("").color("#FFFFFF"));
        } else if (current != null) {
            playerRef.sendMessage(Message.raw("  Current subclass: " + current.getDisplayName()).color("#00FF7F"));
            playerRef.sendMessage(Message.raw("  Use /GridSubclass <name> to switch.").color("#CCCCCC"));
            playerRef.sendMessage(Message.raw("").color("#FFFFFF"));
        } else {
            playerRef.sendMessage(Message.raw("  Pick one with /GridSubclass <name>").color("#FFA500"));
            playerRef.sendMessage(Message.raw("").color("#FFFFFF"));
        }

        // List subclasses for this class only
        boolean found = false;
        for (SubclassType sub : SubclassType.values()) {
            if (sub.getParentClass() != classType) continue;
            found = true;

            boolean isChosen = (sub == current);
            String marker = isChosen ? " [ACTIVE]" : "";
            String color  = isChosen ? "#00FF7F" : "#FFFFFF";

            playerRef.sendMessage(Message.raw("  - " + sub.getDisplayName() + marker).color(color));

            // Print a short flavour description
            String desc = getSubclassDescription(sub);
            playerRef.sendMessage(Message.raw("      " + desc).color("#AAAAAA"));
            playerRef.sendMessage(Message.raw("").color("#FFFFFF"));
        }

        if (!found) {
            playerRef.sendMessage(Message.raw("  (No subclasses registered for this class)").color("#808080"));
        }

        playerRef.sendMessage(Message.raw("=========================================").color("#9370DB"));
    }

    /** Short flavour line per subclass — matches the SpellDatabase descriptions. */
    private String getSubclassDescription(SubclassType sub) {
        switch (sub) {
            // Wizard
            case WIZARD_EVOCATION:      return "Empower destructive spells (Empowered Evocation, Sculpt Spells)";
            case WIZARD_ABJURATION:     return "Ward and protect with arcane barriers (Arcane Ward)";
            case WIZARD_DIVINATION:     return "See fate before it happens (Portent dice)";
            // Cleric
            case CLERIC_LIFE:           return "Master of healing and radiant protection";
            case CLERIC_WAR:            return "Divine warrior — bonus attacks and War Priest power";
            case CLERIC_TEMPEST:        return "Lightning and thunder — Destructive Wrath";
            // Rogue
            case ROGUE_ASSASSIN:        return "Deadly opener — triple damage on surprised targets";
            case ROGUE_ARCANE_TRICKSTER:return "Weave spells into sneak attacks";
            case ROGUE_THIEF:           return "Fast Hands, Use Magic Device";
            // Fighter
            case FIGHTER_BATTLE_MASTER: return "Tactical manoeuvres with superiority dice";
            case FIGHTER_ELDRITCH_KNIGHT:return "Combine weapon mastery with wizard spells";
            case FIGHTER_CHAMPION:      return "Improved critical (19-20), superior athletics";
            // Ranger
            case RANGER_HUNTER:         return "Hunter's Mark and monster slayer techniques";
            case RANGER_BEAST_MASTER:   return "Bond with a beast companion";
            case RANGER_GLOOM_STALKER:  return "Invisible in darkness, extra attack round 1";
            // Paladin
            case PALADIN_DEVOTION:      return "Sacred Weapon, Holy Nimbus — pure radiant power";
            case PALADIN_VENGEANCE:      return "Relentless Avenger — hunt your quarry";
            case PALADIN_ANCIENTS:      return "Nature's warden — Aura of Warding";
            // Warlock
            case WARLOCK_FIEND:         return "Dark One's Blessing — HP on kills, Hellfire";
            case WARLOCK_GREAT_OLD_ONE: return "Awakened Mind — telepathy and forbidden lore";
            case WARLOCK_HEXBLADE:      return "Hex Warrior — CHA for weapon attacks";
            // Barbarian
            case BARBARIAN_BERSERKER:   return "Frenzy — extra attack while raging";
            case BARBARIAN_TOTEM_WARRIOR:return "Bear/Eagle/Wolf — totem spirit powers";
            case BARBARIAN_ANCESTRAL_GUARDIAN:return "Protect allies with spectral ancestors";
            // Druid
            case DRUID_MOON:            return "Wild Shape into powerful beasts (CR 6+)";
            case DRUID_LAND:            return "Natural Recovery — regain spell slots";
            case DRUID_STARS:           return "Starry Form — Archer, Chalice, Dragon constellations";
            // Monk
            case MONK_OPEN_HAND:        return "Open Hand Technique — prone, push, or deny reactions";
            case MONK_SHADOW:           return "Shadow Step — teleport between shadows";
            case MONK_FOUR_ELEMENTS:    return "Elemental disciplines — Fists of Fire, Water Whip";
            // Bard
            case BARD_LORE:             return "Additional magical secrets, Cutting Words";
            case BARD_VALOR:            return "Combat Inspiration, Extra Attack";
            case BARD_GLAMOUR:          return "Mantle of Inspiration, Enthralling Performance";
            // Sorcerer
            case SORCERER_DRACONIC:     return "Draconic resilience — natural armour, element affinity";
            case SORCERER_WILD_MAGIC:   return "Wild Magic Surge — chaotic bursts of power";
            case SORCERER_SHADOW:       return "Eyes of the Dark, Hound of Ill Omen";
            default:                    return "";
        }
    }
}