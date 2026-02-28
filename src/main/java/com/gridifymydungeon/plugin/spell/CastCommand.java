package com.gridifymydungeon.plugin.spell;
import com.gridifymydungeon.plugin.debug.DebugRoleWrapper;

import com.gridifymydungeon.plugin.dnd.EncounterManager;
import com.gridifymydungeon.plugin.gridmove.CollisionDetector;
import com.gridifymydungeon.plugin.gridmove.GridMoveManager;
import com.gridifymydungeon.plugin.gridmove.GridOverlayManager;
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
 * FIX #1: showRangeOverlay now receives playerRef so Grid_Range tiles are
 * spawned at y=-30, teleported to real Y, and hidden from non-owners.
 */
public class CastCommand extends AbstractPlayerCommand {
    private final GridMoveManager playerManager;
    private final EncounterManager encounterManager;
    private final SpellVisualManager visualManager;
    private final com.gridifymydungeon.plugin.dnd.RoleManager roleManager;
    private final CollisionDetector collisionDetector;
    private final RequiredArg<String> spellNameArg;

    public CastCommand(GridMoveManager playerManager, EncounterManager encounterManager,
                       SpellVisualManager visualManager, com.gridifymydungeon.plugin.dnd.RoleManager roleManager,
                       CollisionDetector collisionDetector) {
        super("Cast", "Prepare to cast a spell");
        this.playerManager = playerManager;
        this.encounterManager = encounterManager;
        this.visualManager = visualManager;
        this.roleManager = roleManager;
        this.collisionDetector = collisionDetector;
        this.spellNameArg = this.withRequiredArg("spell", "Spell name", ArgTypes.STRING);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {

        GridPlayerState state = playerManager.getState(playerRef);

        boolean isGmControlling = DebugRoleWrapper.isGM(roleManager, playerRef) && encounterManager.getControlledMonster() != null;
        if (state.stats.getClassType() == null && !isGmControlling) {
            playerRef.sendMessage(Message.raw("[Griddify] Choose a class first! Use /GridClass").color("#FF0000"));
            return;
        }
        if (!isGmControlling && (state.npcEntity == null || !state.npcEntity.isValid())) {
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
            if (spell.isMonsterAttack()) {
                com.gridifymydungeon.plugin.dnd.MonsterState controlled = encounterManager.getControlledMonster();
                String required = spell.getRequiredMonsterType() != null ? spell.getRequiredMonsterType().name() : "?";
                String have = (controlled != null && controlled.monsterType != null) ? controlled.monsterType.name() : "none";
                playerRef.sendMessage(Message.raw("[Griddify] Wrong monster type! "
                        + spell.getName() + " requires " + required
                        + " (controlling: " + have + ")").color("#FF0000"));
            } else if (spell.isSubclassSpell()) {
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

        int casterGridX, casterGridZ;
        float casterY;
        if (isGmControlling) {
            com.gridifymydungeon.plugin.dnd.MonsterState monster = encounterManager.getControlledMonster();
            casterGridX = monster.currentGridX;
            casterGridZ = monster.currentGridZ;
            casterY = monster.spawnY;
        } else {
            casterGridX = state.currentGridX;
            casterGridZ = state.currentGridZ;
            casterY = state.npcY;
        }
        SpellPattern pattern = spell.getPattern();

        float yaw = 0f;
        try { yaw = playerRef.getTransform().getRotation().getY(); } catch (Exception ignored) {}
        float yawDeg = (float) Math.toDegrees(yaw);
        Direction8 initialDirection = Direction8.fromYaw(yawDeg);

        float playerY = casterY;

        // FIX #1: pass playerRef so range overlay is private to the caster
        final int fCasterGridX = casterGridX, fCasterGridZ = casterGridZ;
        final int fRange = spell.getRangeGrids();
        final float fCasterY = casterY;
        final GridPlayerState fState = state;
        final PlayerRef fPlayerRef = playerRef;
        world.execute(() -> {
            if (fState.gridOverlayEnabled && !fState.gmMapOverlayActive) {
                GridOverlayManager.removeGridOverlay(world, fState);
            }
            visualManager.showRangeOverlay(
                    fPlayerRef.getUuid(), fCasterGridX, fCasterGridZ, fRange, world, fCasterY, fPlayerRef);
        });

        // -----------------------------------------------------------------------
        // SELF / AURA: instant, no freeze
        // -----------------------------------------------------------------------
        if (pattern == SpellPattern.SELF || pattern == SpellPattern.AURA) {
            Set<SpellPatternCalculator.GridCell> cells = SpellPatternCalculator.calculatePattern(
                    pattern, initialDirection, casterGridX, casterGridZ,
                    spell.getRangeGrids(), spell.getAreaGrids());
            visualManager.showSpellArea(playerRef.getUuid(), cells, world, playerY, playerRef);
            state.setSpellCastingState(new SpellCastingState(spell, null, initialDirection, casterGridX, casterGridZ, casterY));

            playerRef.sendMessage(Message.raw("[Griddify] " + spellLabel(spell) + " ready!").color("#FFD700"));
            playerRef.sendMessage(Message.raw("[Griddify] Pattern fires on NPC. Use /CastFinal.").color("#87CEEB"));
            System.out.println("[Griddify] [CAST] " + playerRef.getUsername() + " preparing " + spell.getName() + " (instant)");
            return;
        }

        // Chromatic Orb
        if (spell.getName().equalsIgnoreCase("Chromatic_Orb")) {
            Set<SpellPatternCalculator.GridCell> cells = SpellPatternCalculator.calculatePattern(
                    pattern, initialDirection, casterGridX, casterGridZ,
                    spell.getRangeGrids(), spell.getAreaGrids());
            visualManager.showSpellArea(playerRef.getUuid(), cells, world, playerY, playerRef);
            state.setSpellCastingState(new SpellCastingState(spell, null, initialDirection, casterGridX, casterGridZ, casterY));
            playerRef.sendMessage(Message.raw("[Griddify] Chromatic Orb prepared!").color("#DA70D6"));
            playerRef.sendMessage(Message.raw("[Griddify] Choose element: /orb {acid | fire | cold | lightning | poison | thunder}").color("#DA70D6"));
            System.out.println("[Griddify] [CAST] " + playerRef.getUsername() + " preparing Chromatic_Orb — awaiting /orb");
            return;
        }

        // -----------------------------------------------------------------------
        // All other patterns: freeze NPC/monster
        // -----------------------------------------------------------------------
        if (isGmControlling) {
            com.gridifymydungeon.plugin.dnd.MonsterState mon = encounterManager.getControlledMonster();
            mon.freeze("casting");
            final com.gridifymydungeon.plugin.dnd.MonsterState monFinal = mon;
            final GridPlayerState gmState = state;
            world.execute(() -> {
                gmState.currentGridX   = monFinal.currentGridX;
                gmState.currentGridZ   = monFinal.currentGridZ;
                gmState.npcY           = monFinal.spawnY;
                gmState.remainingMoves = monFinal.remainingMoves;
                gmState.maxMoves       = monFinal.maxMoves;
                GridOverlayManager.spawnGMBFSOverlay(world, gmState, collisionDetector);
            });
        }
        state.freeze("casting");
        state.setSpellCastingState(new SpellCastingState(spell, null, initialDirection, casterGridX, casterGridZ, casterY));

        Set<SpellPatternCalculator.GridCell> initialCells = computeOverlay(
                pattern, initialDirection, casterGridX, casterGridZ, spell, casterGridX, casterGridZ);
        visualManager.showSpellArea(playerRef.getUuid(), initialCells, world, playerY, playerRef);

        String label = spellLabel(spell);
        System.out.println("[Griddify] [CAST] /Cast " + spell.getName()
                + " pattern=" + spell.getPattern()
                + " range=" + spell.getRangeGrids()
                + " area=" + spell.getAreaGrids()
                + " by=" + playerRef.getUsername());
        playerRef.sendMessage(Message.raw("[Griddify] CASTING: " + label).color("#FFD700"));
        playerRef.sendMessage(Message.raw("[Griddify] NPC frozen at (" + casterGridX + ", " + casterGridZ + ")").color("#FF6347"));

        if (pattern == SpellPattern.CONE || pattern == SpellPattern.LINE || pattern == SpellPattern.WALL) {
            playerRef.sendMessage(Message.raw("[Griddify] Walk AROUND your NPC to rotate the direction.").color("#87CEEB"));
        } else if (spell.isMultiTarget()) {
            playerRef.sendMessage(Message.raw("[Griddify] Multi-target! Walk to each target and type /CastTarget to confirm it.").color("#87CEEB"));
            playerRef.sendMessage(Message.raw("[Griddify] Targets: 0/" + spell.getMaxTargets() + " | Range: " + spell.getRangeGrids() + " grids").color("#87CEEB"));
        } else {
            playerRef.sendMessage(Message.raw("[Griddify] Walk to aim cell, then /CastFinal to fire.").color("#87CEEB"));
            playerRef.sendMessage(Message.raw("[Griddify] Or: walk anywhere, /CastTarget to lock aim from afar, then /CastFinal.").color("#87CEEB"));
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
                return SpellPatternCalculator.calculatePattern(
                        pattern, direction, casterGridX, casterGridZ,
                        spell.getRangeGrids(), spell.getAreaGrids());

            case SINGLE_TARGET:
                Set<SpellPatternCalculator.GridCell> cell = new HashSet<>();
                cell.add(new SpellPatternCalculator.GridCell(playerGridX, playerGridZ));
                return cell;

            default:
                return SpellPatternCalculator.calculatePattern(
                        pattern, direction, playerGridX, playerGridZ,
                        spell.getRangeGrids(), spell.getAreaGrids());
        }
    }

    private String spellLabel(SpellData spell) {
        if (spell.isSubclassSpell()) return spell.getName() + " (" + spell.getSubclass().getDisplayName() + ")";
        if (spell.getClassType() == null) return spell.getName() + " (Monster Attack)";
        return spell.getName() + " (" + spell.getClassType().getDisplayName() + ")";
    }

    private boolean canAccessSpell(GridPlayerState state, SpellData spell, PlayerRef playerRef) {
        if (spell.isMonsterAttack()) {
            if (!DebugRoleWrapper.isGM(roleManager, playerRef)) return false;
            com.gridifymydungeon.plugin.dnd.MonsterState controlled = encounterManager.getControlledMonster();
            if (controlled == null) return false;
            return spell.getRequiredMonsterType() == controlled.monsterType;
        }

        if (spell.getClassType() == null && !spell.isSubclassSpell()) {
            return DebugRoleWrapper.isGM(roleManager, playerRef) && encounterManager.getControlledMonster() != null;
        }

        if (DebugRoleWrapper.isGM(roleManager, playerRef)) {
            com.gridifymydungeon.plugin.dnd.MonsterState controlled = encounterManager.getControlledMonster();
            if (controlled != null) {
                if (spell.isSubclassSpell()) return spell.getSubclass() == controlled.stats.getSubclassType();
                return spell.getClassType() == controlled.stats.getClassType();
            }
        }

        if (spell.isSubclassSpell()) return spell.getSubclass() == state.stats.getSubclassType();
        return spell.getClassType() == state.stats.getClassType();
    }
}