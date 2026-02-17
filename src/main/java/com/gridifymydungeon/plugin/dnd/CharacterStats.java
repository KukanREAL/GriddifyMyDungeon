package com.gridifymydungeon.plugin.dnd;

import com.gridifymydungeon.plugin.spell.ClassType;
import com.gridifymydungeon.plugin.spell.SubclassType;

/**
 * Complete character stats with CLASS SYSTEM support
 */
public class CharacterStats {
    // Ability scores (3-30 range)
    public int strength = 10;
    public int dexterity = 10;
    public int constitution = 10;
    public int intelligence = 10;
    public int wisdom = 10;
    public int charisma = 10;

    // Combat stats
    public int maxHP = 20;
    public int currentHP = 20;
    public int armor = 10;
    public int initiative = 0;
    public boolean flying = false;

    // CLASS SYSTEM FIELDS
    private int level = 1;
    private ClassType classType = null;
    private SubclassType subclassType = null;
    private int spellSlots = 100;
    private int usedSpellSlots = 0;

    // Add this field with other fields at the top
    public boolean isFlying = false;


    // =========================================================================
    // ABILITY SCORE MODIFIERS
    // =========================================================================

    /**
     * Calculate D&D ability modifier
     * Formula: (ability - 10) / 2, rounded down
     */
    public int getModifier(int abilityScore) {
        return Math.floorDiv(abilityScore - 10, 2);
    }

    public int getStrengthModifier() { return getModifier(strength); }
    public int getDexterityModifier() { return getModifier(dexterity); }
    public int getConstitutionModifier() { return getModifier(constitution); }
    public int getIntelligenceModifier() { return getModifier(intelligence); }
    public int getWisdomModifier() { return getModifier(wisdom); }
    public int getCharismaModifier() { return getModifier(charisma); }

    // =========================================================================
    // LEVEL & CLASS SYSTEM
    // =========================================================================

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = Math.max(1, Math.min(20, level)); }

    public ClassType getClassType() { return classType; }
    public void setClassType(ClassType classType) { this.classType = classType; }

    public SubclassType getSubclassType() { return subclassType; }
    public void setSubclassType(SubclassType subclassType) { this.subclassType = subclassType; }

    public int getSpellSlots() { return spellSlots; }
    public void setSpellSlots(int spellSlots) { this.spellSlots = spellSlots; }

    public int getUsedSpellSlots() { return usedSpellSlots; }
    public void setUsedSpellSlots(int usedSpellSlots) { this.usedSpellSlots = usedSpellSlots; }

    public int getRemainingSpellSlots() { return spellSlots - usedSpellSlots; }

    /**
     * Calculate spellcasting modifier based on class
     * INT for Wizard, WIS for Cleric/Druid, CHA for Sorcerer/Warlock/Bard/Paladin
     */
    public int getSpellcastingModifier() {
        if (classType == null) return 0;

        String ability = classType.getSpellcastingAbility();
        switch (ability) {
            case "INT": return getModifier(intelligence);
            case "WIS": return getModifier(wisdom);
            case "CHA": return getModifier(charisma);
            case "STR": return getModifier(strength);
            case "DEX": return getModifier(dexterity);
            case "CON": return getModifier(constitution);
            default: return 0;
        }
    }

    /**
     * Calculate spell save DC = 8 + proficiency bonus + spellcasting modifier
     */
    public int getSpellSaveDC() {
        int proficiencyBonus = (int) Math.ceil(level / 4.0) + 1; // 2 at level 1-4, 3 at 5-8, etc.
        return 8 + proficiencyBonus + getSpellcastingModifier();
    }

    /**
     * Consume spell slots
     */
    public boolean consumeSpellSlot(int cost) {
        if (getRemainingSpellSlots() >= cost) {
            usedSpellSlots += cost;
            return true;
        }
        return false;
    }

    /**
     * Rest - restore all spell slots
     */
    public void restoreSpellSlots() {
        usedSpellSlots = 0;
    }
    // Add this static method at the bottom
    /**
     * Format stat with modifier for display
     * Example: "16 (+3)"
     */
    public static String formatStatWithModifier(int stat) {
        int modifier = Math.floorDiv(stat - 10, 2);
        String modifierStr = modifier >= 0 ? "+" + modifier : String.valueOf(modifier);
        return stat + " (" + modifierStr + ")";
    }

    // =========================================================================
    // HP MANAGEMENT
    // =========================================================================

    public void takeDamage(int damage) {
        currentHP = Math.max(0, currentHP - damage);
    }

    public void heal(int amount) {
        currentHP = Math.min(maxHP, currentHP + amount);
    }

    public boolean isAlive() {
        return currentHP > 0;
    }

    public boolean isConscious() {
        return currentHP > 0;
    }


    // =========================================================================
    // DISPLAY
    // =========================================================================

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== CHARACTER STATS ===\n");

        if (classType != null) {
            sb.append("Class: ").append(classType.getDisplayName());
            if (subclassType != null) {
                sb.append(" (").append(subclassType.getDisplayName()).append(")");
            }
            sb.append(" - Level ").append(level).append("\n");
        }

        sb.append("STR: ").append(strength).append(" (").append(formatModifier(getStrengthModifier())).append(")\n");
        sb.append("DEX: ").append(dexterity).append(" (").append(formatModifier(getDexterityModifier())).append(")\n");
        sb.append("CON: ").append(constitution).append(" (").append(formatModifier(getConstitutionModifier())).append(")\n");
        sb.append("INT: ").append(intelligence).append(" (").append(formatModifier(getIntelligenceModifier())).append(")\n");
        sb.append("WIS: ").append(wisdom).append(" (").append(formatModifier(getWisdomModifier())).append(")\n");
        sb.append("CHA: ").append(charisma).append(" (").append(formatModifier(getCharismaModifier())).append(")\n");
        sb.append("\n");
        sb.append("HP: ").append(currentHP).append("/").append(maxHP).append("\n");
        sb.append("AC: ").append(armor).append("\n");
        sb.append("Initiative: ").append(formatModifier(initiative)).append("\n");

        if (classType != null && classType.isSpellcaster()) {
            sb.append("\n");
            sb.append("Spell Slots: ").append(getRemainingSpellSlots()).append("/").append(spellSlots).append("\n");
            sb.append("Spell DC: ").append(getSpellSaveDC()).append("\n");
            sb.append("Spell Modifier: ").append(formatModifier(getSpellcastingModifier())).append("\n");
        }

        return sb.toString();
    }

    private String formatModifier(int modifier) {
        return modifier >= 0 ? "+" + modifier : String.valueOf(modifier);
    }
}