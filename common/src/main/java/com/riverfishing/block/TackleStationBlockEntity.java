package com.riverfishing.block;

import com.riverfishing.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
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

    // §1.20.1: BE persistence predates the registry-aware overloads — saveAdditional/load take the
    // bare tag, and stacks round-trip through save(new CompoundTag()) / ItemStack.of.
    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ListTag list = new ListTag();
        for (int i = 0; i < items.getContainerSize(); i++) {
            list.add(items.getItem(i).save(new CompoundTag()));
        }
        tag.put("Materials", list);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        ListTag list = tag.getList("Materials", 10);
        for (int i = 0; i < items.getContainerSize() && i < list.size(); i++) {
            items.setItem(i, ItemStack.of(list.getCompound(i)));
        }
    }
}
