package com.gridifymydungeon.plugin.gridmove;

import com.gridifymydungeon.plugin.debug.DebugRoleWrapper;
import com.gridifymydungeon.plugin.dnd.EncounterManager;
import com.gridifymydungeon.plugin.dnd.MonsterState;
import com.gridifymydungeon.plugin.dnd.PlayerEntityController;
import com.gridifymydungeon.plugin.dnd.RoleManager;
import com.gridifymydungeon.plugin.spell.SpellCastingState;
import com.gridifymydungeon.plugin.spell.SpellData;
import com.gridifymydungeon.plugin.spell.SpellPattern;
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
 *   Key 1 (slot 0) — Cancel / deactivate
 *   Key 2 (slot 1) — Move mode
 *   Key 3 (slot 2) — Spell select (HUD panel; crouch scrolls)
 *   Key 4 (slot 3) — Confirm spell (crouch fires /cast)
 *   Key 5 (slot 4) — Targeting (crouch fires /casttarget)
 *   Key 6 (slot 5) — Cast final (crouch fires /castfinal)
 *   Key 7 (slot 6) — Profile (HUD panel)
 *   Key 8 (slot 7) — Spell list (HUD panel)
 *   Key 9 (slot 8) — End turn
 */
public class HotbarInputHandler implements PlayerPacketFilter {

    private static final int SLOT_CANCEL       = 0;
    private static final int SLOT_MOVE         = 1;
    private static final int SLOT_SPELL_SELECT = 2;
    private static final int SLOT_CONFIRM      = 3;
    private static final int SLOT_TARGETING    = 4;
    private static final int SLOT_CAST_FINAL   = 5;
    private static final int SLOT_PROFILE      = 6;
    private static final int SLOT_LIST_SPELLS  = 7;
    private static final int SLOT_END_TURN     = 8;
    private static final int SLOT_MAX_CUSTOM   = 8;

    // Toggle this to enable/disable HUD panel (set false if .ui causes issues)
    private static final boolean HUD_ENABLED = true;

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

    // ── HUD management ────────────────────────────────────────────────────────

    /**
     * Show the GriddifyHud. Creates it on first call, then show() activates it
     * which triggers build() → append("GriddifyHud.ui").
     */
    private GriddifyHud ensureHud(PlayerRef playerRef, GridPlayerState state) {
        if (!HUD_ENABLED) return null;
        if (state.hud == null) {
            try {
                state.hud = new GriddifyHud(playerRef);
                state.hud.show();
            } catch (Exception e) {
                System.err.println("[Griddify] Failed to create HUD: " + e.getMessage());
                e.printStackTrace();
                state.hud = null;
            }
        }
        return state.hud;
    }

    /**
     * Update the HUD panel contents. Falls back to doing nothing if HUD is null.
     */
    private void refreshHud(PlayerRef playerRef, GridPlayerState state) {
        GriddifyHud hud = ensureHud(playerRef, state);
        if (hud != null) {
            hud.updatePanel(state, encounterManager, roleManager);
        }
    }

    /**
     * Hide the HUD by showing an empty one (per Hytale docs: "Set a custom hud
     * with an empty build method to hide custom UI").
     */
    private void hideHud(PlayerRef playerRef, GridPlayerState state) {
        if (state.hud != null) {
            try {
                new EmptyHud(playerRef).show();
            } catch (Exception ignored) {}
            state.hud = null;
        }
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

            // Pressing any key except 1 and 2 while in MOVE mode freezes the NPC
            if (slot != SLOT_CANCEL && slot != SLOT_MOVE
                    && hs.activeMode == PlayerHotbarState.Mode.MOVE
                    && !isGM
                    && state.npcEntity != null && state.npcEntity.isValid()) {
                state.freeze("hotbar-switch");
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
                    hideHud(playerRef, state);
                    break;

                // ── Key 2: move ───────────────────────────────────────────────
                case SLOT_MOVE:
                    if (isGM) {
                        handleGmMove(playerRef, state, world);
                    } else {
                        handlePlayerMove(playerRef, state, world, entityRef, store);
                    }
                    refreshHud(playerRef, state);
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
                    }
                    refreshHud(playerRef, state);
                    break;
                }

                // ── Key 4: confirm spell ──────────────────────────────────────
                case SLOT_CONFIRM: {
                    SpellData spell = hs.getSelectedSpell();
                    if (spell == null) {
                        chat(playerRef, "#FF0000", "No spell selected — use [3] first.");
                    } else {
                        hs.setMode(PlayerHotbarState.Mode.CONFIRM);
                        refreshHud(playerRef, state);
                    }
                    break;
                }

                // ── Key 5: targeting ──────────────────────────────────────────
                case SLOT_TARGETING: {
                    SpellCastingState cast = state.getSpellCastingState();
                    if (cast == null && !state.hasActiveCustomCast()) {
                        chat(playerRef, "#FF0000", "No active spell — confirm with [4]+crouch first.");
                    } else {
                        hs.setMode(PlayerHotbarState.Mode.TARGETING);
                        refreshHud(playerRef, state);
                    }
                    break;
                }

                // ── Key 6: cast final ─────────────────────────────────────────
                case SLOT_CAST_FINAL: {
                    SpellCastingState cast = state.getSpellCastingState();
                    if (cast == null && !state.hasActiveCustomCast()) {
                        chat(playerRef, "#FF0000", "No active spell — confirm with [4]+crouch first.");
                    } else {
                        hs.setMode(PlayerHotbarState.Mode.CAST_FINAL);
                        refreshHud(playerRef, state);
                    }
                    break;
                }

                // ── Key 7: profile ────────────────────────────────────────────
                case SLOT_PROFILE:
                    hs.setMode(PlayerHotbarState.Mode.PROFILE);
                    refreshHud(playerRef, state);
                    break;

                // ── Key 8: spell list ─────────────────────────────────────────
                case SLOT_LIST_SPELLS:
                    hs.setMode(PlayerHotbarState.Mode.LIST_SPELLS);
                    refreshHud(playerRef, state);
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

            // Crouch in SPELL_SELECT: scroll to next spell, update HUD
            case SPELL_SELECT:
                world.execute(() -> {
                    hs.advanceSpellIndex();
                    refreshHud(playerRef, state);
                });
                return true;

            // Crouch in CONFIRM: fire /cast <spell>
            case CONFIRM:
                world.execute(() -> {
                    SpellData spell = hs.getSelectedSpell();
                    if (spell == null) {
                        chat(playerRef, "#FF0000", "No spell selected — press [3] first.");
                        return;
                    }
                    CommandManager.get().handleCommand(playerRef, "Cast " + spell.getName());
                    hs.spellConfirmed = true;
                    hs.setMode(PlayerHotbarState.Mode.SPELL_SELECT);
                    refreshHud(playerRef, state);
                });
                return true;

            // Crouch in TARGETING: fire /casttarget
            case TARGETING:
                world.execute(() -> {
                    CommandManager.get().handleCommand(playerRef, "CastTarget");
                    refreshHud(playerRef, state);
                });
                return true;

            // Crouch in CAST_FINAL: fire /castfinal
            case CAST_FINAL:
                world.execute(() -> {
                    CommandManager.get().handleCommand(playerRef, "CastFinal");
                    hs.setMode(PlayerHotbarState.Mode.NONE);
                    hideHud(playerRef, state);
                });
                return true;

            default:
                return false;
        }
    }

    // ── Player key-2: spawn NPC or re-activate move ───────────────────────────

    private void handlePlayerMove(PlayerRef playerRef, GridPlayerState state, World world,
                                  Ref<EntityStore> entityRef, Store<EntityStore> store) {
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
                chat(playerRef, "#00FFFF", "Move mode active.");
            } else {
                chat(playerRef, "#FFA500", "Walk to your NPC first (grid "
                        + state.currentGridX + ", " + state.currentGridZ + ").");
            }
            return;
        }

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
                chat(playerRef, "#00FFFF", "NPC spawned! Walk to move.");
            } else {
                chat(playerRef, "#FF0000", "Failed — no ground found below you.");
            }
        });
    }

    // ── GM key-2 ──────────────────────────────────────────────────────────────

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

    // ── Chat helper ───────────────────────────────────────────────────────────

    private static void chat(PlayerRef playerRef, String hexColor, String text) {
        playerRef.sendMessage(Message.raw(text).color(hexColor));
    }

    // ── Stubs ─────────────────────────────────────────────────────────────────

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