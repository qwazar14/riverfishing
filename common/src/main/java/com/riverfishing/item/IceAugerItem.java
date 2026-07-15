package com.riverfishing.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Ice auger / ледобур (§ice-fishing): drills a fishing hole through an ice sheet. Right-click a block of
 * ice (ice / packed / blue / frosted) that sits directly on water and it becomes an open hole (a water
 * source). Fishing a hole ringed by ice is ice fishing — the bite engine treats it as WINTER conditions,
 * so only the cold-water fish (burbot, ruffe, perch, pike, roach…) bite. Costs durability per hole.
 */
public class IceAugerItem extends Item {
    public IceAugerItem(Properties properties) {
        super(properties);
    }

    /** True for any block that forms an ice sheet you could drill through. */
    public static boolean isIce(BlockState state) {
        return state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE)
                || state.is(Blocks.BLUE_ICE) || state.is(Blocks.FROSTED_ICE);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        Player player = ctx.getPlayer();
        BlockState state = level.getBlockState(pos);
        if (!isIce(state)) {
            return InteractionResult.PASS;
        }
        // A hole only makes sense over water — otherwise you've just cracked a decorative ice block.
        boolean waterBelow = level.getFluidState(pos.below()).is(FluidTags.WATER);
        if (!waterBelow) {
            if (!level.isClientSide && player != null) {
                player.displayClientMessage(Component.translatable("message.riverfishing.auger_no_water")
                        .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.CONSUME;
        }
        if (!level.isClientSide) {
            // Turn the ice into a drilled-hole block — a proper fishing hole you work with a winter rod. A
            // pickaxe just breaks the ice, so only the auger makes a hole you can ice-fish (§ice-fishing).
            level.setBlock(pos, com.riverfishing.registry.ModBlocks.ICE_HOLE.get().defaultBlockState(), 3);
            if (level instanceof ServerLevel sl) {
                sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                        pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 30, 0.3, 0.15, 0.3, 0.12);
            }
            level.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 0.8f, 0.7f);
            level.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.5f, 1.2f);
            if (player != null) {
                ctx.getItemInHand().hurtAndBreak(1, player, e -> e.broadcastBreakEvent(ctx.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND ? net.minecraft.world.entity.EquipmentSlot.MAINHAND : net.minecraft.world.entity.EquipmentSlot.OFFHAND));
                player.getCooldowns().addCooldown(this, 15);
                player.displayClientMessage(Component.translatable("message.riverfishing.auger_hole")
                        .withStyle(ChatFormatting.AQUA), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.riverfishing.ice_auger").withStyle(s -> s.withColor(0x80A0C0)));
    }
}
