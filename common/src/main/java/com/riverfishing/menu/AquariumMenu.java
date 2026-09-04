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
    public static final int TANK_SLOTS = 12, DATA_SIZE = 11;   // §scale-genes: +1, the pair's varieties
    public static final int FISH_FIRST = 0, FISH_LAST = 5, FOOD = 6, GROUNDBAIT = 7, WATER = 8, RESULT = 9,
            MODULE_FIRST = 10, MODULE_LAST = 11;
    public static final int INV_START = TANK_SLOTS;

    // Menu = container order for the first twelve slots, so a menu index IS a container index here.
    /** Pixel positions of the tank slots, index-aligned with the table above; the screen texture is drawn on them. */
    public static final int[][] SLOT_XY = {
            {44, 20}, {62, 20}, {80, 20}, {44, 38}, {62, 38}, {80, 38}, // fish 3×2
            {8, 66},    // food
            {30, 66},   // groundbait
            {8, 20},    // water
            {126, 29},  // result
            {152, 20}, {152, 38} // modules
    };

    private final Container tank;
    private final ContainerData data;

    public AquariumMenu(int id, Inventory inv, Container tank, ContainerData data) {
        super(ModMenus.AQUARIUM.get(), id);
        checkContainerSize(tank, TANK_SLOTS);
        checkContainerDataCount(data, DATA_SIZE);
        this.tank = tank;
        this.data = data;
        tank.startOpen(inv.player);

        for (int i = FISH_FIRST; i <= FISH_LAST; i++) addSlot(filtered(i, AquariumMenu::isCardedFish, 1));
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
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 8 + col * 18, 198));

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

    static boolean isCardedFish(ItemStack s) {
        return s.getItem() instanceof FishItem && CatchCard.has(s);
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

    /** The ten ints of the contract (status, spawn day, incubation day/total, feed ticks, water, window, fish, clutch). */
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
