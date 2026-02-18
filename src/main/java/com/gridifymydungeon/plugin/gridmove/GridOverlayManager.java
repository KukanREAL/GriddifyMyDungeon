package com.gridifymydungeon.plugin.gridmove;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkColumn;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

/**
 * GridOverlayManager — spawns movement-range grid overlays.
 *
 * One entity per 2x2 grid cell, centred at the cell's world-space midpoint.
 * Models (full 2x2 tile textures, no rotation needed):
 *   Grid_Basic   — grey, used for monster/GM range
 *   Grid_Player  — blue, used for player movement range
 *
 * GM /gridon with no monster → flat 100x100 area map around GM position.
 * Barrier blocks (id contains "barrier") and fluid cells are always skipped.
 */
public class GridOverlayManager {

    private static final String MODEL_PLAYER  = "Grid_Player"; // blue
    private static final String MODEL_DEFAULT = "Grid_Basic";  // grey
    private static final String FALLBACK_MODEL = "Debug";

    private static Model cachedPlayerModel  = null;
    private static Model cachedDefaultModel = null;
    private static boolean modelsAttempted  = false;

    private static final int MAX_OVERLAY_CELLS = 150;
    private static final int GM_MAP_RADIUS     = 50;   // 100x100

    private static final float MAX_HEIGHT_UP   = 3.0f;
    private static final float MAX_HEIGHT_DOWN = 4.0f;

    private static final int[][] NEIGHBORS = {
            { 0, -1}, { 1, 0}, { 0, 1}, {-1, 0},      // cardinal
            { 1, -1}, { 1, 1}, {-1, 1}, {-1, -1},      // diagonal
    };

    // ========================================================
    // PUBLIC API
    // ========================================================

    /** Player /gridon — blue tiles, BFS movement range. */
    public static boolean spawnPlayerGridOverlay(World world, GridPlayerState state,
                                                 CollisionDetector collisionDetector, UUID excludePlayer) {
        ensureModels();
        Model model = cachedPlayerModel != null ? cachedPlayerModel : cachedDefaultModel;
        if (model == null) return false;
        List<ReachableCell> cells = floodFillReachable(world, state, collisionDetector, excludePlayer);
        spawnCells(world, state, cells, model);
        state.gridOverlayEnabled = true;
        System.out.println("[GridMove] [GRID] Player overlay: " + cells.size() + " cells");
        return true;
    }

    /** Monster /gridon (GM controlling) — grey tiles, BFS movement range. */
    public static boolean spawnGridOverlay(World world, GridPlayerState state,
                                           CollisionDetector collisionDetector, UUID excludePlayer) {
        ensureModels();
        Model model = cachedDefaultModel;
        if (model == null) return false;
        List<ReachableCell> cells = floodFillReachable(world, state, collisionDetector, excludePlayer);
        spawnCells(world, state, cells, model);
        state.gridOverlayEnabled = true;
        System.out.println("[GridMove] [GRID] Monster overlay: " + cells.size() + " cells");
        return true;
    }

    /** GM /gridon with no monster — flat 100x100 area map, skipping barriers and fluid. */
    public static boolean spawnGMMapOverlay(World world, GridPlayerState gmState) {
        ensureModels();
        Model model = cachedDefaultModel;
        if (model == null) return false;

        int centerX = gmState.currentGridX;
        int centerZ = gmState.currentGridZ;
        float refY  = gmState.npcY;

        List<ReachableCell> cells = new ArrayList<>();
        for (int dx = -GM_MAP_RADIUS; dx <= GM_MAP_RADIUS; dx++) {
            for (int dz = -GM_MAP_RADIUS; dz <= GM_MAP_RADIUS; dz++) {
                int gx = centerX + dx;
                int gz = centerZ + dz;
                if (isBarrierCell(world, gx, gz, refY)) continue;
                Float groundY = scanForGround(world, gx, gz, refY + 8.0f);
                if (groundY == null) continue;
                if (hasFluidAbove(world, gx, gz, groundY)) continue;
                cells.add(new ReachableCell(gx, gz, groundY));
            }
        }
        spawnCells(world, gmState, cells, model);
        gmState.gridOverlayEnabled = true;
        System.out.println("[GridMove] [GRID] GM map overlay: " + cells.size() + " cells");
        return true;
    }

    public static void refreshGridOverlay(World world, GridPlayerState state,
                                          CollisionDetector collisionDetector, UUID excludePlayer) {
        if (!state.gridOverlayEnabled) return;
        removeGridOverlayEntities(world, state);
        spawnGridOverlay(world, state, collisionDetector, excludePlayer);
    }

    public static void removeGridOverlay(World world, GridPlayerState state) {
        removeGridOverlayEntities(world, state);
        state.gridOverlayEnabled = false;
        System.out.println("[GridMove] [GRID] Removed overlay");
    }

    // ========================================================
    // CELL SPAWNING — 1 entity per cell, centred in the 2x2 block
    // ========================================================

    private static void spawnCells(World world, GridPlayerState state,
                                   List<ReachableCell> cells, Model model) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        for (ReachableCell cell : cells) {
            // Centre of the 2x2 block: gridX*2 + 1, gridZ*2 + 1
            float cx = (cell.gridX * 2.0f) + 1.0f;
            float cz = (cell.gridZ * 2.0f) + 1.0f;
            float y  = cell.groundY + 0.01f;
            Ref<EntityStore> ref = spawnTile(store, model, cx, y, cz);
            state.gridOverlay.add(ref);
        }
    }

    private static void removeGridOverlayEntities(World world, GridPlayerState state) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        for (Ref<EntityStore> ref : state.gridOverlay) {
            if (ref != null && ref.isValid()) {
                try { store.removeEntity(ref, RemoveReason.REMOVE); } catch (Exception ignored) {}
            }
        }
        state.gridOverlay.clear();
    }

    // ========================================================
    // BFS FLOOD-FILL
    // ========================================================

    private static List<ReachableCell> floodFillReachable(World world, GridPlayerState state,
                                                          CollisionDetector collisionDetector,
                                                          UUID excludePlayer) {
        List<ReachableCell> result = new ArrayList<>();
        Queue<BfsNode> queue = new LinkedList<>();
        Map<Long, Double> bestMoves = new HashMap<>();
        Map<Long, Float> groundYCache = new HashMap<>();

        float startY  = state.npcY;
        long startKey = packKey(state.currentGridX, state.currentGridZ);

        queue.add(new BfsNode(state.currentGridX, state.currentGridZ, state.remainingMoves, startY));
        bestMoves.put(startKey, state.remainingMoves);
        groundYCache.put(startKey, startY);

        while (!queue.isEmpty() && result.size() < MAX_OVERLAY_CELLS) {
            BfsNode cur = queue.poll();
            for (int[] nb : NEIGHBORS) {
                int nx = cur.gridX + nb[0];
                int nz = cur.gridZ + nb[1];
                boolean diagonal = (nb[0] != 0 && nb[1] != 0);
                double cost = diagonal ? 1.5 : 1.0;
                double movesAfter = cur.movesLeft - cost;
                if (movesAfter < 0) continue;

                long nKey = packKey(nx, nz);
                Double prev = bestMoves.get(nKey);
                if (prev != null && prev >= movesAfter) continue;

                if (collisionDetector != null &&
                        collisionDetector.isPositionOccupied(nx, nz, -1, excludePlayer)) continue;

                if (isBarrierCell(world, nx, nz, cur.groundY)) continue;

                Float groundY = groundYCache.get(nKey);
                if (groundY == null) {
                    groundY = scanForGround(world, nx, nz, cur.groundY + 6.0f);
                    if (groundY == null) groundY = scanForGround(world, nx, nz, state.npcY + 6.0f);
                    if (groundY == null) continue;
                    groundYCache.put(nKey, groundY);
                }

                float heightDiff = groundY - cur.groundY;
                if (heightDiff >= MAX_HEIGHT_UP || heightDiff < -MAX_HEIGHT_DOWN) continue;

                bestMoves.put(nKey, movesAfter);
                if (nx != state.currentGridX || nz != state.currentGridZ) {
                    if (prev == null) result.add(new ReachableCell(nx, nz, groundY));
                }
                queue.add(new BfsNode(nx, nz, movesAfter, groundY));
            }
        }
        return result;
    }

    // ========================================================
    // BARRIER DETECTION
    // ========================================================

    private static boolean isBarrierCell(World world, int gridX, int gridZ, float refY) {
        try {
            int scanFrom = (int) Math.floor(refY) + 2;
            int scanTo   = (int) Math.floor(refY) - 4;
            for (int y = scanFrom; y >= scanTo; y--) {
                for (int xOff = 0; xOff < 2; xOff++) {
                    for (int zOff = 0; zOff < 2; zOff++) {
                        BlockType block = world.getBlockType(
                                new Vector3i((gridX * 2) + xOff, y, (gridZ * 2) + zOff));
                        if (isBarrier(block)) return true;
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    // ========================================================
    // GROUND SCANNING
    // ========================================================

    private static Float scanForGround(World world, int gridX, int gridZ, float referenceY) {
        int startY = (int) Math.floor(referenceY);
        int endY   = startY - 12;
        for (int blockY = startY; blockY >= endY; blockY--) {
            boolean hasGround = false;
            float maxHeight = 0;
            for (int xOff = 0; xOff < 2; xOff++) {
                for (int zOff = 0; zOff < 2; zOff++) {
                    BlockType block = world.getBlockType(
                            new Vector3i((gridX * 2) + xOff, blockY, (gridZ * 2) + zOff));
                    if (isSolid(block) && !isBarrier(block)) {
                        maxHeight = Math.max(maxHeight, 1.0f);
                        hasGround = true;
                    } else if (isBarrier(block)) {
                        hasGround = false;
                    }
                }
            }
            if (hasGround) {
                float groundY = blockY + maxHeight;
                if (!hasFluidAbove(world, gridX, gridZ, groundY)) return groundY;
            }
        }
        return null;
    }

    // ========================================================
    // FLUID DETECTION
    // ========================================================

    private static boolean hasFluidAbove(World world, int gridX, int gridZ, float groundY) {
        try {
            Store<ChunkStore> cs = world.getChunkStore().getStore();
            int startY = (int) Math.floor(groundY);
            int endY   = (int) Math.floor(groundY + 2.0f);
            for (int y = startY; y <= endY; y++) {
                for (int xOff = 0; xOff < 2; xOff++) {
                    for (int zOff = 0; zOff < 2; zOff++) {
                        int bx = (gridX * 2) + xOff, bz = (gridZ * 2) + zOff;
                        FluidSection fluid = findFluidSection(world, cs, bx, y, bz);
                        if (fluid != null && fluid.getFluidId(bx, y, bz) != 0) return true;
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static FluidSection findFluidSection(World world, Store<ChunkStore> store,
                                                 int bx, int by, int bz) {
        try {
            long idx = ChunkUtil.indexChunkFromBlock(bx, bz);
            Ref<ChunkStore> cRef = world.getChunkStore().getChunkReference(idx);
            if (cRef == null || !cRef.isValid()) return null;
            ChunkColumn col = store.getComponent(cRef, ChunkColumn.getComponentType());
            if (col == null) return null;
            int targetSY = ChunkUtil.chunkCoordinate(by);
            for (Ref<ChunkStore> sRef : col.getSections()) {
                if (sRef == null || !sRef.isValid()) continue;
                ChunkSection sec = store.getComponent(sRef, ChunkSection.getComponentType());
                if (sec != null && sec.getY() == targetSY)
                    return store.getComponent(sRef, FluidSection.getComponentType());
            }
        } catch (Exception ignored) {}
        return null;
    }

    // ========================================================
    // BLOCK HELPERS
    // ========================================================

    private static boolean isSolid(BlockType block) {
        return block != null && block.getMaterial() == BlockMaterial.Solid;
    }

    private static boolean isBarrier(BlockType block) {
        return block != null && block.getId() != null &&
                block.getId().toLowerCase().contains("barrier");
    }

    // ========================================================
    // MODEL LOADING
    // ========================================================

    private static void ensureModels() {
        if (modelsAttempted) return;
        modelsAttempted = true;
        cachedPlayerModel  = loadModel(MODEL_PLAYER);
        cachedDefaultModel = loadModel(MODEL_DEFAULT);
        if (cachedPlayerModel  == null) cachedPlayerModel  = cachedDefaultModel;
        if (cachedDefaultModel == null) {
            cachedDefaultModel = loadModel(FALLBACK_MODEL);
            cachedPlayerModel  = cachedDefaultModel;
        }
    }

    private static Model loadModel(String id) {
        try {
            ModelAsset asset = ModelAsset.getAssetMap().getAsset(id);
            if (asset != null) {
                System.out.println("[GridMove] [GRID] Loaded model: " + id);
                return Model.createScaledModel(asset, 1.0f);
            }
            System.out.println("[GridMove] [GRID] Model not found: " + id);
        } catch (Exception e) {
            System.err.println("[GridMove] [GRID] Error loading " + id + ": " + e.getMessage());
        }
        return null;
    }

    // ========================================================
    // ENTITY SPAWNING — single tile per cell, no rotation
    // ========================================================

    private static Ref<EntityStore> spawnTile(Store<EntityStore> store, Model model,
                                              float x, float y, float z) {
        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
        holder.addComponent(TransformComponent.getComponentType(),
                new TransformComponent(new Vector3d(x, y, z), new Vector3f(0, 0, 0)));
        holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
        holder.addComponent(BoundingBox.getComponentType(), new BoundingBox(model.getBoundingBox()));
        holder.addComponent(NetworkId.getComponentType(),
                new NetworkId(store.getExternalData().takeNextNetworkId()));
        holder.ensureComponent(UUIDComponent.getComponentType());
        return store.addEntity(holder, com.hypixel.hytale.component.AddReason.SPAWN);
    }

    private static long packKey(int gridX, int gridZ) {
        return ((long) gridX << 32) | (gridZ & 0xFFFFFFFFL);
    }

    // ========================================================
    // INNER CLASSES
    // ========================================================

    private static class BfsNode {
        final int gridX, gridZ; final double movesLeft; final float groundY;
        BfsNode(int x, int z, double m, float y) { gridX=x; gridZ=z; movesLeft=m; groundY=y; }
    }

    private static class ReachableCell {
        final int gridX, gridZ; final float groundY;
        ReachableCell(int x, int z, float y) { gridX=x; gridZ=z; groundY=y; }
    }
}