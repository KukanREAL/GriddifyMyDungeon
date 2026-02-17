package com.gridifymydungeon.plugin.gridmove;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.gridifymydungeon.plugin.gridmove.commands.GridMoveCommand;

/**
 * Manages NPC-Player links for grid-based movement
 * FIXED v4: Store PlayerRef in state for easy access by commands
 */
public class GridMoveManager {

    private final Map<UUID, GridPlayerState> playerStates = new ConcurrentHashMap<>();

    /**
     * Get or create state for a player
     * UPDATED: Now stores PlayerRef in the state for later retrieval
     */
    public GridPlayerState getState(PlayerRef playerRef) {
        GridPlayerState state = playerStates.computeIfAbsent(playerRef.getUuid(), k -> new GridPlayerState());
        // Always update the PlayerRef reference (in case it changed or this is first access)
        state.playerRef = playerRef;
        return state;
    }

    /**
     * Get PlayerRef for a given GridPlayerState
     * Used by LevelUp/LevelDown commands to notify players
     * ADDED: Relies on PlayerRef being stored in GridPlayerState
     */
    public PlayerRef getPlayerRefByState(GridPlayerState state) {
        // Simply return the stored PlayerRef (will be null if state was created outside of getState())
        return state.playerRef;
    }

    /**
     * Get all state entries with UUIDs (for CollisionDetector to check player positions)
     */
    public Set<Map.Entry<UUID, GridPlayerState>> getStateEntries() {
        return playerStates.entrySet();
    }

    public void spawnDirectionHolograms(World world, GridPlayerState state) {
        if (state.npcEntity == null || !state.npcEntity.isValid()) {
            return;
        }

        try {
            float centerX = (state.currentGridX * 2.0f) + 1.0f;
            float centerZ = (state.currentGridZ * 2.0f) + 1.0f;
            float y = state.npcY + 3.0f;

            state.northHologram = spawnDirectionHologram(world, centerX, y, centerZ - 2.0f, "W");
            state.southHologram = spawnDirectionHologram(world, centerX, y, centerZ + 2.0f, "S");
            state.eastHologram = spawnDirectionHologram(world, centerX + 2.0f, y, centerZ, "D");
            state.westHologram = spawnDirectionHologram(world, centerX - 2.0f, y, centerZ, "A");

            float diagDist = 2.0f;
            state.northEastHologram = spawnDirectionHologram(world, centerX + diagDist, y, centerZ - diagDist, "WD");
            state.northWestHologram = spawnDirectionHologram(world, centerX - diagDist, y, centerZ - diagDist, "WA");
            state.southEastHologram = spawnDirectionHologram(world, centerX + diagDist, y, centerZ + diagDist, "SD");
            state.southWestHologram = spawnDirectionHologram(world, centerX - diagDist, y, centerZ + diagDist, "SA");

        } catch (Exception e) {
            System.err.println("[GridMove] [ERROR] Failed to spawn holograms: " + e.getMessage());
        }
    }

    private Ref<EntityStore> spawnDirectionHologram(World world, float x, float y, float z, String text) {
        try {
            Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
            ProjectileComponent projectileComponent = new ProjectileComponent("Projectile");

            holder.putComponent(ProjectileComponent.getComponentType(), projectileComponent);
            holder.putComponent(TransformComponent.getComponentType(),
                    new TransformComponent(new Vector3d(x, y, z), new Vector3f(0, 0, 0)));
            holder.ensureComponent(UUIDComponent.getComponentType());

            if (projectileComponent.getProjectile() == null) {
                projectileComponent.initialize();
            }

            Store<EntityStore> store = world.getEntityStore().getStore();
            holder.addComponent(NetworkId.getComponentType(),
                    new NetworkId(store.getExternalData().takeNextNetworkId()));

            holder.addComponent(Nameplate.getComponentType(), new Nameplate(text));

            Ref<EntityStore> hologramRef = store.addEntity(holder, com.hypixel.hytale.component.AddReason.SPAWN);

            GridMoveCommand.ALL_HOLOGRAMS.add(hologramRef);

            return hologramRef;

        } catch (Exception e) {
            System.err.println("[GridMove] [ERROR] Failed to spawn hologram: " + e.getMessage());
            return null;
        }
    }

    /**
     * Move existing holograms to new position (no destroy+recreate)
     */
    public void moveDirectionHolograms(World world, GridPlayerState state) {
        float centerX = (state.currentGridX * 2.0f) + 1.0f;
        float centerZ = (state.currentGridZ * 2.0f) + 1.0f;
        float y = state.npcY + 3.0f;
        float diagDist = 2.0f;

        Store<EntityStore> store = world.getEntityStore().getStore();

        moveHologram(store, state.northHologram, centerX, y, centerZ - 2.0f);
        moveHologram(store, state.southHologram, centerX, y, centerZ + 2.0f);
        moveHologram(store, state.eastHologram, centerX + 2.0f, y, centerZ);
        moveHologram(store, state.westHologram, centerX - 2.0f, y, centerZ);
        moveHologram(store, state.northEastHologram, centerX + diagDist, y, centerZ - diagDist);
        moveHologram(store, state.northWestHologram, centerX - diagDist, y, centerZ - diagDist);
        moveHologram(store, state.southEastHologram, centerX + diagDist, y, centerZ + diagDist);
        moveHologram(store, state.southWestHologram, centerX - diagDist, y, centerZ + diagDist);
    }

    private void moveHologram(Store<EntityStore> store, Ref<EntityStore> hologramRef, float x, float y, float z) {
        if (hologramRef == null || !hologramRef.isValid()) {
            return;
        }
        try {
            TransformComponent transform = store.getComponent(hologramRef, TransformComponent.getComponentType());
            if (transform != null) {
                transform.setPosition(new Vector3d(x, y, z));
            }
        } catch (Exception e) {
            // Silently ignore
        }
    }

    /**
     * Fallback: destroy + recreate if move fails
     */
    public void updateDirectionHolograms(World world, GridPlayerState state) {
        boolean allValid = state.northHologram != null && state.northHologram.isValid()
                && state.southHologram != null && state.southHologram.isValid()
                && state.eastHologram != null && state.eastHologram.isValid()
                && state.westHologram != null && state.westHologram.isValid();

        if (allValid) {
            moveDirectionHolograms(world, state);
        } else {
            clearDirectionHolograms(world, state);
            spawnDirectionHolograms(world, state);
        }
    }

    public void clearDirectionHolograms(World world, GridPlayerState state) {
        try {
            removeHologram(world, state.northHologram);
            removeHologram(world, state.southHologram);
            removeHologram(world, state.eastHologram);
            removeHologram(world, state.westHologram);
            removeHologram(world, state.northEastHologram);
            removeHologram(world, state.northWestHologram);
            removeHologram(world, state.southEastHologram);
            removeHologram(world, state.southWestHologram);

            state.northHologram = null;
            state.southHologram = null;
            state.eastHologram = null;
            state.westHologram = null;
            state.northEastHologram = null;
            state.northWestHologram = null;
            state.southEastHologram = null;
            state.southWestHologram = null;
        } catch (Exception e) {
            System.err.println("[GridMove] [ERROR] Failed to clear holograms: " + e.getMessage());
        }
    }

    public void clearAllHologramReferences() {
        for (GridPlayerState state : playerStates.values()) {
            state.northHologram = null;
            state.southHologram = null;
            state.eastHologram = null;
            state.westHologram = null;
            state.northEastHologram = null;
            state.northWestHologram = null;
            state.southEastHologram = null;
            state.southWestHologram = null;
        }
    }

    private void removeHologram(World world, Ref<EntityStore> hologramRef) {
        if (hologramRef != null && hologramRef.isValid()) {
            try {
                world.getEntityStore().getStore().removeEntity(hologramRef, RemoveReason.REMOVE);
                GridMoveCommand.ALL_HOLOGRAMS.remove(hologramRef);
            } catch (Exception e) {
                // Silently ignore
            }
        }
    }

    public void removePlayer(UUID playerUuid) {
        playerStates.remove(playerUuid);
    }

    public void cleanup() {
        playerStates.clear();
    }

    public Iterable<GridPlayerState> getAllStates() {
        return playerStates.values();
    }
}