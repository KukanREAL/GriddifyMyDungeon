package com.gridifymydungeon.plugin.spell;

import com.gridifymydungeon.plugin.dnd.CombatSettings;
import com.gridifymydungeon.plugin.dnd.EncounterManager;
import com.gridifymydungeon.plugin.dnd.MonsterState;
import com.gridifymydungeon.plugin.dnd.RoleManager;
import com.gridifymydungeon.plugin.gridmove.GridMoveManager;
import com.gridifymydungeon.plugin.gridmove.GridPlayerState;
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
 * CHANGED: Added Fire_Bolt visual projectile launch.
 * When a SORCERER casts Fire_Bolt (/Cast Fire_Bolt → walk to aim → /CastFinal),
 * after the normal damage resolution a Fire_BoltProjectile is launched from the
 * player's NPC position to the aimed grid cell as a visual effect.
 * No other spells are affected. All existing logic is unchanged.
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
        boolean casterIsGM = roleManager.isGM(playerRef);
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
                final String spellKey = spell.getName().toLowerCase()
                        .replace(" ", "_").replace("-", "_");
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

                        } else if (projPattern == SpellPattern.SPHERE
                                && (spellKey.equals("fireball") || spellKey.equals("quickened_fireball"))) {
                            java.util.List<SpellCastingState.GridCell> targets =
                                    new java.util.ArrayList<>(castState.getConfirmedTargets());
                            if (targets.isEmpty())
                                targets.add(new SpellCastingState.GridCell(aimGridX, aimGridZ));
                            final SpellCastingState.GridCell tc = targets.get(0);
                            world.execute(() -> launchFireballProjectile(
                                    finalType, world, finalCastState, tc.x, tc.z, npcYFinal, allPlayers));

                        } else {
                            java.util.List<SpellCastingState.GridCell> targets =
                                    new java.util.ArrayList<>(castState.getConfirmedTargets());
                            if (targets.isEmpty())
                                targets.add(new SpellCastingState.GridCell(aimGridX, aimGridZ));
                            for (int i = 0; i < targets.size(); i++) {
                                final SpellCastingState.GridCell tc = targets.get(i);
                                final long delayMs = i * 120L;
                                if (delayMs == 0) {
                                    world.execute(() -> launchSingleProjectile(
                                            finalType, world, finalCastState, tc.x, tc.z, npcYFinal, allPlayers));
                                } else {
                                    PROJ_SCHEDULER.schedule(() ->
                                                    world.execute(() -> launchSingleProjectile(
                                                            finalType, world, finalCastState, tc.x, tc.z, npcYFinal, allPlayers)),
                                            delayMs, java.util.concurrent.TimeUnit.MILLISECONDS);
                                }
                            }
                        }
                    }

                    // ── STATIONARY / WAVE / RAIN effects ─────────────────────
                    switch (spellKey) {
                        case "entangle": {
                            for (SpellPatternCalculator.GridCell c : finalCells) {
                                float wx = (c.x * 2.0f) + 1.0f;
                                float wz = (c.z * 2.0f) + 1.0f;
                                Float groundY = SpellVisualManager.scanForGround(
                                        world, c.x, c.z, npcYFinal + 30f, 45);
                                float wy = groundY != null ? groundY : npcYFinal;
                                world.execute(() -> SpellVisualEffect.spawnGrowing(
                                        "Entangle", 1.0f, world, wx, wy, wz, 500L));
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
                            world.execute(() -> SpellVisualEffect.spawnWave(
                                    "Thunderwave", 1.0f, world,
                                    castState.getCasterGridX(), castState.getCasterGridZ(),
                                    npcYFinal, finalCells, allPlayers));
                            break;
                        }
                        case "ice_storm": {
                            world.execute(() -> SpellVisualEffect.spawnIceStorm(
                                    world, finalCells, npcYFinal, allPlayers));
                            break;
                        }
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
     * Fireball: one large projectile that — on arrival — spawns a Moon_Beam for 1 second.
     * SpellProjectile.launch returns the Ref; we use a callback via a custom overload
     * that runs arrivalCallback instead of just despawning.
     * Since SpellProjectile doesn't support callbacks yet, we schedule the Moon_Beam
     * based on travel-time estimate (dist / velocity) + 200ms padding.
     */
    private void launchFireballProjectile(ProjectileType type, World world,
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

        double dist = Math.sqrt(Math.pow(tx - ox, 2) + Math.pow(tz - oz, 2));
        // MUZZLE_VELOCITY is 12 world units/s; estimate travel time + buffer
        long travelMs = (long)(dist / 12.0 * 1000) + 150;

        SpellProjectile.launch(type, world, new Vector3d(ox, oy, oz), new Vector3d(tx, ty, tz), players);

        // Schedule Moon_Beam at target for 1 second after projectile arrives
        final double ftx = tx, fty = ty, ftz = tz;
        PROJ_SCHEDULER.schedule(() -> world.execute(() ->
                        SpellVisualEffect.spawnWithTimeout(
                                "Moon_Beam", 1.5f, world, ftx, fty, ftz, 0f, 1000L)),
                travelMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /** Convert Direction8 to yaw radians for entity orientation. */
    private static float directionToYaw(Direction8 dir) {
        switch (dir) {
            case NORTH:     return 0f;
            case NORTHEAST: return (float)(Math.PI * 0.25);
            case EAST:      return (float)(Math.PI * 0.5);
            case SOUTHEAST: return (float)(Math.PI * 0.75);
            case SOUTH:     return (float) Math.PI;
            case SOUTHWEST: return (float)(Math.PI * 1.25);
            case WEST:      return (float)(Math.PI * 1.5);
            case NORTHWEST: return (float)(Math.PI * 1.75);
            default:        return 0f;
        }
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