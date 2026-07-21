package com.riverfishing.block;

import com.riverfishing.item.FishItem;
import com.riverfishing.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * §mini-aquarium (0.5.1): the old one-fish trophy stand is now a desktop tank — up to
 * {@link #CAPACITY} small fish ({@link #MAX_WEIGHT_G} g each) circling under the glass.
 * §26.1: ValueInput/ValueOutput codec storage; the BE pops its own fish on removal.
 */
public class TrophyStandBlockEntity extends BlockEntity {
    public static final int CAPACITY = 5;
    public static final int MAX_WEIGHT_G = 150;

    private final List<ItemStack> fishes = new ArrayList<>();

    public TrophyStandBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TROPHY_STAND.get(), pos, state);
    }

    public List<ItemStack> getFishes() {
        return fishes;
    }

    /** True when the fish fits (≤150 g, tank not full) and was taken. */
    public boolean addFish(ItemStack stack) {
        if (fishes.size() >= CAPACITY) return false;
        if (FishItem.getWeightG(stack) > MAX_WEIGHT_G) return false;
        fishes.add(stack.copyWithCount(1));
        sync();
        return true;
    }

    public ItemStack removeLast() {
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

    // §26.1: the block's onRemove hook is gone — the BE pops its own fish on removal.
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

    @Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput tag) {
        super.saveAdditional(tag);
        tag.store("Fishes", ItemStack.OPTIONAL_CODEC.listOf(), List.copyOf(fishes));
    }

    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput tag) {
        super.loadAdditional(tag);
        fishes.clear();
        tag.read("Fishes", ItemStack.OPTIONAL_CODEC.listOf()).ifPresent(list -> {
            for (ItemStack f : list) if (!f.isEmpty()) fishes.add(f);
        });
        // Legacy single-mount stand (pre-0.5.1): carry the one fish over.
        if (fishes.isEmpty()) {
            tag.read("Fish", ItemStack.OPTIONAL_CODEC).ifPresent(f -> {
                if (!f.isEmpty()) fishes.add(f);
            });
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
    // §multiloader: no Forge-only onDataPacket override — vanilla's client handler calls load(tag) itself.
}
