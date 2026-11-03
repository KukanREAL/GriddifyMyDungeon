package com.gridifymydungeon.plugin.dnd.commands;

import com.gridifymydungeon.plugin.dnd.CharacterPresets;
import com.gridifymydungeon.plugin.dnd.RoleManager;
import com.gridifymydungeon.plugin.dnd.EncounterManager;
import com.gridifymydungeon.plugin.gridmove.GridMoveManager;
import com.gridifymydungeon.plugin.gridmove.GridPlayerState;
import com.gridifymydungeon.plugin.spell.ClassType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.ItemWithAllMetadata;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;

import javax.annotation.Nonnull;

/**
 * /GridClass <class name> - Choose your character class
 * RENAMED from /GridPreset
 * FIXED: Use CharacterPresets.Preset and applyPreset()
 */
public class GridClassCommand extends AbstractPlayerCommand {
    private final GridMoveManager playerManager;
    private final RoleManager roleManager;
    private final EncounterManager encounterManager;
    private final RequiredArg<String> classNameArg;

    public GridClassCommand(GridMoveManager playerManager, RoleManager roleManager, EncounterManager encounterManager) {
        super("GridClass", "Choose your character class");
        this.playerManager = playerManager;
        this.roleManager = roleManager;
        this.encounterManager = encounterManager;
        this.classNameArg = this.withRequiredArg("class", "Class name (Wizard, Fighter, etc.)", ArgTypes.STRING);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {

        String className = classNameArg.get(context);

        GridPlayerState state = playerManager.getState(playerRef);

        // Try to find class by name
        ClassType classType = null;
        for (ClassType type : ClassType.values()) {
            if (type.getDisplayName().equalsIgnoreCase(className) || type.name().equalsIgnoreCase(className)) {
                classType = type;
                break;
            }
        }

        if (classType == null) {
            playerRef.sendMessage(Message.raw("[Griddify] Unknown class: " + className).color("#FF0000"));
            playerRef.sendMessage(Message.raw("[Griddify] Use /GridClasses to see available classes").color("#FFFFFF"));
            return;
        }

        // Apply preset stats from CharacterPresets - FIXED: Use Preset class
        CharacterPresets.Preset preset = CharacterPresets.getPreset(className);
        if (preset != null) {
            // FIXED: Use static applyPreset method
            CharacterPresets.applyPreset(state.stats, preset);
            state.stats.setClassType(classType);
            state.stats.setLevel(1);
            state.stats.setSubclassType(null); // Reset subclass
            state.stats.setSpellSlots(100);
            state.stats.setUsedSpellSlots(0);

            // Success notification
            Message primary = Message.raw("Class Selected").color("#00FF00");
            Message secondary = Message.raw(classType.getDisplayName() + " - Level 1").color("#FFFFFF");
            ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Green", 1).toPacket();

            NotificationUtil.sendNotification(
                    playerRef.getPacketHandler(),
                    primary,
                    secondary,
                    icon,
                    NotificationStyle.Default
            );

            playerRef.sendMessage(Message.raw(""));
            playerRef.sendMessage(Message.raw("===========================================").color("#00FF00"));
            playerRef.sendMessage(Message.raw("  Class: " + classType.getDisplayName()).color("#FFD700"));
            playerRef.sendMessage(Message.raw("  Spellcasting: " + classType.getSpellcastingAbility()).color("#FFFFFF"));
            if (classType.isSpellcaster()) {
                playerRef.sendMessage(Message.raw("  Spell Slots: 100").color("#87CEEB"));
                playerRef.sendMessage(Message.raw("  Choose subclass at level 3!").color("#FFA500"));
            }
            playerRef.sendMessage(Message.raw("===========================================").color("#00FF00"));
            playerRef.sendMessage(Message.raw(""));

            System.out.println("[Griddify] [CLASS] " + playerRef.getUsername() + " selected " + classType.getDisplayName());
        } else {
            playerRef.sendMessage(Message.raw("[Griddify] Failed to load class preset!").color("#FF0000"));
        }
    }
}