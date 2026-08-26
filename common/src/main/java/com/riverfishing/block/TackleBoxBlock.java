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
    public static final com.mojang.serialization.MapCodec<TackleBoxBlock> CODEC =
            simpleCodec(p -> new TackleBoxBlock(TackleBoxTier.SMALL, p));

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
    protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
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

    // §26.1: Level.isClientSide the FIELD is private now — call the isClientSide() method. And
    // sidedSuccess is gone — InteractionResult.SUCCESS is already the sided success, as in FishingStallBlock.
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            com.riverfishing.menu.TackleBoxMenu.open(sp, pos);
        }
        return InteractionResult.SUCCESS;
    }

    // §26.1: ItemInteractionResult is gone — useItemOn returns a plain InteractionResult.
    @Override
    protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level,
                                          BlockPos pos, Player player, InteractionHand hand,
                                          BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            com.riverfishing.menu.TackleBoxMenu.open(sp, pos);
        }
        return InteractionResult.SUCCESS;
    }

    // §26.x: the box drops through TackleBoxBlockEntity#preRemoveSideEffects, which fires on EVERY
    // removal — broken, blown up, burnt or pushed. The block used to pop it here as well, and on 1.21.1
    // that double-popped a player break (playerWillDestroy AND onRemove both fired); one owner now.

    /** Middle-click / silk-touch style pick gives back the box you actually put down. */
    // §26.1: getCloneItemStack took an extra includeData flag and is protected on BlockBehaviour.
    @Override
    protected ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level, BlockPos pos,
                                          BlockState state, boolean includeData) {
        return level.getBlockEntity(pos) instanceof TackleBoxBlockEntity be && !be.box().isEmpty()
                ? be.box().copy() : super.getCloneItemStack(level, pos, state, includeData);
    }
}
