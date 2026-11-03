package com.gridifymydungeon.plugin.gridmove;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks difficult terrain cells across the battlefield.
 *
 * Difficult terrain doubles movement cost (1 grid costs 2 moves instead of 1).
 *
 * Sources of difficult terrain:
 *   - Entangle spell (spell-placed cells registered via addDifficultCell)
 *   - Natural terrain blocks: Soil_Mud, Rock_Ice, Rock_Ice_Permafrost (standing ON)
 *   - Natural objects: Plant_Bramble_*, Deco_SpiderWeb*, Plant_Vine_Rug, Deco_Scarak_Spit (standing IN)
 *   - Weather mode: DIFFICULT (every cell is difficult)
 *
 * Grid overlay: cells with difficult terrain show Grid_Difficult instead of Grid_Player.
 */
public class TerrainManager {

    // ── Spell-placed difficult cells (Entangle, etc.) ─────────────────────────
    private static final Set<Long> spellDifficultCells = ConcurrentHashMap.newKeySet();

    // ── Weather mode ──────────────────────────────────────────────────────────
    public enum WeatherMode {
        NORMAL,     // standard movement costs
        DIFFICULT,  // every cell costs 2× moves
        SWIFT,      // every move costs 0.5×
        CHASE       // movement is free (no cost)
    }
    private static volatile WeatherMode weatherMode = WeatherMode.NORMAL;

    // ── Block IDs that create difficult terrain ───────────────────────────────
    // Standing ON top of these (surface block)
    private static final Set<String> DIFFICULT_SURFACE_BLOCKS = Set.of(
            "Soil_Mud",
            "Rock_Ice",
            "Rock_Ice_Permafrost"
    );

    // Standing IN/AMONG these (at-player-height block — flora, webs, vines)
    private static final Set<String> DIFFICULT_FLORA_BLOCKS = Set.of(
            "Plant_Bramble_Moss_Twisted",
            "Plant_Bramble_Dry_Twisted",
            "Plant_Bramble_Dead_Twisted",
            "Deco_SpiderWeb_Flat",
            "Plant_Vine_Rug",
            "Deco_SpiderWeb",
            "Deco_Scarak_Spit"
    );

    // ── Key encoding ──────────────────────────────────────────────────────────
    private static long cellKey(int x, int z) { return ((long) x << 32) | (z & 0xFFFFFFFFL); }

    // ── Spell-placed difficult cells ──────────────────────────────────────────
    public static void addDifficultCell(int gridX, int gridZ) {
        spellDifficultCells.add(cellKey(gridX, gridZ));
    }
    public static void removeDifficultCell(int gridX, int gridZ) {
        spellDifficultCells.remove(cellKey(gridX, gridZ));
    }
    public static void clearDifficultCells() { spellDifficultCells.clear(); }

    // ── Weather ───────────────────────────────────────────────────────────────
    public static WeatherMode getWeatherMode() { return weatherMode; }
    public static void setWeatherMode(WeatherMode mode) { weatherMode = mode; }

    // ── Main check: is a grid cell difficult for movement? ────────────────────
    /**
     * Returns true if the cell at (gridX, gridZ) counts as difficult terrain.
     * world may be null (skips block scan, only checks spell-placed cells + weather).
     */
    public static boolean isDifficult(int gridX, int gridZ, float playerY, World world) {
        // Weather overrides
        if (weatherMode == WeatherMode.DIFFICULT) return true;

        // Spell-placed cells (Entangle etc.)
        if (spellDifficultCells.contains(cellKey(gridX, gridZ))) return true;

        // Block scan (only when world is available)
        if (world != null) {
            return hasNaturalDifficultTerrain(world, gridX, gridZ, playerY);
        }
        return false;
    }

    /**
     * Movement multiplier for the given cell.
     *   CHASE   → 0.0 (free)
     *   SWIFT   → 0.5×
     *   normal  → 1.0 or 2.0 (difficult)
     */
    public static double getMoveCostMultiplier(int gridX, int gridZ, float playerY, World world) {
        switch (weatherMode) {
            case CHASE:     return 0.0;
            case SWIFT:     return 0.5;
            case DIFFICULT: return 2.0;
            default:        break;
        }
        return isDifficult(gridX, gridZ, playerY, world) ? 2.0 : 1.0;
    }

    // ── Block scan ────────────────────────────────────────────────────────────
    private static boolean hasNaturalDifficultTerrain(World world, int gridX, int gridZ, float playerY) {
        try {
            int blockY = (int) Math.floor(playerY);
            // Check all 4 blocks of the 2×2 grid cell
            int[] xs = { gridX * 2, gridX * 2 + 1 };
            int[] zs = { gridZ * 2, gridZ * 2 + 1 };
            for (int wx : xs) {
                for (int wz : zs) {
                    // Block the player stands ON (surface)
                    String surfaceId = getBlockId(world, wx, blockY - 1, wz);
                    if (surfaceId != null && DIFFICULT_SURFACE_BLOCKS.contains(surfaceId)) return true;
                    // Block at feet level (flora, webs, vines)
                    String feetId = getBlockId(world, wx, blockY, wz);
                    if (feetId != null && DIFFICULT_FLORA_BLOCKS.contains(feetId)) return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static String getBlockId(World world, int worldX, int blockY, int worldZ) {
        try {
            BlockType bt = world.getBlockType(new Vector3i(worldX, blockY, worldZ));
            return bt != null ? bt.getId() : null;
        } catch (Exception e) { return null; }
    }

    // ── Grid overlay helper: should this cell show Grid_Difficult? ────────────
    public static boolean shouldShowDifficultOverlay(int gridX, int gridZ, float npcY, World world) {
        return isDifficult(gridX, gridZ, npcY, world);
    }
}