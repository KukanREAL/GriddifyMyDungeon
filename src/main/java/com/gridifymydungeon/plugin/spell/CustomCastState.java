package com.gridifymydungeon.plugin.spell;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Holds the in-progress state for a /cast custom flow (GM custom monster attack).
 *
 * Flow:
 *   /cast custom                     → creates this state
 *   /cdmg {number}                   → sets flatDamage
 *   /cdicedmg {N}x{D} or {N}+{D}    → sets diceCount + diceSides
 *   (at least one of cdmg/cdicedmg must be set before targeting)
 *   /casttarget                      → unlimited range, no Grid_Range overlay
 *   /castfinal                       → rolls, resolves, reports damage
 */
public class CustomCastState {

    // Caster position (frozen at /cast custom time)
    public final int casterGridX;
    public final int casterGridZ;
    public final float casterY;

    // Damage components (at least one must be set before /casttarget)
    private Integer flatDamage = null;       // set by /cdmg
    private Integer diceCount  = null;       // set by /cdicedmg  (N in NxD)
    private Integer diceSides  = null;       // set by /cdicedmg  (D in NxD)

    // Confirmed target cells (set by /casttarget — unlimited, no cap)
    private final List<int[]> confirmedTargets = new ArrayList<>(); // each int[]{gridX, gridZ}

    // Aim position (updated live as player walks, used when /castfinal fires without /casttarget)
    private int aimGridX;
    private int aimGridZ;

    private static final Random RNG = new Random();

    public CustomCastState(int casterGridX, int casterGridZ, float casterY) {
        this.casterGridX = casterGridX;
        this.casterGridZ = casterGridZ;
        this.casterY     = casterY;
        this.aimGridX    = casterGridX;
        this.aimGridZ    = casterGridZ;
    }

    // ── Damage setters ────────────────────────────────────────────────────

    public void setFlatDamage(int dmg) { this.flatDamage = dmg; }
    public Integer getFlatDamage()     { return flatDamage; }

    public void setDice(int count, int sides) {
        this.diceCount = count;
        this.diceSides = sides;
    }
    public Integer getDiceCount() { return diceCount; }
    public Integer getDiceSides() { return diceSides; }

    /** Returns true if at least one damage source has been configured. */
    public boolean hasDamageConfigured() {
        return flatDamage != null || (diceCount != null && diceSides != null);
    }

    // ── Aim tracking ──────────────────────────────────────────────────────

    public void setAim(int gridX, int gridZ) { aimGridX = gridX; aimGridZ = gridZ; }
    public int getAimGridX() { return aimGridX; }
    public int getAimGridZ() { return aimGridZ; }

    // ── Target confirmation ───────────────────────────────────────────────

    public void confirmTarget(int gridX, int gridZ) {
        confirmedTargets.add(new int[]{gridX, gridZ});
    }
    public List<int[]> getConfirmedTargets() { return confirmedTargets; }
    public int getTargetCount()              { return confirmedTargets.size(); }
    public boolean hasTargets()              { return !confirmedTargets.isEmpty(); }

    // ── Damage rolling ────────────────────────────────────────────────────

    /**
     * Roll all dice and sum with flat damage.
     * Returns the total, and populates rollLog with a human-readable breakdown.
     */
    public int rollDamage(StringBuilder rollLog) {
        int total = 0;

        if (diceCount != null && diceSides != null) {
            rollLog.append(diceCount).append("x").append("d").append(diceSides).append(" → [");
            for (int i = 0; i < diceCount; i++) {
                int roll = RNG.nextInt(diceSides) + 1;
                total += roll;
                rollLog.append(roll);
                if (i < diceCount - 1) rollLog.append(", ");
            }
            rollLog.append("]");
            if (flatDamage != null) rollLog.append(" + ");
        }

        if (flatDamage != null) {
            total += flatDamage;
            rollLog.append("+").append(flatDamage).append(" flat");
        }

        return total;
    }

    /** Human-readable summary of the damage configuration for status messages. */
    public String damageSummary() {
        StringBuilder sb = new StringBuilder();
        if (diceCount != null && diceSides != null) {
            sb.append(diceCount).append("xd").append(diceSides);
        }
        if (flatDamage != null) {
            if (sb.length() > 0) sb.append(" + ");
            sb.append(flatDamage).append(" flat");
        }
        return sb.length() > 0 ? sb.toString() : "(none)";
    }
}