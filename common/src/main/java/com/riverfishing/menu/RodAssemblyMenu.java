package com.riverfishing.menu;

import com.riverfishing.component.ComponentSlot;
import com.riverfishing.component.RodType;
import com.riverfishing.item.ReelItem;
import com.riverfishing.item.RodComponentItem;
import com.riverfishing.item.RodData;
import com.riverfishing.item.RodItem;
import com.riverfishing.registry.ModMenus;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * GUI for assembling a rod (§3.1). Component slots are built to match the rod: reel-less rods
 * (pole/bamboo/stick) have NO reel slot — just line + rig. Slots are backed by a transient container
 * loaded from the held rod's NBT and written back on change/close.
 */
public class RodAssemblyMenu extends AbstractContainerMenu {
    public static final int SLOT_Y = 34;
    private static final int SLOT_SPACING = 32;
    /** Max internal rig slots across all rig types (GRUSHA has 7) — proxy slots are always present. */
    public static final int RIG_SLOTS = 7;
    /** Below the slot diagonal (its lowest slot ends at y=70) and above the inventory (y=108). */
    public static final int RIG_ROW_Y = 78;

    private final Player player;
    private final InteractionHand hand;
    private final ItemStack rod;
    private final ComponentSlot[] slotTypes;
    private final Container components;
    private final SimpleContainer rigContents = new SimpleContainer(RIG_SLOTS);
    private boolean syncingRig;
    /** Lure rods (§spinning-tackle) carry leader+lure directly via this internal rig — no RIG column. */
    private final boolean directTackle;
    private ItemStack directRig = ItemStack.EMPTY;

    public RodAssemblyMenu(int id, Inventory inv, InteractionHand hand) {
        super(ModMenus.ROD_ASSEMBLY.get(), id);
        this.player = inv.player;
        this.hand = hand;
        this.rod = inv.player.getItemInHand(hand);

        RodType rodType = rod.getItem() instanceof RodItem ri ? ri.rodType() : null;
        this.directTackle = rodType != null && rodType.directTackle();
        boolean hasReel = rodType == null || rodType.takesReel();
        if (directTackle) {
            // §closed-slots: no visible RIG column — the rod's built-in rig lives internally and its own
            // slots (float/hook/bait, leader/lure) show inline. Reel-less float rods drop the reel slot.
            this.slotTypes = hasReel
                    ? new ComponentSlot[]{ComponentSlot.REEL, ComponentSlot.LINE}
                    : new ComponentSlot[]{ComponentSlot.LINE};
            com.riverfishing.component.RigType native_ = rodType != null ? rodType.nativeRig() : null;
            ItemStack rig = RodData.get(rod, ComponentSlot.RIG);
            boolean isNative = native_ != null && rig.getItem() instanceof com.riverfishing.item.RigItem ri
                    && ri.rigType() == native_;
            if (!isNative && native_ != null) {
                var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                        com.riverfishing.RiverFishing.id("rig_" + native_.jsonKey()));
                rig = item != null ? new ItemStack(item) : ItemStack.EMPTY;
            }
            this.directRig = rig;
        } else {
            this.slotTypes = hasReel
                    ? new ComponentSlot[]{ComponentSlot.REEL, ComponentSlot.LINE, ComponentSlot.RIG}
                    : new ComponentSlot[]{ComponentSlot.LINE, ComponentSlot.RIG};
        }
        this.components = new SimpleContainer(slotTypes.length);

        // Lay the slots along a diagonal so the rod reads like an actual rod: reel/handle low-left,
        // line in the middle, rig at the tip up-right (#1).
        int n = slotTypes.length;
        int baseX = (176 - (n - 1) * 36) / 2 - 8;
        int baseY = 30 + (n - 1) * 12;
        for (int i = 0; i < n; i++) {
            components.setItem(i, RodData.get(rod, slotTypes[i]).copy());
            addSlot(new ComponentSlotView(components, i, baseX + i * 36, baseY - i * 12, slotTypes[i]));
        }
        // Live rig contents (§rig-inline): the socketed rig's own slots, editable without unsocketing.
        for (int i = 0; i < RIG_SLOTS; i++) {
            addSlot(new RigProxySlot(rigContents, i, 8 + i * 18, RIG_ROW_Y));
        }
        loadRigContents();
        addPlayerInventory(inv);
    }

    // ---- inline rig slots (§rig-inline) ----

    private ItemStack socketedRig() {
        // Lure rods keep their rig internally (§spinning-tackle); everyone else uses the RIG column.
        return directTackle ? directRig : components.getItem(slotTypes.length - 1);
    }

    /** Roles of the socketed rig, or an empty array when no rig is in the socket. */
    public com.riverfishing.rig.SlotRole[] rigRoles() {
        ItemStack r = socketedRig();
        return r.getItem() instanceof com.riverfishing.item.RigItem
                ? com.riverfishing.rig.RigLayout.rolesFor(com.riverfishing.rig.RigData.rigType(r))
                : new com.riverfishing.rig.SlotRole[0];
    }

    public int rigProxyStart() {
        return slotTypes.length;
    }

    private void loadRigContents() {
        syncingRig = true;
        ItemStack r = socketedRig();
        NonNullList<ItemStack> loaded = r.getItem() instanceof com.riverfishing.item.RigItem
                ? com.riverfishing.rig.RigData.load(r) : null;
        for (int i = 0; i < RIG_SLOTS; i++) {
            rigContents.setItem(i, loaded != null && i < loaded.size() ? loaded.get(i).copy() : ItemStack.EMPTY);
        }
        syncingRig = false;
    }

    private void saveRigContents() {
        if (syncingRig || player.level().isClientSide) return;
        ItemStack r = socketedRig();
        if (!(r.getItem() instanceof com.riverfishing.item.RigItem)) return;
        com.riverfishing.rig.SlotRole[] roles = rigRoles();
        NonNullList<ItemStack> out = NonNullList.withSize(roles.length, ItemStack.EMPTY);
        for (int i = 0; i < roles.length; i++) {
            out.set(i, rigContents.getItem(i));
        }
        com.riverfishing.rig.RigData.save(r, out);
        if (directTackle) {
            RodData.set(rod, ComponentSlot.RIG, r); // persist the internal rig (not a visible column)
        }
        saveToRod();
    }

    /** A live slot inside the socketed rig; inactive (hidden) while no rig is socketed. */
    public class RigProxySlot extends Slot {
        private final int idx;

        RigProxySlot(Container container, int idx, int x, int y) {
            super(container, idx, x, y);
            this.idx = idx;
        }

        @Nullable
        public com.riverfishing.rig.SlotRole role() {
            com.riverfishing.rig.SlotRole[] roles = rigRoles();
            return idx < roles.length ? roles[idx] : null;
        }

        @Override
        public boolean isActive() {
            return role() != null;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            com.riverfishing.rig.SlotRole role = role();
            return role != null && role.accepts(stack);
        }

        @Override
        public boolean mayPickup(Player p) {
            return role() != null;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            saveRigContents();
        }
    }

    /** Network constructor used by IForgeMenuType. */
    public static RodAssemblyMenu fromNetwork(int id, Inventory inv, FriendlyByteBuf buf) {
        return new RodAssemblyMenu(id, inv, buf.readEnum(InteractionHand.class));
    }

    /** Float-depth slider stops (§fishing-depth) — menu buttons so the change runs server-side. */
    public static final int BUTTON_DEPTH_SURFACE = 7;
    public static final int BUTTON_DEPTH_MID = 8;
    public static final int BUTTON_DEPTH_BOTTOM = 9;

    /** The held rod, for the screen (slider visibility + current depth). */
    public ItemStack rodStack() {
        return player.getItemInHand(hand);
    }

    /** Live rig stack — the internal rig for lure rods, else the RIG component slot. */
    public ItemStack currentRig() {
        return socketedRig();
    }

    @Override
    public boolean clickMenuButton(Player p, int id) {
        String depth = switch (id) {
            case BUTTON_DEPTH_SURFACE -> "surface";
            case BUTTON_DEPTH_MID -> "mid";
            case BUTTON_DEPTH_BOTTOM -> "bottom";
            default -> null;
        };
        if (depth != null) {
            ItemStack rodStack = p.getItemInHand(hand);
            if (rodStack.getItem() instanceof RodItem) {
                RodData.setDepth(rodStack, depth);
                return true;
            }
        }
        return false;
    }

    public int componentSlotCount() {
        return slotTypes.length;
    }

    public ComponentSlot componentSlotType(int index) {
        return (index >= 0 && index < slotTypes.length) ? slotTypes[index] : null;
    }

    private void addPlayerInventory(Inventory inv) {
        // Shifted down to make room for the inline rig row (§rig-inline); screen height is 190.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 108 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, 8 + col * 18, 166));
        }
    }

    private void saveToRod() {
        if (player.level().isClientSide) return;
        for (int i = 0; i < slotTypes.length; i++) {
            RodData.set(rod, slotTypes[i], components.getItem(i));
        }
    }

    @Override
    public void removed(Player p) {
        saveToRod();
        super.removed(p);
    }

    @Override
    public boolean stillValid(Player p) {
        return p.getItemInHand(hand).getItem() instanceof RodItem;
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return result;

        ItemStack stack = slot.getItem();
        result = stack.copy();
        final int compCount = slotTypes.length;
        final int rigEnd = compCount + RIG_SLOTS;
        final int invEnd = slots.size();

        if (index < rigEnd) {
            // component or inline-rig slot -> inventory
            if (!moveItemStackTo(stack, rigEnd, invEnd, true)) return ItemStack.EMPTY;
        } else {
            // inventory -> matching component slot, else the first accepting inline rig slot.
            // Hooks are RodComponentItems too (slot HOOK), but the rod GUI has no HOOK column —
            // they belong INSIDE the rig, so they must fall through to the rig row (§rig-inline).
            boolean moved = false;
            if (stack.getItem() instanceof RodComponentItem rc) {
                for (int i = 0; i < slotTypes.length; i++) {
                    if (slotTypes[i] == rc.componentSlot()) {
                        // §tackle-compat: a shift-click has no red overlay, so if the part is incompatible
                        // (wrong reel size, or reel↔line mismatch) flash the reason INSIDE the rod window.
                        Component reason = rejectionReason(i, stack);
                        if (reason != null) {
                            if (p instanceof net.minecraft.server.level.ServerPlayer sp) {
                                com.riverfishing.network.ModNetwork.toPlayer(sp,
                                        new com.riverfishing.network.RodWarningPacket(reason));
                            }
                        } else {
                            moved = moveItemStackTo(stack, i, i + 1, false);
                        }
                        break;
                    }
                }
            }
            if (!moved) {
                com.riverfishing.rig.SlotRole[] roles = rigRoles();
                for (int i = 0; i < roles.length; i++) {
                    if (roles[i].accepts(stack)
                            && moveItemStackTo(stack, compCount + i, compCount + i + 1, false)) {
                        moved = true;
                        break;
                    }
                }
            }
            if (!moved) return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        saveToRod();
        saveRigContents();
        return result;
    }

    /**
     * Why the carried item cannot go in component slot {@code index} (Module 6), or null if it can or
     * simply belongs in a different slot. Used by the screen to draw a red overlay + reason tooltip.
     */
    @Nullable
    public Component rejectionReason(int index, ItemStack carried) {
        if (carried.isEmpty() || index < 0 || index >= slotTypes.length) return null;
        ComponentSlot type = slotTypes[index];
        if (!(carried.getItem() instanceof RodComponentItem rc) || rc.componentSlot() != type) {
            return null; // wrong slot entirely — not flagged as an "incompatibility"
        }
        if (type == ComponentSlot.REEL && carried.getItem() instanceof ReelItem reel
                && rod.getItem() instanceof RodItem rodItem) {
            RodType rt = rodItem.rodType();
            if (!rt.takesReel()) {
                return Component.translatable("validation.riverfishing.reel_none");
            }
            if (!rt.acceptsReelSize(reel.size())) {
                return Component.translatable("validation.riverfishing.reel_size");
            }
            // §tackle-compat: a small reel can't spool the thick line already fitted.
            double lineDia = installedLineDiameter();
            if (lineDia > 0 && !com.riverfishing.component.TackleCompat.reelAcceptsLine(reel.size(), lineDia)) {
                return Component.translatable("validation.riverfishing.reel_line");
            }
        }
        // §tackle-compat: line goes ON a reel — need a reel first (reeled rods), and it must fit the spool.
        if (type == ComponentSlot.LINE && carried.getItem() instanceof com.riverfishing.item.LineItem line
                && rod.getItem() instanceof RodItem rodItem && rodItem.rodType().takesReel()) {
            int reelSize = installedReelSize();
            if (reelSize <= 0) {
                return Component.translatable("validation.riverfishing.line_no_reel");
            }
            if (!com.riverfishing.component.TackleCompat.reelAcceptsLine(reelSize, line.diameterMm())) {
                return Component.translatable("validation.riverfishing.line_reel");
            }
        }
        return null;
    }

    /** The reel size currently in the reel slot (0 = none / reel-less rod). */
    private int installedReelSize() {
        for (int i = 0; i < slotTypes.length; i++) {
            if (slotTypes[i] == ComponentSlot.REEL && components.getItem(i).getItem() instanceof ReelItem r) {
                return r.size();
            }
        }
        return 0;
    }

    /** The diameter (mm) of the line currently in the line slot (0 = none). */
    private double installedLineDiameter() {
        for (int i = 0; i < slotTypes.length; i++) {
            if (slotTypes[i] == ComponentSlot.LINE && components.getItem(i).getItem() instanceof com.riverfishing.item.LineItem l) {
                return l.diameterMm();
            }
        }
        return 0;
    }

    /** A component slot that only accepts the matching tackle piece. */
    private class ComponentSlotView extends Slot {
        private final ComponentSlot slotType;

        ComponentSlotView(Container container, int index, int x, int y, ComponentSlot slotType) {
            super(container, index, x, y);
            this.slotType = slotType;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (!(stack.getItem() instanceof RodComponentItem rc) || rc.componentSlot() != slotType) {
                return false;
            }
            if (slotType == ComponentSlot.REEL && stack.getItem() instanceof ReelItem reel
                    && rod.getItem() instanceof RodItem rodItem) {
                if (!rodItem.rodType().acceptsReelSize(reel.size())) return false;
                // §tackle-compat: reject a reel too small for the already-fitted line.
                double lineDia = installedLineDiameter();
                return lineDia <= 0 || com.riverfishing.component.TackleCompat.reelAcceptsLine(reel.size(), lineDia);
            }
            // §tackle-compat: line goes ON a reel — a reeled rod needs its reel fitted first, and the line
            // must fit that reel's spool. (Reel-less rods spool line straight on the tip — always allowed.)
            if (slotType == ComponentSlot.LINE && stack.getItem() instanceof com.riverfishing.item.LineItem line
                    && rod.getItem() instanceof RodItem rodItem) {
                if (!rodItem.rodType().takesReel()) return true;
                int reelSize = installedReelSize();
                if (reelSize <= 0) return false; // no reel yet — nothing to spool onto
                return com.riverfishing.component.TackleCompat.reelAcceptsLine(reelSize, line.diameterMm());
            }
            return true;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            saveToRod();
            if (slotType == ComponentSlot.RIG) {
                loadRigContents(); // a different rig socketed (or removed) — swap the inline slots
            }
        }
    }
}
