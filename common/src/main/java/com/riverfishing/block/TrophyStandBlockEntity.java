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
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block_UPDATE);
        }
    }

    private static final int Block_UPDATE = 3;

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        // Always write Fish (even empty) so removing a trophy reliably clears it on the client.
        tag.put("Fish", fish.save(registries, new CompoundTag()));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.fish = ItemStack.parseOptional(registries, tag.getCompound("Fish"));
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
    // §multiloader: no Forge-only onDataPacket override — vanilla's client handler calls load(tag) itself.
}
