package com.gridifymydungeon.plugin.spell;

import com.gridifymydungeon.plugin.dnd.EncounterManager;
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
import java.util.Set;

/**
 * /Cast <spell name>
 *
 * Behaviour by pattern:
 *
 *   SELF / AURA     - Fires immediately on the NPC, no aiming needed.
 *   CONE / LINE     - Direction locked from player facing, fires immediately from NPC.
 *   SINGLE_TARGET   - NPC freezes. Red cell follows player body. /CastFinal fires on that cell.
 *   SPHERE/CUBE/etc - NPC freezes. Full area preview follows player body as aim point. /CastFinal fires there.
 */
public class CastCommand extends AbstractPlayerCommand {
    private final GridMoveManager playerManager;
    private final EncounterManager encounterManager;
    private final SpellVisualManager visualManager;
    private final RequiredArg<String> spellNameArg;

    public CastCommand(GridMoveManager playerManager, EncounterManager encounterManager,
                       SpellVisualManager visualManager) {
        super("Cast", "Prepare to cast a spell");
        this.playerManager = playerManager;
        this.encounterManager = encounterManager;
        this.visualManager = visualManager;
        this.spellNameArg = this.withRequiredArg("spell", "Spell name", ArgTypes.STRING);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {

        GridPlayerState state = playerManager.getState(playerRef);

        if (state.stats.getClassType() == null) {
            playerRef.sendMessage(Message.raw("[Griddify] Choose a class first! Use /GridClass").color("#FF0000"));
            return;
        }
        if (state.npcEntity == null || !state.npcEntity.isValid()) {
            playerRef.sendMessage(Message.raw("[Griddify] Enable grid movement first! Use /GridMove").color("#FF0000"));
            return;
        }

        String spellName = spellNameArg.get(context);
        SpellData spell = SpellDatabase.getSpell(spellName);
        if (spell == null) {
            playerRef.sendMessage(Message.raw("[Griddify] Unknown spell: " + spellName).color("#FF0000"));
            return;
        }

        if (!canAccessSpell(state, spell)) {
            if (spell.isSubclassSpell()) {
                playerRef.sendMessage(Message.raw("[Griddify] Wrong subclass! Yours: " +
                        (state.stats.getSubclassType() != null ? state.stats.getSubclassType().getDisplayName() : "none") +
                        " | Required: " + spell.getSubclass().getDisplayName()).color("#FF0000"));
            } else {
                playerRef.sendMessage(Message.raw("[Griddify] Wrong class! Yours: " +
                        state.stats.getClassType().getDisplayName() +
                        " | Required: " + spell.getClassType().getDisplayName()).color("#FF0000"));
            }
            return;
        }
        if (spell.getMinLevel() > state.stats.getLevel()) {
            playerRef.sendMessage(Message.raw("[Griddify] Level " + spell.getMinLevel() +
                    " required (you are level " + state.stats.getLevel() + ")").color("#FF0000"));
            return;
        }
        int cost = spell.getSlotCost();
        if (state.stats.getRemainingSpellSlots() < cost) {
            playerRef.sendMessage(Message.raw("[Griddify] Not enough spell slots! Cost: " + cost +
                    " | Remaining: " + state.stats.getRemainingSpellSlots()).color("#FF0000"));
            return;
        }

        int casterGridX = state.currentGridX;
        int casterGridZ = state.currentGridZ;

        float yaw = 0.0f;
        try { yaw = playerRef.getTransform().getRotation().getY(); } catch (Exception ignored) {}
        Direction8 direction = Direction8.fromYaw(yaw);

        SpellPattern pattern = spell.getPattern();

        // --- Patterns that fire immediately (no aiming walk needed) ---
        if (pattern == SpellPattern.SELF || pattern == SpellPattern.AURA) {
            Set<SpellPatternCalculator.GridCell> cells = SpellPatternCalculator.calculatePattern(
                    pattern, direction, casterGridX, casterGridZ,
                    spell.getRangeGrids(), spell.getAreaGrids());
            float playerY = getPlayerY(playerRef);
            visualManager.showSpellArea(playerRef.getUuid(), cells, world, playerY);

            // Save state with aim = caster (will fire immediately when /CastFinal is used,
            // or player can just call /CastFinal right away)
            state.setSpellCastingState(new SpellCastingState(spell, null, direction, casterGridX, casterGridZ));

            String label = spellLabel(spell);
            playerRef.sendMessage(Message.raw("[Griddify] " + label + " ready!").color("#FFD700"));
            playerRef.sendMessage(Message.raw("[Griddify] Pattern: " + pattern.name() + " | Use /CastFinal to fire.").color("#87CEEB"));
            return;
        }

        if (pattern == SpellPattern.CONE || pattern == SpellPattern.LINE) {
            Set<SpellPatternCalculator.GridCell> cells = SpellPatternCalculator.calculatePattern(
                    pattern, direction, casterGridX, casterGridZ,
                    spell.getRangeGrids(), spell.getAreaGrids());
            float playerY = getPlayerY(playerRef);
            visualManager.showSpellArea(playerRef.getUuid(), cells, world, playerY);

            state.setSpellCastingState(new SpellCastingState(spell, null, direction, casterGridX, casterGridZ));

            String label = spellLabel(spell);
            playerRef.sendMessage(Message.raw("[Griddify] " + label + " ready!").color("#FFD700"));
            playerRef.sendMessage(Message.raw("[Griddify] Direction: " + direction.name() + " | Use /CastFinal to fire.").color("#87CEEB"));
            System.out.println("[Griddify] [CAST] " + playerRef.getUsername() + " preparing " +
                    spell.getName() + " direction=" + direction.name());
            return;
        }

        // --- Patterns that need aiming (SINGLE_TARGET, SPHERE, CUBE, CYLINDER, CHAIN, WALL) ---
        state.freeze("casting");

        // Initial indicator: single cell under caster for SINGLE_TARGET, full area preview for area spells
        Set<SpellPatternCalculator.GridCell> initialCells;
        if (pattern == SpellPattern.SINGLE_TARGET) {
            initialCells = new java.util.HashSet<>();
            initialCells.add(new SpellPatternCalculator.GridCell(casterGridX, casterGridZ));
        } else {
            // Area spell: show full area centred on caster as starting preview
            initialCells = SpellPatternCalculator.calculatePattern(
                    pattern, direction, casterGridX, casterGridZ,
                    spell.getRangeGrids(), spell.getAreaGrids());
        }

        float playerY = getPlayerY(playerRef);
        visualManager.showSpellArea(playerRef.getUuid(), initialCells, world, playerY);

        state.setSpellCastingState(new SpellCastingState(spell, null, direction, casterGridX, casterGridZ));

        String label = spellLabel(spell);
        playerRef.sendMessage(Message.raw("[Griddify] CASTING: " + label).color("#FFD700"));
        playerRef.sendMessage(Message.raw("[Griddify] NPC frozen at (" + casterGridX + ", " + casterGridZ + ")").color("#FF6347"));
        playerRef.sendMessage(Message.raw("[Griddify] Range: " + spell.getRangeGrids() + " grids | Walk to aim").color("#87CEEB"));
        if (spell.getDamageDice() != null) {
            playerRef.sendMessage(Message.raw("[Griddify] Damage: " + spell.getDamageDice() + " " +
                    spell.getDamageType().name().toLowerCase()).color("#FF6B6B"));
        } else {
            playerRef.sendMessage(Message.raw("[Griddify] Effect: " + spell.getDescription()).color("#90EE90"));
        }
        playerRef.sendMessage(Message.raw("[Griddify] Use /CastFinal to fire | /CastCancel to abort").color("#00FF00"));

        System.out.println("[Griddify] [CAST] " + playerRef.getUsername() + " preparing " +
                spell.getName() + " NPC frozen at (" + casterGridX + ", " + casterGridZ + ")");
    }

    private String spellLabel(SpellData spell) {
        if (spell.isSubclassSpell()) return spell.getName() + " (" + spell.getSubclass().getDisplayName() + ")";
        return spell.getName() + " (" + spell.getClassType().getDisplayName() + ")";
    }

    private float getPlayerY(PlayerRef playerRef) {
        try { return (float) playerRef.getTransform().getPosition().getY(); } catch (Exception e) { return 0f; }
    }

    private boolean canAccessSpell(GridPlayerState state, SpellData spell) {
        if (spell.isSubclassSpell()) return spell.getSubclass() == state.stats.getSubclassType();
        return spell.getClassType() == state.stats.getClassType();
    }
}