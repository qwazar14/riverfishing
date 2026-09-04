package com.riverfishing.registry;

import com.riverfishing.RiverFishing;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.Potions;

/**
 * The Potion of Fish Oil and its two modifier variants (§fish-oil-potion). Brewed from a water bottle and
 * {@code fish_oil} — the same rendered oil that goes into a groundbait, which stays exactly what it was.
 *
 * <p>The oil came out of sea fish, so the potion SWIMS (dolphin's grace + water breathing); it is the
 * omega-3 line, so it MENDS (regeneration); it braces a body against a knock (resistance); and it carries
 * {@link ModEffects#FISH_OIL}, the marker whose only job is to clear Mining Fatigue — a potion is a list of
 * effects to add, and nothing in such a list can take one away.
 *
 * <p>Vanilla's modifiers, vanilla-shaped: glowstone doubles the mending and the bracing and halves every
 * duration; redstone stretches only the WATER effects out to four minutes; gunpowder needs no code at all —
 * potion→splash is a container mix in vanilla's own chain and applies to any registered potion, which is
 * also where the lingering bottle and the tipped arrow come from.
 *
 * <p>All three share the display name {@code "fish_oil"} the way vanilla's strong/long variants share theirs
 * ({@code Potions.STRONG_STRENGTH} is {@code new Potion("strength", …)}), so one lang key names all of them
 * per container. The numbers below are the patchnote's numbers; {@code tools/check_fish_oil_potion.py} reads
 * them straight out of this file so the two cannot drift apart.
 */
public final class ModPotions {
    public static final DeferredRegister<Potion> REGISTER =
            DeferredRegister.create(RiverFishing.MODID, Registries.POTION);

    // --- the base bottle -------------------------------------------------------------------------------
    public static final int SWIM_TICKS = 1800;          // 1:30 dolphin's grace + water breathing
    public static final int MEND_TICKS = 900;           // 0:45 regeneration
    public static final int BRACE_TICKS = 1800;         // 1:30 resistance

    // --- glowstone: level II, every duration halved ------------------------------------------------------
    public static final int STRONG_SWIM_TICKS = 900;    // 0:45
    public static final int STRONG_MEND_TICKS = 450;    // 0:22, vanilla's own strong-regeneration length
    public static final int STRONG_BRACE_TICKS = 900;   // 0:45

    // --- redstone: the WATER effects only, out to four minutes ------------------------------------------
    public static final int LONG_SWIM_TICKS = 4800;     // 4:00

    public static final RegistrySupplier<Potion> FISH_OIL =
            REGISTER.register("fish_oil", () -> oil(SWIM_TICKS, MEND_TICKS, 0, BRACE_TICKS, 0));

    public static final RegistrySupplier<Potion> STRONG_FISH_OIL =
            REGISTER.register("strong_fish_oil", () -> oil(STRONG_SWIM_TICKS, STRONG_MEND_TICKS, 1, STRONG_BRACE_TICKS, 1));

    public static final RegistrySupplier<Potion> LONG_FISH_OIL =
            REGISTER.register("long_fish_oil", () -> oil(LONG_SWIM_TICKS, MEND_TICKS, 0, BRACE_TICKS, 0));

    public static void init() {
        REGISTER.register();
    }

    /**
     * The three mixes, in one place, so both loaders say the same thing (§fish-oil-potion). Fabric and
     * NeoForge reach the same vanilla {@code PotionBrewing.Builder} by different doors — see the two
     * {@code PlatformHelperImpl.registerBrewing()} — but from here on the recipe is identical.
     *
     * <p>Nothing registers the splash, lingering or tipped-arrow forms: those are CONTAINER mixes in
     * vanilla's chain (gunpowder/dragon's breath swap the bottle and keep the potion) and so already work
     * for anything in the POTION registry.
     */
    /**
     * §oil-brew: the fish a stand will render down — the same nine as the {@code oily_fish} item tag the
     * furnace reads. It has to be written twice: a tag is datapack-time and the brewing table is built
     * before any datapack is read, so there is nothing to look the tag up in. tools/check_oil_brew.py
     * is what keeps the two copies the same list.
     */
    private static final String[] OILY = {"herring", "mackerel", "salmon", "pink_salmon", "sabrefish",
                                          "eel", "bluefish", "bluefin_tuna", "pollock"};

    public static void addMixes(PotionBrewing.Builder builder) {
        builder.addMix(Potions.WATER, ModItems.FISH_OIL.get(), FISH_OIL);
        // §oil-brew: and the FISH itself, over an awkward base. The oil had one source, a furnace, and a
        // smoker runs no smelting recipe — so the obvious tool for a fish did nothing and the oil looked
        // unobtainable. The rendering step is still there for anyone who wants the ingredient.
        for (String sp : OILY) {
            var fish = ModItems.FISH_ITEMS.get(com.riverfishing.RiverFishing.id(sp));
            if (fish != null) builder.addMix(Potions.AWKWARD, fish.get(), FISH_OIL);
        }
        builder.addMix(Potions.AWKWARD, ModItems.FISH_OIL.get(), FISH_OIL);
        builder.addMix(FISH_OIL, Items.GLOWSTONE_DUST, STRONG_FISH_OIL);
        builder.addMix(FISH_OIL, Items.REDSTONE, LONG_FISH_OIL);
    }

    private ModPotions() {}

    /**
     * One bottle. The marker rides the swim duration so it outlives the splash/lingering rescale — vanilla
     * DROPS any effect that would end within 20 ticks of landing, and a one-tick marker would be lost.
     */
    private static Potion oil(int swim, int mend, int mendAmp, int brace, int braceAmp) {
        return new Potion("fish_oil",
                new MobEffectInstance(MobEffects.DOLPHINS_GRACE, swim, 0),
                new MobEffectInstance(MobEffects.WATER_BREATHING, swim, 0),
                new MobEffectInstance(MobEffects.REGENERATION, mend, mendAmp),
                new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, brace, braceAmp),
                // ambient/visible/showIcon all false: the cure does its work without a HUD icon or particles.
                new MobEffectInstance(ModEffects.FISH_OIL, swim, 0, false, false, false));
    }
}
