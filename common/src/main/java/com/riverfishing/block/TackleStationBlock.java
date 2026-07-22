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
 * §tackle-station (0.6.0): the universal tackle bench — pick a form, set the weight, feed it a hook,
 * iron and string, and it ties the rig or lure. Crafting-table-style: no BlockEntity, the menu owns
 * the ephemeral slots and hands everything back on close.
 */
public class TackleStationBlock extends Block {
    public TackleStationBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            dev.architectury.registry.menu.MenuRegistry.openExtendedMenu(sp,
                    new dev.architectury.registry.menu.ExtendedMenuProvider() {
                        @Override
                        public Component getDisplayName() {
                            return Component.translatable("block.riverfishing.tackle_station");
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
