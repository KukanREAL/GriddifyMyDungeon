package com.gridifymydungeon.plugin.gridmove;

import com.gridifymydungeon.plugin.spell.SpellData;

import java.util.ArrayList;
import java.util.List;

public class PlayerHotbarState {

    public enum Mode {
        NONE,
        MOVE,
        SPELL_SELECT,
        CONFIRM_TARGET,  // key 4: first crouch = /cast, subsequent crouch = /casttarget
        CAST_FINAL,      // key 5
        INFO,            // key 8: profile + spell list, crouch swaps
        END_TURN_CONFIRM // key 9
    }

    public Mode activeMode = Mode.NONE;

    public List<SpellData> spellList = new ArrayList<>();
    public int spellSelectIndex = 0;
    public boolean spellConfirmed = false;

    // INFO mode: true = showing profile, false = showing spell list
    public boolean infoShowingProfile = true;

    public void setMode(Mode mode) {
        this.activeMode = mode;
        if (mode == Mode.NONE || mode == Mode.MOVE) {
            spellConfirmed = false;
        }
    }

    public void advanceSpellIndex() {
        if (spellList.isEmpty()) return;
        spellSelectIndex = (spellSelectIndex + 1) % spellList.size();
    }

    public SpellData getSelectedSpell() {
        if (spellList.isEmpty() || spellSelectIndex >= spellList.size()) return null;
        return spellList.get(spellSelectIndex);
    }

    public void reset() {
        activeMode = Mode.NONE;
        spellList.clear();
        spellSelectIndex = 0;
        spellConfirmed = false;
        infoShowingProfile = true;
    }
}