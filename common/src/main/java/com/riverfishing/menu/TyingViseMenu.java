package com.riverfishing.menu;

import com.riverfishing.item.HookItem;
import com.riverfishing.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * §tying: two slots and the player's inventory. Slot 0 takes the HOOK the lure is tied on, slot 1 is
 * where the tied lure lands. Materials are not slotted — the vise takes them straight out of the
 * inventory when you tie, the way the tackle station takes its iron — so the canvas can show what you
 * can afford as you draw.
 */
public class TyingViseMenu extends AbstractContainerMenu {
    public static final int HOOK = 0, RESULT = 1, FIRST_INV = 2;
    public static final int HOOK_X = 152, HOOK_Y = 138, RESULT_X = 216, RESULT_Y = 138, INV_Y = 170;

    private final SimpleContainer bench = new SimpleContainer(2);
    private final BlockPos pos;
    private final Player player;

    public TyingViseMenu(int id, Inventory inv, BlockPos pos) {
        super(ModMenus.TYING_VISE.get(), id);
        this.pos = pos;
        this.player = inv.player;
        addSlot(new Slot(bench, HOOK, HOOK_X, HOOK_Y) {
            @Override public boolean mayPlace(ItemStack s) { return s.getItem() instanceof HookItem; }
            @Override public int getMaxStackSize() { return 1; }
        });
        addSlot(new Slot(bench, RESULT, RESULT_X, RESULT_Y) {
            @Override public boolean mayPlace(ItemStack s) { return false; }
        });
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inv, col + row * 9 + 9, 43 + col * 18, INV_Y + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 43 + col * 18, INV_Y + 58));
    }

    public static TyingViseMenu fromNetwork(int id, Inventory inv, FriendlyByteBuf buf) {
        return new TyingViseMenu(id, inv, buf.readBlockPos());
    }

    public ItemStack hook() { return bench.getItem(HOOK); }
    public ItemStack result() { return bench.getItem(RESULT); }
    public void setResult(ItemStack s) { bench.setItem(RESULT, s); bench.setChanged(); }
    public void takeHook() { bench.setItem(HOOK, ItemStack.EMPTY); bench.setChanged(); }
    public Player player() { return player; }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), copy = stack.copy();
        if (index < FIRST_INV) {
            if (!moveItemStackTo(stack, FIRST_INV, slots.size(), true)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof HookItem && !slots.get(HOOK).hasItem()) {
            if (!moveItemStackTo(stack, HOOK, HOOK + 1, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player p) {
        return p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void removed(Player p) {
        super.removed(p);
        clearContainer(p, bench);   // the hook and an untaken lure go back to the player
    }
}
