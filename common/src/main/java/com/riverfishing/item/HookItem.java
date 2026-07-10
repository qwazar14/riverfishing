package com.riverfishing.item;

import com.riverfishing.component.ComponentSlot;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Hook (§3.7). Numbered the angling way: a bigger number is a smaller hook. */
public class HookItem extends Item implements RodComponentItem {
    private final int hookSize;

    public HookItem(int hookSize, Properties properties) {
        super(properties);
        this.hookSize = hookSize;
    }

    public int hookSize() { return hookSize; }

    @Override
    public ComponentSlot componentSlot() {
        return ComponentSlot.HOOK;
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.riverfishing.hook_size", hookSize).withStyle(s -> s.withColor(0xA0A0A0)));
        int wear = WearData.get(stack);
        if (wear > 0) {
            tooltip.add(Component.translatable("tooltip.riverfishing.hook_dull", wear)
                    .withStyle(s -> s.withColor(wear >= 70 ? 0xD05050 : 0xC0A060)));
        }
    }
}
