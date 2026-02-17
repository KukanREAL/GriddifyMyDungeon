package com.gridifymydungeon.plugin.dnd;

import com.gridifymydungeon.plugin.gridmove.GridPlayerState;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages player data (not implemented at the moment)
 */
public class PlayerDataManager {

    private final File dataDirectory;
//    private final Map<UUID, SavedPlayerData> cachedData = new HashMap<>();

    public PlayerDataManager(File pluginDataFolder) {
        this.dataDirectory = new File(pluginDataFolder, "playerdata");
        if (!dataDirectory.exists()) {
            dataDirectory.mkdirs();
        }
    }
}
