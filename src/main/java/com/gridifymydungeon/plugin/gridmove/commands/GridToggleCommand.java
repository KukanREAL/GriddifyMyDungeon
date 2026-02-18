package com.gridifymydungeon.plugin.gridmove.commands;

import com.gridifymydungeon.plugin.dnd.EncounterManager;
import com.gridifymydungeon.plugin.dnd.MonsterState;
import com.gridifymydungeon.plugin.dnd.RoleManager;
import com.gridifymydungeon.plugin.gridmove.CollisionDetector;
import com.gridifymydungeon.plugin.gridmove.GridMoveManager;
import com.gridifymydungeon.plugin.gridmove.GridOverlayManager;
import com.gridifymydungeon.plugin.gridmove.GridPlayerState;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.ItemWithAllMetadata;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;

import javax.annotation.Nonnull;

/**
 * /grid — Toggle GM 100×100 flat map overlay on/off.
 *
 * First use: spawns a 100×100 grid centred on the GM / controlled monster position.
 *            The map FOLLOWS the monster as the GM moves it (re-centres on each grid step).
 *            Use /gridoff or /grid again to remove it.
 * Second use: removes the grid overlay.
 *
 * Compare with /gridon which shows the BFS movement-range highlight (same as players).
 * Only available to GMs.
 */
public class GridToggleCommand extends AbstractPlayerCommand {

    private final GridMoveManager gridMoveManager;
    private final CollisionDetector collisionDetector;
    private final EncounterManager encounterManager;
    private final RoleManager roleManager;

    public GridToggleCommand(GridMoveManager gridMoveManager, CollisionDetector collisionDetector,
                             EncounterManager encounterManager, RoleManager roleManager) {
        super("grid", "Toggle GM 100x100 map overlay (follows monster, use /grid again to remove)");
        this.gridMoveManager = gridMoveManager;
        this.collisionDetector = collisionDetector;
        this.encounterManager = encounterManager;
        this.roleManager = roleManager;
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {

        if (!roleManager.isGM(playerRef)) {
            notify(playerRef, "GM only command!", "Only the GM can toggle the grid overlay",
                    "#FF0000", "Ingredient_Crystal_Red");
            return;
        }

        GridPlayerState gmState = gridMoveManager.getState(playerRef);

        // --- Toggle OFF ---
        if (gmState.gridOverlayEnabled) {
            world.execute(() -> {
                GridOverlayManager.removeGridOverlay(world, gmState);
                notify(playerRef, "Grid overlay disabled!", null, "#FF6347", "Ingredient_Crystal_Red");
            });
            System.out.println("[Griddify] [GRID] GM " + playerRef.getUsername() + " toggled grid OFF");
            return;
        }

        // --- Toggle ON ---
        MonsterState monster = encounterManager.getControlledMonster();

        if (monster != null) {
            // Centre map on the controlled monster — it will follow as the monster moves
            gmState.currentGridX = monster.currentGridX;
            gmState.currentGridZ = monster.currentGridZ;
            gmState.npcY         = monster.spawnY;
            gmState.remainingMoves = monster.remainingMoves;
            gmState.maxMoves       = monster.maxMoves;

            world.execute(() -> {
                GridOverlayManager.spawnGMMapOverlay(world, gmState);
                notify(playerRef, "100x100 map enabled!", "Follows " + monster.getDisplayName() + " as it moves",
                        "#90EE90", "Ingredient_Crystal_Green");
            });
            System.out.println("[Griddify] [GRID] GM toggled ON 100x100 map (monster: " + monster.getDisplayName() + ")");
        } else {
            // No monster — centre on GM's current position
            world.execute(() -> {
                try {
                    com.hypixel.hytale.server.core.modules.entity.component.TransformComponent transform =
                            store.getComponent(ref, com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType());
                    if (transform != null) {
                        com.hypixel.hytale.math.vector.Vector3d pos = transform.getPosition();
                        gmState.currentGridX = (int) Math.floor(pos.getX() / 2.0);
                        gmState.currentGridZ = (int) Math.floor(pos.getZ() / 2.0);
                        gmState.npcY         = (float) pos.getY();
                    }
                } catch (Exception ignored) {}

                GridOverlayManager.spawnGMMapOverlay(world, gmState);
                notify(playerRef, "100x100 map enabled!", "Use /grid again to remove.",
                        "#90EE90", "Ingredient_Crystal_Green");
            });
            System.out.println("[Griddify] [GRID] GM " + playerRef.getUsername() + " toggled ON 100x100 map (no monster)");
        }
    }

    private void notify(PlayerRef p, String primary, String secondary, String color, String item) {
        Message pMsg = Message.raw(primary).color(color);
        Message sMsg = secondary != null ? Message.raw(secondary).color("#FFFFFF") : null;
        ItemWithAllMetadata icon = new ItemStack(item, 1).toPacket();
        NotificationUtil.sendNotification(p.getPacketHandler(), pMsg, sMsg, icon, NotificationStyle.Default);
    }
}