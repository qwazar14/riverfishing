package com.riverfishing.item;

import com.riverfishing.registry.ModItems;
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
 * Filleting knife (§11.2). Hold it and right-click while a caught fish is in the other hand to cut the
 * fish into stackable raw fillets — count scales with the fish's weight, turning a non-stacking unique
 * catch into stackable food you can cook.
 */
public class FilletKnifeItem extends Item {
    private static final int GRAMS_PER_FILLET = 300;

    public FilletKnifeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack knife = player.getItemInHand(hand);
        InteractionHand other = (hand == InteractionHand.MAIN_HAND) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack target = player.getItemInHand(other);
        if (!(target.getItem() instanceof FishItem)) {
            return InteractionResultHolder.pass(knife);
        }
        if (!level.isClientSide) {
            // Filleting a koi is possible — but it's an ornamental collectible, so shame the angler
            // in chat by name (§koi). The knife still does its job.
            boolean koi = FishItem.isKoi(target);
            int weight = FishItem.getWeightG(target);
            int count = Math.max(1, weight / GRAMS_PER_FILLET);
            target.shrink(1);
            ItemStack fillets = new ItemStack(ModItems.RAW_FILLET.get(), count);
            if (!player.getInventory().add(fillets)) {
                player.drop(fillets, false);
            }
            knife.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
            level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                    SoundSource.PLAYERS, 0.6f, 1.3f);
            if (koi && level.getServer() != null) {
                level.getServer().getPlayerList().broadcastSystemMessage(
                        Component.literal(player.getName().getString() + ": ")
                                .append(Component.translatable("message.riverfishing.koi_filleted")),
                        false);
                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    com.riverfishing.quest.AnglerAdvancements.grant(serverPlayer, "koi_fillet"); // §challenges (funny)
                }
            }
        }
        return InteractionResultHolder.sidedSuccess(knife, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.riverfishing.knife_use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
