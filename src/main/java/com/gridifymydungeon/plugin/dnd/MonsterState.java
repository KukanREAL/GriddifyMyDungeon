package com.gridifymydungeon.plugin.dnd;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Tracks monster state
 * ADDED: isFlying field to ignore height checks
 */
public class MonsterState {

    // Monster info
    public final int monsterNumber;
    public final String monsterName;
    public CharacterStats stats;

    // Grid position
    public int currentGridX;
    public int currentGridZ;

    // Movement
    public double maxMoves = 6.0;
    public double remainingMoves = 6.0;
    public Vector3d lastGMPosition;

    // Entity references
    public Ref<EntityStore> monsterEntity;
    public Ref<EntityStore> numberHologram;
    public float spawnY;

    // Freeze tracking (monster freezes, not GM)
    public boolean isFrozen = false;
    public String freezeReason = null;

    // ADDED: Flying mode (ignores height checks)
    public boolean isFlying = false;

    public MonsterState(int number, String name) {
        this.monsterNumber = number;
        this.monsterName = name;
        this.stats = new CharacterStats();
    }

    public String getDisplayName() {
        return monsterName + " #" + monsterNumber;
    }

    public boolean hasMovesRemaining(double cost) {
        return remainingMoves >= cost;
    }

    public void consumeMoves(double cost) {
        remainingMoves -= cost;
        if (remainingMoves < 0) {
            remainingMoves = 0;
        }
    }

    public void resetMoves() {
        remainingMoves = maxMoves;
    }

    /**
     * Freeze monster movement (monster stays in place, GM can still move)
     */
    public void freeze(String reason) {
        this.isFrozen = true;
        this.freezeReason = reason;
    }

    /**
     * Unfreeze monster movement
     */
    public void unfreeze() {
        this.isFrozen = false;
        this.freezeReason = null;
    }
    /**
     * Check if monster is alive
     */
    public boolean isAlive() {
        return stats.currentHP > 0;
    }

    /**
     * Apply damage to monster
     */
    public void takeDamage(int damage) {
        stats.takeDamage(damage);
        // If you have a health bar update method, call it here
    }
}