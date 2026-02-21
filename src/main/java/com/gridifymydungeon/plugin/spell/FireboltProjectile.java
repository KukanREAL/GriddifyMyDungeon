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
import java.util.List;

/**
 * Fire_BoltProjectile — animated Fire Bolt projectile.
 * Flies straight at constant size; spawns Explosion_Big particle on arrival.
 */
public class FireboltProjectile {

    private static final String        ANIMATION_SET_ID   = "1";
    private static final AnimationSlot ANIM_SLOT          = AnimationSlot.Action;
    private static final String        MODEL_ASSET_ID     = "Fire_Bolt";
    private static final String        EXPLOSION_PARTICLE = "Explosion_Big";
    private static final float         EXPLOSION_SCALE    = 0.5f;

    private static final float MUZZLE_VELOCITY = 12.0f;
    private static final float GRAVITY         = 0.0f;
    private static final long  TICK_RATE_MS    = 50L;
    private static final long  SAFETY_TTL_MS   = 8000L;

    private static Model   fullModel    = null;
    private static boolean modelsLoaded = false;

    // -----------------------------------------------------------------------
    // PUBLIC API
    // -----------------------------------------------------------------------

    @Nullable
    public static Ref<EntityStore> launch(World world, Vector3d originPos,
                                          Vector3f direction, float spawnYaw, float spawnPitch,
                                          @Nullable Vector3d targetPos,
                                          List<PlayerRef> players) {
        ensureModels();
        if (fullModel == null) {
            System.err.println("[Griddify] [FIRE_BOLT] launch() — model unavailable.");
            return null;
        }

        final float vx = direction.x * MUZZLE_VELOCITY;
        final float vy = direction.y * MUZZLE_VELOCITY;
        final float vz = direction.z * MUZZLE_VELOCITY;

        Store<EntityStore> store = world.getEntityStore().getStore();
        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();

        holder.addComponent(TransformComponent.getComponentType(),
                new TransformComponent(originPos, new Vector3f(spawnPitch, spawnYaw, 0f)));
        holder.addComponent(PersistentModel.getComponentType(),
                new PersistentModel(fullModel.toReference()));
        holder.addComponent(ModelComponent.getComponentType(),
                new ModelComponent(fullModel));
        holder.addComponent(BoundingBox.getComponentType(),
                new BoundingBox(fullModel.getBoundingBox()));
        holder.addComponent(NetworkId.getComponentType(),
                new NetworkId(store.getExternalData().takeNextNetworkId()));
        holder.ensureComponent(UUIDComponent.getComponentType());
        holder.ensureComponent(ActiveAnimationComponent.getComponentType());

        Ref<EntityStore> ref = store.addEntity(holder, AddReason.SPAWN);
        AnimationUtils.playAnimation(ref, ANIM_SLOT, ANIMATION_SET_ID, true,
                world.getEntityStore().getStore());

        System.out.println("[Griddify] [FIRE_BOLT] Launched from " + originPos
                + " vel=(" + vx + "," + vy + "," + vz + ")"
                + " yaw=" + spawnYaw + " pitch=" + spawnPitch
                + " target=" + (targetPos != null ? targetPos : "none"));

        startPhysicsThread(world, ref, originPos, vx, vy, vz, targetPos, players);
        return ref;
    }

    public static void despawn(World world, @Nullable Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) return;
        try {
            world.getEntityStore().getStore().removeEntity(ref, RemoveReason.REMOVE);
            System.out.println("[Griddify] [FIRE_BOLT] Projectile despawned.");
        } catch (Exception e) {
            System.err.println("[Griddify] [FIRE_BOLT] Despawn failed: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // PHYSICS
    // -----------------------------------------------------------------------

    private static void startPhysicsThread(World world, Ref<EntityStore> ref,
                                           Vector3d startPos,
                                           float vx, float vy, float vz,
                                           @Nullable Vector3d targetPos,
                                           List<PlayerRef> players) {
        Thread t = new Thread(() -> {
            double x  = startPos.x, y = startPos.y, z = startPos.z;
            double dx = vx * (TICK_RATE_MS / 1000.0);
            double dy = vy * (TICK_RATE_MS / 1000.0);
            double dz = vz * (TICK_RATE_MS / 1000.0);
            double gravityPerTick = GRAVITY * (TICK_RATE_MS / 1000.0);

            final double targetDistXZ = targetPos != null
                    ? Math.sqrt(Math.pow(targetPos.x - startPos.x, 2) +
                    Math.pow(targetPos.z - startPos.z, 2))
                    : Double.MAX_VALUE;

            double travelledXZ = 0.0;
            long   startTime   = System.currentTimeMillis();

            while (true) {
                try { Thread.sleep(TICK_RATE_MS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }

                if (System.currentTimeMillis() - startTime >= SAFETY_TTL_MS) {
                    System.out.println("[Griddify] [FIRE_BOLT] Safety TTL — despawning.");
                    world.execute(() -> despawn(world, ref));
                    break;
                }
                if (!ref.isValid()) break;

                dy -= gravityPerTick;
                x  += dx;
                y  += dy;
                z  += dz;
                travelledXZ += Math.sqrt(dx * dx + dz * dz);

                // ── Arrival ───────────────────────────────────────────────
                if (travelledXZ >= targetDistXZ) {
                    final double fx = targetPos.x, fy = y, fz = targetPos.z;

                    // Snap to target, then explode, then despawn
                    world.execute(() -> {
                        if (!ref.isValid()) return;
                        try {
                            Store<EntityStore> s = world.getEntityStore().getStore();
                            TransformComponent transform = s.getComponent(
                                    ref, TransformComponent.getComponentType());
                            if (transform != null) transform.setPosition(new Vector3d(fx, fy, fz));
                        } catch (Exception ignored) {}
                    });

                    try { Thread.sleep(80L); } catch (InterruptedException ignored) {}

                    // Spawn explosion particle for all players
                    spawnExplosion(fx, fy, fz, players);

                    world.execute(() -> despawn(world, ref));
                    System.out.println("[Griddify] [FIRE_BOLT] Arrived at (" + fx + "," + fy + "," + fz + ") — explosion spawned.");
                    break;
                }

                // ── Normal tick — update position + rotation ───────────────
                double speed    = Math.sqrt(dx * dx + dy * dy + dz * dz);
                float  newPitch = speed > 0.0001 ? (float) Math.asin(-dy / speed) : 0f;
                float  newYaw   = -(float) Math.atan2(dx, -dz);

                final double fx = x, fy = y, fz = z;
                final float  fp = newPitch, fw = newYaw;

                world.execute(() -> {
                    if (!ref.isValid()) return;
                    try {
                        Store<EntityStore> s = world.getEntityStore().getStore();
                        TransformComponent transform = s.getComponent(
                                ref, TransformComponent.getComponentType());
                        if (transform != null) {
                            transform.setPosition(new Vector3d(fx, fy, fz));
                            transform.setRotation(new Vector3f(fp, fw, 0f));
                        }
                    } catch (Exception e) {
                        System.err.println("[Griddify] [FIRE_BOLT] Physics tick failed: " + e.getMessage());
                    }
                });
            }
        });
        t.setDaemon(true);
        t.setName("griddify-fire-bolt-physics");
        t.start();
    }

    // -----------------------------------------------------------------------
    // EXPLOSION
    // -----------------------------------------------------------------------

    private static void spawnExplosion(double x, double y, double z, List<PlayerRef> players) {
        SpawnParticleSystem packet = new SpawnParticleSystem();
        packet.particleSystemId = EXPLOSION_PARTICLE;
        packet.position = new Position(x, y, z);
        packet.rotation = new Direction(0f, 0f, 0f); // no specific rotation needed
        packet.scale = EXPLOSION_SCALE;
        packet.color = null; // use default particle colors

        for (PlayerRef player : players) {
            try {
                player.getPacketHandler().writeNoCache(packet);
            } catch (Exception e) {
                System.err.println("[Griddify] [FIRE_BOLT] Failed to send explosion to "
                        + player.getUsername() + ": " + e.getMessage());
            }
        }

        System.out.println("[Griddify] [FIRE_BOLT] Explosion_Big sent to " + players.size() + " players.");
    }

    // -----------------------------------------------------------------------
    // MODEL LOADING
    // -----------------------------------------------------------------------

    private static void ensureModels() {
        if (modelsLoaded) return;
        modelsLoaded = true;
        try {
            ModelAsset asset = ModelAsset.getAssetMap().getAsset(MODEL_ASSET_ID);
            if (asset == null) {
                System.err.println("[Griddify] [FIRE_BOLT] ModelAsset '" + MODEL_ASSET_ID + "' not found.");
                return;
            }
            fullModel = Model.createScaledModel(asset, 1.0f);
            System.out.println("[Griddify] [FIRE_BOLT] Model loaded. AnimationSets: "
                    + fullModel.getAnimationSetMap().keySet());
        } catch (Exception e) {
            System.err.println("[Griddify] [FIRE_BOLT] Failed to load model: " + e.getMessage());
            e.printStackTrace();
        }
    }
}