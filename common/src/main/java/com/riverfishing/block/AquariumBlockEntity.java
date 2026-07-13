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

    // §26.1: the block's onRemove hook is gone — the BE now pops its own contents on removal.
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null && !level.isClientSide()) {
            for (ItemStack f : fishes) {
                net.minecraft.world.level.block.Block.popResource(level, pos, f);
            }
            fishes.clear();
        }
        super.preRemoveSideEffects(pos, state);
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
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput tag) {
        super.saveAdditional(tag);
        tag.store("Fishes", ItemStack.OPTIONAL_CODEC.listOf(), java.util.List.copyOf(fishes));
    }

    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput tag) {
        super.loadAdditional(tag);
        fishes.clear();
        for (ItemStack s : tag.read("Fishes", ItemStack.OPTIONAL_CODEC.listOf()).orElse(java.util.List.of())) {
            if (!s.isEmpty() && fishes.size() < MAX_FISH) fishes.add(s);
        }
        if (fishes.isEmpty()) { // migrate the old single-fish format
            tag.read("Fish", ItemStack.OPTIONAL_CODEC).filter(s -> !s.isEmpty()).ifPresent(fishes::add);
        }
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
    // §multiloader: no onDataPacket override — that's a Forge-only hook. Vanilla's client packet handler
    // calls load(tag) itself, so getUpdateTag()/getUpdatePacket() above are all the sync we need.
}
