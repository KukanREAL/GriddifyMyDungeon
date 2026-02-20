package com.gridifymydungeon.plugin.spell.Command;

import com.gridifymydungeon.plugin.spell.FireballSpawner;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.Message;

import javax.annotation.Nonnull;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * /testfireball — spawns the animated Fireball2 model at the player's position,
 * then auto-despawns it after 3 seconds.
 *
 * Use this to verify the animation plays before wiring it into spells.
 */
public class TestFireballCommand extends AbstractPlayerCommand {

    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "fireball-despawn");
                t.setDaemon(true);
                return t;
            });

    public TestFireballCommand() {
        super("testfireball", "Spawn a test fireball at your position (auto-despawns after 3s)");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {

        // Get the player's current world position
        float spawnX, spawnY, spawnZ;
        try {
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) {
                playerRef.sendMessage(Message.raw("[Griddify] Could not read your position.").color("#FF0000"));
                return;
            }
            Vector3d pos = transform.getPosition();
            spawnX = (float) pos.getX();
            spawnY = (float) pos.getY() + 1.0f;  // spawn slightly above ground
            spawnZ = (float) pos.getZ();
        } catch (Exception e) {
            playerRef.sendMessage(Message.raw("[Griddify] Position error: " + e.getMessage()).color("#FF0000"));
            return;
        }

        playerRef.sendMessage(Message.raw("[Griddify] Spawning fireball at ("
                + String.format("%.1f", spawnX) + ", "
                + String.format("%.1f", spawnY) + ", "
                + String.format("%.1f", spawnZ) + ") — despawns in 3s").color("#FF6600"));

        // Spawn inside world thread
        world.execute(() -> {
            Ref<EntityStore> fireballRef = FireballSpawner.spawnAndAnimate(world, spawnX, spawnY, spawnZ);

            if (fireballRef == null) {
                playerRef.sendMessage(Message.raw("[Griddify] Fireball spawn failed — check server log for model errors.").color("#FF0000"));
                return;
            }

            // Schedule despawn after 3 seconds
            SCHEDULER.schedule(() ->
                            world.execute(() -> {
                                FireballSpawner.despawn(world, fireballRef);
                                System.out.println("[Griddify] [FIREBALL] Test fireball despawned.");
                            }),
                    3, TimeUnit.SECONDS
            );
        });
    }
}