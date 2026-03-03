package com.gridifymydungeon.plugin.gridmove;

import com.gridifymydungeon.plugin.dnd.EncounterManager;
import com.gridifymydungeon.plugin.dnd.MonsterState;
import com.gridifymydungeon.plugin.dnd.RoleManager;
import com.gridifymydungeon.plugin.spell.SpellCastingState;
import com.gridifymydungeon.plugin.spell.SpellData;
import com.gridifymydungeon.plugin.spell.SpellDatabase;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * Top-left HUD panel for Griddify.
 *
 * Built entirely inline — no external .ui file needed on the client.
 *
 * The first send() call uses clear=true and prepends the appendInline command
 * so the DOM elements are created before values are set.
 * All subsequent calls use clear=false and just update the existing elements.
 */
public class GriddifyHud extends CustomUIHud {

    private static final int MAX_LINES = 12;

    // Inline panel markup — no .ui file required
    private static final String PANEL_INLINE =
            "Group #GriddifyRoot { Anchor: (Left: 12, Top: 12); LayoutMode: Top; "
                    + "Label #PanelTitle { Style: (FontSize: 14, Alignment: Left); Anchor: (Width: 320, Height: 22); Text: \"\"; } "
                    + "Label #PanelSub   { Style: (FontSize: 11, Alignment: Left); Anchor: (Width: 320, Height: 18); Text: \"\"; } "
                    + "Label #Line1  { Style: (FontSize: 11, Alignment: Left); Anchor: (Width: 320, Height: 17); Text: \"\"; } "
                    + "Label #Line2  { Style: (FontSize: 11, Alignment: Left); Anchor: (Width: 320, Height: 17); Text: \"\"; } "
                    + "Label #Line3  { Style: (FontSize: 11, Alignment: Left); Anchor: (Width: 320, Height: 17); Text: \"\"; } "
                    + "Label #Line4  { Style: (FontSize: 11, Alignment: Left); Anchor: (Width: 320, Height: 17); Text: \"\"; } "
                    + "Label #Line5  { Style: (FontSize: 11, Alignment: Left); Anchor: (Width: 320, Height: 17); Text: \"\"; } "
                    + "Label #Line6  { Style: (FontSize: 11, Alignment: Left); Anchor: (Width: 320, Height: 17); Text: \"\"; } "
                    + "Label #Line7  { Style: (FontSize: 11, Alignment: Left); Anchor: (Width: 320, Height: 17); Text: \"\"; } "
                    + "Label #Line8  { Style: (FontSize: 11, Alignment: Left); Anchor: (Width: 320, Height: 17); Text: \"\"; } "
                    + "Label #Line9  { Style: (FontSize: 11, Alignment: Left); Anchor: (Width: 320, Height: 17); Text: \"\"; } "
                    + "Label #Line10 { Style: (FontSize: 11, Alignment: Left); Anchor: (Width: 320, Height: 17); Text: \"\"; } "
                    + "Label #Line11 { Style: (FontSize: 11, Alignment: Left); Anchor: (Width: 320, Height: 17); Text: \"\"; } "
                    + "Label #Line12 { Style: (FontSize: 11, Alignment: Left); Anchor: (Width: 320, Height: 17); Text: \"\"; } "
                    + "}";

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

    // Track whether DOM has been built yet for this player
    private boolean domBuilt = false;

    public GriddifyHud(@Nonnull PlayerRef playerRef) {
        super(playerRef);
    }

    /**
     * Override show() to do nothing.
     * We never call show() — DOM is built lazily on the first updatePanel() call.
     * This prevents "Failed to load CustomUI documents" on join/registration.
     */
    @Override
    public void show() {
        // intentionally empty — DOM built on first send()
    }

    /**
     * Required by CustomUIHud but never called (show() is a no-op).
     * Left here in case the engine calls it directly.
     */
    @Override
    public void build(@Nonnull UICommandBuilder uiCommandBuilder) {
        uiCommandBuilder.appendInline(null, PANEL_INLINE);
    }

    // ── Public update entry point ──────────────────────────────────────────────

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
        if (controlled != null && roleManager.isGM(getPlayerRef())) {
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
            if (spells.size() > 1) {
                int prev = (hs.spellSelectIndex - 1 + spells.size()) % spells.size();
                int next = (hs.spellSelectIndex + 1) % spells.size();
                lines.add(COLOR_SUBTITLE + "  " + spells.get(prev).getName());
                lines.add(COLOR_SPELL_SEL + "> " + sp.getName()
                        + "  (" + (hs.spellSelectIndex + 1) + "/" + spells.size() + ")");
                lines.add(COLOR_SUBTITLE + "  " + spells.get(next).getName());
            }
        }
        send("SELECT SPELL", COLOR_SUBTITLE + "Crouch:scroll  [3]:cast  [1]:cancel", lines);
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
        send("CASTING", null, lines);
    }

    private void showSpellList(GridPlayerState state, EncounterManager encounterManager,
                               RoleManager roleManager) {
        List<SpellData> spells = buildSpellList(state, encounterManager, roleManager);
        List<String> lines = new ArrayList<>();
        if (spells.isEmpty()) {
            lines.add(COLOR_SUBTITLE + "(No spells - use /GridClass first)");
        } else {
            for (int i = 0; i < spells.size() && i < MAX_LINES; i++) {
                SpellData sp = spells.get(i);
                String dmg   = sp.getDamageDice() != null ? sp.getDamageDice() : "effect";
                String range = "r" + (sp.getRangeGrids() > 0 ? sp.getRangeGrids() : "T");
                lines.add(COLOR_SPELL_NORM + sp.getName() + "  " + dmg + "  " + range);
            }
            if (spells.size() > MAX_LINES) {
                lines.set(MAX_LINES - 1,
                        COLOR_SUBTITLE + "+" + (spells.size() - MAX_LINES + 1) + " more - /listspells");
            }
        }
        String slots = (state.stats != null)
                ? "Slots " + state.stats.getRemainingSpellSlots() + "/" + state.stats.getSpellSlots() + "  "
                : "";
        send("SPELLS", COLOR_SUBTITLE + slots + "[1]:close", lines);
    }

    private void showCastFinalArmed(GridPlayerState state) {
        SpellCastingState cast = state.getSpellCastingState();
        String spellName = cast != null ? cast.getSpell().getName() : "?";
        List<String> lines = new ArrayList<>();
        lines.add(COLOR_ARMED + "Crouch to FIRE!");
        lines.add(COLOR_SUBTITLE + "[1]: abort");
        send("ARMED: " + spellName, null, lines);
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
        send("TARGETING", null, lines);
    }

    private void showProfile(GridPlayerState state) {
        List<String> lines = new ArrayList<>();
        if (state.stats == null) {
            lines.add(COLOR_SUBTITLE + "(No stats - use /GridClass)");
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
        send("PROFILE", null, lines);
    }

    // ── Panel clear ────────────────────────────────────────────────────────────

    public void clearPanel() {
        // If DOM not yet built, nothing to clear — just reset the flag
        if (!domBuilt) return;
        send(null, null, new ArrayList<>());
    }

    // ── Internal send helper ───────────────────────────────────────────────────

    /**
     * On first call (domBuilt=false): sends clear=true + appendInline to build the DOM,
     * then all the set() commands to populate it — all in one packet.
     * On subsequent calls: sends clear=false + only the set() commands.
     */
    private void send(String title, String subtitle, List<String> lines) {
        UICommandBuilder cmd = new UICommandBuilder();

        boolean firstSend = !domBuilt;
        if (firstSend) {
            // First send: build the DOM inline then set values in the same packet
            cmd.appendInline(null, PANEL_INLINE);
            domBuilt = true;
        }

        cmd.set("#PanelTitle.TextSpans",
                title != null ? coloredMsg(title) : Message.raw(""));
        cmd.set("#PanelSub.TextSpans",
                subtitle != null ? coloredMsg(subtitle) : Message.raw(""));

        for (int i = 1; i <= MAX_LINES; i++) {
            String lineId = "#Line" + i + ".TextSpans";
            if (i - 1 < lines.size()) {
                cmd.set(lineId, coloredMsg(lines.get(i - 1)));
            } else {
                cmd.set(lineId, Message.raw(""));
            }
        }

        // First send: clear=true (rebuild DOM); subsequent: clear=false
        update(firstSend, cmd);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Message coloredMsg(String s) {
        if (s != null && s.startsWith("#") && s.length() > 7) {
            return Message.raw(s.substring(7)).color(s.substring(0, 7));
        }
        return Message.raw(s != null ? s : "");
    }

    static List<SpellData> buildSpellList(GridPlayerState state, EncounterManager encounterManager,
                                          RoleManager roleManager) {
        List<SpellData> result = new ArrayList<>();
        MonsterState monster = encounterManager != null ? encounterManager.getControlledMonster() : null;

        if (monster != null) {
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
        } else if (state.stats != null && state.stats.getClassType() != null) {
            result.addAll(SpellDatabase.getAvailableSpells(
                    state.stats.getClassType(),
                    state.stats.getSubclassType(),
                    state.stats.getLevel()));
        }
        return result;
    }

    private static String fmt(double d) {
        if (d == Math.floor(d)) return String.valueOf((int) d);
        return String.format("%.1f", d);
    }
}