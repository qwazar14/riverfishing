package com.riverfishing.item;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.level.Level;

/**
 * §cooking: one recipe for every species — any raw caught fish matches, and the result is THAT
 * fish, cooked ({@link CookedFish#cook}). 26.x: the furnace asks the recipe to assemble its input,
 * so a fixed instance behind a unit serializer is the whole recipe; the JSON only names the type.
 */
public final class CookFishRecipe {
    private CookFishRecipe() {}

    private static Recipe.CommonInfo common() { return new Recipe.CommonInfo(false); }
    private static AbstractCookingRecipe.CookingBookInfo book() { return new AbstractCookingRecipe.CookingBookInfo(CookingBookCategory.FOOD, ""); }

    public static final class Smelting extends SmeltingRecipe {
        public Smelting() { super(common(), book(), Ingredient.of(Items.COD), new ItemStackTemplate(Items.COOKED_COD), 0.35f, 200); }
        @Override public boolean matches(SingleRecipeInput input, Level level) { return raw(input.item()); }
        @Override public ItemStack assemble(SingleRecipeInput input) { return CookedFish.cook(input.item()); }
        @Override public boolean isSpecial() { return true; }
        @SuppressWarnings("unchecked")
        @Override public RecipeSerializer<SmeltingRecipe> getSerializer() { return (RecipeSerializer<SmeltingRecipe>) (RecipeSerializer<?>) com.riverfishing.registry.ModRecipes.COOK_FISH_SMELTING.get(); }
    }

    public static final class Smoking extends SmokingRecipe {
        public Smoking() { super(common(), book(), Ingredient.of(Items.COD), new ItemStackTemplate(Items.COOKED_COD), 0.35f, 100); }
        @Override public boolean matches(SingleRecipeInput input, Level level) { return raw(input.item()); }
        @Override public ItemStack assemble(SingleRecipeInput input) { return CookedFish.cook(input.item()); }
        @Override public boolean isSpecial() { return true; }
        @SuppressWarnings("unchecked")
        @Override public RecipeSerializer<SmokingRecipe> getSerializer() { return (RecipeSerializer<SmokingRecipe>) (RecipeSerializer<?>) com.riverfishing.registry.ModRecipes.COOK_FISH_SMOKING.get(); }
    }

    static boolean raw(ItemStack s) {
        return s.getItem() instanceof FishItem && !CookedFish.isCooked(s) && FishItem.getWeightG(s) > 0;
    }
}
