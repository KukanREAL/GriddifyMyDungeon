package com.gridifymydungeon.plugin.gridmove.commands;
import com.gridifymydungeon.plugin.debug.DebugRoleWrapper;

import com.gridifymydungeon.plugin.dnd.PlayerEntityController;
import com.gridifymydungeon.plugin.dnd.RoleManager;
import com.gridifymydungeon.plugin.gridmove.CollisionDetector;
import com.gridifymydungeon.plugin.gridmove.GridMoveManager;
import com.gridifymydungeon.plugin.gridmove.GridOverlayManager;
import com.gridifymydungeon.plugin.gridmove.GridPlayerState;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.ItemWithAllMetadata;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Set;

/**
 * /gridmove - Enable grid movement mode
 * UPDATED: Uses popup notifications instead of chat messages
 * FIX: Unfreeze + clear spell state on deactivate/activate to prevent permanent freeze after /castfinal
 */
public class GridMoveCommand extends AbstractPlayerCommand {

    private final GridMoveManager manager;
    private final CollisionDetector collisionDetector;
    private final RoleManager roleManager;
    public static final Set<Ref<EntityStore>> ALL_HOLOGRAMS = new HashSet<>();

    public GridMoveCommand(GridMoveManager manager, CollisionDetector collisionDetector, RoleManager roleManager) {
        super("gridmove", "Enable grid movement mode");
        this.manager = manager;
        this.collisionDetector = collisionDetector;
        this.roleManager = roleManager;
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {

        // BLOCK GM FROM USING THIS COMMAND
        if (DebugRoleWrapper.isGM(roleManager, playerRef)) {
            Message primary = Message.raw("GMs cannot use /gridmove!").color("#FF0000");
            Message secondary = Message.raw("Use /creature and /control instead").color("#FF6B6B");
            ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Red", 1).toPacket();
            NotificationUtil.sendNotification(
                    playerRef.getPacketHandler(), primary, secondary, icon, NotificationStyle.Default);
            System.out.println("[GridMove] [WARN] GM " + playerRef.getUsername() + " attempted to use /gridmove");
            return;
        }

        GridPlayerState state = manager.getState(playerRef);

        // Check if already active - DEACTIVATE
        if (state.npcEntity != null && state.npcEntity.isValid()) {
            Message deactivatingPrimary = Message.raw("Deactivating...").color("#FFA500");
            ItemWithAllMetadata deactivatingIcon = new ItemStack("Ingredient_Crystal_Yellow", 1).toPacket();
            NotificationUtil.sendNotification(
                    playerRef.getPacketHandler(), deactivatingPrimary, null, deactivatingIcon, NotificationStyle.Default);

            world.execute(() -> {
                // FIX 1: Clear any lingering freeze/spell state so the next /gridmove starts clean
                state.unfreeze();
                state.clearSpellCastingState();

                // Remove grid overlay if active
                if (state.gridOverlayEnabled) {
                    GridOverlayManager.removeGridOverlay(world, state);
                }

                manager.clearDirectionHolograms(world, state);
                PlayerEntityController.despawnPlayerNpc(world, state);

                Message primary = Message.raw("Deactivated!").color("#FF6347");
                ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Red", 1).toPacket();
                NotificationUtil.sendNotification(
                        playerRef.getPacketHandler(), primary, null, icon, NotificationStyle.Default);

                System.out.println("[GridMove] [INFO] " + playerRef.getUsername() + " deactivated grid movement");
            });
            return;
        }

        // ACTIVATE - Get player position
        Vector3d playerPos = getPlayerPosition(ref, store);
        if (playerPos == null) {
            Message primary = Message.raw("Failed to get your position!").color("#FF0000");
            ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Red", 1).toPacket();
            NotificationUtil.sendNotification(
                    playerRef.getPacketHandler(), primary, null, icon, NotificationStyle.Default);
            return;
        }

        // Calculate grid position
        int gridX = (int) Math.floor(playerPos.getX() / state.gridSize);
        int gridZ = (int) Math.floor(playerPos.getZ() / state.gridSize);

        // COLLISION CHECK
        if (collisionDetector.isPositionOccupied(gridX, gridZ, -1, playerRef.getUuid())) {
            String occupant = collisionDetector.getEntityNameAtPosition(gridX, gridZ);
            Message warningPrimary = Message.raw("Position occupied by: " + (occupant != null ? occupant : "entity")).color("#FFA500");
            ItemWithAllMetadata warningIcon = new ItemStack("Ingredient_Crystal_Yellow", 1).toPacket();
            NotificationUtil.sendNotification(
                    playerRef.getPacketHandler(), warningPrimary, null, warningIcon, NotificationStyle.Default);

            int[] freePos = collisionDetector.findNearestFreePosition(gridX, gridZ, 5);
            gridX = freePos[0];
            gridZ = freePos[1];

            Message infoPrimary = Message.raw("Spawning at nearest free position").color("#00BFFF");
            Message infoSecondary = Message.raw("Grid: (" + gridX + ", " + gridZ + ")").color("#FFFFFF");
            ItemWithAllMetadata infoIcon = new ItemStack("Ingredient_Crystal_Cyan", 1).toPacket();
            NotificationUtil.sendNotification(
                    playerRef.getPacketHandler(), infoPrimary, infoSecondary, infoIcon, NotificationStyle.Default);
        }

        // Crossbow check: not supported in grid combat
        try {
            Player playerComp = store.getComponent(ref, Player.getComponentType());
            if (playerComp != null) {
                Inventory inv = playerComp.getInventory();
                ItemStack hotbarItem = inv.getItemInHand();
                if (hotbarItem != null && !hotbarItem.isEmpty()) {
                    String weapId = hotbarItem.getItemId();
                    if (weapId != null && weapId.toLowerCase().startsWith("weapon_crossbow")) {
                        Message crossbowPrimary   = Message.raw("Crossbow not supported in grid combat!").color("#FF4500");
                        Message crossbowSecondary = Message.raw("Switch to a Shortbow (" + weapId + ") and try again.").color("#FF6B6B");
                        ItemWithAllMetadata crossbowIcon = new ItemStack("Ingredient_Crystal_Red", 1).toPacket();
                        NotificationUtil.sendNotification(playerRef.getPacketHandler(),
                                crossbowPrimary, crossbowSecondary, crossbowIcon, NotificationStyle.Default);
                        System.out.println("[GridMove] [WARN] " + playerRef.getUsername() + " tried /gridmove with crossbow: " + weapId);
                        return;
                    }
                }
            }
        } catch (Exception ignored) {}

        state.currentGridX = gridX;
        state.currentGridZ = gridZ;
        state.lastPlayerPosition = playerPos;
        state.noMovesMessageShown = false;

        final int finalGridX = gridX;
        final int finalGridZ = gridZ;
        final Ref<EntityStore> playerEntityRef = ref;

        world.execute(() -> {
            // FIX 1: Safety-net unfreeze so a /castfinal leftover never permanently locks the new NPC
            state.unfreeze();
            state.clearSpellCastingState();

            boolean success = PlayerEntityController.spawnPlayerNpc(
                    world, state, finalGridX, finalGridZ, playerPos.getY(), playerEntityRef);

            if (success) {
                final GridPlayerState finalState = state;
                java.util.concurrent.Executors.newSingleThreadScheduledExecutor().schedule(() ->
                                world.execute(() -> PlayerEntityController.broadcastAndStoreEquipment(
                                        world, world.getEntityStore().getStore(), playerEntityRef, finalState)),
                        500L, java.util.concurrent.TimeUnit.MILLISECONDS);

                manager.spawnDirectionHolograms(world, state);

                String movesInfo;
                if (state.hasMaxMovesSet()) {
                    movesInfo = "Max moves: " + (int) state.maxMoves + " per turn";
                } else {
                    movesInfo = "Free movement (no limits)";
                }

                Message primary = Message.raw("Grid Movement Enabled!").color("#00FFFF");
                Message secondary = Message.raw("Grid: (" + finalGridX + ", " + finalGridZ + ") | " + movesInfo).color("#FFFFFF");
                ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Cyan", 1).toPacket();
                NotificationUtil.sendNotification(
                        playerRef.getPacketHandler(), primary, secondary, icon, NotificationStyle.Default);

                System.out.println("[GridMove] [INFO] " + playerRef.getUsername() +
                        " enabled grid movement at (" + finalGridX + ", " + finalGridZ + ")");
            } else {
                Message primary = Message.raw("Failed to spawn player NPC!").color("#FF0000");
                Message secondary = Message.raw("No ground found within 15 blocks below you!").color("#FF6B6B");
                ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Red", 1).toPacket();
                NotificationUtil.sendNotification(
                        playerRef.getPacketHandler(), primary, secondary, icon, NotificationStyle.Default);
            }
        });
    }

    private Vector3d getPlayerPosition(Ref<EntityStore> ref, Store<EntityStore> store) {
        try {
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform != null) {
                return transform.getPosition();
            }
        } catch (Exception e) {
            System.err.println("[GridMove] [ERROR] Failed to get player position: " + e.getMessage());
        }
        return null;
    }
}