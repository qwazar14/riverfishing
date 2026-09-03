package com.riverfishing.block;

import com.riverfishing.fishing.WaterUpgrades;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.List;

/**
 * §g §breeding (0.9.0): a water-body upgrade — aerator, snag pile, gravel bed, warm outflow, feeding
 * station. The block itself does nothing: it is a MARK on the water. {@link WaterUpgrades} records
 * where it stands (ModEvents PLACE/BREAK) and the ecosystem reads the marks around a cast.
 *
 * <p>One class for five blocks because they differ in exactly two things — the kind string the
 * ecosystem asks for, and whether the block may stand IN the water (the ones that go on the bed do,
 * the ones on the bank do not). The feeding station is the only one with a verb: groundbait in,
 * charges up, one charge a day — the charges live in the SavedData, not in a block entity, because a
 * counter is not worth a tile.
 */
public class WaterUpgradeBlock extends Block implements SimpleWaterloggedBlock {
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final String FEEDING_STATION = "feeding_station";
    /** Groundbait per jar; a full station is {@link WaterUpgrades#MAX_CHARGES}. */
    private static final int CHARGES_PER_JAR = 4;

    private final String kind;
    private final boolean inWater;
    private final String tooltipKey;

    public WaterUpgradeBlock(String id, String kind, boolean inWater, Properties properties) {
        super(properties);
        this.kind = kind;
        this.inWater = inWater;
        this.tooltipKey = "tooltip.riverfishing.upgrade." + id;
        this.registerDefaultState(this.stateDefinition.any().setValue(WATERLOGGED, Boolean.FALSE));
    }

    /** The name the ecosystem knows this upgrade by ("snags", not "snag_pile"). */
    public String kind() {
        return kind;
    }

    /** §aerator-bubbles: an aerator is a magma block's bubbles without the burn — so it shows them. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, net.minecraft.util.RandomSource random) {
        if (!"aerator".equals(kind())) return;
        boolean wet = (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)
                && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED))
                || level.getFluidState(pos.above()).is(net.minecraft.tags.FluidTags.WATER);
        if (!wet) return;
        for (int i = 0; i < 2; i++) {
            level.addParticle(net.minecraft.core.particles.ParticleTypes.BUBBLE_COLUMN_UP,
                    pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 1.0,
                    pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0.0, 0.06, 0.0);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WATERLOGGED);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        FluidState fluid = ctx.getLevel().getFluidState(ctx.getClickedPos());
        return defaultBlockState().setValue(WATERLOGGED, inWater && fluid.getType() == Fluids.WATER);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbor,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!FEEDING_STATION.equals(kind) || !(stack.getItem() instanceof com.riverfishing.item.GroundbaitItem)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) return ItemInteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel sl)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        WaterUpgrades data = WaterUpgrades.get(sl);
        if (data.charges(pos) >= WaterUpgrades.MAX_CHARGES) {
            player.displayClientMessage(Component.translatable("message.riverfishing.feeder_full"), true);
            return ItemInteractionResult.CONSUME;
        }
        data.put(pos, kind);      // a station placed before the ledger existed is still a station
        data.load(pos, CHARGES_PER_JAR);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        player.displayClientMessage(Component.translatable("message.riverfishing.feeder_loaded",
                data.charges(pos), WaterUpgrades.MAX_CHARGES), true);
        return ItemInteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(tooltipKey).withStyle(ChatFormatting.GRAY));
    }
}
