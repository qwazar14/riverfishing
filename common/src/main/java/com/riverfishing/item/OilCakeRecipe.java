package com.riverfishing.item;

import com.riverfishing.RiverFishing;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Oil-cake groundbait (§groundbait): a sunflower pressed by a piston yields cake, and the PISTON
 * stays in the grid (it's the press, not an ingredient). Custom so the piston can be a "tool".
 */
public class OilCakeRecipe extends CustomRecipe {
    private static final int RESULT_COUNT = 6; // §balance: doubled (was 3) — groundbait burns fast (1/cast)

    public OilCakeRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        boolean sunflower = false, piston = false;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack s = container.getItem(i);
            if (s.isEmpty()) continue;
            if (s.is(Items.SUNFLOWER) && !sunflower) sunflower = true;
            else if (s.is(Items.PISTON) && !piston) piston = true;
            else return false; // anything else, or a duplicate, disqualifies
        }
        return sunflower && piston;
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess access) {
        var cake = BuiltInRegistries.ITEM.get(RiverFishing.id("groundbait_cake"));
        return cake == null ? ItemStack.EMPTY : new ItemStack(cake, RESULT_COUNT);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer container) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < remaining.size(); i++) {
            ItemStack s = container.getItem(i);
            if (s.is(Items.PISTON)) {
                remaining.set(i, new ItemStack(Items.PISTON)); // the press is not consumed
            } else if (s.getItem().hasCraftingRemainingItem()) {
                remaining.set(i, new ItemStack(s.getItem().getCraftingRemainingItem()));
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
