package com.gridifymydungeon.plugin.gridmove;

import com.gridifymydungeon.plugin.dnd.EncounterManager;
import com.gridifymydungeon.plugin.dnd.MonsterState;
import com.gridifymydungeon.plugin.dnd.RoleManager;
import com.gridifymydungeon.plugin.spell.SpellCastingState;
import com.gridifymydungeon.plugin.spell.SpellData;
import com.gridifymydungeon.plugin.spell.SpellDatabase;
import com.gridifymydungeon.plugin.spell.SpellPattern;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class GriddifyHud extends CustomUIHud {

    private static final int MAX_LINES = 12;
    private final PlayerRef playerRef;

    public GriddifyHud(@Nonnull PlayerRef playerRef) {
        super(playerRef);
        this.playerRef = playerRef;
    }

    @Override
    public void build(@Nonnull UICommandBuilder uiCommandBuilder) {
        uiCommandBuilder.append("GriddifyHud.ui");
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    public void updatePanel(GridPlayerState state, EncounterManager encounterManager,
                            RoleManager roleManager) {
        PlayerHotbarState hs = state.hotbarState;
        switch (hs.activeMode) {
            case NONE:             showIdle(state, encounterManager, roleManager); break;
            case MOVE:             showMove(state, encounterManager, roleManager); break;
            case SPELL_SELECT:     showSpellSelect(state); break;
            case CONFIRM_TARGET:   showConfirmTarget(state); break;
            case CAST_FINAL:       showCastFinalArmed(state); break;
            case STAT_EDIT:        showStatEdit(state, encounterManager, roleManager); break;
            case INFO:             showInfo(state, encounterManager, roleManager); break;
            case END_TURN_CONFIRM: showEndTurnConfirm(); break;
        }
    }

    public void clearPanel() {
        sendPanel("", "", new ArrayList<>());
    }

    public void updateRolePanel(boolean isGM) {
        UICommandBuilder cmd = new UICommandBuilder();
        if (isGM) {
            cmd.set("#HotkeyTitle.TextSpans", Message.raw("GM CONTROLS"));
            cmd.set("#HK1.TextSpans",         Message.raw("[1]  Cancel / Release"));
            cmd.set("#HK2.TextSpans",         Message.raw("[2]  Move + Grid"));
            cmd.set("#HK3.TextSpans",         Message.raw("[3]  Select attack"));
            cmd.set("#HK4.TextSpans",         Message.raw("[4]  Confirm + Target"));
            cmd.set("#HK5.TextSpans",         Message.raw("[5]  Fire / Cast"));
            cmd.set("#HK6.TextSpans",         Message.raw("[6]  —"));
            cmd.set("#HK7.TextSpans",         Message.raw("[7]  Edit Stats"));
            cmd.set("#HK8.TextSpans",         Message.raw("[8]  Monster info / Attacks"));
            cmd.set("#HK9.TextSpans",         Message.raw("[9]  End turn"));
            cmd.set("#HKCrouch.TextSpans",    Message.raw("Crouch = Confirm / Scroll / Save"));
        } else {
            cmd.set("#HotkeyTitle.TextSpans", Message.raw("PLAYER CONTROLS"));
            cmd.set("#HK1.TextSpans",         Message.raw("[1]  Cancel / Freeze NPC"));
            cmd.set("#HK2.TextSpans",         Message.raw("[2]  Move + Grid"));
            cmd.set("#HK3.TextSpans",         Message.raw("[3]  Select spell"));
            cmd.set("#HK4.TextSpans",         Message.raw("[4]  Confirm + Target"));
            cmd.set("#HK5.TextSpans",         Message.raw("[5]  Cast / Fire"));
            cmd.set("#HK6.TextSpans",         Message.raw("[6]  —"));
            cmd.set("#HK7.TextSpans",         Message.raw("[7]  Edit Stats"));
            cmd.set("#HK8.TextSpans",         Message.raw("[8]  Profile / Spells"));
            cmd.set("#HK9.TextSpans",         Message.raw("[9]  End turn"));
            cmd.set("#HKCrouch.TextSpans",    Message.raw("Crouch = Confirm / Scroll / Save"));
        }
        update(false, cmd);
    }

    // ── Format range helper ────────────────────────────────────────────────────

    static String formatRange(SpellData sp) {
        SpellPattern p = sp.getPattern();
        String base;
        switch (p) {
            case CONE:     base = "CONE";     break;
            case LINE:     base = "LINE";     break;
            case SPHERE:   base = "SPHERE";   break;
            case CYLINDER: base = "CYLINDER"; break;
            case CUBE:     base = "CUBE";     break;
            case AURA:     base = "AURA";     break;
            case CHAIN:    base = "CHAIN";    break;
            case WALL:     base = "WALL";     break;
            case SELF:     base = "SELF";     break;
            default:
                base = "range:" + (sp.getRangeGrids() > 0 ? sp.getRangeGrids() : "touch");
                break;
        }
        if (sp.getMaxTargets() > 1) base += " hits:" + sp.getMaxTargets();
        return base;
    }

    // ── Mode renderers ─────────────────────────────────────────────────────────

    private void showIdle(GridPlayerState state, EncounterManager em, RoleManager rm) {
        if (em == null || rm == null) { clearPanel(); return; }
        boolean isGM = rm.isGM(playerRef);
        List<String[]> l = new ArrayList<>();

        if (isGM) {
            MonsterState controlled = em.getControlledMonster();
            String ctrlName = controlled != null ? controlled.getDisplayName() : "None";
            add(l, "#FFA500", "Controlling: " + ctrlName);
            if (controlled != null) {
                add(l, "#AAAAAA", "Moves: " + fmt(controlled.remainingMoves)
                        + "/" + fmt(controlled.maxMoves)
                        + "  HP: " + controlled.stats.currentHP
                        + "/" + controlled.stats.maxHP);
            }
            add(l, "#AAAAAA", "");
            add(l, "#87CEEB", "/creature <name> <#>");
            add(l, "#87CEEB", "/control <#>  (0=stop)");
            add(l, "#87CEEB", "/combat  start/end");
            add(l, "#87CEEB", "/slain <#>  remove");
            add(l, "#87CEEB", "/grid  toggle map");
            add(l, "#87CEEB", "/gridweather {mode}");
            add(l, "#AAAAAA", "Monsters: " + em.getAllMonsters().size());
            sendPanel("GAME MASTER", null, l);
        } else {
            String cls = (state.stats != null && state.stats.getClassType() != null)
                    ? state.stats.getClassType().getDisplayName() + "  Lv." + state.stats.getLevel()
                    : "No class — /GridClass <name>";
            if (state.stats != null) {
                add(l, "#FFFFFF", "HP: " + state.stats.currentHP + "/" + state.stats.maxHP
                        + "  AC: " + state.stats.armor);
                if (state.stats.getClassType() != null && state.stats.getClassType().isSpellcaster()) {
                    add(l, "#87CEEB", "Slots: " + state.stats.getRemainingSpellSlots()
                            + "/" + state.stats.getSpellSlots());
                }
            }
            boolean npcActive = state.npcEntity != null && state.npcEntity.isValid();
            if (npcActive) {
                add(l, "#AAAAAA", "Moves: " + fmt(state.remainingMoves) + "/" + fmt(state.maxMoves));
            }
            add(l, "#AAAAAA", "");
            add(l, "#87CEEB", npcActive ? "/gridmove  despawn NPC" : "/gridmove  spawn NPC");
            add(l, "#87CEEB", "/gridon  show range");
            add(l, "#87CEEB", "/gridclass <name>");
            add(l, "#87CEEB", "/gridprofile  view stats");
            sendPanel(cls, null, l);
        }
    }

    private void showMove(GridPlayerState state, EncounterManager em, RoleManager rm) {
        List<String[]> l = new ArrayList<>();
        MonsterState controlled = em.getControlledMonster();
        if (controlled != null && rm.isGM(playerRef)) {
            add(l, "#00FF7F", "MOVE: " + controlled.getDisplayName());
            add(l, "#AAAAAA", "Moves: " + fmt(controlled.remainingMoves)
                    + "/" + fmt(controlled.maxMoves));
        } else {
            add(l, "#00FF7F", "MOVE MODE");
            add(l, "#AAAAAA", "Moves: " + fmt(state.remainingMoves) + "/" + fmt(state.maxMoves));
        }
        add(l, "#AAAAAA", "Walk to move your NPC");
        sendPanel("MOVE", "Walk to move", l);
    }

    private void showSpellSelect(GridPlayerState state) {
        PlayerHotbarState hs = state.hotbarState;
        List<SpellData> spells = hs.spellList;
        List<String[]> l = new ArrayList<>();
        if (spells == null || spells.isEmpty()) {
            add(l, "#AAAAAA", "(No spells available)");
        } else {
            SpellData sp = spells.get(hs.spellSelectIndex);
            String cost = sp.getSlotCost() > 0 ? sp.getSlotCost() + " slot(s)" : "cantrip";
            String dmg = sp.getDamageDice() != null
                    ? sp.getDamageDice() + " " + sp.getDamageType().name().toLowerCase() : "effect";
            add(l, "#FFD700", sp.getName());
            add(l, "#AAAAAA", cost + "  |  " + formatRange(sp));
            add(l, "#AAAAAA", "dmg: " + dmg);
            add(l, "#AAAAAA", "pattern: " + sp.getPattern().name().toLowerCase());
            add(l, "#AAAAAA", "");
            if (spells.size() > 1) {
                int prev = (hs.spellSelectIndex - 1 + spells.size()) % spells.size();
                int next = (hs.spellSelectIndex + 1) % spells.size();
                add(l, "#808080", "  " + spells.get(prev).getName());
                add(l, "#FFD700", "> " + sp.getName()
                        + "  (" + (hs.spellSelectIndex + 1) + "/" + spells.size() + ")");
                add(l, "#808080", "  " + spells.get(next).getName());
            }
        }
        sendPanel("SELECT SPELL", "Crouch:scroll  [4]:confirm  [1]:cancel", l);
    }

    private void showConfirmTarget(GridPlayerState state) {
        if (!state.hotbarState.spellConfirmed) {
            SpellData spell = state.hotbarState.getSelectedSpell();
            List<String[]> l = new ArrayList<>();
            if (spell != null) {
                String dmg = spell.getDamageDice() != null
                        ? spell.getDamageDice() + " " + spell.getDamageType().name().toLowerCase()
                        : "effect";
                add(l, "#FFD700", spell.getName());
                add(l, "#AAAAAA", formatRange(spell));
                add(l, "#AAAAAA", "dmg: " + dmg);
                add(l, "#AAAAAA", "");
                add(l, "#FFD700", "Crouch to cast");
            } else {
                add(l, "#AAAAAA", "No spell selected");
            }
            sendPanel("CONFIRM SPELL", "[1]:cancel", l);
        } else {
            SpellCastingState cast = state.getSpellCastingState();
            if (cast == null) {
                List<String[]> l = new ArrayList<>();
                add(l, "#AAAAAA", "No active spell");
                sendPanel("TARGETING", "[1]:cancel", l);
                return;
            }
            SpellData sp = cast.getSpell();
            List<String[]> l = new ArrayList<>();
            String dmg = sp.getDamageDice() != null
                    ? sp.getDamageDice() + " " + sp.getDamageType().name().toLowerCase()
                    : sp.getDescription();
            add(l, "#FF6B6B", sp.getName());
            add(l, "#AAAAAA", "Dmg: " + dmg);
            add(l, "#AAAAAA", formatRange(sp));
            if (sp.getMaxTargets() > 1) {
                add(l, "#87CEEB", "Targets: " + cast.getConfirmedTargetCount()
                        + "/" + sp.getMaxTargets());
                add(l, "#AAAAAA", "Crouch: add target");
            } else {
                add(l, "#AAAAAA", "Walk to aim");
            }
            add(l, "#AAAAAA", "[5]+crouch: FIRE  [1]: cancel");
            sendPanel("TARGETING", "", l);
        }
    }

    private void showCastFinalArmed(GridPlayerState state) {
        SpellCastingState cast = state.getSpellCastingState();
        String name = cast != null ? cast.getSpell().getName() : "?";
        List<String[]> l = new ArrayList<>();
        add(l, "#FF4500", "Crouch to FIRE!");
        add(l, "#AAAAAA", "[1]: abort");
        sendPanel("ARMED: " + name, "", l);
    }

    private void showStatEdit(GridPlayerState state, EncounterManager em, RoleManager rm) {
        List<String[]> l = new ArrayList<>();
        boolean isGM = rm != null && rm.isGM(playerRef);
        MonsterState monster = (isGM && em != null) ? em.getControlledMonster() : null;

        String subject = monster != null
                ? monster.getDisplayName()
                : (state.stats != null && state.stats.getClassType() != null
                ? state.stats.getClassType().getDisplayName() : "Character");

        add(l, "#FFD700", "Editing: " + subject);
        add(l, "#AAAAAA", "");
        add(l, "#00FF7F", "Change values in the editor.");
        add(l, "#00FF7F", "Crouch to save changes.");
        add(l, "#AAAAAA", "[7] to cancel without saving.");
        add(l, "#AAAAAA", "[1] to cancel without saving.");
        sendPanel("STAT EDITOR", "Crouch to save  [7] to cancel", l);
    }

    private void showInfo(GridPlayerState state, EncounterManager em, RoleManager rm) {
        if (state.hotbarState.infoShowingProfile) {
            showProfile(state, em, rm);
        } else {
            showSpellList(state, em, rm);
        }
    }

    private void showProfile(GridPlayerState state, EncounterManager em, RoleManager rm) {
        List<String[]> l = new ArrayList<>();
        boolean isGM = rm != null && rm.isGM(playerRef);
        MonsterState monster = (isGM && em != null) ? em.getControlledMonster() : null;

        if (monster != null) {
            add(l, "#FF69B4", monster.getDisplayName() + "  #" + monster.monsterNumber);
            add(l, "#FFFFFF", "HP: " + monster.stats.currentHP + "/" + monster.stats.maxHP
                    + "  AC: " + monster.stats.armor);
            add(l, "#AAAAAA", "Moves: " + fmt(monster.remainingMoves)
                    + "/" + fmt(monster.maxMoves)
                    + (monster.isFlying ? "  [FLY]" : ""));
            if (monster.monsterType != null) {
                add(l, "#FFA500", "Type: " + monster.monsterType.name());
            }
            if (monster.stats.getClassType() != null) {
                add(l, "#87CEEB", "Class: " + monster.stats.getClassType().getDisplayName()
                        + "  Lv." + monster.stats.getLevel());
            }
            add(l, "#FFFFFF", "STR " + monster.stats.strength
                    + "  DEX " + monster.stats.dexterity
                    + "  CON " + monster.stats.constitution);
            add(l, "#FFFFFF", "INT " + monster.stats.intelligence
                    + "  WIS " + monster.stats.wisdom
                    + "  CHA " + monster.stats.charisma);
            sendPanel("MONSTER INFO", "Crouch:attacks  [1]:close", l);
        } else {
            if (state.stats == null) {
                add(l, "#AAAAAA", "(No stats - use /GridClass)");
            } else {
                String cls = state.stats.getClassType() != null
                        ? state.stats.getClassType().getDisplayName() : "None";
                String sub = state.stats.getSubclassType() != null
                        ? " / " + state.stats.getSubclassType().getDisplayName() : "";
                add(l, "#00BFFF", cls + sub + "  Lv." + state.stats.getLevel());
                add(l, "#FFFFFF", "HP: " + state.stats.currentHP + "/" + state.stats.maxHP
                        + "  AC: " + state.stats.armor);
                add(l, "#FFFFFF", "Slots: " + state.stats.getRemainingSpellSlots()
                        + "/" + state.stats.getSpellSlots());
                add(l, "#FFFFFF", "STR " + state.stats.strength
                        + "  DEX " + state.stats.dexterity
                        + "  CON " + state.stats.constitution);
                add(l, "#FFFFFF", "INT " + state.stats.intelligence
                        + "  WIS " + state.stats.wisdom
                        + "  CHA " + state.stats.charisma);
                add(l, "#FFFFFF", "Init: " + (state.stats.initiative >= 0 ? "+" : "")
                        + state.stats.initiative + "  Moves: " + fmt(state.maxMoves));
            }
            sendPanel("PROFILE", "Crouch:spell list  [1]:close", l);
        }
    }

    private void showSpellList(GridPlayerState state, EncounterManager em, RoleManager rm) {
        List<SpellData> spells = buildSpellList(state, em, rm);
        List<String[]> l = new ArrayList<>();
        if (spells.isEmpty()) {
            add(l, "#AAAAAA", "(No spells - use /GridClass first)");
        } else {
            for (int i = 0; i < spells.size() && i < MAX_LINES; i++) {
                SpellData sp = spells.get(i);
                String dmg = sp.getDamageDice() != null ? sp.getDamageDice() : "effect";
                add(l, "#FFFFFF", (i + 1) + ". " + sp.getName()
                        + "  " + dmg + "  " + formatRange(sp));
            }
            if (spells.size() > MAX_LINES)
                l.set(MAX_LINES - 1,
                        new String[]{"#AAAAAA", "+" + (spells.size() - MAX_LINES + 1) + " more"});
        }
        boolean isGM = rm != null && rm.isGM(playerRef);
        String slots = (!isGM && state.stats != null)
                ? "Slots " + state.stats.getRemainingSpellSlots()
                + "/" + state.stats.getSpellSlots() + "  " : "";
        String subtitle = isGM
                ? "Crouch:monster info  [1]:close"
                : slots + "Crouch:profile  [1]:close";
        sendPanel(isGM ? "ATTACKS" : "SPELLS", subtitle, l);
    }

    private void showEndTurnConfirm() {
        List<String[]> l = new ArrayList<>();
        add(l, "#FF4500", "Crouch to confirm!");
        add(l, "#AAAAAA", "[1] or [2]: cancel");
        sendPanel("END TURN?", "Crouch to confirm", l);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void add(List<String[]> lines, String color, String text) {
        lines.add(new String[]{color, text});
    }

    private void sendPanel(String title, String subtitle, List<String[]> lines) {
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#PanelTitle.TextSpans", Message.raw(title != null ? title : ""));
        cmd.set("#PanelSub.TextSpans",   Message.raw(subtitle != null ? subtitle : ""));
        for (int i = 1; i <= MAX_LINES; i++) {
            String id = "#Line" + i + ".TextSpans";
            if (i - 1 < lines.size()) {
                String[] e = lines.get(i - 1);
                cmd.set(id, Message.raw(e[1]).color(e[0]));
            } else {
                cmd.set(id, Message.raw(""));
            }
        }
        update(false, cmd);
    }

    // ── Static helpers ────────────────────────────────────────────────────────

    static List<SpellData> buildSpellList(GridPlayerState state, EncounterManager em, RoleManager rm) {
        List<SpellData> result = new ArrayList<>();
        MonsterState monster = em != null ? em.getControlledMonster() : null;
        if (monster != null) {
            com.gridifymydungeon.plugin.spell.MonsterType mType = monster.monsterType;
            if (mType != null) result.addAll(SpellDatabase.getAttacksForMonsterType(mType));
            if (monster.stats.getClassType() != null)
                result.addAll(SpellDatabase.getAvailableSpells(
                        monster.stats.getClassType(),
                        monster.stats.getSubclassType(),
                        Math.max(1, monster.stats.getLevel())));
        } else if (state.stats != null && state.stats.getClassType() != null) {
            result.addAll(SpellDatabase.getAvailableSpells(
                    state.stats.getClassType(),
                    state.stats.getSubclassType(),
                    state.stats.getLevel()));
        }
        return result;
    }

    private static String fmt(double d) {
        return d == Math.floor(d) ? String.valueOf((int) d) : String.format("%.1f", d);
    }
}