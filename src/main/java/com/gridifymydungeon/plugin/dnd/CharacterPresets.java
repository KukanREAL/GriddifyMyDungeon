package com.gridifymydungeon.plugin.dnd;

import java.util.HashMap;
import java.util.Map;

/**
 * Character class presets
 */
public class CharacterPresets {

    public static class Preset {
        public String name;
        public String description;
        public int str, dex, con, intel, wis, cha;
        public int hp;
        public int armor;
        public int initiative;

        public Preset(String name, int str, int dex, int con, int intel, int wis, int cha, int hp, int armor, int init) {
            this.name = name;
            this.str = str;
            this.dex = dex;
            this.con = con;
            this.intel = intel;
            this.wis = wis;
            this.cha = cha;
            this.hp = hp;
            this.armor = armor;
            this.initiative = init;
        }
    }

    private static final Map<String, Preset> PRESETS = new HashMap<>();

    static {
        // Warrior - High STR, CON. Low INT, CHA
        PRESETS.put("warrior", new Preset(
                "Warrior",
                16, 12, 14, 8, 10, 8,
                20, 16, 1  // HP, Armor, Initiative
        ));

        // Mage - High INT. Low STR, CON
        PRESETS.put("mage", new Preset(
                "Mage",
                8, 12, 10, 16, 14, 10,
                12, 11, 1
        ));

        // Rogue - High DEX. Balanced
        PRESETS.put("rogue", new Preset(
                "Rogue",
                10, 16, 12, 12, 10, 14,
                14, 13, 3
        ));

        // Cleric - High WIS, CON. Support
        PRESETS.put("cleric", new Preset(
                "Cleric",
                12, 10, 14, 10, 16, 12,
                16, 14, 0
        ));

        // Bard - High CHA. Versatile
        PRESETS.put("bard", new Preset(
                "Bard",
                10, 14, 12, 12, 10, 16,
                14, 12, 2
        ));

        // Ranger - Balanced DEX, WIS
        PRESETS.put("ranger", new Preset(
                "Ranger",
                12, 16, 12, 10, 14, 10,
                15, 13, 2
        ));

        // Paladin - High STR, CHA
        PRESETS.put("paladin", new Preset(
                "Paladin",
                14, 10, 14, 8, 12, 16,
                18, 16, 0
        ));

        // Barbarian - Highest STR, CON
        PRESETS.put("barbarian", new Preset(
                "Barbarian",
                18, 12, 16, 6, 10, 8,
                25, 13, 1
        ));

        // Monk - High DEX, WIS
        PRESETS.put("monk", new Preset(
                "Monk",
                12, 16, 12, 10, 16, 10,
                14, 14, 3
        ));

        // Druid - High WIS. Nature magic
        PRESETS.put("druid", new Preset(
                "Druid",
                10, 12, 12, 12, 16, 10,
                14, 12, 1
        ));

        // Sorcerer - High CHA. Raw magic
        PRESETS.put("sorcerer", new Preset(
                "Sorcerer",
                8, 12, 10, 12, 10, 16,
                12, 11, 1
        ));

        // Warlock - High CHA. Dark pacts
        PRESETS.put("warlock", new Preset(
                "Warlock",
                10, 12, 12, 12, 10, 16,
                13, 11, 1
        ));
    }

    /**
     * Get preset by name
     */
    public static Preset getPreset(String name) {
        return PRESETS.get(name.toLowerCase());
    }

    /**
     * Get all preset names
     */
    public static Map<String, Preset> getAllPresets() {
        return new HashMap<>(PRESETS);
    }

    /**
     * Apply preset to character stats
     */
    public static void applyPreset(CharacterStats stats, Preset preset) {
        stats.strength = preset.str;
        stats.dexterity = preset.dex;
        stats.constitution = preset.con;
        stats.intelligence = preset.intel;
        stats.wisdom = preset.wis;
        stats.charisma = preset.cha;
        stats.maxHP = preset.hp;
        stats.currentHP = preset.hp;
        stats.armor = preset.armor;
        stats.initiative = preset.initiative;
    }
}
