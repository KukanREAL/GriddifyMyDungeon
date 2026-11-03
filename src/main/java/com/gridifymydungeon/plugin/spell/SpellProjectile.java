package com.gridifymydungeon.plugin.spell;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.packets.world.SpawnParticleSystem;
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
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Generic projectile engine for all SINGLE_TARGET and CONE spell visuals.
 *
 * Call launch() once per target cell. The caller decides how many times to call it
 * (1 for Fire_Bolt, 1–3 staggered for Magic Missile, 9 simultaneous for Burning Hands).
 *
 * entityScale from ProjectileType controls model size; arrivalParticleScale is always full-size.
 */
public class SpellProjectile {

    private static final float MUZZLE_VELOCITY = 12.0f;
    private static final float GRAVITY         = 0.0f;
    private static final long  TICK_RATE_MS    = 50L;
    private static final long  SAFETY_TTL_MS   = 8000L;

    // One cached model per ProjectileType (scale baked in at load)
    private static final Map<ProjectileType, Model> modelCache = new EnumMap<>(ProjectileType.class);

    // -----------------------------------------------------------------------
    // PUBLIC API
    // -----------------------------------------------------------------------

    /**
     * Spawn + fly a single projectile toward targetPos.
     * Must be called inside world.execute().
     */
    @Nullable
    public static Ref<EntityStore> launch(ProjectileType type,
                                          World world,
                                          Vector3d origin,
                                          Vector3d targetPos,
                                          List<PlayerRef> players) {
        Model model = getModel(type);
        if (model == null) return null;

        float dx  = (float)(targetPos.x - origin.x);
        float dy  = (float)(targetPos.y - origin.y);
        float dz  = (float)(targetPos.z - origin.z);
        float len = (float) Math.sqrt(dx*dx + dy*dy + dz*dz);
        if (len < 0.0001f) return null;

        Vector3f dir = new Vector3f(dx/len, dy/len, dz/len);
        float yaw    = -(float) Math.atan2(dx, -dz);
        float pitch  = (float) Math.asin(-dy / len);

        Store<EntityStore> store = world.getEntityStore().getStore();
        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
        holder.addComponent(TransformComponent.getComponentType(),
                new TransformComponent(origin, new Vector3f(pitch, yaw, 0f)));
        holder.addComponent(PersistentModel.getComponentType(), new PersistentModel(model.toReference()));
        holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
        holder.addComponent(BoundingBox.getComponentType(), new BoundingBox(model.getBoundingBox()));
        holder.addComponent(NetworkId.getComponentType(),
                new NetworkId(store.getExternalData().takeNextNetworkId()));
        holder.ensureComponent(UUIDComponent.getComponentType());
        holder.ensureComponent(ActiveAnimationComponent.getComponentType());

        Ref<EntityStore> ref = store.addEntity(holder, AddReason.SPAWN);

        if (type.animSetId != null) {
            AnimationUtils.playAnimation(ref, AnimationSlot.Action, type.animSetId, true,
                    world.getEntityStore().getStore());
        }

        startPhysics(type, world, ref, origin,
                dir.x * MUZZLE_VELOCITY,
                dir.y * MUZZLE_VELOCITY,
                dir.z * MUZZLE_VELOCITY,
                targetPos, players);
        return ref;
    }

    public static void despawn(World world, @Nullable Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) return;
        try { world.getEntityStore().getStore().removeEntity(ref, RemoveReason.REMOVE); }
        catch (Exception e) { System.err.println("[Griddify] [PROJ] despawn: " + e.getMessage()); }
    }

    // -----------------------------------------------------------------------
    // PHYSICS
    // -----------------------------------------------------------------------

    private static void startPhysics(ProjectileType type, World world, Ref<EntityStore> ref,
                                     Vector3d start, float vx, float vy, float vz,
                                     Vector3d targetPos, List<PlayerRef> players) {
        Thread t = new Thread(() -> {
            double x = start.x, y = start.y, z = start.z;
            double dx = vx * (TICK_RATE_MS / 1000.0);
            double dy = vy * (TICK_RATE_MS / 1000.0);
            double dz = vz * (TICK_RATE_MS / 1000.0);
            double gravPerTick = GRAVITY * (TICK_RATE_MS / 1000.0);

            double targetDist = Math.sqrt(
                    Math.pow(targetPos.x - start.x, 2) + Math.pow(targetPos.z - start.z, 2));
            double travelled = 0;
            long startTime = System.currentTimeMillis();

            while (true) {
                try { Thread.sleep(TICK_RATE_MS); } catch (InterruptedException e) { break; }

                if (System.currentTimeMillis() - startTime >= SAFETY_TTL_MS || !ref.isValid()) {
                    world.execute(() -> despawn(world, ref));
                    break;
                }

                dy -= gravPerTick;
                x += dx; y += dy; z += dz;
                travelled += Math.sqrt(dx*dx + dz*dz);

                if (travelled >= targetDist) {
                    final double fx = targetPos.x, fy = y, fz = targetPos.z;
                    world.execute(() -> {
                        if (!ref.isValid()) return;
                        try {
                            TransformComponent tc = world.getEntityStore().getStore()
                                    .getComponent(ref, TransformComponent.getComponentType());
                            if (tc != null) tc.setPosition(new Vector3d(fx, fy, fz));
                        } catch (Exception ignored) {}
                    });
                    try { Thread.sleep(80); } catch (InterruptedException ignored) {}
                    if (type.arrivalParticle != null) sendParticle(type, fx, fy, fz, players);
                    world.execute(() -> despawn(world, ref));
                    break;
                }

                double speed = Math.sqrt(dx*dx + dy*dy + dz*dz);
                float np = speed > 0.0001 ? (float) Math.asin(-dy / speed) : 0f;
                float nw = -(float) Math.atan2(dx, -dz);
                final double fx = x, fy = y, fz = z;
                final float fp = np, fw = nw;
                world.execute(() -> {
                    if (!ref.isValid()) return;
                    try {
                        TransformComponent tc = world.getEntityStore().getStore()
                                .getComponent(ref, TransformComponent.getComponentType());
                        if (tc != null) { tc.setPosition(new Vector3d(fx, fy, fz)); tc.setRotation(new Vector3f(fp, fw, 0f)); }
                    } catch (Exception ignored) {}
                });
            }
        });
        t.setDaemon(true);
        t.setName("griddify-proj-" + type.name().toLowerCase());
        t.start();
    }

    private static void sendParticle(ProjectileType type, double x, double y, double z, List<PlayerRef> players) {
        SpawnParticleSystem pkt = new SpawnParticleSystem();
        pkt.particleSystemId = type.arrivalParticle;
        pkt.position = new Position(x, y, z);
        pkt.rotation = new Direction(0f, 0f, 0f);
        pkt.scale    = type.arrivalParticleScale;
        pkt.color    = null;
        for (PlayerRef p : players) {
            try { p.getPacketHandler().writeNoCache(pkt); } catch (Exception ignored) {}
        }
    }

    // -----------------------------------------------------------------------
    // MODEL CACHE  — one model per ProjectileType, scale baked into the model
    // -----------------------------------------------------------------------

    @Nullable
    private static Model getModel(ProjectileType type) {
        Model cached = modelCache.get(type);
        if (cached != null) return cached;
        try {
            ModelAsset asset = ModelAsset.getAssetMap().getAsset(type.modelAssetId);
            if (asset == null) {
                System.err.println("[Griddify] [PROJ] ModelAsset '" + type.modelAssetId + "' not found for " + type);
                return null;
            }
            Model model = Model.createScaledModel(asset, type.entityScale);
            modelCache.put(type, model);
            System.out.println("[Griddify] [PROJ] Loaded '" + type.modelAssetId
                    + "' scale=" + type.entityScale + " for " + type
                    + " | AnimSets=" + model.getAnimationSetMap().keySet());
            return model;
        } catch (Exception e) {
            System.err.println("[Griddify] [PROJ] Load failed for " + type + ": " + e.getMessage());
            return null;
        }
    }
}