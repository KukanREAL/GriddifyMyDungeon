package com.gridifymydungeon.plugin.spell;

import com.gridifymydungeon.plugin.dnd.commands.MonsterEntityController;
import com.gridifymydungeon.plugin.gridmove.GridMoveManager;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.*;

/**
 * Manages spell range/area visualization using Grid_Spell entities (red).
 *
 * One entity per 2x2 grid cell, centred at the cell's world-space midpoint.
 * No rotation needed — the texture covers the full 2x2 tile.
 */
public class SpellVisualManager {

    private final GridMoveManager gridManager;

    private final Map<UUID, List<Ref<EntityStore>>> playerSpellVisuals = new HashMap<>();

    private static final String SPELL_MODEL_ID   = "Grid_Spell";
    private static final String FALLBACK_MODEL_ID = "Grid_Basic";

    private static Model cachedSpellModel    = null;
    private static boolean modelLoadAttempted = false;

    public SpellVisualManager(GridMoveManager gridManager) {
        this.gridManager = gridManager;
    }

    // ========================================================
    // PUBLIC API
    // ========================================================

    /**
     * Show spell area in red. One tile entity per cell, centred in the 2x2 block.
     */
    public void showSpellArea(UUID playerUUID, Set<SpellPatternCalculator.GridCell> cells,
                              World world, float playerY) {
        clearSpellVisuals(playerUUID, world);

        Model model = getSpellModel();
        if (model == null) {
            System.err.println("[Griddify] [SPELL] Failed to load spell model!");
            return;
        }

        float referenceY = resolveReferenceY(playerUUID, playerY);

        List<Ref<EntityStore>> newVisuals = new ArrayList<>();
        Store<EntityStore> store = world.getEntityStore().getStore();

        for (SpellPatternCalculator.GridCell cell : cells) {
            // Centre of the 2x2 block
            float cx = (cell.x * 2.0f) + 1.0f;
            float cz = (cell.z * 2.0f) + 1.0f;

            Float groundY = MonsterEntityController.scanForGroundPublic(world, cell.x, cell.z, referenceY);
            if (groundY == null) groundY = referenceY;
            float y = groundY + 0.01f;

            Ref<EntityStore> ref = spawnTile(store, model, cx, y, cz);
            if (ref != null) newVisuals.add(ref);
        }

        playerSpellVisuals.put(playerUUID, newVisuals);
        System.out.println("[Griddify] [SPELL] Spell overlay: " + cells.size() +
                " cells (" + newVisuals.size() + " entities)");
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
    // HELPERS
    // ========================================================

    private float resolveReferenceY(UUID playerUUID, float playerY) {
        if (playerY != 0.0f) return playerY;
        for (Map.Entry<UUID, com.gridifymydungeon.plugin.gridmove.GridPlayerState> e :
                gridManager.getStateEntries()) {
            if (e.getKey().equals(playerUUID)) {
                float ny = e.getValue().npcY;
                if (ny != 0.0f) return ny;
                break;
            }
        }
        return 64.0f;
    }

    // ========================================================
    // ENTITY SPAWNING — single tile per cell, no rotation
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