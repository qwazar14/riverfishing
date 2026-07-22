package com.riverfishing.menu;

import com.riverfishing.item.HookItem;
import com.riverfishing.item.StackNbt;
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
    public static final int SLOT_HOOK = 0;
    public static final int SLOT_IRON = 1;
    public static final int SLOT_STRING = 2;
    public static final int SLOT_DYE = 3;
    public static final int SLOT_RESULT = 4;

    private final Player player;
    private final BlockPos pos;
    private final SimpleContainer materials;
    private final SimpleContainer result = new SimpleContainer(1);
    private final DataSlot formIndex = DataSlot.standalone();
    private final DataSlot weightIndex = DataSlot.standalone();
    // §tackle-adv: the fine-tuning knobs; defaults = a sane middle nobody has to touch.
    private final DataSlot leaderCm = DataSlot.standalone();
    private final DataSlot balancePos = DataSlot.standalone();

    public TackleStationMenu(int id, Inventory inv, BlockPos pos) {
        super(ModMenus.TACKLE_STATION.get(), id);
        this.player = inv.player;
        this.pos = pos;
        // §tackle-station: the SERVER binds the bench's own persistent slots (walk up, re-tie); the
        // client mirrors through menu slot sync, so a dummy container there is fine.
        this.materials = inv.player.level().getBlockEntity(pos)
                instanceof com.riverfishing.block.TackleStationBlockEntity be
                ? be.items() : new SimpleContainer(4);
        materials.addListener(c -> updateResult());

        addSlot(new Slot(materials, SLOT_HOOK, 14, 138) {
            @Override public boolean mayPlace(ItemStack s) { return s.getItem() instanceof HookItem; }
        });
        addSlot(new Slot(materials, SLOT_IRON, 38, 138) {
            @Override public boolean mayPlace(ItemStack s) { return s.is(Items.IRON_INGOT); }
        });
        addSlot(new Slot(materials, SLOT_STRING, 62, 138) {
            @Override public boolean mayPlace(ItemStack s) { return s.is(Items.STRING); }
        });
        addSlot(new Slot(materials, SLOT_DYE, 86, 138) {
            @Override public boolean mayPlace(ItemStack s) { return s.getItem() instanceof DyeItem; }
        });
        addSlot(new Slot(result, 0, 152, 138) {
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
                addSlot(new Slot(inv, col + row * 9 + 9, 20 + col * 18, 168 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, 20 + col * 18, 228));
        }

        addDataSlot(formIndex);
        addDataSlot(weightIndex);
        addDataSlot(leaderCm);
        addDataSlot(balancePos);
        leaderCm.set(form().rig ? form().defaultLinkCm : 40);
        balancePos.set(1);
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
        return false;
    }

    private boolean materialsPresent() {
        TackleForm f = form();
        return materials.getItem(SLOT_HOOK).getItem() instanceof HookItem
                && materials.getItem(SLOT_HOOK).getCount() >= f.hooksNeeded()
                && materials.getItem(SLOT_IRON).getCount() >= f.ironFor(weightGrams())
                && materials.getItem(SLOT_STRING).getCount() >= f.stringNeeded();
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
        StackNbt.mutate(out, tag -> {
            tag.putInt(TackleForm.TAG_WEIGHT, grams);
            tag.putString(TackleForm.TAG_TIED_BY, player.getGameProfile().getName());
            // §tackle-adv: the knobs ride along; effects arrive with the bite-engine wiring.
            // Hook link (formerly "leader") is a RIG concept — the distance hook-to-anchor point.
            if (f.rig) tag.putInt(TackleForm.TAG_LEADER_CM, leader);
            if (!f.rig) tag.putInt(TackleForm.TAG_BALANCE, balance);
            if (f == TackleForm.SPINNER || f == TackleForm.SPOON) {
                tag.putInt(TackleForm.TAG_BLADE, Math.min(5, 1 + grams / 15)); // blade follows the mass
            }
        });
        if (f.rig) {
            // The consumed hooks go straight INTO the rig's hook slots — the rig comes ready to bait.
            SlotRole[] roles = RigLayout.rolesFor(RigData.rigType(out));
            NonNullList<ItemStack> contents = RigData.load(out);
            int placed = 0;
            for (int i = 0; i < roles.length && placed < f.hooksNeeded(); i++) {
                if (roles[i] == SlotRole.HOOK) {
                    contents.set(i, materials.getItem(SLOT_HOOK).copyWithCount(1));
                    placed++;
                }
            }
            RigData.save(out, contents);
        }
        if (f.dyeable && materials.getItem(SLOT_DYE).getItem() instanceof DyeItem dye) {
            out = DyedItemColor.applyDyes(out, List.of(dye)); // applyDyes RETURNS the dyed copy
        }
        return out;
    }

    private void consumeMaterials() {
        TackleForm f = form();
        materials.getItem(SLOT_HOOK).shrink(f.hooksNeeded());
        materials.getItem(SLOT_IRON).shrink(f.ironFor(weightGrams()));
        materials.getItem(SLOT_STRING).shrink(f.stringNeeded());
        if (f.dyeable && !materials.getItem(SLOT_DYE).isEmpty()) {
            materials.getItem(SLOT_DYE).shrink(1);
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
            if (!moveItemStackTo(stack, 5, slots.size(), true)) return ItemStack.EMPTY;
            slot.onTake(p, stack);
        } else if (index < 5) {
            if (!moveItemStackTo(stack, 5, slots.size(), false)) return ItemStack.EMPTY;
        } else {
            int target = stack.getItem() instanceof HookItem ? SLOT_HOOK
                    : stack.is(Items.IRON_INGOT) ? SLOT_IRON
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
