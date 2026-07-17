package com.riverfishing.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Bait / наживка (§3.6). The baitId matches the keys in a fish profile's "bait" map.
 * Artificial baits (lures) only interest predators.
 */
public class BaitItem extends Item {
    private final String baitId;
    private final boolean artificial;
    @Nullable private final String tooltipKey;

    public BaitItem(String baitId, boolean artificial, Properties properties) {
        this(baitId, artificial, null, properties);
    }

    /** {@code tooltipKey} overrides the generic natural/artificial line — e.g. the ice jig's own descriptor. */
    public BaitItem(String baitId, boolean artificial, @Nullable String tooltipKey, Properties properties) {
        super(properties);
        this.baitId = baitId;
        this.artificial = artificial;
        this.tooltipKey = tooltipKey;
    }

    public String baitId() { return baitId; }
    public boolean artificial() { return artificial; }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        String key = tooltipKey != null ? tooltipKey
                : (artificial ? "tooltip.riverfishing.bait_artificial" : "tooltip.riverfishing.bait_natural");
        tooltip.accept(Component.translatable(key).withStyle(s -> s.withColor(0x80A080)));

        // §livebait-2 (0.4.0): a weighed live baitfish names its weight — it drives the predator's size.
        if ("livebait".equals(baitId)) {
            int bw = StackNbt.get(stack).getInt(FishItem.TAG_BAIT_WEIGHT);
            if (bw > 0) {
                tooltip.add(Component.translatable("tooltip.riverfishing.livebait_weight", FishItem.weightText(bw))
                        .withStyle(s -> s.withColor(0x88C8E6)));
            }
        }

        // §lure-color: a painted lure names its colour class and the water it fishes best (§8).
        if (artificial) {
            net.minecraft.world.item.component.DyedItemColor dc =
                    stack.get(net.minecraft.core.component.DataComponents.DYED_COLOR);
            if (dc != null) {
                com.riverfishing.engine.LureColor lc = com.riverfishing.engine.LureColor.fromRgb(dc.rgb());
                tooltip.accept(Component.translatable("tooltip.riverfishing.lure_color_" + lc.name().toLowerCase())
                        .withStyle(s -> s.withColor(dc.rgb())));
            }
        }
    }
}
