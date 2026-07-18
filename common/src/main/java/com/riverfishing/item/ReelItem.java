package com.riverfishing.item;

import com.riverfishing.component.ComponentSlot;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Spinning reel (§3.2). Size drives casting distance and drag ceiling. */
public class ReelItem extends Item implements RodComponentItem {
    private final int size; // 1000..7000

    public ReelItem(int size, Properties properties) {
        super(properties);
        this.size = size;
    }

    public int size() { return size; }

    /** Distance multiplier for the cast mini-game (§4): larger reels throw farther. */
    public double distanceMultiplier() {
        return 1.0 + (size - 1000) / 6000.0 * 0.9;
    }

    /**
     * Maximum drag in kg (§3.2): a weak drag against a strong fish snaps the line.
     * §sea-tackle (0.5.0): the freshwater ladder stays linear (1000→1 kg … 7000→7 kg); the saltwater
     * sizes climb steeper (+2.5 kg per 1000 above 7000, 14000→24.5 kg) — ocean drags are a different
     * machine, and without one the pelagics simply cannot be stopped.
     */
    public double maxDragKg() {
        return size <= 7000 ? size / 1000.0 : 7.0 + (size - 7000) / 1000.0 * 2.5;
    }

    @Override
    public ComponentSlot componentSlot() {
        return ComponentSlot.REEL;
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.riverfishing.reel_size", size).withStyle(s -> s.withColor(0xA0A0A0)));
        tooltip.add(Component.translatable("tooltip.riverfishing.reel_drag", String.format("%.1f", maxDragKg())).withStyle(s -> s.withColor(0xA0A0A0)));
        // §tackle-compat: the working line-diameter window this spool takes.
        tooltip.add(Component.translatable("tooltip.riverfishing.reel_line",
                String.format("%.2f", com.riverfishing.component.TackleCompat.minLineDiameter(size)),
                String.format("%.2f", com.riverfishing.component.TackleCompat.maxLineDiameter(size)))
                .withStyle(s -> s.withColor(0x88C8E6)));
    }
}
