package com.riverfishing.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;

import java.util.List;

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

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // Crouching is the "place me" gesture, so a player standing at a chest can still set the box down
        // deliberately; an ordinary right-click always opens it, which is what you want ninety-nine
        // times in a hundred.
        if (player.isCrouching()) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide && player instanceof net.minecraft.server.level.ServerPlayer sp) {
            com.riverfishing.menu.TackleBoxMenu.open(sp, hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Only place it when the player asked for that (crouch); otherwise the click opened the box. */
    @Override
    public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext ctx) {
        Player p = ctx.getPlayer();
        if (p != null && !p.isCrouching()) {
            if (!ctx.getLevel().isClientSide && p instanceof net.minecraft.server.level.ServerPlayer sp) {
                com.riverfishing.menu.TackleBoxMenu.open(sp, ctx.getHand());
            }
            return net.minecraft.world.InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
        }
        return super.useOn(ctx);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        int used = TackleBoxData.used(stack);
        tooltip.add(Component.translatable("tooltip.riverfishing.tackle_box_size", tier.slots())
                .withStyle(ChatFormatting.GRAY));
        if (used > 0) {
            tooltip.add(Component.translatable("tooltip.riverfishing.tackle_box_full", used, tier.slots())
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
        tooltip.add(Component.translatable("tooltip.riverfishing.tackle_box_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
