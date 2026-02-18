package com.gridifymydungeon.plugin.gridmove;

import com.gridifymydungeon.plugin.dnd.*;
import com.gridifymydungeon.plugin.dnd.commands.*;
import com.gridifymydungeon.plugin.gridmove.commands.*;
import com.gridifymydungeon.plugin.gridmove.packet.*;
import com.gridifymydungeon.plugin.spell.*;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.event.EventRegistration;
import com.hypixel.hytale.protocol.packets.player.ClientMovement;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketWatcher;
import com.hypixel.hytale.server.core.io.handlers.game.GamePacketHandler;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;
import java.util.logging.Level;


public class GridMovePlugin extends JavaPlugin {

    public static final String VERSION = "0.3.2";

    // Managers
    private GridMoveManager gridMoveManager;
    private RoleManager roleManager;
    private EncounterManager encounterManager;
    private CombatManager combatManager;
    private CombatSettings combatSettings;
    private PlayerDataManager playerDataManager;
    private CollisionDetector collisionDetector;

    // Trackers
    private PlayerPositionTracker positionTracker;
    private GMPositionTracker gmPositionTracker;

    // Handlers
    private ClientMovementHandler movementHandler;

    // Listeners
    private PlayerDisconnectListener disconnectListener;

    // Event registrations
    private EventRegistration<Void, PlayerDisconnectEvent> disconnectRegistration;

    public GridMovePlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    public void setup() {
        getLogger().at(Level.INFO).log("Setting up GridMove v%s", VERSION);

        // Initialize managers
        this.gridMoveManager = new GridMoveManager();
        this.roleManager = new RoleManager();
        this.encounterManager = new EncounterManager(roleManager);
        this.combatSettings = new CombatSettings();
        this.combatManager = new CombatManager(gridMoveManager, encounterManager, roleManager);
        this.playerDataManager = new PlayerDataManager(this.getDataDirectory().toFile());
        this.collisionDetector = new CollisionDetector(gridMoveManager, encounterManager);

        // Initialize trackers
        this.positionTracker = new PlayerPositionTracker(
                gridMoveManager, roleManager, encounterManager, combatManager, collisionDetector);
        this.gmPositionTracker = new GMPositionTracker(
                encounterManager, roleManager, combatManager, collisionDetector, gridMoveManager);

        // Initialize movement handler
        this.movementHandler = new ClientMovementHandler(positionTracker, gmPositionTracker, roleManager, gridMoveManager);

        // Initialize listeners
        this.disconnectListener = new PlayerDisconnectListener(gridMoveManager, roleManager);

        // Register event listeners
        this.disconnectRegistration = this.getEventRegistry().register(
                EventPriority.NORMAL,
                PlayerDisconnectEvent.class,
                event -> this.disconnectListener.onPlayerDisconnect(event)
        );

        // Register packet handlers
        registerPacketHandlers();

        // Register commands
        registerCommands();

        getLogger().at(Level.INFO).log("GridMove v%s setup complete!", VERSION);
    }

    @Override
    public void shutdown() {
        getLogger().at(Level.INFO).log("Shutting down GridMove v%s", VERSION);

        if (this.disconnectRegistration != null) {
            this.disconnectRegistration.unregister();
        }

        getLogger().at(Level.INFO).log("GridMove v%s shutdown complete!", VERSION);
    }

    private void registerPacketHandlers() {
        PacketAdapters.registerInbound((PacketWatcher) (handler, packet) -> {
            if (packet instanceof ClientMovement && handler instanceof GamePacketHandler) {
                try {
                    GamePacketHandler gameHandler = (GamePacketHandler) handler;
                    movementHandler.handleMovement(
                            gameHandler.getPlayerRef(),
                            (ClientMovement) packet
                    );
                } catch (Exception e) {
                    getLogger().at(Level.WARNING)
                            .log("Error handling ClientMovement: " + e.getMessage());
                }
            }
        });

        getLogger().at(Level.INFO).log("Registered ClientMovement packet handler");
    }

    private void registerCommands() {
        // Role commands
        getCommandRegistry().registerCommand(new GMCommand(roleManager));
        getCommandRegistry().registerCommand(new GridPlayerCommand(roleManager));
        getCommandRegistry().registerCommand(new GridNullCommand(roleManager));
        getCommandRegistry().registerCommand(new GridRestartCommand(roleManager));

        // Combat command declared early so EndTurnCommand can reference it
        CombatCommand combatCommand = new CombatCommand(roleManager, combatManager, gridMoveManager);

        // Player commands
        getCommandRegistry().registerCommand(new GridMoveCommand(gridMoveManager, collisionDetector, roleManager));
        getCommandRegistry().registerCommand(new MaxTurnsCommand(gridMoveManager));
        getCommandRegistry().registerCommand(new EndTurnCommand(gridMoveManager, combatManager, combatCommand));
        getCommandRegistry().registerCommand(new GridCamCommand());
        getCommandRegistry().registerCommand(new ClearHologramsCommand(gridMoveManager));
        getCommandRegistry().registerCommand(new GridOnCommand(gridMoveManager, collisionDetector, encounterManager, roleManager));
        getCommandRegistry().registerCommand(new GridOffCommand(gridMoveManager));
        getCommandRegistry().registerCommand(new GridToggleCommand(gridMoveManager, collisionDetector, encounterManager, roleManager));

        // GM commands
        getCommandRegistry().registerCommand(new CreatureCommand(encounterManager, roleManager, collisionDetector));
        getCommandRegistry().registerCommand(new ControlCommand(roleManager, encounterManager, gridMoveManager));
        getCommandRegistry().registerCommand(new SlainCommand(encounterManager, roleManager));

        // Stat commands
        getCommandRegistry().registerCommand(new STRCommand(gridMoveManager, roleManager, encounterManager));
        getCommandRegistry().registerCommand(new DEXCommand(gridMoveManager, roleManager, encounterManager));
        getCommandRegistry().registerCommand(new CONCommand(gridMoveManager, roleManager, encounterManager));
        getCommandRegistry().registerCommand(new INTCommand(gridMoveManager, roleManager, encounterManager));
        getCommandRegistry().registerCommand(new WISCommand(gridMoveManager, roleManager, encounterManager));
        getCommandRegistry().registerCommand(new CHACommand(gridMoveManager, roleManager, encounterManager));
        getCommandRegistry().registerCommand(new HPCommand(gridMoveManager, roleManager, encounterManager));
        getCommandRegistry().registerCommand(new ArmorCommand(gridMoveManager, roleManager, encounterManager));
        getCommandRegistry().registerCommand(new InitiativeCommand(gridMoveManager, roleManager, encounterManager));
        getCommandRegistry().registerCommand(new FlyingCommand(gridMoveManager, roleManager, encounterManager));

        // Character code system
        getCommandRegistry().registerCommand(new GridRegisterCommand(gridMoveManager, roleManager));
        getCommandRegistry().registerCommand(new GridLoginCommand(gridMoveManager, roleManager));
        getCommandRegistry().registerCommand(new GridProfileCommand(gridMoveManager, roleManager));

        // Dice commands
        getCommandRegistry().registerCommand(new DiceCommand(combatSettings));
        getCommandRegistry().registerCommand(new AdDiceCommand(combatSettings));
        getCommandRegistry().registerCommand(new DisDiceCommand(combatSettings));

        // Combat commands
        getCommandRegistry().registerCommand(new InitivCommand(roleManager, combatManager));
        getCommandRegistry().registerCommand(combatCommand);
        getCommandRegistry().registerCommand(new CriticalCommand(roleManager, combatSettings));

        // Preset commands - FIXED: GridPresetsCommand renamed to GridClassesCommand
        getCommandRegistry().registerCommand(new GridClassesCommand());

        // Level commands - FIXED: Removed extra arguments
        getCommandRegistry().registerCommand(new LevelUpCommand(gridMoveManager, roleManager));
        getCommandRegistry().registerCommand(new LevelDownCommand(gridMoveManager, roleManager));

        // Class selection commands - FIXED: Removed extra arguments
        getCommandRegistry().registerCommand(new GridClassCommand(gridMoveManager, roleManager, encounterManager));
        getCommandRegistry().registerCommand(new GridSubclassCommand(gridMoveManager));

        // FIXED: SpellVisualManager now receives world at call time (not at init), avoiding null world on startup
        SpellVisualManager spellVisualManager = new SpellVisualManager(gridMoveManager);
        positionTracker.setSpellVisualManager(spellVisualManager);
        gmPositionTracker.setSpellVisualManager(spellVisualManager);
        PersistentSpellManager persistentSpellManager = new PersistentSpellManager();

        // Spell casting commands - FIXED: Removed extra arguments
        getCommandRegistry().registerCommand(new ListSpellsCommand(gridMoveManager, roleManager, encounterManager));
        getCommandRegistry().registerCommand(new CastCommand(gridMoveManager, encounterManager, spellVisualManager, roleManager));
        getCommandRegistry().registerCommand(new CastTargetCommand(gridMoveManager, spellVisualManager));
        getCommandRegistry().registerCommand(new CastFinalCommand(gridMoveManager, encounterManager, spellVisualManager, combatSettings, roleManager));
        getCommandRegistry().registerCommand(new CastCancelCommand(gridMoveManager, spellVisualManager, encounterManager, roleManager));

        // Help command
        getCommandRegistry().registerCommand(new GridHelpCommand(roleManager));

        getLogger().at(Level.INFO).log("Registered all commands successfully!");
    }

    // Getters
    public GridMoveManager getGridMoveManager() { return gridMoveManager; }
    public RoleManager getRoleManager() { return roleManager; }
    public EncounterManager getEncounterManager() { return encounterManager; }
    public CombatManager getCombatManager() { return combatManager; }
    public CombatSettings getCombatSettings() { return combatSettings; }
    public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
}