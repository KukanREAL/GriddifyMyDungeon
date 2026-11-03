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

        // ── CUSTOM CAST path ─────────────────────────────────────────────────
        CustomCastState custom = state.getCustomCastState();
        if (custom != null) {
            if (!custom.hasDamageConfigured()) {
                playerRef.sendMessage(Message.raw(
                        "[Griddify] Set damage first! Use /cdmg and/or /cdicedmg.").color("#FF0000"));
                return;
            }
            if (!custom.hasTargets()) {
                playerRef.sendMessage(Message.raw(
                        "[Griddify] No targets confirmed! Use /casttarget to mark targets.").color("#FF0000"));
                return;
            }

            // Roll damage once
            StringBuilder rollLog = new StringBuilder();
            int totalDmg = custom.rollDamage(rollLog);

            // Report
            playerRef.sendMessage(Message.raw(
                    "[Griddify] ★ CUSTOM ATTACK FIRED! " + custom.getTargetCount() + " target(s)").color("#FF69B4"));
            playerRef.sendMessage(Message.raw(
                    "[Griddify] Roll: " + rollLog.toString() + " = " + totalDmg + " damage").color("#FF6B6B"));

            // List each target
            StringBuilder targetList = new StringBuilder("[Griddify] Targets: ");
            java.util.List<int[]> targets = custom.getConfirmedTargets();
            for (int i = 0; i < targets.size(); i++) {
                int[] t = targets.get(i);
                targetList.append("(").append(t[0]).append(",").append(t[1]).append(")");
                if (i < targets.size() - 1) targetList.append(", ");
            }
            playerRef.sendMessage(Message.raw(targetList.toString()).color("#FFAAAA"));
            playerRef.sendMessage(Message.raw(
                    "[Griddify] Apply " + totalDmg + " damage to each marked target.").color("#FF0000"));

            // Cleanup
            state.clearCustomCastState();
            world.execute(() -> {
                visualManager.clearSpellVisuals(playerRef.getUuid(), world);
                visualManager.clearRangeOverlay(playerRef.getUuid(), world);
            });

            System.out.println("[Griddify] [CUSTOM] " + playerRef.getUsername()
                    + " fired custom attack: " + rollLog + " = " + totalDmg
                    + " on " + targets.size() + " targets");
            return;
        }

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

        final GridPlayerState fSt = state;
        final float fFaceYaw      = faceYaw;
        final float fCasterWX     = casterWX;
        final float fCasterWZ     = casterWZ;
        final float fTargetWX     = targetWX;
        final float fTargetWZ     = targetWZ;
        final Set<SpellPatternCalculator.GridCell> fAffectedCells = affectedCells;

        // BUG 3 FIX: Capture the raw confirmed-targets LIST (allows same-cell duplicates).
        // affectedCells is a HashSet which deduplicates by (x,z), so hitting the same cell
        // 3 times would only produce 1 projectile. The raw list preserves all 3 entries.
        final java.util.List<SpellPatternCalculator.GridCell> fTargetList;
        if (spell.isMultiTarget() && !castState.getConfirmedTargets().isEmpty()) {
            java.util.List<SpellPatternCalculator.GridCell> tl = new java.util.ArrayList<>();
            for (SpellCastingState.GridCell c : castState.getConfirmedTargets()) {
                tl.add(new SpellPatternCalculator.GridCell(c.x, c.z));
            }
            fTargetList = tl;
        } else {
            fTargetList = null; // non-multi-target: use fAffectedCells
        }

        // BUG 4 FIX: Detect weapon from storedRightHand item ID, NOT spell name.
        // Generic spells (Fireball, Cure_Wounds, etc.) have no "staff"/"spellbook" in their name,
        // so the old name-based detection always fell through to the default animation.
        // storedRightHand is e.g. "weapon_staff_oak", "weapon_spellbook_arcane", "weapon_shortbow_simple"
        String spellName = spell.getName();
        String rightHandId = state.storedRightHand != null ? state.storedRightHand.toLowerCase() : "";
        boolean isBowShot         = spellName.equalsIgnoreCase("Bow_Shot");
        boolean isStaffWeapon     = rightHandId.startsWith("weapon_staff");
        boolean isSpellbookWeapon = rightHandId.startsWith("weapon_spellbook");

        long effectDelayMs = isBowShot ? 2500L : (isStaffWeapon || isSpellbookWeapon ? 1300L : 0L); // BUG 3 FIX: was 2000ms, -700ms

        world.execute(() -> {
            // FIX 2: Rotate NPC to face the target (for bow, rotate immediately so it faces during draw)
            PlayerEntityController.setNpcYaw(world, fSt, fFaceYaw);

            // BUG 4 FIX: Pass rightHand so animation is picked from equipped weapon
            String[] anim = pickCastAnimation(spell, fSt.storedRightHand);
            String animationId = anim[0];
            String itemAnimationsId = anim[1];
            System.out.println("[Griddify] [CASTFINAL] anim=" + animationId
                    + " itemAnim=" + itemAnimationsId + " rightHand=" + fSt.storedRightHand);
            PlayerEntityController.playNpcAnimation(world, fSt, animationId, itemAnimationsId);

            // Auto-stop animation after delay (for staff/spellbook, stop after 5000ms)
            long stopDelay = (isStaffWeapon || isSpellbookWeapon) ? 5000L : 1500L;
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
                    .schedule(() -> world.execute(() ->
                                    PlayerEntityController.stopNpcAnimation(world, fSt)),
                            stopDelay, java.util.concurrent.TimeUnit.MILLISECONDS);

            // BUG FIX 6: Area-effect dispatch - handle spells that need spawnWave instead of projectiles
            SpellPattern spellPattern = spell.getPattern();
            String spellNameLower = spellName.toLowerCase();

            // Check if this is a wave-based spell (Thunderwave, Entangle, Moonbeam, etc.)
            boolean isWaveSpell = spellNameLower.contains("thunderwave") ||
                    spellNameLower.contains("entangle") ||
                    spellNameLower.contains("moonbeam") ||
                    spellNameLower.contains("sunbeam") ||
                    spellNameLower.contains("ice_storm");

            if (isWaveSpell) {
                // Spawn wave effect for these spells instead of projectiles
                System.out.println("[Griddify] [WAVE] Spawning wave effect for " + spellName);
                int casterGX = castState.getCasterGridX();
                int casterGZ = castState.getCasterGridZ();
                List<PlayerRef> ignored = new java.util.ArrayList<>();
                ignored.add(playerRef);

                if (spellNameLower.contains("entangle")) {
                    if (effectDelayMs > 0) {
                        java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
                                .schedule(() -> world.execute(() ->
                                                SpellVisualEffect.spawnEntangle(world, fAffectedCells, fSt.npcY)),
                                        effectDelayMs, java.util.concurrent.TimeUnit.MILLISECONDS);
                    } else {
                        SpellVisualEffect.spawnEntangle(world, fAffectedCells, fSt.npcY);
                    }
                } else if (spellNameLower.contains("ice_storm")) {
                    if (effectDelayMs > 0) {
                        java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
                                .schedule(() -> world.execute(() ->
                                                SpellVisualEffect.spawnIceStorm(world, fAffectedCells, fSt.npcY, ignored)),
                                        effectDelayMs, java.util.concurrent.TimeUnit.MILLISECONDS);
                    } else {
                        SpellVisualEffect.spawnIceStorm(world, fAffectedCells, fSt.npcY, ignored);
                    }
                } else {
                    // Default to spawnWave (Thunderwave, Moonbeam, Sunbeam)
                    if (effectDelayMs > 0) {
                        java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
                                .schedule(() -> world.execute(() ->
                                                SpellVisualEffect.spawnWave(spellName, 1.0f, world, casterGX, casterGZ, fSt.npcY, fAffectedCells, ignored, fFaceYaw)),
                                        effectDelayMs, java.util.concurrent.TimeUnit.MILLISECONDS);
                    } else {
                        SpellVisualEffect.spawnWave(spellName, 1.0f, world, casterGX, casterGZ, fSt.npcY, fAffectedCells, ignored, fFaceYaw);
                    }
                }
                // Skip projectile logic for wave spells
                return;
            }

            // FIX 3: Launch projectiles based on spell type
            String[] projectileData = pickProjectileModel(spell);

            if (projectileData != null && fSt.npcEntity != null && fSt.npcEntity.isValid()) {
                final String projectileModel = projectileData[0];
                final float projectileScale = Float.parseFloat(projectileData[1]);
                final double startX = fCasterWX;
                final double startY = fSt.npcY + 1.4;
                final double startZ = fCasterWZ;

                // Special case: Magic_Missile fires projectiles per target cell
                boolean isMagicMissile = spellName.equalsIgnoreCase("Magic_Missile");

                // Special case: CONE/LINE/WALL spells (like Burning_Hands) fire projectiles to each affected cell
                boolean isAreaProjectile = (spellPattern == SpellPattern.CONE ||
                        spellPattern == SpellPattern.LINE ||
                        spellPattern == SpellPattern.WALL);

                if (isMagicMissile) {
                    // BUG 3 FIX: Use fTargetList (raw confirmed list, allows same-cell duplicates).
                    // If the player targeted the same cell 3x, fire 3 projectiles.
                    java.util.List<SpellPatternCalculator.GridCell> missiles =
                            (fTargetList != null && !fTargetList.isEmpty())
                                    ? fTargetList
                                    : new java.util.ArrayList<>(fAffectedCells);
                    System.out.println("[Griddify] [PROJECTILE] Magic_Missile - firing " + missiles.size() + " dart(s)");
                    for (SpellPatternCalculator.GridCell targetCell : missiles) {
                        final double endX = targetCell.x * 2.0f + 1.0f;
                        final double endY = fSt.npcY + 1.4;
                        final double endZ = targetCell.z * 2.0f + 1.0f;

                        if (effectDelayMs > 0) {
                            java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
                                    .schedule(() -> world.execute(() ->
                                                    SpellVisualEffect.launchProjectile(
                                                            projectileModel, projectileScale, world,
                                                            startX, startY, startZ,
                                                            endX, endY, endZ,
                                                            fFaceYaw, 600L)),
                                            effectDelayMs, java.util.concurrent.TimeUnit.MILLISECONDS);
                        } else {
                            SpellVisualEffect.launchProjectile(
                                    projectileModel, projectileScale, world,
                                    startX, startY, startZ,
                                    endX, endY, endZ,
                                    fFaceYaw, 600L);
                        }
                    }
                } else if (isAreaProjectile) {
                    // CONE/LINE/WALL spells: fire projectiles to each affected cell.
                    // Burning_Hands: near→far staggered columns (wave of fire bolts).
                    // Other CONE/LINE/WALL: all simultaneously.
                    boolean isBurningHands = spellNameLower.contains("burning_hands");

                    if (isBurningHands) {
                        // Group cells by Chebyshev distance from caster — each distance = one column.
                        // Fire each column 100ms after the previous to create a rolling fire-wave effect.
                        java.util.TreeMap<Integer, java.util.List<SpellPatternCalculator.GridCell>> byDist =
                                new java.util.TreeMap<>();
                        int bhCasterGX = castState.getCasterGridX();
                        int bhCasterGZ = castState.getCasterGridZ();
                        for (SpellPatternCalculator.GridCell c : fAffectedCells) {
                            int d = SpellPatternCalculator.getDistance(bhCasterGX, bhCasterGZ, c.x, c.z);
                            byDist.computeIfAbsent(d, k -> new java.util.ArrayList<>()).add(c);
                        }
                        int colIndex = 0;
                        System.out.println("[Griddify] [PROJECTILE] Burning_Hands wave - " + byDist.size() + " columns");
                        for (java.util.List<SpellPatternCalculator.GridCell> col : byDist.values()) {
                            final long colDelay = effectDelayMs + (colIndex * 100L);
                            final java.util.List<SpellPatternCalculator.GridCell> fCol = col;
                            java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
                                    .schedule(() -> world.execute(() -> {
                                        for (SpellPatternCalculator.GridCell targetCell : fCol) {
                                            final double endX = targetCell.x * 2.0f + 1.0f;
                                            final double endY = fSt.npcY + 1.4;
                                            final double endZ = targetCell.z * 2.0f + 1.0f;
                                            SpellVisualEffect.launchProjectile(
                                                    projectileModel, projectileScale, world,
                                                    startX, startY, startZ,
                                                    endX, endY, endZ,
                                                    fFaceYaw, 400L); // shorter travel = snappier fire feel
                                        }
                                    }), colDelay, java.util.concurrent.TimeUnit.MILLISECONDS);
                            colIndex++;
                        }
                    } else {
                        // Other CONE/LINE/WALL: fire all simultaneously
                        System.out.println("[Griddify] [PROJECTILE] Area projectile - firing " + fAffectedCells.size() + " cells simultaneously");
                        for (SpellPatternCalculator.GridCell targetCell : fAffectedCells) {
                            final double endX = targetCell.x * 2.0f + 1.0f;
                            final double endY = fSt.npcY + 1.4;
                            final double endZ = targetCell.z * 2.0f + 1.0f;

                            if (effectDelayMs > 0) {
                                java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
                                        .schedule(() -> world.execute(() ->
                                                        SpellVisualEffect.launchProjectile(
                                                                projectileModel, projectileScale, world,
                                                                startX, startY, startZ,
                                                                endX, endY, endZ,
                                                                fFaceYaw, 600L)),
                                                effectDelayMs, java.util.concurrent.TimeUnit.MILLISECONDS);
                            } else {
                                SpellVisualEffect.launchProjectile(
                                        projectileModel, projectileScale, world,
                                        startX, startY, startZ,
                                        endX, endY, endZ,
                                        fFaceYaw, 600L);
                            }
                        }
                    }
                } else {
                    // Single-target spells: Fire one projectile to aim point
                    final double endX = fTargetWX;
                    final double endY = fSt.npcY + 1.4;
                    final double endZ = fTargetWZ;

                    System.out.println("[Griddify] [PROJECTILE] Launching " + projectileModel + " scale=" + projectileScale);

                    if (effectDelayMs > 0) {
                        java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
                                .schedule(() -> world.execute(() ->
                                                SpellVisualEffect.launchProjectile(
                                                        projectileModel, projectileScale, world,
                                                        startX, startY, startZ,
                                                        endX, endY, endZ,
                                                        fFaceYaw, 600L)),
                                        effectDelayMs, java.util.concurrent.TimeUnit.MILLISECONDS);
                    } else {
                        SpellVisualEffect.launchProjectile(
                                projectileModel, projectileScale, world,
                                startX, startY, startZ,
                                endX, endY, endZ,
                                fFaceYaw, 600L);
                    }
                }
            }
        });
        // ── end of NPC visual fixes ────────────────────────────────────────────────────

        // Clear visuals and casting state
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
     * BUG 4 FIX: Pick NPC cast animation based on the EQUIPPED WEAPON (rightHandId item ID),
     * not the spell name. Generic spells like "Fireball" or "Cure_Wounds" don't contain
     * "staff" or "spellbook" so name-based detection always fell to the default case.
     *
     * itemAnimationsId meanings:
     *   null         = base character animation (CastSummonCharging, CastHurlCharging, ShootCharging)
     *   "Longsword"  = longsword weapon-overlay animation set (SwingRight, SwingLeft, SwingUpLeft)
     *   "Default"    = unarmed / default weapon-overlay animation set (SwingLeft)
     *
     * @param spell       the spell being cast
     * @param rightHandId state.storedRightHand — the item ID of the equipped weapon, may be null
     * @return {animationId, itemAnimationsId}
     */
    private static String[] pickCastAnimation(SpellData spell, String rightHandId) {
        String sn = spell.getName().toLowerCase();
        String rh = rightHandId != null ? rightHandId.toLowerCase() : "";

        // ── Explicit per-spell overrides (melee weapon spells keyed by spell name) ──────
        if (sn.equals("sword_swing"))       return new String[]{"SwingRight",       "Longsword"};
        if (sn.equals("paladin_strike"))     return new String[]{"SwingUpLeft",      "Longsword"};
        if (sn.equals("sneak_stab"))         return new String[]{"SwingLeft",        "Longsword"};
        if (sn.equals("pact_blade"))         return new String[]{"SwingUpLeft",      "Longsword"};
        if (sn.equals("greataxe_swing"))     return new String[]{"SwingRight",       "Longsword"};
        if (sn.equals("shortsword_slash"))   return new String[]{"SwingLeft",        "Longsword"};
        if (sn.equals("morningstar_strike")) return new String[]{"SwingRight",       "Longsword"};
        if (sn.equals("staff_strike"))       return new String[]{"SwingLeft",        "Default"};
        if (sn.equals("quarterstaff"))       return new String[]{"SwingLeft",        "Default"};
        if (sn.equals("unarmed_strike"))     return new String[]{"SwingLeft",        "Default"};
        if (sn.equals("dagger_throw"))       return new String[]{"SwingRight",       "Default"};
        if (sn.equals("bow_shot"))           return new String[]{"ShootCharging",    null};

        // ── Detect from equipped weapon item ID ──────────────────────────────────────────
        // This is the primary fix: ANY spell cast while holding a staff plays the staff anim
        if (rh.startsWith("weapon_staff"))         return new String[]{"CastSummonCharging", null};
        if (rh.startsWith("weapon_spellbook"))     return new String[]{"CastHurlCharging",   null};
        if (rh.startsWith("weapon_shortbow")
                || rh.startsWith("weapon_longbow")) return new String[]{"ShootCharging",      null};
        if (rh.startsWith("weapon_longsword")
                || rh.startsWith("weapon_shortsword")
                || rh.startsWith("weapon_rapier")
                || rh.startsWith("weapon_greataxe")
                || rh.startsWith("weapon_morningstar"))
            return new String[]{"SwingRight",          "Longsword"};

        // ── Fallback: generic hurl for bare-handed or unknown equipment ─────────────────
        return new String[]{"CastHurlCharging", null};
    }

    /**
     * FIX 3: Pick the projectile model and scale for flying from caster to target.
     * Returns [modelId, scale] or null if no visual is appropriate.
     */
    private static String[] pickProjectileModel(SpellData spell) {
        String spellName = spell.getName().toLowerCase();

        // Healing spells
        if (spell.isHealingSpell()) {
            return new String[]{"Heal_One", "0.5"};
        }

        // Fire spells → Fire_Bolt
        if (spellName.contains("fire_bolt") || spellName.contains("produce_flame") ||
                spellName.contains("fireball") || spellName.contains("meteor") ||
                spellName.contains("burning_hands")) {
            if (spellName.contains("produce_flame")) return new String[]{"Fire_Bolt", "0.8"};
            if (spellName.contains("quickened_fireball")) return new String[]{"Fire_Bolt", "2.0"};
            if (spellName.contains("delayed_blast")) return new String[]{"Fire_Bolt", "3.0"};
            if (spellName.contains("meteor_swarm")) return new String[]{"Fire_Bolt", "4.0"};
            if (spellName.contains("fireball")) return new String[]{"Fire_Bolt", "2.5"};
            if (spellName.contains("burning_hands")) return new String[]{"Fire_Bolt", "1.3"};
            return new String[]{"Fire_Bolt", "1.0"};
        }

        // Cold spells → Frost_Bolt
        if (spellName.contains("cone_of_cold") || spellName.contains("ice_storm") ||
                spellName.contains("dragonfrost")) {
            if (spellName.contains("ice_storm")) return new String[]{"Frost_Bolt", "0.9"};
            return new String[]{"Frost_Bolt", "1.3"};
        }

        // Lightning spells → Lightning_Bolt
        if (spellName.contains("lightning")) {
            if (spellName.contains("lightning_bolt")) return new String[]{"Lightning_Bolt", "1.4"};
            return new String[]{"Lightning_Bolt", "1.0"};
        }

        // Thunder → Thunder_Bolt
        if (spellName.contains("shatter")) return new String[]{"Thunder_Bolt", "2.2"};
        if (spellName.contains("greataxe")) return new String[]{"Thunder_Bolt", "1.0"};

        // Magic Missile → Magic_Bolt
        if (spellName.contains("magic_missile")) {
            return new String[]{"Magic_Bolt", "1.0"};
        }

        // Acid → Acid_Bolt
        if (spellName.contains("sneak") || spellName.contains("dagger") ||
                spellName.contains("shortsword")) {
            return new String[]{"Acid_Bolt", "0.7"};
        }

        // Bow → Arrow_Iron
        if (spellName.contains("bow_shot")) {
            return new String[]{"Arrow_Iron", "0.8"};
        }

        // Necrotic → Crimson_Spell
        if (spellName.contains("soul") || spellName.contains("hadar") ||
                spellName.contains("hunger")) {
            return new String[]{"Crimson_Spell", "1.5"};
        }

        // Arcane/Force/Radiant → Arcane_Bolt
        if (spellName.contains("arcane") || spellName.contains("eldritch") ||
                spellName.contains("sacred") || spellName.contains("divine") ||
                spellName.contains("spiritual") || spellName.contains("radiant") ||
                spellName.contains("psychic") || spellName.contains("prismatic") ||
                spellName.contains("staff_strike") || spellName.contains("unarmed") ||
                spellName.contains("power_strike") || spellName.contains("cleave") ||
                spellName.contains("whirlwind") || spellName.contains("flurry") ||
                spellName.contains("sword_swing")) {
            if (spellName.contains("psychic_blast")) return new String[]{"Arcane_Bolt", "2.5"};
            if (spellName.contains("prismatic")) return new String[]{"Arcane_Bolt", "2.0"};
            if (spellName.contains("staff_strike")) return new String[]{"Arcane_Bolt", "0.8"};
            if (spellName.contains("unarmed")) return new String[]{"Arcane_Bolt", "0.7"};
            return new String[]{"Arcane_Bolt", "1.0"};
        }

        // Damage type fallbacks
        if (spell.getDamageType() != null) {
            switch (spell.getDamageType()) {
                case FIRE:      return new String[]{"Fire_Bolt", "1.0"};
                case COLD:      return new String[]{"Frost_Bolt", "1.0"};
                case LIGHTNING: return new String[]{"Lightning_Bolt", "1.0"};
                case THUNDER:   return new String[]{"Thunder_Bolt", "1.0"};
                case FORCE:     return new String[]{"Arcane_Bolt", "1.0"};
                case ACID:      return new String[]{"Acid_Bolt", "1.0"};
                case POISON:    return new String[]{"Poison_Bolt", "1.0"};
                case NECROTIC:  return new String[]{"Crimson_Spell", "1.0"};
                case RADIANT:   return new String[]{"Arcane_Bolt", "1.0"};
                default:        return new String[]{"Arcane_Bolt", "1.0"};
            }
        }

        return null;
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