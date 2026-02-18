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
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkColumn;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;

import java.util.*;

/**
 * Manages spell range/area visualization using Grid_Spell entities (red).
 *
 * Y-placement rules:
 *  - Ground found  → spawn at groundY + 0.2
 *  - Fluid present → spawn on TOP of fluid (fluidTopY + 0.2) — spells hit fluid surfaces
 *  - Pure air      → SKIP (no grid on air cells)
 *
 * All grids use the NPC's stored npcY as the reference height, never the player body Y.
 */
public class SpellVisualManager {

    private final GridMoveManager gridManager;

    private final Map<UUID, List<Ref<EntityStore>>> playerSpellVisuals = new HashMap<>();

    private static final String SPELL_MODEL_ID    = "Grid_Spell";
    private static final String FALLBACK_MODEL_ID = "Grid_Basic";

    private static Model cachedSpellModel     = null;
    private static boolean modelLoadAttempted = false;

    public SpellVisualManager(GridMoveManager gridManager) {
        this.gridManager = gridManager;
    }

    // ========================================================
    // PUBLIC API
    // ========================================================

    /**
     * Show spell area in red.
     * Uses npcY (NPC ground height) as reference — never raw playerY body height.
     */
    public void showSpellArea(UUID playerUUID, Set<SpellPatternCalculator.GridCell> cells,
                              World world, float playerY) {
        clearSpellVisuals(playerUUID, world);

        Model model = getSpellModel();
        if (model == null) {
            System.err.println("[Griddify] [SPELL] Failed to load spell model!");
            return;
        }

        // Use NPC's stored ground Y as reference — ignore raw body Y which is above ground
        float referenceY = resolveNpcY(playerUUID, playerY);

        List<Ref<EntityStore>> newVisuals = new ArrayList<>();
        Store<EntityStore> store = world.getEntityStore().getStore();

        for (SpellPatternCalculator.GridCell cell : cells) {
            float cx = (cell.x * 2.0f) + 1.0f;
            float cz = (cell.z * 2.0f) + 1.0f;

            // scanForGroundPublic scans from (ref - MIN_OFFSET=3) downward.
            // referenceY is npcY which is already AT ground, so we pass referenceY + 3
            // to make the scan start just above ground and sweep down to find the surface.
            Float groundY = MonsterEntityController.scanForGroundPublic(world, cell.x, cell.z, referenceY + 3.0f);

            // Fallback: use referenceY directly (flat terrain, stairs, etc.)
            if (groundY == null) groundY = referenceY;

            float y = groundY + 0.03f; // Grid_Spell: +0.03 Y offset
            Ref<EntityStore> ref = spawnTile(store, model, cx, y, cz);
            if (ref != null) newVisuals.add(ref);
        }

        playerSpellVisuals.put(playerUUID, newVisuals);
        System.out.println("[Griddify] [SPELL] Spell overlay: " + cells.size() +
                " cells → " + newVisuals.size() + " placed (skipped air/out-of-range)");
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

    // ========================================================
    // Y REFERENCE — always use NPC ground Y, not player body Y
    // ========================================================

    /**
     * Resolve the reference Y for ground scanning.
     * Prefers the NPC's stored npcY (ground-snapped) over raw player body Y.
     * Raw player body Y is above ground (eye-level), causing grids to spawn too high.
     */
    private float resolveNpcY(UUID playerUUID, float fallbackY) {
        for (Map.Entry<UUID, GridPlayerState> e : gridManager.getStateEntries()) {
            if (e.getKey().equals(playerUUID)) {
                float ny = e.getValue().npcY;
                if (ny != 0.0f) return ny;
                break;
            }
        }
        // Last resort: step down from body Y since body Y is roughly 1.8 blocks above feet
        return fallbackY - 1.8f;
    }

    // ========================================================
    // FLUID SURFACE DETECTION
    // ========================================================

    /**
     * Find the top Y of a fluid body at this grid cell, scanning downward from referenceY.
     * Returns the Y coordinate where the fluid surface is (for placing the grid on top).
     */
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
                if (hasFluid) {
                    // Return top of this fluid layer
                    return (float) checkY + 1.0f;
                }
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

    private static Model getSpellModel() {
        if (cachedSpellModel != null) return cachedSpellModel;
        if (modelLoadAttempted) return null;
        modelLoadAttempted = true;
        cachedSpellModel = loadModel(SPELL_MODEL_ID);
        if (cachedSpellModel == null) cachedSpellModel = loadModel(FALLBACK_MODEL_ID);
        return cachedSpellModel;
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