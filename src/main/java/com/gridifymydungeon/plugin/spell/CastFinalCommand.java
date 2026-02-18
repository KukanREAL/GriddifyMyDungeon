package com.gridifymydungeon.plugin.spell;

import com.gridifymydungeon.plugin.dnd.CombatSettings;
import com.gridifymydungeon.plugin.dnd.EncounterManager;
import com.gridifymydungeon.plugin.dnd.MonsterState;
import com.gridifymydungeon.plugin.dnd.RoleManager;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * /CastFinal - Execute the prepared spell.
 * Resolves target from the player's CURRENT real-world position (used to aim while NPC was frozen).
 * Verifies the aimed cell is within range of the FROZEN NPC position.
 * Unfreezes the NPC after casting.
 */
public class CastFinalCommand extends AbstractPlayerCommand {
    private final GridMoveManager playerManager;
    private final EncounterManager encounterManager;
    private final SpellVisualManager visualManager;
    private final CombatSettings combatSettings;
    private final RoleManager roleManager;

    public CastFinalCommand(GridMoveManager playerManager, EncounterManager encounterManager,
                            SpellVisualManager visualManager, CombatSettings combatSettings,
                            RoleManager roleManager) {
        super("CastFinal", "Execute prepared spell");
        this.playerManager = playerManager;
        this.encounterManager = encounterManager;
        this.visualManager = visualManager;
        this.combatSettings = combatSettings;
        this.roleManager = roleManager;
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {

        GridPlayerState state = playerManager.getState(playerRef);

        // Must have a spell prepared
        SpellCastingState castState = state.getSpellCastingState();
        if (castState == null || !castState.isValid()) {
            playerRef.sendMessage(Message.raw("[Griddify] No spell prepared! Use /Cast first").color("#FF0000"));
            return;
        }

        SpellData spell = castState.getSpell();

        // --- Resolve target from aim position ---
        // aimGridX/Z is updated by PlayerPositionTracker each time the player walks while casting.
        // state.currentGridX/Z is the FROZEN NPC position — don't use that for aiming.
        int aimGridX = castState.getAimGridX();
        int aimGridZ = castState.getAimGridZ();

        // Verify the aimed cell is within spell range of the FROZEN NPC
        int distanceFromCaster = SpellPatternCalculator.getDistance(
                castState.getCasterGridX(), castState.getCasterGridZ(),
                aimGridX, aimGridZ
        );

        if (distanceFromCaster > spell.getRangeGrids() && spell.getRangeGrids() > 0) {
            playerRef.sendMessage(Message.raw("[Griddify] Out of range! Aimed " + distanceFromCaster +
                    " grids, max is " + spell.getRangeGrids() + ". Walk body closer to the target.").color("#FF0000"));
            return;
        }

        // Commit: mark action used, calculate affected area.
        // Do NOT unfreeze yet — we re-freeze with reason "post_cast" after resolving so the NPC
        // stays at the cast position. PlayerPositionTracker auto-unfreezes when the player
        // physically moves to a different grid cell.
        state.hasUsedAction = true; // Consume action for this turn

        int cost = spell.getSlotCost();
        if (!state.stats.consumeSpellSlot(cost)) {
            playerRef.sendMessage(Message.raw("[Griddify] Not enough spell slots!").color("#FF0000"));
            state.clearSpellCastingState();
            visualManager.clearSpellVisuals(playerRef.getUuid(), world);
            return;
        }

        SpellPattern pattern = spell.getPattern();

        // Calculate affected cells based on pattern type:
        //   NPC-origin patterns (SELF, AURA, CONE, LINE, WALL): always fire from the frozen NPC cell.
        //   Multi-target: fire on each confirmed target cell individually.
        //   Targeted patterns (SINGLE_TARGET, SPHERE, CUBE, etc.): fire centred on the aimed cell.
        Set<SpellPatternCalculator.GridCell> affectedCells;

        if (spell.isMultiTarget() && !castState.getConfirmedTargets().isEmpty()) {
            // Multi-target: affected cells = all confirmed target cells
            affectedCells = new HashSet<>();
            for (SpellCastingState.GridCell c : castState.getConfirmedTargets()) {
                affectedCells.add(new SpellPatternCalculator.GridCell(c.x, c.z));
            }
            // If player never typed /CastTarget at all, fall back to current aim cell
            if (affectedCells.isEmpty()) {
                affectedCells.add(new SpellPatternCalculator.GridCell(aimGridX, aimGridZ));
            }
        } else {
            switch (pattern) {
                case SELF:
                case AURA:
                case CONE:
                case LINE:
                case WALL:
                    affectedCells = SpellPatternCalculator.calculatePattern(
                            pattern, castState.getDirection(),
                            castState.getCasterGridX(), castState.getCasterGridZ(),
                            spell.getRangeGrids(), spell.getAreaGrids());
                    break;
                default:
                    // SINGLE_TARGET, SPHERE, CUBE, CYLINDER, CHAIN
                    affectedCells = SpellPatternCalculator.calculatePattern(
                            pattern, castState.getDirection(),
                            aimGridX, aimGridZ,
                            spell.getRangeGrids(), spell.getAreaGrids());
                    break;
            }
        }

        // Debug: log affected cells and monster positions so direction issues are visible
        StringBuilder dbg = new StringBuilder("[Griddify] [CASTFINAL] " + spell.getName()
                + " dir=" + castState.getDirection().name()
                + " caster=(" + castState.getCasterGridX() + "," + castState.getCasterGridZ() + ")"
                + " cells=[");
        for (SpellPatternCalculator.GridCell c : affectedCells) dbg.append("(").append(c.x).append(",").append(c.z).append(")");
        dbg.append("] monsters=[");
        for (com.gridifymydungeon.plugin.dnd.MonsterState m : encounterManager.getMonsters()) {
            if (m.isAlive()) dbg.append(m.getDisplayName()).append("@(").append(m.currentGridX).append(",").append(m.currentGridZ).append(")");
        }
        dbg.append("]");
        System.out.println(dbg);

        // --- Calculate roll amount (damage or healing) ---
        int rollAmount = 0;
        if (spell.getDamageDice() != null && !spell.getDamageDice().isEmpty()) {
            rollAmount = rollDamage(spell.getDamageDice());
            rollAmount += state.stats.getSpellcastingModifier();
            if (rollAmount < 0) rollAmount = 0;
        }

        boolean isHeal = spell.isHealingSpell();
        boolean casterIsGM = roleManager.isGM(playerRef);

        int targetsAffected = 0;

        if (isHeal) {
            if (casterIsGM) {
                // GM/monster cast: heal monsters in area
                for (MonsterState monster : encounterManager.getMonsters()) {
                    if (!monster.isAlive()) continue;
                    if (affectedCells.contains(new SpellPatternCalculator.GridCell(monster.currentGridX, monster.currentGridZ))) {
                        targetsAffected++;
                        if (rollAmount > 0) monster.stats.heal(rollAmount);
                    }
                }
            } else {
                // Player cast: heal players (GridPlayerStates) in area
                for (com.gridifymydungeon.plugin.gridmove.GridPlayerState ps : playerManager.getAllStates()) {
                    if (ps.npcEntity == null || !ps.npcEntity.isValid()) continue;
                    if (affectedCells.contains(new SpellPatternCalculator.GridCell(ps.currentGridX, ps.currentGridZ))) {
                        targetsAffected++;
                        if (rollAmount > 0) ps.stats.heal(rollAmount);
                    }
                }
            }
        } else {
            // Damage spell — always hits monsters
            for (MonsterState monster : encounterManager.getMonsters()) {
                if (!monster.isAlive()) continue;
                if (affectedCells.contains(new SpellPatternCalculator.GridCell(monster.currentGridX, monster.currentGridZ))) {
                    targetsAffected++;
                    if (rollAmount > 0) monster.takeDamage(rollAmount);
                }
            }
        }

        int totalDamage = isHeal ? 0 : rollAmount;
        int monstersHit = targetsAffected;

        // Persistent spell handling
        if (spell.isPersistent()) {
            PersistentSpellEffect persistentEffect = new PersistentSpellEffect(
                    spell, playerRef, affectedCells,
                    0 // TODO: pass current turn from CombatManager
            );
            // TODO: persistentSpellManager.addEffect(persistentEffect);
            playerRef.sendMessage(Message.raw("  Duration: " + spell.getDurationTurns() + " turns").color("#9370DB"));
        }

        // Clear visuals and state
        visualManager.clearSpellVisuals(playerRef.getUuid(), world);
        state.clearSpellCastingState();

        // Re-freeze NPC at the cast position so it doesn't teleport if the player
        // walks around after casting. Auto-unfreezes when player moves to a new grid cell.
        state.freeze("post_cast");
        playerRef.sendMessage(Message.raw("[Griddify] NPC holds position — walk to a new cell to move it.").color("#87CEEB"));

        // --- Show world event title ---
        String effectText;
        if (isHeal) {
            effectText = rollAmount > 0 ? "+" + rollAmount + " HP healed" : spell.getDescription();
        } else {
            effectText = totalDamage > 0
                    ? totalDamage + " " + spell.getDamageType().name().toLowerCase() + " damage"
                    : spell.getDescription();
        }
        Message primaryTitle = Message.raw("[ " + spell.getName().toUpperCase() + " ]").color("#FF4500");
        Message secondaryTitle = Message.raw(effectText).color("#FFD700");
        EventTitleUtil.showEventTitleToWorld(primaryTitle, secondaryTitle, true, null, 4.0f, 0.5f, 1.0f, store);

        // --- Player notification ---
        String hitLabel = isHeal ? "target(s) healed" : "target(s) hit";
        Message primary = Message.raw("SPELL CAST!").color("#FFD700");
        Message secondary = Message.raw(spell.getName() + " → " + monstersHit + " " + hitLabel).color("#FFFFFF");
        ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Yellow", 1).toPacket();
        NotificationUtil.sendNotification(playerRef.getPacketHandler(), primary, secondary, icon, NotificationStyle.Default);

        playerRef.sendMessage(Message.raw(""));
        playerRef.sendMessage(Message.raw("===========================================").color("#FFD700"));
        playerRef.sendMessage(Message.raw("  " + spell.getName() + " -> (" + aimGridX + ", " + aimGridZ + ")").color("#FFD700"));
        if (isHeal && rollAmount > 0) {
            playerRef.sendMessage(Message.raw("  Healed: " + rollAmount + " HP").color("#00FF7F"));
        } else if (totalDamage > 0) {
            playerRef.sendMessage(Message.raw("  Damage: " + totalDamage + " " + spell.getDamageType().name().toLowerCase()).color("#FF6B6B"));
        } else {
            playerRef.sendMessage(Message.raw("  Effect applied: " + spell.getDescription()).color("#90EE90"));
        }
        playerRef.sendMessage(Message.raw("  Targets affected: " + monstersHit).color("#FFFFFF"));
        playerRef.sendMessage(Message.raw("  Spell slots remaining: " + state.stats.getRemainingSpellSlots()).color("#87CEEB"));
        playerRef.sendMessage(Message.raw("===========================================").color("#FFD700"));

        System.out.println("[Griddify] [CASTFINAL] " + playerRef.getUsername() + " cast " +
                spell.getName() + " for " + totalDamage + " damage (" + monstersHit + " targets)");
    }



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