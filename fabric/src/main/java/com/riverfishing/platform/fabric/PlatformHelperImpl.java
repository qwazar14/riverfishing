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
     * §fish-oil-potion. On 1.20.1 vanilla's brewing table is a pair of static lists filled once by
     * {@code PotionBrewing.bootStrap()}; Fabric API's {@code FabricBrewingRecipeRegistry} access-widens them
     * and appends. Both sides only ever append, so it does not matter whether mod init runs before or after
     * the vanilla bootstrap. (1.21 replaced all of this with a per-reload builder and a build callback.)
     */
    public static void registerBrewing() {
        com.riverfishing.registry.ModPotions.addMixes((from, ingredient, to) ->
                net.fabricmc.fabric.api.registry.FabricBrewingRecipeRegistry.registerPotionRecipe(
                        from, net.minecraft.world.item.crafting.Ingredient.of(ingredient), to));
        // §oil-brew-item: the empty-bottle recipe, through the invoker — see PotionBrewingInvoker for
        // why Fabric's own registerItemRecipe cannot carry it.
        com.riverfishing.registry.ModPotions.addOilBrews((bottle, fish, oil) -> {
            com.riverfishing.fabric.mixin.PotionBrewingInvoker.riverfishing$addContainer(bottle);
            com.riverfishing.fabric.mixin.PotionBrewingInvoker.riverfishing$addContainerRecipe(bottle, fish, oil);
        });
    }
}
