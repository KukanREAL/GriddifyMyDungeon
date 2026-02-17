package com.gridifymydungeon.plugin.dnd.commands;

import com.gridifymydungeon.plugin.dnd.CharacterPresets;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * /GridClasses - List all available character classes
 * WITH BEAUTIFUL COLORS!
 */
public class GridClassesCommand extends AbstractPlayerCommand {

    public GridClassesCommand() {
        super("GridClasses", "List all character classes");
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {

        // Get all presets
        Map<String, CharacterPresets.Preset> presets = CharacterPresets.getAllPresets();

        // Display header
        playerRef.sendMessage(Message.raw(""));
        playerRef.sendMessage(Message.raw("=========================================").color("#FFD700"));
        playerRef.sendMessage(Message.raw("      CHARACTER CLASSES").color("#00FFFF"));
        playerRef.sendMessage(Message.raw("=========================================").color("#FFD700"));
        playerRef.sendMessage(Message.raw(""));

        // Display each preset
        for (Map.Entry<String, CharacterPresets.Preset> entry : presets.entrySet()) {
            CharacterPresets.Preset preset = entry.getValue();

            playerRef.sendMessage(Message.raw("> " + preset.name.toUpperCase()).color("#FFD700"));
            playerRef.sendMessage(Message.raw("  STR:" + preset.str + " DEX:" + preset.dex + " CON:" + preset.con +
                    " INT:" + preset.intel + " WIS:" + preset.wis + " CHA:" + preset.cha).color("#FFFFFF"));
            playerRef.sendMessage(Message.raw("  HP:" + preset.hp + " Armor:" + preset.armor +
                    " Initiative:" + (preset.initiative >= 0 ? "+" : "") + preset.initiative).color("#00BFFF"));
            playerRef.sendMessage(Message.raw(""));
        }

        playerRef.sendMessage(Message.raw("=========================================").color("#FFD700"));
        playerRef.sendMessage(Message.raw("Use: /GridClass <name> to apply").color("#90EE90"));
        playerRef.sendMessage(Message.raw("Example: /GridClass Fighter").color("#FFFFFF"));
        playerRef.sendMessage(Message.raw(""));

        System.out.println("[Griddify] [PRESETS] " + playerRef.getUsername() + " viewed presets list");
    }
}