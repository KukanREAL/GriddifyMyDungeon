package com.gridifymydungeon.plugin.spell;

import com.gridifymydungeon.plugin.dnd.MonsterState;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a persistent spell effect that lasts multiple turns
 * Examples: Wall of Fire, Spirit Guardians, Moonbeam
 * FIXED: Use Ref<EntityStore> instead of Entity for visual entities
 *
 * TODO FUTURE: Add terrain modification (spawn actual blocks/entities)
 * TODO FUTURE: Add concentration tracking (breaks when caster takes damage)
 * TODO FUTURE: Add movement tracking (some spells can be moved as bonus action)
 */
public class PersistentSpellEffect {
    private final UUID effectId;
    private final SpellData spell;
    private final PlayerRef caster;
    private final Set<SpellPatternCalculator.GridCell> affectedCells;
    private final int startTurn;
    private int turnsRemaining;
    private final boolean isConcentration;

    // FIXED: Visual entities (e.g., Spear_Cobalt markers) - use Ref<EntityStore> instead of Entity
    private final Set<Ref<EntityStore>> visualEntities;

    public PersistentSpellEffect(SpellData spell, PlayerRef caster,
                                 Set<SpellPatternCalculator.GridCell> affectedCells,
                                 int currentTurn) {
        this.effectId = UUID.randomUUID();
        this.spell = spell;
        this.caster = caster;
        this.affectedCells = new HashSet<>(affectedCells);
        this.startTurn = currentTurn;
        this.turnsRemaining = spell.getDurationTurns();
        this.isConcentration = false; // TODO FUTURE: Add concentration flag to SpellData
        this.visualEntities = new HashSet<>();
    }

    /**
     * Advance turn counter
     * @return true if effect should continue, false if expired
     */
    public boolean advanceTurn() {
        turnsRemaining--;
        return turnsRemaining > 0;
    }

    /**
     * Check if a grid cell is affected by this spell
     */
    public boolean isAffected(int gridX, int gridZ) {
        return affectedCells.contains(new SpellPatternCalculator.GridCell(gridX, gridZ));
    }

    /**
     * Apply effect to a monster in the area
     * Called each turn for persistent damage/effects
     */
    public void applyEffect(MonsterState monster) {
        // For now, just apply damage if spell has damage
        if (spell.getDamageDice() != null && !spell.getDamageDice().isEmpty()) {
            int damage = rollDamage(spell.getDamageDice());
            monster.takeDamage(damage);
        }

        // TODO FUTURE: Apply status effects (frightened, paralyzed, etc.)
        // TODO FUTURE: Apply healing for beneficial spells
        // TODO FUTURE: Apply movement restrictions (difficult terrain, etc.)
    }

    /**
     * Roll damage dice
     */
    private int rollDamage(String damageDice) {
        try {
            String[] parts = damageDice.split("d");
            int numDice = Integer.parseInt(parts[0]);
            int dieSize = Integer.parseInt(parts[1]);

            int total = 0;
            for (int i = 0; i < numDice; i++) {
                total += (int) (Math.random() * dieSize) + 1;
            }
            return total;
        } catch (Exception e) {
            return 0;
        }
    }

    // Getters
    public UUID getEffectId() { return effectId; }
    public SpellData getSpell() { return spell; }
    public PlayerRef getCaster() { return caster; }
    public Set<SpellPatternCalculator.GridCell> getAffectedCells() { return affectedCells; }
    public int getTurnsRemaining() { return turnsRemaining; }
    public boolean isConcentration() { return isConcentration; }
    public Set<Ref<EntityStore>> getVisualEntities() { return visualEntities; }

    /**
     * Add visual entity (e.g., Spear_Cobalt marker)
     * FIXED: Parameter is Ref<EntityStore> instead of Entity
     */
    public void addVisualEntity(Ref<EntityStore> entityRef) {
        visualEntities.add(entityRef);
    }

    /**
     * Cleanup visual entities when effect ends
     */
    public void cleanup() {
        for (Ref<EntityStore> entityRef : visualEntities) {
            if (entityRef != null && entityRef.isValid()) {
                // World will despawn these - just clear references
            }
        }
        visualEntities.clear();
    }
}