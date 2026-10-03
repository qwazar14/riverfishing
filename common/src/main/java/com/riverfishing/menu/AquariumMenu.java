package com.riverfishing.menu;

import com.riverfishing.fish.CatchCard;
import com.riverfishing.item.BaitItem;
import com.riverfishing.item.FishItem;
import com.riverfishing.item.FishMealItem;
import com.riverfishing.item.FishOilItem;
import com.riverfishing.item.GroundbaitItem;
import com.riverfishing.registry.ModBlocks;
import com.riverfishing.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * §aquarium-window (0.9.0): the tank's slots, on the indices of docs/design/breeding-api.md "Layer 4".
 * The block entity IS the container (stream B); this class only frames it — filters per slot and a
 * shift-click that lets {@link #moveItemStackTo} pick the slot by those same filters.
 */
public class AquariumMenu extends AbstractContainerMenu {
    public static final int TANK_SLOTS = 12, DATA_SIZE = 13;   // §scale-genes: +1, the pair's varieties; §aquarium-cabinet: +2, the pair's slots and the fry
    public static final int FISH_FIRST = 0, FISH_LAST = 5, FOOD = 6, GROUNDBAIT = 7, WATER = 8, RESULT = 9,
            MODULE_FIRST = 10, MODULE_LAST = 11;
    public static final int INV_START = TANK_SLOTS;

    // Menu = container order for the first twelve slots, so a menu index IS a container index here.
    /** Pixel positions of the tank slots, index-aligned with the table above; the screen texture is drawn on them. */
    // §aquarium-cabinet (1.1.0): the fish sit apart — 6 px across, 8 between the rows — so the pair's line
    // has a gutter to run in. tools/gen_aquarium_gui.py draws each well one pixel up and left of these.
    public static final int[][] SLOT_XY = {
            {71, 24}, {95, 24}, {119, 24}, {71, 48}, {95, 48}, {119, 48}, // fish 3×2
            {17, 105},  // food
            {37, 105},  // groundbait
            {17, 25},   // water
            {202, 84},  // result
            {173, 29}, {197, 29} // modules
    };
    /** Where the inventory's first slot sits; rows are 18 apart and the hotbar 58 below the first row. */
    public static final int INV_X = 35, INV_Y = 163;

    private final Container tank;
    private final ContainerData data;

    public AquariumMenu(int id, Inventory inv, Container tank, ContainerData data) {
        super(ModMenus.AQUARIUM.get(), id);
        checkContainerSize(tank, TANK_SLOTS);
        checkContainerDataCount(data, DATA_SIZE);
        this.tank = tank;
        this.data = data;
        tank.startOpen(inv.player);

        for (int i = FISH_FIRST; i <= FISH_LAST; i++) {
            addSlot(new Slot(tank, i, SLOT_XY[i][0], SLOT_XY[i][1]) {
                // §incubator: roe and fish do not share a tank — none goes in while the cup holds roe or fry
                @Override public boolean mayPlace(ItemStack s) { return isCardedFish(s) && !cupHolds(); }
                @Override public int getMaxStackSize() { return 1; }
                // …and while it incubates in an empty tank, the fish slots step aside for the incubator
                @Override public boolean isActive() { return !incubatorMode(); }
            });
        }
        addSlot(filtered(FOOD, AquariumMenu::isFood, 64));
        addSlot(filtered(GROUNDBAIT, s -> s.getItem() instanceof GroundbaitItem, 64));
        addSlot(filtered(WATER, s -> s.is(Items.WATER_BUCKET), 1));
        addSlot(new Slot(tank, RESULT, SLOT_XY[RESULT][0], SLOT_XY[RESULT][1]) {
            // §aq-fix: roe goes IN here to incubate — the tank decides (only with no fish inside)
            @Override public boolean mayPlace(ItemStack s) { return tank.canPlaceItem(RESULT, s); }
        });
        for (int i = MODULE_FIRST; i <= MODULE_LAST; i++) addSlot(filtered(i, AquariumMenu::isModule, 1));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, INV_X + col * 18, INV_Y + 58));

        addDataSlots(data);
    }

    /**
     * Client side: the tank is the block entity when it is loaded (so the renderer sees a fish the moment
     * it is dropped in), a dummy otherwise; the ints are ALWAYS a plain sync target — the server's
     * {@code data()} is the source of truth and its client twin may well ignore writes.
     */
    public static AquariumMenu fromNetwork(int id, Inventory inv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        Container tank = inv.player.level().getBlockEntity(pos) instanceof Container c
                && c.getContainerSize() == TANK_SLOTS ? c : new SimpleContainer(TANK_SLOTS);
        return new AquariumMenu(id, inv, tank, new SimpleContainerData(DATA_SIZE));
    }

    private Slot filtered(int index, java.util.function.Predicate<ItemStack> filter, int max) {
        return new Slot(tank, index, SLOT_XY[index][0], SLOT_XY[index][1]) {
            @Override public boolean mayPlace(ItemStack s) { return filter.test(s); }
            @Override public int getMaxStackSize() { return max; }
        };
    }

    /** §incubator: roe or fry in the result cup. */
    public boolean cupHolds() {
        Item it = tank.getItem(RESULT).getItem();
        return it instanceof com.riverfishing.item.RoeItem || it instanceof com.riverfishing.item.FryItem;
    }

    /** §incubator: roe or fry in the cup and not a fish in the tank — the window is an incubator now. */
    public boolean incubatorMode() {
        for (int i = FISH_FIRST; i <= FISH_LAST; i++) if (!tank.getItem(i).isEmpty()) return false;
        return cupHolds();
    }

    /** A tank slot's stack, for the window's drawings (the sex rings, the cup's count). */
    public ItemStack tankItem(int i) {
        return tank.getItem(i);
    }

    static boolean isCardedFish(ItemStack s) {
        return s.getItem() instanceof FishItem && CatchCard.has(s) && !com.riverfishing.item.CookedFish.isCooked(s);   // §cooking
    }

    static boolean isFood(ItemStack s) {
        Item it = s.getItem();
        return (it instanceof BaitItem b && !b.artificial()) || it instanceof FishMealItem || it instanceof FishOilItem;
    }

    static boolean isModule(ItemStack s) {
        return s.is(ModBlocks.AERATOR.get().asItem()) || s.is(ModBlocks.SNAG_PILE.get().asItem())
                || s.is(ModBlocks.GRAVEL_BED.get().asItem()) || s.is(ModBlocks.WARM_OUTFLOW.get().asItem())
                || s.is(ModBlocks.FEEDING_STATION.get().asItem());
    }

    /** The ints of the contract (docs/design/breeding-api.md, Layer 4). */
    public int data(int i) {
        return data.get(i);
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        if (index < INV_START) {
            if (!moveItemStackTo(stack, INV_START, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, TANK_SLOTS, false)) { // mayPlace picks the slot; the result refuses
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return before;
    }

    @Override
    public void removed(Player p) {
        super.removed(p);
        tank.stopOpen(p);
    }

    @Override
    public boolean stillValid(Player p) {
        return tank.stillValid(p);
    }
}
