package com.gridifymydungeon.plugin.gridmove.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.protocol.packets.camera.SetServerCamera;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * /gridcam command - Switch camera type 0, 1
 */
public class GridCamCommand extends AbstractPlayerCommand {

    // Required argument for camera type
    private final RequiredArg<Integer> camTypeArg;

    public GridCamCommand() {
        super("gridcam", "Switch camera type 0|1");

        // Allow Adventure mode players to use this command
        this.setPermissionGroup(GameMode.Adventure);

        // Camera number argument 0,1,2
        this.camTypeArg = this.withRequiredArg("camera", "Camera type 0|1", ArgTypes.INTEGER);
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {

        int camType = camTypeArg.get(context);

        if (camType < 0 || camType > 2) {
            playerRef.sendMessage(Message.raw("Camera number must be 0, 1"));
            return;
        }

        System.out.println("[GridCam] [INFO] Player " + playerRef.getUsername() + " switching to camera type " + camType);

        switch (camType) {
            case 0 -> resetCamera(playerRef);
            case 1 -> applyCamera(playerRef, 1, -90f);
        }
    }

    private void applyCamera(PlayerRef player, int camType, float rotationY) {
        float distance = 0f; // Fixed distance, no zoom

        ServerCameraSettings settings = new ServerCameraSettings();
        settings.positionLerpSpeed = 0.3F;
        settings.rotationLerpSpeed = 0.85F;
        settings.distance = distance;
        settings.displayCursor = true;
        settings.isFirstPerson = true; // First-person mode
        settings.movementForceRotationType = MovementForceRotationType.Custom;
        settings.movementMultiplier = new Vector3f(0.5f, 0.0f, 0.5f);  // Lock Z-axis
        settings.eyeOffset = true;
        settings.positionDistanceOffsetType = PositionDistanceOffsetType.DistanceOffset;
        settings.rotationType = RotationType.Custom;
        settings.rotation = new Direction(0f, (float) Math.toRadians(rotationY), 0f);
        settings.mouseInputType = MouseInputType.LookAtPlane;
        settings.planeNormal = new Vector3f(0f, 1f, 0f);

        player.getPacketHandler().writeNoCache(new SetServerCamera(ClientCameraView.Custom, true, settings));

        player.sendMessage(Message.raw("Camera switched to type " + camType));
        System.out.println("[GridCam] [INFO] Applied camera type " + camType + " with rotation " + rotationY + "°");
    }

    private void resetCamera(PlayerRef player) {
        player.getPacketHandler().writeNoCache(new SetServerCamera(ClientCameraView.Custom, false, null));
        player.sendMessage(Message.raw("Camera reset to default!"));
        System.out.println("[GridCam] [INFO] Camera reset to default");
    }
}
