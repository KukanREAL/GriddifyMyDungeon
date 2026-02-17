package com.gridifymydungeon.plugin.spell;

/**
 * Complete spell data structure
 * Supports both:
 * - Base class spells (available level 1+, use classType)
 * - Subclass spells (available level 3+, use subclassType)
 */
public class SpellData {
    private final String name;
    private final int spellLevel;        // 0 = cantrip/ability, 1-9 = spell level
    private final int rangeGrids;        // Range in grids (1 grid = 5ft)
    private final SpellPattern pattern;
    private final int areaGrids;         // Area size in grids (radius/length)
    private final String damageDice;     // e.g., "3d6" or null
    private final DamageType damageType;

    // NEW: Support both class and subclass
    private final ClassType classType;       // For base class spells (level 1+)
    private final SubclassType subclassType; // For subclass spells (level 3+)

    private final int minLevel;          // Character level required
    private final boolean persistent;    // Does it last multiple turns?
    private final int durationTurns;     // How many turns (0 = instant)
    private final String description;

    // TODO FUTURE: Add saving throw type (DEX/WIS/CON/etc.)
    // TODO FUTURE: Add status effects (frightened, paralyzed, etc.)
    // TODO FUTURE: Add concentration requirement
    // TODO FUTURE: Add targeting restrictions (allies only, enemies only, etc.)

    /**
     * Constructor for BASE CLASS spells (available level 1+)
     */
    public SpellData(String name, int spellLevel, int rangeGrids, SpellPattern pattern,
                     int areaGrids, String damageDice, DamageType damageType,
                     ClassType classType, int minLevel, boolean persistent,
                     int durationTurns, String description) {
        this.name = name;
        this.spellLevel = spellLevel;
        this.rangeGrids = rangeGrids;
        this.pattern = pattern;
        this.areaGrids = areaGrids;
        this.damageDice = damageDice;
        this.damageType = damageType;
        this.classType = classType;
        this.subclassType = null; // Base class spells don't have subclass
        this.minLevel = minLevel;
        this.persistent = persistent;
        this.durationTurns = durationTurns;
        this.description = description;
    }

    /**
     * Constructor for SUBCLASS spells (available level 3+)
     */
    public SpellData(String name, int spellLevel, int rangeGrids, SpellPattern pattern,
                     int areaGrids, String damageDice, DamageType damageType,
                     SubclassType subclassType, int minLevel, boolean persistent,
                     int durationTurns, String description) {
        this.name = name;
        this.spellLevel = spellLevel;
        this.rangeGrids = rangeGrids;
        this.pattern = pattern;
        this.areaGrids = areaGrids;
        this.damageDice = damageDice;
        this.damageType = damageType;
        this.classType = subclassType != null ? subclassType.getParentClass() : null;
        this.subclassType = subclassType;
        this.minLevel = minLevel;
        this.persistent = persistent;
        this.durationTurns = durationTurns;
        this.description = description;
    }

    // Getters
    public String getName() { return name; }
    public int getSpellLevel() { return spellLevel; }
    public int getRangeGrids() { return rangeGrids; }
    public SpellPattern getPattern() { return pattern; }
    public int getAreaGrids() { return areaGrids; }
    public String getDamageDice() { return damageDice; }
    public DamageType getDamageType() { return damageType; }

    public ClassType getClassType() { return classType; }
    public SubclassType getSubclass() { return subclassType; } // Keep old name for compatibility
    public SubclassType getSubclassType() { return subclassType; }

    public int getMinLevel() { return minLevel; }
    public boolean isPersistent() { return persistent; }
    public int getDurationTurns() { return durationTurns; }
    public String getDescription() { return description; }

    /**
     * Calculate spell slot cost
     * Cantrip/ability = 0, Level 1 = 1, Level 2 = 2, etc.
     */
    public int getSlotCost() {
        return spellLevel;
    }

    /**
     * Check if this is a base class spell (no subclass required)
     */
    public boolean isBaseClassSpell() {
        return subclassType == null && classType != null;
    }

    /**
     * Check if this is a subclass spell (subclass required)
     */
    public boolean isSubclassSpell() {
        return subclassType != null;
    }
}