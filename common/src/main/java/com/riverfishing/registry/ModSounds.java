package com.riverfishing.registry;

import com.riverfishing.RiverFishing;
import net.minecraft.sounds.SoundEvent;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;

/** Custom fishing sounds (§sound). Files live in assets/riverfishing/sounds, mapped by sounds.json. */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> REGISTER =
            DeferredRegister.create(RiverFishing.MODID, Registries.SOUND_EVENT);

    public static void init() {
        REGISTER.register();
    }

    public static final RegistrySupplier<SoundEvent> CAST_BOTTOM = reg("cast_bottom"); // long-cast rods
    public static final RegistrySupplier<SoundEvent> CAST_SPIN = reg("cast_spin");     // spinning/ultralight
    public static final RegistrySupplier<SoundEvent> LINE_BREAK = reg("line_break");   // the rig snaps off
    public static final RegistrySupplier<SoundEvent> ALARM_BELL = reg("alarm_bell");   // bite-alarm bell
    public static final RegistrySupplier<SoundEvent> ROD_CREAK = reg("rod_creak");     // blank straining
    public static final RegistrySupplier<SoundEvent> DRAG_NOTE = reg("drag_note");     // one ratchet click
    public static final RegistrySupplier<SoundEvent> DRAG_LONG = reg("drag_long");     // long drag scream

    private ModSounds() {}

    private static RegistrySupplier<SoundEvent> reg(String name) {
        return REGISTER.register(name, () -> SoundEvent.createVariableRangeEvent(RiverFishing.id(name)));
    }
}
