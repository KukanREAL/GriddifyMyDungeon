package com.gridifymydungeon.plugin.debug;

import com.gridifymydungeon.plugin.dnd.RoleManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;

/**
 * Thin wrapper — debug mode fully removed. isGM() delegates straight to roleManager.
 */
public class DebugRoleWrapper {
    public static boolean isGM(RoleManager roleManager, PlayerRef playerRef) {
        return roleManager.isGM(playerRef);
    }
    public static boolean isDebug(PlayerRef playerRef) {
        return false;
    }
}