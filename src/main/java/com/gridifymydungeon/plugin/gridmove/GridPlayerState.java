package com.gridifymydungeon.plugin.gridmove;

import com.gridifymydungeon.plugin.dnd.CharacterStats;
import com.gridifymydungeon.plugin.spell.SpellCastingState;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks player grid movement state.
 *
 * FIX #1: Added rangeOverlay list for Grid_Range tiles (spell reach ring).
 * FIX #6: Added fogMarkerRef2/fogMarkerNetId2 for the outer 13x13 fog layer.
 */
public class GridPlayerState {

    // Reference to PlayerRef for easy access by commands
    public PlayerRef playerRef;

    // Grid tracking
    public int currentGridX;
    public int currentGridZ;
    public final double gridSize;

    // Movement tracking
    public double maxMoves = 6.0;
    public double remainingMoves = 6.0;
    public boolean maxMovesLocked = false;
    public Vector3d lastPlayerPosition;

    // NPC entity reference
    public Ref<EntityStore> npcEntity;
    public float npcY;

    // Freeze tracking
    public boolean isFrozen = false;
    public String freezeReason = null;
    /** True after the first frozen message has been sent this freeze cycle. */
    public boolean frozenMessageSent = false;
    /** Grid position where the NPC was frozen (used by post_cast unfreeze: player must walk back here). */
    public int frozenGridX = 0;
    public int frozenGridZ = 0;

    // Character stats
    public CharacterStats stats;

    // Direction holograms (WASD indicators)
    public Ref<EntityStore> northHologram;
    public Ref<EntityStore> southHologram;
    public Ref<EntityStore> eastHologram;
    public Ref<EntityStore> westHologram;
    public Ref<EntityStore> northEastHologram;
    public Ref<EntityStore> northWestHologram;
    public Ref<EntityStore> southEastHologram;
    public Ref<EntityStore> southWestHologram;

    // Grid overlay — movement range tiles (Grid_Basic / Grid_Player)
    public List<Ref<EntityStore>> gridOverlay = new ArrayList<>();
    public boolean gridOverlayEnabled = false;
    /** True when the overlay is the GM static /grid map, false when it's BFS range. */
    public boolean gmMapOverlayActive = false;

    // FIX: Grid tile reuse map — tracks entities by grid coordinates for teleportation
    // Key: "x,z" (e.g., "5,10"), Value: EntityStore Ref
    public java.util.Map<String, Ref<EntityStore>> gridTileMap = new java.util.HashMap<>();

    // FIX: Track if grid tiles have been hidden from other players (only hide once, not on every teleport)
    public boolean gridTilesHiddenFromOthers = false;

    // ── SPELL RANGE overlay — Grid_Range tiles shown during /cast ───────────
    // FIX #1: Stored per-player so clearRangeOverlay() can remove them at /castfinal or /castcancel
    public List<Ref<EntityStore>> rangeOverlay = new ArrayList<>();

    // One-time "no moves" message — reset each turn
    public boolean noMovesMessageShown = false;

    // Spell casting state
    private SpellCastingState spellCastingState = null;

    // Action economy: only 1 action (spell/attack) per turn
    public boolean hasUsedAction = false;

    // ── Fog-of-war markers ────────────────────────────────────────────────────
    // FIX #6: Two fog layers — inner 11×11 and outer 13×13 ring
    public Ref<EntityStore> fogMarkerRef    = null;   // inner  11×11 (scale 5.5f)
    public int              fogMarkerNetId  = -1;
    public Ref<EntityStore> fogMarkerRef2   = null;   // outer  13×13 (scale 6.5f)
    public int              fogMarkerNetId2 = -1;

    // ── Equipment snapshot (taken at /gridmove, re-sent to late viewers) ─────
    public String[] storedArmorIds   = null;
    public String   storedRightHand  = null;
    public String   storedLeftHand   = null;

    public SpellCastingState getSpellCastingState() { return spellCastingState; }
    public void setSpellCastingState(SpellCastingState state) { this.spellCastingState = state; }
    public void clearSpellCastingState() { this.spellCastingState = null; }

    public GridPlayerState() {
        this.gridSize = 2.0;
        this.currentGridX = 0;
        this.currentGridZ = 0;
        this.stats = new CharacterStats();
    }

    public boolean hasMaxMovesSet() {
        return maxMoves >= 0;
    }

    public void setMaxMoves(double moves) {
        this.maxMoves = moves;
        this.remainingMoves = moves;
    }

    public boolean hasMovesRemaining(double cost) {
        return remainingMoves >= cost;
    }

    public void consumeMoves(double cost) {
        remainingMoves -= cost;
        if (remainingMoves < 0) remainingMoves = 0;
    }

    public void resetMoves() {
        if (hasMaxMovesSet()) remainingMoves = maxMoves;
        noMovesMessageShown = false;
        hasUsedAction = false;
    }

    public void freeze(String reason) {
        this.isFrozen = true;
        this.freezeReason = reason;
        this.frozenGridX = this.currentGridX;
        this.frozenGridZ = this.currentGridZ;
    }

    public void unfreeze() {
        this.isFrozen = false;
        this.freezeReason = null;
        this.frozenMessageSent = false;
    }
}