package com.riverfishing.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * Reed and cattail: a two-tall plant that stands where a river bank is made of — soil like tall grass, and also
 * sand, gravel and clay — and in water ONE block deep: the foot may stand in water, the head may not. Water that
 * reaches the head breaks the plant (and takes its place), so two blocks of water drown it. They generate along
 * water and in the shallows (worldgen/configured_feature/patch_reed, patch_cattail).
 */
public class BankPlantBlock extends DoublePlantBlock implements SimpleWaterloggedBlock {
    //? if <26.3 {
    public static final MapCodec<BankPlantBlock> CODEC = simpleCodec(BankPlantBlock::new);
    //?}
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public BankPlantBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(WATERLOGGED, false));
    }

    //? if <26.3 {
    @Override
    public MapCodec<BankPlantBlock> codec() {
        return CODEC;
    }
    //?}

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(WATERLOGGED);
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return super.mayPlaceOn(state, level, pos) || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState state = super.getStateForPlacement(ctx);
        if (state == null) return null;
        BlockPos pos = ctx.getClickedPos();
        if (!ctx.getLevel().getFluidState(pos.above()).isEmpty()) return null;   // two blocks deep: the head would drown
        return state.setValue(WATERLOGGED, ctx.getLevel().getFluidState(pos).getType() == Fluids.WATER);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction dir, BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
        if (state.getValue(WATERLOGGED)) ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        BlockState out = super.updateShape(state, level, ticks, pos, dir, neighbourPos, neighbour, random);
        // a foot standing in water leaves its water behind when the plant goes
        return out.isAir() && state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false).createLegacyBlock() : out;
    }

    @Override
    public boolean canPlaceLiquid(LivingEntity entity, BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) {
        // the head gives way to a stream too: water two deep is water two deep, however it got there
        return fluid == Fluids.WATER || (fluid == Fluids.FLOWING_WATER && state.getValue(HALF) == DoubleBlockHalf.UPPER);
    }

    /** Water at the head breaks the plant and fills the space; at the foot it is taken in, as by any waterlogged block. */
    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluid) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            level.destroyBlock(pos, true);
            level.setBlock(pos, fluid.createLegacyBlock(), Block.UPDATE_ALL);
            return true;
        }
        return SimpleWaterloggedBlock.super.placeLiquid(level, pos, state, fluid);
    }
}
