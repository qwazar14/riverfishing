package com.riverfishing.item;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.level.Level;

/**
 * §cooking: one recipe for every species — any raw caught fish matches, and the result is THAT
 * fish, cooked ({@link CookedFish#cook}), not the placeholder the JSON names. A vanilla cooking
 * recipe would hand back a fresh item and lose the weight, the length, the card and the size.
 */
public final class CookFishRecipe {
    private CookFishRecipe() {}

    public static final class Smelting extends SmeltingRecipe {
        public Smelting(String group, CookingBookCategory category, Ingredient ingredient, ItemStack result, float experience, int cookingTime) {
            super(group, category, ingredient, result, experience, cookingTime);
        }
        @Override public boolean matches(SingleRecipeInput input, Level level) { return raw(input.item()); }
        @Override public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) { return CookedFish.cook(input.item()); }
        @Override public boolean isSpecial() { return true; }
        @Override public RecipeSerializer<?> getSerializer() { return com.riverfishing.registry.ModRecipes.COOK_FISH_SMELTING.get(); }
    }

    public static final class Smoking extends SmokingRecipe {
        public Smoking(String group, CookingBookCategory category, Ingredient ingredient, ItemStack result, float experience, int cookingTime) {
            super(group, category, ingredient, result, experience, cookingTime);
        }
        @Override public boolean matches(SingleRecipeInput input, Level level) { return raw(input.item()); }
        @Override public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) { return CookedFish.cook(input.item()); }
        @Override public boolean isSpecial() { return true; }
        @Override public RecipeSerializer<?> getSerializer() { return com.riverfishing.registry.ModRecipes.COOK_FISH_SMOKING.get(); }
    }

    static boolean raw(ItemStack s) {
        return s.getItem() instanceof FishItem && !CookedFish.isCooked(s) && FishItem.getWeightG(s) > 0;
    }
}
