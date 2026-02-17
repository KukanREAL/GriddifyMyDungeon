package com.gridifymydungeon.plugin.dnd.commands;

import com.gridifymydungeon.plugin.gridmove.GridMoveManager;
import com.gridifymydungeon.plugin.gridmove.GridPlayerState;
import com.gridifymydungeon.plugin.spell.SpellData;
import com.gridifymydungeon.plugin.spell.SpellDatabase;
import com.gridifymydungeon.plugin.spell.SubclassType;
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
import java.util.List;

/**
 * /GridSubclass <subclass name> - Choose your subclass (requires level 3+)
 */
public class GridSubclassCommand extends AbstractPlayerCommand {
    private final GridMoveManager playerManager;
    private final RequiredArg<String> subclassNameArg;

    public GridSubclassCommand(GridMoveManager playerManager) {
        super("GridSubclass", "Choose your subclass (level 3+ required)");
        this.playerManager = playerManager;
        this.subclassNameArg = this.withRequiredArg("subclass", "Subclass name", ArgTypes.STRING);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {

        GridPlayerState state = playerManager.getState(playerRef);

        // Check if player has a class
        if (state.stats.getClassType() == null) {
            playerRef.sendMessage(Message.raw("[Griddify] Choose a class first! Use /GridClass").color("#FF0000"));
            return;
        }

        // Check level requirement
        if (state.stats.getLevel() < 3) {
            Message primary = Message.raw("Level 3 Required").color("#FF0000");
            Message secondary = Message.raw("You are level " + state.stats.getLevel()).color("#FFFFFF");
            ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Red", 1).toPacket();

            NotificationUtil.sendNotification(
                    playerRef.getPacketHandler(),
                    primary,
                    secondary,
                    icon,
                    NotificationStyle.Default
            );
            return;
        }

        String subclassName = subclassNameArg.get(context);

        // Find subclass by name
        SubclassType subclassType = null;
        for (SubclassType type : SubclassType.values()) {
            if (type.getParentClass() == state.stats.getClassType()) {
                if (type.getDisplayName().equalsIgnoreCase(subclassName) ||
                        type.name().equalsIgnoreCase(subclassName)) {
                    subclassType = type;
                    break;
                }
            }
        }

        if (subclassType == null) {
            playerRef.sendMessage(Message.raw("[Griddify] Unknown subclass for " +
                    state.stats.getClassType().getDisplayName() + ": " + subclassName).color("#FF0000"));
            playerRef.sendMessage(Message.raw("[Griddify] Available subclasses:").color("#FFFFFF"));

            for (SubclassType type : SubclassType.values()) {
                if (type.getParentClass() == state.stats.getClassType()) {
                    playerRef.sendMessage(Message.raw("  - " + type.getDisplayName()).color("#87CEEB"));
                }
            }
            return;
        }

        // Set subclass
        state.stats.setSubclassType(subclassType);

        // Success notification
        Message primary = Message.raw("Subclass Chosen").color("#FFD700");
        Message secondary = Message.raw(subclassType.getDisplayName()).color("#FFFFFF");
        ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Yellow", 1).toPacket();

        NotificationUtil.sendNotification(
                playerRef.getPacketHandler(),
                primary,
                secondary,
                icon,
                NotificationStyle.Default
        );

        List<SpellData> spells = SpellDatabase.getAvailableSpells(
                state.stats.getClassType(),  // ← Use this instead of just "classType"
                subclassType,
                state.stats.getLevel()
        );

        playerRef.sendMessage(Message.raw(""));
        playerRef.sendMessage(Message.raw("===========================================").color("#FFD700"));
        playerRef.sendMessage(Message.raw("  Subclass: " + subclassType.getDisplayName()).color("#FFD700"));
        playerRef.sendMessage(Message.raw("  Available Spells: " + spells.size()).color("#FFFFFF"));
        playerRef.sendMessage(Message.raw("===========================================").color("#FFD700"));

        for (SpellData spell : spells) {
            if (spell.getMinLevel() <= state.stats.getLevel()) {
                String levelText = spell.getSpellLevel() == 0 ? "Cantrip" : "Level " + spell.getSpellLevel();
                playerRef.sendMessage(Message.raw("  - " + spell.getName() + " (" + levelText + ")").color("#87CEEB"));
            }
        }

        playerRef.sendMessage(Message.raw(""));
        playerRef.sendMessage(Message.raw("  Use /Cast <spell> to cast spells!").color("#FFA500"));
        playerRef.sendMessage(Message.raw("===========================================").color("#FFD700"));

        System.out.println("[Griddify] [SUBCLASS] " + playerRef.getUsername() + " chose " + subclassType.getDisplayName());
    }
}