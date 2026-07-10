package com.riverfishing.item;

import com.riverfishing.RiverFishing;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Live bait from any small catch (§livebait): a single fish weighing up to 100 g placed in the
 * crafting grid becomes one live bait. Weight lives in the fish's custom data, so this is a custom recipe.
 */
public class LivebaitRecipe extends CustomRecipe {
    public static final int MAX_WEIGHT_G = 100;

    public LivebaitRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        ItemStack fish = ItemStack.EMPTY;
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (s.isEmpty()) continue;
            if (!fish.isEmpty()) return false; // more than one item in the grid
            fish = s;
        }
        if (fish.isEmpty() || !(fish.getItem() instanceof FishItem)) return false;
        int w = FishItem.getWeightG(fish);
        return w > 0 && w <= MAX_WEIGHT_G;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        var livebait = BuiltInRegistries.ITEM.get(RiverFishing.id("livebait"));
        return livebait == null ? ItemStack.EMPTY : new ItemStack(livebait);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return com.riverfishing.registry.ModRecipes.LIVEBAIT.get();
    }
}
