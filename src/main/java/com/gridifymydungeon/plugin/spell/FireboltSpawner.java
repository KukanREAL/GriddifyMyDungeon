package com.gridifymydungeon.plugin.spell;

import com.hypixel.hytale.component.AddReason;
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
import java.util.Set;

/**
 * FireballSpawner — spawns animated spell-effect entities and plays their
 * looping animation immediately on spawn.
 *
 * FIX APPLIED:
 *   AnimationSlot was previously hardcoded as AnimationSlot.Movement in the
 *   playAnimation() call even though the class constant correctly said Action.
 *   Movement gets overridden every tick by Hytale's internal movement system
 *   (idle resets it to nothing since the entity has no velocity), so the
 *   animation was wiped out the moment the next movement tick ran.
 *   AnimationSlot.Action bypasses that guard and persists correctly.
 *
 * FIX 2:
 *   The ComponentAccessor was cast from the Store<EntityStore> obtained BEFORE
 *   addEntity() was called. After addEntity() the store may be a different
 *   tracked instance. We now call world.getEntityStore().getStore() AFTER the
 *   entity is registered so the accessor reflects the live tracked state.
 */
public class FireboltSpawner {

    // -----------------------------------------------------------------------
    // CONSTANTS
    // -----------------------------------------------------------------------

    /**
     * The AnimationSets key from Fire_Bolt.json.
     * Fire_Bolt.json has "AnimationSets": { "1": { ... } } so the ID is "1".
     */
    private static final String ANIMATION_SET_ID = "1";

    /**
     * FIX: Must be AnimationSlot.Action, NOT Movement.
     *
     * Movement is reset every server tick by the entity movement system.
     * Since our fireball entity has no velocity component, the movement system
     * resets it to idle / empty every tick, wiping the animation immediately.
     * Action slot is unmanaged by the movement system and persists until we
     * explicitly clear it.
     */
    private static final AnimationSlot ANIMATION_SLOT = AnimationSlot.Action;

    /** Cached model — loaded once on first spawn, reused for all subsequent spawns. */
    private static Model cachedModel = null;

    // -----------------------------------------------------------------------
    // PUBLIC API
    // -----------------------------------------------------------------------

    /**
     * Spawns the Fireball2 model at world-space position (x, y, z) and
     * immediately starts its looping animation in the Action slot.
     *
     * MUST be called inside world.execute().
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

        // Grab the store reference before building the holder
        Store<EntityStore> store = world.getEntityStore().getStore();
        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();

        // --- Components ---
        holder.addComponent(
                TransformComponent.getComponentType(),
                new TransformComponent(new Vector3d(x, y, z), new Vector3f(0f, 0f, 0f))
        );
        holder.addComponent(
                PersistentModel.getComponentType(),
                new PersistentModel(model.toReference())
        );
        holder.addComponent(
                ModelComponent.getComponentType(),
                new ModelComponent(model)
        );
        holder.addComponent(
                BoundingBox.getComponentType(),
                new BoundingBox(model.getBoundingBox())
        );
        holder.addComponent(
                NetworkId.getComponentType(),
                new NetworkId(store.getExternalData().takeNextNetworkId())
        );
        holder.ensureComponent(UUIDComponent.getComponentType());
        // ActiveAnimationComponent MUST be present before playAnimation is called.
        // The entity tracker reads it each tick and pushes updates to clients.
        holder.ensureComponent(ActiveAnimationComponent.getComponentType());

        // Register the entity into the world
        Ref<EntityStore> ref = store.addEntity(holder, AddReason.SPAWN);

        // FIX 2: Obtain a fresh accessor AFTER addEntity() so it reflects the
        // live tracked state, not the pre-registration snapshot.
        Store<EntityStore> liveStore = world.getEntityStore().getStore();

        // FIX 1: Use ANIMATION_SLOT (Action), not Movement.
        // This writes the animation ID into ActiveAnimationComponent and marks
        // it network-outdated so the tracker sends ComponentUpdate to all
        // visible clients on the next tick.
        AnimationUtils.playAnimation(
                ref,
                ANIMATION_SLOT,        // <-- Action (was Movement — the bug)
                ANIMATION_SET_ID,      // "1"
                true,                  // loop
                liveStore              // fresh post-registration accessor
        );

        System.out.println("[Griddify] [FIREBALL] Spawned at ("
                + x + ", " + y + ", " + z + ")"
                + " | AnimationSets: " + model.getAnimationSetMap().keySet()
                + " | Slot: " + ANIMATION_SLOT);

        return ref;
    }

    /**
     * Spawns an animated fireball at the centre of a grid cell.
     * Coordinate system matches Grid_Basic overlays: each cell is 2x2 world units.
     *
     * MUST be called inside world.execute().
     */
    @Nullable
    public static Ref<EntityStore> spawnAtGrid(World world, int gridX, int gridZ, float groundY) {
        float cx = (gridX * 2.0f) + 1.0f;
        float cz = (gridZ * 2.0f) + 1.0f;
        float cy = groundY + 0.5f; // float slightly above the grid tile
        return spawnAndAnimate(world, cx, cy, cz);
    }

    /**
     * Spawns an animated fireball at every cell in the given set.
     * Returns all Refs so the caller can despawn them later.
     *
     * MUST be called inside world.execute().
     */
    public static List<Ref<EntityStore>> spawnAtCells(
            World world,
            Set<SpellPatternCalculator.GridCell> cells,
            float groundY) {

        List<Ref<EntityStore>> refs = new ArrayList<>();
        for (SpellPatternCalculator.GridCell cell : cells) {
            Ref<EntityStore> ref = spawnAtGrid(world, cell.x, cell.z, groundY);
            if (ref != null) refs.add(ref);
        }
        return refs;
    }

    /**
     * Removes a single fireball entity from the world.
     * MUST be called inside world.execute().
     */
    public static void despawn(World world, @Nullable Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) return;
        try {
            world.getEntityStore().getStore().removeEntity(ref, RemoveReason.REMOVE);
        } catch (Exception e) {
            System.err.println("[Griddify] [FIREBALL] Failed to despawn entity: " + e.getMessage());
        }
    }

    /**
     * Removes all fireball entities in the given list and clears it.
     * MUST be called inside world.execute().
     */
    public static void despawnAll(World world, List<Ref<EntityStore>> refs) {
        for (Ref<EntityStore> ref : refs) {
            despawn(world, ref);
        }
        refs.clear();
    }

    // -----------------------------------------------------------------------
    // INTERNAL
    // -----------------------------------------------------------------------

    /**
     * Loads (or returns the cached) Fire_Bolt model.
     *
     * CRITICAL: createScaledModel() is used WITHOUT a staticModel / noAnimations flag.
     * A static model strips out AnimationSets, which means the client never receives
     * them and AnimationUtils.playAnimation() silently fails its containsKey() check.
     */
    private static Model getModel() {
        if (cachedModel != null) return cachedModel;
        try {
            ModelAsset asset = ModelAsset.getAssetMap().getAsset("Fire_Bolt");
            if (asset == null) {
                System.err.println("[Griddify] [FIREBOLT] ModelAsset 'Fire_Bolt' not found in registry. "
                        + "Ensure the asset is registered under that exact key.");
                return null;
            }
            // scale=1.0f, no noAnimations flag — keeps AnimationSets intact
            cachedModel = Model.createScaledModel(asset, 1.0f);
            System.out.println("[Griddify] [FIREBOLT] Model loaded. AnimationSets: "
                    + cachedModel.getAnimationSetMap().keySet());
            return cachedModel;
        } catch (Exception e) {
            System.err.println("[Griddify] [FIREBOLT] Exception loading model: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}