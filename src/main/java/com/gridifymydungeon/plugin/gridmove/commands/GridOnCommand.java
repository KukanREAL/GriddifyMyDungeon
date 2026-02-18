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
 * /gridon — Show grid overlay.
 *
 * PLAYER: blue Grid_Corner_Player tiles showing BFS movement range.
 *
 * GM + /control active: grey Grid_Corner_Flat tiles showing monster movement range.
 * GM + no control:      grey 100x100 flat area map around GM position.
 *                       Barrier blocks and fluid cells are skipped.
 */
public class GridOnCommand extends AbstractPlayerCommand {

    private final GridMoveManager gridMoveManager;
    private final CollisionDetector collisionDetector;
    private final EncounterManager encounterManager;
    private final RoleManager roleManager;

    public GridOnCommand(GridMoveManager gridMoveManager, CollisionDetector collisionDetector,
                         EncounterManager encounterManager, RoleManager roleManager) {
        super("gridon", "Show grid overlay");
        this.gridMoveManager = gridMoveManager;
        this.collisionDetector = collisionDetector;
        this.encounterManager = encounterManager;
        this.roleManager = roleManager;
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {

        // ---- GM path ----
        if (roleManager.isGM(playerRef)) {
            GridPlayerState gmState = gridMoveManager.getState(playerRef);

            if (gmState.gridOverlayEnabled) {
                notify(playerRef, "Grid overlay already active", null, "#FFA500", "Ingredient_Crystal_Yellow");
                return;
            }

            MonsterState monster = encounterManager.getControlledMonster();

            if (monster != null) {
                // Monster movement range overlay
                gmState.currentGridX = monster.currentGridX;
                gmState.currentGridZ = monster.currentGridZ;
                gmState.npcY         = monster.spawnY;
                gmState.remainingMoves = monster.remainingMoves;
                gmState.maxMoves       = monster.maxMoves;

                world.execute(() -> {
                    GridOverlayManager.spawnGridOverlay(world, gmState, collisionDetector, null);
                    notify(playerRef, "Grid overlay enabled!", "Showing " + monster.getDisplayName() + "'s range",
                            "#90EE90", "Ingredient_Crystal_Green");
                });
                System.out.println("[Griddify] [GRIDON] GM monster range overlay for " + monster.getDisplayName());

            } else {
                // 100x100 area map — needs GM's current grid position
                // Use npcY if available, otherwise send a message asking for position
                world.execute(() -> {
                    GridOverlayManager.spawnGMMapOverlay(world, gmState);
                    notify(playerRef, "100x100 area map spawned!",
                            "Barriers and fluids excluded", "#90EE90", "Ingredient_Crystal_Green");
                });
                System.out.println("[Griddify] [GRIDON] GM spawned 100x100 map overlay");
            }
            return;
        }

        // ---- Player path ----
        GridPlayerState state = gridMoveManager.getState(playerRef);

        if (state.npcEntity == null || !state.npcEntity.isValid()) {
            notify(playerRef, "Activate /gridmove first!", "You need to spawn your character",
                    "#FF0000", "Ingredient_Crystal_Red");
            return;
        }

        if (state.gridOverlayEnabled) {
            notify(playerRef, "Grid overlay already active", null, "#FFA500", "Ingredient_Crystal_Yellow");
            return;
        }

        world.execute(() -> {
            GridOverlayManager.spawnPlayerGridOverlay(world, state, collisionDetector, playerRef.getUuid());
            String movesText = formatMoves(state.remainingMoves) + "/" + formatMoves(state.maxMoves);
            notify(playerRef, "Grid overlay enabled!", "Moves: " + movesText, "#90EE90", "Ingredient_Crystal_Green");
        });
        System.out.println("[Griddify] [GRIDON] " + playerRef.getUsername() + " enabled player grid overlay");
    }

    private void notify(PlayerRef p, String primary, String secondary, String color, String item) {
        Message pMsg = Message.raw(primary).color(color);
        Message sMsg = secondary != null ? Message.raw(secondary).color("#FFFFFF") : null;
        ItemWithAllMetadata icon = new ItemStack(item, 1).toPacket();
        NotificationUtil.sendNotification(p.getPacketHandler(), pMsg, sMsg, icon, NotificationStyle.Default);
    }

    private String formatMoves(double m) {
        return m == Math.floor(m) ? String.valueOf((int) m) : String.format("%.1f", m);
    }
}