package com.gridifymydungeon.plugin.dnd;

import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages GM and Player roles for D&D system
 * - Only 1 GM allowed per server
 * - Players numbered from bottom (Player 1, Player 2, etc.)
 * - Roles can be revoked with /GridNull
 * - All roles can be reset with /GridRestart
 */
public class RoleManager {

    // GM tracking
    private UUID gmUUID = null;
    private PlayerRef gmPlayerRef = null;

    // Player tracking (UUID -> Player Number)
    private final Map<UUID, Integer> playerNumbers = new ConcurrentHashMap<>();

    // Track which player numbers are in use
    private final Set<Integer> usedPlayerNumbers = new HashSet<>();

    // Permission groups
    private static final String GM_GROUP = "GM";
    private static final String PLAYER_GROUP = "Player";

    /**
     * Assign player as GM
     * Returns true if successful, false if GM already exists
     */
    public boolean assignGM(PlayerRef playerRef) {
        UUID uuid = playerRef.getUuid();

        // Check if GM already exists
        if (gmUUID != null) {
            return false;
        }

        // Check if player already has a role
        if (playerNumbers.containsKey(uuid)) {
            return false; // Already a player
        }

        // Assign GM role
        gmUUID = uuid;
        gmPlayerRef = playerRef;

        // Add to GM permission group
        PermissionsModule.get().addUserToGroup(uuid, GM_GROUP);

        System.out.println("[Griddify] [INFO] Player " + playerRef.getUsername() + " assigned as GM");
        return true;
    }

    /**
     * Assign player as Player with number
     * Returns player number if successful, -1 if failed
     */
    public int assignPlayer(PlayerRef playerRef) {
        UUID uuid = playerRef.getUuid();

        // Check if already has a role
        if (uuid.equals(gmUUID)) {
            return -1; // Already GM
        }

        if (playerNumbers.containsKey(uuid)) {
            return playerNumbers.get(uuid); // Already has player number
        }

        // Find lowest available number (bottom-up)
        int playerNumber = findLowestAvailableNumber();

        // Assign player number
        playerNumbers.put(uuid, playerNumber);
        usedPlayerNumbers.add(playerNumber);

        // Add to Player permission group
        PermissionsModule.get().addUserToGroup(uuid, PLAYER_GROUP);

        System.out.println("[Griddify] [INFO] Player " + playerRef.getUsername() + " assigned as Player " + playerNumber);
        return playerNumber;
    }

    /**
     * Revoke a player's role (GM or Player)
     * Returns true if successful, false if no role to revoke
     */
    public boolean revokeRole(PlayerRef playerRef) {
        UUID uuid = playerRef.getUuid();

        // Check if GM
        if (uuid.equals(gmUUID)) {
            gmUUID = null;
            gmPlayerRef = null;
            PermissionsModule.get().removeUserFromGroup(uuid, GM_GROUP);
            System.out.println("[Griddify] [INFO] GM role revoked for " + playerRef.getUsername());
            return true;
        }

        // Check if Player
        Integer playerNumber = playerNumbers.remove(uuid);
        if (playerNumber != null) {
            usedPlayerNumbers.remove(playerNumber);
            PermissionsModule.get().removeUserFromGroup(uuid, PLAYER_GROUP);
            System.out.println("[Griddify] [INFO] Player " + playerNumber + " role revoked for " + playerRef.getUsername());
            return true;
        }

        return false; // No role to revoke
    }

    /**
     * Find lowest available player number (1, 2, 3, ...)
     */
    private int findLowestAvailableNumber() {
        for (int i = 1; i <= 1000; i++) {
            if (!usedPlayerNumbers.contains(i)) {
                return i;
            }
        }
        return 1; // Fallback
    }

    /**
     * Check if player is GM
     */
    public boolean isGM(PlayerRef playerRef) {
        return playerRef.getUuid().equals(gmUUID);
    }

    /**
     * Check if player has Player role
     */
    public boolean isPlayer(PlayerRef playerRef) {
        return playerNumbers.containsKey(playerRef.getUuid());
    }

    /**
     * Check if player has any role assigned
     */
    public boolean hasRole(PlayerRef playerRef) {
        UUID uuid = playerRef.getUuid();
        return uuid.equals(gmUUID) || playerNumbers.containsKey(uuid);
    }

    /**
     * Get player number for a player
     * Returns -1 if not a player
     */
    public int getPlayerNumber(PlayerRef playerRef) {
        return playerNumbers.getOrDefault(playerRef.getUuid(), -1);
    }

    /**
     * Get GM PlayerRef
     */
    public PlayerRef getGM() {
        return gmPlayerRef;
    }

    /**
     * Check if GM exists
     */
    public boolean hasGM() {
        return gmUUID != null;
    }

    /**
     * Reset all roles (server restart simulation or /GridRestart command)
     */
    public void resetAllRoles() {
        // Clear GM permissions
        if (gmUUID != null) {
            PermissionsModule.get().removeUserFromGroup(gmUUID, GM_GROUP);
        }

        // Clear all player permissions
        for (UUID uuid : playerNumbers.keySet()) {
            PermissionsModule.get().removeUserFromGroup(uuid, PLAYER_GROUP);
        }

        gmUUID = null;
        gmPlayerRef = null;
        playerNumbers.clear();
        usedPlayerNumbers.clear();
        System.out.println("[Griddify] [INFO] All roles reset");
    }

    /**
     * Handle player disconnect - keep role but mark as offline
     * Player number remains reserved until server restart or role reset
     */
    public void handlePlayerDisconnect(UUID uuid) {
        // Don't remove from maps - number stays reserved
        System.out.println("[Griddify] [DEBUG] Player " + uuid + " disconnected, role preserved");
    }
}