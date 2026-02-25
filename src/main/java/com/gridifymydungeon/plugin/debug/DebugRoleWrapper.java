package com.gridifymydungeon.plugin.debug;

import com.gridifymydungeon.plugin.dnd.RoleManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;

/**
 * Drop-in helper for isGM checks.
 * When a player has debug mode on, every isGM() call returns true.
 *
 * REMOVE THIS WHOLE PACKAGE before release.
 */
public class DebugRoleWrapper {

    /** True if player is GM or has debug mode enabled. */
    public static boolean isGM(RoleManager roleManager, PlayerRef playerRef) {
        return GridDebugCommand.isDebugMode(playerRef.getUuid()) || roleManager.isGM(playerRef);
    }

    public static boolean isDebug(PlayerRef playerRef) {
        return GridDebugCommand.isDebugMode(playerRef.getUuid());
    }
}