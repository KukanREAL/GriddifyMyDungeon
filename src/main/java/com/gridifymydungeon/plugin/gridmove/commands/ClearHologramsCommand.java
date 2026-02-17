package com.gridifymydungeon.plugin.gridmove.commands;

import com.gridifymydungeon.plugin.gridmove.GridMoveManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;

/**
 * /clearholograms command - Remove all holograms from the world
 */
public class ClearHologramsCommand extends AbstractPlayerCommand {

    private final GridMoveManager manager;

    public ClearHologramsCommand(GridMoveManager manager) {
        super("clearholograms", "Remove all holograms from the world");
        this.manager = manager;
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {

        System.out.println("[ClearHolograms] [DEBUG] Player " + playerRef.getUsername() + " executing clear holograms");

        world.execute(() -> {
            int removedCount = removeAllHolograms(world);
            manager.clearAllHologramReferences();

            playerRef.sendMessage(Message.raw("[ClearHolograms] Removed " + removedCount + " hologram(s)"));
            System.out.println("[ClearHolograms] [INFO] Removed " + removedCount + " holograms");
        });
    }

    private int removeAllHolograms(World world) {
        int removedCount = 0;

        synchronized (GridMoveCommand.ALL_HOLOGRAMS) {
            for (Ref<EntityStore> hologram : new ArrayList<>(GridMoveCommand.ALL_HOLOGRAMS)) {
                try {
                    world.getEntityStore().getStore().removeEntity(hologram, RemoveReason.REMOVE);
                    removedCount++;
                } catch (Exception e) {
                    System.err.println("[ClearHolograms] [ERROR] Failed to remove hologram: " + e.getMessage());
                }
            }
            GridMoveCommand.ALL_HOLOGRAMS.clear();
        }

        return removedCount;
    }
}
