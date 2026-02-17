package com.gridifymydungeon.plugin.spell;

import com.gridifymydungeon.plugin.dnd.EncounterManager;
import com.gridifymydungeon.plugin.dnd.MonsterState;
import com.gridifymydungeon.plugin.gridmove.GridMoveManager;
import com.gridifymydungeon.plugin.gridmove.GridPlayerState;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Set;

/**
 * /Cast <spell name> - Show spell range and prepare casting
 * Player must be targeting a monster (standing next to it)
 * UPDATED: Supports both base class spells (level 1+) and subclass spells (level 3+)
 */
public class CastCommand extends AbstractPlayerCommand {
    private final GridMoveManager playerManager;
    private final EncounterManager encounterManager;
    private final SpellVisualManager visualManager;
    private final RequiredArg<String> spellNameArg;

    public CastCommand(GridMoveManager playerManager, EncounterManager encounterManager,
                       SpellVisualManager visualManager) {
        super("Cast", "Prepare to cast a spell (must target monster)");
        this.playerManager = playerManager;
        this.encounterManager = encounterManager;
        this.visualManager = visualManager;
        this.spellNameArg = this.withRequiredArg("spell", "Spell name", ArgTypes.STRING);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {

        GridPlayerState state = playerManager.getState(playerRef);

        // Check if player has a class
        if (state.stats.getClassType() == null) {
            playerRef.sendMessage(Message.raw("[Griddify] Choose a class first! Use /GridClass").color("#FF0000"));
            return;
        }

        String spellName = spellNameArg.get(context);

        // Find spell
        SpellData spell = SpellDatabase.getSpell(spellName);
        if (spell == null) {
            playerRef.sendMessage(Message.raw("[Griddify] Unknown spell: " + spellName).color("#FF0000"));
            return;
        }

        // NEW: Check spell access based on type
        if (!canAccessSpell(state, spell)) {
            if (spell.isSubclassSpell()) {
                // Subclass spell - need correct subclass
                if (state.stats.getSubclassType() == null) {
                    playerRef.sendMessage(Message.raw("[Griddify] You need to choose a subclass first! (Level 3+ required)").color("#FF0000"));
                } else {
                    playerRef.sendMessage(Message.raw("[Griddify] You don't have access to this spell!").color("#FF0000"));
                    playerRef.sendMessage(Message.raw("[Griddify] Your subclass: " +
                            state.stats.getSubclassType().getDisplayName()).color("#FFA500"));
                    playerRef.sendMessage(Message.raw("[Griddify] Required: " +
                            spell.getSubclass().getDisplayName()).color("#FFA500"));
                }
            } else {
                // Base class spell - need correct class
                playerRef.sendMessage(Message.raw("[Griddify] You don't have access to this spell!").color("#FF0000"));
                playerRef.sendMessage(Message.raw("[Griddify] Your class: " +
                        state.stats.getClassType().getDisplayName()).color("#FFA500"));
                playerRef.sendMessage(Message.raw("[Griddify] Required: " +
                        spell.getClassType().getDisplayName()).color("#FFA500"));
            }
            return;
        }

        // Check level requirement
        if (spell.getMinLevel() > state.stats.getLevel()) {
            playerRef.sendMessage(Message.raw("[Griddify] Level " + spell.getMinLevel() +
                    " required (you are level " + state.stats.getLevel() + ")").color("#FF0000"));
            return;
        }

        // Check spell slots
        int cost = spell.getSlotCost();
        if (state.stats.getRemainingSpellSlots() < cost) {
            playerRef.sendMessage(Message.raw("[Griddify] Not enough spell slots!").color("#FF0000"));
            playerRef.sendMessage(Message.raw("[Griddify] Cost: " + cost + " | Remaining: " +
                    state.stats.getRemainingSpellSlots()).color("#FFA500"));
            return;
        }

        // Find target monster (must be adjacent)
        MonsterState targetMonster = findAdjacentMonster(playerRef, world, state);
        if (targetMonster == null) {
            playerRef.sendMessage(Message.raw("[Griddify] Stand next to a monster to target it!").color("#FF0000"));
            return;
        }

        // Get player position from grid coordinates
        int playerGridX = state.currentGridX;
        int playerGridZ = state.currentGridZ;

        // Get monster position from grid coordinates
        int monsterGridX = targetMonster.currentGridX;
        int monsterGridZ = targetMonster.currentGridZ;

        // Check if monster is within spell range
        int distance = SpellPatternCalculator.getDistance(playerGridX, playerGridZ, monsterGridX, monsterGridZ);
        if (distance > spell.getRangeGrids()) {
            playerRef.sendMessage(Message.raw("[Griddify] Target out of range!").color("#FF0000"));
            playerRef.sendMessage(Message.raw("[Griddify] Distance: " + distance + " grids | Max: " +
                    spell.getRangeGrids() + " grids").color("#FFA500"));
            return;
        }

        // Get player facing direction
        float yaw = 0.0f;
        try {
            yaw = playerRef.getTransform().getRotation().getY();
        } catch (Exception e) {
            System.out.println("[Griddify] [CAST] Could not get player yaw, using default");
        }
        Direction8 direction = Direction8.fromYaw(yaw);

        // Calculate affected area
        Set<SpellPatternCalculator.GridCell> affectedCells;

        if (spell.getPattern() == SpellPattern.CONE || spell.getPattern() == SpellPattern.LINE) {
            // Cone/line emanates from caster
            affectedCells = SpellPatternCalculator.calculatePattern(
                    spell.getPattern(), direction, playerGridX, playerGridZ,
                    spell.getRangeGrids(), spell.getAreaGrids()
            );
        } else {
            // Other spells centered on target
            affectedCells = SpellPatternCalculator.calculatePattern(
                    spell.getPattern(), direction, monsterGridX, monsterGridZ,
                    spell.getRangeGrids(), spell.getAreaGrids()
            );
        }

        // Show RED spell area
        visualManager.showSpellArea(playerRef.getUuid(), affectedCells);

        // Save casting state
        state.setSpellCastingState(new SpellCastingState(
                spell, targetMonster, direction, playerGridX, playerGridZ
        ));

        // Display spell info
        playerRef.sendMessage(Message.raw(""));
        playerRef.sendMessage(Message.raw("===========================================").color("#FF0000"));

        if (spell.isSubclassSpell()) {
            playerRef.sendMessage(Message.raw("  SPELL READY: " + spell.getName() +
                    " (" + spell.getSubclass().getDisplayName() + ")").color("#FFD700"));
        } else {
            playerRef.sendMessage(Message.raw("  SPELL READY: " + spell.getName() +
                    " (" + spell.getClassType().getDisplayName() + ")").color("#FFD700"));
        }

        playerRef.sendMessage(Message.raw("===========================================").color("#FF0000"));
        playerRef.sendMessage(Message.raw("  Target: " + targetMonster.getDisplayName()).color("#FFFFFF"));
        playerRef.sendMessage(Message.raw("  Range: " + distance + " / " + spell.getRangeGrids() + " grids").color("#87CEEB"));
        playerRef.sendMessage(Message.raw("  Cost: " + cost + " spell slots").color("#FFA500"));

        if (spell.getDamageDice() != null) {
            playerRef.sendMessage(Message.raw("  Damage: " + spell.getDamageDice() + " " +
                    spell.getDamageType().name().toLowerCase()).color("#FF6B6B"));
        }

        if (spell.isPersistent()) {
            playerRef.sendMessage(Message.raw("  Duration: " + spell.getDurationTurns() + " turns").color("#9370DB"));
        }

        playerRef.sendMessage(Message.raw(""));
        playerRef.sendMessage(Message.raw("  RED area shows spell effect!").color("#FF0000"));
        playerRef.sendMessage(Message.raw("  Use /CastFinal to execute").color("#00FF00"));
        playerRef.sendMessage(Message.raw("===========================================").color("#FF0000"));

        System.out.println("[Griddify] [CAST] " + playerRef.getUsername() + " preparing " +
                spell.getName() + " on " + targetMonster.getDisplayName());
    }

    /**
     * Check if player can access this spell
     * NEW: Handles both base class spells and subclass spells
     */
    private boolean canAccessSpell(GridPlayerState state, SpellData spell) {
        if (spell.isSubclassSpell()) {
            // Subclass spell - need exact subclass match
            return spell.getSubclass() == state.stats.getSubclassType();
        } else {
            // Base class spell - need class match
            return spell.getClassType() == state.stats.getClassType();
        }
    }

    /**
     * Find monster adjacent to player (within 1 grid)
     */
    private MonsterState findAdjacentMonster(PlayerRef playerRef, World world, GridPlayerState state) {
        int playerGridX = state.currentGridX;
        int playerGridZ = state.currentGridZ;

        List<MonsterState> monsters = encounterManager.getMonsters();
        for (MonsterState monster : monsters) {
            if (!monster.isAlive()) continue;

            int monsterGridX = monster.currentGridX;
            int monsterGridZ = monster.currentGridZ;

            int distance = SpellPatternCalculator.getDistance(playerGridX, playerGridZ, monsterGridX, monsterGridZ);
            if (distance <= 1) {
                return monster;
            }
        }

        return null;
    }
}