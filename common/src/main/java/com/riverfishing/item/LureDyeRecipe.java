package com.riverfishing.item;

import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * §lure-color (§8): paint an artificial predator lure with dyes, exactly like leather armour — one lure
 * plus one or more dyes in the grid yields the lure with a mixed {@code DyedItemColor}. The colour then
 * drives the lure's condition-fit in the bite engine ({@link com.riverfishing.engine.LureColor}).
 */
public class LureDyeRecipe extends CustomRecipe {
    @Override
    public boolean matches(CraftingInput input, Level level) {
        ItemStack lure = ItemStack.EMPTY;
        boolean anyDye = false;
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (s.isEmpty()) continue;
            if (s.getItem() instanceof BaitItem b && b.artificial()) {
                if (!lure.isEmpty()) return false; // only one lure
                lure = s;
            } else if (s.has(net.minecraft.core.component.DataComponents.DYE)) {
                anyDye = true; // §26.1: dyes are marked by the DYE component, not the DyeItem class
            } else {
                return false; // anything else disqualifies
            }
        }
        return !lure.isEmpty() && anyDye;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack lure = ItemStack.EMPTY;
        List<net.minecraft.world.item.DyeColor> dyes = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (s.isEmpty()) continue;
            if (s.getItem() instanceof BaitItem b && b.artificial()) {
                if (!lure.isEmpty()) return ItemStack.EMPTY;
                lure = s;
            } else if (s.has(net.minecraft.core.component.DataComponents.DYE)) {
                dyes.add(s.get(net.minecraft.core.component.DataComponents.DYE));
            } else {
                return ItemStack.EMPTY;
            }
        }
        if (lure.isEmpty() || dyes.isEmpty()) return ItemStack.EMPTY;
        // Vanilla dye-mixing (same helper leather armour uses) → a DyedItemColor on the lure copy.
        return DyedItemColor.applyDyes(lure.copyWithCount(1), dyes);
    }

    @Override
    public RecipeSerializer<LureDyeRecipe> getSerializer() {
        return com.riverfishing.registry.ModRecipes.LURE_DYE.get();
    }
}
