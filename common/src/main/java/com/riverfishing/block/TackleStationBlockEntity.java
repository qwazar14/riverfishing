package com.riverfishing.block;

import com.riverfishing.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * §tackle-station (0.6.0): the bench keeps its material slots while the block stands — walk up and
 * re-tie without re-feeding it. Contents drop on break.
 */
public class TackleStationBlockEntity extends BlockEntity {
    private final SimpleContainer items = new SimpleContainer(4);

    public TackleStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TACKLE_STATION.get(), pos, state);
        items.addListener(c -> setChanged());
    }

    public SimpleContainer items() {
        return items;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (int i = 0; i < items.getContainerSize(); i++) {
            list.add(items.getItem(i).saveOptional(registries));
        }
        tag.put("Materials", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ListTag list = tag.getList("Materials", 10);
        for (int i = 0; i < items.getContainerSize() && i < list.size(); i++) {
            items.setItem(i, ItemStack.parseOptional(registries, list.getCompound(i)));
        }
    }
}
