package com.gridifymydungeon.plugin.dnd;

import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages monsters in encounters
 * FIXED: Use monsters.values() for ArrayList constructor
 */
public class EncounterManager {

    private final RoleManager roleManager;
    private final Map<Integer, MonsterState> monsters = new HashMap<>();
    private MonsterState controlledMonster = null;

    public EncounterManager(RoleManager roleManager) {
        this.roleManager = roleManager;
    }

    /**
     * Add a monster to the encounter
     */
    public MonsterState addMonster(String monsterName, int monsterNumber) {
        MonsterState state = new MonsterState(monsterNumber, monsterName);
        monsters.put(monsterNumber, state);
        System.out.println("[Griddify] [ENCOUNTER] Added monster: " + state.getDisplayName());
        return state;
    }

    /**
     * Get all monsters in encounter
     * FIXED: Use monsters.values() instead of passing the map directly
     */
    public List<MonsterState> getMonsters() {
        return new ArrayList<>(monsters.values());
    }

    public MonsterState getMonster(int monsterNumber) {
        return monsters.get(monsterNumber);
    }

    public void removeMonster(int monsterNumber) {
        MonsterState removed = monsters.remove(monsterNumber);
        if (removed != null) {
            // If this was controlled monster, release control
            if (controlledMonster == removed) {
                controlledMonster = null;
            }
            System.out.println("[Griddify] [ENCOUNTER] Removed monster: " + removed.getDisplayName());
        }
    }

    /**
     * Release control of current monster
     */
    public void releaseControl() {
        if (controlledMonster != null) {
            System.out.println("[Griddify] [CONTROL] Released control of: " + controlledMonster.getDisplayName());
        }
        this.controlledMonster = null;
    }

    public MonsterState getControlledMonster() {
        return controlledMonster;
    }

    public void stopControl() {
        if (controlledMonster != null) {
            controlledMonster = null;
        }
    }

    public boolean setControlled(int monsterNumber) {
        MonsterState monster = monsters.get(monsterNumber);
        if (monster != null) {
            controlledMonster = monster;
            return true;
        }
        return false;
    }

    public MonsterState getMonsterByNumber(int number) {
        return monsters.get(number);
    }

    public Map<Integer, MonsterState> getAllMonsters() {
        return monsters;
    }

    /**
     * Check if a monster is currently being controlled
     */
    public boolean isMonsterControlled(int monsterNumber) {
        MonsterState monster = monsters.get(monsterNumber);
        return monster != null && controlledMonster == monster;
    }

    public void clearAll() {
        monsters.clear();
        controlledMonster = null;
        System.out.println("[Griddify] [ENCOUNTER] Cleared all monsters");
    }
}