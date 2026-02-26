package com.gridifymydungeon.plugin.spell;
import com.gridifymydungeon.plugin.debug.DebugRoleWrapper;

import com.gridifymydungeon.plugin.dnd.CombatSettings;
import com.gridifymydungeon.plugin.dnd.EncounterManager;
import com.gridifymydungeon.plugin.dnd.MonsterState;
import com.gridifymydungeon.plugin.dnd.RoleManager;
import com.gridifymydungeon.plugin.gridmove.GridMoveManager;
import com.gridifymydungeon.plugin.gridmove.GridPlayerState;
import com.gridifymydungeon.plugin.gridmove.TerrainManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * /CastFinal - Execute the prepared spell.
 *
 * FIX: spellKey was declared inside the player-casts-at-monsters else-block but
 * referenced after that block closed (for getNpcAnimationForSpell and NPC rotation).
 * Moved declaration to the top of execute(), right after spell is resolved.
 * The duplicate declaration inside the else-block has been removed.
 */
public class CastFinalCommand extends AbstractPlayerCommand {
    private final GridMoveManager playerManager;
    private final EncounterManager encounterManager;
    private final SpellVisualManager visualManager;
    private final CombatSettings combatSettings;
    private final RoleManager roleManager;
    private final WildShapeManager wildShapeManager;
    private final PolymorphManager polymorphManager;

    public CastFinalCommand(GridMoveManager playerManager, EncounterManager encounterManager,
                            SpellVisualManager visualManager, CombatSettings combatSettings,
                            RoleManager roleManager, WildShapeManager wildShapeManager,
                            PolymorphManager polymorphManager) {
        super("CastFinal", "Execute prepared spell");
        this.playerManager = playerManager;
        this.encounterManager = encounterManager;
        this.visualManager = visualManager;
        this.combatSettings = combatSettings;
        this.roleManager = roleManager;
        this.wildShapeManager = wildShapeManager;
        this.polymorphManager = polymorphManager;
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {

        GridPlayerState state = playerManager.getState(playerRef);

        SpellCastingState castState = state.getSpellCastingState();
        if (castState == null || !castState.isValid()) {
            playerRef.sendMessage(Message.raw("[Griddify] No spell prepared! Use /Cast first").color("#FF0000"));
            return;
        }

        SpellData spell = castState.getSpell();

        // FIX: Declare spellKey here so it is in scope for the entire execute() method,
        //      including the getNpcAnimationForSpell() call at the bottom.
        //      The duplicate declaration that was previously inside the else-block is removed.
        final String spellKey = spell.getName().toLowerCase()
                .replace(" ", "_").replace("-", "_");

        int aimGridX = castState.getAimGridX();
        int aimGridZ = castState.getAimGridZ();

        // ── Range check ──────────────────────────────────────────────────────
        // If /CastTarget was used, validate against the CONFIRMED aim point, not the
        // player's current body position (which may have drifted after confirmation).
        // If no /CastTarget, validate against current body position as before.
        if (spell.getRangeGrids() > 0) {
            int checkX, checkZ;
            if (castState.hasConfirmedCells() && !castState.getConfirmedTargets().isEmpty()) {
                SpellCastingState.GridCell first = castState.getConfirmedTargets().get(0);
                checkX = first.x;
                checkZ = first.z;
            } else {
                checkX = aimGridX;
                checkZ = aimGridZ;
            }
            // For CONE/LINE/WALL the range check doesn't apply (direction-based, range=0 in DB)
            SpellPattern patternCheck = spell.getPattern();
            boolean isDirectional = patternCheck == SpellPattern.CONE
                    || patternCheck == SpellPattern.LINE
                    || patternCheck == SpellPattern.WALL
                    || patternCheck == SpellPattern.SELF
                    || patternCheck == SpellPattern.AURA;
            if (!isDirectional) {
                int dist = SpellPatternCalculator.getDistance(
                        castState.getCasterGridX(), castState.getCasterGridZ(), checkX, checkZ);
                if (dist > spell.getRangeGrids()) {
                    playerRef.sendMessage(Message.raw("[Griddify] Out of range! ("
                            + dist + "/" + spell.getRangeGrids() + " grids)").color("#FF0000"));
                    return;
                }
            }
        }

        // Also honour the live out-of-range flag (set when player walked out without confirming)
        if (castState.isOutOfRange()) {
            playerRef.sendMessage(Message.raw("[Griddify] Out of range — return to range or use /CastTarget first.").color("#FF0000"));
            return;
        }

        state.hasUsedAction = true;

        int cost = spell.getSlotCost();
        if (!state.stats.consumeSpellSlot(cost)) {
            playerRef.sendMessage(Message.raw("[Griddify] Not enough spell slots!").color("#FF0000"));
            state.clearSpellCastingState();
            visualManager.clearSpellVisuals(playerRef.getUuid(), world);
            visualManager.clearRangeOverlay(playerRef.getUuid(), world);
            return;
        }

        SpellPattern pattern = spell.getPattern();

        Set<SpellPatternCalculator.GridCell> affectedCells;

        if (castState.hasConfirmedCells()) {
            // Player used /CastTarget — use the exact snapshot they locked in.
            affectedCells = new HashSet<>(castState.getConfirmedCells());
        } else {
            // No /CastTarget used — compute from current body position (original behaviour)
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
                    affectedCells = SpellPatternCalculator.calculatePattern(
                            pattern, castState.getDirection(),
                            aimGridX, aimGridZ,
                            spell.getRangeGrids(), spell.getAreaGrids());
                    break;
            }
        }


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

        int rollAmount = 0;
        if (spell.getDamageDice() != null && !spell.getDamageDice().isEmpty()) {
            rollAmount = rollDamage(spell.getDamageDice());
            rollAmount += state.stats.getSpellcastingModifier();
            if (rollAmount < 0) rollAmount = 0;
        }

        boolean isHeal = spell.isHealingSpell();
        boolean casterIsGM = DebugRoleWrapper.isGM(roleManager, playerRef);
        PlayerRef gmRef = roleManager.getGM();
        int targetsAffected = 0;

        if (isHeal) {
            if (casterIsGM) {
                for (MonsterState monster : encounterManager.getMonsters()) {
                    if (!monster.isAlive()) continue;
                    if (affectedCells.contains(new SpellPatternCalculator.GridCell(monster.currentGridX, monster.currentGridZ))) {
                        targetsAffected++;
                        if (rollAmount > 0) {
                            int before = monster.stats.currentHP;
                            monster.stats.heal(rollAmount);
                            int after = monster.stats.currentHP;
                            if (gmRef != null) {
                                gmRef.sendMessage(Message.raw("[Griddify] [HEAL] " + monster.getDisplayName()
                                        + " healed " + (after - before) + " HP → "
                                        + after + "/" + monster.stats.maxHP).color("#00FF7F"));
                            }
                        }
                    }
                }
            } else {
                for (com.gridifymydungeon.plugin.gridmove.GridPlayerState ps : playerManager.getAllStates()) {
                    if (ps.npcEntity == null || !ps.npcEntity.isValid()) continue;
                    if (affectedCells.contains(new SpellPatternCalculator.GridCell(ps.currentGridX, ps.currentGridZ))) {
                        targetsAffected++;
                        if (rollAmount > 0 && ps.playerRef != null) {
                            int before = ps.stats.currentHP;
                            ps.stats.heal(rollAmount);
                            int after = ps.stats.currentHP;
                            ps.playerRef.sendMessage(Message.raw("[Griddify] [HEAL] Healed " + (after - before)
                                    + " HP  →  " + after + "/" + ps.stats.maxHP + " HP").color("#00FF7F"));
                            if (!ps.playerRef.equals(playerRef)) {
                                playerRef.sendMessage(Message.raw("[Griddify] [HEAL] " + ps.playerRef.getUsername()
                                        + ": " + after + "/" + ps.stats.maxHP + " HP").color("#00FF7F"));
                            }
                        }
                    }
                }
            }
        } else {
            if (casterIsGM) {
                for (com.gridifymydungeon.plugin.gridmove.GridPlayerState ps : playerManager.getAllStates()) {
                    if (ps.npcEntity == null || !ps.npcEntity.isValid()) continue;
                    if (!ps.stats.isAlive()) continue;
                    if (affectedCells.contains(new SpellPatternCalculator.GridCell(ps.currentGridX, ps.currentGridZ))) {
                        targetsAffected++;
                        if (rollAmount > 0) {
                            ps.stats.takeDamage(rollAmount);
                            int remaining = ps.stats.currentHP;
                            if (ps.playerRef != null) {
                                String hpBar = buildHPBar(remaining, ps.stats.maxHP);
                                if (remaining == 0) {
                                    ps.playerRef.sendMessage(Message.raw(
                                                    "[Griddify] [DEAD] You took " + rollAmount + " "
                                                            + spell.getDamageType().name().toLowerCase() + " damage!  "
                                                            + remaining + "/" + ps.stats.maxHP + " HP  " + hpBar)
                                            .color("#FF0000"));
                                    ps.playerRef.sendMessage(Message.raw("[Griddify] You are DOWN!").color("#FF0000"));
                                } else {
                                    ps.playerRef.sendMessage(Message.raw(
                                                    "[Griddify] [HIT] You took " + rollAmount + " "
                                                            + spell.getDamageType().name().toLowerCase() + " damage!  "
                                                            + remaining + "/" + ps.stats.maxHP + " HP  " + hpBar)
                                            .color("#FF6B6B"));
                                }
                            }
                            if (gmRef != null) {
                                String who = ps.playerRef != null ? ps.playerRef.getUsername() : "Player";
                                String tag = remaining == 0 ? "[DEAD]" : "[HIT]";
                                gmRef.sendMessage(Message.raw("[Griddify] " + tag + " " + who
                                                + " took " + rollAmount + " dmg → "
                                                + remaining + "/" + ps.stats.maxHP + " HP")
                                        .color(remaining == 0 ? "#FF0000" : "#FFA500"));
                            }
                        }
                    }
                }
            } else {
                // ── Player casts at monsters ──────────────────────────────────
                for (MonsterState monster : encounterManager.getMonsters()) {
                    if (!monster.isAlive()) continue;
                    if (affectedCells.contains(new SpellPatternCalculator.GridCell(monster.currentGridX, monster.currentGridZ))) {
                        targetsAffected++;
                        if (rollAmount > 0) {
                            int before = monster.stats.currentHP;
                            monster.takeDamage(rollAmount);
                            int after = monster.stats.currentHP;
                            boolean slain = (after == 0);
                            String tag = slain ? "[DEAD]" : "[HIT]";
                            playerRef.sendMessage(Message.raw("[Griddify] " + tag + " " + monster.getDisplayName()
                                            + "  -" + rollAmount + " "
                                            + spell.getDamageType().name().toLowerCase()
                                            + "  →  " + after + "/" + monster.stats.maxHP + " HP")
                                    .color(slain ? "#FF0000" : "#FF6B6B"));
                            if (gmRef != null && !gmRef.equals(playerRef)) {
                                String hpBar = buildHPBar(after, monster.stats.maxHP);
                                gmRef.sendMessage(Message.raw("[Griddify] " + tag + " " + monster.getDisplayName()
                                                + " took " + rollAmount + " dmg → "
                                                + after + "/" + monster.stats.maxHP + " HP  " + hpBar)
                                        .color(slain ? "#FF0000" : "#FFA500"));
                            }
                            if (slain) {
                                final MonsterState deadMonster = monster;
                                final int deadNum = monster.monsterNumber;
                                world.execute(() -> {
                                    com.gridifymydungeon.plugin.dnd.commands.MonsterEntityController.despawnMonster(world, deadMonster);
                                    encounterManager.removeMonster(deadNum);
                                    System.out.println("[Griddify] [AUTO-SLAIN] " + deadMonster.getDisplayName() + " auto-removed on death.");
                                });
                                if (gmRef != null) {
                                    gmRef.sendMessage(Message.raw("[Griddify] " + monster.getDisplayName()
                                            + " automatically removed from encounter.").color("#FF4500"));
                                }
                            }
                        }
                    }
                }

                // ── Spell visual effects ──────────────────────────────────────
                // NOTE: spellKey is now declared at the top of execute() — no duplicate here.
                final float npcYFinal = state.npcY;
                final List<PlayerRef> allPlayers = playerManager.getAllPlayerRefs();
                final Set<SpellPatternCalculator.GridCell> finalCells = new HashSet<>(affectedCells);
                final SpellCastingState finalCastState = castState;

                // ── WILD SHAPE (toggle transform) ─────────────────────────────
                if (spellKey.startsWith("wild_shape")) {
                    if (wildShapeManager.isTransformed(playerRef.getUuid())) {
                        wildShapeManager.revert(playerRef, world, true);
                    } else {
                        boolean ok = wildShapeManager.transform(playerRef, world, spell.getName());
                        if (ok) {
                            castState.setWildShapeActive(true, spell.getName());
                            playerRef.sendMessage(Message.raw(
                                    "[Griddify] Transformed into " + spell.getName().replace("_", " ")
                                            + "! Cast Wild Shape again or reach 0 HP to revert.").color("#00FF7F"));
                        }
                    }

                    // ── POLYMORPH — set pending target, await /polyform ────────────
                } else if (spellKey.equals("polymorph")) {
                    // Store the first confirmed target cell so /polyform can pick form
                    SpellCastingState.GridCell polyTarget = castState.getConfirmedTargets().isEmpty()
                            ? new SpellCastingState.GridCell(aimGridX, aimGridZ)
                            : castState.getConfirmedTargets().get(0);
                    castState.setPendingPolymorphTarget(polyTarget);
                    playerRef.sendMessage(Message.raw(
                                    "[Griddify] Polymorph ready! Choose form: /polyform {Bear | Dire_Wolf | Rex | Feran_Windwalker | Spider}")
                            .color("#DA70D6"));
                    playerRef.sendMessage(Message.raw(
                            "[Griddify] Forms: " + PolymorphManager.Form.listNames()).color("#FFFFFF"));

                    // ── CHROMATIC ORB — requires /orb element choice ──────────────
                } else if (spellKey.equals("chromatic_orb")) {
                    if (!castState.hasChromaticElement()) {
                        playerRef.sendMessage(Message.raw(
                                        "[Griddify] Choose element first: /orb {acid|fire|cold|lightning|poison|thunder}")
                                .color("#FF0000"));
                        state.stats.restoreSpellSlot(spell.getSlotCost());
                        state.hasUsedAction = false;
                        return;
                    }
                    ProjectileType orbType = ProjectileType.forElement(castState.getChromaticElement());
                    if (orbType != null) {
                        java.util.List<SpellCastingState.GridCell> targets =
                                new java.util.ArrayList<>(castState.getConfirmedTargets());
                        if (targets.isEmpty())
                            targets.add(new SpellCastingState.GridCell(aimGridX, aimGridZ));
                        final ProjectileType finalOrbType = orbType;
                        for (int i = 0; i < targets.size(); i++) {
                            final SpellCastingState.GridCell tc = targets.get(i);
                            final long delayMs = i * 120L;
                            if (delayMs == 0) {
                                world.execute(() -> launchSingleProjectile(
                                        finalOrbType, world, finalCastState, tc.x, tc.z, npcYFinal, allPlayers));
                            } else {
                                PROJ_SCHEDULER.schedule(() ->
                                                world.execute(() -> launchSingleProjectile(
                                                        finalOrbType, world, finalCastState, tc.x, tc.z, npcYFinal, allPlayers)),
                                        delayMs, java.util.concurrent.TimeUnit.MILLISECONDS);
                            }
                        }
                    }

                    // ── ALL OTHER SPELLS ──────────────────────────────────────────
                } else {
                    ProjectileType projType = ProjectileType.forSpell(spell.getName());
                    if (projType != null) {
                        final ProjectileType finalType = projType;
                        final SpellPattern projPattern = spell.getPattern();

                        if (projPattern == SpellPattern.CONE) {
                            world.execute(() -> launchConeProjectiles(
                                    finalType, world, finalCastState, finalCells, npcYFinal, allPlayers));

                        } else if (projPattern == SpellPattern.LINE) {
                            world.execute(() -> launchLineProjectiles(
                                    finalType, world, finalCastState, finalCells, npcYFinal, allPlayers));

                        } else if (projPattern == SpellPattern.CHAIN) {
                            java.util.List<SpellCastingState.GridCell> targets =
                                    new java.util.ArrayList<>(castState.getConfirmedTargets());
                            // Chaos_Bolt requires at least 1 confirmed target via /CastTarget
                            if (targets.isEmpty() && spellKey.equals("chaos_bolt")) {
                                playerRef.sendMessage(Message.raw("[Griddify] Chaos Bolt needs a target! Use /CastTarget first.").color("#FF0000"));
                                visualManager.clearSpellVisuals(playerRef.getUuid(), world);
                                visualManager.clearRangeOverlay(playerRef.getUuid(), world);
                                state.clearSpellCastingState();
                                return;
                            }
                            if (targets.isEmpty())
                                targets.add(new SpellCastingState.GridCell(aimGridX, aimGridZ));
                            // Remove duplicate targets so chain can't hit same monster twice
                            java.util.LinkedHashSet<SpellCastingState.GridCell> uniqueTargets =
                                    new java.util.LinkedHashSet<>(targets);
                            java.util.List<SpellCastingState.GridCell> chainTargets = new java.util.ArrayList<>(uniqueTargets);
                            world.execute(() -> launchChainProjectile(
                                    finalType, world, finalCastState, chainTargets, npcYFinal, allPlayers));

                        } else if (projPattern == SpellPattern.SPHERE) {
                            // All sphere spells get a fireball-style impact
                            java.util.List<SpellCastingState.GridCell> targets =
                                    new java.util.ArrayList<>(castState.getConfirmedTargets());
                            if (targets.isEmpty())
                                targets.add(new SpellCastingState.GridCell(aimGridX, aimGridZ));
                            final SpellCastingState.GridCell tc = targets.get(0);
                            // Scale lingering effect: big spells get bigger/longer explosion
                            float lingerScale = 1.5f;
                            long lingerMs = 800L;
                            if (spellKey.equals("meteor_swarm")) { lingerScale = 3.5f; lingerMs = 2000L; }
                            else if (spellKey.equals("delayed_blast_fireball")) { lingerScale = 2.5f; lingerMs = 1500L; }
                            else if (spellKey.equals("mass_psychic_blast")) { lingerScale = 2.0f; lingerMs = 900L; }
                            final float fScale = lingerScale; final long fLinger = lingerMs;
                            world.execute(() -> launchFireballProjectile(
                                    finalType, world, finalCastState, tc.x, tc.z, npcYFinal, allPlayers, fScale, fLinger));

                        } else {
                            // SINGLE_TARGET — stagger 120ms per target, rotate NPC to face each one
                            java.util.List<SpellCastingState.GridCell> targets =
                                    new java.util.ArrayList<>(castState.getConfirmedTargets());
                            if (targets.isEmpty())
                                targets.add(new SpellCastingState.GridCell(aimGridX, aimGridZ));
                            for (int i = 0; i < targets.size(); i++) {
                                final SpellCastingState.GridCell tc = targets.get(i);
                                final long delayMs = i * 120L;
                                // Yaw from caster → this specific target cell
                                int _dx = tc.x - castState.getCasterGridX();
                                int _dz = tc.z - castState.getCasterGridZ();
                                final float targetYaw = (float) Math.atan2(-_dx, -_dz);
                                System.out.println("[Griddify] [CAST] SINGLE_TARGET idx=" + i
                                        + " target=(" + tc.x + "," + tc.z + ") yaw=" + String.format("%.2f", targetYaw));
                                if (delayMs == 0) {
                                    world.execute(() -> {
                                        com.gridifymydungeon.plugin.dnd.PlayerEntityController
                                                .setNpcYaw(world, state, targetYaw);
                                        launchSingleProjectile(finalType, world, finalCastState, tc.x, tc.z, npcYFinal, allPlayers);
                                    });
                                } else {
                                    PROJ_SCHEDULER.schedule(() ->
                                                    world.execute(() -> {
                                                        com.gridifymydungeon.plugin.dnd.PlayerEntityController
                                                                .setNpcYaw(world, state, targetYaw);
                                                        launchSingleProjectile(finalType, world, finalCastState, tc.x, tc.z, npcYFinal, allPlayers);
                                                    }),
                                            delayMs, java.util.concurrent.TimeUnit.MILLISECONDS);
                                }
                            }
                        }
                    }

                    // ── STATIONARY / WAVE / RAIN effects ─────────────────────
                    switch (spellKey) {
                        case "entangle": {
                            // Spawn 4 Entangle entities per grid cell (4 block corners)
                            java.util.List<com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore>> entangleRefs =
                                    SpellVisualEffect.spawnEntangle(world, finalCells, npcYFinal);
                            // Register difficult terrain cells
                            for (SpellPatternCalculator.GridCell c : finalCells) {
                                TerrainManager.addDifficultCell(c.x, c.z);
                            }
                            // Auto-despawn after 30 s if not in combat, or persist in combat.
                            // Also remove difficult terrain cells when entities despawn.
                            if (!combatSettings.isCombatActive()) {
                                final java.util.Set<SpellPatternCalculator.GridCell> entangleCells =
                                        new java.util.HashSet<>(finalCells);
                                PROJ_SCHEDULER.schedule(() -> world.execute(() -> {
                                    for (com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> r : entangleRefs) {
                                        SpellVisualEffect.despawn(world, r);
                                    }
                                    for (SpellPatternCalculator.GridCell c : entangleCells) {
                                        TerrainManager.removeDifficultCell(c.x, c.z);
                                    }
                                }), 30000L, java.util.concurrent.TimeUnit.MILLISECONDS);
                            }
                            break;
                        }
                        case "moonbeam": {
                            SpellCastingState.GridCell tc = castState.getConfirmedTargets().isEmpty()
                                    ? new SpellCastingState.GridCell(aimGridX, aimGridZ)
                                    : castState.getConfirmedTargets().get(0);
                            final int mx = tc.x, mz = tc.z;
                            Float groundY = SpellVisualManager.scanForGround(
                                    world, mx, mz, npcYFinal + 30f, 45);
                            float wy = groundY != null ? groundY : npcYFinal;
                            world.execute(() -> SpellVisualEffect.spawnStationary(
                                    "Moon_Beam", 1.0f, world,
                                    (mx * 2.0) + 1.0, wy, (mz * 2.0) + 1.0, 0f));
                            break;
                        }
                        case "sunbeam": {
                            float sunYaw = directionToYaw(castState.getDirection());
                            float ox = (castState.getCasterGridX() * 2.0f) + 1.0f;
                            float oz = (castState.getCasterGridZ() * 2.0f) + 1.0f;
                            Float groundY = SpellVisualManager.scanForGround(
                                    world, castState.getCasterGridX(), castState.getCasterGridZ(),
                                    npcYFinal + 30f, 45);
                            float wy = (groundY != null ? groundY : npcYFinal) + 1.0f;
                            final float fYaw = sunYaw, fOx = ox, fWy = wy, fOz = oz;
                            world.execute(() -> SpellVisualEffect.spawnStationary(
                                    "Sun_Beam", 1.0f, world, fOx, fWy, fOz, fYaw));
                            break;
                        }
                        case "thunderwave": {
                            System.out.println("[Griddify] [CAST] thunderwave cells=" + finalCells.size()
                                    + " caster=(" + castState.getCasterGridX() + "," + castState.getCasterGridZ()
                                    + ") aim=(" + aimGridX + "," + aimGridZ + ")");
                            float twYaw = directionToYaw(castState.getDirection());
                            world.execute(() -> SpellVisualEffect.spawnWave(
                                    "Thunderwave", 1.0f, world,
                                    castState.getCasterGridX(), castState.getCasterGridZ(),
                                    npcYFinal, finalCells, allPlayers, twYaw));
                            break;
                        }
                        case "ice_storm": {
                            world.execute(() -> SpellVisualEffect.spawnIceStorm(
                                    world, finalCells, npcYFinal, allPlayers));
                            break;
                        }
                        case "bardic_inspiration":
                        case "vicious_mockery":
                        case "bless":
                        case "hex":
                        case "hunters_mark":
                        case "power_word_stun": {
                            // Buff: spawn Mark under the target cell
                            SpellCastingState.GridCell bt = castState.getConfirmedTargets().isEmpty()
                                    ? new SpellCastingState.GridCell(aimGridX, aimGridZ)
                                    : castState.getConfirmedTargets().get(0);
                            world.execute(() -> SpellVisualEffect.spawnMark(world, bt.x, bt.z, npcYFinal, 4000L));
                            break;
                        }
                        case "wind_dash":
                        case "shadow_dash":
                        case "shadow_step": {
                            // Teleport: Mark at destination then Mark at origin briefly
                            float casterWx = (castState.getCasterGridX() * 2.0f) + 1.0f;
                            float casterWz = (castState.getCasterGridZ() * 2.0f) + 1.0f;
                            Float casterGY = SpellVisualManager.scanForGround(world,
                                    castState.getCasterGridX(), castState.getCasterGridZ(), npcYFinal + 30f, 45);
                            float casterWy = (casterGY != null ? casterGY : npcYFinal) + 0.05f;
                            world.execute(() -> {
                                SpellVisualEffect.spawnWithTimeout("Mark", 1.0f, world,
                                        casterWx, casterWy, casterWz, 0f, 600L); // origin flash
                                SpellCastingState.GridCell dt = castState.getConfirmedTargets().isEmpty()
                                        ? new SpellCastingState.GridCell(aimGridX, aimGridZ)
                                        : castState.getConfirmedTargets().get(0);
                                SpellVisualEffect.spawnMark(world, dt.x, dt.z, npcYFinal, 2500L);
                            });
                            break;
                        }
                        case "call_lightning": {
                            // Repeated lightning strikes at target area over 3 seconds
                            SpellCastingState.GridCell tc = castState.getConfirmedTargets().isEmpty()
                                    ? new SpellCastingState.GridCell(aimGridX, aimGridZ)
                                    : castState.getConfirmedTargets().get(0);
                            final int cx = tc.x, cz = tc.z;
                            for (int strike = 0; strike < 4; strike++) {
                                final long delay = strike * 750L;
                                PROJ_SCHEDULER.schedule(() -> world.execute(() -> {
                                    float wx = (cx * 2.0f) + 1.0f;
                                    float wz = (cz * 2.0f) + 1.0f;
                                    Float gy = SpellVisualManager.scanForGround(world, cx, cz, npcYFinal + 30f, 45);
                                    float wy = (gy != null ? gy : npcYFinal) + 0.5f;
                                    SpellVisualEffect.spawnWithTimeout("Lightning_Bolt", 1.0f, world, wx, wy + 8f, wz, 0f, 50L);
                                    SpellVisualEffect.spawnWithTimeout("Explosion", 1.0f, world, wx, wy, wz, 0f, 400L);
                                }), delay, java.util.concurrent.TimeUnit.MILLISECONDS);
                            }
                            break;
                        }
                        case "spirit_guardians": {
                            float ox2 = (castState.getCasterGridX() * 2.0f) + 1.0f;
                            float oz2 = (castState.getCasterGridZ() * 2.0f) + 1.0f;
                            Float gy2 = SpellVisualManager.scanForGround(world, castState.getCasterGridX(), castState.getCasterGridZ(), npcYFinal + 30f, 45);
                            float wy2 = (gy2 != null ? gy2 : npcYFinal) + 1.0f;
                            final float fOx2 = ox2, fWy2 = wy2, fOz2 = oz2;
                            world.execute(() -> SpellVisualEffect.spawnWithTimeout("Arcane_Bolt", 2.5f, world, fOx2, fWy2, fOz2, 0f, 2500L));
                            break;
                        }

                        // ── Self-buffs: Mark on caster ─────────────────────────────────
                        case "rage":
                        case "reckless_attack":
                        case "shield":
                        case "iron_body":
                        case "evasive_roll":
                        case "arcane_ward":
                        case "spell_resistance":
                        case "portent":
                        case "greater_portent":
                        case "third_eye":
                        case "expert_divination":
                        case "overchannel":
                        case "potent_cantrip":
                        case "sculpt_spells":
                        case "empowered_evocation":
                        case "blessed_healer":
                        case "disciple_of_life":
                        case "supreme_healing":
                        case "preserve_life": {
                            // Buff flash: Mark under caster
                            world.execute(() -> SpellVisualEffect.spawnMark(world,
                                    castState.getCasterGridX(), castState.getCasterGridZ(), npcYFinal, 2500L));
                            break;
                        }

                        // ── Frost aura: Frost_Bolt ring ────────────────────────────────
                        case "frost_shell": {
                            int fsCasterX = castState.getCasterGridX(), fsCasterZ = castState.getCasterGridZ();
                            for (SpellPatternCalculator.GridCell c : finalCells) {
                                if (c.x == fsCasterX && c.z == fsCasterZ) continue; // skip caster cell
                                float wx = (c.x * 2.0f) + 1.0f;
                                float wz = (c.z * 2.0f) + 1.0f;
                                Float groundY = SpellVisualManager.scanForGround(world, c.x, c.z, npcYFinal + 30f, 45);
                                float wy = (groundY != null ? groundY : npcYFinal) + 0.5f;
                                world.execute(() -> SpellVisualEffect.spawnWithTimeout("Frost_Bolt", 0.8f, world, wx, wy, wz, 0f, 2000L));
                            }
                            break;
                        }

                        // ── Healing auras: Heal_Circle under caster ────────────────────
                        case "healing_song": {
                            // 33% smaller than other healing auras
                            float hsWx = (castState.getCasterGridX() * 2.0f) + 1.0f;
                            float hsWz = (castState.getCasterGridZ() * 2.0f) + 1.0f;
                            Float hsGY = SpellVisualManager.scanForGround(world, castState.getCasterGridX(), castState.getCasterGridZ(), npcYFinal + 30f, 45);
                            float hsWy = (hsGY != null ? hsGY : npcYFinal) + 0.1f;
                            final float fHsWx = hsWx, fHsWy = hsWy, fHsWz = hsWz;
                            world.execute(() -> SpellVisualEffect.spawnWithTimeout("Heal_Circle", 0.6f, world, fHsWx, fHsWy, fHsWz, 0f, 3000L));
                            break;
                        }
                        case "aura_of_protection":
                        case "hurricane_strike": {
                            float casterWx = (castState.getCasterGridX() * 2.0f) + 1.0f;
                            float casterWz = (castState.getCasterGridZ() * 2.0f) + 1.0f;
                            Float cGY = SpellVisualManager.scanForGround(world, castState.getCasterGridX(), castState.getCasterGridZ(), npcYFinal + 30f, 45);
                            float cWy = (cGY != null ? cGY : npcYFinal) + 0.1f;
                            final float fCWx = casterWx, fCWy = cWy, fCWz = casterWz;
                            world.execute(() -> SpellVisualEffect.spawnWithTimeout("Heal_Circle", 2.0f, world, fCWx, fCWy, fCWz, 0f, 3000L));
                            break;
                        }

                        // ── Hypnotic Pattern: Arcane_Bolt pillars in area ─────────────
                        case "hypnotic_pattern": {
                            for (SpellPatternCalculator.GridCell c : finalCells) {
                                float wx = (c.x * 2.0f) + 1.0f;
                                float wz = (c.z * 2.0f) + 1.0f;
                                Float groundY = SpellVisualManager.scanForGround(world, c.x, c.z, npcYFinal + 30f, 45);
                                float wy = (groundY != null ? groundY : npcYFinal) + 0.5f;
                                world.execute(() -> SpellVisualEffect.spawnWithTimeout("Arcane_Bolt", 0.9f, world, wx, wy, wz, 0f, 3000L));
                            }
                            break;
                        }

                        // ── Wild Shape: Mark at caster feet (transform flash) ──────────
                        // (actual model swap handled by WildShapeManager above; this just
                        //  adds a ground Mark as visual feedback that the transform happened)
                    }
                }

            }
        }


        // ── HEAL VISUALS ───────────────────────────────────────────────────────
        // Spawn Heal_One on each healed creature, Heal_Circle under caster for area heals
        if (isHeal && targetsAffected > 0) {
            boolean isAreaHeal = spell.getAreaGrids() > 0
                    || spell.getPattern() == SpellPattern.SPHERE
                    || spell.getPattern() == SpellPattern.CYLINDER
                    || spell.getPattern() == SpellPattern.CUBE
                    || spell.getPattern() == SpellPattern.AURA;

            // Collect world positions of all healed targets
            java.util.List<float[]> healedPositions = new java.util.ArrayList<>();

            if (casterIsGM) {
                for (MonsterState ms : encounterManager.getMonsters()) {
                    if (!ms.isAlive()) continue;
                    if (affectedCells.contains(new SpellPatternCalculator.GridCell(ms.currentGridX, ms.currentGridZ))) {
                        float wx = (ms.currentGridX * 2.0f) + 1.0f;
                        float wz = (ms.currentGridZ * 2.0f) + 1.0f;
                        Float gy = SpellVisualManager.scanForGround(world, ms.currentGridX, ms.currentGridZ,
                                ms.spawnY + 30f, 45);
                        float wy = gy != null ? gy : ms.spawnY;
                        healedPositions.add(new float[]{wx, wy, wz});
                    }
                }
            } else {
                for (GridPlayerState ps : playerManager.getAllStates()) {
                    if (ps.npcEntity == null || !ps.npcEntity.isValid()) continue;
                    if (affectedCells.contains(new SpellPatternCalculator.GridCell(ps.currentGridX, ps.currentGridZ))) {
                        float wx = (ps.currentGridX * 2.0f) + 1.0f;
                        float wz = (ps.currentGridZ * 2.0f) + 1.0f;
                        Float gy = SpellVisualManager.scanForGround(world, ps.currentGridX, ps.currentGridZ,
                                state.npcY + 30f, 45);
                        float wy = gy != null ? gy : state.npcY;
                        healedPositions.add(new float[]{wx, wy, wz});
                    }
                }
            }

            if (isAreaHeal) {
                float casterWx = (state.getSpellCastingState() != null
                        ? state.getSpellCastingState().getCasterGridX() : aimGridX) * 2.0f + 1.0f;
                float casterWz = (state.getSpellCastingState() != null
                        ? state.getSpellCastingState().getCasterGridZ() : aimGridZ) * 2.0f + 1.0f;
                Float cgy = SpellVisualManager.scanForGround(world, aimGridX, aimGridZ, state.npcY + 30f, 45);
                float casterWy = cgy != null ? cgy : state.npcY;
                final float fcWx = casterWx, fcWy = casterWy, fcWz = casterWz;
                final java.util.List<float[]> fPos = healedPositions;
                world.execute(() -> SpellVisualEffect.spawnHealArea(world, fcWx, fcWy, fcWz, fPos));
            } else {
                final java.util.List<float[]> fPos = healedPositions;
                world.execute(() -> {
                    for (float[] p : fPos) SpellVisualEffect.spawnHealOne(world, p[0], p[1], p[2]);
                });
            }
        }

        int totalDamage = isHeal ? 0 : rollAmount;
        int monstersHit = targetsAffected;

        if (spell.isPersistent()) {
            PersistentSpellEffect persistentEffect = new PersistentSpellEffect(
                    spell, playerRef, affectedCells, 0);
            playerRef.sendMessage(Message.raw("  Duration: " + spell.getDurationTurns() + " turns").color("#9370DB"));
        }

        visualManager.clearSpellVisuals(playerRef.getUuid(), world);
        visualManager.clearRangeOverlay(playerRef.getUuid(), world);

        // Rotate NPC to face the cast direction
        float castYaw = directionToYaw(castState.getDirection());
        final float fCastYaw = castYaw;
        world.execute(() -> com.gridifymydungeon.plugin.dnd.PlayerEntityController.setNpcYaw(world, state, fCastYaw));

        // Play attack animation on NPC for class basic attacks
        // spellKey is now in scope here — declared at the top of execute()
        final String[] animForSpell = getNpcAnimationForSpell(spellKey);
        if (animForSpell != null) {
            final String _animId     = animForSpell[0];
            final String _itemAnimId = animForSpell[1];
            System.out.println("[Griddify] [ANIM] spell=" + spellKey
                    + " animId=" + _animId + " itemAnimsId=" + _itemAnimId);
            world.execute(() -> com.gridifymydungeon.plugin.dnd.PlayerEntityController.playNpcAnimation(
                    world, state, _animId, _itemAnimId));
        }

        state.clearSpellCastingState();

        boolean casterIsGMControlling = casterIsGM && encounterManager.getControlledMonster() != null;

        if (casterIsGMControlling) {
            MonsterState castMonster = encounterManager.getControlledMonster();
            castMonster.freeze("post_cast");
            state.unfreeze();
            if (state.gridOverlayEnabled && !state.gmMapOverlayActive) {
                world.execute(() -> com.gridifymydungeon.plugin.gridmove.GridOverlayManager.removeGridOverlay(world, state));
            }
            playerRef.sendMessage(Message.raw("[Griddify] Monster holds position — walk back to it to move it.").color("#87CEEB"));
        } else {
            // Remove movement overlay so it doesn't re-appear on next movement after cast
            if (state.gridOverlayEnabled && !state.gmMapOverlayActive) {
                world.execute(() -> com.gridifymydungeon.plugin.gridmove.GridOverlayManager.removeGridOverlay(world, state));
            }
            state.freeze("post_cast");
            playerRef.sendMessage(Message.raw("[Griddify] NPC holds position — walk to a new cell to move it.").color("#87CEEB"));
        }

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

    // ── Projectile scheduler ──────────────────────────────────────────────────

    private static final java.util.concurrent.ScheduledExecutorService PROJ_SCHEDULER =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "griddify-proj-scheduler");
                t.setDaemon(true);
                return t;
            });

    private void launchSingleProjectile(ProjectileType type, World world,
                                        SpellCastingState castState,
                                        int targetGridX, int targetGridZ,
                                        float npcY, List<PlayerRef> players) {
        float ox = (castState.getCasterGridX() * 2.0f) + 1.0f;
        float oy = npcY + 1.2f;
        float oz = (castState.getCasterGridZ() * 2.0f) + 1.0f;
        float tx = (targetGridX * 2.0f) + 1.0f;
        float tz = (targetGridZ * 2.0f) + 1.0f;
        Float groundY = SpellVisualManager.scanForGround(world, targetGridX, targetGridZ, npcY + 30f, 45);
        float ty = (groundY != null ? groundY : npcY) + 1.2f;
        SpellProjectile.launch(type, world, new Vector3d(ox, oy, oz), new Vector3d(tx, ty, tz), players);
    }

    /**
     * Fireball / Sphere spells: one projectile → impact explosion lingers.
     * lingerScale and lingerMs control the explosion size and duration.
     */
    private void launchFireballProjectile(ProjectileType type, World world,
                                          SpellCastingState castState,
                                          int targetGridX, int targetGridZ,
                                          float npcY, List<PlayerRef> players) {
        launchFireballProjectile(type, world, castState, targetGridX, targetGridZ, npcY, players, 1.5f, 1000L);
    }

    private void launchFireballProjectile(ProjectileType type, World world,
                                          SpellCastingState castState,
                                          int targetGridX, int targetGridZ,
                                          float npcY, List<PlayerRef> players,
                                          float lingerScale, long lingerMs) {
        float ox = (castState.getCasterGridX() * 2.0f) + 1.0f;
        float oy = npcY + 1.2f;
        float oz = (castState.getCasterGridZ() * 2.0f) + 1.0f;
        float tx = (targetGridX * 2.0f) + 1.0f;
        float tz = (targetGridZ * 2.0f) + 1.0f;
        Float groundY = SpellVisualManager.scanForGround(world, targetGridX, targetGridZ, npcY + 30f, 45);
        float ty = (groundY != null ? groundY : npcY) + 1.2f;

        double dist = Math.sqrt(Math.pow(tx - ox, 2) + Math.pow(tz - oz, 2));
        long travelMs = (long)(dist / 12.0 * 1000) + 150;

        SpellProjectile.launch(type, world, new Vector3d(ox, oy, oz), new Vector3d(tx, ty, tz), players);

        final double ftx = tx, fty = ty, ftz = tz;
        final float fScale = lingerScale;
        final long fLinger = lingerMs;
        PROJ_SCHEDULER.schedule(() -> world.execute(() ->
                        SpellVisualEffect.spawnWithTimeout(
                                "Explosion", fScale, world, ftx, fty, ftz, 0f, fLinger)),
                travelMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * LINE spells (Lightning_Bolt, Lightning_Arrow, Empowered_Lightning_Bolt, Devastating_Charge):
     * Fire BOLT_COUNT projectiles distributed across the cells of the line.
     */
    private static final int LINE_PROJECTILE_COUNT = 8;

    private void launchLineProjectiles(ProjectileType type, World world,
                                       SpellCastingState castState,
                                       Set<SpellPatternCalculator.GridCell> cells,
                                       float npcY, List<PlayerRef> players) {
        if (cells.isEmpty()) return;
        float ox = (castState.getCasterGridX() * 2.0f) + 1.0f;
        float oy = npcY + 1.2f;
        float oz = (castState.getCasterGridZ() * 2.0f) + 1.0f;

        // Sort cells by distance from caster so bolts travel along the line in order
        java.util.List<SpellPatternCalculator.GridCell> sorted = new java.util.ArrayList<>(cells);
        sorted.sort(java.util.Comparator.comparingDouble(c ->
                Math.sqrt(Math.pow(c.x - castState.getCasterGridX(), 2)
                        + Math.pow(c.z - castState.getCasterGridZ(), 2))));

        for (int i = 0; i < LINE_PROJECTILE_COUNT; i++) {
            SpellPatternCalculator.GridCell cell = sorted.get(i % sorted.size());
            Float groundY = SpellVisualManager.scanForGround(world, cell.x, cell.z, npcY + 30f, 45);
            double ty = (groundY != null ? groundY : npcY) + 1.2;
            double jx = (Math.random() - 0.5) * 0.6;
            double jz = (Math.random() - 0.5) * 0.6;
            final double tx = (cell.x * 2.0) + 1.0 + jx;
            final double fty = ty;
            final double tz = (cell.z * 2.0) + 1.0 + jz;
            final long delay = i * 40L; // stagger 40ms between bolts
            if (delay == 0) {
                SpellProjectile.launch(type, world, new Vector3d(ox, oy, oz), new Vector3d(tx, fty, tz), players);
            } else {
                PROJ_SCHEDULER.schedule(() -> world.execute(() ->
                                SpellProjectile.launch(type, world, new Vector3d(ox, oy, oz),
                                        new Vector3d(tx, fty, tz), players)),
                        delay, java.util.concurrent.TimeUnit.MILLISECONDS);
            }
        }
    }

    /**
     * CHAIN spells (Chaos_Bolt, Chain_Lightning):
     * Primary bolt to first target, then chain to adjacent targets with 200ms gaps.
     */
    private void launchChainProjectile(ProjectileType type, World world,
                                       SpellCastingState castState,
                                       java.util.List<SpellCastingState.GridCell> targets,
                                       float npcY, List<PlayerRef> players) {
        if (targets.isEmpty()) return;

        // First bolt from caster
        float ox = (castState.getCasterGridX() * 2.0f) + 1.0f;
        float oy = npcY + 1.2f;
        float oz = (castState.getCasterGridZ() * 2.0f) + 1.0f;

        SpellCastingState.GridCell first = targets.get(0);
        Float gy = SpellVisualManager.scanForGround(world, first.x, first.z, npcY + 30f, 45);
        double ty = (gy != null ? gy : npcY) + 1.2;
        double tx = (first.x * 2.0) + 1.0;
        double tz = (first.z * 2.0) + 1.0;

        SpellProjectile.launch(type, world, new Vector3d(ox, oy, oz), new Vector3d(tx, ty, tz), players);

        // Estimate travel time for chain delay
        double dist = Math.sqrt(Math.pow(tx - ox, 2) + Math.pow(tz - oz, 2));
        long chainDelay = (long)(dist / 12.0 * 1000) + 100;

        // Chain bolts between subsequent targets
        for (int i = 1; i < targets.size() && i <= 6; i++) {
            SpellCastingState.GridCell prev = targets.get(i - 1);
            SpellCastingState.GridCell curr = targets.get(i);
            final long delay = chainDelay + (i - 1) * 200L;
            final double prevX = (prev.x * 2.0) + 1.0, prevY = ty, prevZ = (prev.z * 2.0) + 1.0;
            Float cgy = SpellVisualManager.scanForGround(world, curr.x, curr.z, npcY + 30f, 45);
            final double cty = (cgy != null ? cgy : npcY) + 1.2;
            final double ctx = (curr.x * 2.0) + 1.0, ctz = (curr.z * 2.0) + 1.0;
            PROJ_SCHEDULER.schedule(() -> world.execute(() ->
                            SpellProjectile.launch(type, world,
                                    new Vector3d(prevX, prevY, prevZ),
                                    new Vector3d(ctx, cty, ctz), players)),
                    delay, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Returns the animation ID to play on the NPC for a given spell key, or null if none.
     * Fighter sword_swing uses Longsword_Stab_Charged (the charged swing animation).
     * More attacks can be added here later once the mechanic is verified.
     */
    /**
     * Returns {animationId, itemAnimationsId} pair for a spell, or null if no animation.
     * animationId = animation name inside the weapon set (SwingRight / SwingLeft / SwingUpLeft).
     * itemAnimationsId = weapon's PlayerAnimationsId field ("Longsword" etc.).
     * AnimationSlot.Action bypasses model-animation validation so any string is accepted.
     */
    private static String[] getNpcAnimationForSpell(String spellKey) {
        switch (spellKey) {
            case "sword_swing":      return new String[]{"SwingRight",  "Longsword"};
            case "paladin_strike":   return new String[]{"SwingUpLeft", "Longsword"};
            case "sneak_stab":       return new String[]{"SwingLeft",   "Longsword"};
            case "pact_blade":       return new String[]{"SwingUpLeft", "Longsword"};
            default:                  return null;
        }
    }

    /** Convert Direction8 to yaw radians for entity orientation.
     *  Uses atan2(-dx, -dz) matching Hytale's facing vector convention:
     *  facing direction = (-sin(yaw), -cos(yaw)) where yaw=0 => facing -Z (North). */
    private static float directionToYaw(Direction8 dir) {
        return (float) Math.atan2(-dir.getDeltaX(), -dir.getDeltaZ());
    }



    private static final int CONE_PROJECTILE_COUNT = 15;

    private void launchConeProjectiles(ProjectileType type, World world,
                                       SpellCastingState castState,
                                       Set<SpellPatternCalculator.GridCell> cells,
                                       float npcY, List<PlayerRef> players) {
        if (cells.isEmpty()) return;
        float ox = (castState.getCasterGridX() * 2.0f) + 1.0f;
        float oy = npcY + 1.2f;
        float oz = (castState.getCasterGridZ() * 2.0f) + 1.0f;

        java.util.List<double[]> centers = new java.util.ArrayList<>();
        for (SpellPatternCalculator.GridCell cell : cells) {
            Float groundY = SpellVisualManager.scanForGround(world, cell.x, cell.z, npcY + 30f, 45);
            double ty = (groundY != null ? groundY : npcY) + 1.2;
            centers.add(new double[]{ (cell.x * 2.0) + 1.0, ty, (cell.z * 2.0) + 1.0 });
        }
        for (int i = 0; i < CONE_PROJECTILE_COUNT; i++) {
            double[] base = centers.get(i % centers.size());
            double jx = (Math.random() - 0.5) * 1.4;
            double jz = (Math.random() - 0.5) * 1.4;
            final double tx = base[0] + jx, ty = base[1], tz = base[2] + jz;
            SpellProjectile.launch(type, world, new Vector3d(ox, oy, oz), new Vector3d(tx, ty, tz), players);
        }
    }

    // ── Existing helpers (unchanged) ─────────────────────────────────────────

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

    private static String buildHPBar(int current, int max) {
        if (max <= 0) return "";
        int filled = (int) Math.round(10.0 * current / max);
        filled = Math.max(0, Math.min(10, filled));
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 10; i++) sb.append(i < filled ? "#" : ".");
        sb.append("]");
        return sb.toString();
    }
}