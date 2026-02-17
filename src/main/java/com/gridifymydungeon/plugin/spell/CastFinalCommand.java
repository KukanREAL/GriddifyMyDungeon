package com.gridifymydungeon.plugin.spell;

import com.gridifymydungeon.plugin.dnd.CombatSettings;
import com.gridifymydungeon.plugin.dnd.EncounterManager;
import com.gridifymydungeon.plugin.dnd.MonsterState;
import com.gridifymydungeon.plugin.gridmove.GridMoveManager;
import com.gridifymydungeon.plugin.gridmove.GridPlayerState;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.ItemWithAllMetadata;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.EventTitleUtil;
import com.hypixel.hytale.server.core.util.NotificationUtil;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Set;

/**
 * /CastFinal - Execute prepared spell
 * FIXED: Use grid coordinates instead of entity positions
 * TODO FUTURE: Implement saving throws (DEX/WIS/CON saves vs spell DC)
 * TODO FUTURE: Implement status effects (frightened, paralyzed, charmed, etc.)
 * TODO FUTURE: Implement concentration mechanics
 * TODO FUTURE: Implement smart targeting (exclude allies option)
 */
public class CastFinalCommand extends AbstractPlayerCommand {
    private final GridMoveManager playerManager;
    private final EncounterManager encounterManager;
    private final SpellVisualManager visualManager;
    private final CombatSettings combatSettings;

    public CastFinalCommand(GridMoveManager playerManager, EncounterManager encounterManager,
                            SpellVisualManager visualManager, CombatSettings combatSettings) {
        super("CastFinal", "Execute prepared spell");
        this.playerManager = playerManager;
        this.encounterManager = encounterManager;
        this.visualManager = visualManager;
        this.combatSettings = combatSettings;
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {

        GridPlayerState state = playerManager.getState(playerRef);

        // Check if player has a spell prepared
        SpellCastingState castState = state.getSpellCastingState();
        if (castState == null || !castState.isValid()) {
            playerRef.sendMessage(Message.raw("[Griddify] No spell prepared! Use /Cast first").color("#FF0000"));
            return;
        }

        SpellData spell = castState.getSpell();
        MonsterState targetMonster = castState.getTargetMonster();

        // Check if target is still alive
        if (!targetMonster.isAlive()) {
            playerRef.sendMessage(Message.raw("[Griddify] Target is already dead!").color("#FF0000"));
            state.clearSpellCastingState();
            visualManager.clearSpellVisuals(playerRef.getUuid());
            return;
        }

        // FIXED: Check if player moved too far from original position using grid coords
        int currentGridX = state.currentGridX;
        int currentGridZ = state.currentGridZ;
        int distanceMoved = SpellPatternCalculator.getDistance(
                castState.getCasterGridX(), castState.getCasterGridZ(),
                currentGridX, currentGridZ
        );

        if (distanceMoved > spell.getRangeGrids()) {
            playerRef.sendMessage(Message.raw("[Griddify] You moved too far! Spell cancelled").color("#FF0000"));
            state.clearSpellCastingState();
            visualManager.clearSpellVisuals(playerRef.getUuid());
            return;
        }

        // Consume spell slots
        int cost = spell.getSlotCost();
        if (!state.stats.consumeSpellSlot(cost)) {
            playerRef.sendMessage(Message.raw("[Griddify] Not enough spell slots!").color("#FF0000"));
            return;
        }

        // FIXED: Calculate affected area using grid coordinates
        int monsterGridX = targetMonster.currentGridX;
        int monsterGridZ = targetMonster.currentGridZ;

        Set<SpellPatternCalculator.GridCell> affectedCells;
        if (spell.getPattern() == SpellPattern.CONE || spell.getPattern() == SpellPattern.LINE) {
            affectedCells = SpellPatternCalculator.calculatePattern(
                    spell.getPattern(), castState.getDirection(),
                    castState.getCasterGridX(), castState.getCasterGridZ(),
                    spell.getRangeGrids(), spell.getAreaGrids()
            );
        } else {
            affectedCells = SpellPatternCalculator.calculatePattern(
                    spell.getPattern(), castState.getDirection(),
                    monsterGridX, monsterGridZ,
                    spell.getRangeGrids(), spell.getAreaGrids()
            );
        }

        // Calculate damage (if spell deals damage)
        int totalDamage = 0;
        if (spell.getDamageDice() != null && !spell.getDamageDice().isEmpty()) {
            totalDamage = rollDamage(spell.getDamageDice());

            // Add spellcasting modifier
            totalDamage += state.stats.getSpellcastingModifier();
        }

        // Apply damage to ALL monsters in affected area (including allies - friendly fire!)
        int monstersHit = 0;
        List<MonsterState> allMonsters = encounterManager.getMonsters();

        for (MonsterState monster : allMonsters) {
            if (!monster.isAlive()) continue;

            // FIXED: Use monster grid coordinates
            int mGridX = monster.currentGridX;
            int mGridZ = monster.currentGridZ;

            // Check if monster is in affected area
            if (affectedCells.contains(new SpellPatternCalculator.GridCell(mGridX, mGridZ))) {
                if (totalDamage > 0) {
                    monster.takeDamage(totalDamage);
                    monstersHit++;
                }
            }
        }

        // If spell is persistent, add to persistent effect manager
        if (spell.isPersistent()) {
            PersistentSpellEffect persistentEffect = new PersistentSpellEffect(
                    spell,
                    playerRef,
                    affectedCells,
                    0 // TODO: Get current turn from combat manager
            );

            // TODO: Pass persistent manager from constructor and add:
            // persistentSpellManager.addEffect(persistentEffect);

            playerRef.sendMessage(Message.raw("  Duration: " + spell.getDurationTurns() + " turns").color("#9370DB"));
        }

        // Show dramatic spell cast event title
        Message primaryTitle = Message.raw("✦ " + spell.getName().toUpperCase() + " ✦").color("#FF0000");
        Message secondaryTitle = Message.raw(totalDamage + " " + spell.getDamageType().name() + " damage").color("#FFD700");

        EventTitleUtil.showEventTitleToWorld(
                primaryTitle,
                secondaryTitle,
                true, // Major event
                null,
                4.0f,
                0.5f,
                1.0f,
                store
        );

        // Clear visuals
        visualManager.clearSpellVisuals(playerRef.getUuid());
        state.clearSpellCastingState();

        // Notify player
        Message primary = Message.raw("SPELL CAST!").color("#FFD700");
        Message secondary = Message.raw(spell.getName() + " - " + monstersHit + " targets hit").color("#FFFFFF");
        ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Yellow", 1).toPacket();

        NotificationUtil.sendNotification(
                playerRef.getPacketHandler(),
                primary,
                secondary,
                icon,
                NotificationStyle.Default
        );

        playerRef.sendMessage(Message.raw(""));
        playerRef.sendMessage(Message.raw("  Damage: " + totalDamage + " " + spell.getDamageType().name()).color("#FF6B6B"));
        playerRef.sendMessage(Message.raw("  Targets hit: " + monstersHit).color("#FFFFFF"));
        playerRef.sendMessage(Message.raw("  Spell slots remaining: " + state.stats.getRemainingSpellSlots()).color("#87CEEB"));

        System.out.println("[Griddify] [CASTFINAL] " + playerRef.getUsername() + " cast " +
                spell.getName() + " for " + totalDamage + " damage (" + monstersHit + " targets)");
    }

    /**
     * Roll damage dice (e.g., "3d6" = roll 3 six-sided dice)
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
            System.err.println("[Griddify] Failed to parse damage dice: " + damageDice);
            return 0;
        }
    }
}