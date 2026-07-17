package com.riverfishing.rig;

import com.riverfishing.component.RigType;
import com.riverfishing.item.BaitItem;
import com.riverfishing.item.GroundbaitItem;
import com.riverfishing.item.HookItem;
import com.riverfishing.item.LeaderItem;
import com.riverfishing.item.RigItem;
import com.riverfishing.item.StackNbt;
import com.riverfishing.util.RegistryHelper;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads/writes a rig's internal inventory (hooks, baits, groundbait, float, leader, lure) stored in
 * the rig {@link ItemStack}'s NBT (Module 4). This travels with the rig even when it is slotted into
 * a rod, so the bite engine reads tackle straight out of the rig.
 */
public final class RigData {
    private static final String ROOT = "RigContents";
    private static final String ITEMS = "Items";
    private static final String SLOT = "Slot";

    private RigData() {}

    public static RigType rigType(ItemStack rig) {
        return rig.getItem() instanceof RigItem r ? r.rigType() : RigType.PRIMITIVE;
    }

    public static int slotCount(RigType type) {
        return RigLayout.rolesFor(type).length;
    }

    public static NonNullList<ItemStack> load(ItemStack rig) {
        NonNullList<ItemStack> list = NonNullList.withSize(slotCount(rigType(rig)), ItemStack.EMPTY);
        CompoundTag tag = StackNbt.get(rig);
        if (tag.contains(ROOT)) {
            ListTag items = tag.getCompound(ROOT).getList(ITEMS, Tag.TAG_COMPOUND);
            var provider = RegistryHelper.provider();
            for (int i = 0; i < items.size(); i++) {
                CompoundTag c = items.getCompound(i);
                int slot = c.getByte(SLOT) & 255;
                if (slot < list.size()) {
                    // §data-components (1.21): the item lives in its own "Item" sub-tag — the 1.21 ItemStack
                    // codec is strict and won't parse a tag that also carries our "Slot" byte.
                    list.set(slot, ItemStack.parseOptional(provider, c.getCompound("Item")));
                }
            }
        }
        return list;
    }

    public static void save(ItemStack rig, NonNullList<ItemStack> contents) {
        var provider = RegistryHelper.provider();
        ListTag items = new ListTag();
        for (int i = 0; i < contents.size(); i++) {
            ItemStack stack = contents.get(i);
            if (!stack.isEmpty()) {
                CompoundTag c = new CompoundTag();
                c.putByte(SLOT, (byte) i);
                c.put("Item", stack.save(provider, new CompoundTag()));
                items.add(c);
            }
        }
        CompoundTag root = new CompoundTag();
        root.put(ITEMS, items);
        StackNbt.mutate(rig, tag -> tag.put(ROOT, root));
    }

    // ---- queries for the bite engine ----

    /** Hook sizes loaded in the rig; the engine picks the best fit per fish. */
    public static List<Integer> hookSizes(ItemStack rig) {
        List<Integer> sizes = new ArrayList<>();
        forEachFilled(rig, (role, stack) -> {
            if (role == SlotRole.HOOK && stack.getItem() instanceof HookItem h) {
                sizes.add(h.hookSize());
            }
        });
        return sizes;
    }

    /** Bait ids loaded on the hooks / lure slots; the engine picks the best per fish. */
    public static List<String> baitIds(ItemStack rig) {
        List<String> baits = new ArrayList<>();
        forEachFilled(rig, (role, stack) -> {
            if ((role == SlotRole.BAIT || role == SlotRole.LURE) && stack.getItem() instanceof BaitItem b) {
                baits.add(b.baitId());
            }
        });
        return baits;
    }

    /** §lure-color: the dyed RGB of an artificial lure loaded in a lure/bait slot, or -1 if none/undyed. */
    public static int lureColorRgb(ItemStack rig) {
        int[] found = { -1 };
        forEachFilled(rig, (role, stack) -> {
            if (found[0] < 0 && (role == SlotRole.LURE || role == SlotRole.BAIT)
                    && stack.getItem() instanceof BaitItem b && b.artificial()) {
                net.minecraft.world.item.component.DyedItemColor dc =
                        stack.get(net.minecraft.core.component.DataComponents.DYED_COLOR);
                if (dc != null) found[0] = dc.rgb();
            }
        });
        return found[0];
    }

    /** livebait-2: weight of a live baitfish loaded in a BAIT slot, or 0 (drives predator size, 0.4.0). */
    public static int livebaitWeightG(ItemStack rig) {
        int[] found = { 0 };
        forEachFilled(rig, (role, stack) -> {
            if (found[0] == 0 && role == SlotRole.BAIT && stack.getItem() instanceof BaitItem b
                    && "livebait".equals(b.baitId())) {
                found[0] = StackNbt.get(stack).getInt(com.riverfishing.item.FishItem.TAG_BAIT_WEIGHT);
            }
        });
        return found[0];
    }

    /** Groundbait category loaded in the feeder/flat/grusha cage, or null. */
    public static String groundbaitCategory(ItemStack rig) {
        String[] found = new String[1];
        forEachFilled(rig, (role, stack) -> {
            if (found[0] == null && role == SlotRole.GROUNDBAIT && stack.getItem() instanceof GroundbaitItem g) {
                found[0] = g.category();
            }
        });
        return found[0];
    }

    public static boolean hasLeader(ItemStack rig) {
        boolean[] found = new boolean[1];
        forEachFilled(rig, (role, stack) -> {
            if (role == SlotRole.LEADER) found[0] = true;
        });
        return found[0];
    }

    /**
     * Consumes one groundbait from the rig's feeder cage (§consumables): each cast delivers a charge
     * to the spot. Returns the consumed category, or null when the cage is empty.
     */
    public static String consumeGroundbait(ItemStack rig) {
        SlotRole[] roles = RigLayout.rolesFor(rigType(rig));
        NonNullList<ItemStack> inv = load(rig);
        for (int i = 0; i < roles.length && i < inv.size(); i++) {
            ItemStack s = inv.get(i);
            if (roles[i] == SlotRole.GROUNDBAIT && !s.isEmpty() && s.getItem() instanceof GroundbaitItem g) {
                s.shrink(1);
                save(rig, inv);
                return g.category();
            }
        }
        return null;
    }

    /**
     * Consumes one NATURAL bait (§consumables) — the fish ate it on the strike. Artificial lures are
     * never consumed, and neither is the mormyshka (§ice-fishing): it's a metal jig that merely sits in
     * the BAIT slot, not something a fish can eat. Returns true when something was eaten.
     */
    public static boolean consumeBait(ItemStack rig) {
        SlotRole[] roles = RigLayout.rolesFor(rigType(rig));
        NonNullList<ItemStack> inv = load(rig);
        for (int i = 0; i < roles.length && i < inv.size(); i++) {
            ItemStack s = inv.get(i);
            if (roles[i] == SlotRole.BAIT && !s.isEmpty()
                    && s.getItem() instanceof BaitItem b && !b.artificial()
                    && !"mormyshka".equals(b.baitId())) {
                s.shrink(1);
                save(rig, inv);
                return true;
            }
        }
        return false;
    }

    /** True when a float is actually loaded in the rig's FLOAT slot (§fishing-depth). */
    public static boolean hasFloat(ItemStack rig) {
        boolean[] found = new boolean[1];
        forEachFilled(rig, (role, stack) -> {
            if (role == SlotRole.FLOAT) found[0] = true;
        });
        return found[0];
    }

    /** Bite-through protection of the fitted leader (0 = none). */
    public static double leaderProtection(ItemStack rig) {
        double[] best = new double[1];
        forEachFilled(rig, (role, stack) -> {
            if (role == SlotRole.LEADER && stack.getItem() instanceof LeaderItem l) {
                best[0] = Math.max(best[0], l.protection());
            }
        });
        return best[0];
    }

    /** Stealth of the fitted leader (0 = none). */
    public static double leaderStealth(ItemStack rig) {
        double[] best = new double[1];
        forEachFilled(rig, (role, stack) -> {
            if (role == SlotRole.LEADER && stack.getItem() instanceof LeaderItem l) {
                best[0] = Math.max(best[0], l.stealth());
            }
        });
        return best[0];
    }

    private interface SlotConsumer {
        void accept(SlotRole role, ItemStack stack);
    }

    private static void forEachFilled(ItemStack rig, SlotConsumer consumer) {
        SlotRole[] roles = RigLayout.rolesFor(rigType(rig));
        NonNullList<ItemStack> contents = load(rig);
        for (int i = 0; i < roles.length && i < contents.size(); i++) {
            ItemStack stack = contents.get(i);
            if (!stack.isEmpty()) {
                consumer.accept(roles[i], stack);
            }
        }
    }
}
