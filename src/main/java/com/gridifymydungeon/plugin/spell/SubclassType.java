package com.gridifymydungeon.plugin.spell;

/**
 * 36 Subclasses (3 per class)
 * Unlock at level 3
 */
public enum SubclassType {
    // WIZARD (3)
    WIZARD_EVOCATION("School_of_Evocation", ClassType.WIZARD),
    WIZARD_ABJURATION("School_of_Abjuration", ClassType.WIZARD),
    WIZARD_DIVINATION("School_of_Divination", ClassType.WIZARD),

    // CLERIC (3)
    CLERIC_LIFE("Life_Domain", ClassType.CLERIC),
    CLERIC_WAR("War_Domain", ClassType.CLERIC),
    CLERIC_TEMPEST("Tempest_Domain", ClassType.CLERIC),

    // ROGUE (3)
    ROGUE_ASSASSIN("Assassin", ClassType.ROGUE),
    ROGUE_ARCANE_TRICKSTER("Arcane_Trickster", ClassType.ROGUE),
    ROGUE_THIEF("Thief", ClassType.ROGUE),

    // FIGHTER (3)
    FIGHTER_BATTLE_MASTER("Battle_Master", ClassType.FIGHTER),
    FIGHTER_ELDRITCH_KNIGHT("Eldritch_Knight", ClassType.FIGHTER),
    FIGHTER_CHAMPION("Champion", ClassType.FIGHTER),

    // RANGER (3)
    RANGER_HUNTER("Hunter", ClassType.RANGER),
    RANGER_BEAST_MASTER("Beast_Master", ClassType.RANGER),
    RANGER_GLOOM_STALKER("Gloom_Stalker", ClassType.RANGER),

    // PALADIN (3)
    PALADIN_DEVOTION("Oath_of_Devotion", ClassType.PALADIN),
    PALADIN_VENGEANCE("Oath_of_Vengeance", ClassType.PALADIN),
    PALADIN_ANCIENTS("Oath_of_the_Ancients", ClassType.PALADIN),

    // WARLOCK (3)
    WARLOCK_FIEND("The_Fiend", ClassType.WARLOCK),
    WARLOCK_GREAT_OLD_ONE("The_Great_Old_One", ClassType.WARLOCK),
    WARLOCK_HEXBLADE("The_Hexblade", ClassType.WARLOCK),

    // BARBARIAN (3)
    BARBARIAN_BERSERKER("Path_of_the_Berserker", ClassType.BARBARIAN),
    BARBARIAN_TOTEM_WARRIOR("Path_of_the_Totem_Warrior", ClassType.BARBARIAN),
    BARBARIAN_ANCESTRAL_GUARDIAN("Path_of_the_Ancestral_Guardian", ClassType.BARBARIAN),

    // DRUID (3)
    DRUID_MOON("Circle_of_the_Moon", ClassType.DRUID),
    DRUID_LAND("Circle_of_the_Land", ClassType.DRUID),
    DRUID_STARS("Circle_of_Stars", ClassType.DRUID),

    // MONK (3)
    MONK_OPEN_HAND("Way_of_the_Open_Hand", ClassType.MONK),
    MONK_SHADOW("Way_of_Shadow", ClassType.MONK),
    MONK_FOUR_ELEMENTS("Way_of_the_Four_Elements", ClassType.MONK),

    // BARD (3)
    BARD_LORE("College_of_Lore", ClassType.BARD),
    BARD_VALOR("College_of_Valor", ClassType.BARD),
    BARD_GLAMOUR("College_of_Glamour", ClassType.BARD),

    // SORCERER (3)
    SORCERER_DRACONIC("Draconic_Bloodline", ClassType.SORCERER),
    SORCERER_WILD_MAGIC("Wild_Magic", ClassType.SORCERER),
    SORCERER_SHADOW("Shadow_Magic", ClassType.SORCERER);

    private final String displayName;
    private final ClassType parentClass;

    SubclassType(String displayName, ClassType parentClass) {
        this.displayName = displayName;
        this.parentClass = parentClass;
    }

    public String getDisplayName() { return displayName; }
    public ClassType getParentClass() { return parentClass; }
}