package com.gridifymydungeon.plugin.spell;

import javax.annotation.Nullable;

/**
 * Defines the visual for each spell's projectile.
 *
 * entityScale         = size of the spawned entity  (1.0 normal, 1.3 cone streaks)
 * arrivalParticleScale = arrival particle stays full-size regardless of entityScale
 *
 * To add a new spell: add an enum entry + one line in forSpell().
 */
public enum ProjectileType {

    // SINGLE_TARGET
    FIRE_BOLT        ("Fire_Bolt", null, 1.0f, "Explosion_Big",    0.5f),
    MAGIC_MISSILE    ("Fire_Bolt", null, 1.0f, "Sparkle_Explosion", 0.4f),
    ARCANE_BARRAGE   ("Fire_Bolt", null, 1.0f, "Explosion_Big",    0.3f),

    // CONE — 1.3 scale streaks, full-size arrival particle per cell
    BURNING_HANDS    ("Fire_Bolt", null, 1.3f, "Fire_Burst",       0.6f);

    // -----------------------------------------------------------------------

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

    @Nullable
    public static ProjectileType forSpell(String spellName) {
        if (spellName == null) return null;
        switch (spellName.toLowerCase().replace(" ", "_").replace("-", "_")) {
            case "fire_bolt":          return FIRE_BOLT;
            case "twinned_fire_bolt":  return FIRE_BOLT;
            case "magic_missile":      return MAGIC_MISSILE;
            case "arcane_barrage":     return ARCANE_BARRAGE;
            case "burning_hands":      return BURNING_HANDS;
            default:                   return null;
        }
    }
}