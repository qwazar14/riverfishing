package com.riverfishing.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Whetstone / точильный брусок (§3.8). Hold it and right-click with a hook in the other hand to
 * sharpen it back to 0% wear. (Pull a hook out of a rig's GUI to sharpen it, then put it back.)
 */
public class WhetstoneItem extends Item {
    public WhetstoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stone = player.getItemInHand(hand);
        InteractionHand other = (hand == InteractionHand.MAIN_HAND) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack target = player.getItemInHand(other);
        if (!(target.getItem() instanceof HookItem) || WearData.get(target) <= 0) {
            return InteractionResultHolder.pass(stone);
        }
        if (!level.isClientSide) {
            WearData.set(target, 0);
            stone.hurtAndBreak(1, player, e -> e.broadcastBreakEvent(hand == net.minecraft.world.InteractionHand.MAIN_HAND ? net.minecraft.world.entity.EquipmentSlot.MAINHAND : net.minecraft.world.entity.EquipmentSlot.OFFHAND));
            level.playSound(null, player.blockPosition(), SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 0.6f, 1.2f);
        }
        return InteractionResultHolder.sidedSuccess(stone, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.riverfishing.whetstone_use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
