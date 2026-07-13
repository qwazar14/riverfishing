package com.riverfishing.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A leader for predator/catfish rigs (§2.2 note 2). Leaders differ:
 * <ul>
 *   <li><b>protection</b> 0..1 — resistance to a pike/zander biting through (1 = never).</li>
 *   <li><b>stealth</b> 0..1 — how invisible it is; higher slightly improves the bite of wary fish.</li>
 * </ul>
 */
public class LeaderItem extends Item {
    private final double protection;
    private final double stealth;

    public LeaderItem(double protection, double stealth, Properties properties) {
        super(properties);
        this.protection = protection;
        this.stealth = stealth;
    }

    public double protection() { return protection; }
    public double stealth() { return stealth; }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.riverfishing.leader_protection", Math.round(protection * 100))
                .withStyle(s -> s.withColor(0x8090C0)));
        tooltip.accept(Component.translatable("tooltip.riverfishing.leader_stealth", Math.round(stealth * 100))
                .withStyle(s -> s.withColor(0x8090C0)));
    }
}
