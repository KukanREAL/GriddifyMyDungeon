package com.gridifymydungeon.plugin.gridmove;

import com.gridifymydungeon.plugin.dnd.GMPositionTracker;
import com.gridifymydungeon.plugin.dnd.RoleManager;
import com.gridifymydungeon.plugin.gridmove.packet.PlayerPositionTracker;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.packets.player.ClientMovement;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Handles ClientMovement packet to track player grid movement
 * FINAL: Gets World from EntityStore.getWorld()
 */
public class ClientMovementHandler {

    private final PlayerPositionTracker playerTracker;
    private final GMPositionTracker gmTracker;
    private final RoleManager roleManager;
    private final GridMoveManager gridMoveManager;

    public ClientMovementHandler(PlayerPositionTracker playerTracker, GMPositionTracker gmTracker,
                                 RoleManager roleManager, GridMoveManager gridMoveManager) {
        this.playerTracker = playerTracker;
        this.gmTracker = gmTracker;
        this.roleManager = roleManager;
        this.gridMoveManager = gridMoveManager;
    }

    /**
     * Called when ClientMovement packet is received
     * World is obtained from the EntityStore via the player's reference
     */
    public void handleMovement(PlayerRef playerRef, ClientMovement packet) {
        // Get new position from packet
        if (packet.absolutePosition == null) {
            return; // No position data
        }

        Vector3d newPosition = new Vector3d(
                packet.absolutePosition.x,
                packet.absolutePosition.y,
                packet.absolutePosition.z
        );

        // Get world from EntityStore
        // PlayerRef is in EntityStore, which knows its World
        if (playerRef.getReference() == null) {
            return; // Player not in world yet
        }

        EntityStore entityStore = playerRef.getReference().getStore().getExternalData();
        World world = entityStore.getWorld();

        // Delegate to appropriate tracker
        if (roleManager.isGM(playerRef)) {
            // GM movement - check if controlling monster
            gmTracker.onGMMove(playerRef, world, newPosition);
        } else {
            // Player movement - check if grid movement active
            GridPlayerState state = gridMoveManager.getState(playerRef);
            if (state != null && state.npcEntity != null && state.npcEntity.isValid()) {
                playerTracker.onPlayerMove(playerRef, world, newPosition);
            }
        }
    }
}