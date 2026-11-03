package com.gridifymydungeon.plugin.spell;

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

/**
 * /orb {acid|fire|cold|lightning|poison|thunder}
 *
 * Must be used after /cast chromatic_orb and before /casttarget.
 * Picks the damage type and matching projectile model.
 * Once chosen, /casttarget and /castfinal behave normally.
 */
public class OrbCommand extends AbstractPlayerCommand {

    private final GridMoveManager playerManager;
    private final RequiredArg<String> elementArg;

    public OrbCommand(GridMoveManager playerManager) {
        super("orb", "Choose Chromatic Orb element: /orb {acid|fire|cold|lightning|poison|thunder}");
        this.playerManager = playerManager;
        this.elementArg = this.withRequiredArg("element", "Element: acid/fire/cold/lightning/poison/thunder", ArgTypes.STRING);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {

        GridPlayerState state = playerManager.getState(playerRef);
        SpellCastingState castState = state.getSpellCastingState();

        if (castState == null || !castState.isValid()) {
            playerRef.sendMessage(Message.raw(
                    "[Griddify] No spell being cast. Use /cast chromatic_orb first.").color("#FF0000"));
            return;
        }

        String spellName = castState.getSpell().getName().toLowerCase();
        if (!spellName.equals("chromatic_orb")) {
            playerRef.sendMessage(Message.raw(
                    "[Griddify] /orb is only for Chromatic Orb.").color("#FF0000"));
            return;
        }

        String element = elementArg.get(context).toLowerCase();
        if (ProjectileType.forElement(element) == null) {
            playerRef.sendMessage(Message.raw(
                    "[Griddify] Unknown element '" + element
                            + "'. Choose: acid, fire, cold, lightning, poison, thunder.").color("#FF0000"));
            return;
        }

        castState.setChromaticElement(element);

        // Color-coded confirmation per element
        String color = switch (element) {
            case "acid"      -> "#7CFC00";
            case "fire"      -> "#FF4500";
            case "cold"      -> "#00BFFF";
            case "lightning" -> "#FFD700";
            case "poison"    -> "#9400D3";
            case "thunder"   -> "#B0C4DE";
            default          -> "#FFFFFF";
        };

        playerRef.sendMessage(Message.raw(
                "[Griddify] Chromatic Orb charged with "
                        + element.substring(0, 1).toUpperCase() + element.substring(1)
                        + "! Now use /CastTarget → /CastFinal.").color(color));
    }
}