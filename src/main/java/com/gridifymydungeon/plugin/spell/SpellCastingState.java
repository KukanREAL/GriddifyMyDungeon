package com.gridifymydungeon.plugin.spell;

import com.gridifymydungeon.plugin.dnd.MonsterState;

/**
 * Tracks the spell being prepared between /cast and /castfinal.
 *
 * For NPC-origin directional spells (CONE, LINE, WALL):
 *   - NPC is frozen at casterGridX/Z
 *   - direction updates live based on where the player body stands relative to the NPC
 *   - aimGridX/Z tracks the player's current body position (used to derive direction)
 *
 * For targeted spells (SINGLE_TARGET, SPHERE, CUBE, etc.):
 *   - NPC is frozen at casterGridX/Z
 *   - aimGridX/Z is the aimed target cell (player body position)
 *
 * For self spells (SELF, AURA):
 *   - No freeze, fires immediately.
 */
public class SpellCastingState {
    private final SpellData spell;
    private final int casterGridX;
    private final int casterGridZ;
    private final float casterY;   // ground Y of the caster NPC/monster — used for spell grid placement
    private final long castStartTime;

    // For directional spells: direction updates as player walks around NPC
    private Direction8 direction;

    // Player's current body grid position (aim for targeted, position for directional)
    private int aimGridX;
    private int aimGridZ;

    // Multi-target: list of confirmed target cells (player walks to each and confirms with a key/move)
    private final java.util.List<GridCell> confirmedTargets = new java.util.ArrayList<>();

    /**
     * Full confirmed pattern cells — set by /CastTarget for ALL spell patterns.
     * Uses SpellPatternCalculator.GridCell directly (same type that computeOverlay produces)
     * so no conversion is needed in CastTargetCommand or CastFinalCommand.
     * Null means the player has not used /CastTarget yet (aim follows body position).
     */
    private java.util.Set<SpellPatternCalculator.GridCell> confirmedCells = null;

    // Out-of-range tracking: warn once, don't cancel; block /CastTarget and /CastFinal
    private boolean currentlyOutOfRange = false;

    // Chromatic Orb: chosen element (acid/fire/cold/lightning/poison/thunder), null = not yet chosen
    private String chromaticElement = null;

    // Polymorph: the target cell set by /castfinal, awaiting /polyform for form choice
    private GridCell pendingPolymorphTarget = null;

    // Wild Shape: true while player is transformed
    private boolean wildShapeActive = false;
    private String wildShapeForm = null; // e.g. "Wild_Shape_Bear"


    public static class GridCell {
        public final int x, z;
        public GridCell(int x, int z) { this.x = x; this.z = z; }
    }

    public SpellCastingState(SpellData spell, MonsterState ignoredTarget,
                             Direction8 initialDirection, int casterGridX, int casterGridZ,
                             float casterY) {
        this.spell = spell;
        this.direction = initialDirection;
        this.casterGridX = casterGridX;
        this.casterGridZ = casterGridZ;
        this.casterY = casterY;
        this.aimGridX = casterGridX;
        this.aimGridZ = casterGridZ;
        this.castStartTime = System.currentTimeMillis();
    }

    /** Legacy constructor without casterY — defaults to 0 (SpellVisualManager will use npcY fallback) */
    public SpellCastingState(SpellData spell, MonsterState ignoredTarget,
                             Direction8 initialDirection, int casterGridX, int casterGridZ) {
        this(spell, ignoredTarget, initialDirection, casterGridX, casterGridZ, 0f);
    }

    public SpellData getSpell() { return spell; }
    public int getCasterGridX() { return casterGridX; }
    public int getCasterGridZ() { return casterGridZ; }
    public float getCasterY()   { return casterY; }
    public Direction8 getDirection() { return direction; }

    /**
     * Called by PlayerPositionTracker when the player body moves.
     * Updates aim position and — for directional spells — recalculates direction
     * from the player's position relative to the frozen NPC.
     */
    public void updatePlayerPosition(int playerGridX, int playerGridZ) {
        this.aimGridX = playerGridX;
        this.aimGridZ = playerGridZ;

        SpellPattern pattern = spell.getPattern();
        if (pattern == SpellPattern.CONE || pattern == SpellPattern.LINE || pattern == SpellPattern.WALL) {
            // Directional: player walks AROUND NPC to rotate
            this.direction = directionFromRelativePosition(playerGridX, playerGridZ);
        } else if (pattern == SpellPattern.SINGLE_TARGET) {
            // Aim-based: direction = caster facing toward the aim cell
            int dx = playerGridX - casterGridX;
            int dz = playerGridZ - casterGridZ;
            if (dx != 0 || dz != 0) {
                this.direction = directionFromRelativePosition(playerGridX, playerGridZ);
            }
        }
    }

    /**
     * Derive the 8-directional facing from where the player body is relative to the NPC.
     * If the player is on the same cell as the NPC, keep existing direction.
     */
    private Direction8 directionFromRelativePosition(int playerGridX, int playerGridZ) {
        int dx = playerGridX - casterGridX;
        int dz = playerGridZ - casterGridZ;

        if (dx == 0 && dz == 0) return direction; // same cell, keep current

        // Normalise to -1/0/1
        int sx = Integer.compare(dx, 0);
        int sz = Integer.compare(dz, 0);

        for (Direction8 d : Direction8.values()) {
            if (d.getDeltaX() == sx && d.getDeltaZ() == sz) return d;
        }
        return direction; // fallback
    }

    public int getAimGridX() { return aimGridX; }
    public int getAimGridZ() { return aimGridZ; }

    /**
     * Confirm the current aim cell as a target.
     * - Same cell CAN be confirmed multiple times (each hit on the same spot is valid,
     *   and shows a smaller Grid_Spell overlay entity each time).
     * - For multi-target spells (maxTargets > 1): capped at maxTargets total.
     * - For single-target spells: uncapped — player decides when to /CastFinal.
     * Always returns true (caller can always add another target unless multi-target cap).
     */
    public boolean confirmTarget() {
        if (confirmedTargets.size() >= spell.getMaxTargets()) return false;
        confirmedTargets.add(new GridCell(aimGridX, aimGridZ));
        return true;
    }

    /** How many times the cell (x, z) has been confirmed as a target. */
    public int getTargetCountAt(int x, int z) {
        int count = 0;
        for (GridCell c : confirmedTargets) {
            if (c.x == x && c.z == z) count++;
        }
        return count;
    }

    public java.util.List<GridCell> getConfirmedTargets() { return confirmedTargets; }
    public int getConfirmedTargetCount() { return confirmedTargets.size(); }
    public boolean hasAllTargets() { return confirmedTargets.size() >= spell.getMaxTargets(); }

    // ── Confirmed pattern cells (set by /CastTarget) ─────────────────────────

    /**
     * Lock in the full pattern snapshot from /CastTarget.
     * Takes SpellPatternCalculator.GridCell directly — no conversion needed.
     * Also records the aim point in confirmedTargets for range validation.
     */
    public void setConfirmedCells(java.util.Set<SpellPatternCalculator.GridCell> cells, int aimX, int aimZ) {
        this.confirmedCells = new java.util.HashSet<>(cells);
        // Do NOT touch confirmedTargets here — CastTargetCommand calls confirmTarget()
        // immediately after, which adds the aim cell. Pre-adding it here would double-count.
    }

    /** Returns the confirmed pattern cells as SpellPatternCalculator.GridCell, or null if not yet confirmed. */
    public java.util.Set<SpellPatternCalculator.GridCell> getConfirmedCells() { return confirmedCells; }

    public boolean hasConfirmedCells() { return confirmedCells != null && !confirmedCells.isEmpty(); }

    // ── Chromatic Orb ────────────────────────────────────────────────────────

    public void setChromaticElement(String element) { this.chromaticElement = element; }
    public String getChromaticElement() { return chromaticElement; }
    public boolean hasChromaticElement() { return chromaticElement != null; }

    public void setPendingPolymorphTarget(GridCell cell) { this.pendingPolymorphTarget = cell; }
    public GridCell getPendingPolymorphTarget() { return pendingPolymorphTarget; }
    public void clearPendingPolymorphTarget() { this.pendingPolymorphTarget = null; }

    // ── Wild Shape ────────────────────────────────────────────────────────────

    public void setWildShapeActive(boolean active, String form) {
        this.wildShapeActive = active;
        this.wildShapeForm = form;
    }
    public boolean isWildShapeActive() { return wildShapeActive; }
    public String getWildShapeForm() { return wildShapeForm; }

    // ── Out-of-range tracking ─────────────────────────────────────────────────

    /**
     * Called by PlayerPositionTracker each time the player moves.
     * Returns true if this is a NEW out-of-range transition (warning should be sent once).
     */
    public boolean setOutOfRange(boolean outOfRange) {
        boolean wasOut = currentlyOutOfRange;
        currentlyOutOfRange = outOfRange;
        return outOfRange && !wasOut; // true = newly out of range
    }

    public boolean isOutOfRange() { return currentlyOutOfRange; }

    /** Cast state expires after 60 seconds */
    public boolean isValid() {
        return (System.currentTimeMillis() - castStartTime) < 60000;
    }
}