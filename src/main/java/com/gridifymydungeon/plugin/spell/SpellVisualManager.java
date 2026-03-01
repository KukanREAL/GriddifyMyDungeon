package com.gridifymydungeon.plugin.spell;

import com.gridifymydungeon.plugin.dnd.commands.MonsterEntityController;
import com.gridifymydungeon.plugin.gridmove.GridMoveManager;
import com.gridifymydungeon.plugin.gridmove.GridPlayerState;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
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
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages spell range/area visualization.
 *
 * Height priority (stacks over Grid_Basic and Grid_Player):
 *   Grid_Range  +0.04  — yellow ring, private, shown during /cast
 *   Grid_Spell  +0.05  — red area, private, shown when aiming
 *
 * FIX #1: showRangeOverlay() / clearRangeOverlay() added.
 *   Tiles spawn at y=-30, teleport to ground+0.04, hide from non-owners after 150ms.
 * FIX #3: Height offsets updated:  Grid_Spell +0.05, Grid_Range +0.04.
 */
public class SpellVisualManager {

    private final GridMoveManager gridManager;

    // Spell area (red, Grid_Spell)
    private final Map<UUID, List<Ref<EntityStore>>> playerSpellVisuals = new HashMap<>();

    // Spell range ring (yellow, Grid_Range)  FIX #1
    private final Map<UUID, List<Ref<EntityStore>>> playerRangeVisuals = new HashMap<>();

    // Pools for re-using visual entities
    private final Map<UUID, List<Ref<EntityStore>>> playerSpellPool = new HashMap<>();
    private final Map<UUID, List<Ref<EntityStore>>> playerRangePool = new HashMap<>();

    private static final String SPELL_MODEL_ID    = "Grid_Spell";
    private static final String RANGE_MODEL_ID    = "Grid_Range";
    private static final String FALLBACK_MODEL_ID = "Grid_Basic";

    private static Model cachedSpellModel     = null;
    private static Model cachedRangeModel     = null;
    private static boolean modelLoadAttempted = false;

    // FIX #3 — height offsets
    private static final float SPELL_Y_OFFSET = 0.05f;  // Grid_Spell: highest
    private static final float RANGE_Y_OFFSET = 0.04f;  // Grid_Range: below spell

    // Scheduler for hide-from-others delay  FIX #1
    private static final ScheduledExecutorService SCHED =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "spell-visual-hider");
                t.setDaemon(true);
                return t;
            });

    public SpellVisualManager(GridMoveManager gridManager) {
        this.gridManager = gridManager;
    }

    // ========================================================
    // SPELL AREA (Grid_Spell, red)
    // ========================================================

    /**
     * Show spell impact area in red (private to owner).
     * FIX #3: Uses SPELL_Y_OFFSET (+0.05) so it renders above Grid_Range.
     * FIX #2: Spawn at y=-30, teleport to real Y, hide from non-owners.
     */
    public void showSpellArea(UUID playerUUID, Set<SpellPatternCalculator.GridCell> cells,
                              World world, float playerY) {
        showSpellArea(playerUUID, cells, world, playerY, null);
    }

    /**
     * Show spell impact area in red, optionally private to owner.
     * @param owner PlayerRef for privacy filter (null = visible to all)
     */
    public void showSpellArea(UUID playerUUID, Set<SpellPatternCalculator.GridCell> cells,
                              World world, float playerY, PlayerRef owner) {
        clearSpellVisuals(playerUUID, world);

        Model model = getModel(SPELL_MODEL_ID);
        if (model == null) {
            System.err.println("[Griddify] [SPELL] Failed to load spell model!");
            return;
        }

        float referenceY = resolveNpcY(playerUUID, playerY);
        List<Ref<EntityStore>> newVisuals = new ArrayList<>();
        Store<EntityStore> store = world.getEntityStore().getStore();

        for (SpellPatternCalculator.GridCell cell : cells) {
            float cx = (cell.x * 2.0f) + 1.0f;
            float cz = (cell.z * 2.0f) + 1.0f;

            Float groundY = MonsterEntityController.scanForGroundPublic(world, cell.x, cell.z, referenceY + 3.0f);
            if (groundY == null) groundY = referenceY;

            float targetY = groundY + SPELL_Y_OFFSET;

            // FIX #2: Spawn at y=-30 (invisible to all), then teleport to real Y
            Ref<EntityStore> ref = (owner != null)
                    ? spawnTile(store, model, cx, -30f, cz)
                    : spawnTile(store, model, cx, targetY, cz);
            if (ref == null) continue;

            // Teleport to real position if spawned privately
            if (owner != null) {
                try {
                    TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
                    if (tc != null) tc.setPosition(new Vector3d(cx, targetY, cz));
                } catch (Exception ignored) {}
            }

            newVisuals.add(ref);
        }

        playerSpellVisuals.put(playerUUID, newVisuals);

        // FIX #2: Hide from non-owners after entity-tracker has had time to broadcast
        if (owner != null) {
            final List<Ref<EntityStore>> finalRefs = newVisuals;
            final PlayerRef finalOwner = owner;
            SCHED.schedule(() -> world.execute(() ->
                    hideRefsFromOthers(world, finalRefs, finalOwner)
            ), 150L, TimeUnit.MILLISECONDS);
        }

        System.out.println("[Griddify] [SPELL] Spell overlay: " + cells.size() +
                " cells → " + newVisuals.size() + " placed" + (owner != null ? " (private)" : ""));
    }

    public void clearSpellVisuals(UUID playerUUID, World world) {
        List<Ref<EntityStore>> visuals = playerSpellVisuals.remove(playerUUID);
        if (visuals == null) return;
        Store<EntityStore> store = world.getEntityStore().getStore();
        for (Ref<EntityStore> ref : visuals) {
            if (ref != null && ref.isValid()) {
                try { store.removeEntity(ref, RemoveReason.REMOVE); } catch (Exception ignored) {}
            }
        }
        visuals.clear();
    }

    public void clearAllSpellVisuals(World world) {
        for (UUID id : new HashSet<>(playerSpellVisuals.keySet())) clearSpellVisuals(id, world);
    }

    /** Fully destroy spell visuals AND pools for ALL players. */
    public void destroyAllSpellVisuals(World world) {
        for (UUID id : new HashSet<>(playerSpellVisuals.keySet())) destroySpellVisuals(id, world);
        // Also destroy any orphaned pool entries
        for (UUID id : new HashSet<>(playerSpellPool.keySet())) destroySpellVisuals(id, world);
    }

    /** Fully destroy spell visuals AND its pool for one player. */
    public void destroySpellVisuals(UUID playerUUID, World world) {
        List<Ref<EntityStore>> visuals = playerSpellVisuals.remove(playerUUID);
        List<Ref<EntityStore>> pool = playerSpellPool.remove(playerUUID);
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (visuals != null) {
            for (Ref<EntityStore> ref : visuals) {
                if (ref != null && ref.isValid()) {
                    try { store.removeEntity(ref, RemoveReason.REMOVE); } catch (Exception ignored) {}
                }
            }
            visuals.clear();
        }
        if (pool != null) {
            for (Ref<EntityStore> ref : pool) {
                if (ref != null && ref.isValid()) {
                    try { store.removeEntity(ref, RemoveReason.REMOVE); } catch (Exception ignored) {}
                }
            }
            pool.clear();
        }
    }

// ========================================================
// STACKED SPELL TILES (multi-target same cell)
// ========================================================

    /**
     * Spawn an additional spell tile at the given cell, scaled down based on hit index.
     * hitIndex 1 = scale 1.0, hitIndex 2 = 0.8, hitIndex 3 = 0.6, min 0.2
     */
    public void addStackedSpellTile(UUID playerUUID, SpellPatternCalculator.GridCell cell,
                                    int hitIndex, World world, float refY) {
        float scale = Math.max(0.2f, 1.0f - (hitIndex - 1) * 0.2f);
        Model model = getSpellModelScaled(scale);
        if (model == null) return;

        float referenceY = resolveNpcY(playerUUID, refY);
        Float groundY = MonsterEntityController.scanForGroundPublic(world, cell.x, cell.z, referenceY + 3.0f);
        if (groundY == null) groundY = referenceY;

        float cx = (cell.x * 2.0f) + 1.0f;
        float cz = (cell.z * 2.0f) + 1.0f;
        float y  = groundY + 0.03f + (hitIndex * 0.01f); // slightly higher per stack

        Store<EntityStore> store = world.getEntityStore().getStore();
        Ref<EntityStore> ref = spawnTile(store, model, cx, y, cz);
        if (ref != null) {
            playerSpellVisuals.computeIfAbsent(playerUUID, k -> new ArrayList<>()).add(ref);
        }
    }

    private static Model getSpellModelScaled(float scale) {
        try {
            ModelAsset asset = ModelAsset.getAssetMap().getAsset(SPELL_MODEL_ID);
            if (asset != null) return Model.createScaledModel(asset, scale);
            asset = ModelAsset.getAssetMap().getAsset(FALLBACK_MODEL_ID);
            if (asset != null) return Model.createScaledModel(asset, scale);
        } catch (Exception e) {
            System.err.println("[Griddify] [SPELL] Error loading scaled model: " + e.getMessage());
        }
        return null;
    }

// ========================================================
// GROUND SCANNING (public static — used by SpellVisualEffect)
// ========================================================

    /**
     * Scan downward from referenceY to find the ground surface Y at the given grid cell.
     * Public static so SpellVisualEffect can call it directly.
     */
    public static Float scanForGround(World world, int gridX, int gridZ,
                                      float referenceY, int scanDepth) {
        try {
            int startY = (int) Math.floor(referenceY);
            int endY   = startY - scanDepth;
            for (int blockY = startY; blockY >= endY; blockY--) {
                boolean hasGround = false;
                for (int xOff = 0; xOff < 2; xOff++) {
                    for (int zOff = 0; zOff < 2; zOff++) {
                        BlockType block = world.getBlockType(
                                new com.hypixel.hytale.math.vector.Vector3i(
                                        (gridX * 2) + xOff, blockY, (gridZ * 2) + zOff));
                        if (block != null
                                && block.getMaterial() == BlockMaterial.Solid
                                && (block.getId() == null
                                || !block.getId().toLowerCase().contains("barrier"))) {
                            hasGround = true;
                        }
                    }
                }
                if (hasGround) return (float) blockY + 1.0f;
            }
        } catch (Exception ignored) {}
        return null;
    }

    // ========================================================
    // SPELL RANGE RING (Grid_Range, yellow)  FIX #1
    // ========================================================

    /**
     * FIX #1: Show spell reach ring (Grid_Range) visible only to the owning player.
     * Tiles spawn at y=-30, teleport to ground+0.04, hide from non-owners after 150ms.
     *
     * @param rangeGrids 0 = skip (SELF/AURA instant cast)
     * @param owner      PlayerRef of the caster — used for privacy filter
     */
    public void showRangeOverlay(UUID playerUUID, int casterGridX, int casterGridZ,
                                 int rangeGrids, World world, float npcY, PlayerRef owner) {
        clearRangeOverlay(playerUUID, world);

        if (rangeGrids <= 0) return;

        Model model = getRangeModel();
        if (model == null) {
            System.err.println("[Griddify] [RANGE] Failed to load Grid_Range model!");
            return;
        }

        Store<EntityStore> store = world.getEntityStore().getStore();
        List<Ref<EntityStore>> refs = new ArrayList<>();

        // BUG 1 FIX: Fill the ENTIRE reachable area, not just the outer ring.
        // Every cell within spell range (excluding caster's own cell) gets a tile.
        for (int dx = -rangeGrids; dx <= rangeGrids; dx++) {
            for (int dz = -rangeGrids; dz <= rangeGrids; dz++) {
                int chebyshev = Math.max(Math.abs(dx), Math.abs(dz));
                if (chebyshev == 0) continue;             // skip caster's own cell
                if (chebyshev > rangeGrids) continue;     // outside range

                int gx = casterGridX + dx;
                int gz = casterGridZ + dz;

                float cx = (gx * 2.0f) + 1.0f;
                float cz2 = (gz * 2.0f) + 1.0f;

                Float groundY = MonsterEntityController.scanForGroundPublic(world, gx, gz, npcY + 3.0f);
                if (groundY == null) groundY = npcY;

                float targetY = groundY + RANGE_Y_OFFSET; // FIX #3

                // BUG FIX: Spawn directly at real Y so entity tracker registers position before hide packet
                Ref<EntityStore> ref = spawnTile(store, model, cx, targetY, cz2);
                if (ref == null) continue;

                refs.add(ref);
            }
        }

        playerRangeVisuals.put(playerUUID, refs);

        // BUG 1 FIX: Hide from non-owners 300ms after spawn.
        // 300ms gives entity-tracker time to broadcast spawns to nearby players FIRST.
        if (owner != null) {
            final List<Ref<EntityStore>> finalRefs = refs;
            final PlayerRef finalOwner = owner;
            SCHED.schedule(() -> world.execute(() ->
                    hideRefsFromOthers(world, finalRefs, finalOwner)
            ), 300L, TimeUnit.MILLISECONDS);
        }

        System.out.println("[Griddify] [RANGE] Range ring: " + refs.size() + " tiles at radius " + rangeGrids);
    }

    /** Legacy overload without owner (range ring visible to all — use sparingly). */
    public void showRangeOverlay(UUID playerUUID, int casterGridX, int casterGridZ,
                                 int rangeGrids, World world, float npcY) {
        showRangeOverlay(playerUUID, casterGridX, casterGridZ, rangeGrids, world, npcY, null);
    }

    /** Clear the Grid_Range ring — parks tiles at y=-30 for reuse, never destroys them. */
    public void clearRangeOverlay(UUID playerUUID, World world) {
        List<Ref<EntityStore>> refs = playerRangeVisuals.remove(playerUUID);
        if (refs == null) return;
        final float PARKED_Y = -30f;
        Store<EntityStore> store = world.getEntityStore().getStore();
        List<Ref<EntityStore>> pool = playerRangePool.computeIfAbsent(playerUUID, k -> new ArrayList<>());
        for (Ref<EntityStore> ref : refs) {
            if (ref != null && ref.isValid()) {
                try {
                    TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
                    if (tc != null) tc.setPosition(new Vector3d(0, PARKED_Y, 0));
                } catch (Exception ignored) {}
                pool.add(ref);
            }
        }
        refs.clear();
    }

    /** Fully destroy the range ring AND its pool (call on session end / /gridoff). */
    public void destroyRangeOverlay(UUID playerUUID, World world) {
        List<Ref<EntityStore>> refs = playerRangeVisuals.remove(playerUUID);
        List<Ref<EntityStore>> pool = playerRangePool.remove(playerUUID);
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (refs != null) for (Ref<EntityStore> ref : refs)
            if (ref != null && ref.isValid()) { try { store.removeEntity(ref, RemoveReason.REMOVE); } catch (Exception ignored) {} }
        if (pool != null) for (Ref<EntityStore> ref : pool)
            if (ref != null && ref.isValid()) { try { store.removeEntity(ref, RemoveReason.REMOVE); } catch (Exception ignored) {} }
    }

    public void clearAllRangeVisuals(World world) {
        for (UUID id : new HashSet<>(playerRangeVisuals.keySet())) clearRangeOverlay(id, world);
    }

    // ========================================================
    // HIDE FROM OTHERS  FIX #1
    // ========================================================

    /**
     * Send EntityUpdates(removed) for each ref to every player EXCEPT the owner.
     */
    private static void hideRefsFromOthers(World world, List<Ref<EntityStore>> refs, PlayerRef owner) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        for (Ref<EntityStore> ref : refs) {
            if (ref == null || !ref.isValid()) continue;
            try {
                NetworkId netIdComp = store.getComponent(ref, NetworkId.getComponentType());
                if (netIdComp == null) continue;
                int netId = netIdComp.getId();
                com.hypixel.hytale.server.core.universe.world.PlayerUtil.forEachPlayerThatCanSeeEntity(
                        ref,
                        (pRef, pRefComponent, ca) -> {
                            if (!pRefComponent.getUuid().equals(owner.getUuid())) {
                                pRefComponent.getPacketHandler().writeNoCache(
                                        new com.hypixel.hytale.protocol.packets.entities.EntityUpdates(
                                                new int[]{netId}, null));
                            }
                        },
                        store);
            } catch (Exception ignored) {}
        }
    }

    // ========================================================
    // Y REFERENCE — always use NPC ground Y, not player body Y
    // ========================================================

    private float resolveNpcY(UUID playerUUID, float fallbackY) {
        for (Map.Entry<UUID, GridPlayerState> e : gridManager.getStateEntries()) {
            if (e.getKey().equals(playerUUID)) {
                float ny = e.getValue().npcY;
                if (ny != 0.0f) return ny;
                break;
            }
        }
        return fallbackY - 1.8f;
    }

    // ========================================================
    // FLUID SURFACE DETECTION
    // ========================================================

    private static Float findFluidSurfaceY(World world, int gridX, int gridZ, float referenceY) {
        try {
            Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
            int startY = (int) Math.floor(referenceY - 3);
            int endY   = (int) Math.floor(referenceY - 15);

            for (int checkY = startY; checkY >= endY; checkY--) {
                boolean hasFluid = false;
                for (int xOff = 0; xOff < 2 && !hasFluid; xOff++) {
                    for (int zOff = 0; zOff < 2 && !hasFluid; zOff++) {
                        int bx = (gridX * 2) + xOff;
                        int bz = (gridZ * 2) + zOff;
                        FluidSection fs = findFluidSection(world, chunkStore, bx, checkY, bz);
                        if (fs != null && fs.getFluidId(bx, checkY, bz) != 0) {
                            hasFluid = true;
                        }
                    }
                }
                if (hasFluid) return (float) checkY + 1.0f;
            }
        } catch (Exception ignored) {}
        return null;
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
    // ENTITY SPAWNING
    // ========================================================

    private Ref<EntityStore> spawnTile(Store<EntityStore> store, Model model,
                                       float x, float y, float z) {
        try {
            Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
            holder.addComponent(TransformComponent.getComponentType(),
                    new TransformComponent(new Vector3d(x, y, z), new Vector3f(0, 0, 0)));
            holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
            holder.addComponent(BoundingBox.getComponentType(), new BoundingBox(model.getBoundingBox()));
            holder.addComponent(NetworkId.getComponentType(),
                    new NetworkId(store.getExternalData().takeNextNetworkId()));
            holder.ensureComponent(UUIDComponent.getComponentType());
            return store.addEntity(holder, AddReason.SPAWN);
        } catch (Exception e) {
            System.err.println("[Griddify] [SPELL] Failed to spawn tile: " + e.getMessage());
            return null;
        }
    }

    // ========================================================
    // MODEL LOADING
    // ========================================================

    private static Model getModel(String id) {
        if (!modelLoadAttempted) initModels();
        if (SPELL_MODEL_ID.equals(id)) return cachedSpellModel;
        return cachedSpellModel; // fallback
    }

    private static Model getRangeModel() {
        if (!modelLoadAttempted) initModels();
        return cachedRangeModel != null ? cachedRangeModel : cachedSpellModel;
    }

    private static void initModels() {
        modelLoadAttempted = true;
        cachedSpellModel = loadModel(SPELL_MODEL_ID);
        cachedRangeModel = loadModel(RANGE_MODEL_ID);
        // BUG 1 FIX: robust fallback — Grid_Spell and Grid_Range each fall back to Grid_Basic
        // so range tiles always appear even if the dedicated models are missing
        if (cachedSpellModel == null) cachedSpellModel = loadModel(FALLBACK_MODEL_ID);
        if (cachedRangeModel == null) cachedRangeModel = loadModel(FALLBACK_MODEL_ID);
        if (cachedRangeModel == null) cachedRangeModel = cachedSpellModel;
        System.out.println("[Griddify] [SPELL] Models — spell=" +
                (cachedSpellModel != null ? "OK" : "NULL") +
                " range=" + (cachedRangeModel != null ? "OK" : "NULL"));
    }

    private static Model loadModel(String id) {
        try {
            ModelAsset asset = ModelAsset.getAssetMap().getAsset(id);
            if (asset != null) {
                System.out.println("[Griddify] [SPELL] Loaded model: " + id);
                return Model.createScaledModel(asset, 1.0f);
            }
            System.out.println("[Griddify] [SPELL] Model not found: " + id);
        } catch (Exception e) {
            System.err.println("[Griddify] [SPELL] Error loading " + id + ": " + e.getMessage());
        }
        return null;
    }
}