package com.riverfishing.block;

import com.riverfishing.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** Holds up to {@link #MAX_FISH} mounted fish for an {@link AquariumBlock} (only the master cell has one). */
public class AquariumBlockEntity extends BlockEntity {
    public static final int MAX_FISH = 3;

    private final List<ItemStack> fishes = new ArrayList<>();

    public AquariumBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AQUARIUM.get(), pos, state);
    }

    /** The mounted fish (0..3), for the renderer and interaction. */
    public List<ItemStack> getFishes() {
        return fishes;
    }

    public boolean isFull() {
        return fishes.size() >= MAX_FISH;
    }

    public boolean isEmpty() {
        return fishes.isEmpty();
    }

    /** Add one fish if there's room. Returns true when it went in. */
    public boolean addFish(ItemStack stack) {
        if (isFull() || stack.isEmpty()) return false;
        fishes.add(stack.copyWithCount(1));
        sync();
        return true;
    }

    /** Remove and return the most-recently added fish, or EMPTY when the tank is empty. */
    public ItemStack removeLastFish() {
        if (fishes.isEmpty()) return ItemStack.EMPTY;
        ItemStack out = fishes.remove(fishes.size() - 1);
        sync();
        return out;
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (ItemStack s : fishes) list.add(s.save(registries, new CompoundTag()));
        tag.put("Fishes", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        fishes.clear();
        if (tag.contains("Fishes")) {
            ListTag list = tag.getList("Fishes", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size() && fishes.size() < MAX_FISH; i++) {
                ItemStack s = ItemStack.parseOptional(registries, list.getCompound(i));
                if (!s.isEmpty()) fishes.add(s);
            }
        } else if (tag.contains("Fish")) { // migrate the old single-fish format
            ItemStack s = ItemStack.parseOptional(registries, tag.getCompound("Fish"));
            if (!s.isEmpty()) fishes.add(s);
        }
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
    // §multiloader: no onDataPacket override — that's a Forge-only hook. Vanilla's client packet handler
    // calls load(tag) itself, so getUpdateTag()/getUpdatePacket() above are all the sync we need.
}
