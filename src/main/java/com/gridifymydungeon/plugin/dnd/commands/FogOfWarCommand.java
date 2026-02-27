package com.gridifymydungeon.plugin.dnd.commands;

import com.gridifymydungeon.plugin.dnd.PlayerEntityController;
import com.gridifymydungeon.plugin.dnd.RoleManager;
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
 * /FogOfWar — GM-only toggle.
 *
 * ON:  Spawns a private "Fog_Of_War" entity at each registered player's NPC position
 *      (ground level, same height as the player model). Only the owning player sees it.
 *      Follows the NPC on every grid step (PlayerPositionTracker.moveFogMarker).
 *
 * OFF: Removes all active fog markers.
 *
 * Auto-activates when /combat is started if fog was previously enabled.
 */
public class FogOfWarCommand extends AbstractPlayerCommand {

    private static final ScheduledExecutorService SCHED =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "fogofwar-scheduler");
                t.setDaemon(true);
                return t;
            });

    static final String FOG_MODEL = "Fog_Of_War";

    private final GridMoveManager gridMoveManager;
    private final RoleManager     roleManager;

    public FogOfWarCommand(GridMoveManager gridMoveManager, RoleManager roleManager) {
        super("FogOfWar", "Toggle fog-of-war markers for all players (GM only)");
        this.gridMoveManager = gridMoveManager;
        this.roleManager     = roleManager;
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {

        if (!roleManager.isGM(playerRef)) {
            playerRef.sendMessage(Message.raw("[FogOfWar] Only the GM can use this command.").color("#FF0000"));
            return;
        }

        if (gridMoveManager.isFogOfWarActive()) {
            // ── TURN OFF ──────────────────────────────────────────────────────
            gridMoveManager.setFogOfWarActive(false);
            removeAllFogMarkers(world);
            playerRef.sendMessage(Message.raw("[FogOfWar] Fog of War DISABLED.").color("#FFA500"));
            System.out.println("[FogOfWar] Disabled by GM " + playerRef.getUsername());
        } else {
            // ── TURN ON ───────────────────────────────────────────────────────
            gridMoveManager.setFogOfWarActive(true);
            spawnAllFogMarkers(world);
            playerRef.sendMessage(Message.raw("[FogOfWar] Fog of War ENABLED — markers spawned for all active players.").color("#00FF7F"));
            System.out.println("[FogOfWar] Enabled by GM " + playerRef.getUsername());
        }
    }

    // ── Package-visible helpers (called from CombatCommand) ───────────────

    /**
     * Spawn fog markers for every player that has an active NPC.
     * Call inside world.execute() or schedule the inner work yourself.
     */
    public void spawnAllFogMarkers(World world) {
        for (GridPlayerState state : gridMoveManager.getAllStates()) {
            if (state.npcEntity == null || !state.npcEntity.isValid()) continue;
            if (state.playerRef == null) continue;
            spawnMarkerForState(world, state);
        }
    }

    /** Remove all active fog markers across all player states. */
    public void removeAllFogMarkers(World world) {
        for (GridPlayerState state : gridMoveManager.getAllStates()) {
            removeFogMarker(world, state);
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private void spawnMarkerForState(World world, GridPlayerState state) {
        // Remove any stale marker first
        removeFogMarker(world, state);

        float wx = (state.currentGridX * 2.0f) + 1.0f;
        float wy = state.npcY + 2.0f;  // player body level (2 blocks above NPC feet)
        float wz = (state.currentGridZ * 2.0f) + 1.0f;

        final GridPlayerState fState  = state;
        final PlayerRef       fPlayer = state.playerRef;

        world.execute(() -> {
            int[] netIdOut = {-1};
            Ref<EntityStore> marker = PlayerEntityController.spawnPrivateEntity(
                    world, fPlayer, FOG_MODEL, wx, wy, wz, netIdOut);

            if (marker == null) {
                System.err.println("[FogOfWar] Failed to spawn marker for " + fPlayer.getUsername());
                return;
            }

            fState.fogMarkerRef   = marker;
            fState.fogMarkerNetId = netIdOut[0];
            System.out.println("[FogOfWar] Marker spawned for " + fPlayer.getUsername()
                    + " netId=" + netIdOut[0] + " pos=(" + wx + "," + wy + "," + wz + ")");

            // Hide from everyone except owner after entity-tracker has had time to broadcast
            final int finalNetId = netIdOut[0];
            final Ref<EntityStore> finalRef = marker;
            SCHED.schedule(() -> world.execute(() -> {
                PlayerEntityController.hideEntityFromOthers(world, finalRef, fPlayer, finalNetId);
                System.out.println("[FogOfWar] Marker hidden from non-owners for " + fPlayer.getUsername());
            }), 200L, TimeUnit.MILLISECONDS);
        });
    }

    private static void removeFogMarker(World world, GridPlayerState state) {
        if (state.fogMarkerRef == null || !state.fogMarkerRef.isValid()) return;
        final Ref<EntityStore> old = state.fogMarkerRef;
        state.fogMarkerRef   = null;
        state.fogMarkerNetId = -1;
        world.execute(() -> {
            try { world.getEntityStore().getStore().removeEntity(old, RemoveReason.REMOVE); }
            catch (Exception ignored) {}
        });
    }
}