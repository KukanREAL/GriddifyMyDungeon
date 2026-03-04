package com.gridifymydungeon.plugin.gridmove;

import com.gridifymydungeon.plugin.dnd.CharacterStats;
import com.gridifymydungeon.plugin.spell.CustomCastState;
import com.gridifymydungeon.plugin.spell.SpellCastingState;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks player grid movement state
 * FIXED v6: Added hotbarState, customCastState, hud fields
 */
public class GridPlayerState {

    // ADDED: Reference to PlayerRef for easy access by commands
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

    // Hotbar state (tracks active mode, spell selection, etc.)
    public PlayerHotbarState hotbarState = new PlayerHotbarState();

    // HUD panel (CustomUI top-left panel, created lazily on first use)
    public GriddifyHud hud;

    // Direction holograms (WASD indicators)
    public Ref<EntityStore> northHologram;
    public Ref<EntityStore> southHologram;
    public Ref<EntityStore> eastHologram;
    public Ref<EntityStore> westHologram;
    public Ref<EntityStore> northEastHologram;
    public Ref<EntityStore> northWestHologram;
    public Ref<EntityStore> southEastHologram;
    public Ref<EntityStore> southWestHologram;

    // Grid overlay — variable number of Grid_Quarter entities (4 per reachable cell)
    public List<Ref<EntityStore>> gridOverlay = new ArrayList<>();
    public boolean gridOverlayEnabled = false;
    /** True when the overlay is the GM 100x100 flat map (/grid), false when it's BFS range (/gridon). */
    public boolean gmMapOverlayActive = false;

    // One-time "no moves" message — reset each turn
    public boolean noMovesMessageShown = false;

    // Spell casting state
    private SpellCastingState spellCastingState = null;

    // Custom cast state (GM custom monster attack via /cast custom)
    private CustomCastState customCastState = null;

    // Action economy: only 1 action (spell/attack) per turn
    public boolean hasUsedAction = false;

    // ── Fog-of-war markers (private entities above NPC, visible only to owner) ──
    public Ref<EntityStore> fogMarkerRef   = null;
    public int              fogMarkerNetId = -1;
    public Ref<EntityStore> fogMarkerRef2   = null;
    public int              fogMarkerNetId2 = -1;

    // ── Grid overlay tile pool / map (used by GridOverlayManager) ──
    public final java.util.Map<String, Ref<EntityStore>> gridTileMap = new java.util.LinkedHashMap<>();
    public final java.util.Map<String, Object[]>         ledgeTileMap = new java.util.LinkedHashMap<>();
    public final java.util.List<Ref<EntityStore>>        gridTilePool = new java.util.ArrayList<>();
    public final java.util.Map<String, Integer>          gridTileNetIds = new java.util.HashMap<>();
    public final java.util.Map<Long, Float>              groundYCache = new java.util.HashMap<>();
    public int     prevBfsX = Integer.MIN_VALUE;
    public int     prevBfsZ = Integer.MIN_VALUE;
    public boolean gridTilesHiddenFromOthers = false;

    // ── Equipment snapshot (taken at /gridmove, re-sent to late viewers) ──
    public String[] storedArmorIds   = null;
    public String   storedRightHand  = null;
    public String   storedLeftHand   = null;

    public SpellCastingState getSpellCastingState() { return spellCastingState; }
    public void setSpellCastingState(SpellCastingState state) { this.spellCastingState = state; }
    public void clearSpellCastingState() { this.spellCastingState = null; }

    // ── Custom cast state (GM /cast custom) ───────────────────────────────────
    public CustomCastState getCustomCastState() { return customCastState; }
    public void setCustomCastState(CustomCastState state) { this.customCastState = state; }
    public void clearCustomCastState() { this.customCastState = null; }
    public boolean hasActiveCustomCast() { return customCastState != null; }

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
        if (remainingMoves < 0) {
            remainingMoves = 0;
        }
    }

    public void resetMoves() {
        if (hasMaxMovesSet()) {
            remainingMoves = maxMoves;
        }
        noMovesMessageShown = false;
        hasUsedAction = false; // Reset action for new turn
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