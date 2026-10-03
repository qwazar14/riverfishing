package com.riverfishing.block;

import com.riverfishing.menu.TackleStationMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * §tackle-station (0.6.0, round 5): the fisherman's stall IS the tackle bench — one block gives the
 * villager profession (POI) AND ties tackle for players. Materials persist in its BlockEntity and
 * drop on break.
 */
public class FishingStallBlock extends Block implements net.minecraft.world.level.block.EntityBlock {
    /**
     * §tackle-station (0.9.x): the bench is a BARREL with drawers down one side, so it has to know which
     * way it is pointing — without this the drawers face whichever way north happens to be.
     */
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING =
            net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;

    /** The model is a barrel inset from the block edges; the hitbox follows it rather than the full cube. */
    private static final net.minecraft.world.phys.shapes.VoxelShape SHAPE =
            Block.box(1.0, 0.0, 1.0, 15.0, 15.5, 15.0);

    public FishingStallBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(
            net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getShape(
            BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos,
            net.minecraft.world.phys.shapes.CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TackleStationBlockEntity(pos, state);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof TackleStationBlockEntity be) {
            net.minecraft.world.Containers.dropContents(level, pos, be.items());
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            dev.architectury.registry.menu.MenuRegistry.openExtendedMenu(sp,
                    new dev.architectury.registry.menu.ExtendedMenuProvider() {
                        @Override
                        public Component getDisplayName() {
                            return Component.translatable("block.riverfishing.fishing_stall");
                        }

                        @Nullable
                        @Override
                        public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                            return new TackleStationMenu(id, inv, pos);
                        }

                        @Override
                        public void saveExtraData(FriendlyByteBuf buf) {
                            buf.writeBlockPos(pos);
                        }
                    });
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
