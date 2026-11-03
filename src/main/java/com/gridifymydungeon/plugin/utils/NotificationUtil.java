package com.gridifymydungeon.plugin.utils;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;

/**
 * Utility class for sending styled messages
 * Uses Unicode symbols and colors since Hytale doesn't have notification API
 */
public class NotificationUtil {

    /**
     * Send a success message (green checkmark)
     */
    public static void sendSuccess(PlayerRef playerRef, String message) {
        playerRef.sendMessage(Message.raw("✓ " + message).color("#00FF00"));
    }

    /**
     * Send a success message with secondary text
     */
    public static void sendSuccess(PlayerRef playerRef, String primary, String secondary) {
        playerRef.sendMessage(Message.raw("✓ " + primary).color("#00FF00"));
        if (!secondary.isEmpty()) {
            playerRef.sendMessage(Message.raw("  " + secondary).color("#90EE90"));
        }
    }

    /**
     * Send an error message (red X)
     */
    public static void sendError(PlayerRef playerRef, String message) {
        playerRef.sendMessage(Message.raw("✗ " + message).color("#FF0000"));
    }

    /**
     * Send an error message with secondary text
     */
    public static void sendError(PlayerRef playerRef, String primary, String secondary) {
        playerRef.sendMessage(Message.raw("✗ " + primary).color("#FF0000"));
        if (!secondary.isEmpty()) {
            playerRef.sendMessage(Message.raw("  " + secondary).color("#FF6B6B"));
        }
    }

    /**
     * Send an info message (blue info symbol)
     */
    public static void sendInfo(PlayerRef playerRef, String message) {
        playerRef.sendMessage(Message.raw("ℹ " + message).color("#00BFFF"));
    }

    /**
     * Send a warning message (orange warning symbol)
     */
    public static void sendWarning(PlayerRef playerRef, String primary, String secondary) {
        playerRef.sendMessage(Message.raw("⚠ " + primary).color("#FFA500"));
        if (!secondary.isEmpty()) {
            playerRef.sendMessage(Message.raw("  " + secondary).color("#FFD700"));
        }
    }
}