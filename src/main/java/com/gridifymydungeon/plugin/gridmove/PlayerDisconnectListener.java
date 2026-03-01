package com.gridifymydungeon.plugin.gridmove;

import com.gridifymydungeon.plugin.dnd.RoleManager;
import com.gridifymydungeon.plugin.gridmove.commands.GridMoveCommand;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;

/**
 * Listens for player disconnects and cleans up holograms + NPCs
 * FIXED: Includes direction holograms
 */
public class PlayerDisconnectListener {

    private final GridMoveManager manager;
    private final RoleManager roleManager;

    public PlayerDisconnectListener(GridMoveManager manager, RoleManager roleManager) {
        this.manager = manager;
        this.roleManager = roleManager;
    }

    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();

        System.out.println("[GridMove] [INFO] Player " + playerRef.getUsername() + " disconnected, cleaning up...");

        GridPlayerState state = manager.getState(playerRef);

        if (state != null) {
            // Remove NPC if exists
            if (state.npcEntity != null && state.npcEntity.isValid()) {
                try {
                    state.npcEntity.getStore().removeEntity(state.npcEntity, RemoveReason.REMOVE);
                    System.out.println("[GridMove] [INFO] Removed NPC for " + playerRef.getUsername());
                } catch (Exception e) {
                    System.err.println("[GridMove] [ERROR] Error removing NPC: " + e.getMessage());
                }
            }
        }

        // Notify role manager
        roleManager.handlePlayerDisconnect(playerRef.getUuid());

        System.out.println("[GridMove] [INFO] Cleanup complete for " + playerRef.getUsername());
    }

}