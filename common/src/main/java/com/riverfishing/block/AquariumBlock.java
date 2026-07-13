package com.riverfishing.block;

import com.riverfishing.item.FishItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * A 2×2 (2 wide × 2 tall × 1 deep) display aquarium (§aquarium) that mounts one caught fish. Glass
 * tank on top, wooden base with a nameplate below. The bottom-left cell is the MASTER (holds the
 * BlockEntity + fish); the other three are parts that place/break together with it.
 */
public class AquariumBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    /** false = left cell, true = right cell (along the width axis = FACING clockwise). */
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");
    /** false = wooden base (bottom), true = glass tank (top). */
    public static final BooleanProperty UPPER = BooleanProperty.create("upper");

    public static final com.mojang.serialization.MapCodec<AquariumBlock> CODEC = simpleCodec(AquariumBlock::new);

    @Override
    protected com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.block.BaseEntityBlock> codec() {
        return CODEC;
    }

    public AquariumBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH).setValue(RIGHT, false).setValue(UPPER, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, RIGHT, UPPER);
    }

    private static boolean isMaster(BlockState state) {
        return !state.getValue(RIGHT) && !state.getValue(UPPER);
    }

    /** The bottom-left (master) position, given any cell's pos/state. */
    private static BlockPos masterPos(BlockPos pos, BlockState state) {
        Direction cw = state.getValue(FACING).getClockWise();
        BlockPos p = pos;
        if (state.getValue(RIGHT)) p = p.relative(cw.getOpposite());
        if (state.getValue(UPPER)) p = p.below();
        return p;
    }

    /** All four cell positions, given the master pos and facing. */
    private static BlockPos[] cells(BlockPos master, Direction facing) {
        Direction cw = facing.getClockWise();
        return new BlockPos[]{ master, master.relative(cw), master.above(), master.above().relative(cw) };
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Level level = ctx.getLevel();
        Direction facing = ctx.getHorizontalDirection().getOpposite(); // front faces the player
        BlockPos base = ctx.getClickedPos();
        Direction cw = facing.getClockWise();
        // Need the three extra cells free (the clicked one is already known replaceable).
        BlockPos[] extra = { base.relative(cw), base.above(), base.above().relative(cw) };
        for (BlockPos p : extra) {
            if (p.getY() > level.getMaxY() || !level.getBlockState(p).canBeReplaced()) {
                return null; // not enough room — placement fails
            }
        }
        return defaultBlockState().setValue(FACING, facing).setValue(RIGHT, false).setValue(UPPER, false);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level.isClientSide()) return;
        Direction facing = state.getValue(FACING);
        Direction cw = facing.getClockWise();
        BlockState part = defaultBlockState().setValue(FACING, facing);
        level.setBlock(pos.relative(cw), part.setValue(RIGHT, true).setValue(UPPER, false), 3);
        level.setBlock(pos.above(), part.setValue(RIGHT, false).setValue(UPPER, true), 3);
        level.setBlock(pos.above().relative(cw), part.setValue(RIGHT, true).setValue(UPPER, true), 3);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return true; // the parts hold each other up; break-together handles removal
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // Break the OTHER three cells without drops so only the struck cell drops the aquarium once.
        if (!level.isClientSide() && !player.isCreative()) {
            BlockPos master = masterPos(pos, state);
            for (BlockPos p : cells(master, state.getValue(FACING))) {
                if (p.equals(pos)) continue;
                BlockState os = level.getBlockState(p);
                if (os.getBlock() == this) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 35); // 32 = suppress drops
                    level.levelEvent(player, 2001, p, Block.getId(os));    // break particles/sound
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    // §26.1: onRemove is gone — the fish pop from AquariumBlockEntity#preRemoveSideEffects, and the
    // multiblock teardown (piston/explosion — nothing may float) moved to this replacement hook.
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level,
                                               BlockPos pos, boolean moved) {
        BlockPos master = masterPos(pos, state);
        for (BlockPos p : cells(master, state.getValue(FACING))) {
            if (!p.equals(pos) && level.getBlockState(p).getBlock() == this) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 35);
            }
        }
        super.affectNeighborsAfterRemoval(state, level, pos, moved);
    }

    @Override
    protected net.minecraft.world.InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) return net.minecraft.world.InteractionResult.SUCCESS;
        BlockPos master = masterPos(pos, state);
        if (!(level.getBlockEntity(master) instanceof AquariumBlockEntity be)) return net.minecraft.world.InteractionResult.TRY_WITH_EMPTY_HAND;

        ItemStack held = player.getItemInHand(hand);
        // Add a fish (up to 3) when holding one and there's room.
        if (held.getItem() instanceof FishItem && !be.isFull()) {
            if (be.addFish(held)) {
                held.shrink(1);
                level.playSound(null, master, net.minecraft.sounds.SoundEvents.BUCKET_EMPTY_FISH,
                        net.minecraft.sounds.SoundSource.BLOCKS, 0.7f, 1.1f);
                return net.minecraft.world.InteractionResult.CONSUME;
            }
        }
        // Empty hand takes the last fish back out.
        if (held.isEmpty() && !be.isEmpty()) {
            ItemStack fish = be.removeLastFish();
            if (!fish.isEmpty() && !player.getInventory().add(fish)) player.drop(fish, false);
            level.playSound(null, master, net.minecraft.sounds.SoundEvents.BUCKET_FILL_FISH,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.7f, 1.0f);
            return net.minecraft.world.InteractionResult.CONSUME;
        }
        return net.minecraft.world.InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        // Only the master cell carries the BlockEntity (and thus the renderer).
        return isMaster(state) ? new AquariumBlockEntity(pos, state) : null;
    }
}
