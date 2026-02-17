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
 * /GridGM command - Assigns player as Game Master
 * Can only be used once per server session
 * Only one GM allowed
 * WITH COLORS!
 */
public class GMCommand extends AbstractPlayerCommand {

    private final RoleManager roleManager;

    public GMCommand(RoleManager roleManager) {
        super("GridGM", "Become the Game Master");
        this.roleManager = roleManager;
    }

    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        // Check if GM already exists
        if (roleManager.hasGM()) {
            PlayerRef existingGM = roleManager.getGM();
            if (existingGM != null && existingGM.getUuid().equals(playerRef.getUuid())) {
                playerRef.sendMessage(Message.raw("[Griddify] You are already the GM!").color("#FFD700"));
            } else {
                playerRef.sendMessage(Message.raw("[Griddify] A GM already exists! Only one GM per server.").color("#FF0000"));
            }
            return;
        }

        // Check if player already has a role
        if (roleManager.hasRole(playerRef)) {
            int playerNum = roleManager.getPlayerNumber(playerRef);
            if (playerNum > 0) {
                playerRef.sendMessage(Message.raw("[Griddify] You are already Player " + playerNum +
                        "! Cannot become GM.").color("#FF0000"));
            }
            return;
        }

        // Assign as GM
        boolean success = roleManager.assignGM(playerRef);

        if (success) {
            playerRef.sendMessage(Message.raw(""));
            playerRef.sendMessage(Message.raw("=========================================").color("#FFD700"));
            playerRef.sendMessage(Message.raw("       GAME MASTER ASSIGNED").color("#FF0000"));
            playerRef.sendMessage(Message.raw("=========================================").color("#FFD700"));
            playerRef.sendMessage(Message.raw(""));
            playerRef.sendMessage(Message.raw("You are now the Game Master!").color("#00FFFF"));
            playerRef.sendMessage(Message.raw(""));
            playerRef.sendMessage(Message.raw("GM Commands:").color("#00BFFF"));
            playerRef.sendMessage(Message.raw("  * /creature <n> <#> - Spawn monsters").color("#FFFFFF"));
            playerRef.sendMessage(Message.raw("  * /control <#> - Control a monster").color("#FFFFFF"));
            playerRef.sendMessage(Message.raw("  * /combat - Start/end combat").color("#FFFFFF"));
            playerRef.sendMessage(Message.raw("  * /slain <#> - Remove defeated monsters").color("#FFFFFF"));
            playerRef.sendMessage(Message.raw(""));
            playerRef.sendMessage(Message.raw("Have Fun, Game Master!").color("#90EE90"));
            playerRef.sendMessage(Message.raw(""));

            System.out.println("[Griddify] [INFO] " + playerRef.getUsername() + " is now GM");
        } else {
            playerRef.sendMessage(Message.raw("[Griddify] Failed to assign GM role!").color("#FF0000"));
        }
    }
}