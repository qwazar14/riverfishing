package com.riverfishing.item;

import com.riverfishing.RiverFishing;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Live bait from any small catch (§livebait): a single fish weighing up to 100 g placed in the
 * crafting grid becomes one live bait. Weight lives in the fish's NBT, so this is a custom recipe.
 */
public class LivebaitRecipe extends CustomRecipe {
    public static final int MAX_WEIGHT_G = 100;

    public LivebaitRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        ItemStack fish = ItemStack.EMPTY;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack s = container.getItem(i);
            if (s.isEmpty()) continue;
            if (!fish.isEmpty()) return false; // more than one item in the grid
            fish = s;
        }
        if (fish.isEmpty() || !(fish.getItem() instanceof FishItem)) return false;
        int w = FishItem.getWeightG(fish);
        return w > 0 && w <= MAX_WEIGHT_G;
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess access) {
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
