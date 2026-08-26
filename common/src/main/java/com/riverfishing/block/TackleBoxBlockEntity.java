package com.riverfishing.block;

import com.riverfishing.item.TackleBoxItem;
import com.riverfishing.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * §tackle-box (0.7.0): holds the box item itself — contents, name and colour in one object.
 *
 * <p>Synced to the client because the placed box is rendered in the box's own dye colour and its name
 * shows on the block; both come out of this stack.
 */
public class TackleBoxBlockEntity extends BlockEntity {
    private ItemStack box = ItemStack.EMPTY;

    public TackleBoxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TACKLE_BOX.get(), pos, state);
    }

    public ItemStack box() {
        return box;
    }

    public void setBox(ItemStack stack) {
        this.box = stack;
        setChanged();
    }

    /** The insert colour for the block renderer — the same answer the item's tint gives. */
    public int color() {
        return TackleBoxItem.color(box);
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        box = tag.contains("Box")
                ? ItemStack.parseOptional(registries, tag.getCompound("Box")) : ItemStack.EMPTY;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!box.isEmpty()) tag.put("Box", box.save(registries, new CompoundTag()));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
