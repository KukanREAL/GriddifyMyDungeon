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
 * Hotbar key bindings:
 *   Key 1 (slot 0) — Cancel + /gridoff
 *   Key 2 (slot 1) — Move + auto /gridon
 *   Key 3 (slot 2) — Spell select + freeze NPC (crouch scrolls)
 *   Key 4 (slot 3) — Confirm + Target merged (first crouch = /cast, next = /casttarget)
 *   Key 5 (slot 4) — Cast final (crouch fires /castfinal)
 *   Key 6 (slot 5) — Empty
 *   Key 7 (slot 6) — Empty
 *   Key 8 (slot 7) — Profile + Spell list merged (crouch swaps)
 *   Key 9 (slot 8) — End turn confirm (crouch fires /endturn)
 */
public class HotbarInputHandler implements PlayerPacketFilter {

    private static final int SLOT_CANCEL         = 0;
    private static final int SLOT_MOVE           = 1;
    private static final int SLOT_SPELL_SELECT   = 2;
    private static final int SLOT_CONFIRM_TARGET = 3;
    private static final int SLOT_CAST_FINAL     = 4;
    // slots 5, 6 = empty
    private static final int SLOT_INFO           = 7;
    private static final int SLOT_END_TURN       = 8;
    private static final int SLOT_MAX_CUSTOM     = 8;

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

    private GriddifyHud ensureHud(PlayerRef playerRef, GridPlayerState state) {
        if (!HUD_ENABLED) return null;
        if (state.hud == null) {
            try {
                state.hud = new GriddifyHud(playerRef);
                state.hud.show();
            } catch (Exception e) {
                System.err.println("[Griddify] Failed to create HUD: " + e.getMessage());
                state.hud = null;
            }
        }
        return state.hud;
    }

    private void refreshHud(PlayerRef playerRef, GridPlayerState state) {
        GriddifyHud hud = ensureHud(playerRef, state);
        if (hud != null) hud.updatePanel(state, encounterManager, roleManager);
    }

    // HUD stays permanent — always refresh to idle instead of hiding
    private void hideHud(PlayerRef playerRef, GridPlayerState state) {
        refreshHud(playerRef, state);
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

                // ── Key 1: cancel + gridoff ───────────────────────────────────
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
                    // Remove grid overlay if active
                    if (state.gridOverlayEnabled) {
                        GridOverlayManager.removeGridOverlay(world, state);
                    }
                    hs.setMode(PlayerHotbarState.Mode.NONE);
                    refreshHud(playerRef, state);
                    break;

                // ── Key 2: move + auto gridon ─────────────────────────────────
                case SLOT_MOVE:
                    if (isGM) {
                        handleGmMove(playerRef, state, world, entityRef, store);
                    } else {
                        handlePlayerMove(playerRef, state, world, entityRef, store);
                    }
                    refreshHud(playerRef, state);
                    break;

                // ── Key 3: spell select + freeze NPC ─────────────────────────
                case SLOT_SPELL_SELECT: {
                    // Always freeze NPC so player can walk around to aim freely
                    if (!isGM && state.npcEntity != null && state.npcEntity.isValid()) {
                        state.freeze("spell-select");
                    }
                    List<SpellData> spells = GriddifyHud.buildSpellList(state, encounterManager, roleManager);
                    hs.spellList        = spells;
                    hs.spellSelectIndex = 0;
                    hs.spellConfirmed   = false;
                    hs.setMode(PlayerHotbarState.Mode.SPELL_SELECT);
                    if (spells.isEmpty())
                        chat(playerRef, "#FF0000", "No spells available — use /GridClass first.");
                    refreshHud(playerRef, state);
                    break;
                }

                // ── Key 4: confirm + target merged ────────────────────────────
                case SLOT_CONFIRM_TARGET: {
                    SpellCastingState cast = state.getSpellCastingState();
                    if (cast != null || state.hasActiveCustomCast()) {
                        // Already casting — switch to targeting view
                        hs.spellConfirmed = true;
                        hs.setMode(PlayerHotbarState.Mode.CONFIRM_TARGET);
                    } else {
                        // No active cast — confirm the selected spell
                        SpellData spell = hs.getSelectedSpell();
                        if (spell == null) {
                            chat(playerRef, "#FF0000", "No spell selected — use [3] first.");
                            break;
                        }
                        hs.spellConfirmed = false;
                        hs.setMode(PlayerHotbarState.Mode.CONFIRM_TARGET);
                    }
                    refreshHud(playerRef, state);
                    break;
                }

                // ── Key 5: cast final ─────────────────────────────────────────
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

                // ── Keys 6, 7: empty ──────────────────────────────────────────
                case 5:
                case 6:
                    break;

                // ── Key 8: info (profile + spell list, crouch swaps) ──────────
                case SLOT_INFO:
                    hs.infoShowingProfile = true; // always open on profile
                    hs.setMode(PlayerHotbarState.Mode.INFO);
                    refreshHud(playerRef, state);
                    break;

                // ── Key 9: arm end-turn confirm ───────────────────────────────
                case SLOT_END_TURN:
                    hs.setMode(PlayerHotbarState.Mode.END_TURN_CONFIRM);
                    refreshHud(playerRef, state);
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

            // Crouch in SPELL_SELECT: scroll
            case SPELL_SELECT:
                world.execute(() -> {
                    hs.advanceSpellIndex();
                    refreshHud(playerRef, state);
                });
                return true;

            // Crouch in CONFIRM_TARGET:
            //   Phase 1 (spellConfirmed=false) → fire /cast <spell>
            //   Phase 2 (spellConfirmed=true)  → fire /casttarget
            case CONFIRM_TARGET:
                world.execute(() -> {
                    if (!hs.spellConfirmed) {
                        SpellData spell = hs.getSelectedSpell();
                        if (spell == null) {
                            chat(playerRef, "#FF0000", "No spell selected — press [3] first.");
                            return;
                        }
                        CommandManager.get().handleCommand(playerRef, "Cast " + spell.getName());
                        hs.spellConfirmed = true;
                    } else {
                        CommandManager.get().handleCommand(playerRef, "CastTarget");
                    }
                    refreshHud(playerRef, state);
                });
                return true;

            // Crouch in CAST_FINAL: fire /castfinal
            case CAST_FINAL:
                world.execute(() -> {
                    CommandManager.get().handleCommand(playerRef, "CastFinal");
                    hs.setMode(PlayerHotbarState.Mode.NONE);
                    refreshHud(playerRef, state);
                });
                return true;

            // Crouch in INFO: swap profile ↔ spell/attack list
            case INFO:
                world.execute(() -> {
                    hs.infoShowingProfile = !hs.infoShowingProfile;
                    refreshHud(playerRef, state);
                });
                return true;

            // Crouch in END_TURN_CONFIRM: fire /endturn
            case END_TURN_CONFIRM:
                world.execute(() -> {
                    CommandManager.get().handleCommand(playerRef, "endturn");
                    hs.setMode(PlayerHotbarState.Mode.NONE);
                    refreshHud(playerRef, state);
                });
                return true;

            default:
                return false;
        }
    }

    // ── Player key-2: move + auto gridon ─────────────────────────────────────

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
                GridOverlayManager.spawnPlayerGridOverlay(
                        world, state, collisionDetector, playerRef.getUuid(), playerRef);
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
            boolean ok = PlayerEntityController.spawnPlayerNpc(
                    world, state, fGX, fGZ, fPos.getY(), entityRef);
            if (ok) {
                java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
                        .schedule(() -> world.execute(() ->
                                        PlayerEntityController.broadcastAndStoreEquipment(
                                                world, store, entityRef, state)),
                                500L, java.util.concurrent.TimeUnit.MILLISECONDS);
                gridManager.spawnDirectionHolograms(world, state);
                state.hotbarState.setMode(PlayerHotbarState.Mode.MOVE);
                GridOverlayManager.spawnPlayerGridOverlay(
                        world, state, collisionDetector, playerRef.getUuid(), playerRef);
                chat(playerRef, "#00FFFF", "NPC spawned! Walk to move.");
            } else {
                chat(playerRef, "#FF0000", "Failed — no ground found below you.");
            }
        });
    }

    // ── GM key-2: move + auto gridon ─────────────────────────────────────────

    private void handleGmMove(PlayerRef playerRef, GridPlayerState state, World world,
                              Ref<EntityStore> entityRef, Store<EntityStore> store) {
        // Read GM's actual world position fresh — state.currentGridX/Z may be
        // stale (last set to controlled monster's position on previous key2 press)
        Vector3d pos = getPosition(entityRef, store);
        int gmX = state.currentGridX;
        int gmZ = state.currentGridZ;
        if (pos != null) {
            gmX = (int) Math.floor(pos.getX() / 2.0);
            gmZ = (int) Math.floor(pos.getZ() / 2.0);
        }

        // Update GM state to actual position so future lookups are correct
        state.currentGridX = gmX;
        state.currentGridZ = gmZ;
        if (pos != null) state.npcY = (float) pos.getY();

        final int fGmX = gmX, fGmZ = gmZ;

        MonsterState match = null;
        for (MonsterState m : encounterManager.getMonsters()) {
            if (m.currentGridX == fGmX && m.currentGridZ == fGmZ) { match = m; break; }
        }

        if (match != null) {
            encounterManager.setControlled(match.monsterNumber);
            state.currentGridX   = match.currentGridX;
            state.currentGridZ   = match.currentGridZ;
            state.npcY           = match.spawnY;
            state.remainingMoves = match.remainingMoves;
            state.maxMoves       = match.maxMoves;
            state.hotbarState.setMode(PlayerHotbarState.Mode.MOVE);
            GridOverlayManager.spawnGMBFSOverlay(world, state, collisionDetector);
            chat(playerRef, "#FF69B4", "Controlling: " + match.getDisplayName());
        } else {
            MonsterState current = encounterManager.getControlledMonster();
            if (current != null) {
                state.currentGridX   = current.currentGridX;
                state.currentGridZ   = current.currentGridZ;
                state.npcY           = current.spawnY;
                state.remainingMoves = current.remainingMoves;
                state.maxMoves       = current.maxMoves;
                state.hotbarState.setMode(PlayerHotbarState.Mode.MOVE);
                GridOverlayManager.spawnGMBFSOverlay(world, state, collisionDetector);
                chat(playerRef, "#00FF7F", "Moving: " + current.getDisplayName());
            } else {
                chat(playerRef, "#FFA500", "No monster here — stand on its grid cell.");
            }
        }
    }

    // ── initHudForPlayer ──────────────────────────────────────────────────────

    public void initHudForPlayer(PlayerRef playerRef) {
        GridPlayerState state = gridManager.getState(playerRef);
        GriddifyHud hud = ensureHud(playerRef, state);
        if (hud != null) {
            boolean isGM = roleManager.isGM(playerRef);
            hud.updateRolePanel(isGM);
            hud.updatePanel(state, encounterManager, roleManager);
        }
    }

    // ── Chat helper ───────────────────────────────────────────────────────────

    private static void chat(PlayerRef playerRef, String hexColor, String text) {
        playerRef.sendMessage(Message.raw(text).color(hexColor));
    }

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