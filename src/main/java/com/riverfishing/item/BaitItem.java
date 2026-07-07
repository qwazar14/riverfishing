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

    public BaitItem(String baitId, boolean artificial, Properties properties) {
        super(properties);
        this.baitId = baitId;
        this.artificial = artificial;
    }

    public String baitId() { return baitId; }
    public boolean artificial() { return artificial; }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        String key = artificial ? "tooltip.riverfishing.bait_artificial" : "tooltip.riverfishing.bait_natural";
        tooltip.add(Component.translatable(key).withStyle(s -> s.withColor(0x80A080)));
    }
}
