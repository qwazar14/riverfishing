package com.riverfishing.registry;

import com.riverfishing.RiverFishing;
import com.riverfishing.effect.FishOilEffect;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;

/**
 * The mod's mob effects (§fish-oil-potion). Exactly one so far: the marker the Potion of Fish Oil carries so
 * that it can CLEAR Mining Fatigue — see {@link FishOilEffect} for why that has to be an effect at all.
 *
 * <p>Bound before {@link ModPotions}: a {@code Potion}'s effect list is built when the POTION registry binds,
 * and it needs this one to exist by then.
 */
public final class ModEffects {
    public static final DeferredRegister<MobEffect> REGISTER =
            DeferredRegister.create(RiverFishing.MODID, Registries.MOB_EFFECT);

    /** Invisible while it works; named in the inventory list as "Fish Oil". */
    public static final RegistrySupplier<MobEffect> FISH_OIL = REGISTER.register("fish_oil", FishOilEffect::new);

    public static void init() {
        REGISTER.register();
    }

    private ModEffects() {}
}
