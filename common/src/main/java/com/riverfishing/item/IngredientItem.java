package com.riverfishing.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * An item that goes IN something rather than being used itself, and says so.
 *
 * <p>Exists because a plain {@link Item} has no description and this mod has no generic tooltip hook:
 * groundbait soil shipped a {@code tooltip.riverfishing.groundbait_soil} line in three languages that
 * nothing ever drew, and the crafted groundbait base was about to be the second. Both are items whose
 * point is invisible — one feeds nothing, the other cannot be thrown at all — so the line is the
 * difference between a considered ingredient and a mystery.
 *
 * <p>The key is handed in rather than built from the item's id. Deriving it would render a raw lang key
 * at the player the first time somebody added an ingredient and forgot the translation; this way the
 * omission is a compile-time argument you cannot leave out.
 */
public class IngredientItem extends Item {

    private final String tooltipKey;

    public IngredientItem(String tooltipKey, Properties properties) {
        super(properties);
        this.tooltipKey = tooltipKey;
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
                                net.minecraft.world.item.component.TooltipDisplay display,
                                java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable(tooltipKey).withStyle(s -> s.withColor(0x7F8C99)));
    }
}
