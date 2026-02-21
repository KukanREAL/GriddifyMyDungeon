package com.gridifymydungeon.plugin.gridmove.commands;

import com.gridifymydungeon.plugin.dnd.CombatManager;
import com.gridifymydungeon.plugin.dnd.EncounterManager;
import com.gridifymydungeon.plugin.dnd.MonsterState;
import com.gridifymydungeon.plugin.dnd.commands.CombatCommand;
import com.gridifymydungeon.plugin.gridmove.CollisionDetector;
import com.gridifymydungeon.plugin.gridmove.GridMoveManager;
import com.gridifymydungeon.plugin.gridmove.GridOverlayManager;
import com.gridifymydungeon.plugin.gridmove.GridPlayerState;
import com.gridifymydungeon.plugin.spell.SpellVisualManager;
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
import java.util.List;

/**
 * /endturn — End turn, reset moves, advance to next participant.
 *
 * On turn change (combat active):
 *   1. Removes ALL BFS movement overlays from every player.
 *   2. Removes ALL range overlays (Grid_Range) from every player.
 *   3. Spawns a fresh movement overlay ONLY for the new current participant:
 *        - Player turn  → blue Grid_Player overlay on that player's state.
 *        - Monster turn → blue Grid_Player overlay on the GM's state,
 *                         configured to the monster's position and remaining moves.
 *   Static /grid map overlays (gmMapOverlayActive = true) are preserved.
 */
public class EndTurnCommand extends AbstractPlayerCommand {

    private final GridMoveManager    manager;
    private final CombatManager      combatManager;
    private final CombatCommand      combatCommand;
    private final CollisionDetector  collisionDetector;
    private final EncounterManager   encounterManager;
    private final SpellVisualManager spellVisualManager;

    public EndTurnCommand(GridMoveManager manager, CombatManager combatManager,
                          CombatCommand combatCommand, CollisionDetector collisionDetector,
                          EncounterManager encounterManager, SpellVisualManager spellVisualManager) {
        super("endturn", "End your turn and reset moves");
        this.manager            = manager;
        this.combatManager      = combatManager;
        this.combatCommand      = combatCommand;
        this.collisionDetector  = collisionDetector;
        this.encounterManager   = encounterManager;
        this.spellVisualManager = spellVisualManager;
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {

        GridPlayerState state = manager.getState(playerRef);

        if (combatManager.isCombatActive()) {
            state.resetMoves();
            CombatManager.CombatParticipant next = combatManager.nextTurn();

            // Popup for the person who just ended their turn
            String nextName = (next != null) ? next.name : "???";
            Message primary   = Message.raw("Turn ended!").color("#90EE90");
            Message secondary = Message.raw("Now: " + nextName).color("#00BFFF");
            ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Green", 1).toPacket();
            NotificationUtil.sendNotification(playerRef.getPacketHandler(), primary, secondary, icon, NotificationStyle.Default);

            // Broadcast updated turn order to everyone
            List<CombatManager.CombatParticipant> order = combatManager.getTurnOrder();
            int currentIndex = combatManager.getCurrentTurnIndex();
            combatCommand.broadcastTurnOrder(order, currentIndex, "TURN CHANGE");

            world.execute(() -> {
                // ── Step 1: Remove ALL BFS movement overlays and range overlays ──────
                for (GridPlayerState ps : manager.getAllStates()) {
                    if (ps.gridOverlayEnabled && !ps.gmMapOverlayActive) {
                        // BFS overlay — remove it
                        GridOverlayManager.removeGridOverlay(world, ps);
                    }
                    // Always clear range overlay — it belongs to the casting phase,
                    // not to combat turns, so it shouldn't persist across turns.
                    if (ps.playerRef != null) {
                        spellVisualManager.clearRangeOverlay(ps.playerRef.getUuid(), world);
                    }
                }

                if (next == null) return;

                // ── Step 2: Spawn overlay only for the new current participant ────────

                if (next.isPlayer) {
                    // ── Player turn: find matching GridPlayerState by UUID and spawn ──
                    for (GridPlayerState ps : manager.getAllStates()) {
                        if (ps.playerRef == null) continue;
                        if (next.playerUUID != null &&
                                !ps.playerRef.getUuid().equals(next.playerUUID)) continue;
                        if (ps.npcEntity == null || !ps.npcEntity.isValid()) continue;

                        GridOverlayManager.spawnPlayerGridOverlay(
                                world, ps, collisionDetector, ps.playerRef.getUuid());
                        ps.playerRef.sendMessage(Message.raw(
                                "[Griddify] Your turn! Grid overlay active — "
                                        + formatMoves(ps.remainingMoves) + "/" + formatMoves(ps.maxMoves)
                                        + " moves").color("#00BFFF"));
                        break;
                    }

                } else {
                    // ── Monster turn: spawn BFS overlay on the GM's GridPlayerState ──
                    // Find the monster that matches the turn participant.
                    MonsterState monster = findMonsterByNumber(next.monsterNumber);
                    if (monster == null) {
                        System.out.println("[Griddify] [ENDTURN] Monster #" + next.monsterNumber
                                + " not found — skipping overlay.");
                        return;
                    }

                    // Find the GM's GridPlayerState (the one with roleManager.isGM flag is not
                    // accessible here, so we use the GM's state identified by having no npcEntity
                    // OR by finding the state whose playerRef is the GM).
                    // Simplest reliable approach: find the state that the EncounterManager
                    // associates with the controlled monster — the GM is whoever called /control.
                    // We expose it via EncounterManager.getGMPlayerRef() if available, otherwise
                    // fall back to finding the first state with a null npcEntity (GM never /gridmoves).
                    GridPlayerState gmState = findGMState();
                    if (gmState == null) {
                        System.out.println("[Griddify] [ENDTURN] No GM state found — skipping monster overlay.");
                        return;
                    }

                    // Configure the GM state to the monster's position so BFS runs from there
                    gmState.currentGridX   = monster.currentGridX;
                    gmState.currentGridZ   = monster.currentGridZ;
                    gmState.npcY           = monster.spawnY;
                    gmState.remainingMoves = monster.remainingMoves;
                    gmState.maxMoves       = monster.maxMoves;

                    GridOverlayManager.spawnGMBFSOverlay(world, gmState, collisionDetector);

                    // Notify GM
                    if (gmState.playerRef != null) {
                        gmState.playerRef.sendMessage(Message.raw(
                                "[Griddify] Monster turn: " + monster.getDisplayName()
                                        + " — " + formatMoves(monster.remainingMoves)
                                        + "/" + formatMoves(monster.maxMoves) + " moves").color("#FFA500"));
                    }

                    System.out.println("[Griddify] [ENDTURN] Spawned monster overlay for "
                            + monster.getDisplayName());
                }
            });

            System.out.println("[Griddify] [COMBAT] Turn advanced to: " + nextName);

        } else {
            // ── Not in combat: just reset moves ──────────────────────────────────
            if (state.hasMaxMovesSet()) {
                state.resetMoves();
                String movesText = formatMoves(state.remainingMoves) + "/" + formatMoves(state.maxMoves);
                Message primary2   = Message.raw("Moves reset").color("#90EE90");
                Message secondary2 = Message.raw(movesText).color("#00BFFF");
                ItemWithAllMetadata icon2 = new ItemStack("Ingredient_Crystal_Green", 1).toPacket();
                NotificationUtil.sendNotification(playerRef.getPacketHandler(), primary2, secondary2, icon2, NotificationStyle.Default);
            } else {
                Message primary2   = Message.raw("Free movement mode").color("#00BFFF");
                Message secondary2 = Message.raw("No move limits!").color("#FFFFFF");
                ItemWithAllMetadata icon2 = new ItemStack("Ingredient_Crystal_Cyan", 1).toPacket();
                NotificationUtil.sendNotification(playerRef.getPacketHandler(), primary2, secondary2, icon2, NotificationStyle.Default);
            }
        }
    }

    // -----------------------------------------------------------------------
    // HELPERS
    // -----------------------------------------------------------------------

    /**
     * Find a MonsterState by its monsterNumber from the EncounterManager.
     */
    private MonsterState findMonsterByNumber(int monsterNumber) {
        for (MonsterState ms : encounterManager.getAllMonsters().values()) {
            if (ms.monsterNumber == monsterNumber) return ms;
        }
        return null;
    }

    /**
     * Find the GM's GridPlayerState.
     * The GM is the only participant who never has an npcEntity (they don't /gridmove),
     * but they do have a playerRef. If multiple such states exist, pick the first.
     * Falls back to the state whose playerRef matches the encounter's controlled context
     * if EncounterManager exposes a GM ref.
     */
    private GridPlayerState findGMState() {
        // GM has no npcEntity (they don't /gridmove, only players do)
        for (GridPlayerState ps : manager.getAllStates()) {
            if (ps.npcEntity == null || !ps.npcEntity.isValid()) return ps;
        }
        return null;
    }

    private String formatMoves(double moves) {
        if (moves == Math.floor(moves)) return String.valueOf((int) moves);
        return String.format("%.1f", moves);
    }
}