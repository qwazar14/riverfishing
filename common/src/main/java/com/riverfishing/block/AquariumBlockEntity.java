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
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** Holds up to {@link #MAX_FISH} mounted fish for an {@link AquariumBlock} (only the master cell has one). */
public class AquariumBlockEntity extends BlockEntity implements net.minecraft.world.Container {
    /** Slots 0..5 hold fish (§aq-b: the window's 3x2 grid). */
    public static final int MAX_FISH = 6;
    /** 0-5 fish, 6 food, 7 groundbait, 8 water bucket, 9 roe/fry (the {@link #roe} field), 10-11 modules. */
    public static final int SLOTS = 12;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);

    // §b/breeding (0.9.0): the tank is a live one — the rules are in AquariumBreeding, next door, which is the
    // only thing that reads or writes these (package-private on purpose; no getters for one caller).
    long fedUntil;                   // world day until which the fish count as fed (exclusive)
    long spawnTicks;                 // §tank-days: world time the clutch run started, 0 = none
    long incubate;                   // §tank-days: world time incubation started, 0 = none
    ItemStack roe = ItemStack.EMPTY; // the roe slot: a RoeItem, or the FryItem it hatched into
    /**
     * §aq-water: 0..100. A tank starts FULL — the recipe pours a bucket of water into it, so a tank you
     * have just placed is a tank you have just filled, and asking for a second bucket before the first
     * fish can go in was asking twice for the same thing.
     */
    int water = 100;
    long waterAcc;                   // water change in percent-ticks not yet a whole percent (not saved)
    long clock;                      // world time the ticker last saw (not saved: a reload skips the gap)
    boolean oil;                     // fish oil was taken at the start of the current spawn run
    String lastFood = "";            // what the last feeding was ("fish_meal" makes the clutch richer)
    final int[] view = new int[11];  // the ints the window reads, filled by the rules once a second

    public AquariumBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AQUARIUM.get(), pos, state);
    }

    /** The fish in slots 0..5, the empty ones skipped — the renderer, Jade and the rules read this. */
    public List<ItemStack> getFishes() {
        List<ItemStack> out = new ArrayList<>(MAX_FISH);
        for (int i = 0; i < MAX_FISH; i++) if (!items.get(i).isEmpty()) out.add(items.get(i));
        return out;
    }

    /** The window's ints (docs/design/breeding-api.md, Layer 4; §scale-genes added the eleventh),
     *  filled by the rules once a second. */
    public net.minecraft.world.inventory.ContainerData data() {
        return new net.minecraft.world.inventory.ContainerData() {
            @Override public int get(int i) { return view[i]; }
            @Override public void set(int i, int v) { view[i] = v; }
            @Override public int getCount() { return view.length; }
        };
    }

    // ---- Container: twelve slots. Slot 9 IS the roe field — the renderer and the rules call it by
    // name, the menu by number — so the two never disagree. ----

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < SLOTS; i++) if (!getItem(i).isEmpty()) return false;
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot == 9 ? roe : items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack out = slot == 9 ? roe.split(count) : ContainerHelper.removeItem(items, slot, count);
        if (!out.isEmpty()) changed(slot);
        return out;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack out = getItem(slot);
        if (slot == 9) roe = ItemStack.EMPTY; else items.set(slot, ItemStack.EMPTY);
        return out;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot == 9) roe = stack; else items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize());
        changed(slot);
    }

    /** The menu's own filters, mirrored so a hopper obeys the same table as a hand. */
    @Override
    public boolean canPlaceItem(int slot, ItemStack s) {
        if (slot < MAX_FISH) return s.getItem() instanceof com.riverfishing.item.FishItem && com.riverfishing.fish.CatchCard.has(s);
        return switch (slot) {
            case 6 -> s.getItem() instanceof com.riverfishing.item.BaitItem b && !b.artificial()
                    || s.getItem() instanceof com.riverfishing.item.FishMealItem
                    || s.getItem() instanceof com.riverfishing.item.FishOilItem;
            case 7 -> s.getItem() instanceof com.riverfishing.item.GroundbaitItem;
            case 8 -> s.is(net.minecraft.world.item.Items.WATER_BUCKET);
            case 9 -> s.getItem() instanceof com.riverfishing.item.RoeItem && getFishes().isEmpty(); // roe to hatch, in an empty tank
            default -> s.getItem() instanceof net.minecraft.world.item.BlockItem bi && bi.getBlock() instanceof WaterUpgradeBlock;
        };
    }

    @Override
    public boolean stillValid(Player player) {
        return net.minecraft.world.Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
        roe = ItemStack.EMPTY;
        incubate = 0;
        sync();
    }

    /** Fish and the roe slot are drawn in the world, so they sync; the rest only needs saving. */
    private void changed(int slot) {
        if (slot < MAX_FISH || slot == 9) {
            // Roe taken out (or hatched) forgets its days; roe put in starts them on the next tick.
            if (slot == 9 && !(roe.getItem() instanceof com.riverfishing.item.RoeItem)) incubate = 0;
            sync();
        } else {
            setChanged();
        }
    }

    /** The roe slot — a RoeItem while it incubates, the FryItem it hatched into after; for the renderer. */
    public ItemStack getRoe() {
        return roe;
    }

    /** World time incubation started, 0 when it has not; the renderer picks the day's frame from it. */
    public long getIncubate() {
        return incubate;
    }

    void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("Water", water);
        tag.putBoolean("Oil", oil);
        tag.putString("LastFood", lastFood);
        tag.putLong("FedUntil", fedUntil);
        tag.putLong("SpawnTicks", spawnTicks);
        tag.putLong("Incubate", incubate);
        if (!roe.isEmpty()) tag.put("Roe", roe.save(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registries);
        // §aq-water: an absent key is a tank that has never been saved, which is a full one.
        water = tag.contains("Water") ? tag.getInt("Water") : 100;
        oil = tag.getBoolean("Oil");
        lastFood = tag.getString("LastFood");
        fedUntil = tag.getLong("FedUntil");
        spawnTicks = tag.getLong("SpawnTicks");
        incubate = tag.getLong("Incubate");
        roe = tag.contains("Roe") ? ItemStack.parseOptional(registries, tag.getCompound("Roe")) : ItemStack.EMPTY;
        if (tag.contains("Fishes")) { // migrate the pre-window tank: its mounted fish into the first slots
            ListTag list = tag.getList("Fishes", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size() && i < MAX_FISH; i++) items.set(i, ItemStack.parseOptional(registries, list.getCompound(i)));
        } else if (tag.contains("Fish")) { // older still: the single-fish format
            items.set(0, ItemStack.parseOptional(registries, tag.getCompound("Fish")));
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
