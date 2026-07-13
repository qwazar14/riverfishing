package com.riverfishing.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * Wear (0–100%) stored on a line or hook stack (§3.8). Worn line breaks sooner; a blunt hook misses
 * the strike more often. Stored as a single NBT int so it travels with the component wherever it sits.
 */
public final class WearData {
    public static final String TAG = "Wear";

    private WearData() {}

    public static int get(ItemStack stack) {
        return Mth.clamp(StackNbt.get(stack).getIntOr(TAG, 0), 0, 100);
    }

    public static void add(ItemStack stack, int amount) {
        set(stack, get(stack) + amount);
    }

    public static void set(ItemStack stack, int wear) {
        StackNbt.mutate(stack, tag -> tag.putInt(TAG, Mth.clamp(wear, 0, 100)));
    }

    /** Breaking-strain multiplier: at 100% wear a line keeps ~45% of its strain. */
    public static double lineStrainMultiplier(int wear) {
        return 1.0 - 0.55 * (wear / 100.0);
    }

    /** Chance a blunt hook fails to set on the strike: up to 50% at full bluntness. */
    public static double hookEmptySetChance(int wear) {
        return (wear / 100.0) * 0.5;
    }
}
