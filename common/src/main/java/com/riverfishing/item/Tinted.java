package com.riverfishing.item;

import net.minecraft.world.item.ItemStack;

/** An item painted by its own data (a flavour, a mix): both loaders register {@link #tint} for every one. */
public interface Tinted {
    /** §26.x: write the layer-0 colour where the item definition's minecraft:dye tint source reads it. */
    default void stampTint(net.minecraft.world.item.ItemStack stack) {
        int argb = tint(stack, 0);
        if (argb == -1) stack.remove(net.minecraft.core.component.DataComponents.DYED_COLOR);
        else stack.set(net.minecraft.core.component.DataComponents.DYED_COLOR,
                new net.minecraft.world.item.component.DyedItemColor(argb & 0xFFFFFF));
    }

    /** ARGB for this tint layer, or -1 to leave the layer as drawn. */
    int tint(ItemStack stack, int tintIndex);
}
