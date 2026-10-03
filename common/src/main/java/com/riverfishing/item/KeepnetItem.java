package com.riverfishing.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * §keepnet (0.7.0): a box with a shape to it.
 *
 * <p>Right-click to open the grid. It opens on a held keepnet and nowhere else — never from a hotbar
 * shortcut, never mid-fight — because a spatial inventory is only tolerable during a believable pause in
 * the action, and standing on the bank sorting your catch is exactly that pause.
 */
public class KeepnetItem extends Item {
    private final KeepnetTier tier;

    public KeepnetItem(KeepnetTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public KeepnetTier tier() {
        return tier;
    }

    @Override
    public void onCraftedBy(ItemStack stack, Level level, Player player) {
        carryOver(stack, player);
    }

    /**
     * §upgrade-keeps: a bigger keepnet or tackle box crafted around a smaller one is the same net, grown — it
     * keeps what was in it, its dye and its name. The upgrade recipes are plain shaped ones, so the result
     * comes out blank; this runs when it is taken, while the old one still lies in the grid (the take and the
     * shift-click both call it before the grid is used up), and copies the old one's data across.
     * ponytail: a crafter block has no player and no grid here — an upgrade crafted by one comes out empty.
     */
    static void carryOver(ItemStack made, Player player) {
        if (player == null) return;
        for (net.minecraft.world.inventory.Slot s : player.containerMenu.slots) {
            ItemStack old = s.getItem();
            if (s.container instanceof net.minecraft.world.inventory.CraftingContainer
                    && old.getItem().getClass() == made.getItem().getClass()) {   // a net from a net, a box from a box
                made.applyComponents(old.getComponentsPatch());
                return;
            }
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof net.minecraft.server.level.ServerPlayer sp) {
            com.riverfishing.menu.KeepnetMenu.open(sp, hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        KeepnetData data = KeepnetData.read(stack);
        tooltip.add(Component.translatable("tooltip.riverfishing.keepnet_size",
                tier.width(), tier.height()).withStyle(ChatFormatting.GRAY));
        if (!data.items().isEmpty()) {
            tooltip.add(Component.translatable("tooltip.riverfishing.keepnet_full",
                    data.items().size(), (int) Math.round(data.fullness() * 100))
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
    }
}
