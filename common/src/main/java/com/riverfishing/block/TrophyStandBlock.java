package com.riverfishing.block;

import com.riverfishing.item.FishItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/** A trophy stand (§15.5): mount one caught fish on it; it's rendered standing above the pedestal. */
public class TrophyStandBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1, 0, 1, 15, 3, 15),    // base
            Block.box(3, 3, 3, 13, 10, 13),   // body
            Block.box(2, 10, 2, 14, 12, 14)); // cap

    public static final com.mojang.serialization.MapCodec<TrophyStandBlock> CODEC = simpleCodec(TrophyStandBlock::new);

    @Override
    protected com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.block.BaseEntityBlock> codec() {
        return CODEC;
    }

    public TrophyStandBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        // Face the player who placed it, so the mounted fish looks back at them.
        Direction facing = ctx.getHorizontalDirection().getOpposite();
        com.riverfishing.RiverFishing.LOGGER.info("[RiverFishing] TrophyStand placed: facing={}", facing);
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TrophyStandBlockEntity(pos, state);
    }

    @Override
    protected net.minecraft.world.InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) return net.minecraft.world.InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof TrophyStandBlockEntity be)) return net.minecraft.world.InteractionResult.TRY_WITH_EMPTY_HAND;

        ItemStack held = player.getItemInHand(hand);
        if (be.getFish().isEmpty() && held.getItem() instanceof FishItem) {
            be.setFish(held.copyWithCount(1));
            held.shrink(1);
            return net.minecraft.world.InteractionResult.CONSUME;
        }
        if (!be.getFish().isEmpty() && held.isEmpty()) {
            ItemStack fish = be.getFish();
            be.setFish(ItemStack.EMPTY);
            if (!player.getInventory().add(fish)) player.drop(fish, false);
            return net.minecraft.world.InteractionResult.CONSUME;
        }
        return net.minecraft.world.InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    // §26.1: onRemove is gone — the mounted fish pops from TrophyStandBlockEntity#preRemoveSideEffects.
}
