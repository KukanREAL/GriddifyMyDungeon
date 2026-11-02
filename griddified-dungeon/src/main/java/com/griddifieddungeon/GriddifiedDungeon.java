package com.griddifieddungeon;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import java.util.logging.Level;

public class GriddifiedDungeon extends JavaPlugin {

    public static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public GriddifiedDungeon(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void start() {
        LOGGER.at(Level.INFO).log("Starting Griddified Dungeon!");
    }

    @Override
    protected void setup() {
        LOGGER.at(Level.INFO).log("Setting up Griddified Dungeon!");
    }

    @Override
    protected void shutdown() {
        LOGGER.at(Level.INFO).log("Shutting down Griddified Dungeon!");
    }
}
