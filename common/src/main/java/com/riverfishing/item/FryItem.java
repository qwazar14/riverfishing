package com.riverfishing.item;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * §breeding (0.9.0): a bucketful of fry hatched from roe (or netted with the fry net). NBT: the same
 * {@code Species} / {@code Genome} / {@code Count} keys as {@link RoeItem} — fry are roe that lived,
 * so the readers are shared. The item does not stack; {@code Count} is how many fry it holds.
 */
public class FryItem extends Item {
    public FryItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static ItemStack of(ResourceLocation species, String genome, int count) {
        ItemStack s = new ItemStack(com.riverfishing.registry.ModItems.FRY.get());
        StackNbt.mutate(s, t -> {
            t.putString(RoeItem.TAG_SPECIES, species.toString());
            t.putString(RoeItem.TAG_GENOME, genome);
            t.putInt(RoeItem.TAG_COUNT, count);
        });
        return s;
    }

    public static ResourceLocation species(ItemStack s) {
        return RoeItem.species(s);
    }

    public static String genome(ItemStack s) {
        return RoeItem.genome(s);
    }

    public static int count(ItemStack s) {
        return RoeItem.count(s);
    }

    public static void setCount(ItemStack s, int n) {
        StackNbt.mutate(s, t -> t.putInt(RoeItem.TAG_COUNT, n));
    }

    @Override
    public Component getName(ItemStack stack) {
        ResourceLocation sp = species(stack);
        return sp == null ? Component.translatable("item.riverfishing.fry.generic")
                : Component.translatable("item.riverfishing.fry", RoeItem.speciesName(sp));
    }

    @Override
    public void appendHoverText(ItemStack stack, @org.jetbrains.annotations.Nullable net.minecraft.world.level.Level level, List<Component> tooltip, TooltipFlag flag) {
        RoeItem.brood(stack, "tooltip.riverfishing.fry_count", tooltip);
    }
}
