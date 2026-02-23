package com.gridifymydungeon.plugin.spell;

import javax.annotation.Nullable;

/**
 * Defines the visual for each PROJECTILE spell.
 * Non-projectile spells (Entangle, Moonbeam, Sunbeam, Thunderwave, Ice_Storm)
 * are handled by SpellVisualEffect instead.
 *
 * entityScale          = size of the spawned projectile entity
 * arrivalParticle      = particle burst on impact (null = none)
 * arrivalParticleScale = particle scale (independent of entity scale)
 */
public enum ProjectileType {

    // ── SINGLE_TARGET ────────────────────────────────────────────────────────
    FIRE_BOLT            ("Fire_Bolt",      null, 1.0f, "Explosion_Big",     0.5f),
    PRODUCE_FLAME        ("Fire_Bolt",      null, 0.8f, "Explosion_Big",     0.4f),  // 0.2 smaller than Fire_Bolt
    MAGIC_MISSILE        ("Magic_Bolt",     null, 1.0f, "Sparkle_Explosion", 0.4f),
    ARCANE_BARRAGE       ("Arcane_Bolt",    null, 1.0f, "Explosion_Big",     0.3f),

    // ── Chromatic Orb variants — model chosen at runtime via forElement() ──
    CHROMATIC_ACID       ("Acid_Bolt",      null, 1.0f, "Explosion_Big",     0.5f),
    CHROMATIC_FIRE       ("Fire_Bolt",      null, 1.0f, "Explosion_Big",     0.5f),
    CHROMATIC_COLD       ("Frost_Bolt",     null, 1.0f, "IceBall",           0.5f),
    CHROMATIC_LIGHTNING  ("Lightning_Bolt", null, 1.0f, "Explosion_Big",     0.5f),
    CHROMATIC_POISON     ("Poison_Bolt",    null, 1.0f, "Explosion_Big",     0.5f),
    CHROMATIC_THUNDER    ("Thunder_Bolt",   null, 1.0f, "Explosion_Big",     0.5f),

    // ── SPHERE ───────────────────────────────────────────────────────────────
    FIREBALL             ("Fire_Bolt",      null, 2.5f, "Explosion_Big",     1.2f),
    QUICKENED_FIREBALL   ("Fire_Bolt",      null, 2.0f, "Explosion_Big",     1.0f),  // smaller than Fireball

    // ── CONE — dense fan of streaks ──────────────────────────────────────────
    BURNING_HANDS        ("Fire_Bolt",      null, 1.3f, "Fire_Burst",        0.6f),
    CONE_OF_COLD         ("Frost_Bolt",     null, 1.3f, "IceBall",           0.5f),

    // ── ICE_STORM falling bolts (spawned by SpellVisualEffect, not projectile) ─
    ICE_STORM_BOLT       ("Frost_Bolt",     null, 0.9f, null,                0f);

    // ────────────────────────────────────────────────────────────────────────

    public final String  modelAssetId;
    @Nullable public final String  animSetId;
    public final float   entityScale;
    @Nullable public final String  arrivalParticle;
    public final float   arrivalParticleScale;

    ProjectileType(String model, String anim, float scale, String particle, float pScale) {
        this.modelAssetId         = model;
        this.animSetId            = anim;
        this.entityScale          = scale;
        this.arrivalParticle      = particle;
        this.arrivalParticleScale = pScale;
    }

    /** Map a spell name to its ProjectileType (null = handled by SpellVisualEffect or unknown). */
    @Nullable
    public static ProjectileType forSpell(String spellName) {
        if (spellName == null) return null;
        switch (spellName.toLowerCase().replace(" ", "_").replace("-", "_")) {
            case "fire_bolt":
            case "twinned_fire_bolt":         return FIRE_BOLT;
            case "produce_flame":             return PRODUCE_FLAME;
            case "magic_missile":             return MAGIC_MISSILE;
            case "arcane_barrage":            return ARCANE_BARRAGE;
            case "fireball":                  return FIREBALL;
            case "quickened_fireball":        return QUICKENED_FIREBALL;
            case "burning_hands":             return BURNING_HANDS;
            case "cone_of_cold":
            case "dragonfrost_breath":
            case "dragonfrost_claw":          return CONE_OF_COLD;
            // Ice_Storm and Chromatic_Orb are NOT returned here —
            // Ice_Storm uses SpellVisualEffect.spawnIceStorm(),
            // Chromatic_Orb uses forElement() below.
            default:                          return null;
        }
    }

    /** For Chromatic_Orb: pick the projectile type by chosen element string. */
    @Nullable
    public static ProjectileType forElement(String element) {
        if (element == null) return null;
        switch (element.toLowerCase()) {
            case "acid":      return CHROMATIC_ACID;
            case "fire":      return CHROMATIC_FIRE;
            case "cold":      return CHROMATIC_COLD;
            case "lightning": return CHROMATIC_LIGHTNING;
            case "poison":    return CHROMATIC_POISON;
            case "thunder":   return CHROMATIC_THUNDER;
            default:          return null;
        }
    }
}