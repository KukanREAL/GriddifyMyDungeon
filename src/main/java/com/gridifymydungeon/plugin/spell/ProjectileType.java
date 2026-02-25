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
    ICE_STORM_BOLT       ("Frost_Bolt",     null, 0.9f, null,                0f),

    // ── LINE spells ──────────────────────────────────────────────────────────
    LIGHTNING_BOLT       ("Lightning_Bolt", null, 1.4f, "Explosion_Big",     0.7f),
    LIGHTNING_ARROW      ("Lightning_Bolt", null, 1.0f, "Explosion_Big",     0.5f),
    CHAIN_LIGHTNING      ("Lightning_Bolt", null, 1.0f, "Explosion_Big",     0.5f),

    // ── SPHERE spells ─────────────────────────────────────────────────────────
    SHATTER              ("Thunder_Bolt",   null, 2.2f, "Explosion_Big",     0.9f),
    MASS_PSYCHIC_BLAST   ("Arcane_Bolt",    null, 2.5f, "Explosion_Big",     1.0f),
    DELAYED_BLAST_FIREBALL("Fire_Bolt",     null, 3.0f, "Explosion_Big",     1.5f),
    METEOR_SWARM         ("Fire_Bolt",      null, 4.0f, "Explosion_Big",     2.0f),

    // ── Radiant single-target ─────────────────────────────────────────────────
    SACRED_FLAME         ("Arcane_Bolt",    null, 1.0f, "Sparkle_Explosion", 0.5f),
    DIVINE_SMITE         ("Arcane_Bolt",    null, 1.2f, "Sparkle_Explosion", 0.6f),
    SPIRITUAL_WEAPON     ("Arcane_Bolt",    null, 1.3f, "Sparkle_Explosion", 0.5f),
    RADIANT_SUN_BOLT     ("Arcane_Bolt",    null, 1.1f, "Sparkle_Explosion", 0.5f),
    PRISMATIC_SPRAY      ("Arcane_Bolt",    null, 2.0f, "Explosion_Big",     1.0f),

    // ── Arcane single-target ──────────────────────────────────────────────────
    ELDRITCH_BLAST       ("Arcane_Bolt",    null, 1.2f, "Explosion_Big",     0.5f),
    CHAOS_BOLT           ("Arcane_Bolt",    null, 1.1f, "Explosion_Big",     0.5f),
    ARCANE_PULSE         ("Arcane_Bolt",    null, 1.0f, "Sparkle_Explosion", 0.4f),

    // ── Melee flash (instant, near-zero travel) ───────────────────────────────
    MELEE_FLASH          ("Arcane_Bolt",    null, 0.7f, "Sparkle_Explosion", 0.3f),

    // ── Cone variants ────────────────────────────────────────────────────────
    ARCANE_CONE          ("Arcane_Bolt",    null, 0.9f, "Sparkle_Explosion", 0.3f),

    // ── Electric explosion (Zap model) ───────────────────────────────────────
    ZAP                  ("Zap",             null, 1.0f, "Explosion_Big",     0.6f),
    ZAP_LARGE            ("Zap",             null, 2.0f, "Explosion_Big",     1.0f),

    // ── Buff / Mark (Mark model — 2x2 flat tile, ground-level) ───────────────
    MARK_BUFF            ("Mark",            null, 1.0f, null,                0f),

    // ── Dark / Crimson spells ─────────────────────────────────────────────────
    CRIMSON_BOLT         ("Crimson_Spell",   null, 1.0f, "Explosion_Big",     0.5f),
    CRIMSON_BLAST        ("Crimson_Spell",   null, 1.5f, "Explosion_Big",     0.8f);

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
        switch (spellName.toLowerCase().replace(" ", "_").replace("-", "_").replace("'", "")) {

            // ── Fire ────────────────────────────────────────────────────────
            case "fire_bolt":
            case "twinned_fire_bolt":            return FIRE_BOLT;
            case "produce_flame":                return PRODUCE_FLAME;
            case "fireball":
            case "careful_fireball":             return FIREBALL;
            case "quickened_fireball":           return QUICKENED_FIREBALL;
            case "delayed_blast_fireball":       return DELAYED_BLAST_FIREBALL;
            case "meteor_swarm":                 return METEOR_SWARM;
            case "burning_hands":                return BURNING_HANDS;

            // ── Frost ───────────────────────────────────────────────────────
            case "cone_of_cold":
            case "dragonfrost_breath":
            case "dragonfrost_claw":             return CONE_OF_COLD;

            // ── Lightning ───────────────────────────────────────────────────
            case "lightning_bolt":
            case "empowered_lightning_bolt":     return LIGHTNING_BOLT;
            case "lightning_arrow":              return LIGHTNING_ARROW;
            case "chain_lightning":              return CHAIN_LIGHTNING;

            // ── Thunder ─────────────────────────────────────────────────────
            case "shatter":                      return SHATTER;

            // ── Arcane ──────────────────────────────────────────────────────
            case "magic_missile":                return MAGIC_MISSILE;
            case "arcane_barrage":               return ARCANE_BARRAGE;
            case "chaos_bolt":                   return CHAOS_BOLT;
            case "mass_psychic_blast":           return MASS_PSYCHIC_BLAST;
            case "eldritch_blast":               return ELDRITCH_BLAST;
            case "counterspell":
            case "dispel_magic":
            case "banishment":
            case "projected_ward":               return ARCANE_PULSE;
            case "globe_of_invulnerability":
            case "explosive_runes":              return ARCANE_PULSE;

            // ── Radiant ─────────────────────────────────────────────────────
            case "sacred_flame":                 return SACRED_FLAME;
            case "divine_smite":                 return DIVINE_SMITE;
            case "spiritual_weapon":             return SPIRITUAL_WEAPON;
            case "divine_strike":                return DIVINE_SMITE;
            case "radiant_sun_bolt":             return RADIANT_SUN_BOLT;
            case "prismatic_spray":              return PRISMATIC_SPRAY;

            // ── Melee flash (no actual projectile, just impact flash) ────────
            case "power_strike":
            case "cleave":
            case "whirlwind_attack":
            case "execute":
            case "flurry_of_blows":
            case "quivering_palm":
            case "stunning_strike":
            case "sneak_attack":
            case "divine_smite_melee":           return MELEE_FLASH;

            // ── Melee cone ──────────────────────────────────────────────────
            case "battle_cry":
            case "detect_thoughts":              return ARCANE_CONE;

            // ── Buff effects: Mark model (flat glowing tile under target) ────────
            case "hex":
            case "hunters_mark":
            case "bless":
            case "bardic_inspiration":
            case "vicious_mockery":
            case "power_word_stun":

                // ── Teleport self: Mark under caster ─────────────────────────────────
            case "wind_dash":
            case "shadow_dash":
            case "shadow_step":            return MARK_BUFF;

            // ── Dark / Crimson spells ─────────────────────────────────────────────
            case "soul_cage":
            case "hunger_of_hadar":
            case "arms_of_hadar":          return CRIMSON_BLAST;

            // ── Heals — no projectile, handled separately ─────────────────────────
            case "lay_on_hands":
            case "cure_wounds":
            case "heal":
            case "mass_cure_wounds":
            case "hail_of_thorns":
            case "foresight":
            case "true_seeing":
            case "scrying":                return null; // heals/passives handled separately

            // ── Ice_Storm / Chromatic_Orb handled elsewhere ─────────────────
            default:                             return null;
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