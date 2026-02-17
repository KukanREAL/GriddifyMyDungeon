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
 * /gridhelp - Display all available commands organized by role
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

        // Header
        playerRef.sendMessage(Message.raw(""));
        playerRef.sendMessage(Message.raw("=========================================").color("#FFD700"));
        playerRef.sendMessage(Message.raw("         GRIDDIFY COMMANDS").color("#00FFFF"));
        playerRef.sendMessage(Message.raw("=========================================").color("#FFD700"));

        // Role status
        if (isGM) {
            playerRef.sendMessage(Message.raw("Your Role: GAME MASTER").color("#FF0000"));
        } else if (isPlayer) {
            int playerNum = roleManager.getPlayerNumber(playerRef);
            playerRef.sendMessage(Message.raw("Your Role: Player " + playerNum).color("#00FF00"));
        } else {
            playerRef.sendMessage(Message.raw("No role assigned - Use /GridGM or /GridPlayer").color("#808080"));
        }
        playerRef.sendMessage(Message.raw(""));

        // SETUP COMMANDS
        playerRef.sendMessage(Message.raw("SETUP COMMANDS").color("#FFD700"));
        sendCommand(playerRef, "/GridGM", "Become the Game Master", "#FF6347");
        sendCommand(playerRef, "/GridPlayer", "Become a numbered Player", "#90EE90");
        playerRef.sendMessage(Message.raw(""));

        // SETUP COMMANDS section
        sendCommand(playerRef, "/GridNull", "Revoke your current role", "#FF6347");
        sendCommand(playerRef, "/GridRestart", "Reset all roles (GM only)", "#FFD700");

        // PLAYER COMMANDS
        playerRef.sendMessage(Message.raw("PLAYER COMMANDS").color("#00FF00"));
        playerRef.sendMessage(Message.raw("Movement:").color("#00BFFF"));
        sendCommand(playerRef, "/gridmove", "Activate/deactivate grid", "#FFFFFF");
        sendCommand(playerRef, "/gridon", "Show movement range", "#FFFFFF");
        sendCommand(playerRef, "/gridoff", "Hide range overlay", "#FFFFFF");
        sendCommand(playerRef, "/maxmoves <n>", "Set max moves per turn", "#FFFFFF");
        sendCommand(playerRef, "/endturn", "End turn & reset moves", "#FFFFFF");
        sendCommand(playerRef, "/gridcam <0|1>", "Switch camera view", "#FFFFFF");
        playerRef.sendMessage(Message.raw("Stats:").color("#00BFFF"));
        sendCommand(playerRef, "/STR /DEX /CON /INT /WIS /CHA", "Set ability scores (0-30)", "#FFFFFF");
        sendCommand(playerRef, "/HP <1-999>", "Set max HP", "#FFFFFF");
        sendCommand(playerRef, "/ARMOR <1-50>", "Set armor class", "#FFFFFF");
        sendCommand(playerRef, "/INITIATIVE <-10 to +10>", "Set initiative", "#FFFFFF");
        sendCommand(playerRef, "/flying", "Toggle flying mode", "#FFFFFF");
        playerRef.sendMessage(Message.raw("Presets:").color("#00BFFF"));
        sendCommand(playerRef, "/GridPresets", "List all class presets", "#FFFFFF");
        sendCommand(playerRef, "/GridPreset <class>", "Apply preset", "#FFFFFF");
        sendCommand(playerRef, "/gridprofile", "View your character profile", "#FFFFFF");
        sendCommand(playerRef, "/gridregister", "Generate character code", "#FFFFFF");
        sendCommand(playerRef, "/gridlogin <code>", "Load character from code", "#FFFFFF");
        playerRef.sendMessage(Message.raw(""));

        // GM COMMANDS
        playerRef.sendMessage(Message.raw("GM COMMANDS").color("#FF0000"));
        playerRef.sendMessage(Message.raw("Monsters:").color("#FFA500"));
        sendCommand(playerRef, "/creature <name> <#>", "Spawn a monster", "#FFFFFF");
        sendCommand(playerRef, "/control <#>", "Control monster (0=stop)", "#FFFFFF");
        sendCommand(playerRef, "/slain <#>", "Remove slain monster", "#FFFFFF");
        sendCommand(playerRef, "/gridon", "Show monster range", "#FFFFFF");
        playerRef.sendMessage(Message.raw("Combat:").color("#FFA500"));
        sendCommand(playerRef, "/initiv", "Roll initiative for all", "#FFFFFF");
        sendCommand(playerRef, "/combat", "Start/end combat mode", "#FFFFFF");
        sendCommand(playerRef, "/Critical", "Toggle critical rolls", "#FFFFFF");
        playerRef.sendMessage(Message.raw("Monster Stats:").color("#FFA500"));
        sendCommand(playerRef, "/STR /DEX /CON etc.", "Set monster stats", "#FFFFFF");
        sendCommand(playerRef, "/GridPreset <class>", "Apply preset to monster", "#FFFFFF");
        sendCommand(playerRef, "/flying", "Toggle monster flying", "#FFFFFF");
        playerRef.sendMessage(Message.raw(""));

        // SHARED COMMANDS
        playerRef.sendMessage(Message.raw("SHARED COMMANDS (Player & GM)").color("#00FFFF"));
        playerRef.sendMessage(Message.raw("Dice:").color("#00BFFF"));
        sendCommand(playerRef, "/dice <2-100>", "Roll a dice", "#FFFFFF");
        sendCommand(playerRef, "/AdDice <2-100>", "Roll with advantage", "#FFFFFF");
        sendCommand(playerRef, "/DisDice <2-100>", "Roll with disadvantage", "#FFFFFF");
        playerRef.sendMessage(Message.raw("Utility:").color("#00BFFF"));
        sendCommand(playerRef, "/clearholograms", "Remove all holograms", "#FFFFFF");
        sendCommand(playerRef, "/gridhelp", "Show this help menu", "#FFFFFF");
        playerRef.sendMessage(Message.raw(""));

        // Footer
        playerRef.sendMessage(Message.raw("=========================================").color("#FFD700"));
        if (isGM) {
            playerRef.sendMessage(Message.raw("Tip: /creature spawn, /control move, /combat start").color("#FFA500"));
        } else if (isPlayer) {
            playerRef.sendMessage(Message.raw("Tip: /gridmove, /gridon, /GridPreset for quick setup").color("#90EE90"));
        } else {
            playerRef.sendMessage(Message.raw("Start: Choose /GridGM or /GridPlayer first!").color("#00BFFF"));
        }
        playerRef.sendMessage(Message.raw(""));

        System.out.println("[Griddify] [HELP] " + playerRef.getUsername() + " viewed help menu");
    }

    /**
     * Send a formatted command line
     */
    private void sendCommand(PlayerRef playerRef, String command, String description, String color) {
        playerRef.sendMessage(Message.raw("  " + command).color("#FFD700")
                .insert(Message.raw(" - " + description).color(color)));
    }
}