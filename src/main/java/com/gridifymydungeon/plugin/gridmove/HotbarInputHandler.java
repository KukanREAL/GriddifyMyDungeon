package com.gridifymydungeon.plugin.gridmove;

import com.gridifymydungeon.plugin.debug.DebugRoleWrapper;
import com.gridifymydungeon.plugin.dnd.EncounterManager;
import com.gridifymydungeon.plugin.dnd.MonsterState;
import com.gridifymydungeon.plugin.dnd.PlayerEntityController;
import com.gridifymydungeon.plugin.dnd.RoleManager;
import com.gridifymydungeon.plugin.spell.SpellCastingState;
import com.gridifymydungeon.plugin.spell.SpellData;
import com.gridifymydungeon.plugin.spell.SpellVisualManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.protocol.packets.inventory.SetActiveSlot;
import com.hypixel.hytale.protocol.packets.player.ClientMovement;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketFilter;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hotbar key bindings (slot 0-based, key = slot+1):
 *
 *   Key 1 (slot 0) — Cancel / deactivate. Freezes NPC. Cancels active cast.
 *                    GM: also releases monster control.
 *   Key 2 (slot 1) — Move. Player: spawn NPC if not yet spawned; re-activate move if frozen.
 *                    GM: auto-control monster at current grid cell.
 *   Key 3 (slot 2) — Spell select. Shows list; crouch scrolls through it.
 *   Key 4 (slot 3) — Confirm spell. Crouch fires /cast <selected spell>.
 *   Key 5 (slot 4) — Targeting. Crouch fires /casttarget.
 *   Key 6 (slot 5) — Cast final. Crouch fires /castfinal.
 *   Key 7 (slot 6) — Profile (prints to chat).
 *   Key 8 (slot 7) — Spell list (prints to chat).
 *   Key 9 (slot 8) — End turn (/endturn).
 *
 * No CustomUI — all feedback via chat messages.
 */
public class HotbarInputHandler implements PlayerPacketFilter {

    private static final int SLOT_CANCEL       = 0;  // key 1
    private static final int SLOT_MOVE         = 1;  // key 2
    private static final int SLOT_SPELL_SELECT = 2;  // key 3
    private static final int SLOT_CONFIRM      = 3;  // key 4 — confirm spell, crouch = /cast
    private static final int SLOT_TARGETING    = 4;  // key 5 — crouch = /casttarget
    private static final int SLOT_CAST_FINAL   = 5;  // key 6 — crouch = /castfinal
    private static final int SLOT_PROFILE      = 6;  // key 7
    private static final int SLOT_LIST_SPELLS  = 7;  // key 8
    private static final int SLOT_END_TURN     = 8;  // key 9
    private static final int SLOT_MAX_CUSTOM   = 8;

    private final GridMoveManager    gridManager;
    private final EncounterManager   encounterManager;
    private final SpellVisualManager spellVisualManager;
    private final RoleManager        roleManager;
    private final CollisionDetector  collisionDetector;

    private final Map<UUID, Boolean> wasSneaking = new HashMap<>();

    public HotbarInputHandler(GridMoveManager gridManager, EncounterManager encounterManager,
                              SpellVisualManager spellVisualManager, RoleManager roleManager,
                              CollisionDetector collisionDetector) {
        this.gridManager        = gridManager;
        this.encounterManager   = encounterManager;
        this.spellVisualManager = spellVisualManager;
        this.roleManager        = roleManager;
        this.collisionDetector  = collisionDetector;
    }

    // ── PacketFilter ──────────────────────────────────────────────────────────

    @Override
    public boolean test(@Nonnull PlayerRef playerRef, @Nonnull Packet packet) {
        if (!roleManager.hasRole(playerRef)) return false;

        if (packet instanceof SyncInteractionChains syncPacket) {
            for (SyncInteractionChain chain : syncPacket.updates) {
                if (chain.interactionType == InteractionType.SwapFrom
                        && chain.data != null && chain.initial) {
                    int target = chain.data.targetSlot;
                    if (target > SLOT_MAX_CUSTOM) return false;
                    resyncSlot(playerRef, chain.activeHotbarSlot);
                    handleSlotPress(playerRef, target);
                    return true;
                }
            }
        }

        if (packet instanceof ClientMovement cm) {
            boolean now  = cm.movementStates != null && cm.movementStates.crouching;
            boolean prev = wasSneaking.getOrDefault(playerRef.getUuid(), false);
            wasSneaking.put(playerRef.getUuid(), now);
            if (now && !prev) return handleCrouch(playerRef);
        }

        return false;
    }

    // ── Slot press ────────────────────────────────────────────────────────────

    private void handleSlotPress(PlayerRef playerRef, int slot) {
        Ref<EntityStore> entityRef = playerRef.getReference();
        if (entityRef == null || !entityRef.isValid()) return;
        Store<EntityStore> store = entityRef.getStore();
        World world = store.getExternalData().getWorld();

        world.execute(() -> {
            GridPlayerState state = gridManager.getState(playerRef);
            if (state == null) return;
            PlayerHotbarState hs = state.hotbarState;
            boolean isGM = DebugRoleWrapper.isGM(roleManager, playerRef);

            // Pressing any key except 1 and 2 while in MOVE mode freezes the NPC automatically
            if (slot != SLOT_CANCEL && slot != SLOT_MOVE
                    && hs.activeMode == PlayerHotbarState.Mode.MOVE
                    && !isGM
                    && state.npcEntity != null && state.npcEntity.isValid()) {
                state.freeze("hotbar-switch");
                chat(playerRef, "#AAAAAA", "NPC frozen. Press [2] from its cell to move again.");
            }

            switch (slot) {

                // ── Key 1: cancel / freeze ────────────────────────────────────
                case SLOT_CANCEL:
                    if (isGM) {
                        MonsterState controlled = encounterManager.getControlledMonster();
                        if (controlled != null) {
                            encounterManager.releaseControl();
                            chat(playerRef, "#FFA500", "Released: " + controlled.getDisplayName());
                        }
                    }
                    if (state.getSpellCastingState() != null || state.hasActiveCustomCast()) {
                        CommandManager.get().handleCommand(playerRef, "CastCancel");
                        chat(playerRef, "#AAAAAA", "Cast cancelled.");
                    }
                    state.freeze("key1");
                    hs.setMode(PlayerHotbarState.Mode.NONE);
                    chat(playerRef, "#AAAAAA", "[1] NPC frozen. Press [2] to move again.");
                    break;

                // ── Key 2: move ───────────────────────────────────────────────
                case SLOT_MOVE:
                    if (isGM) {
                        handleGmMove(playerRef, state, world);
                    } else {
                        handlePlayerMove(playerRef, state, world, entityRef, store);
                    }
                    break;

                // ── Key 3: spell select ───────────────────────────────────────
                case SLOT_SPELL_SELECT: {
                    List<SpellData> spells = GriddifyHud.buildSpellList(state, encounterManager, roleManager);
                    hs.spellList        = spells;
                    hs.spellSelectIndex = 0;
                    hs.spellConfirmed   = false;
                    hs.setMode(PlayerHotbarState.Mode.SPELL_SELECT);
                    if (spells.isEmpty()) {
                        chat(playerRef, "#FF0000", "No spells available — use /GridClass first.");
                    } else {
                        chat(playerRef, "#AAAAAA", "--- SELECT SPELL ---");
                        printSpellCursor(playerRef, hs);
                        chat(playerRef, "#AAAAAA", "Crouch: scroll  [4]: confirm spell");
                    }
                    break;
                }

                // ── Key 4: confirm spell (crouch fires /cast) ─────────────────
                case SLOT_CONFIRM: {
                    SpellData spell = hs.getSelectedSpell();
                    if (spell == null) {
                        chat(playerRef, "#FF0000", "No spell selected — use [3] first.");
                    } else {
                        hs.setMode(PlayerHotbarState.Mode.CONFIRM);
                        chat(playerRef, "#FFD700", "[4] Ready: " + spell.getName()
                                + " — crouch to cast.");
                    }
                    break;
                }

                // ── Key 5: targeting (crouch fires /casttarget) ───────────────
                case SLOT_TARGETING: {
                    SpellCastingState cast = state.getSpellCastingState();
                    if (cast == null && !state.hasActiveCustomCast()) {
                        chat(playerRef, "#FF0000", "No active spell — confirm with [4]+crouch first.");
                    } else {
                        hs.setMode(PlayerHotbarState.Mode.TARGETING);
                        int confirmed = cast != null ? cast.getConfirmedTargetCount() : 0;
                        int max       = cast != null ? cast.getSpell().getMaxTargets() : 1;
                        chat(playerRef, "#87CEEB", "[5] TARGETING (" + confirmed + "/" + max
                                + ") — crouch to confirm cell.");
                    }
                    break;
                }

                // ── Key 6: cast final (crouch fires /castfinal) ───────────────
                case SLOT_CAST_FINAL: {
                    SpellCastingState cast = state.getSpellCastingState();
                    if (cast == null && !state.hasActiveCustomCast()) {
                        chat(playerRef, "#FF0000", "No active spell — confirm with [4]+crouch first.");
                    } else {
                        hs.setMode(PlayerHotbarState.Mode.CAST_FINAL);
                        String spellName = cast != null ? cast.getSpell().getName() : "custom";
                        chat(playerRef, "#FF4500", "[6] ARMED: " + spellName + " — crouch to FIRE!");
                    }
                    break;
                }

                // ── Key 7: profile ────────────────────────────────────────────
                case SLOT_PROFILE:
                    printProfile(playerRef, state);
                    break;

                // ── Key 8: spell list ─────────────────────────────────────────
                case SLOT_LIST_SPELLS:
                    printSpellList(playerRef, state);
                    break;

                // ── Key 9: end turn ───────────────────────────────────────────
                case SLOT_END_TURN:
                    CommandManager.get().handleCommand(playerRef, "endturn");
                    chat(playerRef, "#FFA500", "Turn ended.");
                    break;
            }
        });
    }

    // ── Crouch ────────────────────────────────────────────────────────────────

    private boolean handleCrouch(PlayerRef playerRef) {
        Ref<EntityStore> entityRef = playerRef.getReference();
        if (entityRef == null || !entityRef.isValid()) return false;
        Store<EntityStore> store = entityRef.getStore();
        World world = store.getExternalData().getWorld();
        GridPlayerState state = gridManager.getState(playerRef);
        if (state == null) return false;
        PlayerHotbarState hs = state.hotbarState;

        switch (hs.activeMode) {

            // Crouch in SPELL_SELECT scrolls to next spell
            case SPELL_SELECT:
                world.execute(() -> {
                    hs.advanceSpellIndex();
                    printSpellCursor(playerRef, hs);
                });
                return true;

            // Crouch in CONFIRM fires /cast <spell>
            case CONFIRM:
                world.execute(() -> {
                    SpellData spell = hs.getSelectedSpell();
                    if (spell == null) {
                        chat(playerRef, "#FF0000", "No spell selected — press [3] first.");
                        return;
                    }
                    CommandManager.get().handleCommand(playerRef, "Cast " + spell.getName());
                    hs.spellConfirmed = true;
                    hs.setMode(PlayerHotbarState.Mode.TARGETING);
                    chat(playerRef, "#FFD700", "Casting: " + spell.getName()
                            + " — [5] to target, [6] to fire.");
                });
                return true;

            // Crouch in TARGETING fires /casttarget
            case TARGETING:
                world.execute(() -> {
                    CommandManager.get().handleCommand(playerRef, "CastTarget");
                    SpellCastingState cast = state.getSpellCastingState();
                    if (cast != null) {
                        int confirmed = cast.getConfirmedTargetCount();
                        int max = cast.getSpell().getMaxTargets();
                        chat(playerRef, "#87CEEB", "Target confirmed (" + confirmed + "/" + max
                                + "). Crouch again or [6]+crouch to fire.");
                    }
                });
                return true;

            // Crouch in CAST_FINAL fires /castfinal
            case CAST_FINAL:
                world.execute(() -> {
                    CommandManager.get().handleCommand(playerRef, "CastFinal");
                    hs.setMode(PlayerHotbarState.Mode.NONE);
                    chat(playerRef, "#FF4500", "Fired!");
                });
                return true;

            default:
                return false;
        }
    }

    // ── Player key-2: spawn NPC or re-activate move ───────────────────────────

    private void handlePlayerMove(PlayerRef playerRef, GridPlayerState state, World world,
                                  Ref<EntityStore> entityRef, Store<EntityStore> store) {
        // NPC already exists — only unfreeze if player is standing on same grid cell as NPC
        if (state.npcEntity != null && state.npcEntity.isValid()) {
            Vector3d pos = getPosition(entityRef, store);
            if (pos == null) {
                chat(playerRef, "#FF0000", "Could not read position!");
                return;
            }
            int playerGridX = (int) Math.floor(pos.getX() / state.gridSize);
            int playerGridZ = (int) Math.floor(pos.getZ() / state.gridSize);
            if (playerGridX == state.currentGridX && playerGridZ == state.currentGridZ) {
                state.unfreeze();
                state.hotbarState.setMode(PlayerHotbarState.Mode.MOVE);
                chat(playerRef, "#00FFFF", "[2] Move mode — walk to move. [1] to freeze.");
            } else {
                chat(playerRef, "#FFA500", "Walk to your NPC first (grid "
                        + state.currentGridX + ", " + state.currentGridZ + "), then press [2].");
            }
            return;
        }

        // No NPC yet — spawn one
        Vector3d pos = getPosition(entityRef, store);
        if (pos == null) {
            chat(playerRef, "#FF0000", "Could not read position!");
            return;
        }

        // Crossbow guard
        try {
            Player pc = store.getComponent(entityRef, Player.getComponentType());
            if (pc != null) {
                com.hypixel.hytale.server.core.inventory.ItemStack held = pc.getInventory().getItemInHand();
                if (held != null && !held.isEmpty()) {
                    String id = held.getItemId();
                    if (id != null && id.toLowerCase().startsWith("weapon_crossbow")) {
                        chat(playerRef, "#FF4500", "Crossbow not supported — switch weapon first.");
                        return;
                    }
                }
            }
        } catch (Exception ignored) {}

        int gridX = (int) Math.floor(pos.getX() / state.gridSize);
        int gridZ = (int) Math.floor(pos.getZ() / state.gridSize);

        if (collisionDetector.isPositionOccupied(gridX, gridZ, -1, playerRef.getUuid())) {
            int[] free = collisionDetector.findNearestFreePosition(gridX, gridZ, 5);
            gridX = free[0];
            gridZ = free[1];
        }

        state.currentGridX        = gridX;
        state.currentGridZ        = gridZ;
        state.lastPlayerPosition  = pos;
        state.noMovesMessageShown = false;

        final int fGX = gridX, fGZ = gridZ;
        final Vector3d fPos = pos;

        world.execute(() -> {
            state.unfreeze();
            state.clearSpellCastingState();
            boolean ok = PlayerEntityController.spawnPlayerNpc(world, state, fGX, fGZ, fPos.getY(), entityRef);
            if (ok) {
                java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
                        .schedule(() -> world.execute(() ->
                                        PlayerEntityController.broadcastAndStoreEquipment(world, store, entityRef, state)),
                                500L, java.util.concurrent.TimeUnit.MILLISECONDS);
                gridManager.spawnDirectionHolograms(world, state);
                state.hotbarState.setMode(PlayerHotbarState.Mode.MOVE);
                chat(playerRef, "#00FFFF", "NPC spawned! Walk to move. [1] to freeze.");
            } else {
                chat(playerRef, "#FF0000", "Failed — no ground found below you.");
            }
        });
    }

    // ── GM key-2: auto-control monster ────────────────────────────────────────

    private void handleGmMove(PlayerRef playerRef, GridPlayerState state, World world) {
        int gmX = state.currentGridX;
        int gmZ = state.currentGridZ;

        MonsterState match = null;
        for (MonsterState m : encounterManager.getMonsters()) {
            if (m.currentGridX == gmX && m.currentGridZ == gmZ) { match = m; break; }
        }

        if (match != null) {
            encounterManager.setControlled(match.monsterNumber);
            state.hotbarState.setMode(PlayerHotbarState.Mode.MOVE);
            chat(playerRef, "#FF69B4", "Controlling: " + match.getDisplayName());
        } else {
            MonsterState current = encounterManager.getControlledMonster();
            if (current != null) {
                state.hotbarState.setMode(PlayerHotbarState.Mode.MOVE);
                chat(playerRef, "#00FF7F", "Moving: " + current.getDisplayName());
            } else {
                chat(playerRef, "#FFA500", "No monster here — stand on its grid cell.");
            }
        }
    }

    // ── Chat helpers ──────────────────────────────────────────────────────────

    private static void chat(PlayerRef playerRef, String hexColor, String text) {
        playerRef.sendMessage(Message.raw(text).color(hexColor));
    }

    private static void printSpellCursor(PlayerRef playerRef, PlayerHotbarState hs) {
        List<SpellData> spells = hs.spellList;
        if (spells == null || spells.isEmpty()) return;
        SpellData sp = spells.get(hs.spellSelectIndex);
        String dmg   = sp.getDamageDice() != null
                ? sp.getDamageDice() + " " + sp.getDamageType().name().toLowerCase() : "effect";
        String range = "r" + (sp.getRangeGrids() > 0 ? sp.getRangeGrids() : "T");
        chat(playerRef, "#FFD700", "> " + sp.getName() + "  " + dmg + "  " + range
                + "  (" + (hs.spellSelectIndex + 1) + "/" + spells.size() + ")");
    }

    private static void printSpellList(PlayerRef playerRef, GridPlayerState state) {
        List<SpellData> spells = state.hotbarState.spellList;
        if (spells == null || spells.isEmpty()) {
            chat(playerRef, "#AAAAAA", "No spells loaded — press [3] first.");
            return;
        }
        chat(playerRef, "#FFD700", "--- SPELLS (" + spells.size() + ") ---");
        for (int i = 0; i < spells.size(); i++) {
            SpellData sp = spells.get(i);
            String dmg   = sp.getDamageDice() != null ? sp.getDamageDice() : "effect";
            String range = "r" + (sp.getRangeGrids() > 0 ? sp.getRangeGrids() : "T");
            chat(playerRef, "#FFFFFF", (i + 1) + ". " + sp.getName() + "  " + dmg + "  " + range);
        }
    }

    private static void printProfile(PlayerRef playerRef, GridPlayerState state) {
        if (state.stats == null) {
            chat(playerRef, "#AAAAAA", "No stats — use /GridClass first.");
            return;
        }
        String cls = state.stats.getClassType() != null
                ? state.stats.getClassType().getDisplayName() : "None";
        String sub = state.stats.getSubclassType() != null
                ? " / " + state.stats.getSubclassType().getDisplayName() : "";
        chat(playerRef, "#00BFFF", "--- PROFILE ---");
        chat(playerRef, "#00BFFF", cls + sub + "  Lv." + state.stats.getLevel());
        chat(playerRef, "#FFFFFF", "HP: " + state.stats.currentHP + "/" + state.stats.maxHP
                + "  AC: " + state.stats.armor
                + "  Slots: " + state.stats.getRemainingSpellSlots() + "/" + state.stats.getSpellSlots());
        chat(playerRef, "#FFFFFF", "STR " + state.stats.strength
                + "  DEX " + state.stats.dexterity + "  CON " + state.stats.constitution
                + "  INT " + state.stats.intelligence + "  WIS " + state.stats.wisdom
                + "  CHA " + state.stats.charisma);
        chat(playerRef, "#FFFFFF", "Moves: " + state.maxMoves
                + "  Init: " + (state.stats.initiative >= 0 ? "+" : "") + state.stats.initiative);
    }

    // ── No-op HUD stub ────────────────────────────────────────────────────────

    /** Called by GMCommand/GridPlayerCommand — no-op, no CustomUI. */
    public void initHudForPlayer(PlayerRef playerRef) {}

    public void onPlayerDisconnect(PlayerRef playerRef) {
        wasSneaking.remove(playerRef.getUuid());
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static void resyncSlot(PlayerRef playerRef, int slot) {
        SetActiveSlot packet = new SetActiveSlot();
        packet.activeSlot = slot;
        try {
            java.lang.reflect.Method sendMethod =
                    playerRef.getPacketHandler().getClass().getMethod("send", Packet.class);
            sendMethod.invoke(playerRef.getPacketHandler(), packet);
        } catch (Exception ignored) {}
    }

    private static Vector3d getPosition(Ref<EntityStore> entityRef, Store<EntityStore> store) {
        try {
            TransformComponent tc = store.getComponent(entityRef, TransformComponent.getComponentType());
            return tc != null ? tc.getPosition() : null;
        } catch (Exception ignored) {}
        return null;
    }
}