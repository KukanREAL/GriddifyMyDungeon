package com.gridifymydungeon.plugin.gridmove;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.protocol.packets.entities.EntityUpdates;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.PlayerRef;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * GridOverlayManager — persistent-tile teleport system (no flicker).
 *
 * Tiles are spawned ONCE per player session (/gridon).
 * On every movement refresh, tiles are TELEPORTED to new BFS positions instead of
 * being despawned and respawned. This eliminates the entity-tracker spawn/remove
 * cycle that caused flickering for other players.
 *
 * Slots with no BFS cell are "parked" 30 blocks underground (invisible).
 * Private visibility is applied once at spawn via EntityUpdates(removed) sent to
 * all non-owners 80ms after initial spawn.
 *
 * Models (full 2x2 tile textures):
 *   Grid_Player  — blue, BFS movement range
 *   Grid_Basic   — grey, GM static map / monster range
 *   Grid_Difficult — difficult terrain overlay
 */
public class GridOverlayManager {

    private static final String MODEL_PLAYER    = "Grid_Player";
    private static final String MODEL_DEFAULT   = "Grid_Basic";
    private static final String MODEL_DIFFICULT = "Grid_Difficult";
    private static final String FALLBACK_MODEL  = "Debug";

    private static Model cachedPlayerModel    = null;
    private static Model cachedDefaultModel   = null;
    private static Model cachedDifficultModel = null;
    private static boolean modelsAttempted    = false;

    private static final int   MAX_OVERLAY_CELLS = 150;
    private static final int   GM_MAP_RADIUS     = 15;
    private static final float MAX_HEIGHT_UP     = 3.0f;
    private static final float MAX_HEIGHT_DOWN   = 4.0f;

    /** Y offset below NPC for parked (invisible) tiles. */
    private static final float PARK_DEPTH = 30.0f;
    /** Y offset above ground for visible tiles. */
    private static final float TILE_Y_OFFSET = 0.02f;
    /** Minimum Y delta (relative to npcY) to show tile: 15 blocks below NPC = -15.0f. */
    private static final float MIN_SHOW_Y_RANGE = -15.0f;
    /** Maximum Y delta (relative to npcY) to show tile: 3 blocks above NPC = +3.0f. */
    private static final float MAX_SHOW_Y_RANGE = 3.0f;

    /** Shared scheduler for post-spawn hide (80ms delay, 1 daemon thread). */
    private static final ScheduledExecutorService HIDE_SCHED =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "grid-hide-scheduler");
                t.setDaemon(true);
                return t;
            });
    private static final long HIDE_DELAY_MS = 80L;

    private static final int[][] NEIGHBORS = {
            { 0,-1}, { 1, 0}, { 0, 1}, {-1, 0},
            { 1,-1}, { 1, 1}, {-1, 1}, {-1,-1},
    };

    // ========================================================
    // PUBLIC API
    // ========================================================

    /** Player /gridon — blue tiles, BFS movement range. Tiles are private to owner. */
    public static boolean spawnPlayerGridOverlay(World world, GridPlayerState state,
                                                 CollisionDetector collisionDetector,
                                                 UUID excludePlayer, PlayerRef ownerRef) {
        ensureModels();
        Model model = cachedPlayerModel != null ? cachedPlayerModel : cachedDefaultModel;
        if (model == null) return false;

        if (state.gridOverlay.isEmpty()) {
            // First time: allocate the persistent tile pool
            allocateTilePool(world, state, model, ownerRef);
        }

        List<ReachableCell> cells = floodFillReachable(world, state, collisionDetector, excludePlayer);
        teleportTilesToCells(world, state, cells, model);
        state.gridOverlayEnabled = true;
        state.gmMapOverlayActive = false;
        System.out.println("[GridMove] [GRID] Player overlay: " + cells.size() + " cells (teleport)");
        return true;
    }

    public static boolean spawnPlayerGridOverlay(World world, GridPlayerState state,
                                                 CollisionDetector collisionDetector, UUID excludePlayer) {
        return spawnPlayerGridOverlay(world, state, collisionDetector, excludePlayer, null);
    }

    /** GM /gridon with monster — blue tiles, BFS. Hidden from non-ownerRef players. */
    public static boolean spawnGMBFSOverlay(World world, GridPlayerState state,
                                            CollisionDetector collisionDetector, PlayerRef ownerRef) {
        ensureModels();
        Model model = cachedPlayerModel != null ? cachedPlayerModel : cachedDefaultModel;
        if (model == null) return false;

        if (state.gridOverlay.isEmpty()) {
            allocateTilePool(world, state, model, ownerRef);
        }

        List<ReachableCell> cells = floodFillReachable(world, state, collisionDetector, null);
        teleportTilesToCells(world, state, cells, model);
        state.gridOverlayEnabled = true;
        state.gmMapOverlayActive = false;
        System.out.println("[GridMove] [GRID] GM BFS overlay: " + cells.size() + " cells (teleport)");
        return true;
    }

    public static boolean spawnGMBFSOverlay(World world, GridPlayerState state,
                                            CollisionDetector collisionDetector) {
        return spawnGMBFSOverlay(world, state, collisionDetector, null);
    }

    /** Monster /gridon (GM controlling) — grey tiles, BFS movement range. */
    public static boolean spawnGridOverlay(World world, GridPlayerState state,
                                           CollisionDetector collisionDetector, UUID excludePlayer) {
        ensureModels();
        Model model = cachedDefaultModel;
        if (model == null) return false;

        if (state.gridOverlay.isEmpty()) {
            allocateTilePool(world, state, model, null);
        }

        List<ReachableCell> cells = floodFillReachable(world, state, collisionDetector, excludePlayer);
        teleportTilesToCells(world, state, cells, model);
        state.gridOverlayEnabled = true;
        state.gmMapOverlayActive = false;
        System.out.println("[GridMove] [GRID] Monster overlay: " + cells.size() + " cells (teleport)");
        return true;
    }

    /**
     * GM /grid toggle — flat 30x30 area map, static.
     * Uses legacy spawn/remove (not persistent pool) since it only runs once.
     */
    public static boolean spawnGMMapOverlay(World world, GridPlayerState gmState) {
        ensureModels();
        Model model = cachedDefaultModel;
        if (model == null) return false;

        removeGridOverlayEntities(world, gmState);

        int centerX = gmState.currentGridX;
        int centerZ = gmState.currentGridZ;
        float scanStart = gmState.npcY + 2.0f;

        Store<EntityStore> store = world.getEntityStore().getStore();
        List<ReachableCell> cells = new ArrayList<>();
        for (int dx = -GM_MAP_RADIUS; dx <= GM_MAP_RADIUS; dx++) {
            for (int dz = -GM_MAP_RADIUS; dz <= GM_MAP_RADIUS; dz++) {
                int gx = centerX + dx;
                int gz = centerZ + dz;
                if (isBarrierCell(world, gx, gz, scanStart)) continue;
                Float groundY = scanForGround(world, gx, gz, scanStart, 15);
                if (groundY == null) continue;
                if (hasFluidAbove(world, gx, gz, groundY)) continue;
                cells.add(new ReachableCell(gx, gz, groundY));
            }
        }
        for (ReachableCell cell : cells) {
            float cx = (cell.gridX * 2.0f) + 1.0f;
            float cz = (cell.gridZ * 2.0f) + 1.0f;
            Ref<EntityStore> ref = spawnTile(store, model, cx, cell.groundY + TILE_Y_OFFSET, cz);
            gmState.gridOverlay.add(ref);
        }
        gmState.gridOverlayEnabled = true;
        gmState.gmMapOverlayActive = true;
        System.out.println("[GridMove] [GRID] GM map overlay: " + cells.size() + " cells (30x30)");
        return true;
    }

    public static boolean spawnGMSmallMapOverlay(World world, GridPlayerState gmState) {
        return spawnGMMapOverlay(world, gmState);
    }

    /**
     * Refresh: re-run BFS and teleport existing tiles to new positions.
     * If the tile pool doesn't exist yet, falls through to spawnPlayerGridOverlay.
     */
    public static void refreshGridOverlay(World world, GridPlayerState state,
                                          CollisionDetector collisionDetector, UUID excludePlayer,
                                          PlayerRef ownerRef) {
        if (!state.gridOverlayEnabled) return;
        if (state.gmMapOverlayActive) return;

        if (excludePlayer != null) {
            spawnPlayerGridOverlay(world, state, collisionDetector, excludePlayer, ownerRef);
        } else {
            spawnGMBFSOverlay(world, state, collisionDetector, ownerRef);
        }
    }

    public static void refreshGridOverlay(World world, GridPlayerState state,
                                          CollisionDetector collisionDetector, UUID excludePlayer) {
        refreshGridOverlay(world, state, collisionDetector, excludePlayer, null);
    }

    /** Remove all tile entities and reset overlay state. */
    public static void removeGridOverlay(World world, GridPlayerState state) {
        removeGridOverlayEntities(world, state);
        state.gridOverlayEnabled = false;
        state.gmMapOverlayActive = false;
        System.out.println("[GridMove] [GRID] Removed overlay");
    }

    // ========================================================
    // PERSISTENT TILE POOL
    // ========================================================

    /**
     * Allocate MAX_OVERLAY_CELLS tile entities at the NPC's parked position
     * (30 blocks underground). This is called ONCE per session.
     * After 80ms, all tiles are hidden from non-owner players.
     */
    private static void allocateTilePool(World world, GridPlayerState state,
                                         Model model, PlayerRef ownerRef) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        float parkX = (state.currentGridX * 2.0f) + 1.0f;
        float parkY = state.npcY - PARK_DEPTH;
        float parkZ = (state.currentGridZ * 2.0f) + 1.0f;

        int[] netIds = new int[MAX_OVERLAY_CELLS];

        for (int i = 0; i < MAX_OVERLAY_CELLS; i++) {
            int[] netIdOut = {-1};
            Ref<EntityStore> ref = spawnTile(store, model, parkX, parkY, parkZ, netIdOut);
            state.gridOverlay.add(ref);
            netIds[i] = netIdOut[0];
        }
        System.out.println("[GridMove] [GRID] Allocated " + MAX_OVERLAY_CELLS + " persistent tiles");

        // Hide from non-owners after entity-tracker has broadcast the spawn
        if (ownerRef != null) {
            final PlayerRef owner = ownerRef;
            final int[] ids = netIds;
            HIDE_SCHED.schedule(() -> world.execute(() -> {
                EntityUpdates removePacket = new EntityUpdates(ids, null);
                for (PlayerRef p : world.getPlayerRefs()) {
                    if (!p.getUuid().equals(owner.getUuid())) {
                        p.getPacketHandler().writeNoCache(removePacket);
                    }
                }
                System.out.println("[GridMove] [GRID] Hidden " + ids.length + " tiles from non-owners");
            }), HIDE_DELAY_MS, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Teleport existing tile pool entities to match the new BFS cell list.
     * Tiles with a BFS cell → moved to that cell's world position.
     * Tiles with no cell    → parked 30 blocks below NPC (invisible).
     *
     * Also updates model if a cell needs the difficult-terrain overlay.
     */
    private static void teleportTilesToCells(World world, GridPlayerState state,
                                             List<ReachableCell> cells, Model baseModel) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        float parkX = (state.currentGridX * 2.0f) + 1.0f;
        float parkY = state.npcY - PARK_DEPTH;
        float parkZ = (state.currentGridZ * 2.0f) + 1.0f;

        int poolSize = state.gridOverlay.size();
        int cellCount = Math.min(cells.size(), poolSize);

        for (int i = 0; i < poolSize; i++) {
            Ref<EntityStore> ref = state.gridOverlay.get(i);
            if (ref == null || !ref.isValid()) continue;

            TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
            if (tc == null) continue;

            if (i < cellCount) {
                ReachableCell cell = cells.get(i);
                float tileY = cell.groundY + TILE_Y_OFFSET;

                // Height-range filter: hide tiles that are out of the [-3, +15] range
                float deltaY = tileY - state.npcY;
                if (deltaY < MIN_SHOW_Y_RANGE || deltaY > MAX_SHOW_Y_RANGE) {
                    tc.setPosition(new Vector3d(parkX, parkY, parkZ));
                    continue;
                }

                float cx = (cell.gridX * 2.0f) + 1.0f;
                float cz = (cell.gridZ * 2.0f) + 1.0f;
                tc.setPosition(new Vector3d(cx, tileY, cz));

                // Update model component if difficult terrain
                if (cachedDifficultModel != null && cachedDifficultModel != baseModel
                        && TerrainManager.shouldShowDifficultOverlay(cell.gridX, cell.gridZ, cell.groundY, world)) {
                    store.putComponent(ref, ModelComponent.getComponentType(), new ModelComponent(cachedDifficultModel));
                } else {
                    ModelComponent mc = store.getComponent(ref, ModelComponent.getComponentType());
                    if (mc != null && mc.getModel() != baseModel) {
                        store.putComponent(ref, ModelComponent.getComponentType(), new ModelComponent(baseModel));
                    }
                }
            } else {
                // Park this slot underground
                tc.setPosition(new Vector3d(parkX, parkY, parkZ));
            }
        }
    }

    // ========================================================
    // LEGACY REMOVE (used by GM map and removeGridOverlay)
    // ========================================================

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
    // BARRIER / GROUND / FLUID
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

    private static Float scanForGround(World world, int gridX, int gridZ, float referenceY) {
        return scanForGround(world, gridX, gridZ, referenceY, 12);
    }

    private static Float scanForGround(World world, int gridX, int gridZ, float referenceY, int scanDepth) {
        int startY = (int) Math.floor(referenceY);
        int endY   = startY - scanDepth;
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
        cachedPlayerModel    = loadModel(MODEL_PLAYER);
        cachedDefaultModel   = loadModel(MODEL_DEFAULT);
        cachedDifficultModel = loadModel(MODEL_DIFFICULT);
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
    // ENTITY SPAWNING
    // ========================================================

    private static Ref<EntityStore> spawnTile(Store<EntityStore> store, Model model,
                                              float x, float y, float z) {
        return spawnTile(store, model, x, y, z, null);
    }

    private static Ref<EntityStore> spawnTile(Store<EntityStore> store, Model model,
                                              float x, float y, float z, int[] netIdOut) {
        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
        holder.addComponent(TransformComponent.getComponentType(),
                new TransformComponent(new Vector3d(x, y, z), new Vector3f(0, 0, 0)));
        holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
        holder.addComponent(BoundingBox.getComponentType(), new BoundingBox(model.getBoundingBox()));
        int netId = store.getExternalData().takeNextNetworkId();
        if (netIdOut != null && netIdOut.length > 0) netIdOut[0] = netId;
        holder.addComponent(NetworkId.getComponentType(), new NetworkId(netId));
        holder.ensureComponent(UUIDComponent.getComponentType());
        return store.addEntity(holder, AddReason.SPAWN);
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

    static class ReachableCell {
        final int gridX, gridZ; final float groundY;
        ReachableCell(int x, int z, float y) { gridX=x; gridZ=z; groundY=y; }
    }
}