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
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.gridifymydungeon.plugin.gridmove.commands.GridMoveCommand;
import com.gridifymydungeon.plugin.dnd.PlayerEntityController;

/**
 * Manages NPC-Player links for grid-based movement.

 */
public class GridMoveManager {

    private final Map<UUID, GridPlayerState> playerStates = new ConcurrentHashMap<>();

    /** Whether fog-of-war is currently active (toggled by GM via /FogOfWar). */
    private volatile boolean fogOfWarActive = false;

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
        return (Set<Map.Entry<UUID, GridPlayerState>>) (Set<?>) playerStates.entrySet();
    }

    /** Direction holograms removed — not needed. */
    public void spawnDirectionHolograms(World world, GridPlayerState state) {}

    /** Direction holograms removed — not needed. */
    public void moveDirectionHolograms(World world, GridPlayerState state) {}

    /** Direction holograms removed — not needed. */
    public void clearDirectionHolograms(World world, GridPlayerState state) {}

    /** Direction holograms removed — not needed. */
    public void clearAllHologramReferences() {}

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