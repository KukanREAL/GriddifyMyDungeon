package com.gridifymydungeon.plugin.spell;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Calculate affected grid cells for spell patterns
 * Uses 8-directional system (N, NE, E, SE, S, SW, W, NW)
 */
public class SpellPatternCalculator {

    /**
     * Grid cell position
     */
    public static class GridCell {
        public final int x;
        public final int z;

        public GridCell(int x, int z) {
            this.x = x;
            this.z = z;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof GridCell)) return false;
            GridCell cell = (GridCell) o;
            return x == cell.x && z == cell.z;
        }

        @Override
        public int hashCode() {
            return 31 * x + z;
        }
    }

    /**
     * Calculate affected cells for a spell pattern
     *
     * @param pattern Spell pattern type
     * @param direction Facing direction (for cones/lines)
     * @param originX Origin grid X (caster position or target point)
     * @param originZ Origin grid Z
     * @param rangeGrids Maximum range in grids
     * @param areaGrids Area size (radius for sphere, length for line, etc.)
     * @return Set of affected grid cells
     */
    public static Set<GridCell> calculatePattern(SpellPattern pattern, Direction8 direction,
                                                 int originX, int originZ,
                                                 int rangeGrids, int areaGrids) {
        Set<GridCell> cells = new HashSet<>();

        switch (pattern) {
            case SINGLE_TARGET:
                cells.add(new GridCell(originX, originZ));
                break;

            case CONE:
                cells.addAll(calculateCone(direction, originX, originZ, areaGrids));
                break;

            case LINE:
                cells.addAll(calculateLine(direction, originX, originZ, areaGrids));
                break;

            case SPHERE:
            case CYLINDER:
                cells.addAll(calculateSphere(originX, originZ, areaGrids));
                break;

            case CUBE:
                cells.addAll(calculateCube(originX, originZ, areaGrids));
                break;

            case AURA:
                cells.addAll(calculateSphere(originX, originZ, areaGrids));
                break;

            case WALL:
                cells.addAll(calculateWall(direction, originX, originZ, areaGrids));
                break;

            case SELF:
                cells.add(new GridCell(originX, originZ));
                break;

            case CHAIN:
                // Chain is handled differently - start with origin
                cells.add(new GridCell(originX, originZ));
                break;
        }

        return cells;
    }

    /**
     * D&D cone — stair-step pattern where the tip is closest to the caster.
     *
     * The cone grows 1 cell wider per row away from the caster:
     *   row 1 (adjacent):  1 cell wide
     *   row 2:             2 cells wide
     *   row 3:             3 cells wide
     *   ...and so on up to 'length' rows.
     *
     * The far corner of the cone is diagonal from the player, per D&D 5e rules.
     * Width at row d = d cells (centered on the forward axis, offset to one side for even rows).
     *
     * For cardinal directions the spread is perpendicular (left/right).
     * For diagonal directions the spread is along the perpendicular cardinal axis.
     */
    private static Set<GridCell> calculateCone(Direction8 direction, int originX, int originZ, int length) {
        Set<GridCell> cells = new HashSet<>();

        int fdx = direction.getDeltaX();
        int fdz = direction.getDeltaZ();

        // Perpendicular spread axis: rotate 90°, snap diagonals to cardinal
        int perpDx, perpDz;
        boolean isDiagonal = (fdx != 0 && fdz != 0);
        if (isDiagonal) {
            // For diagonal directions spread East-West (perpendicular-ish to the diagonal)
            perpDx = 1;
            perpDz = 0;
        } else {
            // For cardinal directions rotate 90° CCW
            perpDx = -fdz;
            perpDz =  fdx;
        }

        for (int dist = 1; dist <= length; dist++) {
            // Width at this row = dist (1, 2, 3, ...)
            int width = dist;
            // Center of this row
            int cx = originX + fdx * dist;
            int cz = originZ + fdz * dist;

            // Spread from -(width-1) to 0 or 0 to (width-1)?
            // Per spec: the corner is diagonally from the player, meaning the cone
            // is offset to one side. We offset left: from -(width-1) to 0.
            for (int side = -(width - 1); side <= 0; side++) {
                cells.add(new GridCell(cx + perpDx * side, cz + perpDz * side));
            }
        }

        return cells;
    }

    /**
     * Calculate line pattern — starts 1 cell in front of the caster (does not include caster cell).
     */
    private static Set<GridCell> calculateLine(Direction8 direction, int originX, int originZ, int length) {
        Set<GridCell> cells = new HashSet<>();

        int dx = direction.getDeltaX();
        int dz = direction.getDeltaZ();

        // i starts at 1 to skip the caster's own cell
        for (int i = 1; i <= length; i++) {
            cells.add(new GridCell(originX + dx * i, originZ + dz * i));
        }

        return cells;
    }

    /**
     * Calculate sphere/cylinder pattern (circular area)
     */
    private static Set<GridCell> calculateSphere(int centerX, int centerZ, int radius) {
        Set<GridCell> cells = new HashSet<>();

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                // Use circular distance
                double distance = Math.sqrt(x * x + z * z);
                if (distance <= radius) {
                    cells.add(new GridCell(centerX + x, centerZ + z));
                }
            }
        }

        return cells;
    }

    /**
     * Calculate cube pattern (square area)
     */
    private static Set<GridCell> calculateCube(int centerX, int centerZ, int radius) {
        Set<GridCell> cells = new HashSet<>();

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                cells.add(new GridCell(centerX + x, centerZ + z));
            }
        }

        return cells;
    }

    /**
     * Calculate wall pattern (line perpendicular to direction)
     */
    private static Set<GridCell> calculateWall(Direction8 direction, int originX, int originZ, int length) {
        Set<GridCell> cells = new HashSet<>();

        // Perpendicular to direction
        int dx = direction.getDeltaX();
        int dz = direction.getDeltaZ();
        int perpDx = -dz;
        int perpDz = dx;

        // Create wall perpendicular to facing
        for (int i = -length/2; i <= length/2; i++) {
            cells.add(new GridCell(originX + perpDx * i, originZ + perpDz * i));
        }

        return cells;
    }

    /**
     * Calculate distance between two grid cells (in grids)
     */
    public static int getDistance(int x1, int z1, int x2, int z2) {
        int dx = Math.abs(x2 - x1);
        int dz = Math.abs(z2 - z1);
        return Math.max(dx, dz); // D&D uses diagonal = 1 grid
    }

    /**
     * Check if a grid cell is within range of origin
     */
    public static boolean isInRange(int originX, int originZ, int targetX, int targetZ, int rangeGrids) {
        return getDistance(originX, originZ, targetX, targetZ) <= rangeGrids;
    }
}