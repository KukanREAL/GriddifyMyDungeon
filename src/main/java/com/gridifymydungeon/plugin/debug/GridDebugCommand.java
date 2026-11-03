package com.gridifymydungeon.plugin.debug;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /griddebug — Toggle debug mode for the calling player.
 *
 * While debug mode is active all role/restriction checks that call
 * DebugRoleWrapper.isGM() pass unconditionally, letting anyone use
 * every command with no restrictions.
 *
 * REMOVE THIS WHOLE PACKAGE before release.
 */
public class GridDebugCommand extends AbstractPlayerCommand {

    /** Global set of UUIDs currently in debug mode — read by DebugRoleWrapper. */
    static final Set<UUID> debugPlayers = ConcurrentHashMap.newKeySet();

    public GridDebugCommand() {
        super("griddebug", "Toggle debug mode (dev only) — remove before release");
    }

    /** Returns true if the given UUID currently has debug mode enabled. */
    public static boolean isDebugMode(UUID uuid) {
        return debugPlayers.contains(uuid);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        UUID uuid = playerRef.getUuid();
        if (debugPlayers.contains(uuid)) {
            debugPlayers.remove(uuid);
            playerRef.sendMessage(Message.raw("[Griddify] Debug mode OFF.").color("#00FF7F"));
            System.out.println("[Griddify] [DEBUG] " + playerRef.getUsername() + " disabled debug mode.");
        } else {
            debugPlayers.add(uuid);
            playerRef.sendMessage(Message.raw(
                            "[Griddify] ⚠ DEBUG MODE ON — all restrictions lifted! Remove before release!")
                    .color("#FF4500"));
            System.out.println("[Griddify] [DEBUG] " + playerRef.getUsername() + " enabled debug mode.");
        }
    }
}