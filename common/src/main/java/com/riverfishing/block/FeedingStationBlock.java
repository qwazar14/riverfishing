package com.riverfishing.block;

import com.riverfishing.fishing.WaterUpgrades;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * §feeder-fill: the feeding station — the one water upgrade you can read from the bank.
 *
 * <p>Its groundbait lives in {@link WaterUpgrades}, a SavedData keyed by position: a counter and a day
 * stamp, no block entity. That is still the right place for it, but it meant the block showed nothing:
 * a station brimming with bait and one that ran dry a week ago were the same six texels of plank. So
 * the count is MIRRORED here, onto a state the model can switch on.
 *
 * <p>{@link #FILL} is deliberately coarse — four steps out of {@link WaterUpgrades#MAX_CHARGES} —
 * because a player reads a silhouette across the water rather than a number, and four steps keep the
 * blockstate file to four lines.
 *
 * <p>Why a class of its own instead of a flag on {@link WaterUpgradeBlock}: that class is five blocks,
 * and a property added there lands on all five — every one of their blockstate files would have to
 * enumerate a value that means nothing to them. It cannot be branched on inside
 * createBlockStateDefinition either, because Block's constructor calls that before a subclass has
 * assigned its kind.
 */
public class FeedingStationBlock extends WaterUpgradeBlock {
    /** How full it LOOKS: 0 empty, 1 nearly out, 2 half (one jar), 3 brimming. */
    public static final IntegerProperty FILL = IntegerProperty.create("fill", 0, 3);

    public FeedingStationBlock(String id, String kind, boolean inWater, Properties properties) {
        super(id, kind, inWater, properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(WATERLOGGED, Boolean.FALSE).setValue(FILL, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FILL);
    }

    /** Charges to the four steps the model draws: 0, 1-3, 4-6, 7-8. */
    public static int fill(int charges) {
        return Math.min(3, (charges + 2) / 3);
    }

    /** Put the ledger's count on the block. Harmless on a state that has no FILL — /setblock leaves those. */
    public static void sync(ServerLevel level, BlockPos pos, int charges) {
        BlockState state = level.getBlockState(pos);
        int step = fill(charges);
        if (!state.hasProperty(FILL) || state.getValue(FILL) == step) return;
        level.setBlock(pos, state.setValue(FILL, step), 3);
    }

    /**
     * The other half of keeping it honest. Loading is easy — the interaction has a level in its hand —
     * but the decay is settled lazily inside a SavedData with no level to tell, so nothing pushes the
     * drop out to the world. Rather than give the SavedData a way to reach back, the block asks. A
     * random tick finds a given block about once a minute, only in chunks a player is near, and costs
     * one HashMap lookup; the charge itself moves once a world day.
     *
     * <p>ponytail: so a station can read one step stale for up to a minute after its chunk loads. Give
     * it a block entity, or a scheduled tick re-armed on load, if that ever matters.
     */
    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        sync(level, pos, WaterUpgrades.get(level).settle(pos));
    }
}
