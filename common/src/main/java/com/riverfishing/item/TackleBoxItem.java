package com.riverfishing.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;

/**
 * §tackle-box (0.7.0): somewhere to put the tackle.
 *
 * <p>Asked for plainly — "неудобно все снасти в инвентаре носить" — and it is a fair complaint: this mod
 * ships seventeen line diameters, six hook sizes, four rig types and dozens of baits, and until now they
 * all lived loose in the same nine rows as your food and your blocks.
 *
 * <p>It is a {@link net.minecraft.world.item.BlockItem} so it can also be set down: the placed block holds
 * this exact stack, so its contents, its name and its colour are the same object whether it is in your
 * hand or on the bank. Right-click in the air to open it; right-click a surface to put it down.
 */
public class TackleBoxItem extends net.minecraft.world.item.BlockItem {
    private final TackleBoxTier tier;

    public TackleBoxItem(TackleBoxTier tier, net.minecraft.world.level.block.Block block, Properties properties) {
        super(block, properties);
        this.tier = tier;
    }

    public TackleBoxTier tier() {
        return tier;
    }

    /** The insert colour, or the default off-white the sprite is drawn in. */
    public static int color(ItemStack stack) {
        DyedItemColor dc = stack.get(net.minecraft.core.component.DataComponents.DYED_COLOR);
        return dc != null ? dc.rgb() : 0xE8E6DF;
    }

    /**
     * Crouch to open, plain click to place — the way round every other block in the game already works,
     * so the box does not need its own rule in the player's head. Clicking at the sky with nothing to
     * place against still opens it, because there is nothing else that click could mean.
     */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player instanceof net.minecraft.server.level.ServerPlayer sp) {
            com.riverfishing.menu.TackleBoxMenu.open(sp, hand);
        }
        // §26.1: sidedSuccess is gone — InteractionResult.SUCCESS is already the sided success.
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext ctx) {
        Player p = ctx.getPlayer();
        if (p != null && p.isCrouching()) {
            if (!ctx.getLevel().isClientSide() && p instanceof net.minecraft.server.level.ServerPlayer sp) {
                com.riverfishing.menu.TackleBoxMenu.open(sp, ctx.getHand());
            }
            return InteractionResult.SUCCESS;
        }
        return super.useOn(ctx);
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        int used = TackleBoxData.used(stack);
        tooltip.accept(Component.translatable("tooltip.riverfishing.tackle_box_size", tier.slots())
                .withStyle(ChatFormatting.GRAY));
        if (used > 0) {
            tooltip.accept(Component.translatable("tooltip.riverfishing.tackle_box_full", used, tier.slots())
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
        tooltip.accept(Component.translatable("tooltip.riverfishing.tackle_box_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
