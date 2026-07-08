package com.riverfishing.item;

import com.riverfishing.RiverFishing;
import com.riverfishing.component.ComponentSlot;
import com.riverfishing.component.RigType;
import com.riverfishing.component.RodType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Reads/writes the reel, line, rig and hook stored inside an assembled rod's NBT (§3.1, §12).
 * The rod item itself carries the {@link com.riverfishing.component.RodType}.
 */
public final class RodData {
    public static final String ROOT = "RodComponents";

    private RodData() {}

    private static String key(ComponentSlot slot) {
        return switch (slot) {
            case REEL -> "Reel";
            case LINE -> "Line";
            case RIG -> "Rig";
            case HOOK -> "Hook";
        };
    }

    public static ItemStack get(ItemStack rod, ComponentSlot slot) {
        CompoundTag tag = rod.getTag();
        if (tag == null || !tag.contains(ROOT)) return ItemStack.EMPTY;
        CompoundTag root = tag.getCompound(ROOT);
        String k = key(slot);
        if (!root.contains(k)) return ItemStack.EMPTY;
        return ItemStack.of(root.getCompound(k));
    }

    public static void set(ItemStack rod, ComponentSlot slot, ItemStack component) {
        CompoundTag tag = rod.getOrCreateTag();
        CompoundTag root = tag.getCompound(ROOT);
        String k = key(slot);
        if (component.isEmpty()) {
            root.remove(k);
        } else {
            root.put(k, component.save(new CompoundTag()));
        }
        tag.put(ROOT, root);
    }

    /**
     * A rod can fish once it has a line and a rig (Module 4: hooks live inside the rig now, and an
     * empty hook only lowers the bite chance, it doesn't block casting).
     */
    public static boolean isAssembled(ItemStack rod) {
        return !get(rod, ComponentSlot.LINE).isEmpty()
                && !get(rod, ComponentSlot.RIG).isEmpty();
    }

    /** Float depth setting stored on the ROD ("спуск", §fishing-depth): surface / mid / bottom. */
    public static String getDepth(ItemStack rod) {
        String d = rod.getTag() != null ? rod.getTag().getString("SetDepth") : "";
        return d.isEmpty() ? "mid" : d;
    }

    public static void setDepth(ItemStack rod, String depth) {
        rod.getOrCreateTag().putString("SetDepth", depth);
    }

    /**
     * Installs the rod's built-in rig into the RIG slot if it isn't already there (§closed-slots). Float
     * and lure rods own one native rig instead of a swappable one, so a freshly crafted or trade-bought
     * rod (or one whose rig was snapped off) gets it here. A rod that already holds the correct rig type
     * is left untouched, so its hooks/baits/leader survive. Rods with no native rig (bottom) are ignored.
     */
    public static void ensureNativeRig(ItemStack rod, RodType type) {
        RigType native_ = type.nativeRig();
        if (native_ == null) return;
        ItemStack rig = get(rod, ComponentSlot.RIG);
        if (rig.getItem() instanceof com.riverfishing.item.RigItem ri && ri.rigType() == native_) {
            return; // already the native rig — keep its contents
        }
        Item rigItem = BuiltInRegistries.ITEM.get(RiverFishing.id("rig_" + native_.jsonKey()));
        set(rod, ComponentSlot.RIG, rigItem != null ? new ItemStack(rigItem) : ItemStack.EMPTY);
    }
}
