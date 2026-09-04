package com.riverfishing.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;

/**
 * The platform-abstraction seam (§multiloader). Methods here are declared in {@code common} and
 * implemented per loader: Architectury's {@code @ExpectPlatform} rewrites each call at build time to
 * the matching {@code com.riverfishing.platform.<forge|fabric>.PlatformHelperImpl} static method.
 *
 * <p>This is the pattern every later stage uses for the Forge-only surfaces (registration lives in
 * Architectury's unified DeferredRegister, but things like "is a mod loaded" / config dir go here).
 */
public final class PlatformHelper {
    private PlatformHelper() {}

    /** "Forge" or "Fabric" — proves the @ExpectPlatform redirect resolves on each loader. */
    @ExpectPlatform
    public static String platformName() {
        throw new AssertionError("@ExpectPlatform stub — replaced per platform at build time");
    }

    /** Whether another mod is present (used later to gate Serene Seasons / Biomes O' Plenty features). */
    @ExpectPlatform
    public static boolean isModLoaded(String modId) {
        throw new AssertionError("@ExpectPlatform stub — replaced per platform at build time");
    }

    /**
     * Puts the Potion of Fish Oil into the brewing stand (§fish-oil-potion). Brewing is the one recipe type
     * that is CODE, not JSON, in every dialect the mod ships on, and 1.20.1's two loaders do not even agree
     * on what a brewing recipe IS — Fabric appends to vanilla's static mix table, Forge keeps a list of its
     * own {@code IBrewingRecipe}s. So the mixes are stated once in {@code ModPotions.addMixes} and each
     * implementation here only says how its loader is told.
     *
     * <p>Called once from {@link com.riverfishing.RiverFishing#init()}, after the potions are queued.
     */
    @ExpectPlatform
    public static void registerBrewing() {
        throw new AssertionError("@ExpectPlatform stub — replaced per platform at build time");
    }
}
