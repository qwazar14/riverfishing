package com.riverfishing.item;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * §tackle-box (0.7.0): paint the inserts, exactly like leather armour and like the lures already do.
 *
 * <p>Colour is navigation here, not decoration — the request was for four boxes you can tell apart at a
 * glance — so it had to be as cheap as one dye and as reversible as any dyed thing in the game.
 *
 * <p>The box keeps everything it was carrying through the craft. A recolour that emptied your tackle
 * would be a trap, and {@code copyWithCount} carries the whole component map including the contents and
 * the name.
 */
public class TackleBoxDyeRecipe extends CustomRecipe {
    public TackleBoxDyeRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return !assemble(input, null).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack box = ItemStack.EMPTY;
        List<DyeItem> dyes = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (s.isEmpty()) continue;
            if (s.getItem() instanceof TackleBoxItem) {
                if (!box.isEmpty()) return ItemStack.EMPTY;   // only one box
                box = s;
            } else if (s.getItem() instanceof DyeItem d) {
                dyes.add(d);
            } else {
                return ItemStack.EMPTY;
            }
        }
        if (box.isEmpty() || dyes.isEmpty()) return ItemStack.EMPTY;
        // applyDyes RETURNS the dyed stack rather than colouring in place — the first cut threw the
        // result away and handed back the original, so the craft "worked" and changed nothing.
        return DyedItemColor.applyDyes(box.copyWithCount(1), dyes);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return com.riverfishing.registry.ModRecipes.TACKLE_BOX_DYE.get();
    }
}
