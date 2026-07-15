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
    public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level level, List<Component> tooltip, TooltipFlag flag) {
        String key = tooltipKey != null ? tooltipKey
                : (artificial ? "tooltip.riverfishing.bait_artificial" : "tooltip.riverfishing.bait_natural");
        tooltip.add(Component.translatable(key).withStyle(s -> s.withColor(0x80A080)));

        // §lure-color: a painted lure names its colour class and the water it fishes best (§8).
        if (artificial) {
            int rgb = DyeUtil.color(stack);
            if (rgb >= 0) {
                com.riverfishing.engine.LureColor lc = com.riverfishing.engine.LureColor.fromRgb(rgb);
                tooltip.add(Component.translatable("tooltip.riverfishing.lure_color_" + lc.name().toLowerCase())
                        .withStyle(s -> s.withColor(rgb)));
            }
        }
    }
}
