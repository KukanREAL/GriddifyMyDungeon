package com.gridifymydungeon.plugin.gridmove;

import com.gridifymydungeon.plugin.dnd.EncounterManager;
import com.gridifymydungeon.plugin.dnd.MonsterState;
import com.gridifymydungeon.plugin.dnd.RoleManager;
import com.gridifymydungeon.plugin.spell.SpellCastingState;
import com.gridifymydungeon.plugin.spell.SpellData;
import com.gridifymydungeon.plugin.spell.SpellDatabase;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * Top-left HUD panel for Griddify.
 *
 * One instance per player. Shows different content depending on the active
 * hotbar mode stored in GridPlayerState.hotbarState.
 *
 * Usage:
 *   GriddifyHud hud = new GriddifyHud(playerRef);
 *   player.getHudManager().setCustomHud(entityRef, store, hud);
 *   hud.updatePanel(state, encounterManager, roleManager);
 *
 * To hide the panel entirely:
 *   hud.clearPanel();
 */
public class GriddifyHud extends CustomUIHud {

    private static final int MAX_LINES = 12;
    private static final String UI_FILE = "GriddifyPanel.ui";

    private final PlayerRef playerRef;

    // ── Colors ─────────────────────────────────────────────────────────────────
    private static final String COLOR_TITLE      = "#FFD700"; // gold
    private static final String COLOR_SUBTITLE   = "#AAAAAA"; // grey
    private static final String COLOR_MOVE       = "#00FF7F"; // spring green
    private static final String COLOR_SPELL_NORM = "#FFFFFF"; // white
    private static final String COLOR_SPELL_SEL  = "#FFD700"; // gold highlight
    private static final String COLOR_CAST       = "#FF6B6B"; // red-ish
    private static final String COLOR_TARGET     = "#87CEEB"; // sky blue
    private static final String COLOR_PROFILE    = "#00BFFF"; // deep sky blue
    private static final String COLOR_ARMED      = "#FF4500"; // orange-red

    public GriddifyHud(PlayerRef playerRef) {
        this.playerRef = playerRef;
    }

    @Override
    public void build(@Nonnull UICommandBuilder uiCommandBuilder) {
        uiCommandBuilder.append(UI_FILE);
    }

    // ── Public update entry point ──────────────────────────────────────────────

    /**
     * Refresh the panel for the given player state and mode.
     * Call from world thread after any state change.
     */
    public void updatePanel(GridPlayerState state, EncounterManager encounterManager,
                            RoleManager roleManager) {
        PlayerHotbarState hs = state.hotbarState;

        switch (hs.activeMode) {
            case NONE:
                clearPanel();
                break;
            case MOVE:
                showMove(state, encounterManager, roleManager);
                break;
            case SPELL_SELECT:
                if (hs.spellConfirmed) {
                    showCastStatus(state);
                } else {
                    showSpellSelect(state);
                }
                break;
            case LIST_SPELLS:
                showSpellList(state, encounterManager, roleManager);
                break;
            case CAST_FINAL:
                showCastFinalArmed(state);
                break;
            case TARGETING:
                showTargeting(state);
                break;
            case PROFILE:
                showProfile(state);
                break;
        }
    }

    // ── Mode-specific render methods ───────────────────────────────────────────

    private void showMove(GridPlayerState state, EncounterManager encounterManager,
                          RoleManager roleManager) {
        List<String> lines = new ArrayList<>();
        MonsterState controlled = encounterManager.getControlledMonster();
        if (controlled != null && roleManager.isGM(playerRef)) {
            lines.add(COLOR_MOVE + "MOVE: " + controlled.getDisplayName());
            lines.add(COLOR_SUBTITLE + "Moves: " + fmt(controlled.remainingMoves)
                    + "/" + fmt(controlled.maxMoves));
        } else {
            lines.add(COLOR_MOVE + "MOVE MODE");
            lines.add(COLOR_SUBTITLE + "Moves: " + fmt(state.remainingMoves)
                    + "/" + fmt(state.maxMoves));
        }
        lines.add(COLOR_SUBTITLE + "Walk to move your NPC");
        lines.add(COLOR_SUBTITLE + "[1] to deactivate");
        send(null, null, lines);
    }

    private void showSpellSelect(GridPlayerState state) {
        PlayerHotbarState hs = state.hotbarState;
        List<SpellData> spells = hs.spellList;

        List<String> lines = new ArrayList<>();
        if (spells.isEmpty()) {
            lines.add(COLOR_SUBTITLE + "(No spells available)");
        } else {
            // Show only the currently highlighted spell with full detail
            SpellData sp = spells.get(hs.spellSelectIndex);
            String cost  = sp.getSlotCost() > 0 ? sp.getSlotCost() + " slot(s)" : "cantrip";
            String range = sp.getRangeGrids() > 0 ? sp.getRangeGrids() + " grids" : "Touch";
            String dmg   = sp.getDamageDice() != null
                    ? sp.getDamageDice() + " " + sp.getDamageType().name().toLowerCase()
                    : "effect";

            lines.add(COLOR_SPELL_SEL + sp.getName());
            lines.add(COLOR_SUBTITLE + cost + "  range " + range);
            lines.add(COLOR_SUBTITLE + "dmg: " + dmg);
            lines.add(COLOR_SUBTITLE + sp.getPattern().name().toLowerCase());
            lines.add("");
            // Mini nav: prev / current / next names
            if (spells.size() > 1) {
                int prev = (hs.spellSelectIndex - 1 + spells.size()) % spells.size();
                int next = (hs.spellSelectIndex + 1) % spells.size();
                lines.add(COLOR_SUBTITLE + "  " + spells.get(prev).getName());
                lines.add(COLOR_SPELL_SEL + "▶ " + sp.getName()
                        + "  (" + (hs.spellSelectIndex + 1) + "/" + spells.size() + ")");
                lines.add(COLOR_SUBTITLE + "  " + spells.get(next).getName());
            }
        }
        send("⚔ SELECT SPELL", COLOR_SUBTITLE + "Crouch:scroll  [3]:cast  [1]:cancel", lines);
    }

    private void showCastStatus(GridPlayerState state) {
        SpellCastingState cast = state.getSpellCastingState();
        if (cast == null) {
            clearPanel();
            return;
        }
        SpellData sp = cast.getSpell();
        List<String> lines = new ArrayList<>();
        lines.add(COLOR_CAST + sp.getName());
        String dmg = sp.getDamageDice() != null
                ? sp.getDamageDice() + " " + sp.getDamageType().name().toLowerCase()
                : sp.getDescription();
        lines.add(COLOR_SUBTITLE + "Dmg: " + dmg);
        lines.add(COLOR_SUBTITLE + "Range: " + (sp.getRangeGrids() > 0 ? sp.getRangeGrids() + " grids" : "Touch"));
        lines.add(COLOR_SUBTITLE + "Pattern: " + sp.getPattern().name().toLowerCase());
        if (sp.isMultiTarget()) {
            int confirmed = cast.getConfirmedTargetCount();
            int max = sp.getMaxTargets();
            lines.add(COLOR_TARGET + "Targets: " + confirmed + "/" + max + " confirmed");
            lines.add(COLOR_SUBTITLE + "[6]+crouch: add target");
        } else {
            lines.add(COLOR_SUBTITLE + "Walk to aim, then:");
        }
        lines.add(COLOR_SUBTITLE + "[5]+crouch: FIRE");
        lines.add(COLOR_SUBTITLE + "[1]: cancel");
        send("⚔ CASTING", null, lines);
    }

    private void showSpellList(GridPlayerState state, EncounterManager encounterManager,
                               RoleManager roleManager) {
        List<SpellData> spells = buildSpellList(state, encounterManager, roleManager);
        List<String> lines = new ArrayList<>();
        if (spells.isEmpty()) {
            lines.add(COLOR_SUBTITLE + "(No spells — use /GridClass first)");
        } else {
            // Compact: one line per spell — name  dmg-roll  rN
            for (int i = 0; i < spells.size() && i < MAX_LINES; i++) {
                SpellData sp = spells.get(i);
                String dmg   = sp.getDamageDice() != null ? sp.getDamageDice() : "effect";
                String range = "r" + (sp.getRangeGrids() > 0 ? sp.getRangeGrids() : "T");
                lines.add(COLOR_SPELL_NORM + sp.getName() + "  " + dmg + "  " + range);
            }
            if (spells.size() > MAX_LINES) {
                lines.set(MAX_LINES - 1,
                        COLOR_SUBTITLE + "+" + (spells.size() - MAX_LINES + 1) + " more — /listspells");
            }
        }
        String slots = (state.stats != null)
                ? "Slots " + state.stats.getRemainingSpellSlots() + "/" + state.stats.getSpellSlots() + "  "
                : "";
        send("📜 SPELLS", COLOR_SUBTITLE + slots + "[1]:close", lines);
    }

    private void showCastFinalArmed(GridPlayerState state) {
        SpellCastingState cast = state.getSpellCastingState();
        String spellName = cast != null ? cast.getSpell().getName() : "?";
        List<String> lines = new ArrayList<>();
        lines.add(COLOR_ARMED + "Crouch to FIRE!");
        lines.add(COLOR_SUBTITLE + "[1]: abort");
        send("🔴 ARMED: " + spellName, null, lines);
    }

    private void showTargeting(GridPlayerState state) {
        SpellCastingState cast = state.getSpellCastingState();
        List<String> lines = new ArrayList<>();
        if (cast != null) {
            int confirmed = cast.getConfirmedTargetCount();
            int max = cast.getSpell().getMaxTargets();
            lines.add(COLOR_TARGET + "Confirmed: " + confirmed + "/" + max);
            lines.add(COLOR_SUBTITLE + "Crouch: confirm this cell");
            lines.add(COLOR_SUBTITLE + "[5]+crouch: fire now");
            lines.add(COLOR_SUBTITLE + "[1]: cancel");
        } else {
            lines.add(COLOR_SUBTITLE + "Crouch: confirm target");
        }
        send("🎯 TARGETING", null, lines);
    }

    private void showProfile(GridPlayerState state) {
        List<String> lines = new ArrayList<>();
        if (state.stats == null) {
            lines.add(COLOR_SUBTITLE + "(No stats — use /GridClass)");
        } else {
            String className = state.stats.getClassType() != null
                    ? state.stats.getClassType().getDisplayName() : "None";
            String sub = state.stats.getSubclassType() != null
                    ? " / " + state.stats.getSubclassType().getDisplayName() : "";
            lines.add(COLOR_PROFILE + className + sub + "  Lv." + state.stats.getLevel());
            lines.add(COLOR_SPELL_NORM + "HP: " + state.stats.currentHP + "/" + state.stats.maxHP);
            lines.add(COLOR_SPELL_NORM + "Slots: " + state.stats.getRemainingSpellSlots()
                    + "/" + state.stats.getSpellSlots());
            lines.add(COLOR_SPELL_NORM + "STR " + state.stats.strength
                    + "  DEX " + state.stats.dexterity
                    + "  CON " + state.stats.constitution);
            lines.add(COLOR_SPELL_NORM + "INT " + state.stats.intelligence
                    + "  WIS " + state.stats.wisdom
                    + "  CHA " + state.stats.charisma);
            lines.add(COLOR_SPELL_NORM + "AC: " + state.stats.armor
                    + "  Init: " + (state.stats.initiative >= 0 ? "+" : "") + state.stats.initiative);
            lines.add(COLOR_SPELL_NORM + "Moves: " + fmt(state.maxMoves));
        }
        lines.add(COLOR_SUBTITLE + "[1]: close");
        send("👤 PROFILE", null, lines);
    }

    // ── Panel clear ────────────────────────────────────────────────────────────

    /** Hide the panel (set all labels to empty). */
    public void clearPanel() {
        send(null, null, new ArrayList<>());
    }

    // ── Internal send helper ───────────────────────────────────────────────────

    /**
     * Push an update to the client.
     * title/subtitle are format strings like "#FF0000text" — the first 7 chars are the hex color.
     * lines is a List of the same format, padded/trimmed to MAX_LINES.
     */
    private void send(String title, String subtitle, List<String> lines) {
        UICommandBuilder cmd = new UICommandBuilder();

        // Title
        cmd.set("#PanelTitle.TextSpans",
                title != null ? coloredMsg(title) : Message.raw(""));

        // Subtitle
        cmd.set("#PanelSub.TextSpans",
                subtitle != null ? coloredMsg(subtitle) : Message.raw(""));

        // Lines 1–12
        for (int i = 1; i <= MAX_LINES; i++) {
            String lineId = "#Line" + i + ".TextSpans";
            if (i - 1 < lines.size()) {
                cmd.set(lineId, coloredMsg(lines.get(i - 1)));
            } else {
                cmd.set(lineId, Message.raw(""));
            }
        }

        update(false, cmd);
    }

    /**
     * Parse a line string of the form "#RRGGBB text..." into a colored Message.
     * If the string starts with '#' and has at least 7 chars, the first 7 are the color.
     */
    private static Message coloredMsg(String s) {
        if (s != null && s.startsWith("#") && s.length() > 7) {
            String color = s.substring(0, 7);
            String text  = s.substring(7);
            return Message.raw(text).color(color);
        }
        return Message.raw(s != null ? s : "");
    }

    // ── Spell list builder (mirrors ListSpellsCommand logic) ──────────────────

    /** Build the flat spell list for the current player/monster. */
    static List<SpellData> buildSpellList(GridPlayerState state, EncounterManager encounterManager,
                                          RoleManager roleManager) {
        // Called statically from HotbarInputHandler too for spell caching
        List<SpellData> result = new ArrayList<>();
        MonsterState monster = encounterManager != null ? encounterManager.getControlledMonster() : null;

        if (monster != null) {
            // GM controlling monster: monster attacks + class spells if any
            com.gridifymydungeon.plugin.spell.MonsterType mType = monster.monsterType;
            if (mType != null) {
                result.addAll(SpellDatabase.getAttacksForMonsterType(mType));
            }
            if (monster.stats.getClassType() != null) {
                result.addAll(SpellDatabase.getAvailableSpells(
                        monster.stats.getClassType(),
                        monster.stats.getSubclassType(),
                        Math.max(1, monster.stats.getLevel())));
            }
        } else if (state.stats.getClassType() != null) {
            result.addAll(SpellDatabase.getAvailableSpells(
                    state.stats.getClassType(),
                    state.stats.getSubclassType(),
                    state.stats.getLevel()));
        }
        return result;
    }

    // ── Formatting helpers ────────────────────────────────────────────────────

    private static String fmt(double d) {
        if (d == Math.floor(d)) return String.valueOf((int) d);
        return String.format("%.1f", d);
    }
}