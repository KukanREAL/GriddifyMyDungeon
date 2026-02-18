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
import com.hypixel.hytale.protocol.PlayerSkin;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkColumn;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

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