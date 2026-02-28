package com.gridifymydungeon.plugin.spell;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Calculate affected grid cells for spell patterns.
 *
 * FIX #5/#9: calculateSphere() now excludes the center cell (0,0),
 * meaning AURA and SPHERE patterns cannot hit the caster themselves.
 * This prevents monsters and players from targeting themselves with
 * Whirlwind Attack, Spirit Guardians, Hurricane Strike, etc.
 *
 * Melee SINGLE_TARGET self-block is handled in CastFinalCommand.
 */
public class SpellPatternCalculator {

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
                // FIX #5/#9: pass excludeCenter=true so caster is never in the area
                cells.addAll(calculateSphere(originX, originZ, areaGrids, true));
                break;

            case CUBE:
                cells.addAll(calculateCube(originX, originZ, areaGrids));
                break;

            case AURA:
                // FIX #5/#9: AURA always excludes center — hits AROUND the caster, not self
                cells.addAll(calculateSphere(originX, originZ, areaGrids, true));
                break;

            case WALL:
                cells.addAll(calculateWall(direction, originX, originZ, areaGrids));
                break;

            case SELF:
                // SELF targets only the caster's cell — intentional self-buff/heal
                cells.add(new GridCell(originX, originZ));
                break;

            case CHAIN:
                cells.add(new GridCell(originX, originZ));
                break;
        }

        return cells;
    }

    /**
     * D&D 5e cone — correct for all 8 directions.
     */
    private static Set<GridCell> calculateCone(Direction8 direction, int originX, int originZ, int length) {
        Set<GridCell> cells = new HashSet<>();

        int fdx = direction.getDeltaX();
        int fdz = direction.getDeltaZ();
        boolean isDiagonal = (fdx != 0 && fdz != 0);

        for (int rx = -length; rx <= length; rx++) {
            for (int rz = -length; rz <= length; rz++) {
                if (isDiagonal) {
                    if (rx * fdx <= 0 || rz * fdz <= 0) continue;
                    int cx = Math.abs(rx);
                    int cz = Math.abs(rz);
                    int fwdD = Math.max(cx, cz);
                    if (fwdD < 1 || fwdD > length) continue;
                    if (cx + cz <= length + 1) {
                        cells.add(new GridCell(originX + rx, originZ + rz));
                    }
                } else if (fdx == 0) {
                    if (rz * fdz <= 0) continue;
                    int fwdD = Math.abs(rz);
                    int perpD = Math.abs(rx);
                    if (fwdD < 1 || fwdD > length) continue;
                    if (perpD <= fwdD / 2) {
                        cells.add(new GridCell(originX + rx, originZ + rz));
                    }
                } else {
                    if (rx * fdx <= 0) continue;
                    int fwdD = Math.abs(rx);
                    int perpD = Math.abs(rz);
                    if (fwdD < 1 || fwdD > length) continue;
                    if (perpD <= fwdD / 2) {
                        cells.add(new GridCell(originX + rx, originZ + rz));
                    }
                }
            }
        }

        return cells;
    }

    private static Set<GridCell> calculateLine(Direction8 direction, int originX, int originZ, int length) {
        Set<GridCell> cells = new HashSet<>();
        int dx = direction.getDeltaX();
        int dz = direction.getDeltaZ();
        for (int i = 1; i <= length; i++) {
            cells.add(new GridCell(originX + dx * i, originZ + dz * i));
        }
        return cells;
    }

    /**
     * Sphere/cylinder/aura pattern.
     *
     * FIX #5/#9: excludeCenter=true skips the origin cell so caster cannot hit themselves.
     */
    private static Set<GridCell> calculateSphere(int centerX, int centerZ, int radius, boolean excludeCenter) {
        Set<GridCell> cells = new HashSet<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                // FIX #5/#9: skip center cell when excludeCenter is true
                if (excludeCenter && x == 0 && z == 0) continue;
                double distance = Math.sqrt(x * x + z * z);
                if (distance <= radius) {
                    cells.add(new GridCell(centerX + x, centerZ + z));
                }
            }
        }
        return cells;
    }

    private static Set<GridCell> calculateCube(int centerX, int centerZ, int radius) {
        Set<GridCell> cells = new HashSet<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                cells.add(new GridCell(centerX + x, centerZ + z));
            }
        }
        return cells;
    }

    private static Set<GridCell> calculateWall(Direction8 direction, int originX, int originZ, int length) {
        Set<GridCell> cells = new HashSet<>();

        int fdx = direction.getDeltaX();
        int fdz = direction.getDeltaZ();
        boolean isDiagonal = (fdx != 0 && fdz != 0);
        int halfWidth = length / 2;
        int depth     = halfWidth * 2 + 1;

        if (isDiagonal) {
            int cx = originX + fdx * (halfWidth + 1);
            int cz = originZ + fdz * (halfWidth + 1);
            for (int rx = -halfWidth; rx <= halfWidth; rx++) {
                for (int rz = -halfWidth; rz <= halfWidth; rz++) {
                    cells.add(new GridCell(cx + rx, cz + rz));
                }
            }
            cells.remove(new GridCell(originX, originZ));
        } else {
            int perpDx = -fdz;
            int perpDz =  fdx;
            for (int d = 1; d <= depth; d++) {
                for (int i = -halfWidth; i <= halfWidth; i++) {
                    cells.add(new GridCell(
                            originX + fdx * d + perpDx * i,
                            originZ + fdz * d + perpDz * i));
                }
            }
        }
        return cells;
    }

    public static int getDistance(int x1, int z1, int x2, int z2) {
        int dx = Math.abs(x2 - x1);
        int dz = Math.abs(z2 - z1);
        return Math.max(dx, dz);
    }

    public static boolean isInRange(int originX, int originZ, int targetX, int targetZ, int rangeGrids) {
        return getDistance(originX, originZ, targetX, targetZ) <= rangeGrids;
    }
}