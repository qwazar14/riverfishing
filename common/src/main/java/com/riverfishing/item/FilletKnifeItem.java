package com.riverfishing.item;

import com.riverfishing.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Filleting knife (§11.2). Hold it and right-click while a caught fish is in the other hand to cut the
 * fish into stackable raw fillets — the count scales with the fish's weight, turning a non-stacking
 * unique catch into something you can hook, mix or cook.
 *
 * <p>§one-cutter: this is the only thing in the mod that cuts up a fish. A shapeless recipe used to turn
 * any tagged species into a flat 4 strips with no knife involved, which meant a 20 g bleak and a 400 kg
 * marlin yielded the same, and the knife's weight scaling could be skipped entirely.
 *
 * <p>§one-fillet: and it cuts ONE thing. Crouching used to produce "fish strips" at 100 g a piece and
 * standing "raw fillets" at 300 g, from the same knife and the same fish — two items whose only real
 * difference was the yield, so the crouch was a hidden lever on a number rather than a choice about
 * anything. One product, one rate, no modifier key to discover.
 */
public class FilletKnifeItem extends Item {
    /**
     * Between the old 300 (food) and 100 (bait), because one item now has to serve both: at 300 a roach
     * was worth a single piece and bait was a chore, at 100 a decent bream fed a village.
     */
    private static final int GRAMS_PER_FILLET = 200;

    public FilletKnifeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack knife = player.getItemInHand(hand);
        InteractionHand other = (hand == InteractionHand.MAIN_HAND) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack target = player.getItemInHand(other);
        if (!(target.getItem() instanceof FishItem)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            // Filleting a koi is possible — but it's an ornamental collectible, so shame the angler
            // in chat by name (§koi). The knife still does its job.
            boolean koi = FishItem.isKoi(target);
            int weight = FishItem.getWeightG(target);
            // One fish is at most one stack. Unbounded, a legendary catfish paid 500 fillets and
            // a blue marlin 1333 — a single right-click that fed a server for a week.
            int count = Mth.clamp(weight / GRAMS_PER_FILLET, 1, 64);
            target.shrink(1);
            ItemStack cut = new ItemStack(ModItems.FISH_STRIP.get(), count);
            if (!player.getInventory().add(cut)) {
                player.drop(cut, false);
            }
            knife.hurtAndBreak(1, player, hand == net.minecraft.world.InteractionHand.MAIN_HAND ? net.minecraft.world.entity.EquipmentSlot.MAINHAND : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
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
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.riverfishing.knife_use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
