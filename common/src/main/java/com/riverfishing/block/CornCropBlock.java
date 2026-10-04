package com.riverfishing.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * §bait-crops: corn grows two blocks tall, five stages (models from 3D/corn/corn_stage0..4.bbmodel): ages 0-2 are
 * one block, 3-4 two, and it grows past 2 only into a free block above. Vanilla's PitcherCropBlock is this crop,
 * but it can't be subclassed — its private isLower/canGrowInto test for Blocks.PITCHER_CROP itself, so bonemeal
 * does nothing and growth stops at 3 — so this is its logic with {@code this} in their place. The growth chance is
 * CropBlock's, copied: CropBlock.getGrowthSpeed takes a Block on Fabric but a BlockState on NeoForge and is
 * protected, so common code can't call it. Serene Seasons hooks every block's random tick, so the sereneseasons
 * summer_crops tag still gates it. You walk through corn as through wheat (no collision, unlike a pitcher).
 */
public class CornCropBlock extends DoublePlantBlock implements BonemealableBlock {
    public static final IntegerProperty AGE = BlockStateProperties.AGE_4;
    public static final int MAX_AGE = 4;
    private static final int DOUBLE_FROM_AGE = 3;

    private static final VoxelShape[] LOWER = {
            box(6, -1, 6, 10, 3, 10), box(4, -1, 4, 12, 8, 12), box(3, -1, 3, 13, 14, 13),
            box(3, -1, 3, 13, 16, 13), box(3, -1, 3, 13, 16, 13)};
    private static final VoxelShape UPPER_GROWING = box(3, 0, 3, 13, 9, 13), UPPER_RIPE = box(3, 0, 3, 13, 16, 13);

    public CornCropBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
        super.createBlockStateDefinition(builder);
    }

    // ---- planting: a seed makes the one-block sprout; the top half comes with age 3 ----

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.LivingEntity placer,
                            net.minecraft.world.item.ItemStack stack) {
    }

    @Override
    public boolean canBeReplaced(BlockState state, BlockPlaceContext ctx) {
        return false;
    }

    @Override
    public boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(Blocks.FARMLAND);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return (!isLower(state) || sufficientLight(level, pos)) && super.canSurvive(state, level, pos);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbour, LevelAccessor level,
                                  BlockPos pos, BlockPos neighbourPos) {
        if (isDouble(state.getValue(AGE))) return super.updateShape(state, dir, neighbour, level, pos, neighbourPos);
        return state.canSurvive(level, pos) ? state : Blocks.AIR.defaultBlockState();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        int age = state.getValue(AGE);
        if (isLower(state)) return LOWER[age];
        return age == MAX_AGE ? UPPER_RIPE : UPPER_GROWING;
    }

    /** §no-seeds: corn is planted from corn — pick-block gives the bait. */
    @Override
    public net.minecraft.world.item.ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return new net.minecraft.world.item.ItemStack(com.riverfishing.registry.ModItems.CORN.get());
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (entity instanceof Ravager && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            level.destroyBlock(pos, true, entity);
        }
        super.entityInside(state, level, pos, entity);
    }

    // ---- growing ----

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return isLower(state) && state.getValue(AGE) < MAX_AGE;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt((int) (25.0F / growthSpeed(level, pos)) + 1) == 0) grow(level, state, pos, 1);
    }

    private void grow(ServerLevel level, BlockState lower, BlockPos pos, int by) {
        int age = Math.min(lower.getValue(AGE) + by, MAX_AGE);
        if (!canGrow(level, pos, lower, age)) return;
        BlockState grown = lower.setValue(AGE, age);
        level.setBlock(pos, grown, Block.UPDATE_CLIENTS);
        if (isDouble(age)) level.setBlock(pos.above(), grown.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    private boolean canGrow(LevelReader level, BlockPos pos, BlockState lower, int toAge) {
        if (lower.getValue(AGE) >= MAX_AGE || !sufficientLight(level, pos)) return false;
        if (!isDouble(toAge)) return true;
        BlockState above = level.getBlockState(pos.above());
        return above.isAir() || above.is(this);
    }

    /** CropBlock.getGrowthSpeed (vanilla): wetter farmland around is faster, the same crop crowding it slower. */
    private float growthSpeed(BlockGetter level, BlockPos pos) {
        float speed = 1.0F;
        BlockPos below = pos.below();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockState soil = level.getBlockState(below.offset(x, 0, z));
                float s = !soil.is(Blocks.FARMLAND) ? 0.0F : soil.getValue(FarmBlock.MOISTURE) > 0 ? 3.0F : 1.0F;
                speed += (x != 0 || z != 0) ? s / 4.0F : s;
            }
        }
        BlockPos n = pos.north(), s = pos.south(), w = pos.west(), e = pos.east();
        boolean rowX = level.getBlockState(w).is(this) || level.getBlockState(e).is(this);
        boolean rowZ = level.getBlockState(n).is(this) || level.getBlockState(s).is(this);
        boolean diagonal = level.getBlockState(w.north()).is(this) || level.getBlockState(e.north()).is(this)
                || level.getBlockState(e.south()).is(this) || level.getBlockState(w.south()).is(this);
        return (rowX && rowZ) || diagonal ? speed / 2.0F : speed;
    }

    // ---- bonemeal: one stage a use, from either half ----

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, boolean isClient) {
        BlockPos lowerPos = lowerPos(level, pos, state);
        if (lowerPos == null) return false;
        BlockState lower = level.getBlockState(lowerPos);
        return canGrow(level, lowerPos, lower, lower.getValue(AGE) + 1);
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        BlockPos lowerPos = lowerPos(level, pos, state);
        if (lowerPos != null) grow(level, level.getBlockState(lowerPos), lowerPos, 1);
    }

    private BlockPos lowerPos(LevelReader level, BlockPos pos, BlockState state) {
        if (isLower(state)) return pos;
        BlockState below = level.getBlockState(pos.below());
        return below.is(this) && isLower(below) ? pos.below() : null;
    }

    private static boolean isLower(BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    private static boolean isDouble(int age) {
        return age >= DOUBLE_FROM_AGE;
    }

    private static boolean sufficientLight(LevelReader level, BlockPos pos) {
        return level.getRawBrightness(pos, 0) >= 8;   // CropBlock.hasSufficientLight
    }
}
