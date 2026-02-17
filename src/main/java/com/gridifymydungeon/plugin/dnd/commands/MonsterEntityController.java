package com.gridifymydungeon.plugin.dnd.commands;

import com.gridifymydungeon.plugin.dnd.MonsterState;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkColumn;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Controls monster entity spawning, movement, and despawning
 * FIXED v3: Proper fluid detection via FluidSection (fluids are separate layer from blocks in Hytale)
 */
public class MonsterEntityController {

    private static final String DEFAULT_MODEL = "PlayerTestModel_G";
    private static final float HOLOGRAM_Y_OFFSET = 2.5f;
    private static final int MIN_SCAN_OFFSET = 3;
    private static final int MAX_SCAN_OFFSET = 15;

    /**
     * Spawn monster entity at grid position with ground scanning
     */
    public static boolean spawnMonster(World world, MonsterState monster, int gridX, int gridZ, double gmY) {
        try {
            Float groundY = scanForGround(world, gridX, gridZ, (float) gmY, MIN_SCAN_OFFSET, MAX_SCAN_OFFSET);

            if (groundY == null) {
                System.err.println("[Griddify] [MONSTER] No ground found for " + monster.getDisplayName());
                return false;
            }

            float monsterX = (gridX * 2.0f) + 1.0f;
            float monsterZ = (gridZ * 2.0f) + 1.0f;
            float monsterY = groundY;

            monster.spawnY = monsterY;

            Store<EntityStore> store = world.getEntityStore().getStore();
            Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();

            String modelName = monster.monsterName;
            ModelAsset modelAsset = ModelAsset.getAssetMap().getAsset(modelName);

            if (modelAsset == null) {
                System.err.println("[Griddify] [ERROR] Model not found: " + modelName + " - trying default");
                modelAsset = ModelAsset.getAssetMap().getAsset(DEFAULT_MODEL);

                if (modelAsset == null) {
                    System.err.println("[Griddify] [ERROR] Default model also not found: " + DEFAULT_MODEL);
                    return false;
                }
            }

            Model model = Model.createScaledModel(modelAsset, 1.0f);

            Vector3d position = new Vector3d(monsterX, monsterY, monsterZ);
            Vector3f rotation = new Vector3f(0, 0, 0);

            holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(position, rotation));
            holder.addComponent(HeadRotation.getComponentType(), new HeadRotation(new Vector3f(0, 0, 0)));
            holder.addComponent(PersistentModel.getComponentType(), new PersistentModel(model.toReference()));
            holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
            holder.addComponent(BoundingBox.getComponentType(), new BoundingBox(model.getBoundingBox()));
            holder.addComponent(NetworkId.getComponentType(), new NetworkId(store.getExternalData().takeNextNetworkId()));
            holder.ensureComponent(UUIDComponent.getComponentType());

            Ref<EntityStore> monsterRef = store.addEntity(holder, com.hypixel.hytale.component.AddReason.SPAWN);
            monster.monsterEntity = monsterRef;

            System.out.println("[Griddify] [MONSTER] Spawned " + monster.getDisplayName() +
                    " at (" + monsterX + ", " + monsterY + ", " + monsterZ + ")");

            spawnNumberHologram(world, monster, monsterX, monsterY + HOLOGRAM_Y_OFFSET, monsterZ);

            return true;

        } catch (Exception e) {
            System.err.println("[Griddify] [ERROR] Failed to spawn monster: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private static void spawnNumberHologram(World world, MonsterState monster, float x, float y, float z) {
        try {
            Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
            ProjectileComponent projectileComponent = new ProjectileComponent("Projectile");

            holder.putComponent(ProjectileComponent.getComponentType(), projectileComponent);
            holder.putComponent(TransformComponent.getComponentType(),
                    new TransformComponent(new Vector3d(x, y, z), new Vector3f(0, 0, 0)));
            holder.ensureComponent(UUIDComponent.getComponentType());

            if (projectileComponent.getProjectile() == null) {
                projectileComponent.initialize();
                if (projectileComponent.getProjectile() == null) {
                    System.err.println("[Griddify] [ERROR] Failed to initialize projectile for hologram");
                    return;
                }
            }

            Store<EntityStore> store = world.getEntityStore().getStore();
            holder.addComponent(NetworkId.getComponentType(),
                    new NetworkId(store.getExternalData().takeNextNetworkId()));

            String holoText = String.valueOf(monster.monsterNumber);
            holder.addComponent(Nameplate.getComponentType(), new Nameplate(holoText));

            Ref<EntityStore> hologramRef = store.addEntity(holder, com.hypixel.hytale.component.AddReason.SPAWN);
            monster.numberHologram = hologramRef;

        } catch (Exception e) {
            System.err.println("[Griddify] [ERROR] Failed to spawn number hologram: " + e.getMessage());
        }
    }

    public static void despawnMonster(World world, MonsterState monster) {
        if (monster.monsterEntity != null && monster.monsterEntity.isValid()) {
            try {
                world.getEntityStore().getStore().removeEntity(monster.monsterEntity, RemoveReason.REMOVE);
            } catch (Exception e) {
                System.err.println("[Griddify] [ERROR] Failed to despawn monster entity: " + e.getMessage());
            }
        }

        if (monster.numberHologram != null && monster.numberHologram.isValid()) {
            try {
                world.getEntityStore().getStore().removeEntity(monster.numberHologram, RemoveReason.REMOVE);
            } catch (Exception e) {
                System.err.println("[Griddify] [ERROR] Failed to despawn hologram: " + e.getMessage());
            }
        }

        monster.monsterEntity = null;
        monster.numberHologram = null;
    }

    public static void teleportMonsterToY(World world, MonsterState monster, int newGridX, int newGridZ, float targetY) {
        if (monster.monsterEntity == null || !monster.monsterEntity.isValid()) {
            return;
        }

        try {
            float newX = (newGridX * 2.0f) + 1.0f;
            float newZ = (newGridZ * 2.0f) + 1.0f;

            monster.spawnY = targetY;

            Vector3d newPosition = new Vector3d(newX, targetY, newZ);
            Store<EntityStore> store = world.getEntityStore().getStore();

            TransformComponent monsterTransform = store.getComponent(monster.monsterEntity, TransformComponent.getComponentType());
            if (monsterTransform != null) {
                monsterTransform.setPosition(newPosition);
            }

            if (monster.numberHologram != null && monster.numberHologram.isValid()) {
                TransformComponent holoTransform = store.getComponent(monster.numberHologram, TransformComponent.getComponentType());
                if (holoTransform != null) {
                    holoTransform.setPosition(new Vector3d(newX, targetY + HOLOGRAM_Y_OFFSET, newZ));
                }
            }

        } catch (Exception e) {
            System.err.println("[Griddify] [ERROR] Failed to teleport monster: " + e.getMessage());
        }
    }

    public static void teleportMonster(World world, MonsterState monster, int newGridX, int newGridZ, double gmY) {
        if (monster.monsterEntity == null || !monster.monsterEntity.isValid()) {
            return;
        }

        Float newGroundY = scanForGround(world, newGridX, newGridZ, (float) gmY, MIN_SCAN_OFFSET, MAX_SCAN_OFFSET);
        float targetY = (newGroundY != null) ? newGroundY : monster.spawnY;

        teleportMonsterToY(world, monster, newGridX, newGridZ, targetY);
    }

    public static void updateHologramText(World world, MonsterState monster, String newText) {
        if (monster.numberHologram == null || !monster.numberHologram.isValid()) {
            return;
        }

        try {
            Store<EntityStore> store = world.getEntityStore().getStore();
            Nameplate nameplate = store.getComponent(monster.numberHologram, Nameplate.getComponentType());

            if (nameplate != null) {
                nameplate.setText(newText);
            }

        } catch (Exception e) {
            System.err.println("[Griddify] [ERROR] Failed to update hologram text: " + e.getMessage());
        }
    }

    public static void setMonsterYaw(World world, MonsterState monster, float yawRad) {
        if (monster.monsterEntity == null || !monster.monsterEntity.isValid()) {
            return;
        }
        try {
            Store<EntityStore> store = world.getEntityStore().getStore();

            TransformComponent transform = store.getComponent(monster.monsterEntity, TransformComponent.getComponentType());
            if (transform != null) {
                transform.setRotation(new Vector3f(0, yawRad, 0));
            }

            HeadRotation headRotation = store.getComponent(monster.monsterEntity, HeadRotation.getComponentType());
            if (headRotation != null) {
                headRotation.setRotation(new Vector3f(0, yawRad, 0));
            }
        } catch (Exception e) {
            System.err.println("[Griddify] [ERROR] Failed to set monster rotation: " + e.getMessage());
        }
    }

    // ====================================================================
    // GROUND SCANNING
    // ====================================================================

    /**
     * Scan for highest solid ground with clear, dry space above.
     * FIXED v3: After finding solid ground + clear air, also checks the FluidSection layer
     * for water/lava/poison. Positions with fluid above them are skipped.
     */
    private static Float scanForGround(World world, int gridX, int gridZ, float referenceY, int minOffset, int maxOffset) {
        int startBlockY = (int) Math.floor(referenceY - minOffset);
        int endBlockY = (int) Math.floor(referenceY - maxOffset);

        for (int blockY = startBlockY; blockY >= endBlockY; blockY--) {

            boolean hasGround = false;
            float maxHeight = 0;

            for (int xOff = 0; xOff < 2; xOff++) {
                for (int zOff = 0; zOff < 2; zOff++) {
                    int blockX = (gridX * 2) + xOff;
                    int blockZ = (gridZ * 2) + zOff;

                    BlockType block = world.getBlockType(new Vector3i(blockX, blockY, blockZ));

                    if (isSolidBlock(block)) {
                        float blockHeight = getBlockHeight(block);
                        if (blockHeight > maxHeight) {
                            maxHeight = blockHeight;
                        }
                        hasGround = true;
                    }
                }
            }

            if (hasGround) {
                float groundY = blockY + maxHeight;

                // Check solid blocks don't obstruct the 2-block space above
                if (isSpaceClear(world, gridX, gridZ, groundY, 2.0f)) {
                    // FIXED: Check fluid layer (water/lava/poison are a SEPARATE layer from blocks)
                    if (!hasFluidAbove(world, gridX, gridZ, groundY)) {
                        return groundY;
                    }
                    // Has fluid above — continue scanning lower
                }
            }
        }

        return null;
    }

    /**
     * Check if there's enough vertical space above ground (solid blocks only).
     */
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
                        float blockHeight = getBlockHeight(block);
                        float blockTop = checkY + blockHeight;

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
     * Hytale stores fluids in a separate FluidSection layer, NOT in the block layer.
     * So world.getBlockType() never sees fluids — we must read FluidSection directly.
     *
     * FluidSection.getFluidId() returns 0 for no fluid (Fluid.EMPTY_ID = 0).
     * Any non-zero value means fluid is present.
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
                                return true; // Has water, lava, poison, etc.
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // If fluid check fails, allow position (don't block on errors)
            System.err.println("[Griddify] [FLUID] Error checking fluid: " + e.getMessage());
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

    private static float getBlockHeight(BlockType block) {
        if (block == null || !isSolidBlock(block)) {
            return 0.0f;
        }

        try {
            int hitboxIndex = block.getHitboxTypeIndex();
            BlockBoundingBoxes hitboxAsset = BlockBoundingBoxes.getAssetMap().getAsset(hitboxIndex);

            if (hitboxAsset != null) {
                BlockBoundingBoxes.RotatedVariantBoxes variant = hitboxAsset.get(0);
                if (variant != null) {
                    Box boundingBox = variant.getBoundingBox();
                    return (float) (boundingBox.max.y - boundingBox.min.y);
                }
            }
        } catch (Exception e) {
            // Fallback to default height
        }

        String blockId = block.getId();
        if (blockId != null && blockId.toLowerCase().contains("slab")) {
            return 0.5f;
        }

        return 1.0f;
    }

    public static Float scanForGroundPublic(World world, int gridX, int gridZ, float gmY) {
        return scanForGround(world, gridX, gridZ, gmY, MIN_SCAN_OFFSET, MAX_SCAN_OFFSET);
    }
}