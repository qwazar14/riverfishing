package com.riverfishing.item;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

/**
 * §fish-card: the tooltip's data half. Vanilla hands this to the client, which turns it into a
 * {@code ClientTooltipComponent} through the loader's factory hook — see ClientInit's callers.
 */
public record FishCardTooltip(ItemStack fish) implements TooltipComponent {}
