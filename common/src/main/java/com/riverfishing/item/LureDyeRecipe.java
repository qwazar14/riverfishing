package com.riverfishing.item;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * §lure-color (§8): paint an artificial predator lure with dyes, exactly like leather armour — one lure
 * plus one or more dyes in the grid yields the lure with a mixed colour (stored in {@code display.color},
 * §1.20.1). The colour then drives the lure's condition-fit in the bite engine
 * ({@link com.riverfishing.engine.LureColor}).
 */
public class LureDyeRecipe extends CustomRecipe {
    public LureDyeRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        ItemStack lure = ItemStack.EMPTY;
        boolean anyDye = false;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack s = container.getItem(i);
            if (s.isEmpty()) continue;
            if (s.getItem() instanceof BaitItem b && b.artificial()) {
                if (!lure.isEmpty()) return false; // only one lure
                lure = s;
            } else if (s.getItem() instanceof DyeItem) {
                anyDye = true;
            } else {
                return false; // anything else disqualifies
            }
        }
        return !lure.isEmpty() && anyDye;
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess access) {
        ItemStack lure = ItemStack.EMPTY;
        List<DyeItem> dyes = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack s = container.getItem(i);
            if (s.isEmpty()) continue;
            if (s.getItem() instanceof BaitItem b && b.artificial()) {
                if (!lure.isEmpty()) return ItemStack.EMPTY;
                lure = s;
            } else if (s.getItem() instanceof DyeItem d) {
                dyes.add(d);
            } else {
                return ItemStack.EMPTY;
            }
        }
        if (lure.isEmpty() || dyes.isEmpty()) return ItemStack.EMPTY;
        // Vanilla dye-mixing (same algorithm leather armour uses) → display.color on the lure copy.
        return DyeUtil.applyDyes(lure, dyes);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return com.riverfishing.registry.ModRecipes.LURE_DYE.get();
    }
}
