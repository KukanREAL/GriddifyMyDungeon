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
 * GridOverlayManager — spawns movement-range grid overlays.
 *
 * Height priority order (all rendered on same ground tile):
 *   Grid_Basic   +0.01  — grey, public, used for static GM map
 *   Grid_Player  +0.02  — blue, private (owner only), player/monster BFS range
 *   Grid_Range   +0.04  — yellow, private (owner only), spell reach ring
 *   Grid_Spell   +0.05  — red, private (owner only), spell impact area
 *
 * FIX #2: GM_MAP_RADIUS = 30  →  61×61 ≈ 60×60 tiles.
 * FIX #3: Grid_Player spawns at y=-30, teleports to ground+0.02, then hides from non-owners.
 */
public class GridOverlayManager {

    private static final String MODEL_PLAYER  = "Grid_Player"; // blue
    private static final String MODEL_DEFAULT = "Grid_Basic";  // grey
    private static final String FALLBACK_MODEL = "Debug";

    private static Model cachedPlayerModel  = null;
    private static Model cachedDefaultModel = null;
    private static boolean modelsAttempted  = false;

    private static final int MAX_OVERLAY_CELLS = 150;

    // FIX #2: was 15 (30×30). Now 30 → 61×61 ≈ 60×60
    private static final int GM_MAP_RADIUS = 30;

    private static final float MAX_HEIGHT_UP   = 3.0f;
    private static final float MAX_HEIGHT_DOWN = 4.0f;

    // Scheduler for hide-from-others delay
    private static final ScheduledExecutorService SCHED =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "grid-overlay-hider");
                t.setDaemon(true);
                return t;
            });

    private static final int[][] NEIGHBORS = {
            { 0, -1}, { 1, 0}, { 0, 1}, {-1, 0},      // cardinal
            { 1, -1}, { 1, 1}, {-1, 1}, {-1, -1},      // diagonal
    };

    // ========================================================
    // PUBLIC API
    // ========================================================

    /**
     * Player /gridon — blue tiles (Grid_Player), BFS movement range.
     * FIX #3: Tiles spawn at y=-30, teleport to ground+0.02, hide from non-owners after 150ms.
     */
    public static boolean spawnPlayerGridOverlay(World world, GridPlayerState state,
                                                 CollisionDetector collisionDetector, UUID excludePlayer) {
        return spawnPlayerGridOverlay(world, state, collisionDetector, excludePlayer, null);
    }

    /**
     * Player /gridon with PlayerRef for privacy filter.
     * FIX #3: Tiles are private to the owning player.
     * FIX #9: Reuses existing tiles by teleporting them instead of despawning/respawning.
     */
    public static boolean spawnPlayerGridOverlay(World world, GridPlayerState state,
                                                 CollisionDetector collisionDetector,
                                                 UUID excludePlayer, PlayerRef owner) {
        ensureModels();
        Model model = cachedPlayerModel != null ? cachedPlayerModel : cachedDefaultModel;
        if (model == null) return false;

        List<ReachableCell> cells = floodFillReachable(world, state, collisionDetector, excludePlayer);

        // FIX #9: Reuse/teleport existing tiles instead of removing and respawning
        if (owner != null) {
            updateCellsWithReuse(world, state, cells, model, 0.02f, owner);
        } else {
            updateCellsWithReuse(world, state, cells, model, 0.02f, null);
        }

        state.gridOverlayEnabled = true;
        state.gmMapOverlayActive = false;
        System.out.println("[GridMove] [GRID] Player overlay: " + cells.size() + " cells (reuse)");
        return true;
    }

    /**
     * GM /gridon with monster — blue tiles (Grid_Player), BFS movement range.
     */
    public static boolean spawnGMBFSOverlay(World world, GridPlayerState state,
                                            CollisionDetector collisionDetector) {
        ensureModels();
        Model model = cachedPlayerModel != null ? cachedPlayerModel : cachedDefaultModel;
        if (model == null) return false;
        removeGridOverlayEntities(world, state);
        List<ReachableCell> cells = floodFillReachable(world, state, collisionDetector, null);
        spawnCells(world, state, cells, model, 0.02f);
        state.gridOverlayEnabled = true;
        state.gmMapOverlayActive = false;
        System.out.println("[GridMove] [GRID] GM BFS overlay (blue): " + cells.size() + " cells");
        return true;
    }

    /** Monster /gridon — grey tiles, BFS movement range. */
    public static boolean spawnGridOverlay(World world, GridPlayerState state,
                                           CollisionDetector collisionDetector, UUID excludePlayer) {
        ensureModels();
        Model model = cachedDefaultModel;
        if (model == null) return false;
        removeGridOverlayEntities(world, state);
        List<ReachableCell> cells = floodFillReachable(world, state, collisionDetector, excludePlayer);
        spawnCells(world, state, cells, model, 0.01f); // Grid_Basic: +0.01
        state.gridOverlayEnabled = true;
        state.gmMapOverlayActive = false;
        System.out.println("[GridMove] [GRID] Monster overlay: " + cells.size() + " cells");
        return true;
    }

    /**
     * GM /grid toggle — FIX #2: flat 60×60 area map (was 30×30).
     * gmMapOverlayActive = TRUE → this is the static /grid map, not BFS range.
     */
    public static boolean spawnGMMapOverlay(World world, GridPlayerState gmState) {
        ensureModels();
        Model model = cachedDefaultModel;
        if (model == null) return false;

        removeGridOverlayEntities(world, gmState);

        int centerX = gmState.currentGridX;
        int centerZ = gmState.currentGridZ;
        float scanStart = gmState.npcY - 3.0f;

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
        spawnCells(world, gmState, cells, model, 0.01f); // Grid_Basic: +0.01
        gmState.gridOverlayEnabled = true;
        gmState.gmMapOverlayActive = true;
        System.out.println("[GridMove] [GRID] GM map overlay: " + cells.size() + " cells (60x60)");
        return true;
    }

    /** GM /grid — same as spawnGMMapOverlay, kept for GridToggleCommand compatibility. */
    public static boolean spawnGMSmallMapOverlay(World world, GridPlayerState gmState) {
        return spawnGMMapOverlay(world, gmState);
    }

    public static void refreshGridOverlay(World world, GridPlayerState state,
                                          CollisionDetector collisionDetector, UUID excludePlayer) {
        refreshGridOverlay(world, state, collisionDetector, excludePlayer, null);
    }

    public static void refreshGridOverlay(World world, GridPlayerState state,
                                          CollisionDetector collisionDetector,
                                          UUID excludePlayer, PlayerRef owner) {
        if (!state.gridOverlayEnabled) return;
        // Static /grid map — do NOT auto-refresh on movement
        if (state.gmMapOverlayActive) return;

        removeGridOverlayEntities(world, state);
        if (excludePlayer != null) {
            spawnPlayerGridOverlay(world, state, collisionDetector, excludePlayer, owner);
        } else {
            spawnGMBFSOverlay(world, state, collisionDetector);
        }
    }

    public static void removeGridOverlay(World world, GridPlayerState state) {
        removeGridOverlayEntities(world, state);
        state.gridOverlayEnabled = false;
        state.gmMapOverlayActive = false;
        System.out.println("[GridMove] [GRID] Removed overlay");
    }

    // ========================================================
    // CELL SPAWNING — public, visible to all
    // ========================================================

    private static void spawnCells(World world, GridPlayerState state,
                                   List<ReachableCell> cells, Model model) {
        spawnCells(world, state, cells, model, 0.01f);
    }

    private static void spawnCells(World world, GridPlayerState state,
                                   List<ReachableCell> cells, Model model, float yOffset) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        for (ReachableCell cell : cells) {
            float cx = (cell.gridX * 2.0f) + 1.0f;
            float cz = (cell.gridZ * 2.0f) + 1.0f;
            float y  = cell.groundY + yOffset;
            Ref<EntityStore> ref = spawnTile(store, model, cx, y, cz);
            state.gridOverlay.add(ref);
        }
    }

    /**
     * FIX #3: Spawn tiles at y=-30 (below world → no players see spawn broadcast),
     * teleport to real Y, then hide from non-owners after 150ms delay.
     */
    private static void spawnCellsPrivate(World world, GridPlayerState state,
                                          List<ReachableCell> cells, Model model,
                                          float yOffset, PlayerRef owner) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        for (ReachableCell cell : cells) {
            float cx = (cell.gridX * 2.0f) + 1.0f;
            float cz = (cell.gridZ * 2.0f) + 1.0f;
            float targetY = cell.groundY + yOffset;

            // Spawn below world — entity-tracker broadcasts to nobody here
            Ref<EntityStore> ref = spawnTile(store, model, cx, -30f, cz);
            if (ref == null) continue;
            state.gridOverlay.add(ref);

            // Teleport to real position
            try {
                TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
                if (tc != null) tc.setPosition(new Vector3d(cx, targetY, cz));
            } catch (Exception ignored) {}
        }

        // After entity-tracker has had time to broadcast, hide from non-owners
        SCHED.schedule(() -> world.execute(() ->
                com.gridifymydungeon.plugin.dnd.PlayerEntityController
                        .hideGridOverlayFromOthers(world, state, owner)
        ), 150L, TimeUnit.MILLISECONDS);
    }

    /**
     * FIX #9: Update grid overlay by reusing existing tiles (teleport) instead of despawn/respawn.
     * This prevents flickering when the player moves.
     */
    private static void updateCellsWithReuse(World world, GridPlayerState state,
                                             List<ReachableCell> newCells, Model model,
                                             float yOffset, PlayerRef owner) {
        Store<EntityStore> store = world.getEntityStore().getStore();

        // Build set of new cell coordinates
        java.util.Set<String> newCoords = new java.util.HashSet<>();
        java.util.Map<String, ReachableCell> newCellMap = new java.util.HashMap<>();
        for (ReachableCell cell : newCells) {
            String key = cell.gridX + "," + cell.gridZ;
            newCoords.add(key);
            newCellMap.put(key, cell);
        }

        // 1. Collect tiles to teleport (don't teleport yet - hide them first!)
        int reused = 0;
        java.util.List<Ref<EntityStore>> tilesToTeleport = new java.util.ArrayList<>();
        java.util.List<ReachableCell> teleportDestinations = new java.util.ArrayList<>();

        for (String key : new java.util.HashSet<>(state.gridTileMap.keySet())) {
            if (newCoords.contains(key)) {
                // Tile exists and is still needed — mark for teleport
                Ref<EntityStore> ref = state.gridTileMap.get(key);
                if (ref != null && ref.isValid()) {
                    ReachableCell cell = newCellMap.get(key);
                    tilesToTeleport.add(ref);
                    teleportDestinations.add(cell);
                    reused++;
                }
                newCoords.remove(key); // already handled
            } else {
                // Tile exists but is no longer needed — despawn it
                Ref<EntityStore> ref = state.gridTileMap.remove(key);
                if (ref != null && ref.isValid()) {
                    try { store.removeEntity(ref, RemoveReason.REMOVE); } catch (Exception ignored) {}
                }
            }
        }

        // 1a. For private grids: hide tiles ONLY ONCE after initial spawn (not on every move!)
        // After first hide, tiles stay hidden and we just teleport them
        boolean needsInitialHide = (owner != null && !state.gridTilesHiddenFromOthers && !state.gridOverlay.isEmpty());
        if (needsInitialHide) {
            com.gridifymydungeon.plugin.dnd.PlayerEntityController
                    .hideGridOverlayFromOthers(world, state, owner);
            state.gridTilesHiddenFromOthers = true;
        }

        // 1b. Teleport the reused tiles (if already hidden, other players still can't see position updates)
        for (int i = 0; i < tilesToTeleport.size(); i++) {
            Ref<EntityStore> ref = tilesToTeleport.get(i);
            ReachableCell cell = teleportDestinations.get(i);
            float cx = (cell.gridX * 2.0f) + 1.0f;
            float cz = (cell.gridZ * 2.0f) + 1.0f;
            float y = cell.groundY + yOffset;
            try {
                TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
                if (tc != null) tc.setPosition(new Vector3d(cx, y, cz));
            } catch (Exception ignored) {}
        }

        // 2. Spawn new tiles for cells that don't have entities yet
        int spawned = 0;
        for (String key : newCoords) {
            ReachableCell cell = newCellMap.get(key);
            float cx = (cell.gridX * 2.0f) + 1.0f;
            float cz = (cell.gridZ * 2.0f) + 1.0f;
            float targetY = cell.groundY + yOffset;

            // Spawn at y=-30 (invisible), then teleport to real Y
            Ref<EntityStore> ref = spawnTile(store, model, cx, -30f, cz);
            if (ref == null) continue;

            // Teleport to real position
            try {
                TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
                if (tc != null) tc.setPosition(new Vector3d(cx, targetY, cz));
            } catch (Exception ignored) {}

            state.gridTileMap.put(key, ref);
            spawned++;
        }

        // 3. Rebuild gridOverlay list from the map
        state.gridOverlay.clear();
        state.gridOverlay.addAll(state.gridTileMap.values());

        // 4. Hide newly spawned tiles after 150ms (first time only, then flag prevents re-hiding)
        if (owner != null && spawned > 0 && !state.gridTilesHiddenFromOthers) {
            SCHED.schedule(() -> world.execute(() -> {
                com.gridifymydungeon.plugin.dnd.PlayerEntityController
                        .hideGridOverlayFromOthers(world, state, owner);
                state.gridTilesHiddenFromOthers = true;
            }), 150L, TimeUnit.MILLISECONDS);
        }

        System.out.println("[GridMove] [GRID] updateCellsWithReuse: reused=" + reused + " spawned=" + spawned);
    }

    private static void removeGridOverlayEntities(World world, GridPlayerState state) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        for (Ref<EntityStore> ref : state.gridOverlay) {
            if (ref != null && ref.isValid()) {
                try { store.removeEntity(ref, RemoveReason.REMOVE); } catch (Exception ignored) {}
            }
        }
        state.gridOverlay.clear();
        state.gridTileMap.clear(); // FIX #9: also clear the reuse map
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