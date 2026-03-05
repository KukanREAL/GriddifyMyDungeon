package com.gridifymydungeon.plugin.gridmove;

import com.gridifymydungeon.plugin.dnd.CharacterStats;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class StatEditorPage extends InteractiveCustomUIPage<StatEditorPage.Data> {

    private final Snapshot snap = new Snapshot();
    private final String subjectName;
    private Runnable onSave;
    private Runnable onCancel;

    public StatEditorPage(@Nonnull PlayerRef playerRef,
                          @Nonnull CharacterStats stats,
                          @Nonnull String subjectName,
                          @Nonnull Runnable onSave,
                          @Nonnull Runnable onCancel) {
        super(playerRef, CustomPageLifetime.CanDismiss, Data.CODEC);
        this.subjectName = subjectName;
        this.onSave      = onSave;
        this.onCancel    = onCancel;
        snap.str        = stats.strength;
        snap.dex        = stats.dexterity;
        snap.con        = stats.constitution;
        snap.intel      = stats.intelligence;
        snap.wis        = stats.wisdom;
        snap.cha        = stats.charisma;
        snap.hp         = stats.currentHP;
        snap.maxhp      = stats.maxHP;
        snap.armor      = stats.armor;
        snap.initiative = stats.initiative;
        snap.level      = stats.getLevel();
        snap.spellSlots = stats.getSpellSlots();
    }

    // ── build() ───────────────────────────────────────────────────────────────

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder evt,
                      @Nonnull Store<EntityStore> store) {
        cmd.append("StatEditor.ui");
        cmd.set("#Title.TextSpans", Message.raw("EDIT: " + subjectName).color("#FFD700"));

        cmd.set("#FieldStr.Value",   String.valueOf(snap.str));
        cmd.set("#FieldDex.Value",   String.valueOf(snap.dex));
        cmd.set("#FieldCon.Value",   String.valueOf(snap.con));
        cmd.set("#FieldInt.Value",   String.valueOf(snap.intel));
        cmd.set("#FieldWis.Value",   String.valueOf(snap.wis));
        cmd.set("#FieldCha.Value",   String.valueOf(snap.cha));
        cmd.set("#FieldHP.Value",    String.valueOf(snap.hp));
        cmd.set("#FieldMaxHP.Value", String.valueOf(snap.maxhp));
        cmd.set("#FieldArmor.Value", String.valueOf(snap.armor));
        cmd.set("#FieldInit.Value",  String.valueOf(snap.initiative));
        cmd.set("#FieldLevel.Value", String.valueOf(snap.level));
        cmd.set("#FieldSlots.Value", String.valueOf(snap.spellSlots));

        evt.addEventBinding(CustomUIEventBindingType.ValueChanged,  "#FieldStr",   EventData.of("@str",   "#FieldStr.Value"),   false);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged,  "#FieldDex",   EventData.of("@dex",   "#FieldDex.Value"),   false);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged,  "#FieldCon",   EventData.of("@con",   "#FieldCon.Value"),   false);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged,  "#FieldInt",   EventData.of("@intel", "#FieldInt.Value"),   false);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged,  "#FieldWis",   EventData.of("@wis",   "#FieldWis.Value"),   false);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged,  "#FieldCha",   EventData.of("@cha",   "#FieldCha.Value"),   false);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged,  "#FieldHP",    EventData.of("@hp",    "#FieldHP.Value"),    false);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged,  "#FieldMaxHP", EventData.of("@maxhp", "#FieldMaxHP.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged,  "#FieldArmor", EventData.of("@armor", "#FieldArmor.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged,  "#FieldInit",  EventData.of("@init",  "#FieldInit.Value"),  false);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged,  "#FieldLevel", EventData.of("@level", "#FieldLevel.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged,  "#FieldSlots", EventData.of("@slots", "#FieldSlots.Value"), false);

        evt.addEventBinding(CustomUIEventBindingType.Activating, "#BtnSave",   EventData.of("Button", "Save"),   false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#BtnCancel", EventData.of("Button", "Cancel"), false);
    }

    // ── open() ────────────────────────────────────────────────────────────────

    public void open() {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) return;
        Store<EntityStore> store = ref.getStore();
        store.getExternalData().getWorld().execute(() -> {
            if (!ref.isValid()) return;
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                player.getPageManager().openCustomPage(ref, store, this);
            }
        });
    }

    // ── dismiss() ─────────────────────────────────────────────────────────────

    public void dismiss() {
        close();
    }

    // ── triggerSave() — called by crouch as well as Save button ───────────────

    public void triggerSave() {
        if (onSave != null) onSave.run();
    }

    // ── handleDataEvent ───────────────────────────────────────────────────────

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                Data data) {
        super.handleDataEvent(ref, store, data);

        if ("Save".equals(data.button)) {
            if (onSave != null) onSave.run();
            return;
        }
        if ("Cancel".equals(data.button)) {
            if (onCancel != null) onCancel.run();
            return;
        }

        if (data.str   != null) snap.str        = parse(data.str,    0, 30,   snap.str);
        if (data.dex   != null) snap.dex        = parse(data.dex,    0, 30,   snap.dex);
        if (data.con   != null) snap.con        = parse(data.con,    0, 30,   snap.con);
        if (data.intel != null) snap.intel      = parse(data.intel,  0, 30,   snap.intel);
        if (data.wis   != null) snap.wis        = parse(data.wis,    0, 30,   snap.wis);
        if (data.cha   != null) snap.cha        = parse(data.cha,    0, 30,   snap.cha);
        if (data.hp    != null) snap.hp         = parse(data.hp,     0, 9999, snap.hp);
        if (data.maxhp != null) snap.maxhp      = parse(data.maxhp,  1, 9999, snap.maxhp);
        if (data.armor != null) snap.armor      = parse(data.armor,  0, 50,   snap.armor);
        if (data.init  != null) snap.initiative = parse(data.init,  -10, 10,  snap.initiative);
        if (data.level != null) snap.level      = parse(data.level,  1, 20,   snap.level);
        if (data.slots != null) snap.spellSlots = parse(data.slots,  0, 3843, snap.spellSlots);

        sendUpdate();
    }

    // ── applyTo() ─────────────────────────────────────────────────────────────

    public void applyTo(@Nonnull CharacterStats stats) {
        stats.strength     = snap.str;
        stats.dexterity    = snap.dex;
        stats.constitution = snap.con;
        stats.intelligence = snap.intel;
        stats.wisdom       = snap.wis;
        stats.charisma     = snap.cha;
        stats.maxHP        = snap.maxhp;
        stats.currentHP    = Math.min(snap.hp, snap.maxhp);
        stats.armor        = snap.armor;
        stats.initiative   = snap.initiative;
        stats.setLevel(snap.level);
        stats.setSpellSlots(snap.spellSlots);
    }

    private static int parse(String raw, int min, int max, int fallback) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(raw.trim())));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // ── Snapshot ──────────────────────────────────────────────────────────────

    private static class Snapshot {
        int str, dex, con, intel, wis, cha;
        int hp, maxhp, armor, initiative;
        int level, spellSlots;
    }

    // ── Data codec ────────────────────────────────────────────────────────────

    public static class Data {
        public static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
                .append(new KeyedCodec<>("Button",  Codec.STRING), (d, v) -> d.button = v, d -> d.button).add()
                .append(new KeyedCodec<>("@str",    Codec.STRING), (d, v) -> d.str    = v, d -> d.str).add()
                .append(new KeyedCodec<>("@dex",    Codec.STRING), (d, v) -> d.dex    = v, d -> d.dex).add()
                .append(new KeyedCodec<>("@con",    Codec.STRING), (d, v) -> d.con    = v, d -> d.con).add()
                .append(new KeyedCodec<>("@intel",  Codec.STRING), (d, v) -> d.intel  = v, d -> d.intel).add()
                .append(new KeyedCodec<>("@wis",    Codec.STRING), (d, v) -> d.wis    = v, d -> d.wis).add()
                .append(new KeyedCodec<>("@cha",    Codec.STRING), (d, v) -> d.cha    = v, d -> d.cha).add()
                .append(new KeyedCodec<>("@hp",     Codec.STRING), (d, v) -> d.hp     = v, d -> d.hp).add()
                .append(new KeyedCodec<>("@maxhp",  Codec.STRING), (d, v) -> d.maxhp  = v, d -> d.maxhp).add()
                .append(new KeyedCodec<>("@armor",  Codec.STRING), (d, v) -> d.armor  = v, d -> d.armor).add()
                .append(new KeyedCodec<>("@init",   Codec.STRING), (d, v) -> d.init   = v, d -> d.init).add()
                .append(new KeyedCodec<>("@level",  Codec.STRING), (d, v) -> d.level  = v, d -> d.level).add()
                .append(new KeyedCodec<>("@slots",  Codec.STRING), (d, v) -> d.slots  = v, d -> d.slots).add()
                .build();

        public String button;
        public String str, dex, con, intel, wis, cha;
        public String hp, maxhp, armor, init;
        public String level, slots;
    }
}