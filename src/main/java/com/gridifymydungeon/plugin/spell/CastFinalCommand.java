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

        // Commit: unfreeze NPC, consume slot, mark action used, calculate affected area.
        state.unfreeze();
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
        //   Targeted patterns (SINGLE_TARGET, SPHERE, CUBE, etc.): fire centred on the aimed cell.
        Set<SpellPatternCalculator.GridCell> affectedCells;
        switch (pattern) {
            case SELF:
            case AURA:
            case CONE:
            case LINE:
            case WALL:
                // All NPC-origin patterns: direction already updated live as player walked around NPC
                affectedCells = SpellPatternCalculator.calculatePattern(
                        pattern, castState.getDirection(),
                        castState.getCasterGridX(), castState.getCasterGridZ(),
                        spell.getRangeGrids(), spell.getAreaGrids());
                break;
            default:
                // SINGLE_TARGET, SPHERE, CUBE, CYLINDER, CHAIN — centred on aimed cell
                affectedCells = SpellPatternCalculator.calculatePattern(
                        pattern, castState.getDirection(),
                        aimGridX, aimGridZ,
                        spell.getRangeGrids(), spell.getAreaGrids());
                break;
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

        // --- Calculate damage ---
        int totalDamage = 0;
        if (spell.getDamageDice() != null && !spell.getDamageDice().isEmpty()) {
            totalDamage = rollDamage(spell.getDamageDice());
            totalDamage += state.stats.getSpellcastingModifier();
            if (totalDamage < 0) totalDamage = 0; // damage can't go negative
        }

        // --- Apply to all monsters in affected area ---
        int monstersHit = 0;
        List<MonsterState> allMonsters = encounterManager.getMonsters();
        for (MonsterState monster : allMonsters) {
            if (!monster.isAlive()) continue;
            if (affectedCells.contains(new SpellPatternCalculator.GridCell(monster.currentGridX, monster.currentGridZ))) {
                monstersHit++;
                if (totalDamage > 0) {
                    monster.takeDamage(totalDamage);
                }
                // Non-damage spells still "hit" — effects go here
            }
        }

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

        // --- Show world event title ---
        String damageText = totalDamage > 0
                ? totalDamage + " " + spell.getDamageType().name().toLowerCase() + " damage"
                : spell.getDescription();
        Message primaryTitle = Message.raw("[ " + spell.getName().toUpperCase() + " ]").color("#FF4500");
        Message secondaryTitle = Message.raw(damageText).color("#FFD700");
        EventTitleUtil.showEventTitleToWorld(primaryTitle, secondaryTitle, true, null, 4.0f, 0.5f, 1.0f, store);

        // --- Player notification ---
        Message primary = Message.raw("SPELL CAST!").color("#FFD700");
        Message secondary = Message.raw(spell.getName() + " hit " + monstersHit + " target(s)").color("#FFFFFF");
        ItemWithAllMetadata icon = new ItemStack("Ingredient_Crystal_Yellow", 1).toPacket();
        NotificationUtil.sendNotification(playerRef.getPacketHandler(), primary, secondary, icon, NotificationStyle.Default);

        playerRef.sendMessage(Message.raw(""));
        playerRef.sendMessage(Message.raw("===========================================").color("#FFD700"));
        playerRef.sendMessage(Message.raw("  " + spell.getName() + " -> (" + aimGridX + ", " + aimGridZ + ")").color("#FFD700"));
        if (totalDamage > 0) {
            playerRef.sendMessage(Message.raw("  Damage: " + totalDamage + " " + spell.getDamageType().name().toLowerCase()).color("#FF6B6B"));
        } else {
            playerRef.sendMessage(Message.raw("  Effect applied: " + spell.getDescription()).color("#90EE90"));
        }
        playerRef.sendMessage(Message.raw("  Monsters hit: " + monstersHit).color("#FFFFFF"));
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