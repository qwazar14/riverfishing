package com.riverfishing.block;

import com.riverfishing.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * §tackle-station (0.6.0): the bench keeps its material slots while the block stands — walk up and
 * re-tie without re-feeding it. Contents drop on break.
 */
public class TackleStationBlockEntity extends BlockEntity {
    // §26.1: SimpleContainer lost addListener, so the "mark the chunk dirty" hook is an override of
    // setChanged instead — it fires from exactly the places the old listener did (setItem /
    // removeItem / addItem / clearContent), so the bench still persists what a player drops in.
    private final SimpleContainer items = new SimpleContainer(4) {
        @Override
        public void setChanged() {
            super.setChanged();
            TackleStationBlockEntity.this.setChanged();
        }
    };

    public TackleStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TACKLE_STATION.get(), pos, state);
    }

    public SimpleContainer items() {
        return items;
    }

    // §26.1: the block's onRemove hook is gone — the BE pops its own materials on removal, same shape
    // as AquariumBlockEntity/RodPodBlockEntity. Only called on a real removal, so the old
    // "!state.is(newState.getBlock())" guard is implicit.
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null && !level.isClientSide()) {
            Containers.dropContents(level, pos, items);
        }
        super.preRemoveSideEffects(pos, state);
    }

    // §26.1: block-entity NBT is ValueOutput/ValueInput, and ItemStack.saveOptional/parseOptional are
    // gone. ContainerHelper does the whole slot-indexed round-trip over the container's backing list
    // (the same call RodPodBlockEntity uses for its docked rods), so no per-slot codec work here.
    @Override
    protected void saveAdditional(ValueOutput tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items.getItems());
    }

    @Override
    protected void loadAdditional(ValueInput tag) {
        super.loadAdditional(tag);
        ContainerHelper.loadAllItems(tag, items.getItems());
    }
}
