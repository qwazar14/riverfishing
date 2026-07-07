package com.riverfishing.block;

import com.riverfishing.component.RodType;
import com.riverfishing.fishing.FishingManager;
import com.riverfishing.item.RodData;
import com.riverfishing.item.RodItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A drilled ice hole (§ice-fishing): the {@link com.riverfishing.item.IceAugerItem} makes one from an ice
 * sheet over water. Right-clicking it with an ASSEMBLED WINTER rod starts vertical ice fishing here — the
 * first click drops the mormyshka in, and each further click jigs it (the rhythm mini-game) / sets the
 * hook. No other rod fishes a hole; you fish it by working the hole, not by casting.
 *
 * <p>Extends the vanilla {@link IceBlock} so it PHYSICALLY behaves exactly like ice: slippery (friction
 * 0.98 via copied properties), melts back to water in bright light, and breaking it leaves water, never
 * a dry gap in the lake.
 */
public class IceHoleBlock extends IceBlock {
    public IceHoleBlock(Properties properties) {
        super(properties);
    }

    /**
     * §ice-refreeze: the hole slowly skins over — on average ~5 minutes it turns back to ICE and must be
     * re-drilled. It never freezes while an angler stands right at it (≤4 blocks), and only in biomes cold
     * enough to freeze water. The super call keeps the vanilla melt-in-bright-light behaviour.
     */
    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.randomTick(state, level, pos, random);
        if (!level.getBlockState(pos).is(this)) return; // melted away this tick
        if (random.nextFloat() < 0.25f
                && !level.getBiome(pos).value().warmEnoughToRain(pos)
                && !level.hasNearbyAlivePlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4.0)) {
            level.setBlockAndUpdate(pos, Blocks.ICE.defaultBlockState());
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        boolean winterRod = held.getItem() instanceof RodItem rod && rod.rodType() == RodType.WINTER;
        if (!winterRod) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.riverfishing.need_winter_rod")
                        .withStyle(ChatFormatting.YELLOW), true);
            }
            return InteractionResult.PASS;
        }
        if (!RodData.isAssembled(held)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.riverfishing.not_assembled")
                        .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.CONSUME;
        }
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            if (FishingManager.hasSession(sp)) {
                FishingManager.handleRodUse(sp, hand); // already fishing this hole → jig / strike
            } else {
                FishingManager.startIceFishing(sp, pos, hand);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
