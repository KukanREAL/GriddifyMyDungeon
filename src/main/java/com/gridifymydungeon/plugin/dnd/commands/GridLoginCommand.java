package com.gridifymydungeon.plugin.dnd.commands;

import com.gridifymydungeon.plugin.dnd.CharacterCodec;
import com.gridifymydungeon.plugin.dnd.RoleManager;
import com.gridifymydungeon.plugin.gridmove.GridMoveManager;
import com.gridifymydungeon.plugin.gridmove.GridPlayerState;
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
 * /gridlogin <code> - Load character stats from a code
 * Supports 11-char format (AABBCC-DEF-GH) or 15-char format with spells
 * Restores all stats, maxMoves, and flying status
 * FIXED: Use correct field names from DecodedStats (hp not maxHP, flying not isFlying)
 */
public class GridLoginCommand extends AbstractPlayerCommand {

    private final GridMoveManager playerManager;
    private final RoleManager roleManager;
    private final RequiredArg<String> codeArg;

    public GridLoginCommand(GridMoveManager playerManager, RoleManager roleManager) {
        super("gridlogin", "Load character stats from code");
        this.playerManager = playerManager;
        this.roleManager = roleManager;
        this.codeArg = this.withRequiredArg("code", "Your character code", ArgTypes.STRING);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {

        // GMs cannot use this
        if (roleManager.isGM(playerRef)) {
            playerRef.sendMessage(Message.raw("[Griddify] GMs cannot use character codes!"));
            return;
        }

        String code = codeArg.get(context);

        // Decode the character code
        CharacterCodec.DecodedStats decoded = CharacterCodec.decode(code);

        if (decoded == null) {
            playerRef.sendMessage(Message.raw("[Griddify] Invalid character code!"));
            playerRef.sendMessage(Message.raw("[Griddify] Make sure you copied it correctly."));
            playerRef.sendMessage(Message.raw("[Griddify] Format: AABBCC-DEF-GH (11 characters)"));
            playerRef.sendMessage(Message.raw("[Griddify] Or with spells: AABBCC-DEF-GH-HIJK (15 characters)"));
            System.out.println("[Griddify] [LOGIN] " + playerRef.getUsername() +
                    " failed to decode code: " + code);
            return;
        }

        GridPlayerState state = playerManager.getState(playerRef);

        // Apply decoded stats
        decoded.applyTo(state.stats);
        state.maxMoves = decoded.maxMoves;
        state.remainingMoves = decoded.maxMoves;

        // Display success
        playerRef.sendMessage(Message.raw(""));
        playerRef.sendMessage(Message.raw("=========================================").color("#FFD700"));
        playerRef.sendMessage(Message.raw("     CHARACTER LOADED SUCCESSFULLY").color("#00FF00"));
        playerRef.sendMessage(Message.raw("=========================================").color("#FFD700"));
        playerRef.sendMessage(Message.raw(""));
        playerRef.sendMessage(Message.raw("Stats restored:").color("#00BFFF"));
        playerRef.sendMessage(Message.raw("  STR: " + decoded.strength +
                " DEX: " + decoded.dexterity +
                " CON: " + decoded.constitution).color("#FFFFFF"));
        playerRef.sendMessage(Message.raw("  INT: " + decoded.intelligence +
                " WIS: " + decoded.wisdom +
                " CHA: " + decoded.charisma).color("#FFFFFF"));
        // FIXED: Use decoded.hp instead of decoded.maxHP
        playerRef.sendMessage(Message.raw("  HP: " + decoded.hp +
                " Armor: " + decoded.armor +
                " Initiative: " + (decoded.initiative >= 0 ? "+" : "") + decoded.initiative).color("#FFFFFF"));
        // FIXED: Use decoded.flying instead of decoded.isFlying
        playerRef.sendMessage(Message.raw("  Max Moves: " + decoded.maxMoves +
                " Flying: " + (decoded.flying ? "Yes" : "No")).color("#FFFFFF"));

        // Show spell data if present - FIXED: No hasSpellData field, check level/class instead
        if (decoded.level > 1 || decoded.classType != null) {
            playerRef.sendMessage(Message.raw(""));
            playerRef.sendMessage(Message.raw("Character info loaded:").color("#FF69B4"));
            playerRef.sendMessage(Message.raw("  Level: " + decoded.level).color("#FFFFFF"));
            if (decoded.classType != null) {
                playerRef.sendMessage(Message.raw("  Class: " + decoded.classType.getDisplayName()).color("#FFFFFF"));
            }
            if (decoded.subclassType != null) {
                playerRef.sendMessage(Message.raw("  Subclass: " + decoded.subclassType.getDisplayName()).color("#FFFFFF"));
            }
            // FIXED: Use decoded.spellSlots directly
            playerRef.sendMessage(Message.raw("  Spell slots: " + decoded.spellSlots).color("#FFFFFF"));
        }

        playerRef.sendMessage(Message.raw(""));
        playerRef.sendMessage(Message.raw("You can now use /gridmove to start playing!").color("#90EE90"));
        playerRef.sendMessage(Message.raw(""));
        playerRef.sendMessage(Message.raw("=========================================").color("#FFD700"));

        System.out.println("[Griddify] [LOGIN] " + playerRef.getUsername() +
                " loaded character: STR=" + decoded.strength + " DEX=" + decoded.dexterity +
                " Level=" + decoded.level);
    }
}