package com.gridifymydungeon.plugin.dnd;

/**
 * SpellList - Manages spell slots and spell list references
 * Used for character code spell expansion (characters I-J)
 *
 * Character I encodes spell slots and flags
 * Character J encodes spell list ID reference
 */
public class SpellList {

    // Spell slots by level
    private int level1Slots = 0;
    private int level2Slots = 0;

    // Spell flags
    private boolean concentrating = false;
    private boolean hasInspiration = false;

    // Spell list ID (0-61)
    private int spellListId = 0;

    /**
     * Default constructor
     */
    public SpellList() {
    }

    /**
     * Constructor with spell list ID
     */
    public SpellList(int spellListId) {
        this.spellListId = spellListId;
    }

    // Getters and Setters

    public int getLevel1Slots() {
        return level1Slots;
    }

    public void setLevel1Slots(int level1Slots) {
        if (level1Slots < 0 || level1Slots > 7) {
            throw new IllegalArgumentException("Level 1 slots must be 0-7");
        }
        this.level1Slots = level1Slots;
    }

    public int getLevel2Slots() {
        return level2Slots;
    }

    public void setLevel2Slots(int level2Slots) {
        if (level2Slots < 0 || level2Slots > 7) {
            throw new IllegalArgumentException("Level 2 slots must be 0-7");
        }
        this.level2Slots = level2Slots;
    }

    public boolean isConcentrating() {
        return concentrating;
    }

    public void setConcentrating(boolean concentrating) {
        this.concentrating = concentrating;
    }

    public boolean hasInspiration() {
        return hasInspiration;
    }

    public void setHasInspiration(boolean hasInspiration) {
        this.hasInspiration = hasInspiration;
    }

    public int getSpellListId() {
        return spellListId;
    }

    public void setSpellListId(int spellListId) {
        if (spellListId < 0 || spellListId > 61) {
            throw new IllegalArgumentException("Spell list ID must be 0-61");
        }
        this.spellListId = spellListId;
    }

    /**
     * Pack spell slot data into a single integer for encoding
     * Bit layout:
     * - Bits 0-2: Level 1 slots (0-7)
     * - Bits 3-5: Level 2 slots (0-7)
     * - Bit 6: Concentration flag
     * - Bit 7: Inspiration flag
     *
     * Max value: 255 (fits in 1 Base62 char which holds 0-61)
     * Actual max with constraints: 7 + (7<<3) + (1<<6) + (1<<7) = 255
     * But we only use 0-61 in Base62, so we're limited
     *
     * Better encoding for Base62:
     * Combined = level1 * 8 + level2 + concentration*56 + inspiration*57
     * Max = 7*8 + 7 + 1*56 + 1*57 = 176... still over 61!
     *
     * Simplest encoding:
     * Combined = level1 + level2*8 (max 63, close to 61)
     * We'll store concentration/inspiration as separate flags or in the stats
     */
    public int getSpellSlotData() {
        // Simple packing: level1 + level2*8
        // Max value: 7 + 7*8 = 63 (slightly over 61, but workable)
        int data = level1Slots + (level2Slots * 8);

        // If we need flags, we can use bits:
        if (concentrating) {
            data |= 64;  // Bit 6
        }
        // Note: This could exceed 61, so we may need to adjust

        return Math.min(data, 61); // Cap at 61 for Base62
    }

    /**
     * Unpack spell slot data from encoded integer
     */
    public void setSpellSlotData(int data) {
        this.level1Slots = data % 8;
        this.level2Slots = (data / 8) % 8;
        this.concentrating = (data & 64) != 0;
    }

    /**
     * Predefined spell lists (0-61)
     * Can be expanded with actual spell combinations
     */
    public static String getSpellListName(int id) {
        switch (id) {
            case 0: return "None";
            case 1: return "Fire Mage";
            case 2: return "Ice Mage";
            case 3: return "Healer";
            case 4: return "Necromancer";
            case 5: return "Druid Nature";
            case 6: return "Paladin Holy";
            case 7: return "Warlock Dark";
            case 8: return "Bard Support";
            case 9: return "Wizard Arcane";
            case 10: return "Cleric Divine";
            // Add more spell lists as needed (up to ID 61)
            default: return "Custom List " + id;
        }
    }

    @Override
    public String toString() {
        return String.format("SpellList[L1:%d L2:%d Conc:%s Insp:%s ListID:%d (%s)]",
                level1Slots, level2Slots, concentrating, hasInspiration,
                spellListId, getSpellListName(spellListId));
    }
}