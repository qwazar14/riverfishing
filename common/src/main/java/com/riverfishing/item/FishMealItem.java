package com.riverfishing.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * §j (0.9.0): ground small fish. Two jobs, one item.
 *
 * <p>On a crop it IS bone meal — vanilla's {@link BoneMealItem#useOn} does the growing, so every block
 * bone meal works on works here too, with no list of our own to fall behind. In the groundbait bowl it
 * is protein: the {@code fish_meal} pantry entry in {@code GroundbaitMix} is what the predators answer to.
 *
 * <p>The second tooltip line is there because the fry recipe exists on purpose: a bucket of fry grinds
 * to ONE meal, and three bleak to two. The recipe is the sad option and it says so before you craft it.
 */
public class FishMealItem extends BoneMealItem {
    public FishMealItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.riverfishing.fish_meal").withStyle(s -> s.withColor(0x7F8C99)));
        tooltip.add(Component.translatable("tooltip.riverfishing.fish_meal_fry").withStyle(s -> s.withColor(0x7F8C99)));
    }
}
