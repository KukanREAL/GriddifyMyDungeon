package com.gridifymydungeon.plugin.dnd.commands;

import com.gridifymydungeon.plugin.dnd.EncounterManager;
import com.gridifymydungeon.plugin.dnd.MonsterDatabase;
import com.gridifymydungeon.plugin.dnd.MonsterState;
import com.gridifymydungeon.plugin.dnd.RoleManager;
import com.gridifymydungeon.plugin.gridmove.CollisionDetector;
import com.gridifymydungeon.plugin.gridmove.GridMoveManager;
import com.gridifymydungeon.plugin.gridmove.GridPlayerState;
import com.gridifymydungeon.plugin.gridmove.HotbarInputHandler;
import com.gridifymydungeon.plugin.gridmove.PlayerHotbarState;
import com.gridifymydungeon.plugin.gridmove.StatEditorPage;
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
 * After successful spawn, automatically opens the stat editor for that monster.
 */
public class CreatureCommand extends AbstractPlayerCommand {

    private final EncounterManager   encounterManager;
    private final RoleManager        roleManager;
    private final CollisionDetector  collisionDetector;
    private final GridMoveManager    gridMoveManager;
    private final HotbarInputHandler hotbarInputHandler;
    private final RequiredArg<String>  nameArg;
    private final RequiredArg<Integer> numberArg;

    public CreatureCommand(@Nonnull EncounterManager encounterManager,
                           @Nonnull RoleManager roleManager,
                           @Nonnull CollisionDetector collisionDetector,
                           @Nonnull GridMoveManager gridMoveManager,
                           @Nonnull HotbarInputHandler hotbarInputHandler) {
        super("creature", "Spawn a creature (GM only)");
        this.encounterManager   = encounterManager;
        this.roleManager        = roleManager;
        this.collisionDetector  = collisionDetector;
        this.gridMoveManager    = gridMoveManager;
        this.hotbarInputHandler = hotbarInputHandler;
        this.nameArg   = this.withRequiredArg("name",   "Creature name",   ArgTypes.STRING);
        this.numberArg = this.withRequiredArg("number", "Creature number", ArgTypes.INTEGER);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {

        if (!roleManager.isGM(playerRef)) {
            notify(playerRef, "#FF0000", "Only the GM can use this command!", null, "Ingredient_Crystal_Red");
            return;
        }

        String monsterName   = nameArg.get(context);
        int    monsterNumber = numberArg.get(context);

        if (monsterNumber <= 0) {
            notify(playerRef, "#FF0000", "Monster number must be greater than 0!", null, "Ingredient_Crystal_Red");
            return;
        }

        if (encounterManager.getMonster(monsterNumber) != null) {
            notify(playerRef, "#FFA500", "Monster #" + monsterNumber + " already exists!", null, "Ingredient_Crystal_Yellow");
            return;
        }

        Vector3d gmPos = getPlayerPosition(playerRef, world);
        if (gmPos == null) {
            notify(playerRef, "#FF0000", "Failed to get your position!", null, "Ingredient_Crystal_Red");
            return;
        }

        int gridX = (int) Math.floor(gmPos.getX() / 2.0);
        int gridZ = (int) Math.floor(gmPos.getZ() / 2.0);

        if (collisionDetector.isPositionOccupied(gridX, gridZ, monsterNumber)) {
            String occupant = collisionDetector.getEntityNameAtPosition(gridX, gridZ);
            notify(playerRef, "#FFA500", "Position occupied by: " + occupant, null, "Ingredient_Crystal_Yellow");

            int[] freePos = collisionDetector.findNearestFreePosition(gridX, gridZ, 5);
            gridX = freePos[0];
            gridZ = freePos[1];

            notify(playerRef, "#00BFFF", "Spawning at nearest free position",
                    "Grid: (" + gridX + ", " + gridZ + ")", "Ingredient_Crystal_Cyan");
        }

        MonsterState monster = encounterManager.addMonster(monsterName, monsterNumber);
        monster.currentGridX   = gridX;
        monster.currentGridZ   = gridZ;
        monster.lastGMPosition = gmPos;

        MonsterDatabase.MonsterStats dbStats = MonsterDatabase.getStats(monsterName);
        if (dbStats != null) {
            dbStats.applyTo(monster.stats);
            monster.maxMoves       = dbStats.moves;
            monster.remainingMoves = dbStats.moves;
            monster.isFlying       = dbStats.flying;
            monster.stats.isFlying = dbStats.flying;
            monster.monsterType    = dbStats.monsterType;
            playerRef.sendMessage(Message.raw("[Griddify] Stats loaded: HP " + dbStats.maxHP
                    + " (" + dbStats.hitDice + ")  AC " + dbStats.ac
                    + "  CR " + MonsterDatabase.formatCR(dbStats.cr10x)
                    + "  [" + dbStats.type + "]").color("#90EE90"));
        } else {
            playerRef.sendMessage(Message.raw("[Griddify] No D&D stats found for '" + monsterName
                    + "' — using defaults. Use /HP and /AC to set manually.").color("#FFA500"));
        }

        final int finalGridX        = gridX;
        final int finalGridZ        = gridZ;
        final MonsterState finalMonster = monster;

        world.execute(() -> {
            boolean success = MonsterEntityController.spawnMonster(
                    world, finalMonster, finalGridX, finalGridZ, gmPos.getY());

            if (success) {
                notify(playerRef, "#90EE90",
                        "Spawned " + finalMonster.getDisplayName(),
                        "Grid: (" + finalGridX + ", " + finalGridZ + ")",
                        "Ingredient_Crystal_Green");

                System.out.println("[Griddify] [CREATURE] GM spawned " + finalMonster.getDisplayName()
                        + " at grid (" + finalGridX + ", " + finalGridZ + ")");

                // ── Auto-open stat editor for the new monster ─────────────────
                openStatEditorForMonster(playerRef, finalMonster);

            } else {
                encounterManager.removeMonster(finalMonster.monsterNumber);
                notify(playerRef, "#FF0000",
                        "Failed to spawn " + monsterName + " #" + monsterNumber,
                        "No ground found within 15 blocks below you!",
                        "Ingredient_Crystal_Red");
                System.err.println("[Griddify] [ERROR] Failed to spawn " + finalMonster.getDisplayName());
            }
        });
    }

    // ── Opens stat editor for a freshly spawned monster ───────────────────────

    private void openStatEditorForMonster(PlayerRef playerRef, MonsterState monster) {
        GridPlayerState state = gridMoveManager.getState(playerRef);
        if (state == null) return;

        PlayerHotbarState hs = state.hotbarState;
        String subjectName = monster.getDisplayName();

        if (state.statEditorPage != null) {
            state.statEditorPage.dismiss();
            state.statEditorPage = null;
        }

        encounterManager.setControlled(monster.monsterNumber);

        state.statEditorPage = new StatEditorPage(playerRef, monster.stats, subjectName,
                // onSave
                () -> {
                    MonsterState ctrl = encounterManager.getControlledMonster();
                    if (ctrl != null) {
                        state.statEditorPage.applyTo(ctrl.stats);
                        playerRef.sendMessage(Message.raw("Saved: " + ctrl.getDisplayName()).color("#00FF7F"));
                    }
                    state.statEditorPage.dismiss();
                    state.statEditorPage = null;
                    hs.setMode(PlayerHotbarState.Mode.NONE);
                    hotbarInputHandler.refreshHudPublic(playerRef, state);
                },
                // onCancel
                () -> {
                    state.statEditorPage.dismiss();
                    state.statEditorPage = null;
                    hs.setMode(PlayerHotbarState.Mode.NONE);
                    hotbarInputHandler.refreshHudPublic(playerRef, state);
                    playerRef.sendMessage(Message.raw("Stat editor closed.").color("#AAAAAA"));
                }
        );

        state.statEditorPage.open();
        hs.setMode(PlayerHotbarState.Mode.STAT_EDIT);
        hotbarInputHandler.refreshHudPublic(playerRef, state);
        playerRef.sendMessage(Message.raw("Edit stats for " + subjectName
                + " — Save or Cancel when done.").color("#FFD700"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void notify(PlayerRef playerRef, String color, String primary,
                        String secondary, String itemId) {
        Message primaryMsg   = Message.raw(primary).color(color);
        Message secondaryMsg = secondary != null ? Message.raw(secondary).color("#FFFFFF") : null;
        ItemWithAllMetadata icon = new ItemStack(itemId, 1).toPacket();
        NotificationUtil.sendNotification(
                playerRef.getPacketHandler(), primaryMsg, secondaryMsg, icon, NotificationStyle.Default);
    }

    private Vector3d getPlayerPosition(PlayerRef playerRef, World world) {
        try {
            TransformComponent transform = world.getEntityStore().getStore().getComponent(
                    playerRef.getReference(), TransformComponent.getComponentType());
            return transform != null ? transform.getPosition() : null;
        } catch (Exception e) {
            System.err.println("[Griddify] [ERROR] Failed to get player position: " + e.getMessage());
            return null;
        }
    }
}