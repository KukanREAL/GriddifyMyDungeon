package com.gridifymydungeon.plugin.gridmove;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

/**
 * Empty HUD — used to clear/hide the custom HUD.
 * Per Hytale docs: "Set a custom hud with an empty build method to hide custom UI"
 */
public class EmptyHud extends CustomUIHud {

    public EmptyHud(@Nonnull PlayerRef playerRef) {
        super(playerRef);
    }

    @Override
    public void build(@Nonnull UICommandBuilder uiCommandBuilder) {
        // intentionally empty — hides the custom HUD
    }
}