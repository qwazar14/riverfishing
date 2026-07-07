package com.riverfishing.registry;

import com.riverfishing.RiverFishing;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Custom fishing sounds (§sound). Files live in assets/riverfishing/sounds, mapped by sounds.json. */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> REGISTER =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, RiverFishing.MODID);

    public static final RegistryObject<SoundEvent> CAST_BOTTOM = reg("cast_bottom"); // long-cast rods
    public static final RegistryObject<SoundEvent> CAST_SPIN = reg("cast_spin");     // spinning/ultralight
    public static final RegistryObject<SoundEvent> LINE_BREAK = reg("line_break");   // the rig snaps off
    public static final RegistryObject<SoundEvent> ALARM_BELL = reg("alarm_bell");   // bite-alarm bell
    public static final RegistryObject<SoundEvent> ROD_CREAK = reg("rod_creak");     // blank straining
    public static final RegistryObject<SoundEvent> DRAG_NOTE = reg("drag_note");     // one ratchet click
    public static final RegistryObject<SoundEvent> DRAG_LONG = reg("drag_long");     // long drag scream

    private ModSounds() {}

    private static RegistryObject<SoundEvent> reg(String name) {
        return REGISTER.register(name, () -> SoundEvent.createVariableRangeEvent(RiverFishing.id(name)));
    }
}
