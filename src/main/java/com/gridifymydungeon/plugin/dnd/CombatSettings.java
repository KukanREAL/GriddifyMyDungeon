package com.gridifymydungeon.plugin.dnd;

/**
 * Global combat settings
 */
public class CombatSettings {

    // Critical roll settings
    private boolean criticalRollsEnabled = false;
    private boolean criticalInitiativeEnabled = false;

    /**
     * Toggle critical rolls for dice
     */
    public void toggleCriticalRolls() {
        criticalRollsEnabled = !criticalRollsEnabled;
        System.out.println("[Griddify] [SETTINGS] Critical rolls: " +
                (criticalRollsEnabled ? "ENABLED" : "DISABLED"));
    }

    /**
     * Enable/disable critical rolls
     */
    public void setCriticalRolls(boolean enabled) {
        criticalRollsEnabled = enabled;
    }

    /**
     * Check if critical rolls enabled
     */
    public boolean isCriticalRollsEnabled() {
        return criticalRollsEnabled;
    }

    /**
     * Toggle critical initiative
     */
    public void toggleCriticalInitiative() {
        criticalInitiativeEnabled = !criticalInitiativeEnabled;
        System.out.println("[Griddify] [SETTINGS] Critical initiative: " +
                (criticalInitiativeEnabled ? "ENABLED" : "DISABLED"));
    }

    /**
     * Check if critical initiative enabled
     */
    public boolean isCriticalInitiativeEnabled() {
        return criticalInitiativeEnabled;
    }

    /**
     * Check if roll is critical success
     */
    public boolean isCriticalSuccess(int roll, int maxSides) {
        return criticalRollsEnabled && roll == maxSides;
    }

    /**
     * Check if roll is critical failure
     */
    public boolean isCriticalFailure(int roll) {
        return criticalRollsEnabled && roll == 1;
    }

    /**
     * Format dice result with critical notation
     */
    public String formatDiceResult(int roll, int sides) {
        if (!criticalRollsEnabled) {
            return String.valueOf(roll);
        }

        if (roll == sides) {
            return roll + " CRITICAL SUCCESS!";
        } else if (roll == 1) {
            return roll + " CRITICAL FAILURE!";
        } else {
            return String.valueOf(roll);
        }
    }
}
