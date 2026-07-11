package com.riverfishing.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** A bite alarm (§6, Module 3) you attach to a rod standing on a pod by right-clicking the pod. */
public class AlarmItem extends Item {
    private final AlarmType type;

    public AlarmItem(AlarmType type, Properties properties) {
        super(properties);
        this.type = type;
    }

    public AlarmType alarmType() {
        return type;
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.riverfishing.alarm_use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
