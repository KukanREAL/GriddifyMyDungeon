package com.gridifymydungeon.plugin.spell;

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
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Stationary / animated spell visual effects — NOT projectiles.
 *
 * Handles:
 *   spawnStationary  – spawn one entity at a fixed position (Moonbeam, Sunbeam)
 *   spawnGrowing     – entity grows from scale 0→target over durationMs (Entangle per cell)
 *   spawnWave        – sweeping row-by-row wave of entities then despawn (Thunderwave)
 *   spawnWithTimeout – spawn then auto-despawn after ms (Fireball lingering Explosion)
 */
public class SpellVisualEffect {

    private static final ScheduledExecutorService SCHED =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "griddify-vfx");
                t.setDaemon(true);
                return t;
            });

    private static final Map<String, Model> modelCache = new java.util.HashMap<>();

    // ── PUBLIC API ────────────────────────────────────────────────────────────

    /**
     * Spawn a single stationary entity at world pos with given yaw.
     * Returns the Ref so the caller can despawn it later (or after a delay).
     * Must be called inside world.execute().
     */
    @Nullable
    public static Ref<EntityStore> spawnStationary(String modelAssetId, float entityScale,
                                                   World world,
                                                   double wx, double wy, double wz,
                                                   float yaw) {
        Model model = getModel(modelAssetId, entityScale);
        if (model == null) return null;
        return spawnEntity(model, world, wx, wy, wz, 0f, yaw, 0f);
    }

    /**
     * Spawn an entity that grows from scale 0 → entityScale over growMs milliseconds.
     * After growing it stays permanently (caller is responsible for despawning).
     * Returns the Ref. Must be called inside world.execute().
     */
    @Nullable
    public static Ref<EntityStore> spawnGrowing(String modelAssetId, float entityScale,
                                                World world,
                                                double wx, double wy, double wz,
                                                long growMs) {
        // Spawn at scale 0 first (tiny), then animate scale up
        Model model = getModel(modelAssetId, 0.001f); // near-zero initial scale
        if (model == null) return null;
        Ref<EntityStore> ref = spawnEntity(model, world, wx, wy, wz, 0f, 0f, 0f);
        if (ref == null) return null;

        // Animate scale from 0 → entityScale over growMs by swapping the model
        Model fullModel = getModel(modelAssetId, entityScale);
        if (fullModel == null) return ref;
        SCHED.schedule(() -> world.execute(() -> {
            if (!ref.isValid()) return;
            try {
                Store<EntityStore> store = world.getEntityStore().getStore();
                store.replaceComponent(ref, ModelComponent.getComponentType(), new ModelComponent(fullModel));
                store.replaceComponent(ref, PersistentModel.getComponentType(),
                        new PersistentModel(fullModel.toReference()));
                store.replaceComponent(ref, BoundingBox.getComponentType(),
                        new BoundingBox(fullModel.getBoundingBox()));
            } catch (Exception ignored) {}
        }), growMs, TimeUnit.MILLISECONDS);
        return ref;
    }

    /**
     * Spawn a stationary entity that auto-despawns after lifetimeMs.
     * Must be called inside world.execute().
     */
    @Nullable
    public static Ref<EntityStore> spawnWithTimeout(String modelAssetId, float entityScale,
                                                    World world,
                                                    double wx, double wy, double wz,
                                                    float yaw, long lifetimeMs) {
        Ref<EntityStore> ref = spawnStationary(modelAssetId, entityScale, world, wx, wy, wz, yaw);
        if (ref != null) {
            SCHED.schedule(() -> world.execute(() -> despawn(world, ref)),
                    lifetimeMs, TimeUnit.MILLISECONDS);
        }
        return ref;
    }

    /**
     * Thunderwave: 3 directional sweeps of 6 entities each, moving from one edge
     * of the spell area to the other.
     *
     * The spell pattern is sorted by distance from caster; we treat the sorted order
     * as the "near → far" axis.  Each of the 3 waves is a full row of 6 entities that
     * appears, travels one step forward, then the next wave follows, etc.
     *
     * Animation: each entity in a wave spawns, lingers 180 ms, then despawns so the
     * visual illusion of movement crosses the spell area in ~540 ms per wave.
     * Three complete passes run back-to-back (waves 2 and 3 start after the previous
     * wave has finished).
     */
    /**
     * Thunderwave wave: 3 sweeps of columns, 2 entities per cell offset by ±0.5 perpendicular
     * to the cast direction. Excludes the caster cell.
     */
    public static void spawnWave(String modelAssetId, float entityScale,
                                 World world,
                                 int casterGridX, int casterGridZ, float npcY,
                                 Set<SpellPatternCalculator.GridCell> cells,
                                 List<PlayerRef> ignoredPlayers) {
        spawnWave(modelAssetId, entityScale, world, casterGridX, casterGridZ, npcY, cells, ignoredPlayers, 0f);
    }

    public static void spawnWave(String modelAssetId, float entityScale,
                                 World world,
                                 int casterGridX, int casterGridZ, float npcY,
                                 Set<SpellPatternCalculator.GridCell> cells,
                                 List<PlayerRef> ignoredPlayers,
                                 float yawRad) {
        if (cells.isEmpty()) return;

        // Exclude caster cell
        Set<SpellPatternCalculator.GridCell> filteredCells = new java.util.HashSet<>();
        for (SpellPatternCalculator.GridCell c : cells) {
            if (c.x != casterGridX || c.z != casterGridZ) filteredCells.add(c);
        }
        if (filteredCells.isEmpty()) return;

        // Perpendicular offset (±0.5) along the axis perpendicular to cast direction
        float perpX = (float) Math.sin(yawRad + Math.PI / 2.0);
        float perpZ = (float) Math.cos(yawRad + Math.PI / 2.0);

        // Group cells by Chebyshev distance from caster — each distance ring is one "column"
        java.util.TreeMap<Integer, List<SpellPatternCalculator.GridCell>> byDist = new java.util.TreeMap<>();
        for (SpellPatternCalculator.GridCell c : filteredCells) {
            int d = SpellPatternCalculator.getDistance(casterGridX, casterGridZ, c.x, c.z);
            byDist.computeIfAbsent(d, k -> new ArrayList<>()).add(c);
        }
        List<List<SpellPatternCalculator.GridCell>> columns = new ArrayList<>(byDist.values());
        if (columns.isEmpty()) return;

        int numCols     = columns.size();
        long colStepMs  = 150L;
        long waveLinger = 180L;
        long sweepDur   = numCols * colStepMs + waveLinger;
        int  numSweeps  = 3;

        final float fpX = perpX, fpZ = perpZ;

        for (int sweep = 0; sweep < numSweeps; sweep++) {
            long sweepStart = sweep * (sweepDur + 60L);

            for (int ci = 0; ci < numCols; ci++) {
                final List<SpellPatternCalculator.GridCell> col = columns.get(ci);
                final long showAt   = sweepStart + ci * colStepMs;
                final long hideAt   = showAt + waveLinger;

                SCHED.schedule(() -> world.execute(() -> {
                    List<Ref<EntityStore>> colRefs = new ArrayList<>();
                    for (SpellPatternCalculator.GridCell c : col) {
                        float wx = (c.x * 2.0f) + 1.0f;
                        float wz = (c.z * 2.0f) + 1.0f;
                        Float groundY = SpellVisualManager.scanForGround(world, c.x, c.z, npcY + 30f, 45);
                        float wy = (groundY != null ? groundY : npcY) + 0.5f;
                        // 2 entities per cell, offset ±0.5 perpendicular to cast direction
                        for (float sign : new float[]{-0.5f, 0.5f}) {
                            Ref<EntityStore> ref = spawnStationary(modelAssetId, entityScale, world,
                                    wx + fpX * sign, wy, wz + fpZ * sign, yawRad);
                            if (ref != null) colRefs.add(ref);
                        }
                    }
                    SCHED.schedule(() -> world.execute(() -> {
                        for (Ref<EntityStore> r : colRefs) despawn(world, r);
                    }), waveLinger, TimeUnit.MILLISECONDS);
                }), showAt, TimeUnit.MILLISECONDS);
            }
        }
    }

    /**
     * Entangle: spawn 4 Entangle entities per grid cell in the affected area,
     * offset at the 4 block corners (+/-0.5, +/-0.5) within the 2×2 grid cell.
     * Each grows from scale 0→entityScale over growMs.
     * Returns a list of all spawned refs so they can be tracked for despawn.
     */
    public static List<Ref<EntityStore>> spawnEntangle(World world,
                                                       Set<SpellPatternCalculator.GridCell> cells,
                                                       float npcY) {
        List<Ref<EntityStore>> refs = new java.util.concurrent.CopyOnWriteArrayList<>();
        float[] offsets = {-0.5f, 0.5f};
        for (SpellPatternCalculator.GridCell c : cells) {
            Float groundY = SpellVisualManager.scanForGround(world, c.x, c.z, npcY + 30f, 45);
            float wy = groundY != null ? groundY : npcY;
            float cx = (c.x * 2.0f) + 1.0f;
            float cz = (c.z * 2.0f) + 1.0f;
            for (float ox : offsets) {
                for (float oz : offsets) {
                    final float fx = cx + ox, fy = wy, fz = cz + oz;
                    Ref<EntityStore> ref = spawnGrowing("Entangle", 0.7f, world, fx, fy, fz, 400L);
                    if (ref != null) refs.add(ref);
                }
            }
        }
        return refs;
    }


    /**
     * Ice Storm: Frost_Bolt entities fall from 5 world-units above ground into random cells
     * for 5 seconds at 5-10 bolts/second.
     */
    public static void spawnIceStorm(World world,
                                     Set<SpellPatternCalculator.GridCell> cells,
                                     float npcY, List<PlayerRef> players) {
        if (cells.isEmpty()) return;
        List<SpellPatternCalculator.GridCell> cellList = new ArrayList<>(cells);
        java.util.Random rng = new java.util.Random();
        long durationMs = 5000L;
        long startTime = System.currentTimeMillis();

        Thread t = new Thread(() -> {
            while (System.currentTimeMillis() - startTime < durationMs) {
                long sleepMs = 100 + rng.nextInt(100); // 100–200ms = 5–10/sec
                try { Thread.sleep(sleepMs); } catch (InterruptedException e) { break; }

                SpellPatternCalculator.GridCell cell = cellList.get(rng.nextInt(cellList.size()));
                float wx = (cell.x * 2.0f) + 1.0f + (rng.nextFloat() - 0.5f) * 1.2f;
                float wz = (cell.z * 2.0f) + 1.0f + (rng.nextFloat() - 0.5f) * 1.2f;
                Float groundY = SpellVisualManager.scanForGround(world, cell.x, cell.z, npcY + 30f, 45);
                float gy = groundY != null ? groundY : npcY;
                float startY = gy + 10f;
                float endY   = gy + 0.3f;
                final float fx = wx, fz = wz, fStartY = startY, fEndY = endY;
                world.execute(() -> launchFallingBolt(world, fx, fStartY, fz, fEndY, players));
            }
        });
        t.setDaemon(true);
        t.setName("griddify-icestorm");
        t.start();
    }

    private static void launchFallingBolt(World world,
                                          float wx, float startY, float wz, float endY,
                                          List<PlayerRef> players) {
        Model model = getModel("Frost_Bolt", ProjectileType.ICE_STORM_BOLT.entityScale);
        if (model == null) return;
        // pitch = PI/2 so bolt faces downward
        Ref<EntityStore> ref = spawnEntity(model, world, wx, startY, wz,
                (float)(Math.PI / 2), 0f, 0f);
        if (ref == null) return;

        float fallDist = startY - endY;
        long tickMs = 50L;
        long steps = Math.max(1, (long)(fallDist / 8.0f * 1000 / tickMs));
        float dyPerTick = -fallDist / steps;

        Thread ft = new Thread(() -> {
            double y = startY;
            for (int i = 0; i < steps; i++) {
                try { Thread.sleep(tickMs); } catch (InterruptedException e) { break; }
                if (!ref.isValid()) return;
                y += dyPerTick;
                final double fy = y;
                world.execute(() -> {
                    if (!ref.isValid()) return;
                    try {
                        TransformComponent tc = world.getEntityStore().getStore().getComponent(
                                ref, TransformComponent.getComponentType());
                        if (tc != null) tc.setPosition(new Vector3d(wx, fy, wz));
                    } catch (Exception ignored) {}
                });
            }
            world.execute(() -> despawn(world, ref));
        });
        ft.setDaemon(true);
        ft.setName("griddify-bolt-fall");
        ft.start();
    }


    /**
     * Heal visuals for SINGLE-TARGET heals:
     * Spawn Heal_One at each healed creature's world position for 3 seconds.
     */
    public static void spawnHealOne(World world, float wx, float wy, float wz) {
        spawnWithTimeout("Heal_One", 1.0f, world, wx, wy, wz, 0f, 3000L);
    }

    /**
     * Heal visuals for AREA heals:
     * Spawn Heal_Circle under caster + Heal_One at every affected creature position.
     */
    public static void spawnHealArea(World world,
                                     float casterWx, float casterWy, float casterWz,
                                     java.util.List<float[]> targetPositions) {
        // Big circle under the caster
        spawnWithTimeout("Heal_Circle", 1.5f, world, casterWx, casterWy, casterWz, 0f, 3000L);
        // Individual Heal_One per affected target
        for (float[] pos : targetPositions) {
            spawnWithTimeout("Heal_One", 1.0f, world, pos[0], pos[1], pos[2], 0f, 3000L);
        }
    }


    /**
     * Mark: spawn a Mark entity flat on the ground at the given grid cell for lifetimeMs.
     * Used for buff spells (Bardic Inspiration, Hex, Bless, etc.) and teleport destinations.
     */
    public static void spawnMark(World world, int gridX, int gridZ, float npcY, long lifetimeMs) {
        Float groundY = SpellVisualManager.scanForGround(world, gridX, gridZ, npcY + 30f, 45);
        float wy = groundY != null ? groundY + 0.05f : npcY + 0.05f;
        float wx = (gridX * 2.0f) + 1.0f;
        float wz = (gridZ * 2.0f) + 1.0f;
        spawnWithTimeout("Mark", 1.0f, world, wx, wy, wz, 0f, lifetimeMs);
    }
    public static void despawn(World world, @Nullable Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) return;
        try { world.getEntityStore().getStore().removeEntity(ref, RemoveReason.REMOVE); }
        catch (Exception ignored) {}
    }

    // ── INTERNALS ────────────────────────────────────────────────────────────

    @Nullable
    private static Ref<EntityStore> spawnEntity(Model model, World world,
                                                double wx, double wy, double wz,
                                                float pitch, float yaw, float roll) {
        try {
            Store<EntityStore> store = world.getEntityStore().getStore();
            Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
            holder.addComponent(TransformComponent.getComponentType(),
                    new TransformComponent(new Vector3d(wx, wy, wz), new Vector3f(pitch, yaw, roll)));
            holder.addComponent(PersistentModel.getComponentType(), new PersistentModel(model.toReference()));
            holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
            holder.addComponent(BoundingBox.getComponentType(), new BoundingBox(model.getBoundingBox()));
            holder.addComponent(NetworkId.getComponentType(),
                    new NetworkId(store.getExternalData().takeNextNetworkId()));
            holder.ensureComponent(UUIDComponent.getComponentType());
            return store.addEntity(holder, AddReason.SPAWN);
        } catch (Exception e) {
            System.err.println("[Griddify] [VFX] spawnEntity failed: " + e.getMessage());
            return null;
        }
    }

    @Nullable
    private static Model getModel(String assetId, float scale) {
        String key = assetId + "@" + scale;
        Model cached = modelCache.get(key);
        if (cached != null) return cached;
        try {
            ModelAsset asset = ModelAsset.getAssetMap().getAsset(assetId);
            if (asset == null) {
                System.err.println("[Griddify] [VFX] ModelAsset '" + assetId + "' not found");
                return null;
            }
            Model model = Model.createScaledModel(asset, scale);
            modelCache.put(key, model);
            System.out.println("[Griddify] [VFX] Loaded '" + assetId + "' scale=" + scale);
            return model;
        } catch (Exception e) {
            System.err.println("[Griddify] [VFX] Load failed '" + assetId + "': " + e.getMessage());
            return null;
        }
    }
}