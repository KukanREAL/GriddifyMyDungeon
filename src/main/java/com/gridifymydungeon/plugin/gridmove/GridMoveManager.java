package com.gridifymydungeon.plugin.gridmove;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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
import com.gridifymydungeon.plugin.dnd.PlayerEntityController;

/**
 * Manages NPC-Player links for grid-based movement.
 *
 * FIX #7: Direction holograms (WASD indicators) now spawn at y=-30, teleport to
 *         real Y, then hide from non-owners after 150ms. Only the owning player
 *         can see the WASD direction indicators above their own NPC.
 */
public class GridMoveManager {

    private final Map<UUID, GridPlayerState> playerStates = new ConcurrentHashMap<>();

    /** Whether fog-of-war is currently active (toggled by GM via /FogOfWar). */
    private volatile boolean fogOfWarActive = false;

    // FIX #7: Scheduler for hiding holograms from non-owners
    private static final ScheduledExecutorService HOLOGRAM_SCHED =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "hologram-hider");
                t.setDaemon(true);
                return t;
            });

    public boolean isFogOfWarActive() { return fogOfWarActive; }
    public void setFogOfWarActive(boolean v) { fogOfWarActive = v; }

    /**
     * Get or create state for a player.
     */
    public GridPlayerState getState(PlayerRef playerRef) {
        GridPlayerState state = playerStates.computeIfAbsent(playerRef.getUuid(), k -> new GridPlayerState());
        state.playerRef = playerRef;
        return state;
    }

    public PlayerRef getPlayerRefByState(GridPlayerState state) {
        return state.playerRef;
    }

    public Set<Map.Entry<UUID, GridPlayerState>> getStateEntries() {
        return playerStates.entrySet();
    }

    /**
     * FIX #7: Spawn WASD holograms private to the owning player.
     * Each hologram spawns at y=-30 (below world, no broadcast), teleports to real Y,
     * then gets hidden from all non-owners after 150ms.
     */
    public void spawnDirectionHolograms(World world, GridPlayerState state) {
        if (state.npcEntity == null || !state.npcEntity.isValid()) return;

        try {
            float centerX = (state.currentGridX * 2.0f) + 1.0f;
            float centerZ = (state.currentGridZ * 2.0f) + 1.0f;
            float y = state.npcY + 3.0f;
            float realY = y; // store real Y for teleport
            float spawnY = -30f; // spawn below world

            PlayerRef owner = state.playerRef;

            state.northHologram    = spawnDirectionHologram(world, centerX, spawnY, realY, centerZ - 2.0f, "W",  owner);
            state.southHologram    = spawnDirectionHologram(world, centerX, spawnY, realY, centerZ + 2.0f, "S",  owner);
            state.eastHologram     = spawnDirectionHologram(world, centerX + 2.0f, spawnY, realY, centerZ, "D",  owner);
            state.westHologram     = spawnDirectionHologram(world, centerX - 2.0f, spawnY, realY, centerZ, "A",  owner);

            float diagDist = 2.0f;
            state.northEastHologram = spawnDirectionHologram(world, centerX + diagDist, spawnY, realY, centerZ - diagDist, "WD", owner);
            state.northWestHologram = spawnDirectionHologram(world, centerX - diagDist, spawnY, realY, centerZ - diagDist, "WA", owner);
            state.southEastHologram = spawnDirectionHologram(world, centerX + diagDist, spawnY, realY, centerZ + diagDist, "SD", owner);
            state.southWestHologram = spawnDirectionHologram(world, centerX - diagDist, spawnY, realY, centerZ + diagDist, "SA", owner);

        } catch (Exception e) {
            System.err.println("[GridMove] [ERROR] Failed to spawn holograms: " + e.getMessage());
        }
    }

    /**
     * FIX #7: Spawn hologram at spawnY (y=-30), teleport to realY immediately,
     * schedule hideEntityFromOthers() after 150ms so only the owner sees it.
     */
    private Ref<EntityStore> spawnDirectionHologram(World world,
                                                    float x, float spawnY, float realY, float z,
                                                    String text, PlayerRef owner) {
        try {
            Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
            ProjectileComponent projectileComponent = new ProjectileComponent("Projectile");

            holder.putComponent(ProjectileComponent.getComponentType(), projectileComponent);
            holder.putComponent(TransformComponent.getComponentType(),
                    new TransformComponent(new Vector3d(x, spawnY, z), new Vector3f(0, 0, 0)));
            holder.ensureComponent(UUIDComponent.getComponentType());

            if (projectileComponent.getProjectile() == null) {
                projectileComponent.initialize();
            }

            Store<EntityStore> store = world.getEntityStore().getStore();
            int netId = store.getExternalData().takeNextNetworkId();
            holder.addComponent(NetworkId.getComponentType(), new NetworkId(netId));
            holder.addComponent(Nameplate.getComponentType(), new Nameplate(text));

            Ref<EntityStore> hologramRef = store.addEntity(holder, com.hypixel.hytale.component.AddReason.SPAWN);

            if (hologramRef == null) return null;

            // Teleport to real Y immediately
            try {
                TransformComponent tc = store.getComponent(hologramRef, TransformComponent.getComponentType());
                if (tc != null) tc.setPosition(new Vector3d(x, realY, z));
            } catch (Exception ignored) {}

            GridMoveCommand.ALL_HOLOGRAMS.add(hologramRef);

            // FIX #7: hide from non-owners after entity-tracker broadcasts
            if (owner != null) {
                final Ref<EntityStore> finalRef = hologramRef;
                final int finalNetId = netId;
                final PlayerRef finalOwner = owner;
                HOLOGRAM_SCHED.schedule(() -> world.execute(() ->
                        PlayerEntityController.hideEntityFromOthers(world, finalRef, finalOwner, finalNetId)
                ), 150L, TimeUnit.MILLISECONDS);
            }

            return hologramRef;

        } catch (Exception e) {
            System.err.println("[GridMove] [ERROR] Failed to spawn hologram: " + e.getMessage());
            return null;
        }
    }

    /**
     * Move existing holograms to new position (no destroy+recreate).
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

        // FIX #7: re-hide from non-owners after each move so newly-in-range players don't see them
        if (state.playerRef != null) {
            hideAllHologramsFromOthers(world, state, state.playerRef);
        }
    }

    private void moveHologram(Store<EntityStore> store, Ref<EntityStore> hologramRef, float x, float y, float z) {
        if (hologramRef == null || !hologramRef.isValid()) return;
        try {
            TransformComponent transform = store.getComponent(hologramRef, TransformComponent.getComponentType());
            if (transform != null) {
                transform.setPosition(new Vector3d(x, y, z));
            }
        } catch (Exception ignored) {}
    }

    /**
     * FIX #7: After moving holograms, re-send hide packets to any player newly in range.
     */
    private void hideAllHologramsFromOthers(World world, GridPlayerState state, PlayerRef owner) {
        Ref<EntityStore>[] refs = new Ref[]{
                state.northHologram, state.southHologram, state.eastHologram, state.westHologram,
                state.northEastHologram, state.northWestHologram, state.southEastHologram, state.southWestHologram
        };
        Store<EntityStore> store = world.getEntityStore().getStore();
        for (Ref<EntityStore> ref : refs) {
            if (ref == null || !ref.isValid()) continue;
            try {
                NetworkId netIdComp = store.getComponent(ref, NetworkId.getComponentType());
                if (netIdComp == null) continue;
                PlayerEntityController.hideEntityFromOthers(world, ref, owner, netIdComp.getId());
            } catch (Exception ignored) {}
        }
    }

    /**
     * Fallback: destroy + recreate if move fails.
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
            } catch (Exception ignored) {}
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

    /** Get all PlayerRefs currently in the game (for broadcasting messages). */
    public java.util.List<com.hypixel.hytale.server.core.universe.PlayerRef> getAllPlayerRefs() {
        java.util.List<com.hypixel.hytale.server.core.universe.PlayerRef> refs = new java.util.ArrayList<>();
        for (GridPlayerState state : playerStates.values()) {
            if (state.playerRef != null) refs.add(state.playerRef);
        }
        return refs;
    }
}