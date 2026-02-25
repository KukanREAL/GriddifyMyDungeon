package com.gridifymydungeon.plugin.dnd.commands;

import com.gridifymydungeon.plugin.dnd.PlayerEntityController;
import com.gridifymydungeon.plugin.gridmove.GridMoveManager;
import com.gridifymydungeon.plugin.gridmove.GridPlayerState;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * /testfog — Spawns one Grid_Player tile 2 blocks above the calling player,
 * visible ONLY to that player. Tests per-player entity (fog-of-war) feasibility.
 */
public class TestFogCommand extends AbstractPlayerCommand {

    private final GridMoveManager playerManager;
    // Store the last spawned ref per player so it can be cleaned up on re-run
    private Ref<EntityStore> lastRef = null;

    public TestFogCommand(GridMoveManager playerManager) {
        super("testfog", "Spawn a private Grid_Player tile 2 blocks above you (fog-of-war test)");
        this.playerManager = playerManager;
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {

        Vector3d pos;
        try {
            pos = playerRef.getTransform().getPosition();
        } catch (Exception e) {
            playerRef.sendMessage(Message.raw("[TestFog] Could not get player position.").color("#FF0000"));
            return;
        }

        float x = (float) pos.getX();
        float y = (float) pos.getY() + 2.0f;
        float z = (float) pos.getZ();

        // Clean up previous marker
        if (lastRef != null && lastRef.isValid()) {
            final Ref<EntityStore> toRemove = lastRef;
            world.execute(() -> {
                try {
                    world.getEntityStore().getStore().removeEntity(toRemove,
                            com.hypixel.hytale.component.RemoveReason.REMOVE);
                } catch (Exception ignored) {}
            });
        }

        final float fx = x, fy = y, fz = z;
        final PlayerRef fPlayer = playerRef;
        world.execute(() -> {
            Ref<EntityStore> newRef = PlayerEntityController.spawnPrivateEntity(
                    world, fPlayer, "Grid_Player", fx, fy, fz);
            lastRef = newRef;
            if (newRef != null) {
                fPlayer.sendMessage(Message.raw(
                                "[TestFog] ✓ Private Grid_Player spawned at your position +2Y. Only YOU can see it!")
                        .color("#00FF7F"));
            } else {
                fPlayer.sendMessage(Message.raw(
                                "[TestFog] ✗ Failed to spawn private entity. Check server logs.")
                        .color("#FF0000"));
            }
        });
    }
}