package com.riverfishing.item;

import com.riverfishing.fishing.FishingManager;
import com.riverfishing.network.CullListPacket;
import com.riverfishing.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * §cull (0.7.0): the electrofisher — a world-editing tool, not a fishing one.
 *
 * <p>Asked for by vptareo-aao: a way to take a nuisance species out of a particular water so it stops
 * getting in the way. Real electrofishing is exactly this job — a survey crew stuns a stretch of river and
 * removes what does not belong — so the name is not a joke, and it is the one piece of tackle in this mod
 * that is not tackle.
 *
 * <p><b>Creative only, deliberately.</b> The request said "только с читами в мире" and that is the right
 * gate: this permanently changes what a water can hold, and there is no survival cost that would make
 * that a fair trade. Craftable by nobody; it exists in the creative menu and refuses to fire in survival.
 *
 * <p>Right-click water → the list of what actually lives there → pick one → confirm. Nothing happens on
 * the first click, because a mis-click that empties a lake is not a mistake anyone should be able to make.
 */
public class ElectroRodItem extends Item {
    public ElectroRodItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return probe(level, player) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player p = ctx.getPlayer();
        if (p == null) return InteractionResult.PASS;
        return probe(ctx.getLevel(), p) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    private boolean probe(Level level, Player player) {
        BlockPos water = WaterProbeItem.findWater(level, player);
        if (!level.isClientSide() && player instanceof ServerPlayer sp && level instanceof ServerLevel sl) {
            if (!sp.isCreative()) {
                sp.sendOverlayMessage(Component.translatable("message.riverfishing.cull_creative_only")
                        .withStyle(ChatFormatting.RED));
                return true;   // consumed: it did something, it said no
            }
            if (water == null) {
                sp.sendOverlayMessage(Component.translatable("message.riverfishing.no_water")
                        .withStyle(ChatFormatting.RED));
                return false;
            }
            List<Identifier> here = FishingManager.speciesHere(sl, water);
            if (here.isEmpty()) {
                sp.sendOverlayMessage(Component.translatable("message.riverfishing.cull_empty")
                        .withStyle(ChatFormatting.YELLOW));
                return true;
            }
            ModNetwork.toPlayer(sp, CullListPacket.of(sl, water, here));
        }
        return water != null || !level.isClientSide();
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
                                net.minecraft.world.item.component.TooltipDisplay display,
                                java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.riverfishing.electro_rod")
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.riverfishing.electro_rod_creative")
                .withStyle(ChatFormatting.RED));
    }
}
