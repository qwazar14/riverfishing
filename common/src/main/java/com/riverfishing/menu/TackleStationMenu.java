package com.riverfishing.menu;

import com.riverfishing.item.StackNbt;
import com.riverfishing.registry.ModItems;
import com.riverfishing.registry.ModMenus;
import com.riverfishing.rig.RigData;
import com.riverfishing.rig.RigLayout;
import com.riverfishing.rig.SlotRole;
import com.riverfishing.tackle.TackleForm;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;

import java.util.List;

/**
 * §tackle-station (0.6.0): stonecutter-style flow — pick a form (buttons 0..17), pick a weight
 * (buttons 100+i), feed the material slots; the result slot previews the tied tackle and TAKING it
 * consumes the materials. Slots hand their contents back on close (no BlockEntity).
 */
public class TackleStationMenu extends AbstractContainerMenu {
    // Container (block-entity) indices. Deliberately UNCHANGED by §hook-pick so an existing bench's
    // contents still read as what they are: index 0 used to hold hooks, is no longer bound to any slot,
    // and whatever a player left in it is handed back the first time they open the bench.
    private static final int C_HOOK = 0;
    private static final int C_IRON = 1;
    private static final int C_STRING = 2;
    private static final int C_DYE = 3;

    // Menu slot order — what quickMoveStack and the screen index by. No longer the same numbers as the
    // container above, which is the whole reason they are now two named sets instead of one.
    public static final int SLOT_IRON = 0;
    public static final int SLOT_STRING = 1;
    public static final int SLOT_DYE = 2;
    public static final int SLOT_RESULT = 3;
    private static final int INV_START = 4;

    private final Player player;
    private final BlockPos pos;
    private final SimpleContainer materials;
    private final SimpleContainer result = new SimpleContainer(1);
    private final DataSlot formIndex = DataSlot.standalone();
    private final DataSlot weightIndex = DataSlot.standalone();
    // §tackle-adv: the fine-tuning knobs; defaults = a sane middle nobody has to touch.
    private final DataSlot leaderCm = DataSlot.standalone();
    private final DataSlot balancePos = DataSlot.standalone();
    /** §hook-pick: the hook is CHOSEN, not fed in — index into {@link TackleForm#HOOK_SIZES}. */
    private final DataSlot hookIndex = DataSlot.standalone();

    public TackleStationMenu(int id, Inventory inv, BlockPos pos) {
        super(ModMenus.TACKLE_STATION.get(), id);
        this.player = inv.player;
        this.pos = pos;
        // §tackle-station: the SERVER binds the bench's own persistent slots (walk up, re-tie); the
        // client mirrors through menu slot sync, so a dummy container there is fine.
        this.materials = inv.player.level().getBlockEntity(pos)
                instanceof com.riverfishing.block.TackleStationBlockEntity be
                ? be.items() : new SimpleContainer(4);
        // §hook-pick migration: the hook slot is gone, so anything still sitting in it goes back to the
        // player the first time this bench is opened. Server only — the client's copy is a mirror, and
        // handing the same stack back on both sides is how you print one.
        if (!inv.player.level().isClientSide && !materials.getItem(C_HOOK).isEmpty()) {
            inv.player.getInventory().placeItemBackInInventory(materials.removeItemNoUpdate(C_HOOK));
            materials.setChanged();
        }

        // Positions unchanged: x=38 is where the hook slot was, and the hook PICKER now sits there — the
        // material row keeps its shape and the hook stays where players already look for it.
        material(C_IRON, 62, s -> s.is(Items.IRON_INGOT));
        material(C_STRING, 86, s -> s.is(Items.STRING));
        material(C_DYE, 110, s -> s.getItem() instanceof DyeItem);
        addSlot(new Slot(result, 0, 176, 150) {
            @Override public boolean mayPlace(ItemStack s) { return false; }
            @Override public boolean mayPickup(Player p) { return !getItem().isEmpty(); }
            @Override public void onTake(Player p, ItemStack taken) {
                consumeMaterials();
                super.onTake(p, taken);
                updateResult();
            }
        });

        // Player inventory + hotbar.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, 43 + col * 18, 180 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, 43 + col * 18, 240));
        }

        addDataSlot(formIndex);
        addDataSlot(weightIndex);
        addDataSlot(leaderCm);
        addDataSlot(balancePos);
        addDataSlot(hookIndex);
        leaderCm.set(form().rig ? form().defaultLinkCm : 40);
        balancePos.set(1);
        hookIndex.set(3);        // hook 10 — the middle of the ladder, and what the old ghost slot showed
        updateResult();
    }

    public static TackleStationMenu fromNetwork(int id, Inventory inv, FriendlyByteBuf buf) {
        return new TackleStationMenu(id, inv, buf.readBlockPos());
    }

    public TackleForm form() {
        return TackleForm.values()[Math.floorMod(formIndex.get(), TackleForm.values().length)];
    }

    public int weightGrams() {
        TackleForm f = form();
        return f.weights[Math.floorMod(weightIndex.get(), f.weights.length)];
    }

    public int leaderCm() { return Math.max(5, leaderCm.get()); }
    public int balancePos() { return Math.floorMod(balancePos.get(), 3); }

    /** §hook-pick: index into {@link TackleForm#HOOK_SIZES}. */
    public int hookIdx() { return Math.floorMod(hookIndex.get(), TackleForm.HOOK_SIZES.length); }

    /** The angling size of the hook the bench will tie on, e.g. 10 for a hook 10. */
    public int hookSize() { return TackleForm.HOOK_SIZES[hookIdx()]; }

    /** Iron the current tackle costs — its own metal PLUS the hooks the bench now supplies (§hook-pick). */
    public int ironNeeded() {
        TackleForm f = form();
        return f.ironFor(weightGrams()) + TackleForm.hookIngots(hookIdx(), f.hooksNeeded());
    }

    /** 0..N = form; 100+i = weight step; 200+cm = leader length (§tackle-adv); 400+i = balance. */
    @Override
    public boolean clickMenuButton(Player p, int id) {
        if (id >= 0 && id < TackleForm.values().length) {
            formIndex.set(id);
            weightIndex.set(0);
            // §tackle-adv: each rig style resets to ITS OWN sensible hook-link default.
            TackleForm f = TackleForm.values()[id];
            if (f.rig) leaderCm.set(f.defaultLinkCm);
            updateResult();
            return true;
        }
        if (id >= 100 && id < 100 + form().weights.length) {
            weightIndex.set(id - 100);
            updateResult();
            return true;
        }
        if (id >= 200 && id <= 300) {
            leaderCm.set(Math.max(5, Math.min(100, id - 200)));
            updateResult();
            return true;
        }
        if (id >= 400 && id <= 402) {
            balancePos.set(id - 400);
            updateResult();
            return true;
        }
        if (id >= 500 && id < 500 + TackleForm.HOOK_SIZES.length) {
            hookIndex.set(id - 500);
            updateResult();
            return true;
        }
        return false;
    }

    private boolean materialsPresent() {
        return materials.getItem(C_IRON).getCount() >= ironNeeded()
                && materials.getItem(C_STRING).getCount() >= form().stringNeeded();
    }

    private void updateResult() {
        result.setItem(0, materialsPresent() ? buildResult() : ItemStack.EMPTY);
        broadcastChanges();
    }

    private ItemStack buildResult() {
        TackleForm f = form();
        int grams = weightGrams();
        ItemStack out = new ItemStack(f.item());
        int leader = leaderCm();
        int balance = balancePos();
        TackleForm.stamp(out, f, grams, player.getPlainTextName(), leader, balance);
        if (f.rig) {
            // §hook-pick: the bench TIES the chosen size on — the rig comes ready to bait, and the player
            // never has to have crafted that hook at all; they paid for it in iron (see ironNeeded).
            ItemStack hook = new ItemStack(ModItems.HOOKS.get(hookIdx()).get());
            SlotRole[] roles = RigLayout.rolesFor(RigData.rigType(out));
            NonNullList<ItemStack> contents = RigData.load(out);
            int placed = 0;
            for (int i = 0; i < roles.length && placed < f.hooksNeeded(); i++) {
                if (roles[i] == SlotRole.HOOK) {
                    contents.set(i, hook.copyWithCount(1));
                    placed++;
                }
            }
            RigData.save(out, contents);
        }
        // §26.x: a dye's colour is no longer on the DyeItem — it rides the stack as DataComponents.DYE,
        // and applyDyes takes DyeColor instead of DyeItem. Still returns the dyed copy.
        if (f.dyeable) {
            net.minecraft.world.item.DyeColor dye =
                    materials.getItem(SLOT_DYE).get(net.minecraft.core.component.DataComponents.DYE);
            if (dye != null) {
                out = DyedItemColor.applyDyes(out, List.of(dye));
            }
        }
        return out;
    }

    /**
     * One material slot: what it accepts, plus the thing all four must do — refresh the preview.
     *
     * <p>§26.x: {@code SimpleContainer.addListener} is gone, and the port replaced it with an override
     * of {@link #slotsChanged}. That hook is not a general notification: vanilla only reaches it from
     * containers that call {@code menu.slotsChanged(this)} themselves (CraftingContainer, TransientCraftingContainer),
     * and the bench's container is the block entity's plain one — so nothing ever called it and the
     * result slot stayed empty no matter what you fed the bench. {@code Slot#setChanged} IS called on
     * every path that writes a slot, so the notification is taken from there instead. Going through
     * one helper is deliberate: four hand-written copies is exactly how three of them end up right.
     */
    private void material(int index, int x, java.util.function.Predicate<ItemStack> accepts) {
        addSlot(new Slot(materials, index, x, 150) {
            @Override public boolean mayPlace(ItemStack s) { return accepts.test(s); }

            @Override
            public void setChanged() {
                super.setChanged();
                updateResult();
            }
        });
    }

    /** Kept for the paths that DO call it (a bundle emptied into the bench goes through here). */
    @Override
    public void slotsChanged(net.minecraft.world.Container container) {
        super.slotsChanged(container);
        updateResult();
    }

    private void consumeMaterials() {
        TackleForm f = form();
        // The iron pays for the hooks too — one number, charged by the same method that showed it in the
        // GUI, so the price a player read and the price they paid cannot drift apart.
        materials.getItem(C_IRON).shrink(ironNeeded());
        materials.getItem(C_STRING).shrink(f.stringNeeded());
        if (f.dyeable && !materials.getItem(C_DYE).isEmpty()) {
            materials.getItem(C_DYE).shrink(1);
        }
        materials.setChanged();
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        if (index == SLOT_RESULT) {
            if (!moveItemStackTo(stack, INV_START, slots.size(), true)) return ItemStack.EMPTY;
            slot.onTake(p, stack);
        } else if (index < INV_START) {
            if (!moveItemStackTo(stack, INV_START, slots.size(), false)) return ItemStack.EMPTY;
        } else {
            // Hooks are no longer a material (§hook-pick), so shift-clicking one here does nothing.
            int target = stack.is(Items.IRON_INGOT) ? SLOT_IRON
                    : stack.is(Items.STRING) ? SLOT_STRING
                    : stack.getItem() instanceof DyeItem ? SLOT_DYE : -1;
            if (target < 0 || !moveItemStackTo(stack, target, target + 1, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return before;
    }

    // NOTE: no clearContainer on close — the materials LIVE in the block entity now and drop when
    // the block breaks (§tackle-station persistence).

    @Override
    public boolean stillValid(Player p) {
        return p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
}
