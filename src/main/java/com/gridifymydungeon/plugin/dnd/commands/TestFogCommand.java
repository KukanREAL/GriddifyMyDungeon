package com.gridifymydungeon.plugin.dnd.commands;

import com.gridifymydungeon.plugin.dnd.PlayerEntityController;
import com.gridifymydungeon.plugin.gridmove.GridMoveManager;
import com.gridifymydungeon.plugin.gridmove.GridPlayerState;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * /testfog — spawns a Grid_Player tile 2 blocks above the player's NPC,
 * visible ONLY to the calling player (fog-of-war proof of concept).
 *
 * The marker follows the NPC on every grid move (PlayerPositionTracker calls
 * PlayerEntityController.moveFogMarker on each successful NPC step).
 *
 * How private visibility works:
 *   1. Entity spawned normally — entity-tracker will broadcast it to ALL viewers next tick.
 *   2. We wait 200 ms (one tick ~= 50 ms, so 4 ticks is enough).
 *   3. Then send EntityUpdates(removed=[netId]) to every OTHER player that can see it.
 *   4. From their perspective the entity never existed (spawn + immediate remove = net zero).
 */
public class TestFogCommand extends AbstractPlayerCommand {

    private static final ScheduledExecutorService SCHED =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "testfog-hide-scheduler");
                t.setDaemon(true);
                return t;
            });

    private final GridMoveManager playerManager;

    public TestFogCommand(GridMoveManager playerManager) {
        super("testfog", "Spawn private fog-of-war marker above your NPC (only you see it)");
        this.playerManager = playerManager;
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {

        GridPlayerState state = playerManager.getState(playerRef);

        if (state.npcEntity == null || !state.npcEntity.isValid()) {
            playerRef.sendMessage(Message.raw(
                    "[TestFog] No NPC active. Use /gridmove first.").color("#FF0000"));
            return;
        }

        // Remove old marker if present
        if (state.fogMarkerRef != null && state.fogMarkerRef.isValid()) {
            final Ref<EntityStore> old = state.fogMarkerRef;
            world.execute(() -> {
                try { world.getEntityStore().getStore().removeEntity(old, RemoveReason.REMOVE); }
                catch (Exception ignored) {}
            });
            state.fogMarkerRef   = null;
            state.fogMarkerNetId = -1;
        }

        float wx = (state.currentGridX * 2.0f) + 1.0f;
        float wy = state.npcY;        // same height as player standing (ground level)
        float wz = (state.currentGridZ * 2.0f) + 1.0f;

        final PlayerRef fPlayer = playerRef;
        final GridPlayerState fState = state;

        world.execute(() -> {
            int[] netIdOut = {-1};
            Ref<EntityStore> marker = PlayerEntityController.spawnPrivateEntity(
                    world, fPlayer, "Grid_Player", wx, wy, wz, netIdOut);

            if (marker == null) {
                fPlayer.sendMessage(Message.raw("[TestFog] Failed to spawn marker — check console.").color("#FF0000"));
                return;
            }

            fState.fogMarkerRef   = marker;
            fState.fogMarkerNetId = netIdOut[0];

            System.out.println("[TestFog] Marker spawned netId=" + netIdOut[0]
                    + " pos=(" + wx + "," + wy + "," + wz + ") for " + fPlayer.getUsername());

            // Wait 200ms so entity-tracker has sent spawn packets to everyone, THEN hide from others
            final int finalNetId = netIdOut[0];
            final Ref<EntityStore> finalRef = marker;
            SCHED.schedule(() -> world.execute(() -> {
                System.out.println("[TestFog] Hiding marker from non-owners...");
                PlayerEntityController.hideEntityFromOthers(world, finalRef, fPlayer, finalNetId);
            }), 200L, TimeUnit.MILLISECONDS);

            fPlayer.sendMessage(Message.raw(
                    "[TestFog] Marker placed above NPC — should be visible only to YOU. "
                            + "Walk your NPC to test tracking.").color("#00FF7F"));
        });
    }
}