package com.gridifymydungeon.plugin.spell;

/**
 * Every distinct monster attack-pool.
 * Multiple Hytale entities can share a type (e.g. Wolf_Black and Wolf_White → WOLF).
 * CastCommand checks monster.monsterType against SpellData.requiredMonsterType
 * so a Bat can never cast Goblin Javelin, etc.
 */
public enum MonsterType {
    // Goblins
    GOBLIN, GOBLIN_LOBBER, GOBLIN_SHAMAN, GOBLIN_BOSS, GOBLIN_OGRE,
    // Trorks
    TRORK, TRORK_SHAMAN, TRORK_CHIEFTAIN,
    // Undead humanoid
    SKELETON, SKELETON_ARCHER, SKELETON_MAGE, SKELETON_KNIGHT,
    ZOMBIE, ZOMBIE_WEREWOLF,
    GHOUL, WRAITH, SHADOW_KNIGHT,
    // Humanoid factions
    OUTLANDER, OUTLANDER_BERSERKER, OUTLANDER_PRIEST, OUTLANDER_SORCERER, OUTLANDER_HUNTER,
    SAURIAN, SAURIAN_ROGUE, SAURIAN_WARRIOR,
    SLOTHIAN, SLOTHIAN_MONK, SLOTHIAN_ELDER,
    // Constructs / elementals
    GOLEM, SPIRIT, VOID_CREATURE,
    SPARK_LIVING, TORNADO,
    // Insects / arthropods
    SCARAK, SCARAK_BROODMOTHER,
    // Plant/nature
    BRAMBLEKIN, BRAMBLEKIN_SHAMAN,
    // Beasts
    BAT, BEAR, WOLF, BOAR, CROCODILE, SPIDER, SNAKE, SCORPION,
    BIG_CAT, RAPTOR, REX, PTERODACTYL, SHARK, MOOSE, WARTHOG, YETI, WEREWOLF,
    FERAN, FERAN_WINDWALKER, TRILLODON, TOAD_RHINO,
    SNAPJAW, FEN_STALKER, SLUG_MAGMA, EMBER_WULF,
    // Dragons
    DRAGON_FIRE, DRAGON_FROST, DRAGON_VOID;
}