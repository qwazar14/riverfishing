package com.riverfishing.block;

import com.riverfishing.item.FishItem;
import com.riverfishing.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
        int w = FishItem.getWeightG(stack);
        if (w > MAX_WEIGHT_G) return false;
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
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ListTag list = new ListTag();
        for (ItemStack f : fishes) list.add(f.save(new CompoundTag()));
        tag.put("Fishes", list);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        fishes.clear();
        ListTag list = tag.getList("Fishes", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            ItemStack f = ItemStack.of(list.getCompound(i));
            if (!f.isEmpty()) fishes.add(f);
        }
        // Legacy single-mount stand (pre-0.5.1): carry the one fish over.
        if (fishes.isEmpty() && tag.contains("Fish")) {
            ItemStack f = ItemStack.of(tag.getCompound("Fish"));
            if (!f.isEmpty()) fishes.add(f);
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
    // §multiloader: no Forge-only onDataPacket override — vanilla's client handler calls load(tag) itself.
}
