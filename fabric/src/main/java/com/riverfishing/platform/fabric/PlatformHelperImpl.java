package com.riverfishing.platform.fabric;

import net.fabricmc.loader.api.FabricLoader;

/** Fabric implementation of {@link com.riverfishing.platform.PlatformHelper} (§multiloader). */
public final class PlatformHelperImpl {
    private PlatformHelperImpl() {}

    public static String platformName() {
        return "Fabric";
    }

    public static boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    /**
     * §fish-oil-potion. 1.21 moved the brewing table off statics and onto a per-reload
     * {@code PotionBrewing.Builder}; Fabric API's door to it is this one build callback (fabric-api's
     * {@code FabricBrewingRecipeRegistryBuilder}, which replaced 1.20's static
     * {@code FabricBrewingRecipeRegistry}). The builder handed in is vanilla's own, so the mixes
     * themselves live in common.
     */
    public static void registerBrewing() {
        net.fabricmc.fabric.api.registry.FabricBrewingRecipeRegistryBuilder.BUILD
                .register(com.riverfishing.registry.ModPotions::addMixes);
    }
}
