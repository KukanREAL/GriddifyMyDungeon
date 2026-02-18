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
import java.util.HashSet;
import java.util.Set;

/**
 * /Cast <spell>
 *
 * Pattern behaviour:
 *
 *   SELF / AURA         - Fires from NPC immediately, no freeze needed. Just /CastFinal.
 *
 *   CONE / LINE / WALL  - NPC freezes. Player walks AROUND the NPC to rotate the
 *                         direction (player position relative to NPC = facing direction).
 *                         Red overlay updates live. /CastFinal fires.
 *
 *   SINGLE_TARGET       - NPC freezes. Red cell follows player body as aim. /CastFinal fires.
 *
 *   SPHERE / CUBE / etc - NPC freezes. Full area preview centred on player body. /CastFinal fires.
 */
public class CastCommand extends AbstractPlayerCommand {
    private final GridMoveManager playerManager;
    private final EncounterManager encounterManager;
    private final SpellVisualManager visualManager;
    private final com.gridifymydungeon.plugin.dnd.RoleManager roleManager;
    private final RequiredArg<String> spellNameArg;

    public CastCommand(GridMoveManager playerManager, EncounterManager encounterManager,
                       SpellVisualManager visualManager, com.gridifymydungeon.plugin.dnd.RoleManager roleManager) {
        super("Cast", "Prepare to cast a spell");
        this.playerManager = playerManager;
        this.encounterManager = encounterManager;
        this.visualManager = visualManager;
        this.roleManager = roleManager;
        this.spellNameArg = this.withRequiredArg("spell", "Spell name", ArgTypes.STRING);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {

        GridPlayerState state = playerManager.getState(playerRef);

        // GM controlling a monster can cast without having a personal class
        boolean isGmControlling = roleManager.isGM(playerRef) && encounterManager.getControlledMonster() != null;
        if (state.stats.getClassType() == null && !isGmControlling) {
            playerRef.sendMessage(Message.raw("[Griddify] Choose a class first! Use /GridClass").color("#FF0000"));
            return;
        }
        if (state.npcEntity == null || !state.npcEntity.isValid()) {
            playerRef.sendMessage(Message.raw("[Griddify] Enable grid movement first! Use /GridMove").color("#FF0000"));
            return;
        }
        if (state.hasUsedAction) {
            playerRef.sendMessage(Message.raw("[Griddify] Already used your action this turn! Use /endturn to reset.").color("#FF0000"));
            return;
        }

        String spellName = spellNameArg.get(context);
        SpellData spell = SpellDatabase.getSpell(spellName);
        if (spell == null) {
            playerRef.sendMessage(Message.raw("[Griddify] Unknown spell: " + spellName).color("#FF0000"));
            return;
        }
        if (!canAccessSpell(state, spell, playerRef)) {
            if (spell.isSubclassSpell()) {
                playerRef.sendMessage(Message.raw("[Griddify] Wrong subclass! Required: " +
                        spell.getSubclass().getDisplayName()).color("#FF0000"));
            } else if (spell.getClassType() == null) {
                playerRef.sendMessage(Message.raw("[Griddify] Monster attacks require /control <#> first!").color("#FF0000"));
            } else {
                String have = isGmControlling
                        ? (encounterManager.getControlledMonster().stats.getClassType() != null
                        ? encounterManager.getControlledMonster().stats.getClassType().getDisplayName() : "no class")
                        : (state.stats.getClassType() != null ? state.stats.getClassType().getDisplayName() : "none");
                playerRef.sendMessage(Message.raw("[Griddify] Wrong class! Have: " + have +
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
        SpellPattern pattern = spell.getPattern();

        // Initial direction: player's current body yaw (will rotate live for CONE/LINE/WALL)
        float yaw = 0f;
        try { yaw = playerRef.getTransform().getRotation().getY(); } catch (Exception ignored) {}
        Direction8 initialDirection = Direction8.fromYaw(yaw);

        float playerY = getPlayerY(playerRef);

        // -----------------------------------------------------------------------
        // SELF / AURA: instant, no freeze
        // -----------------------------------------------------------------------
        if (pattern == SpellPattern.SELF || pattern == SpellPattern.AURA) {
            Set<SpellPatternCalculator.GridCell> cells = SpellPatternCalculator.calculatePattern(
                    pattern, initialDirection, casterGridX, casterGridZ,
                    spell.getRangeGrids(), spell.getAreaGrids());
            visualManager.showSpellArea(playerRef.getUuid(), cells, world, playerY);
            state.setSpellCastingState(new SpellCastingState(spell, null, initialDirection, casterGridX, casterGridZ));

            playerRef.sendMessage(Message.raw("[Griddify] " + spellLabel(spell) + " ready!").color("#FFD700"));
            playerRef.sendMessage(Message.raw("[Griddify] Pattern fires on NPC. Use /CastFinal.").color("#87CEEB"));
            System.out.println("[Griddify] [CAST] " + playerRef.getUsername() + " preparing " + spell.getName() + " (instant)");
            return;
        }

        // -----------------------------------------------------------------------
        // All other patterns: freeze NPC, player walks to aim / rotate
        // -----------------------------------------------------------------------
        state.freeze("casting");
        state.setSpellCastingState(new SpellCastingState(spell, null, initialDirection, casterGridX, casterGridZ));

        // Compute initial overlay
        Set<SpellPatternCalculator.GridCell> initialCells = computeOverlay(
                pattern, initialDirection, casterGridX, casterGridZ, spell, casterGridX, casterGridZ);
        visualManager.showSpellArea(playerRef.getUuid(), initialCells, world, playerY);

        String label = spellLabel(spell);
        playerRef.sendMessage(Message.raw("[Griddify] CASTING: " + label).color("#FFD700"));
        playerRef.sendMessage(Message.raw("[Griddify] NPC frozen at (" + casterGridX + ", " + casterGridZ + ")").color("#FF6347"));

        if (pattern == SpellPattern.CONE || pattern == SpellPattern.LINE || pattern == SpellPattern.WALL) {
            playerRef.sendMessage(Message.raw("[Griddify] Walk AROUND your NPC to rotate the direction.").color("#87CEEB"));
        } else {
            playerRef.sendMessage(Message.raw("[Griddify] Range: " + spell.getRangeGrids() + " grids | Walk to aim").color("#87CEEB"));
        }

        if (spell.getDamageDice() != null) {
            playerRef.sendMessage(Message.raw("[Griddify] Damage: " + spell.getDamageDice() + " " +
                    spell.getDamageType().name().toLowerCase()).color("#FF6B6B"));
        } else {
            playerRef.sendMessage(Message.raw("[Griddify] Effect: " + spell.getDescription()).color("#90EE90"));
        }
        playerRef.sendMessage(Message.raw("[Griddify] /CastFinal to fire | /CastCancel to abort").color("#00FF00"));

        System.out.println("[Griddify] [CAST] " + playerRef.getUsername() + " preparing " +
                spell.getName() + " pattern=" + pattern.name() + " frozen at (" + casterGridX + ", " + casterGridZ + ")");
    }

    /**
     * Compute the red overlay cells based on pattern and current player position.
     * For directional patterns: origin=NPC, direction from player-relative-to-NPC.
     * For targeted patterns: centred on player body (aim cell).
     */
    public static Set<SpellPatternCalculator.GridCell> computeOverlay(
            SpellPattern pattern, Direction8 direction,
            int casterGridX, int casterGridZ,
            SpellData spell,
            int playerGridX, int playerGridZ) {

        switch (pattern) {
            case CONE:
            case LINE:
            case WALL:
            case SELF:
            case AURA:
                // Always from NPC, direction already updated
                return SpellPatternCalculator.calculatePattern(
                        pattern, direction, casterGridX, casterGridZ,
                        spell.getRangeGrids(), spell.getAreaGrids());

            case SINGLE_TARGET:
                // One cell under the player body
                Set<SpellPatternCalculator.GridCell> cell = new HashSet<>();
                cell.add(new SpellPatternCalculator.GridCell(playerGridX, playerGridZ));
                return cell;

            default:
                // SPHERE, CUBE, CYLINDER, CHAIN — area centred on player body
                return SpellPatternCalculator.calculatePattern(
                        pattern, direction, playerGridX, playerGridZ,
                        spell.getRangeGrids(), spell.getAreaGrids());
        }
    }

    private String spellLabel(SpellData spell) {
        if (spell.isSubclassSpell()) return spell.getName() + " (" + spell.getSubclass().getDisplayName() + ")";
        return spell.getName() + " (" + spell.getClassType().getDisplayName() + ")";
    }
    private float getPlayerY(PlayerRef playerRef) {
        try { return (float) playerRef.getTransform().getPosition().getY(); } catch (Exception e) { return 0f; }
    }
    private boolean canAccessSpell(GridPlayerState state, SpellData spell, PlayerRef playerRef) {
        // Monster basic attacks (classType == null) - only GM controlling a monster can use them
        if (spell.getClassType() == null && !spell.isSubclassSpell()) {
            return roleManager.isGM(playerRef) && encounterManager.getControlledMonster() != null;
        }
        // GM controlling a monster with a class uses the monster's class
        if (roleManager.isGM(playerRef)) {
            com.gridifymydungeon.plugin.dnd.MonsterState controlled = encounterManager.getControlledMonster();
            if (controlled != null) {
                if (spell.isSubclassSpell()) return spell.getSubclass() == controlled.stats.getSubclassType();
                return spell.getClassType() == controlled.stats.getClassType();
            }
        }
        // Regular player: check their own class
        if (spell.isSubclassSpell()) return spell.getSubclass() == state.stats.getSubclassType();
        return spell.getClassType() == state.stats.getClassType();
    }
}