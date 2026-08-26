package com.riverfishing.block;

import com.riverfishing.item.TackleBoxTier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * §tackle-box (0.7.0): the box, set down.
 *
 * <p>It stores the ITEM, not a copy of its contents — one object, so the box on the bank and the box in
 * your hand can never drift apart in what they hold, what they are called or what colour they are. That
 * also makes "keeps its loot when broken" free rather than a feature: breaking it drops the stack it was
 * already holding.
 *
 * <p>It faces the way it was put down, because a box with a lid and a handle that always pointed north
 * would look wrong on three sides out of four.
 */
public class TackleBoxBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 3.0, 14.0, 7.0, 13.0);

    private final TackleBoxTier tier;

    public TackleBoxBlock(TackleBoxTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(HorizontalDirectionalBlock.FACING,
                net.minecraft.core.Direction.NORTH));
    }

    public TackleBoxTier tier() {
        return tier;
    }

    @Override
    public void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HorizontalDirectionalBlock.FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(HorizontalDirectionalBlock.FACING,
                ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TackleBoxBlockEntity(pos, state);
    }

    /** Placing keeps the exact stack — contents, custom name and dye come with it. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (level.getBlockEntity(pos) instanceof TackleBoxBlockEntity be) {
            be.setBox(stack.copyWithCount(1));
        }
    }

    /** §1.20.1: one interaction method, whether or not the player is holding something. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            com.riverfishing.menu.TackleBoxMenu.open(sp, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Broken with everything still in it. Done here rather than through a loot table because the drop is
     * a specific stack with NBT, and a loot table that had to be kept in step with four tiers is four
     * more files that can silently disagree with the code.
     */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide
                && level.getBlockEntity(pos) instanceof TackleBoxBlockEntity be && !be.box().isEmpty()) {
            if (!player.isCreative()) popResource(level, pos, be.box().copy());
            // Emptied deliberately: onRemove fires straight after this on a player break and would pop
            // the very same box a SECOND time. One removal, one box.
            be.setBox(ItemStack.EMPTY);
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    /** Blown up, burnt, or pushed into lava: the box still lets go of what it was holding. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !moved
                && level.getBlockEntity(pos) instanceof TackleBoxBlockEntity be && !be.box().isEmpty()) {
            popResource(level, pos, be.box().copy());
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    /** Middle-click / silk-touch style pick gives back the box you actually put down. */
    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(pos) instanceof TackleBoxBlockEntity be && !be.box().isEmpty()
                ? be.box().copy() : super.getCloneItemStack(level, pos, state);
    }
}
