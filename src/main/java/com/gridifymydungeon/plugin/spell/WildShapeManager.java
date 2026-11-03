package com.gridifymydungeon.plugin.spell;

import com.gridifymydungeon.plugin.gridmove.GridMoveManager;
import com.gridifymydungeon.plugin.gridmove.GridPlayerState;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles Wild Shape transformations.
 *
 * On transform:  swap the player's NPC entity ModelComponent to the creature model.
 *                Store original model reference so we can revert.
 * On revert:     restore original model, clear active form.
 *
 * Revert triggers:
 *   - Player casts Wild_Shape again (toggle)
 *   - Player HP drops to 0 (checked by CastFinalCommand / combat system)
 */
public class WildShapeManager {

    /** Stores the original Model of each transformed player's NPC (keyed by player UUID). */
    private final Map<UUID, Model> originalModels = new HashMap<>();

    private final GridMoveManager gridMoveManager;

    public WildShapeManager(GridMoveManager gridMoveManager) {
        this.gridMoveManager = gridMoveManager;
    }

    /**
     * Transform the player's NPC entity into the Wild Shape form.
     * @param spellName e.g. "Wild_Shape_Bear"
     * @return true if transform succeeded
     */
    public boolean transform(PlayerRef playerRef, World world, String spellName) {
        GridPlayerState state = gridMoveManager.getState(playerRef);
        Ref<EntityStore> npcRef = state.npcEntity;
        if (npcRef == null || !npcRef.isValid()) {
            playerRef.sendMessage(
                    com.hypixel.hytale.server.core.Message.raw(
                                    "[Griddify] Wild Shape requires an active NPC (/gridmove first).")
                            .color("#FF0000"));
            return false;
        }

        // Derive model asset ID from spell name: "Wild_Shape_Bear" → asset "Wild_Shape_Bear"
        String assetId = spellName; // e.g. Wild_Shape_Bear.blockymodel in Common/Items
        Model creatureModel = loadModel(assetId);
        if (creatureModel == null) {
            playerRef.sendMessage(
                    com.hypixel.hytale.server.core.Message.raw(
                                    "[Griddify] Wild Shape model '" + assetId + "' not found.")
                            .color("#FF0000"));
            return false;
        }

        // Store original model before swapping
        world.execute(() -> {
            try {
                var store = world.getEntityStore().getStore();
                ModelComponent oldComp = store.getComponent(npcRef, ModelComponent.getComponentType());
                if (oldComp != null) {
                    originalModels.put(playerRef.getUuid(), oldComp.getModel());
                }
                store.replaceComponent(npcRef, ModelComponent.getComponentType(),
                        new ModelComponent(creatureModel));
                store.replaceComponent(npcRef, PersistentModel.getComponentType(),
                        new PersistentModel(creatureModel.toReference()));
                store.replaceComponent(npcRef, BoundingBox.getComponentType(),
                        new BoundingBox(creatureModel.getBoundingBox()));
            } catch (Exception e) {
                System.err.println("[Griddify] [WILDSHAPE] Transform failed: " + e.getMessage());
            }
        });
        return true;
    }

    /**
     * Revert the player's NPC back to the original model.
     * @param announce whether to send a chat message
     */
    public void revert(PlayerRef playerRef, World world, boolean announce) {
        GridPlayerState state = gridMoveManager.getState(playerRef);
        Ref<EntityStore> npcRef = state.npcEntity;
        Model original = originalModels.remove(playerRef.getUuid());

        if (npcRef == null || !npcRef.isValid() || original == null) {
            if (announce) {
                playerRef.sendMessage(
                        com.hypixel.hytale.server.core.Message.raw(
                                "[Griddify] Wild Shape not active.").color("#FFA500"));
            }
            return;
        }

        final Model finalModel = original;
        world.execute(() -> {
            try {
                var store = world.getEntityStore().getStore();
                store.replaceComponent(npcRef, ModelComponent.getComponentType(),
                        new ModelComponent(finalModel));
                store.replaceComponent(npcRef, PersistentModel.getComponentType(),
                        new PersistentModel(finalModel.toReference()));
                store.replaceComponent(npcRef, BoundingBox.getComponentType(),
                        new BoundingBox(finalModel.getBoundingBox()));
            } catch (Exception e) {
                System.err.println("[Griddify] [WILDSHAPE] Revert failed: " + e.getMessage());
            }
        });

        // Also clear the casting state wild shape flag
        SpellCastingState cs = state.getSpellCastingState();
        if (cs != null) cs.setWildShapeActive(false, null);

        if (announce) {
            playerRef.sendMessage(
                    com.hypixel.hytale.server.core.Message.raw(
                            "[Griddify] Wild Shape ended — reverted to original form.").color("#90EE90"));
        }
    }

    public boolean isTransformed(UUID playerUUID) {
        return originalModels.containsKey(playerUUID);
    }

    @Nullable
    private static Model loadModel(String assetId) {
        try {
            ModelAsset asset = ModelAsset.getAssetMap().getAsset(assetId);
            if (asset == null) return null;
            return Model.createScaledModel(asset, 1.0f);
        } catch (Exception e) {
            System.err.println("[Griddify] [WILDSHAPE] Load failed '" + assetId + "': " + e.getMessage());
            return null;
        }
    }
}