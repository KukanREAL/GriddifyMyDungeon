package com.gridifymydungeon.plugin.spell;

/**
 * 12 D&D base classes
 */
public enum ClassType {
    ROGUE("Rogue", "DEX"),
    CLERIC("Cleric", "WIS"),
    BARD("Bard", "CHA"),
    RANGER("Ranger", "WIS"),
    PALADIN("Paladin", "CHA"),
    BARBARIAN("Barbarian", "STR"),
    DRUID("Druid", "WIS"),
    MONK("Monk", "WIS"),
    SORCERER("Sorcerer", "CHA"),
    WARLOCK("Warlock", "CHA"),
    WIZARD("Wizard", "INT"),
    FIGHTER("Fighter", "STR");

    private final String displayName;
    private final String spellcastingAbility;

    ClassType(String displayName, String spellcastingAbility) {
        this.displayName = displayName;
        this.spellcastingAbility = spellcastingAbility;
    }

    public String getDisplayName() { return displayName; }
    public String getSpellcastingAbility() { return spellcastingAbility; }

    /**
     * Does this class have spellcasting?
     */
    public boolean isSpellcaster() {
        return this != BARBARIAN && this != ROGUE && this != FIGHTER && this != MONK;
    }
}