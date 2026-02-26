package com.gridifymydungeon.plugin.dnd;

import com.gridifymydungeon.plugin.gridmove.GridPlayerState;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.protocol.EquipmentUpdate;
import com.hypixel.hytale.protocol.PlayerSkin;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.protocol.packets.entities.EntityUpdates;
import com.hypixel.hytale.server.core.universe.world.PlayerUtil;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkColumn;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Arrays;

/**
 * Controls player NPC spawning and movement.
 * FIXED: FluidSection-based fluid check (fluids are separate layer from blocks in Hytale),
 *        Flying scans ground (no wall clip), height >= 3.0f
 */
public class PlayerEntityController {

    private static final String PLAYER_MODEL = "Player";
    private static final int MIN_SCAN_OFFSET = 3;
    private static final int MAX_SCAN_OFFSET = 15;

    /**
     * Spawn player NPC at grid position with the player's skin.
     */
    public static boolean spawnPlayerNpc(World world, GridPlayerState state, int gridX, int gridZ,
                                         double playerY, Ref<EntityStore> playerEntityRef) {
        try {
            Float groundY = scanForGround(world, gridX, gridZ, (float) playerY, MIN_SCAN_OFFSET, MAX_SCAN_OFFSET);

            if (groundY == null) {
                System.err.println("[GridMove] [NPC] No ground found");
                return false;
            }

            float npcX = (gridX * 2.0f) + 1.0f;
            float npcZ = (gridZ * 2.0f) + 1.0f;
            float npcY = groundY;

            state.npcY = npcY;

            ModelAsset modelAsset = ModelAsset.getAssetMap().getAsset(PLAYER_MODEL);

            if (modelAsset == null) {
                System.err.println("[GridMove] [ERROR] Model not found: " + PLAYER_MODEL);
                return false;
            }

            Model model = Model.createScaledModel(modelAsset, 1.0f);

            Store<EntityStore> store = world.getEntityStore().getStore();
            Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();

            Vector3d position = new Vector3d(npcX, npcY, npcZ);
            Vector3f rotation = new Vector3f(0, 0, 0);

            holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(position, rotation));
            holder.addComponent(HeadRotation.getComponentType(), new HeadRotation(new Vector3f(0, 0, 0)));
            holder.addComponent(PersistentModel.getComponentType(), new PersistentModel(model.toReference()));
            holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
            holder.addComponent(BoundingBox.getComponentType(), new BoundingBox(model.getBoundingBox()));
            holder.addComponent(NetworkId.getComponentType(),
                    new NetworkId(store.getExternalData().takeNextNetworkId()));
            holder.ensureComponent(UUIDComponent.getComponentType());

            // Copy player skin to NPC
            if (playerEntityRef != null && playerEntityRef.isValid()) {
                try {
                    PlayerSkinComponent playerSkinComponent = store.getComponent(
                            playerEntityRef, PlayerSkinComponent.getComponentType());

                    if (playerSkinComponent != null) {
                        PlayerSkin playerSkin = playerSkinComponent.getPlayerSkin();

                        if (playerSkin != null) {
                            PlayerSkin clonedSkin = playerSkin.clone();
                            PlayerSkinComponent npcSkinComponent = new PlayerSkinComponent(clonedSkin);
                            holder.addComponent(PlayerSkinComponent.getComponentType(), npcSkinComponent);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[GridMove] [NPC] Failed to copy player skin: " + e.getMessage());
                }
            }

            Ref<EntityStore> npcRef = store.addEntity(holder, com.hypixel.hytale.component.AddReason.SPAWN);
            state.npcEntity = npcRef;

            // Mark skin for network update
            if (npcRef != null && npcRef.isValid()) {
                try {
                    PlayerSkinComponent npcSkin = store.getComponent(npcRef, PlayerSkinComponent.getComponentType());
                    if (npcSkin != null) {
                        npcSkin.setNetworkOutdated();
                    }
                } catch (Exception e) {
                    System.err.println("[GridMove] [NPC] Failed to mark skin for network update: " + e.getMessage());
                }
            }

            return true;

        } catch (Exception e) {
            System.err.println("[GridMove] [ERROR] Failed to spawn NPC: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static boolean spawnPlayerNpc(World world, GridPlayerState state, int gridX, int gridZ, double playerY) {
        return spawnPlayerNpc(world, state, gridX, gridZ, playerY, null);
    }

    public static void despawnPlayerNpc(World world, GridPlayerState state) {
        if (state.npcEntity != null && state.npcEntity.isValid()) {
            try {
                world.getEntityStore().getStore().removeEntity(state.npcEntity, RemoveReason.REMOVE);
            } catch (Exception e) {
                System.err.println("[GridMove] [ERROR] Failed to despawn NPC: " + e.getMessage());
            }
        }
        state.npcEntity = null;
    }

    public static boolean checkHeightAndTeleport(World world, GridPlayerState state,
                                                 int newGridX, int newGridZ, double playerY,
                                                 PlayerRef playerRef) {
        if (state.npcEntity == null || !state.npcEntity.isValid()) {
            return false;
        }

        // FLYING: Still scan for ground (avoid clipping into walls), but skip height limit
        if (state.stats.isFlying) {
            try {
                Float newGroundY = scanForGround(world, newGridX, newGridZ, (float) playerY, MIN_SCAN_OFFSET, MAX_SCAN_OFFSET);

                if (newGroundY == null) {
                    playerRef.sendMessage(Message.raw("[GridMove] Can't fly there - no valid landing!"));
                    return false;
                }

                float newX = (newGridX * 2.0f) + 1.0f;
                float newZ = (newGridZ * 2.0f) + 1.0f;

                Store<EntityStore> store = world.getEntityStore().getStore();
                TransformComponent transform = store.getComponent(state.npcEntity, TransformComponent.getComponentType());

                if (transform != null) {
                    transform.setPosition(new Vector3d(newX, newGroundY, newZ));
                    state.npcY = newGroundY;
                    return true;
                }
            } catch (Exception e) {
                System.err.println("[GridMove] [ERROR] Flying teleport failed: " + e.getMessage());
            }
            return false;
        }

        try {
            Float newGroundY = scanForGround(world, newGridX, newGridZ, (float) playerY, MIN_SCAN_OFFSET, MAX_SCAN_OFFSET);

            if (newGroundY == null) {
                state.freeze("No ground found");
                playerRef.sendMessage(Message.raw("[GridMove] Can't move there - no ground!"));
                return false;
            }

            float heightDiff = newGroundY - state.npcY;

            // FIXED: >= 3.0f (strictly under 3 blocks climb, 2 OK, 3 NOT OK)
            if (heightDiff >= 3.0f) {
                state.freeze("Too steep (+" + (int) heightDiff + " blocks)");
                playerRef.sendMessage(Message.raw("[GridMove] Too steep! (+" + (int) heightDiff + " blocks)"));
                return false;
            }

            if (heightDiff < -4.0f) {
                state.freeze("Too steep (" + (int) heightDiff + " blocks)");
                playerRef.sendMessage(Message.raw("[GridMove] Too steep! (" + (int) heightDiff + " blocks)"));
                return false;
            }

            float newX = (newGridX * 2.0f) + 1.0f;
            float newZ = (newGridZ * 2.0f) + 1.0f;

            Store<EntityStore> store = world.getEntityStore().getStore();
            TransformComponent transform = store.getComponent(state.npcEntity, TransformComponent.getComponentType());

            if (transform != null) {
                transform.setPosition(new Vector3d(newX, newGroundY, newZ));
                state.npcY = newGroundY;
                return true;
            }

            return false;

        } catch (Exception e) {
            System.err.println("[GridMove] [ERROR] Failed to check height: " + e.getMessage());
            return false;
        }
    }

    /**
     * Spawn a marker entity visible ONLY to the specified player.
     * Spawns the entity, then sends EntityUpdates(removed) to every other player in view range,
     * effectively giving that player a private entity.
     *
     * FIX 1: Removed non-existent AssetPackage import/usage.
     * FIX 2: ModelAsset path corrected to ...model.config.ModelAsset.
     * FIX 3: createDefaultModel -> createScaledModel(asset, 1.0f).
     * FIX 4: store.createEntity() -> EntityStore.REGISTRY.newHolder().
     * FIX 5: new PersistentModel(String) -> new PersistentModel(model.toReference()).
     * FIX 6: new BoundingBox(float, float) -> new BoundingBox(model.getBoundingBox()).
     * FIX 7: Lambda pRef is Ref<EntityStore> — getUuid/getPacketHandler moved to pRefComponent (PlayerRef).
     *
     * @return the spawned entity ref, or null on failure
     */
    public static com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore>
    spawnPrivateEntity(World world, PlayerRef targetPlayer,
                       String modelAssetId, float x, float y, float z, int[] netIdOut) {
        try {
            // FIX 1+2: Use the correct fully-qualified ModelAsset path; AssetPackage removed entirely.
            com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset asset =
                    com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset.getAssetMap().getAsset(modelAssetId);
            if (asset == null) {
                System.err.println("[GridMove] spawnPrivateEntity: model not found: " + modelAssetId);
                return null;
            }

            // FIX 3: createScaledModel instead of non-existent createDefaultModel
            com.hypixel.hytale.server.core.asset.type.model.config.Model model =
                    com.hypixel.hytale.server.core.asset.type.model.config.Model.createScaledModel(asset, 1.0f);

            Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store =
                    world.getEntityStore().getStore();

            // FIX 4: EntityStore.REGISTRY.newHolder() instead of store.createEntity()
            com.hypixel.hytale.component.Holder<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> holder =
                    EntityStore.REGISTRY.newHolder();

            holder.addComponent(com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType(),
                    new com.hypixel.hytale.server.core.modules.entity.component.TransformComponent(
                            new com.hypixel.hytale.math.vector.Vector3d(x, y, z),
                            new Vector3f(0, 0, 0)));
            holder.addComponent(com.hypixel.hytale.server.core.modules.entity.component.ModelComponent.getComponentType(),
                    new com.hypixel.hytale.server.core.modules.entity.component.ModelComponent(model));

            // FIX 5: model.toReference() instead of raw String
            holder.addComponent(com.hypixel.hytale.server.core.modules.entity.component.PersistentModel.getComponentType(),
                    new com.hypixel.hytale.server.core.modules.entity.component.PersistentModel(model.toReference()));

            int netId = store.getExternalData().takeNextNetworkId();
            if (netIdOut != null && netIdOut.length > 0) netIdOut[0] = netId;
            holder.addComponent(NetworkId.getComponentType(), new NetworkId(netId));

            // FIX 6: model.getBoundingBox() instead of BoundingBox(float, float)
            holder.addComponent(com.hypixel.hytale.server.core.modules.entity.component.BoundingBox.getComponentType(),
                    new com.hypixel.hytale.server.core.modules.entity.component.BoundingBox(model.getBoundingBox()));

            com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> ref =
                    store.addEntity(holder, com.hypixel.hytale.component.AddReason.SPAWN);

            System.out.println("[GridMove][Fog] spawnPrivateEntity OK netId=" + netId + " model=" + modelAssetId);
            // Caller must call hideEntityFromOthers() ~200ms later (after entity-tracker tick)
            return ref;
        } catch (Exception e) {
            System.err.println("[GridMove] [ERROR] spawnPrivateEntity failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Play an animation on the player NPC entity.
     * Uses AnimationSlot.Action which bypasses model animation validation,
     * allowing any animation string to be broadcast to all viewers.
     *
     * @param animationId  the animation asset ID to play (e.g. "Longsword_Stab_Charged")
     * @param itemAnimationsId  optional item animation set ID (e.g. "Stab"), or null
     */
    public static void playNpcAnimation(World world, GridPlayerState state,
                                        String animationId, String itemAnimationsId) {
        if (state.npcEntity == null || !state.npcEntity.isValid()) return;
        try {
            Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store =
                    world.getEntityStore().getStore();
            AnimationUtils.playAnimation(
                    state.npcEntity, AnimationSlot.Action,
                    itemAnimationsId, animationId,
                    true, store);
        } catch (Exception e) {
            System.err.println("[GridMove] [ERROR] Failed to play NPC animation: " + e.getMessage());
        }
    }

    public static void setNpcYaw(World world, GridPlayerState state, float yawRad) {
        if (state.npcEntity == null || !state.npcEntity.isValid()) {
            return;
        }
        try {
            Store<EntityStore> store = world.getEntityStore().getStore();

            TransformComponent transform = store.getComponent(state.npcEntity, TransformComponent.getComponentType());
            if (transform != null) {
                transform.setRotation(new Vector3f(0, yawRad, 0));
            }

            HeadRotation headRotation = store.getComponent(state.npcEntity, HeadRotation.getComponentType());
            if (headRotation != null) {
                headRotation.setRotation(new Vector3f(0, yawRad, 0));
            }
        } catch (Exception e) {
            System.err.println("[GridMove] [ERROR] Failed to set NPC rotation: " + e.getMessage());
        }
    }

    public static Float scanForGroundPublic(World world, int gridX, int gridZ, float referenceY) {
        return scanForGround(world, gridX, gridZ, referenceY, MIN_SCAN_OFFSET, MAX_SCAN_OFFSET);
    }

    /**
     * Reads the real player's current armor and held item, then broadcasts an
     * Equipment ComponentUpdate to every viewer that can already see the NPC.
     *
     * Call this right after spawnPlayerNpc() succeeds.
     *
     * @param store      the EntityStore for the world
     * @param playerRef  the real player entity reference (used to read inventory)
     * @param npcRef     the freshly spawned NPC entity reference (update target)
     */
    public static void broadcastEquipmentFromPlayer(Store<EntityStore> store,
                                                    Ref<EntityStore> playerRef,
                                                    Ref<EntityStore> npcRef) {
        try {
            // 1. Read the real player's inventory
            Player playerComponent = store.getComponent(playerRef, Player.getComponentType());
            if (playerComponent == null) {
                System.err.println("[GridMove] [Equipment] Could not read Player component.");
                return;
            }

            Inventory inventory = playerComponent.getInventory();

            // Build armor ID array (4 slots: Head, Chest, Hands, Legs)
            ItemContainer armorContainer = inventory.getArmor();
            int armorCapacity = armorContainer.getCapacity();
            String[] armorIds = new String[armorCapacity];
            Arrays.fill(armorIds, "");
            armorContainer.forEachWithMeta(
                    (slot, itemStack, accumulator) -> accumulator[slot] = itemStack.getItemId(),
                    armorIds
            );

            // Right hand = active hotbar item; left hand = utility slot
            ItemStack inHand  = inventory.getItemInHand();
            ItemStack utility = inventory.getUtilityItem();

            String rightHandId = (inHand  != null && !inHand.isEmpty())  ? inHand.getItemId()  : "Empty";
            String leftHandId  = (utility != null && !utility.isEmpty()) ? utility.getItemId() : "Empty";

            // 2. Build the EquipmentUpdate (it extends ComponentUpdate directly)
            EquipmentUpdate update = new EquipmentUpdate();
            update.armorIds        = armorIds;
            update.rightHandItemId = rightHandId;
            update.leftHandItemId  = leftHandId;

            // 3. Push to all viewers that can currently see the NPC
            EntityTrackerSystems.Visible visible =
                    store.getComponent(npcRef, EntityTrackerSystems.Visible.getComponentType());

            if (visible == null) {
                System.out.println("[GridMove] [Equipment] NPC has no Visible component yet - skipping.");
                return;
            }

            int sent = 0;
            if (visible.visibleTo != null) {
                for (EntityTrackerSystems.EntityViewer viewer : visible.visibleTo.values()) {
                    viewer.queueUpdate(npcRef, update);
                    sent++;
                }
            }
            // Also cover players for whom the NPC is newly visible this tick
            if (visible.newlyVisibleTo != null) {
                for (EntityTrackerSystems.EntityViewer viewer : visible.newlyVisibleTo.values()) {
                    viewer.queueUpdate(npcRef, update);
                    sent++;
                }
            }

            System.out.println("[GridMove] [Equipment] Sent to " + sent + " viewer(s). "
                    + "Right=" + rightHandId + " Left=" + leftHandId
                    + " Armor=" + Arrays.toString(armorIds));

        } catch (Exception e) {
            System.err.println("[GridMove] [Equipment] Failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // == Equipment: store snapshot + rebroadcast ==

    public static void broadcastAndStoreEquipment(World world, Store<EntityStore> store,
                                                  Ref<EntityStore> playerEntityRef,
                                                  GridPlayerState state) {
        if (state.npcEntity == null || !state.npcEntity.isValid()) return;
        try {
            Player playerComponent = store.getComponent(playerEntityRef, Player.getComponentType());
            if (playerComponent == null) { System.err.println("[GridMove][Equip] Player null"); return; }
            Inventory inventory = playerComponent.getInventory();
            ItemContainer armorContainer = inventory.getArmor();
            String[] armorIds = new String[armorContainer.getCapacity()];
            Arrays.fill(armorIds, "");
            armorContainer.forEachWithMeta((slot, item, acc) -> acc[slot] = item.getItemId(), armorIds);
            ItemStack inHand  = inventory.getItemInHand();
            ItemStack utility = inventory.getUtilityItem();
            String right = (inHand  != null && !inHand.isEmpty())  ? inHand.getItemId()  : "Empty";
            String left  = (utility != null && !utility.isEmpty()) ? utility.getItemId() : "Empty";
            state.storedArmorIds  = armorIds;
            state.storedRightHand = right;
            state.storedLeftHand  = left;
            System.out.println("[GridMove][Equip] Snapshot R=" + right + " L=" + left);
            rebroadcastStoredEquipment(store, state);
        } catch (Exception e) {
            System.err.println("[GridMove][Equip] broadcastAndStore failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void rebroadcastStoredEquipment(Store<EntityStore> store, GridPlayerState state) {
        if (state.storedArmorIds == null) return;
        if (state.npcEntity == null || !state.npcEntity.isValid()) return;
        try {
            EquipmentUpdate upd = new EquipmentUpdate();
            upd.armorIds        = state.storedArmorIds;
            upd.rightHandItemId = state.storedRightHand;
            upd.leftHandItemId  = state.storedLeftHand;
            EntityTrackerSystems.Visible vis =
                    store.getComponent(state.npcEntity, EntityTrackerSystems.Visible.getComponentType());
            if (vis == null) return;
            int sent = 0;
            if (vis.visibleTo != null)
                for (EntityTrackerSystems.EntityViewer v : vis.visibleTo.values()) { v.queueUpdate(state.npcEntity, upd); sent++; }
            if (vis.newlyVisibleTo != null)
                for (EntityTrackerSystems.EntityViewer v : vis.newlyVisibleTo.values()) { v.queueUpdate(state.npcEntity, upd); sent++; }
            System.out.println("[GridMove][Equip] Rebroadcast to " + sent + " viewer(s)");
        } catch (Exception e) {
            System.err.println("[GridMove][Equip] rebroadcast failed: " + e.getMessage());
        }
    }

    // == Fog-of-war helpers ==

    public static void hideEntityFromOthers(World world,
                                            Ref<EntityStore> entityRef, PlayerRef owner, int networkId) {
        try {
            Store<EntityStore> store = world.getEntityStore().getStore();
            int[] count = {0};
            PlayerUtil.forEachPlayerThatCanSeeEntity(entityRef, (pRef, pRefComponent, ca) -> {
                if (!pRefComponent.getUuid().equals(owner.getUuid())) {
                    pRefComponent.getPacketHandler().writeNoCache(
                            new EntityUpdates(new int[]{networkId}, null));
                    count[0]++;
                }
            }, store);
            System.out.println("[GridMove][Fog] hideEntityFromOthers: hid from " + count[0]);
        } catch (Exception e) {
            System.err.println("[GridMove][Fog] hideEntityFromOthers failed: " + e.getMessage());
        }
    }

    public static void moveFogMarker(World world, GridPlayerState state) {
        if (state.fogMarkerRef == null || !state.fogMarkerRef.isValid()) return;
        try {
            Store<EntityStore> store = world.getEntityStore().getStore();
            com.hypixel.hytale.server.core.modules.entity.component.TransformComponent tc =
                    store.getComponent(state.fogMarkerRef,
                            com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType());
            if (tc != null) {
                float nx = (state.currentGridX * 2.0f) + 1.0f;
                float ny = state.npcY + 2.0f;
                float nz = (state.currentGridZ * 2.0f) + 1.0f;
                tc.setPosition(new com.hypixel.hytale.math.vector.Vector3d(nx, ny, nz));
                System.out.println("[GridMove][Fog] moveFogMarker -> (" + nx + "," + ny + "," + nz + ")");
                if (state.fogMarkerNetId >= 0 && state.playerRef != null) {
                    hideEntityFromOthers(world, state.fogMarkerRef, state.playerRef, state.fogMarkerNetId);
                }
            }
        } catch (Exception e) {
            System.err.println("[GridMove][Fog] moveFogMarker failed: " + e.getMessage());
        }
    }

    // ====================================================================
    // GROUND SCANNING
    // ====================================================================

    /**
     * Scan for highest solid ground with clear, dry space above.
     * After finding solid ground + clear air, also checks FluidSection layer
     * for water/lava/poison. Positions with fluid above them are skipped.
     */
    private static Float scanForGround(World world, int gridX, int gridZ, float startY, int minOffset, int maxOffset) {
        int startBlockY = (int) Math.floor(startY - minOffset);
        int endBlockY = (int) Math.floor(startY - maxOffset);

        for (int blockY = startBlockY; blockY >= endBlockY; blockY--) {
            boolean hasGround = false;
            float maxHeight = 0;

            for (int xOff = 0; xOff < 2; xOff++) {
                for (int zOff = 0; zOff < 2; zOff++) {
                    int blockX = (gridX * 2) + xOff;
                    int blockZ = (gridZ * 2) + zOff;

                    BlockType block = world.getBlockType(new Vector3i(blockX, blockY, blockZ));

                    if (isSolidBlock(block) && !isBarrierBlock(block)) {
                        float blockHeight = getBlockHeight(block);
                        if (blockHeight > maxHeight) {
                            maxHeight = blockHeight;
                        }
                        hasGround = true;
                    } else if (isBarrierBlock(block)) {
                        // Barrier present — this cell is blocked, skip it entirely
                        hasGround = false;
                    }
                }
            }

            if (hasGround) {
                float groundY = blockY + maxHeight;
                if (isSpaceClear(world, gridX, gridZ, groundY, 2.0f)) {
                    // FIXED: Check fluid layer (water/lava/poison are SEPARATE from blocks)
                    if (!hasFluidAbove(world, gridX, gridZ, groundY)) {
                        return groundY;
                    }
                    // Has fluid above — continue scanning lower
                }
            }
        }

        return null;
    }

    private static boolean isSpaceClear(World world, int gridX, int gridZ, float groundY, float requiredHeight) {
        int startCheckY = (int) Math.floor(groundY);
        int endCheckY = (int) Math.floor(groundY + requiredHeight);

        for (int checkY = startCheckY; checkY <= endCheckY; checkY++) {
            for (int xOff = 0; xOff < 2; xOff++) {
                for (int zOff = 0; zOff < 2; zOff++) {
                    int blockX = (gridX * 2) + xOff;
                    int blockZ = (gridZ * 2) + zOff;

                    BlockType block = world.getBlockType(new Vector3i(blockX, checkY, blockZ));

                    if (isSolidBlock(block)) {
                        float blockTop = checkY + getBlockHeight(block);
                        if (blockTop > groundY) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    // ====================================================================
    // FLUID DETECTION (via FluidSection — fluids are separate from blocks)
    // ====================================================================

    /**
     * Check if any fluid (water, lava, poison) exists in the 2-block space above ground.
     * Hytale stores fluids in FluidSection, a separate layer from blocks.
     * world.getBlockType() never sees fluids. We must read FluidSection directly.
     *
     * FluidSection.getFluidId() returns 0 for no fluid (Fluid.EMPTY_ID = 0).
     */
    private static boolean hasFluidAbove(World world, int gridX, int gridZ, float groundY) {
        try {
            Store<ChunkStore> chunkStore = world.getChunkStore().getStore();

            int startCheckY = (int) Math.floor(groundY);
            int endCheckY = (int) Math.floor(groundY + 2.0f);

            for (int checkY = startCheckY; checkY <= endCheckY; checkY++) {
                for (int xOff = 0; xOff < 2; xOff++) {
                    for (int zOff = 0; zOff < 2; zOff++) {
                        int blockX = (gridX * 2) + xOff;
                        int blockZ = (gridZ * 2) + zOff;

                        FluidSection fluidSection = findFluidSection(world, chunkStore, blockX, checkY, blockZ);
                        if (fluidSection != null) {
                            int fluidId = fluidSection.getFluidId(blockX, checkY, blockZ);
                            if (fluidId != 0) {
                                return true;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[GridMove] [FLUID] Error checking fluid: " + e.getMessage());
        }
        return false;
    }

    /**
     * Find the FluidSection for a given block position by navigating:
     *   World → ChunkStore → ChunkColumn → Sections → FluidSection
     */
    private static FluidSection findFluidSection(World world, Store<ChunkStore> store,
                                                 int blockX, int blockY, int blockZ) {
        try {
            long chunkIndex = ChunkUtil.indexChunkFromBlock(blockX, blockZ);
            Ref<ChunkStore> chunkRef = world.getChunkStore().getChunkReference(chunkIndex);
            if (chunkRef == null || !chunkRef.isValid()) return null;

            ChunkColumn column = store.getComponent(chunkRef, ChunkColumn.getComponentType());
            if (column == null) return null;

            int targetSectionY = ChunkUtil.chunkCoordinate(blockY);

            for (Ref<ChunkStore> sectionRef : column.getSections()) {
                if (sectionRef == null || !sectionRef.isValid()) continue;
                ChunkSection sectionInfo = store.getComponent(sectionRef, ChunkSection.getComponentType());
                if (sectionInfo != null && sectionInfo.getY() == targetSectionY) {
                    return store.getComponent(sectionRef, FluidSection.getComponentType());
                }
            }
        } catch (Exception e) {
            // Silently ignore — section might not be loaded
        }
        return null;
    }

    // ====================================================================
    // BLOCK HELPERS
    // ====================================================================

    private static boolean isSolidBlock(BlockType block) {
        if (block == null) return false;
        return block.getMaterial() == BlockMaterial.Solid;
    }

    /**
     * Barrier blocks are invisible walls — entities cannot stand on or inside them.
     * Identified by block ID containing "barrier" (case-insensitive).
     */
    private static boolean isBarrierBlock(BlockType block) {
        return block != null && block.getId() != null &&
                block.getId().toLowerCase().contains("barrier");
    }

    private static float getBlockHeight(BlockType block) {
        if (block == null || !isSolidBlock(block)) {
            return 0.0f;
        }
        return 1.0f;
    }
}