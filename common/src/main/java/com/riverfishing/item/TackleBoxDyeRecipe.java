package com.riverfishing.item;

import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.inventory.CraftingContainer;
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
    public TackleBoxDyeRecipe(net.minecraft.resources.ResourceLocation id, CraftingBookCategory category) {
        super(id, category);   // §1.20.1: a recipe still carries its own id
    }

    @Override
    public boolean matches(CraftingContainer input, Level level) {
        return !assemble(input, null).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingContainer input, net.minecraft.core.RegistryAccess registries) {
        ItemStack box = ItemStack.EMPTY;
        List<DyeItem> dyes = new ArrayList<>();
        for (int i = 0; i < input.getContainerSize(); i++) {
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
        // §1.20.1: the same vanilla mixing, stored in display.color (see DyeUtil).
        return DyeUtil.applyDyes(box, dyes);
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
