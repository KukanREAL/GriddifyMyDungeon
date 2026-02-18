package com.gridifymydungeon.plugin.dnd;

import com.gridifymydungeon.plugin.gridmove.GridMoveManager;
import com.gridifymydungeon.plugin.gridmove.GridPlayerState;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.*;

public class CombatManager {

    private final GridMoveManager playerManager;
    private final EncounterManager encounterManager;
    private final RoleManager roleManager;

    // Combat state
    private boolean combatActive = false;
    private List<CombatParticipant> turnOrder = new ArrayList<>();
    private int currentTurnIndex = 0;
    private int roundNumber = 0;

    public CombatManager(GridMoveManager playerManager, EncounterManager encounterManager, RoleManager roleManager) {
        this.playerManager = playerManager;
        this.encounterManager = encounterManager;
        this.roleManager = roleManager;
    }

    public static class CombatParticipant {
        public String name;
        public int initiativeRoll;
        public int initiativeModifier;
        public int totalInitiative;
        public boolean isPlayer;
        public UUID playerUUID; // For players
        public int monsterNumber; // For monsters
        public boolean skipTurn; // If initiative = 0

        public CombatParticipant(String name, int roll, int modifier, boolean isPlayer) {
            this.name = name;
            this.initiativeRoll = roll;
            this.initiativeModifier = modifier;
            this.totalInitiative = Math.max(0, roll + modifier);
            this.isPlayer = isPlayer;
            this.skipTurn = (this.totalInitiative == 0);
        }
    }

    public List<CombatParticipant> startCombat() {
        turnOrder.clear();
        currentTurnIndex = 0;
        roundNumber = 1;

        System.out.println("[Griddify] [COMBAT] Rolling initiative for all participants...");

        // Roll for all players
        for (GridPlayerState playerState : playerManager.getAllStates()) {
            if (playerState.npcEntity != null && playerState.npcEntity.isValid()) {
                int roll = rollD20();
                int modifier = playerState.stats.initiative;

                CombatParticipant participant = new CombatParticipant(
                        "Player", roll, modifier, true
                );

                turnOrder.add(participant);

                System.out.println("[Griddify] [COMBAT] Player rolled " + roll + " + " + modifier + " = " + participant.totalInitiative);
            }
        }

        // Roll for all monsters
        for (MonsterState monster : encounterManager.getAllMonsters().values()) {
            int roll = rollD20();
            int modifier = monster.stats.initiative;

            CombatParticipant participant = new CombatParticipant(
                    monster.getDisplayName(), roll, modifier, false
            );
            participant.monsterNumber = monster.monsterNumber;

            turnOrder.add(participant);

            System.out.println("[Griddify] [COMBAT] " + monster.getDisplayName() +
                    " rolled " + roll + " + " + modifier + " = " + participant.totalInitiative);
        }

        // Sort by total initiative (highest first)
        turnOrder.sort((a, b) -> Integer.compare(b.totalInitiative, a.totalInitiative));

        // Filter out participants with initiative 0 (they skip turn and reroll next)
        List<CombatParticipant> activeParticipants = new ArrayList<>();
        for (CombatParticipant p : turnOrder) {
            if (!p.skipTurn) {
                activeParticipants.add(p);
            } else {
                System.out.println("[Griddify] [COMBAT] " + p.name + " rolled 0 - skipping turn, will reroll");
            }
        }

        turnOrder = activeParticipants;
        combatActive = true;

        System.out.println("[Griddify] [COMBAT] Combat started! Turn order established.");
        return new ArrayList<>(turnOrder);
    }

    public void endCombat() {
        combatActive = false;
        turnOrder.clear();
        currentTurnIndex = 0;
        roundNumber = 0;
        System.out.println("[Griddify] [COMBAT] Combat ended");
    }

    public CombatParticipant nextTurn() {
        if (!combatActive || turnOrder.isEmpty()) {
            return null;
        }

        currentTurnIndex++;
        if (currentTurnIndex >= turnOrder.size()) {
            currentTurnIndex = 0;
            roundNumber++;
            System.out.println("[Griddify] [COMBAT] Round " + roundNumber + " started!");
        }

        CombatParticipant current = getCurrentParticipant();
        System.out.println("[Griddify] [COMBAT] Now turn: " + current.name);
        return current;
    }

    public CombatParticipant getCurrentParticipant() {
        if (!combatActive || turnOrder.isEmpty()) {
            return null;
        }
        return turnOrder.get(currentTurnIndex);
    }

    public boolean isPlayerTurn(PlayerRef playerRef) {
        if (!combatActive) {
            return true; // No combat = everyone can move
        }

        CombatParticipant current = getCurrentParticipant();
        if (current == null || !current.isPlayer) {
            return false;
        }

        // Check if this player matches
        // For now, simplified - in full version, track by UUID
        return current.isPlayer;
    }

    public boolean isMonsterTurn(int monsterNumber) {
        if (!combatActive) {
            return true; // No combat = GM can move freely
        }

        CombatParticipant current = getCurrentParticipant();
        if (current == null || current.isPlayer) {
            return false;
        }

        return current.monsterNumber == monsterNumber;
    }


    public boolean isCombatActive() {
        return combatActive;
    }

    public int getCurrentTurnIndex() {
        return currentTurnIndex;
    }

    public int getRoundNumber() {
        return roundNumber;
    }

    public List<CombatParticipant> getTurnOrder() {
        return new ArrayList<>(turnOrder);
    }

    private int rollD20() {
        return (int) (Math.random() * 20) + 1;
    }

    public List<CombatParticipant> rollInitiativeOnly() {
        List<CombatParticipant> results = new ArrayList<>();

        // Roll for players
        for (GridPlayerState playerState : playerManager.getAllStates()) {
            if (playerState.npcEntity != null && playerState.npcEntity.isValid()) {
                int roll = rollD20();
                int modifier = playerState.stats.initiative;

                CombatParticipant participant = new CombatParticipant(
                        "Player", roll, modifier, true
                );
                results.add(participant);
            }
        }

        // Roll for monsters
        for (MonsterState monster : encounterManager.getAllMonsters().values()) {
            int roll = rollD20();
            int modifier = monster.stats.initiative;

            CombatParticipant participant = new CombatParticipant(
                    monster.getDisplayName(), roll, modifier, false
            );
            participant.monsterNumber = monster.monsterNumber;
            results.add(participant);
        }

        return results;
    }
}