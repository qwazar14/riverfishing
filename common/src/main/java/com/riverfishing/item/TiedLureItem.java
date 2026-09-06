package com.riverfishing.item;

import com.riverfishing.tackle.TiedDesign;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * §tying: a lure tied at the vise. It IS a mormyshka to every rig and rule that asks — the winter rod
 * takes it, it is never consumed, it carries its own hook — and what the drawing adds is read off the
 * canvas by {@link TiedDesign} where the rig is priced ({@code RigData.lureColorRgb}, {@code tied}).
 */
public class TiedLureItem extends BaitItem {
    public TiedLureItem(Properties properties) {
        super("mormyshka", true, "tooltip.riverfishing.tied_lure", properties.stacksTo(1));
    }

    @Override
    public Component getName(ItemStack stack) {
        TiedDesign.Analysis a = TiedDesign.analyse(stack);
        return Component.translatable("tied.riverfishing." + a.template().key);
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
                                net.minecraft.world.item.component.TooltipDisplay display,
                                java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        TiedDesign.Analysis a = TiedDesign.analyse(stack);
        tooltip.accept(Component.translatable("tooltip.riverfishing.tied_size", a.sizeMm(),
                String.format(java.util.Locale.ROOT, "%.1f", a.weightG())).withStyle(ChatFormatting.GRAY));
        int hook = TiedDesign.hookSize(stack);
        if (hook > 0) tooltip.accept(Component.translatable("tooltip.riverfishing.tied_hook", hook).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.riverfishing.lure_color_" + a.lureColor().name().toLowerCase(java.util.Locale.ROOT))
                .withStyle(ChatFormatting.DARK_AQUA));
        if (a.eyes()) tooltip.accept(Component.translatable("tooltip.riverfishing.tied_eyes").withStyle(ChatFormatting.GRAY));
        if (a.flash() > 0.15) tooltip.accept(Component.translatable("tooltip.riverfishing.tied_flash").withStyle(ChatFormatting.GRAY));
        if (a.action() > 0.2) tooltip.accept(Component.translatable("tooltip.riverfishing.tied_action").withStyle(ChatFormatting.GRAY));
        String maker = StackNbt.get(stack).getStringOr(TiedDesign.TAG_MAKER, "");
        if (!maker.isEmpty()) tooltip.accept(Component.translatable("tooltip.riverfishing.tied_by", maker).withStyle(ChatFormatting.DARK_GRAY));
    }
}
