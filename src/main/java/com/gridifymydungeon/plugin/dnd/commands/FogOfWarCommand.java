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
 * ON:  Spawns TWO private fog entities at each player's NPC position:
 *        Inner layer: scale 5.5f  →  11×11 tiles (5 grids radius)
 *        Outer layer: scale 6.5f  →  13×13 tiles (6 grids radius)
 *      Only the owning player sees them. Follow the NPC on every grid step.
 *
 * OFF: Removes all active fog markers.
 *
 * FIX #6: Was a single 3×3 fog entity. Now spawns inner 11×11 + outer 13×13 layers.
 */
public class FogOfWarCommand extends AbstractPlayerCommand {

    private static final ScheduledExecutorService SCHED =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "fogofwar-scheduler");
                t.setDaemon(true);
                return t;
            });

    static final String FOG_MODEL = "Fog_Of_War";

    // FIX #6: Two fog layer scales
    private static final float INNER_SCALE = 5.5f; // 11×11 tiles
    private static final float OUTER_SCALE = 6.5f; // 13×13 tiles

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
            // ── TURN OFF ─────────────────────────────────────────────────────────
            gridMoveManager.setFogOfWarActive(false);
            removeAllFogMarkers(world);
            playerRef.sendMessage(Message.raw("[FogOfWar] Fog of War DISABLED.").color("#FFA500"));
            System.out.println("[FogOfWar] Disabled by GM " + playerRef.getUsername());
        } else {
            // ── TURN ON ──────────────────────────────────────────────────────────
            gridMoveManager.setFogOfWarActive(true);
            spawnAllFogMarkers(world);
            playerRef.sendMessage(Message.raw(
                    "[FogOfWar] Fog of War ENABLED — inner 11×11 + outer 13×13 per player.").color("#00FF7F"));
            System.out.println("[FogOfWar] Enabled by GM " + playerRef.getUsername());
        }
    }

    // ── Package-visible helpers (called from CombatCommand) ──────────────────

    /**
     * Spawn inner + outer fog markers for every player with an active NPC.
     */
    public void spawnAllFogMarkers(World world) {
        for (GridPlayerState state : gridMoveManager.getAllStates()) {
            if (state.npcEntity == null || !state.npcEntity.isValid()) continue;
            if (state.playerRef == null) continue;
            spawnMarkersForState(world, state);
        }
    }

    /** Remove all active fog markers across all player states. */
    public void removeAllFogMarkers(World world) {
        for (GridPlayerState state : gridMoveManager.getAllStates()) {
            removeFogMarkers(world, state);
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * FIX #6: Spawn two fog entities per player — inner 11×11 and outer 13×13.
     */
    private void spawnMarkersForState(World world, GridPlayerState state) {
        removeFogMarkers(world, state);

        float wx = (state.currentGridX * 2.0f) + 1.0f;
        float wy = state.npcY + 2.0f;  // player body level
        float wz = (state.currentGridZ * 2.0f) + 1.0f;

        final GridPlayerState fState  = state;
        final PlayerRef       fPlayer = state.playerRef;

        world.execute(() -> {
            // ── Inner 11×11 marker ─────────────────────────────────────────────
            int[] innerNetId = {-1};
            Ref<EntityStore> innerMarker = spawnScaledPrivate(world, fPlayer, FOG_MODEL, wx, wy, wz,
                    innerNetId, INNER_SCALE);

            if (innerMarker == null) {
                System.err.println("[FogOfWar] Failed to spawn inner marker for " + fPlayer.getUsername());
            } else {
                fState.fogMarkerRef   = innerMarker;
                fState.fogMarkerNetId = innerNetId[0];
                System.out.println("[FogOfWar] Inner marker spawned for " + fPlayer.getUsername()
                        + " netId=" + innerNetId[0] + " scale=" + INNER_SCALE);

                final int fNetId = innerNetId[0];
                final Ref<EntityStore> fRef = innerMarker;
                SCHED.schedule(() -> world.execute(() -> {
                    PlayerEntityController.hideEntityFromOthers(world, fRef, fPlayer, fNetId);
                    System.out.println("[FogOfWar] Inner marker hidden from non-owners for " + fPlayer.getUsername());
                }), 200L, TimeUnit.MILLISECONDS);
            }

            // ── Outer 13×13 marker ─────────────────────────────────────────────
            int[] outerNetId = {-1};
            Ref<EntityStore> outerMarker = spawnScaledPrivate(world, fPlayer, FOG_MODEL, wx, wy, wz,
                    outerNetId, OUTER_SCALE);

            if (outerMarker == null) {
                System.err.println("[FogOfWar] Failed to spawn outer marker for " + fPlayer.getUsername());
            } else {
                fState.fogMarkerRef2   = outerMarker;
                fState.fogMarkerNetId2 = outerNetId[0];
                System.out.println("[FogOfWar] Outer marker spawned for " + fPlayer.getUsername()
                        + " netId=" + outerNetId[0] + " scale=" + OUTER_SCALE);

                final int fNetId2 = outerNetId[0];
                final Ref<EntityStore> fRef2 = outerMarker;
                SCHED.schedule(() -> world.execute(() -> {
                    PlayerEntityController.hideEntityFromOthers(world, fRef2, fPlayer, fNetId2);
                    System.out.println("[FogOfWar] Outer marker hidden from non-owners for " + fPlayer.getUsername());
                }), 200L, TimeUnit.MILLISECONDS);
            }
        });
    }

    /**
     * FIX #6: Spawn a scaled private entity (used to create differently-sized fog layers).
     * Mirrors PlayerEntityController.spawnPrivateEntity() but accepts a custom scale.
     */
    private static Ref<EntityStore> spawnScaledPrivate(World world, PlayerRef targetPlayer,
                                                       String modelAssetId, float x, float y, float z,
                                                       int[] netIdOut, float scale) {
        try {
            com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset asset =
                    com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset.getAssetMap().getAsset(modelAssetId);
            if (asset == null) {
                System.err.println("[FogOfWar] spawnScaledPrivate: model not found: " + modelAssetId);
                return null;
            }

            com.hypixel.hytale.server.core.asset.type.model.config.Model model =
                    com.hypixel.hytale.server.core.asset.type.model.config.Model.createScaledModel(asset, scale);

            Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store =
                    world.getEntityStore().getStore();

            com.hypixel.hytale.component.Holder<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> holder =
                    com.hypixel.hytale.server.core.universe.world.storage.EntityStore.REGISTRY.newHolder();

            holder.addComponent(
                    com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType(),
                    new com.hypixel.hytale.server.core.modules.entity.component.TransformComponent(
                            new com.hypixel.hytale.math.vector.Vector3d(x, y, z),
                            new com.hypixel.hytale.math.vector.Vector3f(0, 0, 0)));

            holder.addComponent(
                    com.hypixel.hytale.server.core.modules.entity.component.ModelComponent.getComponentType(),
                    new com.hypixel.hytale.server.core.modules.entity.component.ModelComponent(model));

            holder.addComponent(
                    com.hypixel.hytale.server.core.modules.entity.component.PersistentModel.getComponentType(),
                    new com.hypixel.hytale.server.core.modules.entity.component.PersistentModel(model.toReference()));

            int netId = store.getExternalData().takeNextNetworkId();
            if (netIdOut != null && netIdOut.length > 0) netIdOut[0] = netId;
            holder.addComponent(
                    com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId.getComponentType(),
                    new com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId(netId));

            holder.addComponent(
                    com.hypixel.hytale.server.core.modules.entity.component.BoundingBox.getComponentType(),
                    new com.hypixel.hytale.server.core.modules.entity.component.BoundingBox(model.getBoundingBox()));

            com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> ref =
                    store.addEntity(holder, com.hypixel.hytale.component.AddReason.SPAWN);

            System.out.println("[FogOfWar] spawnScaledPrivate OK netId=" + netId
                    + " model=" + modelAssetId + " scale=" + scale);
            return ref;

        } catch (Exception e) {
            System.err.println("[FogOfWar] spawnScaledPrivate failed: " + e.getMessage());
            return null;
        }
    }

    /** Remove both fog markers from a player state. */
    private static void removeFogMarkers(World world, GridPlayerState state) {
        if (state.fogMarkerRef != null && state.fogMarkerRef.isValid()) {
            final Ref<EntityStore> old = state.fogMarkerRef;
            state.fogMarkerRef   = null;
            state.fogMarkerNetId = -1;
            world.execute(() -> {
                try { world.getEntityStore().getStore().removeEntity(old, RemoveReason.REMOVE); }
                catch (Exception ignored) {}
            });
        }
        if (state.fogMarkerRef2 != null && state.fogMarkerRef2.isValid()) {
            final Ref<EntityStore> old2 = state.fogMarkerRef2;
            state.fogMarkerRef2   = null;
            state.fogMarkerNetId2 = -1;
            world.execute(() -> {
                try { world.getEntityStore().getStore().removeEntity(old2, RemoveReason.REMOVE); }
                catch (Exception ignored) {}
            });
        }
    }
}