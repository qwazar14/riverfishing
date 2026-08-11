package com.riverfishing.item;

import com.riverfishing.network.CullListPacket;
import com.riverfishing.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
 * <p>Right-click water → every species, laid out by family, each marked with where it stands in this
 * water → pick one → confirm. Nothing happens on the first click, because a mis-click that empties a
 * lake is not a mistake anyone should be able to make.
 *
 * <p>§stock-tool (0.8.0): it PUTS FISH IN as well. The same click on a species that is not here settles
 * it, and that is also the only way back for one that was culled — a culled fish scores zero on the
 * environment, so it fell out of the old "what lives here" list and the undo the screen promised could
 * never actually be reached. What the water cannot hold is greyed out rather than hidden: a river being
 * unable to keep a marlin is information, and an empty list would have looked like a broken tool.
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
            // Water with nothing living in it used to be an early exit. It is now the case the tool is
            // most useful in, so it opens the same screen as anywhere else.
            ModNetwork.toPlayer(sp, CullListPacket.of(sl, water));
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
