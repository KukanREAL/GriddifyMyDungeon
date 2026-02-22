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

                // ── Spell projectile visuals ──────────────────────────────────
                ProjectileType projType = ProjectileType.forSpell(spell.getName());
                if (projType != null) {
                    final ProjectileType finalType = projType;
                    final SpellCastingState finalCastState = castState;
                    final float npcY = state.npcY;
                    final List<PlayerRef> allPlayers = playerManager.getAllPlayerRefs();
                    final SpellPattern projPattern = spell.getPattern();
                    final Set<SpellPatternCalculator.GridCell> finalCells = new HashSet<>(affectedCells);

                    if (projPattern == SpellPattern.CONE) {
                        world.execute(() -> launchConeProjectiles(
                                finalType, world, finalCastState, finalCells, npcY, allPlayers));
                    } else {
                        // Use confirmed targets if /CastTarget was used; fall back to aim cell
                        java.util.List<SpellCastingState.GridCell> targets =
                                new java.util.ArrayList<>(castState.getConfirmedTargets());
                        if (targets.isEmpty()) {
                            targets.add(new SpellCastingState.GridCell(aimGridX, aimGridZ));
                        }
                        for (int i = 0; i < targets.size(); i++) {
                            final SpellCastingState.GridCell tc = targets.get(i);
                            final long delayMs = i * 120L;
                            if (delayMs == 0) {
                                world.execute(() -> launchSingleProjectile(
                                        finalType, world, finalCastState, tc.x, tc.z, npcY, allPlayers));
                            } else {
                                PROJ_SCHEDULER.schedule(() ->
                                                world.execute(() -> launchSingleProjectile(
                                                        finalType, world, finalCastState, tc.x, tc.z, npcY, allPlayers)),
                                        delayMs, java.util.concurrent.TimeUnit.MILLISECONDS);
                            }
                        }
                    }
                }
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