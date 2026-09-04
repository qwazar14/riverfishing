package com.riverfishing.platform.neoforge;

import net.neoforged.fml.ModList;

/** NeoForge implementation of {@link com.riverfishing.platform.PlatformHelper} (§multiloader, 1.21.1). */
public final class PlatformHelperImpl {
    private PlatformHelperImpl() {}

    public static String platformName() {
        return "NeoForge";
    }

    public static boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    /**
     * §fish-oil-potion. NeoForge fires {@code RegisterBrewingRecipesEvent} from inside
     * {@code PotionBrewing.bootstrap}, every time the brewing table is rebuilt. It is a GAME-bus event
     * (it carries no {@code IModBusEvent}), so it goes on {@code NeoForge.EVENT_BUS} rather than the mod
     * bus — which is also why this can be wired from common init instead of the {@code @Mod} constructor.
     */
    public static void registerBrewing() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent.class,
                event -> {
                    com.riverfishing.registry.ModPotions.addMixes(event.getBuilder());
                    // §oil-brew-item: an EMPTY bottle with a fish over it. NeoForge's builder keeps its
                    // own list beside the vanilla ones, with an ItemStack output and no opinion about
                    // whether it is a potion — which vanilla's container table very much has.
                    com.riverfishing.registry.ModPotions.addOilBrews((bottle, fish, oil) ->
                            event.getBuilder().addRecipe(
                                    net.minecraft.world.item.crafting.Ingredient.of(bottle),
                                    net.minecraft.world.item.crafting.Ingredient.of(fish),
                                    new net.minecraft.world.item.ItemStack(oil)));
                });
    }

    /** §26.1: the vanilla BlockEntityType ctor is private — NeoForge opens it via its access transformer. */
    public static <T extends net.minecraft.world.level.block.entity.BlockEntity>
    net.minecraft.world.level.block.entity.BlockEntityType<T> createBlockEntityType(
            java.util.function.BiFunction<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState, T> factory,
            net.minecraft.world.level.block.Block... blocks) {
        return new net.minecraft.world.level.block.entity.BlockEntityType<>(
                factory::apply, java.util.Set.of(blocks));
    }
}
