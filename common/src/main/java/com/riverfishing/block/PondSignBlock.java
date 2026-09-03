package com.riverfishing.block;

import com.riverfishing.fishing.PondData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * §pond §breeding (0.9.0): the private-pond sign. Plant it within three blocks of water and that
 * water body is yours ({@link PondData}); pull it out and the water is wild again. The block itself is
 * furniture — the claim lives in the SavedData, this class only translates place/break into it and
 * tells the player why a claim was refused. A refused sign pops back off, so a standing sign always
 * means a standing claim.
 */
public class PondSignBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 16, 12);

    public PondSignBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!(level instanceof ServerLevel sl) || !(placer instanceof ServerPlayer sp)) return;
        List<Long> body = PondData.flood(sl, pos);
        String refuse = null;
        Object arg = null;
        if (body == null) {
            refuse = "message.riverfishing.no_water";
        } else if (body.size() > PondData.MAX_BLOCKS) {
            refuse = "message.riverfishing.pond_too_big";
        } else {
            BlockPos water = BlockPos.of(body.get(0));
            UUID owner = PondData.owner(sl, water);
            if (owner != null && !owner.equals(sp.getUUID())) {   // somebody else's sign already stands here
                refuse = "message.riverfishing.pond_not_yours";
                arg = PondData.ownerName(sl, water);
            }
        }
        if (refuse != null) {
            sp.displayClientMessage((arg == null ? Component.translatable(refuse) : Component.translatable(refuse, arg))
                    .withStyle(ChatFormatting.RED), true);
            sl.destroyBlock(pos, true);   // hands the sign back; no sign, no claim
            return;
        }
        PondData.get(sl).put(pos, sp, body);
        sp.displayClientMessage(Component.translatable("message.riverfishing.pond_claimed", body.size())
                .withStyle(ChatFormatting.GREEN), true);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // The message belongs to the player who pulled the sign; the release itself is in onRemove so a
        // piston or a creeper frees the water too.
        if (level instanceof ServerLevel sl && PondData.get(sl).remove(pos)) {
            player.displayClientMessage(Component.translatable("message.riverfishing.pond_released")
                    .withStyle(ChatFormatting.GRAY), true);
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void appendHoverText(ItemStack stack, @org.jetbrains.annotations.Nullable net.minecraft.world.level.BlockGetter level,
                                List<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.riverfishing.pond_sign").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl) PondData.get(sl).remove(pos);
        super.onRemove(state, level, pos, newState, moved);
    }
}
