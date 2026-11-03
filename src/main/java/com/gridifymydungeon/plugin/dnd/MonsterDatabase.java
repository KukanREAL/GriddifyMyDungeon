package com.gridifymydungeon.plugin.dnd;

import com.gridifymydungeon.plugin.spell.MonsterType;

import java.util.HashMap;
import java.util.Map;

/**
 * D&D 5e stats for every combat-relevant Hytale entity.
 * HP is the average of the hit dice roll (5e Monster Manual / Basic Rules).
 *
 * Each entry carries a MonsterType so CastCommand can validate which
 * monster-specific attacks the GM is allowed to use.
 */
public class MonsterDatabase {

    public static class MonsterStats {
        public final int    maxHP;
        public final String hitDice;
        public final int    ac;
        public final int    speed;
        public final int    str, dex, con, intel, wis, cha;
        public final int    cr10x;    // CR × 10 to avoid floats (1/4 → 2, 1/2 → 5, 1 → 10)
        public final String type;
        public final boolean flying;
        public final int    moves;    // grid squares per turn = speed / 10, min 3
        public final MonsterType monsterType;

        public MonsterStats(int maxHP, String hitDice, int ac, int speed,
                            int str, int dex, int con, int intel, int wis, int cha,
                            int cr10x, String type, boolean flying, MonsterType monsterType) {
            this.maxHP  = maxHP;
            this.hitDice = hitDice;
            this.ac     = ac;
            this.speed  = speed;
            this.str    = str;  this.dex  = dex;  this.con   = con;
            this.intel  = intel; this.wis = wis;  this.cha   = cha;
            this.cr10x  = cr10x;
            this.type   = type;
            this.flying = flying;
            this.moves  = Math.max(3, speed / 10);
            this.monsterType = monsterType;
        }

        public void applyTo(CharacterStats s) {
            s.maxHP        = maxHP;
            s.currentHP    = maxHP;
            s.armor        = ac;
            s.strength     = str;
            s.dexterity    = dex;
            s.constitution = con;
            s.intelligence = intel;
            s.wisdom       = wis;
            s.charisma     = cha;
            s.isFlying     = flying;
        }
    }

    private static final Map<String, MonsterStats> DB = new HashMap<>();

    static {
        // helper alias so lines stay short
        // add(name, hp, dice, ac, speed, str,dex,con,int,wis,cha, cr10x, type, flying, MonsterType)

        // ── GOBLINS ──────────────────────────────────────────────────────────
        add("Goblin",                  7,"2d6",  15,30,  8,14,10, 8, 8, 8,  2,"Humanoid",false, MonsterType.GOBLIN);
        add("Goblin_Scrapper",         7,"2d6",  13,30,  8,14,10, 8, 8, 8,  2,"Humanoid",false, MonsterType.GOBLIN);
        add("Goblin_Miner",            7,"2d6",  12,30,  8,14,10, 8, 8, 8,  2,"Humanoid",false, MonsterType.GOBLIN);
        add("Goblin_Thief",           10,"3d6",  14,40,  8,16,10,10, 8,10,  2,"Humanoid",false, MonsterType.GOBLIN);
        add("Goblin_Lobber",           7,"2d6",  13,30,  8,14,10, 8, 8, 8,  2,"Humanoid",false, MonsterType.GOBLIN_LOBBER);
        add("Goblin_Hermit",           9,"2d6",  12,30,  8,12,12,10,14, 8,  2,"Humanoid",false, MonsterType.GOBLIN_SHAMAN);
        add("Goblin_Boss",            21,"6d6",  17,30, 10,14,10,10, 8,14, 10,"Humanoid",false, MonsterType.GOBLIN_BOSS);
        add("Goblin_Duke",            45,"7d8",  16,30, 14,14,14,10,10,12, 20,"Humanoid",false, MonsterType.GOBLIN_BOSS);
        add("Goblin_Duke_Large",      59,"9d8",  16,30, 16,14,14,10,10,12, 20,"Humanoid",false, MonsterType.GOBLIN_BOSS);
        add("Goblin_Ogre",            59,"7d10", 11,30, 19, 8,16, 5, 7, 7, 40,"Giant",   false, MonsterType.GOBLIN_OGRE);

        // ── TRORKS ───────────────────────────────────────────────────────────
        add("Trork",                  15,"2d8+6",13,30, 16,12,16, 7,11,10, 10,"Humanoid",false, MonsterType.TRORK);
        add("Trork_Peon",              9,"2d8",  11,30, 14,10,14, 7,10, 8,  2,"Humanoid",false, MonsterType.TRORK);
        add("Trork_Warrior",          15,"2d8+6",13,30, 16,12,16, 7,11,10, 10,"Humanoid",false, MonsterType.TRORK);
        add("Trork_Brawler",          32,"5d8+10",14,30,18,10,14, 8,10, 9, 20,"Humanoid",false, MonsterType.TRORK);
        add("Trork_Guard",            19,"3d8+6",14,30, 16,10,14, 8,10, 9, 10,"Humanoid",false, MonsterType.TRORK);
        add("Trork_Hunter",           16,"3d8+3",13,30, 14,14,12,10,12, 8, 10,"Humanoid",false, MonsterType.TRORK);
        add("Trork_Mauler",           45,"6d10+12",15,30,20,10,14, 8,10, 9,30,"Humanoid",false, MonsterType.TRORK);
        add("Trork_Sentry",           19,"3d8+6",16,30, 14,10,14, 8,11, 9, 10,"Humanoid",false, MonsterType.TRORK);
        add("Trork_Shaman",           27,"5d8+5",11,30, 12,10,12,10,14,10, 10,"Humanoid",false, MonsterType.TRORK_SHAMAN);
        add("Trork_Chieftain",        65,"10d8+20",15,30,20,12,14,9,11,11, 40,"Humanoid",false, MonsterType.TRORK_CHIEFTAIN);
        add("Trork_Christmas",        15,"2d8+6",13,30, 16,12,16, 7,11,10, 10,"Humanoid",false, MonsterType.TRORK);
        add("Trork_Doctor_Witch",     27,"5d8+5",11,30, 12,10,12,10,14,10, 10,"Humanoid",false, MonsterType.TRORK_SHAMAN);

        // ── SKELETONS ─────────────────────────────────────────────────────────
        add("Skeleton",               13,"2d8+4",13,30, 10,14,15, 6, 8, 5,  2,"Undead",  false, MonsterType.SKELETON);
        add("Skeleton_Fighter",       26,"4d8+8",15,30, 14,12,14, 6, 8, 5, 10,"Undead",  false, MonsterType.SKELETON);
        add("Skeleton_Soldier",       26,"4d8+8",14,30, 14,12,14, 6, 8, 5, 10,"Undead",  false, MonsterType.SKELETON);
        add("Skeleton_Scout",         13,"2d8+4",13,30,  8,16,15, 6, 8, 5,  2,"Undead",  false, MonsterType.SKELETON_ARCHER);
        add("Skeleton_Archer",        13,"2d8+4",13,30,  8,16,15, 6, 8, 5,  2,"Undead",  false, MonsterType.SKELETON_ARCHER);
        add("Skeleton_Ranger",        13,"2d8+4",13,30,  8,16,15, 6,10, 5, 10,"Undead",  false, MonsterType.SKELETON_ARCHER);
        add("Skeleton_Knight",        52,"8d8+16",17,30,16,12,15, 6, 8, 5, 30,"Undead",  false, MonsterType.SKELETON_KNIGHT);
        add("Skeleton_Mage",          26,"4d8+8",12,30,  8,14,14,14, 8, 5, 20,"Undead",  false, MonsterType.SKELETON_MAGE);
        add("Skeleton_Archmage",      45,"7d8+14",12,30, 8,14,14,18,10, 5, 40,"Undead",  false, MonsterType.SKELETON_MAGE);
        add("Skeleton_Frost_Archer",  13,"2d8+4",13,30,  8,16,15, 6, 8, 5,  2,"Undead",  false, MonsterType.SKELETON_ARCHER);
        add("Skeleton_Frost_Archmage",45,"7d8+14",12,30, 8,14,14,18,10, 5, 40,"Undead",  false, MonsterType.SKELETON_MAGE);
        add("Skeleton_Frost_Fighter", 26,"4d8+8",15,30, 14,12,14, 6, 8, 5, 10,"Undead",  false, MonsterType.SKELETON);
        add("Skeleton_Frost_Knight",  52,"8d8+16",17,30,16,12,15, 6, 8, 5, 30,"Undead",  false, MonsterType.SKELETON_KNIGHT);
        add("Skeleton_Frost_Mage",    26,"4d8+8",12,30,  8,14,14,14, 8, 5, 20,"Undead",  false, MonsterType.SKELETON_MAGE);
        add("Skeleton_Frost_Ranger",  13,"2d8+4",13,30,  8,16,15, 6,10, 5,  2,"Undead",  false, MonsterType.SKELETON_ARCHER);
        add("Skeleton_Frost_Scout",   13,"2d8+4",13,30,  8,16,15, 6, 8, 5,  2,"Undead",  false, MonsterType.SKELETON_ARCHER);
        add("Skeleton_Frost_Soldier", 26,"4d8+8",14,30, 14,12,14, 6, 8, 5, 10,"Undead",  false, MonsterType.SKELETON);
        add("Skeleton_Burnt_Alchemist",26,"4d8+8",12,30, 8,14,14,14, 8, 5, 20,"Undead",  false, MonsterType.SKELETON_MAGE);
        add("Skeleton_Burnt_Archer",  13,"2d8+4",13,30,  8,16,15, 6, 8, 5,  2,"Undead",  false, MonsterType.SKELETON_ARCHER);
        add("Skeleton_Burnt_Gunner",  13,"2d8+4",13,30,  8,16,15, 6, 8, 5,  2,"Undead",  false, MonsterType.SKELETON_ARCHER);
        add("Skeleton_Burnt_Knight",  52,"8d8+16",17,30,16,12,15, 6, 8, 5, 30,"Undead",  false, MonsterType.SKELETON_KNIGHT);
        add("Skeleton_Burnt_Lancer",  26,"4d8+8",14,30, 14,12,14, 6, 8, 5, 10,"Undead",  false, MonsterType.SKELETON);
        add("Skeleton_Burnt_Praetorian",52,"8d8+16",18,30,18,12,16,6, 8, 5, 40,"Undead", false, MonsterType.SKELETON_KNIGHT);
        add("Skeleton_Burnt_Soldier", 26,"4d8+8",14,30, 14,12,14, 6, 8, 5, 10,"Undead",  false, MonsterType.SKELETON);
        add("Skeleton_Burnt_Wizard",  26,"4d8+8",12,30,  8,14,14,14, 8, 5, 20,"Undead",  false, MonsterType.SKELETON_MAGE);
        add("Skeleton_Sand_Archer",   13,"2d8+4",13,30,  8,16,15, 6, 8, 5,  2,"Undead",  false, MonsterType.SKELETON_ARCHER);
        add("Skeleton_Sand_Archmage", 45,"7d8+14",12,30, 8,14,14,18,10, 5, 40,"Undead",  false, MonsterType.SKELETON_MAGE);
        add("Skeleton_Sand_Assassin", 26,"4d8+8",14,30, 10,18,14, 6, 8, 5, 20,"Undead",  false, MonsterType.SKELETON_ARCHER);
        add("Skeleton_Sand_Guard",    19,"3d8+6",15,30, 14,12,14, 6, 8, 5, 10,"Undead",  false, MonsterType.SKELETON);
        add("Skeleton_Sand_Mage",     26,"4d8+8",12,30,  8,14,14,14, 8, 5, 20,"Undead",  false, MonsterType.SKELETON_MAGE);
        add("Skeleton_Sand_Ranger",   13,"2d8+4",13,30,  8,16,15, 6,10, 5,  2,"Undead",  false, MonsterType.SKELETON_ARCHER);
        add("Skeleton_Sand_Scout",    13,"2d8+4",13,30,  8,16,15, 6, 8, 5,  2,"Undead",  false, MonsterType.SKELETON_ARCHER);
        add("Skeleton_Sand_Soldier",  26,"4d8+8",14,30, 14,12,14, 6, 8, 5, 10,"Undead",  false, MonsterType.SKELETON);
        add("Skeleton_Incandescent_Fighter",32,"5d8+10",15,30,16,12,14,6,8,5,20,"Undead", false, MonsterType.SKELETON);
        add("Skeleton_Incandescent_Footman",19,"3d8+6",14,30,14,10,14,6,8,5,10,"Undead",  false, MonsterType.SKELETON);
        add("Skeleton_Incandescent_Head",    13,"2d8+4",10,30, 6,10,14,6,8,5, 2,"Undead", false, MonsterType.SKELETON);
        add("Skeleton_Incandescent_Mage",    26,"4d8+8",12,30, 8,14,14,14,8,5,20,"Undead",false, MonsterType.SKELETON_MAGE);
        add("Skeleton_Pirate_Captain",45,"7d8+14",16,30,16,14,14,10,8,14,30,"Undead",     false, MonsterType.SKELETON_KNIGHT);
        add("Skeleton_Pirate_Gunner", 19,"3d8+6",13,30,  8,16,12,10, 8, 8, 10,"Undead",  false, MonsterType.SKELETON_ARCHER);
        add("Skeleton_Pirate_Striker",19,"3d8+6",13,30, 12,16,12, 8, 8, 8, 10,"Undead",  false, MonsterType.SKELETON);
        add("Horse_Skeleton",         19,"3d10+3",13,60,15,10,12, 2,12, 5,  5,"Undead",  false, MonsterType.SKELETON);
        add("Horse_Skeleton_Armored", 26,"4d10+8",16,60,16,10,14, 2,12, 5, 10,"Undead",  false, MonsterType.SKELETON_KNIGHT);

        // ── ZOMBIES ───────────────────────────────────────────────────────────
        add("Zombie",                 22,"3d8+9", 8,20, 13, 6,16, 3, 6, 5,  2,"Undead",  false, MonsterType.ZOMBIE);
        add("Zombie_Aberrant",        32,"4d10+8",9,20, 16, 6,16, 3, 6, 5, 10,"Undead",  false, MonsterType.ZOMBIE);
        add("Zombie_Aberrant_Big",    52,"6d10+18",9,20,18, 6,16, 3, 6, 5, 20,"Undead",  false, MonsterType.ZOMBIE);
        add("Zombie_Aberrant_Small",  22,"3d8+9", 8,20, 13, 6,16, 3, 6, 5,  2,"Undead",  false, MonsterType.ZOMBIE);
        add("Zombie_Burnt",           22,"3d8+9", 8,20, 13, 6,16, 3, 6, 5,  2,"Undead",  false, MonsterType.ZOMBIE);
        add("Zombie_Frost",           22,"3d8+9", 8,20, 13, 6,16, 3, 6, 5,  2,"Undead",  false, MonsterType.ZOMBIE);
        add("Zombie_Sand",            22,"3d8+9", 8,20, 13, 6,16, 3, 6, 5,  2,"Undead",  false, MonsterType.ZOMBIE);
        add("Zombie_Werewolf",        58,"9d8+18",11,30, 17,10,16, 3, 7, 8, 30,"Undead",  false, MonsterType.ZOMBIE_WEREWOLF);
        add("Zombie_Werewolf",        58,"9d8+18",11,30, 17,10,16, 3, 7, 8, 30,"Undead",  false, MonsterType.ZOMBIE_WEREWOLF);

        // ── OUTLANDERS ────────────────────────────────────────────────────────
        add("Outlander",              11,"2d8+2",12,30, 12,12,12,10,10,10,  2,"Humanoid",false, MonsterType.OUTLANDER);
        add("Outlander_Peon",          9,"2d8",  11,30, 10,10,10, 8,10, 8,  2,"Humanoid",false, MonsterType.OUTLANDER);
        add("Outlander_Marauder",     32,"5d8+10",14,30,16,12,14,10,10, 8, 20,"Humanoid",false, MonsterType.OUTLANDER);
        add("Outlander_Brute",        32,"5d8+10",13,30,16,10,14, 8,10, 8, 20,"Humanoid",false, MonsterType.OUTLANDER_BERSERKER);
        add("Outlander_Berserker",    67,"9d8+27",13,40,18,12,16, 8,12,10, 40,"Humanoid",false, MonsterType.OUTLANDER_BERSERKER);
        add("Outlander_Cultist",      11,"2d8+2",12,30,  8,10,12,14,12,14, 10,"Humanoid",false, MonsterType.OUTLANDER_PRIEST);
        add("Outlander_Priest",       27,"5d8+5",12,30,  8,10,12,10,16,12, 20,"Humanoid",false, MonsterType.OUTLANDER_PRIEST);
        add("Outlander_Sorcerer",     27,"5d8+5",12,30,  8,14,12,14,12,14, 20,"Humanoid",false, MonsterType.OUTLANDER_SORCERER);
        add("Outlander_Hunter",       11,"2d8+2",13,30, 10,14,12,10,12, 8, 10,"Humanoid",false, MonsterType.OUTLANDER_HUNTER);
        add("Outlander_Stalker",      27,"5d8+5",14,40, 10,18,12,10,12, 8, 20,"Humanoid",false, MonsterType.OUTLANDER_HUNTER);

        // ── SAURIANS ─────────────────────────────────────────────────────────
        add("Saurian",                13,"2d8+4",12,30, 12,14,14, 8,12, 8,  2,"Humanoid",false, MonsterType.SAURIAN);
        add("Saurian_Hunter",         27,"5d8+5",13,30, 12,16,12,10,14, 8, 20,"Humanoid",false, MonsterType.SAURIAN);
        add("Saurian_Rogue",          27,"5d8+5",14,40, 10,18,12,10,14, 8, 30,"Humanoid",false, MonsterType.SAURIAN_ROGUE);
        add("Saurian_Warrior",        32,"5d8+10",14,30,16,14,14,10,12, 8, 20,"Humanoid",false, MonsterType.SAURIAN_WARRIOR);

        // ── SLOTHIANS ─────────────────────────────────────────────────────────
        add("Slothian",               11,"2d8+2",11,30, 12,10,12, 8,10, 8,  2,"Humanoid",false, MonsterType.SLOTHIAN);
        add("Slothian_Scout",         19,"3d8+6",13,40, 10,16,14,10,12, 8, 10,"Humanoid",false, MonsterType.SLOTHIAN);
        add("Slothian_Warrior",       32,"5d8+10",14,30,16,10,14, 8,10, 8, 20,"Humanoid",false, MonsterType.SLOTHIAN);
        add("Slothian_Monk",          27,"5d8+5",13,40, 10,14,12,10,14,10, 20,"Humanoid",false, MonsterType.SLOTHIAN_MONK);
        add("Slothian_Elder",         52,"8d8+16",13,30,14,10,14,12,14,10, 30,"Humanoid",false, MonsterType.SLOTHIAN_ELDER);
        add("Slothian_Villager",       9,"2d8",  10,30, 10,10,10, 8,10, 8,  0,"Humanoid",false, MonsterType.SLOTHIAN);
        add("Slothian_Kid",            7,"1d8+3", 9,30,  8, 8,12, 8,10, 8,  0,"Humanoid",false, MonsterType.SLOTHIAN);

        // ── FERANS ────────────────────────────────────────────────────────────
        add("Feran",                  11,"2d8+2",11,40, 10,14,12, 4,12, 5,  2,"Beast",   false, MonsterType.FERAN);
        add("Feran_Cub",               5,"1d8+1", 9,30,  8,12,12, 4,12, 5,  0,"Beast",   false, MonsterType.FERAN);
        add("Feran_Burrower",         22,"4d8+4",12,30, 14,10,12, 4,12, 5, 10,"Beast",   false, MonsterType.FERAN);
        add("Feran_Civilian",         11,"2d8+2",10,40, 10,14,12, 4,12, 5,  0,"Beast",   false, MonsterType.FERAN);
        add("Feran_Longtooth",        45,"7d8+14",14,40,18,14,14, 5,12, 5, 30,"Beast",   false, MonsterType.FERAN);
        add("Feran_Sharptooth",       52,"8d8+16",14,40,18,14,14, 5,12, 5, 40,"Beast",   false, MonsterType.FERAN);
        add("Feran_Windwalker",       65,"10d8+20",14,40,18,14,14,5,12, 5, 50,"Beast",   true,  MonsterType.FERAN_WINDWALKER);

        // ── KWEEBECS ──────────────────────────────────────────────────────────
        add("Kweebec_Rootling",        9,"2d6+2", 9,20,  6, 8,12,10,12, 8,  0,"Plant",   false, MonsterType.BRAMBLEKIN);
        add("Kweebec_Seedling",        9,"2d6+2", 9,20,  6, 8,12,10,12, 8,  0,"Plant",   false, MonsterType.BRAMBLEKIN);
        add("Kweebec_Sproutling",     13,"2d8+4",10,25,  8,10,14,10,12, 8,  2,"Plant",   false, MonsterType.BRAMBLEKIN);
        add("Kweebec_Sapling",        16,"3d8+3",11,30, 10,12,12,10,14, 8,  2,"Plant",   false, MonsterType.BRAMBLEKIN);

        // ── KLOPS ─────────────────────────────────────────────────────────────
        add("Klops",                   9,"2d6+2",10,25,  8,10,12, 8,10, 8,  0,"Humanoid",false, MonsterType.SLOTHIAN);
        add("Klops_Gentleman",         9,"2d6+2",10,25,  8,10,12,10,10,12,  0,"Humanoid",false, MonsterType.SLOTHIAN);
        add("Klops_Merchant",          9,"2d6+2",10,25,  8,10,12,10,10,12,  0,"Humanoid",false, MonsterType.SLOTHIAN);
        add("Klops_Miner",             9,"2d6+2",10,25, 10,10,12, 8,10, 8,  0,"Humanoid",false, MonsterType.SLOTHIAN);

        // ── GOLEMS ────────────────────────────────────────────────────────────
        add("Golem_Crystal_Earth",    52,"7d10+14",17,20,18, 9,14, 3,11, 1, 40,"Construct",false, MonsterType.GOLEM);
        add("Golem_Crystal_Flame",    52,"7d10+14",17,20,18, 9,14, 3,11, 1, 40,"Construct",false, MonsterType.GOLEM);
        add("Golem_Crystal_Frost",    52,"7d10+14",17,20,18, 9,14, 3,11, 1, 40,"Construct",false, MonsterType.GOLEM);
        add("Golem_Crystal_Sand",     52,"7d10+14",17,20,18, 9,14, 3,11, 1, 40,"Construct",false, MonsterType.GOLEM);
        add("Golem_Crystal_Thunder",  52,"7d10+14",17,20,18, 9,14, 3,11, 1, 40,"Construct",false, MonsterType.GOLEM);
        add("Golem_Firesteel",        93,"11d10+33",18,20,22,9,16, 3,11, 1, 80,"Construct",false, MonsterType.GOLEM);
        add("Golem_Guardian_Void",   114,"13d10+39",18,30,22,9,20, 3,11, 1,100,"Construct",false, MonsterType.GOLEM);

        // ── SCARAK ────────────────────────────────────────────────────────────
        add("Scarak_Louse",            5,"1d6+2",11,20,  6,12,14, 1, 7, 3,  0,"Monstrosity",false, MonsterType.SCARAK);
        add("Scarak_Seeker",          13,"2d8+4",13,40, 10,16,14, 2, 8, 3,  2,"Monstrosity",false, MonsterType.SCARAK);
        add("Scarak_Fighter",         22,"4d8+4",14,30, 14,12,14, 2, 8, 3, 10,"Monstrosity",false, MonsterType.SCARAK);
        add("Scarak_Defender",        45,"7d8+14",16,20,18,10,16, 2, 8, 3, 30,"Monstrosity",false, MonsterType.SCARAK);
        add("Scarak_Fighter_Royal_Guard",45,"7d8+14",17,30,18,12,16,2,8,3, 40,"Monstrosity",false, MonsterType.SCARAK);
        add("Scarak_Broodmother",     93,"11d10+33",15,20,18,8,16,6,10, 3, 80,"Monstrosity",false, MonsterType.SCARAK_BROODMOTHER);
        add("Scarak_Broodmother_Young",52,"7d10+14",14,20,16,8,14,4,10,3, 40,"Monstrosity",false, MonsterType.SCARAK_BROODMOTHER);

        // ── VOID / SPIRITS ────────────────────────────────────────────────────
        add("Crawler_Void",           13,"2d8+4",13,30, 12,14,14, 5, 8, 3, 10,"Aberration",false, MonsterType.VOID_CREATURE);
        add("Larva_Void",              7,"1d6+4",12,20, 10,12,18, 1, 7, 3,  2,"Aberration",false, MonsterType.VOID_CREATURE);
        add("Eye_Void",               22,"4d8+4",13, 0, 10,14,14,15,12, 8, 20,"Aberration",true,  MonsterType.VOID_CREATURE);
        add("Necromancer_Void",       52,"8d8+16",13,30, 8,14,14,18,12,12,50,"Aberration",false,  MonsterType.VOID_CREATURE);
        add("Spectre_Void",           22,"5d8",  12, 0,  1,14,11,10,10,11, 10,"Undead",    true,  MonsterType.WRAITH);
        add("Spawn_Void",             13,"2d8+4",13,30, 12,14,14, 5, 8, 3, 10,"Aberration",false, MonsterType.VOID_CREATURE);
        add("Spirit_Ember",           22,"5d8",  12, 0, 10,14,11,10,10,11, 10,"Elemental", true,  MonsterType.SPIRIT);
        add("Spirit_Frost",           22,"5d8",  12, 0, 10,14,11,10,10,11, 10,"Elemental", true,  MonsterType.SPIRIT);
        add("Spirit_Root",            22,"5d8",  12, 0, 10,14,11,10,10,11, 10,"Elemental", true,  MonsterType.SPIRIT);
        add("Spirit_Thunder",         22,"5d8",  12, 0, 10,14,11,10,10,11, 10,"Elemental", true,  MonsterType.SPIRIT);
        add("Wraith",                 67,"9d8+27",13,0,  6,16,16,13,14,15, 50,"Undead",    true,  MonsterType.WRAITH);
        add("Wraith_Lantern",         67,"9d8+27",13,0,  6,16,16,13,14,15, 50,"Undead",    true,  MonsterType.WRAITH);
        add("Ghoul",                  22,"5d8",  12,30, 13, 8,12, 7,10, 6, 10,"Undead",    false, MonsterType.GHOUL);
        add("Shadow_Knight",          78,"12d8+24",17,30,16,12,14,10,12,10,60,"Undead",    false, MonsterType.SHADOW_KNIGHT);

        // ── ANIMALS ──────────────────────────────────────────────────────────
        add("Bat",                     2,"1d4",  12,30,  5,15, 8, 2,12, 4,  0,"Beast",    true,  MonsterType.BAT);
        add("Bat_Ice",                 4,"1d6+1",12,30,  5,15,12, 2,12, 4,  0,"Beast",    true,  MonsterType.BAT);
        add("Bear_Grizzly",           34,"4d10+12",11,40,19,10,16,2,13, 7, 20,"Beast",    false, MonsterType.BEAR);
        add("Bear_Polar",             42,"5d10+15",12,40,20,10,16,2,13, 7, 30,"Beast",    false, MonsterType.BEAR);
        add("Wolf_Black",             11,"2d8+2",13,40, 12,15,12, 3,12, 6,  2,"Beast",    false, MonsterType.WOLF);
        add("Wolf_White",             11,"2d8+2",13,40, 12,15,12, 3,12, 6,  2,"Beast",    false, MonsterType.WOLF);
        add("Wolf_Outlander_Priest",  11,"2d8+2",13,40, 12,15,12, 3,12, 6,  2,"Beast",    false, MonsterType.WOLF);
        add("Wolf_Outlander_Sorcerer",11,"2d8+2",13,40, 12,15,12, 3,12, 6,  2,"Beast",    false, MonsterType.WOLF);
        add("Wolf_Trork_Hunter",      11,"2d8+2",13,40, 12,15,12, 3,12, 6,  2,"Beast",    false, MonsterType.WOLF);
        add("Wolf_Trork_Shaman",      11,"2d8+2",13,40, 12,15,12, 3,12, 6,  2,"Beast",    false, MonsterType.WOLF);
        add("Boar",                   11,"2d8+2",11,40, 13, 8,12, 2, 9, 5,  2,"Beast",    false, MonsterType.BOAR);
        add("Boar_Piglet",             3,"1d6",   9,30,  7, 8,11, 2, 9, 5,  0,"Beast",    false, MonsterType.BOAR);
        add("Crocodile",              19,"3d10+3",12,20, 15,10,13, 2,10, 5, 10,"Beast",    false, MonsterType.CROCODILE);
        add("Tiger_Sabertooth",       45,"6d10+12",12,50,18,14,14,3,12, 8, 30,"Beast",    false, MonsterType.BIG_CAT);
        add("Leopard_Snow",           13,"2d8+4",12,40, 14,16,14, 3,14, 8, 10,"Beast",    false, MonsterType.BIG_CAT);
        add("Raptor_Cave",            13,"2d8+4",12,40, 12,16,12, 4,12, 6, 10,"Beast",    false, MonsterType.RAPTOR);
        add("Rex_Cave",              136,"16d12+64",13,50,25,10,19,2,12,9, 80,"Beast",    false, MonsterType.REX);
        add("Hyena",                  11,"2d8+2",11,40, 11,13,12, 2,12, 7,  2,"Beast",    false, MonsterType.WOLF);
        add("Scorpion",                1,"1d4-1",11,10,  2,11,11, 1, 8, 4,  0,"Beast",    false, MonsterType.SCORPION);
        add("Snake_Cobra",             5,"1d6+2",12,30,  4,14,12, 2,10, 3,  0,"Beast",    false, MonsterType.SNAKE);
        add("Snake_Marsh",            11,"2d8+2",12,30, 10,14,12, 2,10, 3,  2,"Beast",    false, MonsterType.SNAKE);
        add("Snake_Rattle",            5,"1d6+2",12,30,  4,14,12, 2,10, 3,  0,"Beast",    false, MonsterType.SNAKE);
        add("Spider",                  1,"1d6-1",12,20,  2,14, 8, 1,10, 4,  0,"Beast",    false, MonsterType.SPIDER);
        add("Spider_Cave",            11,"2d8+2",13,30, 12,16,12, 7,11, 4, 10,"Beast",    false, MonsterType.SPIDER);
        add("Moose_Bull",             42,"5d10+15",11,40,19,10,16,2,11, 5, 20,"Beast",    false, MonsterType.MOOSE);
        add("Shark_Hammerhead",       19,"3d10+3",12,40, 17, 6,13, 1,10,5, 10,"Beast",    false, MonsterType.SHARK);
        add("Warthog",                11,"2d8+2",11,40, 13, 8,12, 2, 9, 5,  2,"Beast",    false, MonsterType.WARTHOG);
        add("Warthog_Piglet",          3,"1d6",   9,30,  7, 8,11, 2, 9, 5,  0,"Beast",    false, MonsterType.WARTHOG);
        add("Yeti",                   51,"6d10+18",12,40,18,13,16,8,12, 7, 40,"Monstrosity",false,MonsterType.YETI);
        add("Werewolf",               58,"9d8+18",11,30, 17,10,16, 3, 7, 8, 30,"Humanoid",false, MonsterType.WEREWOLF);
        add("Archaeopteryx",           5,"1d6+2",12,20,  6,14,12, 4,12, 5,  0,"Beast",    true,  MonsterType.BAT);
        add("Pterodactyl",            26,"4d10+4",13,60, 14,14,12, 2,10, 5, 20,"Beast",    true,  MonsterType.PTERODACTYL);
        add("Trillodon",              45,"6d10+12",14,40,18,10,14, 2,10, 5, 30,"Beast",    false, MonsterType.TRILLODON);
        add("Toad_Rhino",             45,"6d10+12",14,30,18,10,16, 2,10, 5, 20,"Beast",    false, MonsterType.TOAD_RHINO);
        add("Toad_Rhino_Magma",       52,"7d10+14",14,30,18,10,16, 2,10, 5, 30,"Beast",    false, MonsterType.TOAD_RHINO);
        add("Emberwulf",              45,"6d10+12",13,50, 16,15,14, 6,12, 8, 30,"Monstrosity",false,MonsterType.EMBER_WULF);
        add("Snapdragon",              9,"2d6+2",11,10, 10, 8,14, 3,10, 3,  0,"Plant",    false, MonsterType.SNAPJAW);
        add("Snapjaw",                19,"3d8+6",13,30, 14, 8,14, 2,10, 5, 10,"Beast",    false, MonsterType.SNAPJAW);
        add("Bramblekin",             13,"2d8+4",11,30, 12,10,14, 5,12, 6, 10,"Plant",    false, MonsterType.BRAMBLEKIN);
        add("Bramblekin_Shaman",      22,"4d8+4",11,30, 10,10,14,10,14, 8, 20,"Plant",    false, MonsterType.BRAMBLEKIN_SHAMAN);
        add("Fen_Stalker",            52,"8d10+8",13,40, 14,16,12, 5,12, 6, 40,"Monstrosity",false,MonsterType.FEN_STALKER);
        add("Slug_Magma",             19,"3d10+3",11,20, 14, 6,13, 1,10, 5, 10,"Elemental",false, MonsterType.SLUG_MAGMA);
        add("Grooble",                 9,"2d6+2",11,25, 10,12,12, 6,10, 6,  2,"Monstrosity",false,MonsterType.GOBLIN);
        add("Cactee",                 13,"2d8+4",11,25, 12,10,14, 3,10, 4, 10,"Plant",    false, MonsterType.SNAPJAW);
        add("Hatworm",                 5,"1d6+2",11,20,  6, 8,14, 1, 7, 3,  0,"Monstrosity",false,MonsterType.SCARAK);
        add("Mushee",                  9,"2d6+2",11,20,  8,10,14, 5,10, 5,  0,"Plant",    false, MonsterType.BRAMBLEKIN);
        add("Hedera",                 13,"2d8+4",13,30, 10,16,14, 8,14, 8, 10,"Plant",    false, MonsterType.BRAMBLEKIN_SHAMAN);
        add("Tuluk",                  13,"2d8+4",11,30, 12,10,14, 5,12, 6,  2,"Humanoid", false, MonsterType.SLOTHIAN);
        add("Tuluk_Fisherman",        13,"2d8+4",10,30, 12,10,14, 5,12, 6,  0,"Humanoid", false, MonsterType.SLOTHIAN);
        add("Spark_Living",           13,"3d6",  13, 0,  4,20,11, 2,12, 7, 10,"Elemental",true,  MonsterType.SPARK_LIVING);
        add("Tornado",                52,"8d10+8",14,0,  2,20,14, 1, 8, 5, 50,"Elemental",true,  MonsterType.TORNADO);

        // ── DRAGONS ───────────────────────────────────────────────────────────
        add("Dragon_Fire",           195,"17d12+85",19,80,23,10,21,14,11,17,170,"Dragon",true, MonsterType.DRAGON_FIRE);
        add("Dragon_Frost",          200,"17d12+85",18,80,23,10,21,14,11,17,170,"Dragon",true, MonsterType.DRAGON_FROST);
        add("Dragon_Void",           225,"18d12+108",19,80,27,10,25,16,13,19,200,"Dragon",true,MonsterType.DRAGON_VOID);
    }

    private static void add(String name, int hp, String dice, int ac, int speed,
                            int str, int dex, int con, int intel, int wis, int cha,
                            int cr10x, String type, boolean flying, MonsterType mt) {
        // Store with lowercase key for case-insensitive lookup
        DB.put(name.toLowerCase(), new MonsterStats(hp, dice, ac, speed, str, dex, con, intel, wis, cha, cr10x, type, flying, mt));
    }

    public static MonsterStats getStats(String entityName) {
        return entityName != null ? DB.get(entityName.toLowerCase()) : null;
    }
    public static boolean has(String entityName) {
        return entityName != null && DB.containsKey(entityName.toLowerCase());
    }

    public static String formatCR(int cr10x) {
        switch (cr10x) {
            case 0:  return "0";
            case 1:  return "1/8";
            case 2:  return "1/4";
            case 5:  return "1/2";
            default: return String.valueOf(cr10x / 10);
        }
    }
}