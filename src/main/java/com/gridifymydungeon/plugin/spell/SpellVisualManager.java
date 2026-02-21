package com.gridifymydungeon.plugin.spell;

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

import java.util.*;

/**
 * Manages spell range/area visualization using Grid_Spell entities (red).
 *
 * Ground scanning now uses the same direct world.getBlockType() approach as
 * GridOverlayManager — scanning from referenceY+30 downward to handle terrain
 * up to 30 blocks above or below the caster correctly.
 */
public class SpellVisualManager {

    private final GridMoveManager gridManager;
    private final Map<UUID, List<Ref<EntityStore>>> playerSpellVisuals = new HashMap<>();

    private static final String SPELL_MODEL_ID    = "Grid_Spell";
    private static final String FALLBACK_MODEL_ID = "Grid_Basic";
    private static final String RANGE_MODEL_ID    = "Grid_Range";

    private static Model cachedSpellModel     = null;
    private static Model cachedRangeModel     = null;
    private static boolean modelLoadAttempted = false;
    private static boolean rangeModelAttempted = false;

    // Per-player range overlay tiles (separate from the aim overlay)
    private final Map<UUID, List<Ref<EntityStore>>> playerRangeVisuals = new HashMap<>();

    public SpellVisualManager(GridMoveManager gridManager) {
        this.gridManager = gridManager;
    }

    // ========================================================
    // PUBLIC API
    // ========================================================

    public void showSpellArea(UUID playerUUID, Set<SpellPatternCalculator.GridCell> cells,
                              World world, float playerY) {
        clearSpellVisuals(playerUUID, world);

        Model model = getSpellModel();
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

            // Use the same scanForGround approach as GridOverlayManager:
            // start 30 blocks ABOVE referenceY and scan down 45 blocks total,
            // so both uphill and downhill cells are found correctly.
            Float groundY = scanForGround(world, cell.x, cell.z, referenceY + 30.0f, 45);
            if (groundY == null) groundY = referenceY;

            float y = groundY + 0.03f;
            Ref<EntityStore> ref = spawnTile(store, model, cx, y, cz);
            if (ref != null) newVisuals.add(ref);
        }

        playerSpellVisuals.put(playerUUID, newVisuals);
        System.out.println("[Griddify] [SPELL] Spell overlay: " + cells.size() +
                " cells → " + newVisuals.size() + " placed");
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

    /**
     * Shows a ring of Grid_Range tiles covering every cell within the spell's
     * range from the caster's grid position. Called on /cast so the player can
     * see exactly how far the spell reaches before aiming.
     * SELF/AURA spells with range 0 skip the overlay (they fire on the caster).
     */
    public void showRangeOverlay(UUID playerUUID, int casterGridX, int casterGridZ,
                                 int rangeGrids, World world, float npcY) {
        clearRangeOverlay(playerUUID, world);
        if (rangeGrids <= 0) return;

        Model model = getRangeModel();
        if (model == null) {
            System.err.println("[Griddify] [RANGE] Failed to load Grid_Range model!");
            return;
        }

        float referenceY = resolveNpcY(playerUUID, npcY);
        List<Ref<EntityStore>> tiles = new ArrayList<>();
        Store<EntityStore> store = world.getEntityStore().getStore();

        // All cells with Euclidean distance <= rangeGrids from caster
        for (int dx = -rangeGrids; dx <= rangeGrids; dx++) {
            for (int dz = -rangeGrids; dz <= rangeGrids; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > rangeGrids) continue;

                int cellX = casterGridX + dx;
                int cellZ = casterGridZ + dz;

                float wx = (cellX * 2.0f) + 1.0f;
                float wz = (cellZ * 2.0f) + 1.0f;

                Float groundY = scanForGround(world, cellX, cellZ, referenceY + 30.0f, 45);
                if (groundY == null) groundY = referenceY;

                Ref<EntityStore> ref = spawnTile(store, model, wx, groundY + 0.02f, wz);
                if (ref != null) tiles.add(ref);
            }
        }

        playerRangeVisuals.put(playerUUID, tiles);
        System.out.println("[Griddify] [RANGE] Range overlay: range=" + rangeGrids
                + " cells=" + tiles.size() + " for " + playerUUID);
    }

    public void clearRangeOverlay(UUID playerUUID, World world) {
        List<Ref<EntityStore>> tiles = playerRangeVisuals.remove(playerUUID);
        if (tiles == null) return;
        Store<EntityStore> store = world.getEntityStore().getStore();
        for (Ref<EntityStore> ref : tiles) {
            if (ref != null && ref.isValid()) {
                try { store.removeEntity(ref, RemoveReason.REMOVE); } catch (Exception ignored) {}
            }
        }
        tiles.clear();
    }

    public void clearAllRangeOverlays(World world) {
        for (UUID id : new HashSet<>(playerRangeVisuals.keySet())) clearRangeOverlay(id, world);
    }

    // ========================================================
    // GROUND SCANNING — identical logic to GridOverlayManager
    // ========================================================

    /**
     * Scans downward from referenceY for the first solid non-barrier block.
     * Returns groundY = blockY + 1.0f (top surface of that block).
     * Identical to GridOverlayManager.scanForGround — this is what actually works.
     */
    public static Float scanForGround(World world, int gridX, int gridZ, float referenceY, int scanDepth) {
        int startY = (int) Math.floor(referenceY);
        int endY   = startY - scanDepth;
        for (int blockY = startY; blockY >= endY; blockY--) {
            boolean hasGround = false;
            for (int xOff = 0; xOff < 2; xOff++) {
                for (int zOff = 0; zOff < 2; zOff++) {
                    BlockType block = world.getBlockType(
                            new Vector3i((gridX * 2) + xOff, blockY, (gridZ * 2) + zOff));
                    if (isSolid(block) && !isBarrier(block)) {
                        hasGround = true;
                    }
                }
            }
            if (hasGround) {
                return blockY + 1.0f;
            }
        }
        return null;
    }

    private static boolean isSolid(BlockType block) {
        return block != null && block.getMaterial() == BlockMaterial.Solid;
    }

    private static boolean isBarrier(BlockType block) {
        return block != null && block.getId() != null &&
                block.getId().toLowerCase().contains("barrier");
    }

    // ========================================================
    // Y REFERENCE
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

    private static Model getSpellModel() {
        if (cachedSpellModel != null) return cachedSpellModel;
        if (modelLoadAttempted) return null;
        modelLoadAttempted = true;
        cachedSpellModel = loadModel(SPELL_MODEL_ID);
        if (cachedSpellModel == null) cachedSpellModel = loadModel(FALLBACK_MODEL_ID);
        return cachedSpellModel;
    }

    private static Model getRangeModel() {
        if (cachedRangeModel != null) return cachedRangeModel;
        if (rangeModelAttempted) return null;
        rangeModelAttempted = true;
        cachedRangeModel = loadModel(RANGE_MODEL_ID);
        if (cachedRangeModel == null) cachedRangeModel = loadModel(FALLBACK_MODEL_ID);
        return cachedRangeModel;
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