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
 * Manages spell range visualization using Grid_Corner_Spell entities (RED)
 * Uses same entity spawning pattern as GridOverlayManager
 */
public class SpellVisualManager {
    private final World world;
    private final GridMoveManager gridManager;

    // Track spawned spell visualization entities per player UUID
    private final Map<UUID, List<Ref<EntityStore>>> playerSpellVisuals = new HashMap<>();

    private static final String SPELL_MODEL_ASSET_ID = "Grid_Corner_Spell";
    private static final String FALLBACK_MODEL = "Grid_Corner_Flat";

    private static Model cachedSpellModel = null;
    private static boolean modelLoadAttempted = false;

    // Same corner offsets as GridOverlayManager
    private static final float[][] OFFSETS = {
            {-0.5f, -0.5f},  // NW
            {+0.5f, -0.5f},  // NE
            {-0.5f, +0.5f},  // SW
            {+0.5f, +0.5f},  // SE
    };

    private static final float[] ROTATIONS = {
            (float) Math.PI,                // NW: 180°
            (float) (Math.PI / 2.0),        // NE: 90°
            (float) (-Math.PI / 2.0),       // SW: 270°
            0f,                             // SE: 0°
    };

    public SpellVisualManager(World world, GridMoveManager gridManager) {
        this.world = world;
        this.gridManager = gridManager;
    }

    /**
     * Show spell range/effect area in RED
     *
     * @param playerUUID Player casting spell
     * @param cells Grid cells to highlight
     */
    public void showSpellArea(UUID playerUUID, Set<SpellPatternCalculator.GridCell> cells) {
        // Clear existing visuals
        clearSpellVisuals(playerUUID);

        Model model = getSpellModel();
        if (model == null) {
            System.err.println("[Griddify] [SPELL] Failed to load spell visualization model!");
            return;
        }

        List<Ref<EntityStore>> newVisuals = new ArrayList<>();
        Store<EntityStore> store = world.getEntityStore().getStore();

        // Spawn Grid_Corner_Spell at each cell (RED)
        for (SpellPatternCalculator.GridCell cell : cells) {
            float centerX = (cell.x * 2.0f) + 1.0f;
            float centerZ = (cell.z * 2.0f) + 1.0f;

            // Get ground height using MonsterEntityController's scan method
            Float groundY = MonsterEntityController.scanForGroundPublic(world, cell.x, cell.z, 100.0f);
            if (groundY == null) {
                groundY = 64.0f; // Fallback height
            }
            float y = groundY + 0.01f;

            // Spawn 4 corner entities
            for (int i = 0; i < 4; i++) {
                float entityX = centerX + OFFSETS[i][0];
                float entityZ = centerZ + OFFSETS[i][1];

                Ref<EntityStore> ref = spawnQuarter(store, model, entityX, y, entityZ, ROTATIONS[i]);
                if (ref != null) {
                    newVisuals.add(ref);
                }
            }
        }

        playerSpellVisuals.put(playerUUID, newVisuals);

        System.out.println("[Griddify] [SPELL] Spawned spell overlay: " + cells.size() +
                " cells (" + newVisuals.size() + " entities)");
    }

    /**
     * Spawn single corner entity (same pattern as GridOverlayManager)
     */
    private Ref<EntityStore> spawnQuarter(Store<EntityStore> store, Model model,
                                          float x, float y, float z, float yawRad) {
        try {
            Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();

            Vector3d position = new Vector3d(x, y, z);
            Vector3f rotation = new Vector3f(0, yawRad, 0);

            holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(position, rotation));
            holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
            holder.addComponent(BoundingBox.getComponentType(), new BoundingBox(model.getBoundingBox()));
            holder.addComponent(NetworkId.getComponentType(),
                    new NetworkId(store.getExternalData().takeNextNetworkId()));
            holder.ensureComponent(UUIDComponent.getComponentType());

            return store.addEntity(holder, AddReason.SPAWN);
        } catch (Exception e) {
            System.err.println("[Griddify] [SPELL] Failed to spawn quarter: " + e.getMessage());
            return null;
        }
    }

    /**
     * Clear spell visuals for a player
     */
    public void clearSpellVisuals(UUID playerUUID) {
        List<Ref<EntityStore>> visuals = playerSpellVisuals.get(playerUUID);
        if (visuals != null) {
            Store<EntityStore> store = world.getEntityStore().getStore();

            for (Ref<EntityStore> ref : visuals) {
                if (ref != null && ref.isValid()) {
                    try {
                        store.removeEntity(ref, RemoveReason.REMOVE);
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
            visuals.clear();
        }
        playerSpellVisuals.remove(playerUUID);
    }

    /**
     * Clear all spell visuals (for cleanup)
     */
    public void clearAllSpellVisuals() {
        for (UUID playerUUID : new HashSet<>(playerSpellVisuals.keySet())) {
            clearSpellVisuals(playerUUID);
        }
    }

    /**
     * Get spell visualization model (RED Grid_Corner_Spell)
     */
    private static Model getSpellModel() {
        if (cachedSpellModel != null) {
            return cachedSpellModel;
        }

        if (modelLoadAttempted) {
            return null;
        }

        modelLoadAttempted = true;

        try {
            System.out.println("[Griddify] [SPELL] Attempting to load Model asset: " + SPELL_MODEL_ASSET_ID);

            ModelAsset modelAsset = ModelAsset.getAssetMap().getAsset(SPELL_MODEL_ASSET_ID);
            if (modelAsset != null) {
                cachedSpellModel = Model.createScaledModel(modelAsset, 1.0f);
                System.out.println("[Griddify] [SPELL] SUCCESS! Loaded Grid_Corner_Spell model");
                return cachedSpellModel;
            }

            System.out.println("[Griddify] [SPELL] Model asset '" + SPELL_MODEL_ASSET_ID + "' not found");

            // Fallback to Grid_Corner_Flat
            ModelAsset fallback = ModelAsset.getAssetMap().getAsset(FALLBACK_MODEL);
            if (fallback != null) {
                cachedSpellModel = Model.createScaledModel(fallback, 1.0f);
                System.out.println("[Griddify] [SPELL] Using Grid_Corner_Flat fallback");
                return cachedSpellModel;
            }
        } catch (Exception e) {
            System.err.println("[Griddify] [SPELL] Error loading model: " + e.getMessage());
            e.printStackTrace();
        }

        System.err.println("[Griddify] [SPELL] All model loading failed");
        return null;
    }
}