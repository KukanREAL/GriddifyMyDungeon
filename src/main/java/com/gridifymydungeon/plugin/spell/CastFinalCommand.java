package com.gridifymydungeon.plugin.spell;

import com.gridifymydungeon.plugin.dnd.CombatSettings;
import com.gridifymydungeon.plugin.dnd.EncounterManager;
import com.gridifymydungeon.plugin.dnd.MonsterState;
import com.gridifymydungeon.plugin.dnd.PlayerEntityController;
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
 *
 * FIXES:
 *   Bug 2: NPC now rotates to face the cast target via setNpcYaw().
 *   Bug 3: A mid-air projectile model is sent toward the target via SpellVisualEffect.
 *   Bug 4: A cast animation is played on the NPC via playNpcAnimation() and auto-stopped after 1.5 s.
 *   Bug 5: 0-cost spells (cantrips, monster attacks) skip consumeSpellSlot entirely,
 *           so Burning_Hands / Thunderwave etc. no longer falsely abort with "Not enough spell slots!".
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

        state.hasUsedAction = true;

        // FIX 5: Only consume spell slots for spells that actually cost slots (cost > 0).
        // 0-cost spells (cantrips, monster attacks, free abilities like Burning_Hands/Thunderwave)
        // previously hit consumeSpellSlot(0) which returned false when slots=0, falsely aborting.
        int cost = spell.getSlotCost();
        if (cost > 0 && !state.stats.consumeSpellSlot(cost)) {
            playerRef.sendMessage(Message.raw("[Griddify] Not enough spell slots!").color("#FF0000"));
            state.clearSpellCastingState();
            visualManager.clearSpellVisuals(playerRef.getUuid(), world);
            return;
        }

        SpellPattern pattern = spell.getPattern();

        Set<SpellPatternCalculator.GridCell> affectedCells;

        if (spell.isMultiTarget() && !castState.getConfirmedTargets().isEmpty()) {
            affectedCells = new HashSet<>();
            for (SpellCastingState.GridCell c : castState.getConfirmedTargets()) {
                affectedCells.add(new SpellPatternCalculator.GridCell(c.x, c.z));
            }
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
                    affectedCells = SpellPatternCalculator.calculatePattern(
                            pattern, castState.getDirection(),
                            aimGridX, aimGridZ,
                            spell.getRangeGrids(), spell.getAreaGrids());
                    break;
            }
        }

        // Debug log
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
            }
        }

        int totalDamage = isHeal ? 0 : rollAmount;
        int monstersHit = targetsAffected;

        // Persistent spell handling
        if (spell.isPersistent()) {
            PersistentSpellEffect persistentEffect = new PersistentSpellEffect(
                    spell, playerRef, affectedCells, 0);
            // TODO: persistentSpellManager.addEffect(persistentEffect);
            playerRef.sendMessage(Message.raw("  Duration: " + spell.getDurationTurns() + " turns").color("#9370DB"));
        }

        // ── FIX 2+3+4: NPC rotation, animation, and mid-air visual ───────────────────
        // Compute the world-space direction from the caster NPC toward the aimed cell.
        float casterWX = castState.getCasterGridX() * 2.0f + 1.0f;
        float casterWZ = castState.getCasterGridZ() * 2.0f + 1.0f;
        float targetWX = aimGridX * 2.0f + 1.0f;
        float targetWZ = aimGridZ * 2.0f + 1.0f;
        float faceDx   = targetWX - casterWX;
        float faceDz   = targetWZ - casterWZ;
        float faceYaw  = (float) Math.atan2(-faceDx, -faceDz);

        final String animId       = pickCastAnimation(spell);
        final GridPlayerState fSt = state;
        final float fFaceYaw      = faceYaw;
        final float fCasterWX     = casterWX;
        final float fCasterWZ     = casterWZ;
        final float fTargetWX     = targetWX;
        final float fTargetWZ     = targetWZ;

        world.execute(() -> {
            // FIX 2: Rotate NPC to face the target
            PlayerEntityController.setNpcYaw(world, fSt, fFaceYaw);

            // FIX 4: Play cast animation and auto-stop after 1.5 s
            PlayerEntityController.playNpcAnimation(world, fSt, animId, null);
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
                    .schedule(() -> world.execute(() ->
                                    PlayerEntityController.stopNpcAnimation(world, fSt)),
                            1500L, java.util.concurrent.TimeUnit.MILLISECONDS);

            // FIX 3: Send a mid-air model toward the target
            String projectileModel = pickProjectileModel(spell);
            if (projectileModel != null && fSt.npcEntity != null && fSt.npcEntity.isValid()) {
                // Midpoint between caster and target, at chest height
                double midX = (fCasterWX + fTargetWX) * 0.5;
                double midY = fSt.npcY + 1.4;
                double midZ = (fCasterWZ + fTargetWZ) * 0.5;
                SpellVisualEffect.spawnWithTimeout(
                        projectileModel, 0.5f, world, midX, midY, midZ, fFaceYaw, 600L);
            }
        });
        // ── end of NPC visual fixes ────────────────────────────────────────────────────

        // Clear visuals and casting state
        visualManager.clearSpellVisuals(playerRef.getUuid(), world);
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

    // ── Helpers ──────────────────────────────────────────────────────────────────

    /**
     * FIX 4: Pick an animation asset ID based on the spell's pattern and damage type.
     * Animation IDs must match assets registered in the game; adjust names as needed.
     */
    private static String pickCastAnimation(SpellData spell) {
        SpellPattern pattern = spell.getPattern();
        // Area/directional spells use a sweeping cast
        if (pattern == SpellPattern.CONE
                || pattern == SpellPattern.WALL
                || pattern == SpellPattern.LINE) {
            return "Spellcast_Area";
        }
        // Healing spells
        if (spell.isHealingSpell()) return "Spellcast_Heal";
        // Damage type specific animations
        if (spell.getDamageType() != null) {
            switch (spell.getDamageType()) {
                case FIRE:      return "Spellcast_Fire";
                case COLD:      return "Spellcast_Ice";
                case LIGHTNING: return "Spellcast_Lightning";
                default:        break;
            }
        }
        // Generic fallback
        return "Spellcast_1";
    }

    /**
     * FIX 3: Pick the model ID to fly from caster to target.
     * Returns null if no visual is appropriate for this spell.
     */
    private static String pickProjectileModel(SpellData spell) {
        if (spell.isHealingSpell()) return "Heal_One";
        if (spell.getDamageType() == null) return null;
        switch (spell.getDamageType()) {
            case FIRE:      return "Fireball";
            case COLD:      return "Frost_Bolt";
            case LIGHTNING: return "Lightning_Bolt";
            case NECROTIC:  return "Necrotic_Orb";
            default:        return null;
        }
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

    /** ASCII health bar: [##########..........] (10 segments) */
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