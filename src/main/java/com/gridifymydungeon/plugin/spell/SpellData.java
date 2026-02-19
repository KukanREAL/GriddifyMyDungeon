package com.gridifymydungeon.plugin.spell;

import com.gridifymydungeon.plugin.spell.MonsterType;

/**
 * Complete spell data structure
 * Supports both:
 * - Base class spells (available level 1+, use classType)
 * - Subclass spells (available level 3+, use subclassType)
 * - Monster attacks (locked to a specific MonsterType, use monsterAttack constructor)
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
    private final int maxTargets;
    /** Non-null means this attack is locked to a specific monster type. */
    private final MonsterType requiredMonsterType;        // Number of separate aim cursors (1 = normal, >1 = multi-target)

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
        this(name, spellLevel, rangeGrids, pattern, areaGrids, damageDice, damageType,
                classType, minLevel, persistent, durationTurns, description, 1);
    }

    public SpellData(String name, int spellLevel, int rangeGrids, SpellPattern pattern,
                     int areaGrids, String damageDice, DamageType damageType,
                     ClassType classType, int minLevel, boolean persistent,
                     int durationTurns, String description, int maxTargets) {
        this.name = name;
        this.spellLevel = spellLevel;
        this.rangeGrids = rangeGrids;
        this.pattern = pattern;
        this.areaGrids = areaGrids;
        this.damageDice = damageDice;
        this.damageType = damageType;
        this.classType = classType;
        this.subclassType = null;
        this.minLevel = minLevel;
        this.persistent = persistent;
        this.durationTurns = durationTurns;
        this.description = description;
        this.maxTargets = Math.max(1, maxTargets);
        this.requiredMonsterType = null;
    }

    /**
     * Constructor for SUBCLASS spells (available level 3+)
     */
    public SpellData(String name, int spellLevel, int rangeGrids, SpellPattern pattern,
                     int areaGrids, String damageDice, DamageType damageType,
                     SubclassType subclassType, int minLevel, boolean persistent,
                     int durationTurns, String description) {
        this(name, spellLevel, rangeGrids, pattern, areaGrids, damageDice, damageType,
                subclassType, minLevel, persistent, durationTurns, description, 1);
    }

    public SpellData(String name, int spellLevel, int rangeGrids, SpellPattern pattern,
                     int areaGrids, String damageDice, DamageType damageType,
                     SubclassType subclassType, int minLevel, boolean persistent,
                     int durationTurns, String description, int maxTargets) {
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
        this.maxTargets = Math.max(1, maxTargets);
        this.requiredMonsterType = null;
    }

    /**
     * Constructor for MONSTER ATTACKS — locked to one MonsterType.
     * GM can only /cast this when controlling a monster of that exact type.
     */
    public SpellData(String name, int rangeGrids, SpellPattern pattern, int areaGrids,
                     String damageDice, DamageType damageType,
                     MonsterType requiredMonsterType, String description) {
        this.name          = name;
        this.spellLevel    = 0;
        this.rangeGrids    = rangeGrids;
        this.pattern       = pattern;
        this.areaGrids     = areaGrids;
        this.damageDice    = damageDice;
        this.damageType    = damageType;
        this.classType     = null;
        this.subclassType  = null;
        this.minLevel      = 1;
        this.persistent    = false;
        this.durationTurns = 0;
        this.description   = description;
        this.maxTargets    = 1;
        this.requiredMonsterType = requiredMonsterType;
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
    public int getMaxTargets() { return maxTargets; }
    public boolean isMultiTarget() { return maxTargets > 1; }
    /** Non-null = this attack is only available when controlling this monster type. */
    public MonsterType getRequiredMonsterType() { return requiredMonsterType; }
    public boolean isMonsterAttack() { return requiredMonsterType != null; }

    /**
     * Calculate spell slot cost
     * Cantrip/ability = 0, Level 1 = 1, Level 2 = 2, etc.
     */
    public int getSlotCost() {
        return spellLevel;
    }

    /**
     * A spell is a healing spell if it has DamageType.NONE and a dice expression.
     * The damageDice field doubles as the healing dice for these spells.
     */
    public boolean isHealingSpell() {
        return damageType == DamageType.NONE && damageDice != null && !damageDice.isEmpty();
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