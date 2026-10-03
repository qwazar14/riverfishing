package com.riverfishing.item;

import com.riverfishing.fish.Flavour;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * §boilies: a bottle of flavouring — sugar and one ingredient in a glass bottle. Stirred into boilie paste it
 * flavours the boilies; carried onto a stack of boilies and right-clicked, it is a DIP: the whole stack soaks
 * it up, and the smell carries further for the next few casts.
 */
public class FlavourItem extends Item implements Tinted {
    public static final String TAG = "Flavour";

    public FlavourItem(Properties properties) {
        super(properties);
    }

    public static Flavour of(ItemStack stack) {
        return Flavour.of(StackNbt.get(stack).getStringOr(TAG, ""));
    }

    public static ItemStack make(Flavour f) {
        ItemStack s = new ItemStack(com.riverfishing.registry.ModItems.FLAVOUR.get());
        StackNbt.mutate(s, tag -> tag.putString(TAG, f.id()));
        ((Tinted) s.getItem()).stampTint(s);
        return s;
    }

    @Override
    public Component getName(ItemStack stack) {
        Flavour f = of(stack);
        return f == null ? super.getName(stack)
                : Component.translatable("item.riverfishing.flavour_named", Component.translatable("flavour.riverfishing." + f.id()));
    }

    @Override
    public int tint(ItemStack stack, int tintIndex) {
        Flavour f = of(stack);
        return tintIndex == 0 && f != null ? 0xFF000000 | f.rgb : -1;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.riverfishing.flavour_use").withStyle(ChatFormatting.GRAY));
    }

    /** §boilies: carried onto a stack of boilies and right-clicked — the dip. */
    @Override
    public boolean overrideStackedOnOther(ItemStack bottle, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY) return false;
        ItemStack target = slot.getItem();
        Flavour f = of(bottle);
        if (f == null || !(target.getItem() instanceof BoilieItem)) return false;
        BoilieItem.dip(target, f);
        slot.setChanged();
        bottle.shrink(1);
        ItemStack empty = new ItemStack(Items.GLASS_BOTTLE);
        if (!player.getInventory().add(empty)) com.riverfishing.compat.Mc.drop(player, empty, false);
        player.playSound(SoundEvents.BOTTLE_EMPTY, 0.8f, 1.1f);
        return true;
    }
}
