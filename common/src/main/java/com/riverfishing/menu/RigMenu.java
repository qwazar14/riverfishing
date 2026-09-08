package com.riverfishing.menu;

import com.riverfishing.component.RigType;
import com.riverfishing.item.RigItem;
import com.riverfishing.registry.ModMenus;
import com.riverfishing.rig.RigData;
import com.riverfishing.rig.RigLayout;
import com.riverfishing.rig.SlotRole;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * GUI for a rig's internal inventory (Module 4). Slots are role-validated and laid out per
 * {@link RigLayout}; contents are backed by the held rig's NBT and saved back on change/close.
 */
public class RigMenu extends AbstractContainerMenu {
    public static final int SLOT_Y = 30;

    private final Player player;
    private final InteractionHand hand;
    private final ItemStack rig;
    private final RigType type;
    private final SlotRole[] roles;
    private final Container contents;

    public RigMenu(int id, Inventory inv, InteractionHand hand) {
        super(ModMenus.RIG.get(), id);
        this.player = inv.player;
        this.hand = hand;
        this.rig = inv.player.getItemInHand(hand);
        this.type = RigData.rigType(rig);
        this.roles = RigLayout.rolesFor(type);

        NonNullList<ItemStack> loaded = RigData.load(rig);
        SimpleContainer c = new SimpleContainer(roles.length);
        for (int i = 0; i < roles.length; i++) {
            c.setItem(i, loaded.get(i).copy());
        }
        this.contents = c;

        int startX = (176 - roles.length * 18) / 2 + 1;
        for (int i = 0; i < roles.length; i++) {
            addSlot(new RoleSlot(contents, i, startX + i * 18, SLOT_Y, roles[i]));
        }
        addPlayerInventory(inv);
    }

    public static RigMenu fromNetwork(int id, Inventory inv, FriendlyByteBuf buf) {
        return new RigMenu(id, inv, buf.readEnum(InteractionHand.class));
    }

    public SlotRole roleAt(int slotIndex) {
        return slotIndex >= 0 && slotIndex < roles.length ? roles[slotIndex] : null;
    }

    public int rigSlotCount() {
        return roles.length;
    }

    private void addPlayerInventory(Inventory inv) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, 8 + col * 18, 142));
        }
    }

    private void saveToRig() {
        if (player.level().isClientSide) return;
        NonNullList<ItemStack> out = NonNullList.withSize(roles.length, ItemStack.EMPTY);
        for (int i = 0; i < roles.length; i++) {
            out.set(i, contents.getItem(i));
        }
        RigData.save(rig, out);
    }

    @Override
    public void removed(Player p) {
        saveToRig();
        super.removed(p);
    }

    @Override
    public boolean stillValid(Player p) {
        return p.getItemInHand(hand).getItem() instanceof RigItem;
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return result;

        ItemStack stack = slot.getItem();
        result = stack.copy();
        int rigCount = roles.length;
        int invEnd = slots.size();

        if (index < rigCount) {
            if (!moveItemStackTo(stack, rigCount, invEnd, true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            for (int i = 0; i < rigCount; i++) {
                if (roles[i].accepts(stack) && moveItemStackTo(stack, i, i + 1, false)) {
                    moved = true;
                    break;
                }
            }
            if (!moved) return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        saveToRig();
        return result;
    }

    private class RoleSlot extends Slot {
        private final SlotRole role;

        RoleSlot(Container container, int index, int x, int y, SlotRole role) {
            super(container, index, x, y);
            this.role = role;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return role.accepts(stack);
        }

        @Override
        public int getMaxStackSize() {
            // §rig: a rig carries a SINGLE hook — cap the slot at 1 so shift-click can't dump a whole stack.
            return role == SlotRole.HOOK ? 1 : super.getMaxStackSize();
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return Math.min(getMaxStackSize(), stack.getMaxStackSize());
        }

        @Override
        public void setChanged() {
            super.setChanged();
            saveToRig();
        }
    }
}
