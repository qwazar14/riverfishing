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

    /**
     * §26.x: {@code Block#onRemove} is gone, so EVERY way a block can leave the world — broken, blown up,
     * burnt, pushed — arrives here. The box owns its own contents on the way out, the way the trophy
     * stand owns its fish, which is also why the block no longer pops anything itself: two owners meant
     * a player break dropped the box twice.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null && !level.isClientSide() && !box.isEmpty()) {
            net.minecraft.world.level.block.Block.popResource(level, pos, box.copy());
            box = ItemStack.EMPTY;
        }
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput tag) {
        super.loadAdditional(tag);
        box = tag.read("Box", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
    }

    @Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput tag) {
        super.saveAdditional(tag);
        if (!box.isEmpty()) tag.store("Box", ItemStack.OPTIONAL_CODEC, box);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
