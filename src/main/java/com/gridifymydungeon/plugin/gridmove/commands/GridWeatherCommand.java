package com.gridifymydungeon.plugin.gridmove.commands;

import com.gridifymydungeon.plugin.dnd.RoleManager;
import com.gridifymydungeon.plugin.gridmove.TerrainManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * /gridweather {normal|difficult|swift|chase}
 *
 *   normal    — standard movement costs (default)
 *   difficult — every cell is difficult terrain (2× move cost)
 *   swift     — every move costs 0.5× (fast travel / chase scenes)
 *   chase     — movement is completely free (no cost)
 *
 * GM-only.
 */
public class GridWeatherCommand extends AbstractPlayerCommand {

    private final RoleManager roleManager;
    private final RequiredArg<String> modeArg;

    public GridWeatherCommand(RoleManager roleManager) {
        super("gridweather", "Set movement weather: /gridweather {normal|difficult|swift|chase}");
        this.roleManager = roleManager;
        this.modeArg = this.withRequiredArg("mode", "normal | difficult | swift | chase", ArgTypes.STRING);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {

        if (!roleManager.isGM(playerRef)) {
            playerRef.sendMessage(Message.raw("[Griddify] GM only.").color("#FF0000"));
            return;
        }

        String input = modeArg.get(context).toLowerCase().trim();
        TerrainManager.WeatherMode mode;
        String label;
        String color;

        switch (input) {
            case "normal":    mode = TerrainManager.WeatherMode.NORMAL;    label = "Normal";    color = "#00FF7F"; break;
            case "difficult": mode = TerrainManager.WeatherMode.DIFFICULT; label = "Difficult terrain everywhere (2×)"; color = "#FFA500"; break;
            case "swift":     mode = TerrainManager.WeatherMode.SWIFT;     label = "Swift (0.5× cost)"; color = "#00BFFF"; break;
            case "chase":     mode = TerrainManager.WeatherMode.CHASE;     label = "Chase (free movement)"; color = "#FFD700"; break;
            default:
                playerRef.sendMessage(Message.raw("[Griddify] Unknown mode. Use: normal, difficult, swift, chase").color("#FF0000"));
                return;
        }

        TerrainManager.setWeatherMode(mode);
        playerRef.sendMessage(Message.raw("[Griddify] Weather → " + label).color(color));
        System.out.println("[Griddify] [WEATHER] " + playerRef.getUsername() + " set " + mode.name());
    }
}