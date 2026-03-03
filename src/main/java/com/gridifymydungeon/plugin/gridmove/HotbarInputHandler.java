package com.gridifymydungeon.plugin.gridmove;

import com.gridifymydungeon.plugin.debug.DebugRoleWrapper;
import com.gridifymydungeon.plugin.dnd.EncounterManager;
import com.gridifymydungeon.plugin.dnd.MonsterState;
import com.gridifymydungeon.plugin.dnd.PlayerEntityController;
import com.gridifymydungeon.plugin.dnd.RoleManager;
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
 * Intercepts hotbar key presses (SyncInteractionChains) and crouch (ClientMovement.isSneaking).
 *
 * HUD is invisible until /GridPlayer or /GridGM assigns a role.
 * initHudForPlayer() is called by GMCommand/GridPlayerCommand after role assignment.
 *
 * Slot map (0-based):
 *   0 (key 1) NONE        — GM: release control. Both: cancel cast. Panel hides.
 *   1 (key 2) MOVE        — Player: spawn/toggle NPC. GM: auto-control monster at XZ.
 *   2 (key 3) SPELL_SELECT— shows current spell; crouch scrolls; 2nd press = /cast
 *   3 (key 4) LIST_SPELLS — compact read-only spell list
 *   4 (key 5) CAST_FINAL  — arm; crouch = /castfinal
 *   5 (key 6) TARGETING   — arm; crouch = /casttarget
 *   6 (key 7) PROFILE     — character stats
 *   7-8       pass through
 */
public class HotbarInputHandler implements PlayerPacketFilter {

    private static final int SLOT_NONE         = 0;
    private static final int SLOT_MOVE         = 1;
    private static final int SLOT_SPELL_SELECT = 2;
    private static final int SLOT_LIST_SPELLS  = 3;
    private static final int SLOT_CAST_FINAL   = 4;
    private static final int SLOT_TARGETING    = 5;
    private static final int SLOT_PROFILE      = 6;
    private static final int SLOT_MAX_CUSTOM   = 6;

    private final GridMoveManager    gridManager;
    private final EncounterManager   encounterManager;
    private final SpellVisualManager spellVisualManager;
    private final RoleManager        roleManager;
    private final CollisionDetector  collisionDetector;

    private final Map<UUID, GriddifyHud> huds        = new HashMap<>();
    private final Map<UUID, Boolean>     wasSneaking = new HashMap<>();

    public HotbarInputHandler(GridMoveManager gridManager, EncounterManager encounterManager,
                              SpellVisualManager spellVisualManager, RoleManager roleManager,
                              CollisionDetector collisionDetector) {
        this.gridManager       = gridManager;
        this.encounterManager  = encounterManager;
        this.spellVisualManager = spellVisualManager;
        this.roleManager       = roleManager;
        this.collisionDetector = collisionDetector;
    }

    // ── PacketFilter ──────────────────────────────────────────────────────────

    @Override
    public boolean test(@Nonnull PlayerRef playerRef, @Nonnull Packet packet) {
        // Only active after /GridPlayer or /GridGM
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
            // NOTE: field name is isSneaking — adjust if your server.jar differs
            boolean now  = cm.isSneaking;
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

            switch (slot) {

                case SLOT_NONE:
                    // GM: release monster control
                    if (isGM) {
                        MonsterState controlled = encounterManager.getControlledMonster();
                        if (controlled != null) {
                            encounterManager.releaseControl();
                            playerRef.sendMessage(Message.raw(
                                            "[Griddify] Released: " + controlled.getDisplayName())
                                    .color("#FFA500"));
                        }
                    }
                    cancelCastIfActive(playerRef, state);
                    hs.setMode(PlayerHotbarState.Mode.NONE);
                    break;

                case SLOT_MOVE:
                    if (isGM) {
                        handleGmMove(playerRef, state, world);
                    } else {
                        handlePlayerMove(playerRef, state, world, entityRef, store);
                    }
                    break;

                case SLOT_SPELL_SELECT:
                    if (hs.activeMode == PlayerHotbarState.Mode.SPELL_SELECT && !hs.spellConfirmed) {
                        confirmSpell(playerRef, state);
                    } else {
                        cancelCastIfActive(playerRef, state);
                        List<SpellData> spells = GriddifyHud.buildSpellList(state, encounterManager, roleManager);
                        hs.spellList        = spells;
                        hs.spellSelectIndex = 0;
                        hs.spellConfirmed   = false;
                        hs.setMode(PlayerHotbarState.Mode.SPELL_SELECT);
                        if (spells.isEmpty())
                            playerRef.sendMessage(Message.raw("[Griddify] No spells available!").color("#FF0000"));
                    }
                    break;

                case SLOT_LIST_SPELLS:
                    hs.setMode(hs.activeMode == PlayerHotbarState.Mode.LIST_SPELLS
                            ? PlayerHotbarState.Mode.NONE
                            : PlayerHotbarState.Mode.LIST_SPELLS);
                    break;

                case SLOT_CAST_FINAL:
                    if (state.getSpellCastingState() != null || state.hasActiveCustomCast()) {
                        hs.setMode(hs.activeMode == PlayerHotbarState.Mode.CAST_FINAL
                                ? PlayerHotbarState.Mode.SPELL_SELECT
                                : PlayerHotbarState.Mode.CAST_FINAL);
                    } else {
                        playerRef.sendMessage(Message.raw("[Griddify] No spell prepared — use key 3 first.").color("#FF0000"));
                    }
                    break;

                case SLOT_TARGETING:
                    if (state.getSpellCastingState() != null || state.hasActiveCustomCast()) {
                        hs.setMode(hs.activeMode == PlayerHotbarState.Mode.TARGETING
                                ? PlayerHotbarState.Mode.SPELL_SELECT
                                : PlayerHotbarState.Mode.TARGETING);
                    } else {
                        playerRef.sendMessage(Message.raw("[Griddify] No spell prepared — use key 3 first.").color("#FF0000"));
                    }
                    break;

                case SLOT_PROFILE:
                    hs.setMode(hs.activeMode == PlayerHotbarState.Mode.PROFILE
                            ? PlayerHotbarState.Mode.NONE
                            : PlayerHotbarState.Mode.PROFILE);
                    break;
            }

            refreshHud(playerRef, state, entityRef, store);
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
            case SPELL_SELECT:
                world.execute(() -> {
                    hs.advanceSpellIndex();
                    refreshHud(playerRef, state, entityRef, store);
                });
                return true;

            case TARGETING:
                world.execute(() -> {
                    com.hypixel.hytale.server.core.command.CommandManager.get()
                            .handleCommand(playerRef, "CastTarget");
                    refreshHud(playerRef, state, entityRef, store);
                });
                return true;

            case CAST_FINAL:
                world.execute(() -> {
                    com.hypixel.hytale.server.core.command.CommandManager.get()
                            .handleCommand(playerRef, "CastFinal");
                    hs.setMode(PlayerHotbarState.Mode.NONE);
                    refreshHud(playerRef, state, entityRef, store);
                });
                return true;

            default:
                return false;
        }
    }

    // ── Player key-2: spawn / despawn NPC ────────────────────────────────────

    private void handlePlayerMove(PlayerRef playerRef, GridPlayerState state, World world,
                                  Ref<EntityStore> entityRef, Store<EntityStore> store) {
        // Toggle: if NPC already active, despawn
        if (state.npcEntity != null && state.npcEntity.isValid()) {
            state.unfreeze();
            state.clearSpellCastingState();
            if (state.gridOverlayEnabled) GridOverlayManager.removeGridOverlay(world, state);
            PlayerEntityController.despawnPlayerNpc(world, state);
            state.hotbarState.setMode(PlayerHotbarState.Mode.NONE);
            playerRef.sendMessage(Message.raw("[Griddify] NPC despawned.").color("#FFA500"));
            return;
        }

        // Get position
        Vector3d pos = getPosition(entityRef, store);
        if (pos == null) {
            playerRef.sendMessage(Message.raw("[Griddify] Could not read position!").color("#FF0000"));
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
                        playerRef.sendMessage(Message.raw("[Griddify] Crossbow not supported — switch weapon first.").color("#FF4500"));
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

        state.currentGridX       = gridX;
        state.currentGridZ       = gridZ;
        state.lastPlayerPosition = pos;
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
                playerRef.sendMessage(Message.raw("[Griddify] NPC ready! Walk to move.").color("#00FFFF"));
            } else {
                playerRef.sendMessage(Message.raw("[Griddify] Failed — no ground found below you.").color("#FF0000"));
            }
        });
    }

    // ── GM key-2: auto-control monster ───────────────────────────────────────

    private void handleGmMove(PlayerRef playerRef, GridPlayerState state, World world) {
        int gmX = state.currentGridX;
        int gmZ = state.currentGridZ;

        MonsterState match = null;
        for (MonsterState m : encounterManager.getMonsters()) {
            if (m.currentGridX == gmX && m.currentGridZ == gmZ) {
                match = m;
                break;
            }
        }

        if (match != null) {
            encounterManager.setControlled(match.monsterNumber);
            state.hotbarState.setMode(PlayerHotbarState.Mode.MOVE);
            playerRef.sendMessage(Message.raw("[Griddify] Controlling: " + match.getDisplayName()).color("#FF69B4"));
        } else {
            MonsterState current = encounterManager.getControlledMonster();
            if (current != null) {
                state.hotbarState.setMode(PlayerHotbarState.Mode.MOVE);
                playerRef.sendMessage(Message.raw("[Griddify] Moving: " + current.getDisplayName()).color("#00FF7F"));
            } else {
                playerRef.sendMessage(Message.raw("[Griddify] No monster here — stand on its cell.").color("#FFA500"));
            }
        }
    }

    // ── Spell confirm ─────────────────────────────────────────────────────────

    private void confirmSpell(PlayerRef playerRef, GridPlayerState state) {
        SpellData spell = state.hotbarState.getSelectedSpell();
        if (spell == null) {
            playerRef.sendMessage(Message.raw("[Griddify] No spell selected.").color("#FF0000"));
            return;
        }
        com.hypixel.hytale.server.core.command.CommandManager.get()
                .handleCommand(playerRef, "Cast " + spell.getName());
        state.hotbarState.spellConfirmed = true;
    }

    private void cancelCastIfActive(PlayerRef playerRef, GridPlayerState state) {
        if (state.getSpellCastingState() != null || state.hasActiveCustomCast()) {
            com.hypixel.hytale.server.core.command.CommandManager.get()
                    .handleCommand(playerRef, "CastCancel");
        }
        state.hotbarState.spellConfirmed = false;
    }

    // ── HUD ──────────────────────────────────────────────────────────────────

    /**
     * Called by /GridPlayer and /GridGM immediately after role assignment.
     * Registers the HUD so it's ready for the first key press.
     */
    public void initHudForPlayer(PlayerRef playerRef) {
        Ref<EntityStore> entityRef = playerRef.getReference();
        if (entityRef == null || !entityRef.isValid()) return;
        Store<EntityStore> store = entityRef.getStore();
        store.getExternalData().getWorld().execute(() -> {
            if (gridManager.getState(playerRef) == null) return;
            getOrCreateHud(playerRef, entityRef, store);
        });
    }

    private void refreshHud(PlayerRef playerRef, GridPlayerState state,
                            Ref<EntityStore> entityRef, Store<EntityStore> store) {
        GriddifyHud hud = getOrCreateHud(playerRef, entityRef, store);
        if (hud == null) return;
        hud.updatePanel(state, encounterManager, roleManager);
    }

    private GriddifyHud getOrCreateHud(PlayerRef playerRef,
                                       Ref<EntityStore> entityRef, Store<EntityStore> store) {
        UUID uuid = playerRef.getUuid();
        GriddifyHud hud = huds.get(uuid);
        if (hud != null) return hud;

        hud = new GriddifyHud(playerRef);
        huds.put(uuid, hud);
        try {
            Player player = store.getComponent(entityRef, Player.getComponentType());
            if (player != null) player.getHudManager().setCustomHud(entityRef, store, hud);
        } catch (Exception e) {
            System.err.println("[Griddify] [HUD] Failed for " + playerRef.getUsername() + ": " + e.getMessage());
            huds.remove(uuid);
            return null;
        }
        return hud;
    }

    public void onPlayerDisconnect(PlayerRef playerRef) {
        huds.remove(playerRef.getUuid());
        wasSneaking.remove(playerRef.getUuid());
        GridPlayerState state = gridManager.getState(playerRef);
        if (state != null) state.hotbarState.reset();
    }

    // ── Utilities ────────────────────────────────────────────────────────────

    private static void resyncSlot(PlayerRef playerRef, int slot) {
        try { playerRef.getPacketHandler().write(new SetActiveSlot(Inventory.HOTBAR_SECTION_ID, slot)); }
        catch (Exception ignored) {}
    }

    private static Vector3d getPosition(Ref<EntityStore> entityRef, Store<EntityStore> store) {
        try {
            TransformComponent t = store.getComponent(entityRef, TransformComponent.getComponentType());
            return t != null ? t.getPosition() : null;
        } catch (Exception e) { return null; }
    }
}