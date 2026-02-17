package com.gridifymydungeon.plugin.spell;

import com.gridifymydungeon.plugin.dnd.MonsterState;
import com.hypixel.hytale.server.core.Message;

import java.util.*;

/**
 * Manages all persistent spell effects in combat
 * Tracks turn-based durations and applies effects
 * FIXED: Use grid coordinates instead of entity positions
 */
public class PersistentSpellManager {
    private final List<PersistentSpellEffect> activeEffects = new ArrayList<>();
    private int currentTurn = 0;

    /**
     * Add a new persistent spell effect
     */
    public void addEffect(PersistentSpellEffect effect) {
        activeEffects.add(effect);
        System.out.println("[Griddify] [PERSISTENT] Added effect: " + effect.getSpell().getName() +
                " (duration: " + effect.getTurnsRemaining() + " turns)");
    }

    /**
     * Advance to next turn - process all persistent effects
     * @param monsters All monsters in combat
     */
    public void advanceTurn(List<MonsterState> monsters) {
        currentTurn++;

        List<PersistentSpellEffect> expiredEffects = new ArrayList<>();

        // Process each active effect
        for (PersistentSpellEffect effect : activeEffects) {
            // Apply effect to monsters in area
            for (MonsterState monster : monsters) {
                if (!monster.isAlive()) continue;

                // FIXED: Use monster grid coordinates instead of entity position
                int monsterGridX = monster.currentGridX;
                int monsterGridZ = monster.currentGridZ;

                if (effect.isAffected(monsterGridX, monsterGridZ)) {
                    effect.applyEffect(monster);

                    // Notify caster
                    if (effect.getCaster() != null) {
                        effect.getCaster().sendMessage(
                                Message.raw("[" + effect.getSpell().getName() + "] " +
                                        monster.getDisplayName() + " takes damage!").color("#FF6B6B")
                        );
                    }
                }
            }

            // Advance turn counter
            if (!effect.advanceTurn()) {
                expiredEffects.add(effect);
            }
        }

        // Remove expired effects
        for (PersistentSpellEffect effect : expiredEffects) {
            removeEffect(effect);
        }
    }

    /**
     * Remove an effect and cleanup
     */
    public void removeEffect(PersistentSpellEffect effect) {
        activeEffects.remove(effect);
        effect.cleanup();

        if (effect.getCaster() != null) {
            effect.getCaster().sendMessage(
                    Message.raw("[" + effect.getSpell().getName() + "] Effect expired").color("#808080")
            );
        }

        System.out.println("[Griddify] [PERSISTENT] Removed effect: " + effect.getSpell().getName());
    }

    /**
     * Clear all effects (end of combat)
     */
    public void clearAllEffects() {
        for (PersistentSpellEffect effect : new ArrayList<>(activeEffects)) {
            removeEffect(effect);
        }
        currentTurn = 0;
    }

    /**
     * Get all active effects
     */
    public List<PersistentSpellEffect> getActiveEffects() {
        return new ArrayList<>(activeEffects);
    }

    /**
     * Get current turn number
     */
    public int getCurrentTurn() {
        return currentTurn;
    }

    /**
     * Check if a grid cell has any active spell effects
     */
    public List<PersistentSpellEffect> getEffectsAt(int gridX, int gridZ) {
        List<PersistentSpellEffect> effects = new ArrayList<>();
        for (PersistentSpellEffect effect : activeEffects) {
            if (effect.isAffected(gridX, gridZ)) {
                effects.add(effect);
            }
        }
        return effects;
    }
}