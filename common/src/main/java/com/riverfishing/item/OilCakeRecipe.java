package com.riverfishing.item;

import com.riverfishing.RiverFishing;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Oil-cake groundbait (§groundbait): a sunflower pressed by a piston yields cake, and the PISTON
 * stays in the grid (it's the press, not an ingredient). Custom so the piston can be a "tool".
 * §data-components (1.21): {@code CraftingContainer}→{@code CraftingInput}; recipe id is external now.
 */
public class OilCakeRecipe extends CustomRecipe {
    private static final int RESULT_COUNT = 6; // §balance: doubled (was 3) — groundbait burns fast (1/cast)

    public OilCakeRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        boolean sunflower = false, piston = false;
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (s.isEmpty()) continue;
            if (s.is(Items.SUNFLOWER) && !sunflower) sunflower = true;
            else if (s.is(Items.PISTON) && !piston) piston = true;
            else return false; // anything else, or a duplicate, disqualifies
        }
        return sunflower && piston;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        var cake = BuiltInRegistries.ITEM.get(RiverFishing.id("groundbait_cake"));
        return cake == null ? ItemStack.EMPTY : new ItemStack(cake, RESULT_COUNT);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int i = 0; i < remaining.size(); i++) {
            if (input.getItem(i).is(Items.PISTON)) {
                remaining.set(i, new ItemStack(Items.PISTON)); // the press is not consumed
            }
        }
        return remaining;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return com.riverfishing.registry.ModRecipes.OIL_CAKE.get();
    }
}
