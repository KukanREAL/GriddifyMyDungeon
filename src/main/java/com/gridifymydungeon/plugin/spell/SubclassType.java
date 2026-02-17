package com.gridifymydungeon.plugin.spell;

/**
 * 36 Subclasses (3 per class)
 * Unlock at level 3
 */
public enum SubclassType {
    // WIZARD (3)
    WIZARD_EVOCATION("School of Evocation", ClassType.WIZARD),
    WIZARD_ABJURATION("School of Abjuration", ClassType.WIZARD),
    WIZARD_DIVINATION("School of Divination", ClassType.WIZARD),

    // CLERIC (3)
    CLERIC_LIFE("Life Domain", ClassType.CLERIC),
    CLERIC_WAR("War Domain", ClassType.CLERIC),
    CLERIC_TEMPEST("Tempest Domain", ClassType.CLERIC),

    // ROGUE (3)
    ROGUE_ASSASSIN("Assassin", ClassType.ROGUE),
    ROGUE_ARCANE_TRICKSTER("Arcane Trickster", ClassType.ROGUE),
    ROGUE_THIEF("Thief", ClassType.ROGUE),

    // FIGHTER (3)
    FIGHTER_BATTLE_MASTER("Battle Master", ClassType.FIGHTER),
    FIGHTER_ELDRITCH_KNIGHT("Eldritch Knight", ClassType.FIGHTER),
    FIGHTER_CHAMPION("Champion", ClassType.FIGHTER),

    // RANGER (3)
    RANGER_HUNTER("Hunter", ClassType.RANGER),
    RANGER_BEAST_MASTER("Beast Master", ClassType.RANGER),
    RANGER_GLOOM_STALKER("Gloom Stalker", ClassType.RANGER),

    // PALADIN (3)
    PALADIN_DEVOTION("Oath of Devotion", ClassType.PALADIN),
    PALADIN_VENGEANCE("Oath of Vengeance", ClassType.PALADIN),
    PALADIN_ANCIENTS("Oath of the Ancients", ClassType.PALADIN),

    // WARLOCK (3)
    WARLOCK_FIEND("The Fiend", ClassType.WARLOCK),
    WARLOCK_GREAT_OLD_ONE("The Great Old One", ClassType.WARLOCK),
    WARLOCK_HEXBLADE("The Hexblade", ClassType.WARLOCK),

    // BARBARIAN (3)
    BARBARIAN_BERSERKER("Path of the Berserker", ClassType.BARBARIAN),
    BARBARIAN_TOTEM_WARRIOR("Path of the Totem Warrior", ClassType.BARBARIAN),
    BARBARIAN_ANCESTRAL_GUARDIAN("Path of the Ancestral Guardian", ClassType.BARBARIAN),

    // DRUID (3)
    DRUID_MOON("Circle of the Moon", ClassType.DRUID),
    DRUID_LAND("Circle of the Land", ClassType.DRUID),
    DRUID_STARS("Circle of Stars", ClassType.DRUID),

    // MONK (3)
    MONK_OPEN_HAND("Way of the Open Hand", ClassType.MONK),
    MONK_SHADOW("Way of Shadow", ClassType.MONK),
    MONK_FOUR_ELEMENTS("Way of the Four Elements", ClassType.MONK),

    // BARD (3)
    BARD_LORE("College of Lore", ClassType.BARD),
    BARD_VALOR("College of Valor", ClassType.BARD),
    BARD_GLAMOUR("College of Glamour", ClassType.BARD),

    // SORCERER (3)
    SORCERER_DRACONIC("Draconic Bloodline", ClassType.SORCERER),
    SORCERER_WILD_MAGIC("Wild Magic", ClassType.SORCERER),
    SORCERER_SHADOW("Shadow Magic", ClassType.SORCERER);

    private final String displayName;
    private final ClassType parentClass;

    SubclassType(String displayName, ClassType parentClass) {
        this.displayName = displayName;
        this.parentClass = parentClass;
    }

    public String getDisplayName() { return displayName; }
    public ClassType getParentClass() { return parentClass; }
}