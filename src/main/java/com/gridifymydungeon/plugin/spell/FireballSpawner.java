package com.gridifymydungeon.plugin.spell;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.ActiveAnimationComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * FireballSpawner — spawns animated spell-effect entities (fireball, etc.)
 * and plays their looping animation immediately on spawn.
 *
 * KEY: The model must be loaded WITHOUT the staticModel flag so that
 * Hytale sends its AnimationSets to the client. Then AnimationUtils.playAnimation()
 * sends a PlayAnimation packet (id 162) using the JSON AnimationSets key as the
 * animationId (e.g. "1" for the first set in your Fireball2.json).
 *
 * Usage:
 *   world.execute(() -> {
 *       Ref<EntityStore> ref = FireballSpawner.spawnAndAnimate(world, x, y, z);
 *       // later when the effect is done:
 *       FireballSpawner.despawn(world, ref);
 *   });
 */
public class FireballSpawner {

    /**
     * The AnimationSets key from Fireball2.json.
     * Your JSON has "AnimationSets": { "1": { ... } }, so the ID is "1".
     */
    private static final String ANIMATION_SET_ID = "1";

    /**
     * Action slot bypasses Hytale's containsKey() guard entirely —
     * it sends the PlayAnimation packet unconditionally, which is what
     * we need for a custom non-NPC entity with a custom animation set.
     */
    private static final AnimationSlot ANIMATION_SLOT = AnimationSlot.Action;

    /** Cached model — loaded once, reused for all spawns. */
    private static Model cachedModel = null;

    // -----------------------------------------------------------------------
    // PUBLIC API
    // -----------------------------------------------------------------------

    /**
     * Spawns the fireball model at world position (x, y, z), then immediately
     * sends a PlayAnimation packet to all players who can see it so that the
     * Fireball2.blockyanim plays in a loop.
     *
     * Must be called inside world.execute().
     *
     * @return The entity Ref, or null if the model failed to load.
     */
    @Nullable
    public static Ref<EntityStore> spawnAndAnimate(World world, float x, float y, float z) {
        Model model = getModel();
        if (model == null) {
            System.err.println("[Griddify] [FIREBALL] Could not load Fireball2 model — check asset name");
            return null;
        }

        Store<EntityStore> store = world.getEntityStore().getStore();
        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();

        holder.addComponent(TransformComponent.getComponentType(),
                new TransformComponent(new Vector3d(x, y, z), new Vector3f(0, 0, 0)));
        holder.addComponent(PersistentModel.getComponentType(), new PersistentModel(model.toReference()));
        holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
        holder.addComponent(BoundingBox.getComponentType(), new BoundingBox(model.getBoundingBox()));
        holder.addComponent(NetworkId.getComponentType(),
                new NetworkId(store.getExternalData().takeNextNetworkId()));
        holder.ensureComponent(UUIDComponent.getComponentType());
        // ActiveAnimationComponent must be present before playAnimation is called.
        // The entity tracker reads it each tick and pushes updates to clients.
        holder.ensureComponent(ActiveAnimationComponent.getComponentType());

        Ref<EntityStore> ref = store.addEntity(holder, com.hypixel.hytale.component.AddReason.SPAWN);

        com.hypixel.hytale.component.ComponentAccessor<EntityStore> accessor =
                (com.hypixel.hytale.component.ComponentAccessor<EntityStore>) store;

        // Now that ActiveAnimationComponent exists on the entity, playAnimation writes
        // the animation ID into the component and marks it network-outdated, which causes
        // the tracker to send a ComponentUpdate(ActiveAnimations) to all visible clients.
        AnimationUtils.playAnimation(ref, AnimationSlot.Movement, ANIMATION_SET_ID, true, accessor);

        System.out.println("[Griddify] [FIREBALL] Spawned at (" + x + ", " + y + ", " + z + ")"
                + " | AnimationSets: " + model.getAnimationSetMap().keySet());
        return ref;
    }

    /**
     * Spawns animated fireball at a grid cell's world-space centre (same
     * coordinate system as Grid_Basic overlays).
     *
     * Must be called inside world.execute().
     */
    @Nullable
    public static Ref<EntityStore> spawnAtGrid(World world, int gridX, int gridZ, float groundY) {
        float cx = (gridX * 2.0f) + 1.0f;
        float cz = (gridZ * 2.0f) + 1.0f;
        float cy = groundY + 0.5f;   // float slightly above the grid tile
        return spawnAndAnimate(world, cx, cy, cz);
    }

    /**
     * Spawns an animated fireball at every cell in the given set, returning
     * all refs so the caller can despawn them later.
     *
     * Must be called inside world.execute().
     */
    public static List<Ref<EntityStore>> spawnAtCells(
            World world,
            java.util.Set<SpellPatternCalculator.GridCell> cells,
            float groundY) {
        List<Ref<EntityStore>> refs = new ArrayList<>();
        for (SpellPatternCalculator.GridCell cell : cells) {
            Ref<EntityStore> ref = spawnAtGrid(world, cell.x, cell.z, groundY);
            if (ref != null) refs.add(ref);
        }
        return refs;
    }

    /**
     * Removes a single fireball entity.
     * Must be called inside world.execute().
     */
    public static void despawn(World world, @Nullable Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) return;
        try {
            world.getEntityStore().getStore().removeEntity(ref, RemoveReason.REMOVE);
        } catch (Exception e) {
            System.err.println("[Griddify] [FIREBALL] Failed to despawn: " + e.getMessage());
        }
    }

    /**
     * Removes a list of fireball entities (e.g. those returned by spawnAtCells).
     * Must be called inside world.execute().
     */
    public static void despawnAll(World world, List<Ref<EntityStore>> refs) {
        for (Ref<EntityStore> ref : refs) despawn(world, ref);
        refs.clear();
    }

    // -----------------------------------------------------------------------
    // INTERNAL
    // -----------------------------------------------------------------------

    /**
     * Loads the Fireball2 model from the asset registry.
     *
     * CRITICAL: we use Model.createScaledModel() WITHOUT the staticModel=true flag.
     * A static model strips out the AnimationSets, which means the client never
     * receives them and playAnimation() silently fails the containsKey() check.
     */
    private static Model getModel() {
        if (cachedModel != null) return cachedModel;
        try {
            ModelAsset asset = ModelAsset.getAssetMap().getAsset("Fireball2");
            if (asset == null) {
                System.err.println("[Griddify] [FIREBALL] ModelAsset 'Fireball2' not found in registry");
                return null;
            }
            // createScaledModel(asset, scale) — keeps AnimationSets intact.
            // Do NOT use a static-model path or Model.createScaledModel(asset, scale, noAnimations=true).
            cachedModel = Model.createScaledModel(asset, 1.0f);
            System.out.println("[Griddify] [FIREBALL] Model loaded. AnimationSets: "
                    + cachedModel.getAnimationSetMap().keySet());
            return cachedModel;
        } catch (Exception e) {
            System.err.println("[Griddify] [FIREBALL] Failed to load model: " + e.getMessage());
            return null;
        }
    }
}