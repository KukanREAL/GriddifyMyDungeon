package com.gridifymydungeon.plugin.dnd.commands;

import com.gridifymydungeon.plugin.dnd.EncounterManager;
import com.gridifymydungeon.plugin.dnd.commands.MonsterEntityController;
import com.gridifymydungeon.plugin.dnd.MonsterState;
import com.gridifymydungeon.plugin.dnd.RoleManager;
import com.gridifymydungeon.plugin.gridmove.CollisionDetector;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.ItemWithAllMetadata;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;

import javax.annotation.Nonnull;

/**
 * /creature <name> <number> - Spawn a monster (GM only)
 * UPDATED: Uses popup notifications instead of chat messages
 */
public class CreatureCommand extends AbstractPlayerCommand {
    private final EncounterManager encounterManager;
    private final RoleManager roleManager;
    private final CollisionDetector collisionDetector;
    private final RequiredArg<String> nameArg;
    private final RequiredArg<Integer> numberArg;

    public CreatureCommand(EncounterManager encounterManager, RoleManager roleManager, CollisionDetector collisionDetector) {
        super("creature", "Spawn a creature (GM only)");
        this.encounterManager = encounterManager;
        this.roleManager = roleManager;
        this.collisionDetector = collisionDetector;
        this.nameArg = this.withRequiredArg("name", "Creature name", ArgTypes.STRING);
        this.numberArg = this.withRequiredArg("number", "Creature number", ArgTypes.INTEGER);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {

        // Check if GM
        if (!roleManager.isGM(playerRef)) {
            // ERROR: Not GM
            Message primary = Message.raw("Only the GM can use this command!").color("#FF0000");
            ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Red", 1).toPacket();

            NotificationUtil.sendNotification(
                    playerRef.getPacketHandler(),
                    primary,
                    null,
                    icon,
                    NotificationStyle.Default
            );
            return;
        }

        String monsterName = nameArg.get(context);
        int monsterNumber = numberArg.get(context);

        // Validate monster number
        if (monsterNumber <= 0) {
            // ERROR: Invalid number
            Message primary = Message.raw("Monster number must be greater than 0!").color("#FF0000");
            ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Red", 1).toPacket();

            NotificationUtil.sendNotification(
                    playerRef.getPacketHandler(),
                    primary,
                    null,
                    icon,
                    NotificationStyle.Default
            );
            return;
        }

        // Check if number already exists
        if (encounterManager.getMonster(monsterNumber) != null) {
            // WARNING: Monster already exists
            Message primary = Message.raw("Monster #" + monsterNumber + " already exists!").color("#FFA500");
            ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Yellow", 1).toPacket();

            NotificationUtil.sendNotification(
                    playerRef.getPacketHandler(),
                    primary,
                    null,
                    icon,
                    NotificationStyle.Default
            );
            return;
        }

        // Get GM position
        Vector3d gmPos = getPlayerPosition(playerRef, world);
        if (gmPos == null) {
            // ERROR: Failed to get position
            Message primary = Message.raw("Failed to get your position!").color("#FF0000");
            ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Red", 1).toPacket();

            NotificationUtil.sendNotification(
                    playerRef.getPacketHandler(),
                    primary,
                    null,
                    icon,
                    NotificationStyle.Default
            );
            return;
        }

        // Calculate grid position
        int gridX = (int) Math.floor(gmPos.getX() / 2.0);
        int gridZ = (int) Math.floor(gmPos.getZ() / 2.0);

        // Check collision
        if (collisionDetector.isPositionOccupied(gridX, gridZ, monsterNumber)) {
            String occupant = collisionDetector.getEntityNameAtPosition(gridX, gridZ);

            // WARNING: Position occupied
            Message warningPrimary = Message.raw("Position occupied by: " + occupant).color("#FFA500");
            ItemWithAllMetadata warningIcon = new ItemStack("Ingredient_Crystal_Yellow", 1).toPacket();

            NotificationUtil.sendNotification(
                    playerRef.getPacketHandler(),
                    warningPrimary,
                    null,
                    warningIcon,
                    NotificationStyle.Default
            );

            // Find nearest free position
            int[] freePos = collisionDetector.findNearestFreePosition(gridX, gridZ, 5);
            gridX = freePos[0];
            gridZ = freePos[1];

            // INFO: Using alternate position
            Message infoPrimary = Message.raw("Spawning at nearest free position").color("#00BFFF");
            Message infoSecondary = Message.raw("Grid: (" + gridX + ", " + gridZ + ")").color("#FFFFFF");
            ItemWithAllMetadata infoIcon = new ItemStack("Ingredient_Crystal_Cyan", 1).toPacket();

            NotificationUtil.sendNotification(
                    playerRef.getPacketHandler(),
                    infoPrimary,
                    infoSecondary,
                    infoIcon,
                    NotificationStyle.Default
            );
        }

        // Create monster state
        MonsterState monster = encounterManager.addMonster(monsterName, monsterNumber);
        monster.currentGridX = gridX;
        monster.currentGridZ = gridZ;
        monster.lastGMPosition = gmPos;

        // Spawn monster entity in world
        final int finalGridX = gridX;
        final int finalGridZ = gridZ;

        world.execute(() -> {
            boolean success = MonsterEntityController.spawnMonster(world, monster, finalGridX, finalGridZ, gmPos.getY());

            if (success) {
                // SUCCESS: Monster spawned
                Message primary = Message.raw("Spawned " + monster.getDisplayName()).color("#90EE90");
                Message secondary = Message.raw("Grid: (" + finalGridX + ", " + finalGridZ + ")").color("#FFFFFF");
                ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Green", 1).toPacket();

                NotificationUtil.sendNotification(
                        playerRef.getPacketHandler(),
                        primary,
                        secondary,
                        icon,
                        NotificationStyle.Default
                );

                System.out.println("[Griddify] [CREATURE] GM spawned " + monster.getDisplayName() +
                        " at grid (" + finalGridX + ", " + finalGridZ + ") with entity");
            } else {
                // ERROR: Spawn failed
                encounterManager.removeMonster(monsterNumber);

                Message primary = Message.raw("Failed to spawn " + monsterName + " #" + monsterNumber).color("#FF0000");
                Message secondary = Message.raw("No ground found within 15 blocks below you!").color("#FF6B6B");
                ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Red", 1).toPacket();

                NotificationUtil.sendNotification(
                        playerRef.getPacketHandler(),
                        primary,
                        secondary,
                        icon,
                        NotificationStyle.Default
                );

                System.err.println("[Griddify] [ERROR] Failed to spawn " + monster.getDisplayName() + " - no ground found");
            }
        });
    }

    /**
     * Get player position
     */
    private Vector3d getPlayerPosition(PlayerRef playerRef, World world) {
        try {
            TransformComponent transform = world.getEntityStore().getStore().getComponent(
                    playerRef.getReference(),
                    TransformComponent.getComponentType()
            );
            return transform != null ? transform.getPosition() : null;
        } catch (Exception e) {
            System.err.println("[Griddify] [ERROR] Failed to get player position: " + e.getMessage());
            return null;
        }
    }
}