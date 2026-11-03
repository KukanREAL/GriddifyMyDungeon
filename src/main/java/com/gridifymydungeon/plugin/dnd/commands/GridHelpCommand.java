package com.gridifymydungeon.plugin.dnd.commands;

import com.gridifymydungeon.plugin.dnd.RoleManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * /gridhelp - Display all available commands organized by role.
 */
public class GridHelpCommand extends AbstractPlayerCommand {

    private final RoleManager roleManager;

    public GridHelpCommand(RoleManager roleManager) {
        super("gridhelp", "Show all available commands");
        this.roleManager = roleManager;
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {

        boolean isGM = roleManager.isGM(playerRef);
        boolean isPlayer = roleManager.isPlayer(playerRef);

        playerRef.sendMessage(Message.raw(""));
        playerRef.sendMessage(Message.raw("=========================================").color("#FFD700"));
        playerRef.sendMessage(Message.raw("         GRIDDIFY COMMANDS").color("#00FFFF"));
        playerRef.sendMessage(Message.raw("=========================================").color("#FFD700"));

        if (isGM)         playerRef.sendMessage(Message.raw("Your Role: GAME MASTER").color("#FF4500"));
        else if (isPlayer) playerRef.sendMessage(Message.raw("Your Role: Player " + roleManager.getPlayerNumber(playerRef)).color("#00FF00"));
        else               playerRef.sendMessage(Message.raw("No role - Use /GridGM or /GridPlayer").color("#808080"));
        playerRef.sendMessage(Message.raw(""));

        // SETUP
        section(playerRef, "SETUP");
        cmd(playerRef, "/GridGM",         "Become the Game Master");
        cmd(playerRef, "/GridPlayer",     "Become a numbered Player");
        cmd(playerRef, "/GridNull",       "Revoke your role");
        cmd(playerRef, "/GridRestart",    "Reset all roles (GM only)");
        playerRef.sendMessage(Message.raw(""));

        // PLAYER COMMANDS
        section(playerRef, "PLAYER COMMANDS");
        playerRef.sendMessage(Message.raw("  Movement:").color("#00BFFF"));
        cmd(playerRef, "/gridmove",       "Activate/deactivate NPC");
        cmd(playerRef, "/gridon",         "Show movement overlay");
        cmd(playerRef, "/gridoff",        "Hide overlay");
        cmd(playerRef, "/maxmoves <n>",   "Set max moves per turn");
        cmd(playerRef, "/endturn",        "End turn & reset moves");
        cmd(playerRef, "/gridcam <0|1>",  "Switch camera view");
        playerRef.sendMessage(Message.raw("  Class & Stats:").color("#00BFFF"));
        cmd(playerRef, "/GridClass <c>",  "Choose class (Cleric, Wizard...)");
        cmd(playerRef, "/GridSubclass <s>","Choose subclass (level 3+)");
        cmd(playerRef, "/GridClasses",    "List all classes");
        cmd(playerRef, "/GridPresets",    "List class presets");
        cmd(playerRef, "/gridprofile",    "View character profile");
        cmd(playerRef, "/LevelUp",        "Level up your character");
        cmd(playerRef, "/LevelDown",      "Level down your character");
        cmd(playerRef, "/STR /DEX /CON",  "Set ability scores");
        cmd(playerRef, "/HP /ARMOR /INITIATIVE", "Set combat stats");
        cmd(playerRef, "/flying",         "Toggle flying mode");
        playerRef.sendMessage(Message.raw("  Spells & Actions:").color("#00BFFF"));
        cmd(playerRef, "/ListSpells",     "Show available spells/attacks");
        cmd(playerRef, "/Cast <spell>",   "Prepare a spell (NPC freezes)");
        cmd(playerRef, "/CastFinal",      "Fire the prepared spell");
        cmd(playerRef, "/CastCancel",     "Cancel prepared spell");
        playerRef.sendMessage(Message.raw("  How casting works:").color("#808080"));
        playerRef.sendMessage(Message.raw("    1. /Cast <name>  ->  NPC freezes").color("#808080"));
        playerRef.sendMessage(Message.raw("    2. Walk body to aim (or around NPC for CONE/LINE)").color("#808080"));
        playerRef.sendMessage(Message.raw("    3. /CastFinal  ->  spell fires, NPC unfreezes").color("#808080"));
        playerRef.sendMessage(Message.raw(""));

        // GM COMMANDS
        section(playerRef, "GM COMMANDS");
        playerRef.sendMessage(Message.raw("  Monsters:").color("#FFA500"));
        cmd(playerRef, "/creature <n> <#>","Spawn a monster");
        cmd(playerRef, "/control <#>",    "Control monster (0=stop)");
        cmd(playerRef, "/slain <#>",      "Remove slain monster");
        cmd(playerRef, "/GridClass <c>",  "Set monster class while controlling");
        playerRef.sendMessage(Message.raw("  Monster Actions:").color("#FFA500"));
        cmd(playerRef, "/ListSpells",     "Show monster actions when controlling");
        cmd(playerRef, "/Cast <attack>",  "Use Scratch / Hit / Bow_Shot");
        cmd(playerRef, "/CastFinal",      "Fire the attack");
        playerRef.sendMessage(Message.raw("  Combat:").color("#FFA500"));
        cmd(playerRef, "/combat",         "Start/end combat (broadcasts to all)");
        cmd(playerRef, "/initiv",         "Roll initiative for all");
        cmd(playerRef, "/endturn",        "Advance turn (broadcasts turn order)");
        cmd(playerRef, "/Critical",       "Toggle critical rolls");
        playerRef.sendMessage(Message.raw(""));

        // SHARED
        section(playerRef, "SHARED COMMANDS");
        cmd(playerRef, "/dice <2-100>",   "Roll a dice");
        cmd(playerRef, "/AdDice <n>",     "Roll with advantage");
        cmd(playerRef, "/DisDice <n>",    "Roll with disadvantage");
        cmd(playerRef, "/clearholograms", "Remove all holograms");
        cmd(playerRef, "/gridhelp",       "Show this menu");
        playerRef.sendMessage(Message.raw(""));

        playerRef.sendMessage(Message.raw("=========================================").color("#FFD700"));
        if (isGM)         playerRef.sendMessage(Message.raw("Tip: /creature bat 1 -> /combat -> /control 1 -> /ListSpells").color("#FFA500"));
        else if (isPlayer) playerRef.sendMessage(Message.raw("Tip: /GridClass Cleric -> /Cast Sacred_Flame -> /CastFinal").color("#90EE90"));
        else               playerRef.sendMessage(Message.raw("Start: /GridGM or /GridPlayer").color("#00BFFF"));
        playerRef.sendMessage(Message.raw(""));

        System.out.println("[Griddify] [HELP] " + playerRef.getUsername() + " viewed help menu");
    }

    private void section(PlayerRef p, String title) {
        p.sendMessage(Message.raw(title).color("#FFD700"));
    }

    private void cmd(PlayerRef p, String command, String description) {
        p.sendMessage(Message.raw("  " + command).color("#FFD700")
                .insert(Message.raw(" - " + description).color("#FFFFFF")));
    }
}