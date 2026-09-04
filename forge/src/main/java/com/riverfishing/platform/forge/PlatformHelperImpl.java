package com.riverfishing.platform.forge;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import net.minecraftforge.common.brewing.IBrewingRecipe;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/** Forge implementation of {@link com.riverfishing.platform.PlatformHelper} (§multiloader). */
public final class PlatformHelperImpl {
    private PlatformHelperImpl() {}

    public static String platformName() {
        return "Forge";
    }

    public static boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    /**
     * §fish-oil-potion. 1.20.1 Forge leaves {@code PotionBrewing.addMix} private and routes the brewing
     * stand through {@code BrewingRecipeRegistry} instead, so a mod adds an {@link IBrewingRecipe} rather
     * than a vanilla mix. Registered from {@code FMLCommonSetupEvent} via {@code enqueueWork} because that
     * registry is a plain static list with no synchronisation and parallel mod loading would race it.
     *
     * <p>Forge's own {@code BrewingRecipe} is not usable here: it matches its input with
     * {@code Ingredient.test}, which compares the ITEM only, so a "water bottle" ingredient would happily
     * accept a potion of healing. {@link PotionMix} below compares the potion in the stack's NBT instead,
     * and rebuilds the output on the input's own container so glowstone/redstone keep working on a splash
     * or lingering bottle exactly as they do in vanilla's chain.
     */
    public static void registerBrewing() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener((FMLCommonSetupEvent e) ->
                e.enqueueWork(() -> com.riverfishing.registry.ModPotions.addMixes(
                        (from, ingredient, to) -> BrewingRecipeRegistry.addRecipe(new PotionMix(from, ingredient, to)))));
    }

    /** One potion→potion mix, matched on the potion in the bottle rather than on the bottle. */
    private record PotionMix(Potion from, Item ingredient, Potion to) implements IBrewingRecipe {
        @Override
        public boolean isInput(ItemStack stack) {
            return (stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION))
                    && PotionUtils.getPotion(stack) == this.from;
        }

        @Override
        public boolean isIngredient(ItemStack stack) {
            return stack.is(this.ingredient);
        }

        @Override
        public ItemStack getOutput(ItemStack input, ItemStack ing) {
            if (!isInput(input) || !isIngredient(ing)) {
                return ItemStack.EMPTY;
            }
            // Keep the container the brewer put in: a splash bottle stays a splash bottle.
            return PotionUtils.setPotion(new ItemStack(input.getItem()), this.to);
        }
    }
}
