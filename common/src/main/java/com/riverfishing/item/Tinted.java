package com.riverfishing.item;

import net.minecraft.world.item.ItemStack;

/** An item painted by its own data (a flavour, a mix): both loaders register {@link #tint} for every one. */
public interface Tinted {
    /** ARGB for this tint layer, or -1 to leave the layer as drawn. */
    int tint(ItemStack stack, int tintIndex);
}
