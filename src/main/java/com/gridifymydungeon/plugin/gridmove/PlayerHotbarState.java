package com.gridifymydungeon.plugin.gridmove;

import com.gridifymydungeon.plugin.spell.SpellData;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-player hotbar mode state.
 *
 * Tracks which hotbar mode is active, which spell is highlighted in
 * SPELL_SELECT mode, and the cached spell list for the current session.
 *
 * Stored inside GridPlayerState.hotbarState.
 */
public class PlayerHotbarState {

    // ── Hotbar modes ──────────────────────────────────────────────────────────
    public enum Mode {
        NONE,          // Key 1 — nothing active, NPC frozen
        MOVE,          // Key 2 — movement mode active
        SPELL_SELECT,  // Key 3 — selecting spell (crouch scrolls)
        CONFIRM,       // Key 4 — spell confirmed, crouch fires /cast <spell>
        TARGETING,     // Key 5 — crouch fires /casttarget
        CAST_FINAL,    // Key 6 — crouch fires /castfinal
        PROFILE,       // Key 7 — profile printed to chat
        LIST_SPELLS    // Key 8 — spell list printed to chat
    }

    // Current active mode
    public Mode activeMode = Mode.NONE;

    // ── Spell selection ───────────────────────────────────────────────────────
    // Cached list of spells available to this player (refreshed when entering SPELL_SELECT)
    public List<SpellData> spellList = new ArrayList<>();

    // Index into spellList of the currently highlighted spell (0-based)
    public int spellSelectIndex = 0;

    // True after the player confirmed a spell (pressed key 3 again) — panel shows cast status
    public boolean spellConfirmed = false;

    // ── Helpers ───────────────────────────────────────────────────────────────

    public void setMode(Mode mode) {
        this.activeMode = mode;
        if (mode == Mode.NONE || mode == Mode.MOVE) {
            spellConfirmed = false;
        }
    }

    /** Advance spell selection index by 1, wrapping around. */
    public void advanceSpellIndex() {
        if (spellList.isEmpty()) return;
        spellSelectIndex = (spellSelectIndex + 1) % spellList.size();
    }

    /** Return the currently highlighted spell, or null if list is empty. */
    public SpellData getSelectedSpell() {
        if (spellList.isEmpty() || spellSelectIndex >= spellList.size()) return null;
        return spellList.get(spellSelectIndex);
    }

    /** Reset to clean state (used on player disconnect or /gridoff). */
    public void reset() {
        activeMode = Mode.NONE;
        spellList.clear();
        spellSelectIndex = 0;
        spellConfirmed = false;
    }
}