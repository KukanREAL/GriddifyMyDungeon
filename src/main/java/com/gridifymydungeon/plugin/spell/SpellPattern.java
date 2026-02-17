package com.gridifymydungeon.plugin.spell;

/**
 * Spell area pattern types
 */
public enum SpellPattern {
    SINGLE_TARGET,   // Single target (touch, ranged attack)
    CONE,           // Cone emanating from caster
    LINE,           // Straight line
    SPHERE,         // Circular area around a point
    CYLINDER,       // Vertical cylinder
    CUBE,           // Square area
    SELF,           // Affects only caster
    AURA,           // Radius around caster
    CHAIN,          // Jumps between targets
    WALL            // Linear barrier
}