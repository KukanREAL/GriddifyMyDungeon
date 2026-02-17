package com.gridifymydungeon.plugin.gridmove;

import com.gridifymydungeon.plugin.dnd.commands.MonsterEntityController;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

public class GridOverlayManager {

    private static final String MODEL_ASSET_ID = "Grid_Corner_Flat";
    private static final String FALLBACK_MODEL = "Debug";

    private static Model cachedModel = null;
    private static boolean modelLoadAttempted = false;

    private static final int MAX_OVERLAY_CELLS = 150;

    private static final float MAX_HEIGHT_UP = 3.0f;
    private static final float MAX_HEIGHT_DOWN = 4.0f;

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

    private static final int[][] NEIGHBORS = {
            { 0, -1},  { 1,  0},  { 0,  1},  {-1,  0},   // cardinal
            { 1, -1},  { 1,  1},  {-1,  1},  {-1, -1},   // diagonal
    };

    public static boolean spawnGridOverlay(World world, GridPlayerState state,
                                           CollisionDetector collisionDetector, UUID excludePlayer) {
        try {
            Model model = getGridModel();
            if (model == null) {
                return false;
            }

            List<ReachableCell> reachableCells = floodFillReachable(
                    world, state, collisionDetector, excludePlayer);

            if (reachableCells.isEmpty()) {
                System.out.println("[GridMove] [GRID] No reachable cells found");
                state.gridOverlayEnabled = true;
                return true;
            }

            Store<EntityStore> store = world.getEntityStore().getStore();

            for (ReachableCell cell : reachableCells) {
                float centerX = (cell.gridX * 2.0f) + 1.0f;
                float centerZ = (cell.gridZ * 2.0f) + 1.0f;
                float y = cell.groundY + 0.01f;

                for (int i = 0; i < 4; i++) {
                    float entityX = centerX + OFFSETS[i][0];
                    float entityZ = centerZ + OFFSETS[i][1];

                    Ref<EntityStore> ref = spawnQuarter(store, model,
                            entityX, y, entityZ, ROTATIONS[i]);
                    state.gridOverlay.add(ref);
                }
            }

            state.gridOverlayEnabled = true;
            System.out.println("[GridMove] [GRID] Spawned overlay: " + reachableCells.size() +
                    " cells (" + state.gridOverlay.size() + " entities)");
            return true;

        } catch (Exception e) {
            System.err.println("[GridMove] [GRID] Failed to spawn overlay: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static void refreshGridOverlay(World world, GridPlayerState state,
                                          CollisionDetector collisionDetector, UUID excludePlayer) {
        if (!state.gridOverlayEnabled) {
            return;
        }
        removeGridOverlayEntities(world, state);
        spawnGridOverlay(world, state, collisionDetector, excludePlayer);
    }

    public static void removeGridOverlay(World world, GridPlayerState state) {
        removeGridOverlayEntities(world, state);
        state.gridOverlayEnabled = false;
        System.out.println("[GridMove] [GRID] Removed overlay");
    }

    private static void removeGridOverlayEntities(World world, GridPlayerState state) {
        Store<EntityStore> store = world.getEntityStore().getStore();

        for (Ref<EntityStore> ref : state.gridOverlay) {
            if (ref != null && ref.isValid()) {
                try {
                    store.removeEntity(ref, RemoveReason.REMOVE);
                } catch (Exception e) {

                }
            }
        }

        state.gridOverlay.clear();
    }

    private static List<ReachableCell> floodFillReachable(World world, GridPlayerState state,
                                                          CollisionDetector collisionDetector,
                                                          UUID excludePlayer) {
        List<ReachableCell> result = new ArrayList<>();
        Queue<BfsNode> queue = new LinkedList<>();
        Map<Long, Double> bestMoves = new HashMap<>();
        Map<Long, Float> groundYCache = new HashMap<>();

        float startGroundY = state.npcY;
        long startKey = packKey(state.currentGridX, state.currentGridZ);

        queue.add(new BfsNode(state.currentGridX, state.currentGridZ,
                state.remainingMoves, startGroundY));
        bestMoves.put(startKey, state.remainingMoves);
        groundYCache.put(startKey, startGroundY);

        while (!queue.isEmpty() && result.size() < MAX_OVERLAY_CELLS) {
            BfsNode current = queue.poll();

            for (int[] neighbor : NEIGHBORS) {
                int nx = current.gridX + neighbor[0];
                int nz = current.gridZ + neighbor[1];
                boolean diagonal = (neighbor[0] != 0 && neighbor[1] != 0);
                double cost = diagonal ? 1.5 : 1.0;

                double movesAfter = current.movesLeft - cost;
                if (movesAfter < 0) {
                    continue;
                }

                long nKey = packKey(nx, nz);

                Double previousBest = bestMoves.get(nKey);
                if (previousBest != null && previousBest >= movesAfter) {
                    continue;
                }

                if (collisionDetector != null &&
                        collisionDetector.isPositionOccupied(nx, nz, -1, excludePlayer)) {
                    continue;
                }

                Float neighborGroundY = groundYCache.get(nKey);
                if (neighborGroundY == null) {

                    neighborGroundY = MonsterEntityController.scanForGroundPublic(
                            world, nx, nz, current.groundY + 6.0f);

                    if (neighborGroundY == null) {

                        neighborGroundY = MonsterEntityController.scanForGroundPublic(
                                world, nx, nz, state.npcY + 6.0f);
                    }

                    if (neighborGroundY == null) {
                        continue;
                    }
                    groundYCache.put(nKey, neighborGroundY);
                }

                float heightDiff = neighborGroundY - current.groundY;

                if (heightDiff >= MAX_HEIGHT_UP || heightDiff < -MAX_HEIGHT_DOWN) {
                    continue;
                }

                bestMoves.put(nKey, movesAfter);

                if (nx != state.currentGridX || nz != state.currentGridZ) {
                    if (previousBest == null) {
                        result.add(new ReachableCell(nx, nz, neighborGroundY));
                    }
                }

                queue.add(new BfsNode(nx, nz, movesAfter, neighborGroundY));
            }
        }

        return result;
    }

    private static Model getGridModel() {
        if (cachedModel != null) {
            return cachedModel;
        }

        if (modelLoadAttempted) {
            return null;
        }

        modelLoadAttempted = true;

        try {
            System.out.println("[GridMove] [GRID] === MODEL LOADING ===");

            System.out.println("[GridMove] [GRID] Available Model assets in registry:");
            try {
                Map<String, ModelAsset> allModels = ModelAsset.getAssetMap().getAssetMap();
                System.out.println("[GridMove] [GRID] Total models: " + allModels.size());

                int count = 0;
                for (String key : allModels.keySet()) {
                    System.out.println("[GridMove] [GRID]   - " + key);
                    count++;
                    if (count >= 20) {
                        System.out.println("[GridMove] [GRID]   ... and " + (allModels.size() - 20) + " more");
                        break;
                    }
                }
            } catch (Exception e) {
                System.out.println("[GridMove] [GRID] Could not list models: " + e.getMessage());
            }

            System.out.println("[GridMove] [GRID] Attempting to load Model asset: " + MODEL_ASSET_ID);

            ModelAsset modelAsset = ModelAsset.getAssetMap().getAsset(MODEL_ASSET_ID);
            if (modelAsset != null) {
                cachedModel = Model.createScaledModel(modelAsset, 1.0f);
                System.out.println("[GridMove] [GRID] SUCCESS! Loaded Grid_Corner_Flat model");
                return cachedModel;
            }

            System.out.println("[GridMove] [GRID] Model asset '" + MODEL_ASSET_ID + "' not found in registry");
        } catch (Exception e) {
            System.err.println("[GridMove] [GRID] Error: " + e.getMessage());
            e.printStackTrace();
        }

        try {
            ModelAsset fallback = ModelAsset.getAssetMap().getAsset(FALLBACK_MODEL);
            if (fallback != null) {
                cachedModel = Model.createScaledModel(fallback, 1.0f);
                System.out.println("[GridMove] [GRID] Using Debug fallback (RGB cube)");
                return cachedModel;
            }
        } catch (Exception e) {
            System.err.println("[GridMove] [GRID] Debug fallback failed: " + e.getMessage());
        }

        System.err.println("[GridMove] [GRID] All loading failed");
        return null;
    }

    private static Ref<EntityStore> spawnQuarter(Store<EntityStore> store, Model model,
                                                 float x, float y, float z, float yawRad) {
        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();

        Vector3d position = new Vector3d(x, y, z);
        Vector3f rotation = new Vector3f(0, yawRad, 0);

        holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(position, rotation));
        holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
        holder.addComponent(BoundingBox.getComponentType(), new BoundingBox(model.getBoundingBox()));
        holder.addComponent(NetworkId.getComponentType(),
                new NetworkId(store.getExternalData().takeNextNetworkId()));
        holder.ensureComponent(UUIDComponent.getComponentType());

        return store.addEntity(holder, com.hypixel.hytale.component.AddReason.SPAWN);
    }

    private static long packKey(int gridX, int gridZ) {
        return ((long) gridX << 32) | (gridZ & 0xFFFFFFFFL);
    }

    private static class BfsNode {
        final int gridX, gridZ;
        final double movesLeft;
        final float groundY;

        BfsNode(int gridX, int gridZ, double movesLeft, float groundY) {
            this.gridX = gridX;
            this.gridZ = gridZ;
            this.movesLeft = movesLeft;
            this.groundY = groundY;
        }
    }

    private static class ReachableCell {
        final int gridX, gridZ;
        final float groundY;

        ReachableCell(int gridX, int gridZ, float groundY) {
            this.gridX = gridX;
            this.gridZ = gridZ;
            this.groundY = groundY;
        }
    }
}