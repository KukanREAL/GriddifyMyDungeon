package com.griddifymydungeon.plugin.vtt;

import java.io.*;

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
