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
    public FishingStallBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TackleStationBlockEntity(pos, state);
    }

    // §26.1: onRemove is gone — the materials pop from TackleStationBlockEntity#preRemoveSideEffects.

    // useWithoutItem still exists unchanged on 26.x, so the bench keeps opening for an EMPTY HAND only
    // (a held item goes through useItemOn, which this block does not implement).
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        // §26.1: Level.isClientSide the FIELD is private now — call the isClientSide() method.
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
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
        // §26.1: sidedSuccess is gone — InteractionResult.SUCCESS is already the sided success (it swings
        // the arm on the client and does not re-run the action there), matching IceHoleBlock's port.
        return InteractionResult.SUCCESS;
    }
}
