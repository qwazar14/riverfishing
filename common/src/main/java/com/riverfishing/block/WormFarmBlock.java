package com.riverfishing.block;

import com.riverfishing.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Worm farm / червятник (§bait-farm): works like a composter — feed it ANY compostable organic matter
 * (seeds, plants, food scraps; the vanilla composter list) to fill it up, then wait: the compost visibly
 * sinks as the worms eat through it, each level turning into worms. Right-click with an empty hand (or
 * anything non-compostable) to collect. Needs soil below.
 */
public class WormFarmBlock extends BaseEntityBlock {
    /** Visible compost fill 0..4 (§bait-farm): rises as you feed it, sinks as the worms eat it. */
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 4);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 10, 16);

    public static final com.mojang.serialization.MapCodec<WormFarmBlock> CODEC = simpleCodec(WormFarmBlock::new);

    @Override
    protected com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.block.BaseEntityBlock> codec() {
        return CODEC;
    }

    public WormFarmBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LEVEL, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEVEL);
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
        return new WormFarmBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.WORM_FARM.get()) return null;
        return (lvl, pos, st, be) -> ((WormFarmBlockEntity) be).serverTick(lvl, st);
    }

    @Override
    protected net.minecraft.world.InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        boolean compostable = ComposterBlock.COMPOSTABLES.containsKey(held.getItem());
        if (compostable && state.getValue(LEVEL) < 4) {
            if (!level.isClientSide()) {
                if (!player.getAbilities().instabuild) held.shrink(1);
                level.setBlock(pos, state.setValue(LEVEL, state.getValue(LEVEL) + 1), 3);
                level.playSound(null, pos, SoundEvents.COMPOSTER_FILL_SUCCESS, SoundSource.BLOCKS, 0.8f, 1.0f);
                if (level instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.COMPOSTER, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5,
                            6, 0.3, 0.1, 0.3, 0.0);
                }
            }
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        if (level.isClientSide()) return net.minecraft.world.InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof WormFarmBlockEntity be) {
            be.collect(player);
        }
        return net.minecraft.world.InteractionResult.CONSUME;
    }
}
