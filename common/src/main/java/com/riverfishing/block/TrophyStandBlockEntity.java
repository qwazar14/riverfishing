package com.riverfishing.block;

import com.riverfishing.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/** Holds the single mounted fish for a {@link TrophyStandBlock}; synced to the client for the renderer. */
public class TrophyStandBlockEntity extends BlockEntity {
    private ItemStack fish = ItemStack.EMPTY;

    public TrophyStandBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TROPHY_STAND.get(), pos, state);
    }

    public ItemStack getFish() {
        return fish;
    }

    public void setFish(ItemStack stack) {
        this.fish = stack;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block_UPDATE);
        }
    }

    private static final int Block_UPDATE = 3;

    // §26.1: the block's onRemove hook is gone — the BE pops its own mounted fish on removal.
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null && !level.isClientSide() && !fish.isEmpty()) {
            net.minecraft.world.level.block.Block.popResource(level, pos, fish);
            fish = ItemStack.EMPTY;
        }
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput tag) {
        super.saveAdditional(tag);
        // Always write Fish (even empty) so removing a trophy reliably clears it on the client.
        tag.store("Fish", ItemStack.OPTIONAL_CODEC, fish);
    }

    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput tag) {
        super.loadAdditional(tag);
        this.fish = tag.read("Fish", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
    // §multiloader: no Forge-only onDataPacket override — vanilla's client handler calls load(tag) itself.
}
