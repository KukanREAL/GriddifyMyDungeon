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
    private static final String MODEL_DIFFICULT = "Grid_Difficult"; // BUG FIX: difficult terrain
    private static final String FALLBACK_MODEL = "Debug";

    private static Model cachedPlayerModel  = null;
    private static Model cachedDefaultModel = null;
    private static Model cachedDifficultModel = null; // BUG FIX
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

        if (excludePlayer != null) {
            // Player path: pool manages its own cleanup — never pre-clear
            spawnPlayerGridOverlay(world, state, collisionDetector, excludePlayer, owner);
        } else {
            removeGridOverlayEntities(world, state);
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
    /**
     * BUG 2 FIX: Spawn private grid tiles and hide from non-owners.
     * Mirrors fog-of-war spawnPrivateEntity pattern:
     * - Spawn at real Y so entity tracker broadcasts to nearby players
     * - Store networkId per tile for immediate hide on future teleports
     * - Hide from non-owners 200ms after spawn (one tracker tick for new entities)
     */
    private static void spawnCellsPrivate(World world, GridPlayerState state,
                                          List<ReachableCell> cells, Model model,
                                          float yOffset, PlayerRef owner) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        for (ReachableCell cell : cells) {
            float cx = (cell.gridX * 2.0f) + 1.0f;
            float cz = (cell.gridZ * 2.0f) + 1.0f;
            float targetY = cell.groundY + yOffset;
            String key = cell.gridX + "," + cell.gridZ;

            Ref<EntityStore> ref = spawnTile(store, model, cx, targetY, cz);
            if (ref == null) continue;
            state.gridOverlay.add(ref);

            // Store netId for immediate-hide on future teleports (fog pattern)
            try {
                NetworkId netComp = store.getComponent(ref, NetworkId.getComponentType());
                if (netComp != null) state.gridTileNetIds.put(key, netComp.getId());
            } catch (Exception ignored) {}
        }

        // New entities need one entity-tracker tick before we can hide them (200ms)
        final PlayerRef finalOwner = owner;
        SCHED.schedule(() -> world.execute(() ->
                com.gridifymydungeon.plugin.dnd.PlayerEntityController
                        .hideGridOverlayFromOthers(world, state, finalOwner)
        ), 200L, TimeUnit.MILLISECONDS);
    }


    // ─── helpers used by updateCellsWithReuse ────────────────────────────

    /** Hide tile from every player except owner. Reads netId from component. */
    private static void hideFromNonOwners(Store<EntityStore> store, World world,
                                          Ref<EntityStore> ref, PlayerRef owner) {
        try {
            NetworkId nc = store.getComponent(ref, NetworkId.getComponentType());
            if (nc != null)
                com.gridifymydungeon.plugin.dnd.PlayerEntityController
                        .hideEntityFromOthers(world, ref, owner, nc.getId());
        } catch (Exception ignored) {}
    }

    /** Pop the last valid ref from a pool list, discarding dead ones. */
    private static Ref<EntityStore> poolPop(java.util.List<Ref<EntityStore>> pool) {
        while (!pool.isEmpty()) {
            Ref<EntityStore> r = pool.remove(pool.size() - 1);
            if (r != null && r.isValid()) return r;
        }
        return null;
    }

    // Simple data bag for a fresh-spawn whose hide+teleport must happen after 200 ms
    private static final class PendingTile {
        final Ref<EntityStore> ref;
        final float cx, targetY, cz;
        PendingTile(Ref<EntityStore> r, float cx, float y, float cz) {
            ref = r; this.cx = cx; this.targetY = y; this.cz = cz;
        }
    }

    /**
     * Update the player grid overlay using a zero-despawn pool strategy.
     *
     * FIX 2 — Ledge / unavailable-cell tiles stay VISIBLE to owner at y=-30:
     *   When a tile leaves BFS range (wall, ledge, too far) it is teleported to y=-30
     *   and parked in gridTilePool.  It is hidden from other players but the OWNER
     *   still sees it below the world.  When the same cell re-enters range the tile is
     *   teleported back up — no spawn flash, no new entity needed.
     *
     * FIX 3 — Delta ground-scan:
     *   groundYCache persists between moves in GridPlayerState.
     *   floodFillReachable (below) reads from this cache for cells it already knows,
     *   so block-scanning only happens for truly-new cells entering the BFS frontier.
     */
    private static void updateCellsWithReuse(World world, GridPlayerState state,
                                             List<ReachableCell> newCells, Model model,
                                             float yOffset, PlayerRef owner) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        final float PARKED_Y = -30f;

        // Build lookup for the incoming cell set
        java.util.Map<String, ReachableCell> incoming = new java.util.HashMap<>();
        for (ReachableCell c : newCells) incoming.put(c.gridX + "," + c.gridZ, c);

        // ── 1. Process currently-active tiles ────────────────────────────────
        for (String key : new java.util.HashSet<>(state.gridTileMap.keySet())) {
            Ref<EntityStore> ref = state.gridTileMap.get(key);
            if (ref == null || !ref.isValid()) { state.gridTileMap.remove(key); continue; }

            ReachableCell cell = incoming.remove(key);   // null → leaving range
            if (cell != null) {
                // Still reachable — teleport to updated position, re-hide from others
                float cx = (cell.gridX * 2.0f) + 1.0f;
                float cz = (cell.gridZ * 2.0f) + 1.0f;
                float y  = cell.groundY + yOffset;
                try {
                    TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
                    if (tc != null) tc.setPosition(new Vector3d(cx, y, cz));
                } catch (Exception ignored) {}
                if (owner != null) hideFromNonOwners(store, world, ref, owner);
            } else {
                // Leaving BFS range — park at y=-30, VISIBLE TO OWNER, hidden from others.
                // Owner sees their "shadow" grid below the world; tile ready for instant reuse.
                try {
                    TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
                    if (tc != null) tc.setPosition(new Vector3d(0, PARKED_Y, 0));
                } catch (Exception ignored) {}
                if (owner != null) hideFromNonOwners(store, world, ref, owner);
                state.gridTileMap.remove(key);
                state.gridTilePool.add(ref);
            }
        }

        // ── 2. Fill new cells — recycle pool tiles first, spawn fresh if needed ─
        java.util.List<PendingTile> pending = new java.util.ArrayList<>();

        for (java.util.Map.Entry<String, ReachableCell> e : incoming.entrySet()) {
            String key = e.getKey();
            ReachableCell cell = e.getValue();
            float cx = (cell.gridX * 2.0f) + 1.0f;
            float cz = (cell.gridZ * 2.0f) + 1.0f;
            float targetY = cell.groundY + yOffset;

            Ref<EntityStore> recycled = poolPop(state.gridTilePool);
            if (recycled != null) {
                // Recycled tile: already known to entity-tracker → teleport + hide immediately
                try {
                    TransformComponent tc = store.getComponent(recycled, TransformComponent.getComponentType());
                    if (tc != null) tc.setPosition(new Vector3d(cx, targetY, cz));
                } catch (Exception ignored) {}
                if (owner != null) hideFromNonOwners(store, world, recycled, owner);
                state.gridTileMap.put(key, recycled);
                continue;
            }

            // Pool exhausted — brand-new entity, born at y=-30 (below world)
            boolean isDifficult = TerrainManager.isDifficult(cell.gridX, cell.gridZ, cell.groundY, world);
            Ref<EntityStore> ref = spawnTile(store, isDifficult ? cachedDifficultModel : model, cx, PARKED_Y, cz);
            if (ref == null) continue;
            state.gridTileMap.put(key, ref);
            pending.add(new PendingTile(ref, cx, targetY, cz));
        }

        // ── 3. Rebuild gridOverlay list ───────────────────────────────────────
        state.gridOverlay.clear();
        state.gridOverlay.addAll(state.gridTileMap.values());

        // ── 4. Fresh tiles: after 200 ms hide from others, then teleport up ───
        // Born at y=-30 so the entity-tracker broadcasts them there first.
        // After one tick we hide from non-owners, THEN move to real Y.
        // Owner sees the tile pop up; non-owners never see it at real Y.
        if (!pending.isEmpty()) {
            final PlayerRef fOwner = owner;
            final java.util.List<PendingTile> fPending = pending;
            final Store<EntityStore> fStore = store;
            SCHED.schedule(() -> world.execute(() -> {
                for (PendingTile pt : fPending) {
                    if (!pt.ref.isValid()) continue;
                    if (fOwner != null) hideFromNonOwners(fStore, world, pt.ref, fOwner);
                    try {
                        TransformComponent tc = fStore.getComponent(pt.ref, TransformComponent.getComponentType());
                        if (tc != null) tc.setPosition(new Vector3d(pt.cx, pt.targetY, pt.cz));
                    } catch (Exception ignored) {}
                }
            }), 200L, TimeUnit.MILLISECONDS);
        }

        System.out.println("[GridMove][GRID] active=" + state.gridTileMap.size()
                + " pool=" + state.gridTilePool.size()
                + " freshSpawn=" + pending.size());
    }


    private static void removeGridOverlayEntities(World world, GridPlayerState state) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        // Remove active tiles
        for (Ref<EntityStore> ref : state.gridOverlay) {
            if (ref != null && ref.isValid())
                try { store.removeEntity(ref, RemoveReason.REMOVE); } catch (Exception ignored) {}
        }
        // Remove parked pool tiles (full teardown on /gridoff)
        for (Ref<EntityStore> ref : state.gridTilePool) {
            if (ref != null && ref.isValid())
                try { store.removeEntity(ref, RemoveReason.REMOVE); } catch (Exception ignored) {}
        }
        state.gridOverlay.clear();
        state.gridTileMap.clear();
        state.gridTilePool.clear();
        state.gridTileNetIds.clear();
        state.groundYCache.clear();
        state.prevBfsX = Integer.MIN_VALUE;
        state.prevBfsZ = Integer.MIN_VALUE;
        state.gridTilesHiddenFromOthers = false;
    }

    // ========================================================
    // BFS FLOOD-FILL
    // ========================================================

    /**
     * FIX 3 — Delta BFS (ground-scan cache).
     *
     * groundYCache in GridPlayerState persists between moves.  For cells the BFS
     * has already visited, we return the cached groundY instead of reading blocks.
     * Only the new fringe cells entering the range on each step need actual block reads.
     *
     * On flat terrain a 1-cell move refreshes ~13 fringe cells instead of ~104,
     * cutting block I/O by ~85 %.
     *
     * Cache is evicted for cells that are now more than (maxMoves+2) Chebyshev steps
     * from the player, keeping memory bounded.
     */
    private static List<ReachableCell> floodFillReachable(World world, GridPlayerState state,
                                                          CollisionDetector collisionDetector,
                                                          UUID excludePlayer) {
        int curX = state.currentGridX, curZ = state.currentGridZ;
        float startY = state.npcY;

        List<ReachableCell> result = new ArrayList<>();
        Queue<BfsNode> queue = new LinkedList<>();
        Map<Long, Double> bestMoves = new HashMap<>();

        long startKey = packKey(curX, curZ);
        queue.add(new BfsNode(curX, curZ, state.remainingMoves, startY));
        bestMoves.put(startKey, state.remainingMoves);
        state.groundYCache.put(startKey, startY);   // cache player's own cell

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

                // FIX 3: use cached groundY — skip block scan if we already know this cell
                Float groundY = state.groundYCache.get(nKey);
                if (groundY == null) {
                    groundY = scanForGround(world, nx, nz, cur.groundY + 6.0f);
                    if (groundY == null) groundY = scanForGround(world, nx, nz, state.npcY + 6.0f);
                    if (groundY == null) continue;
                    state.groundYCache.put(nKey, groundY);   // cache for next move
                }

                float heightDiff = groundY - cur.groundY;
                if (heightDiff >= MAX_HEIGHT_UP || heightDiff < -MAX_HEIGHT_DOWN) continue;

                bestMoves.put(nKey, movesAfter);
                if (nx != curX || nz != curZ) {
                    if (prev == null) result.add(new ReachableCell(nx, nz, groundY));
                }
                queue.add(new BfsNode(nx, nz, movesAfter, groundY));
            }
        }

        // Evict cells now far outside the possible range to keep cache bounded
        int evictRadius = (int) Math.ceil(state.remainingMoves) + 4;
        state.groundYCache.entrySet().removeIf(e -> {
            long k = e.getKey();
            int kx = (int)(k >> 32), kz = (int)(k & 0xFFFFFFFFL);
            return Math.max(Math.abs(kx - curX), Math.abs(kz - curZ)) > evictRadius;
        });

        state.prevBfsX = curX;
        state.prevBfsZ = curZ;
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
        cachedDifficultModel = loadModel(MODEL_DIFFICULT); // BUG FIX
        if (cachedPlayerModel  == null) cachedPlayerModel  = cachedDefaultModel;
        if (cachedDefaultModel == null) {
            cachedDefaultModel = loadModel(FALLBACK_MODEL);
            cachedPlayerModel  = cachedDefaultModel;
        }
        if (cachedDifficultModel == null) cachedDifficultModel = cachedPlayerModel; // BUG FIX: fallback
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