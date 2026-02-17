package com.gridifymydungeon.plugin.dnd;

import com.gridifymydungeon.plugin.spell.ClassType;
import com.gridifymydungeon.plugin.spell.SubclassType;

/**
 * Character encoding/decoding with CLASS SUPPORT
 * FORMAT: AABBCC-DEF-GH-IJK (15 characters)
 *
 * AA: STR+DEX packed (stat1*31 + stat2)
 * BB: CON+INT packed
 * CC: WIS+CHA packed
 * D: HP high part (HP / 62)
 * E: HP low part (HP % 62)
 * F: Armor (1-50)
 * G: Initiative + MaxMoves + Flying combined
 * H: Spell slots high part
 * I: Level (1-20) + Class (0-13)
 * J: Subclass (0-35)
 * K: Spell slots low part
 *
 * UPDATED: Now supports 15-character codes with class/level
 */
public class CharacterCodec {
    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    /**
     * Encode character stats to 15-character code
     */
    public static String encode(CharacterStats stats, int maxMoves) {
        // Validate ranges
        if (!validateStats(stats)) {
            return null;
        }

        StringBuilder code = new StringBuilder();

        // AA: STR+DEX packed
        int strDex = stats.strength * 31 + stats.dexterity;
        code.append(toBase62(strDex / 62));
        code.append(toBase62(strDex % 62));

        // BB: CON+INT packed
        int conInt = stats.constitution * 31 + stats.intelligence;
        code.append(toBase62(conInt / 62));
        code.append(toBase62(conInt % 62));

        // CC: WIS+CHA packed
        int wisCha = stats.wisdom * 31 + stats.charisma;
        code.append(toBase62(wisCha / 62));
        code.append(toBase62(wisCha % 62));

        code.append('-');

        // D: HP high part
        code.append(toBase62(stats.maxHP / 62));
        // E: HP low part
        code.append(toBase62(stats.maxHP % 62));
        // F: Armor
        code.append(toBase62(stats.armor));

        code.append('-');

        // G: Initiative + MaxMoves + Flying
        int initiative = stats.initiative + 10; // -10 to +10 → 0 to 20
        int flying = stats.flying ? 1 : 0;
        int combined = initiative * 62 + maxMoves * 2 + flying;
        code.append(toBase62(combined / 62));
        code.append(toBase62(combined % 62));

        code.append('-');

        // H: Spell slots high part
        code.append(toBase62(stats.getSpellSlots() / 62));

        // I: Level (0-19) + Class (0-13) combined
        int level = stats.getLevel() - 1; // 1-20 → 0-19
        int classId = stats.getClassType() != null ? stats.getClassType().ordinal() : 0;
        int levelClass = level * 14 + classId; // Max: 19*14 + 13 = 279
        code.append(toBase62(levelClass / 62));
        code.append(toBase62(levelClass % 62));

        // J: Subclass (0-35)
        int subclassId = stats.getSubclassType() != null ? stats.getSubclassType().ordinal() : 0;
        code.append(toBase62(subclassId));

        // K: Spell slots low part
        code.append(toBase62(stats.getSpellSlots() % 62));

        return code.toString();
    }

    /**
     * Decode 15-character code to DecodedStats
     */
    public static DecodedStats decode(String code) {
        if (code == null) return null;

        // Remove any whitespace/dashes for validation
        String clean = code.replace("-", "").trim();

        // Support both 11-char (old) and 15-char (new) codes
        if (clean.length() != 11 && clean.length() != 15) {
            return null;
        }

        try {
            DecodedStats decoded = new DecodedStats();

            // Parse with dashes
            String[] parts = code.split("-");
            if (parts.length < 3) return null;

            String part1 = parts[0]; // AABBCC (6 chars)
            String part2 = parts[1]; // DEF (3 chars)
            String part3 = parts[2]; // GH (2 chars)

            // AA: STR+DEX
            int strDex = fromBase62(part1.charAt(0)) * 62 + fromBase62(part1.charAt(1));
            decoded.strength = strDex / 31;
            decoded.dexterity = strDex % 31;

            // BB: CON+INT
            int conInt = fromBase62(part1.charAt(2)) * 62 + fromBase62(part1.charAt(3));
            decoded.constitution = conInt / 31;
            decoded.intelligence = conInt % 31;

            // CC: WIS+CHA
            int wisCha = fromBase62(part1.charAt(4)) * 62 + fromBase62(part1.charAt(5));
            decoded.wisdom = wisCha / 31;
            decoded.charisma = wisCha % 31;

            // D+E: HP
            decoded.hp = fromBase62(part2.charAt(0)) * 62 + fromBase62(part2.charAt(1));

            // F: Armor
            decoded.armor = fromBase62(part2.charAt(2));

            // G+H: Initiative + MaxMoves + Flying
            int combined = fromBase62(part3.charAt(0)) * 62 + fromBase62(part3.charAt(1));
            decoded.initiative = (combined / 62) - 10;
            decoded.maxMoves = (combined % 62) / 2;
            decoded.flying = (combined % 2) == 1;

            // NEW: Parse class/subclass if 15-char code
            if (parts.length >= 4 && parts[3].length() >= 4) {
                String part4 = parts[3]; // HIJK (4 chars)

                // H: Spell slots high
                int spellSlotsHigh = fromBase62(part4.charAt(0));

                // I: Level + Class
                int levelClass = fromBase62(part4.charAt(1)) * 62 + fromBase62(part4.charAt(2));
                decoded.level = (levelClass / 14) + 1; // 0-19 → 1-20
                int classId = levelClass % 14;
                decoded.classType = classId < ClassType.values().length ? ClassType.values()[classId] : null;

                // J: Subclass
                int subclassId = fromBase62(part4.charAt(3));
                decoded.subclassType = subclassId < SubclassType.values().length ? SubclassType.values()[subclassId] : null;

                // K: Spell slots low (if present)
                if (part4.length() >= 5) {
                    int spellSlotsLow = fromBase62(part4.charAt(4));
                    decoded.spellSlots = spellSlotsHigh * 62 + spellSlotsLow;
                } else {
                    decoded.spellSlots = spellSlotsHigh * 62; // Default if missing
                }
            } else {
                // Old 11-char code - defaults
                decoded.level = 1;
                decoded.classType = null;
                decoded.subclassType = null;
                decoded.spellSlots = 100;
            }

            // Validate
            if (!validateDecodedStats(decoded)) {
                return null;
            }

            return decoded;

        } catch (Exception e) {
            return null;
        }
    }

    private static boolean validateStats(CharacterStats stats) {
        return stats.strength >= 0 && stats.strength <= 30 &&
                stats.dexterity >= 0 && stats.dexterity <= 30 &&
                stats.constitution >= 0 && stats.constitution <= 30 &&
                stats.intelligence >= 0 && stats.intelligence <= 30 &&
                stats.wisdom >= 0 && stats.wisdom <= 30 &&
                stats.charisma >= 0 && stats.charisma <= 30 &&
                stats.maxHP >= 1 && stats.maxHP <= 999 &&
                stats.armor >= 1 && stats.armor <= 50 &&
                stats.initiative >= -10 && stats.initiative <= 10 &&
                stats.getLevel() >= 1 && stats.getLevel() <= 20 &&
                stats.getSpellSlots() >= 0 && stats.getSpellSlots() <= 3843; // 62*62 - 1
    }

    private static boolean validateDecodedStats(DecodedStats stats) {
        return stats.strength >= 0 && stats.strength <= 30 &&
                stats.dexterity >= 0 && stats.dexterity <= 30 &&
                stats.constitution >= 0 && stats.constitution <= 30 &&
                stats.intelligence >= 0 && stats.intelligence <= 30 &&
                stats.wisdom >= 0 && stats.wisdom <= 30 &&
                stats.charisma >= 0 && stats.charisma <= 30 &&
                stats.hp >= 1 && stats.hp <= 999 &&
                stats.armor >= 1 && stats.armor <= 50 &&
                stats.initiative >= -10 && stats.initiative <= 10 &&
                stats.level >= 1 && stats.level <= 20 &&
                stats.spellSlots >= 0 && stats.spellSlots <= 3843;
    }

    private static char toBase62(int value) {
        if (value < 0 || value >= 62) {
            throw new IllegalArgumentException("Value out of range: " + value);
        }
        return BASE62.charAt(value);
    }

    private static int fromBase62(char c) {
        int index = BASE62.indexOf(c);
        if (index == -1) {
            throw new IllegalArgumentException("Invalid base62 character: " + c);
        }
        return index;
    }

    /**
     * Decoded stats structure with CLASS SUPPORT
     */
    public static class DecodedStats {
        public int strength;
        public int dexterity;
        public int constitution;
        public int intelligence;
        public int wisdom;
        public int charisma;
        public int hp;
        public int armor;
        public int initiative;
        public int maxMoves;
        public boolean flying;
        public int level = 1;
        public ClassType classType = null;
        public SubclassType subclassType = null;
        public int spellSlots = 100;

        public void applyTo(CharacterStats stats) {
            stats.strength = this.strength;
            stats.dexterity = this.dexterity;
            stats.constitution = this.constitution;
            stats.intelligence = this.intelligence;
            stats.wisdom = this.wisdom;
            stats.charisma = this.charisma;
            stats.maxHP = this.hp;
            stats.currentHP = this.hp;
            stats.armor = this.armor;
            stats.initiative = this.initiative;
            stats.flying = this.flying;
            stats.setLevel(this.level);
            stats.setClassType(this.classType);
            stats.setSubclassType(this.subclassType);
            stats.setSpellSlots(this.spellSlots);
            stats.setUsedSpellSlots(0); // Fresh on login
        }
    }
}