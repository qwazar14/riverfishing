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
        CompoundTag tag = StackNbt.get(rod);
        if (!tag.contains(ROOT)) return ItemStack.EMPTY;
        CompoundTag root = tag.getCompoundOrEmpty(ROOT);
        String k = key(slot);
        if (!root.contains(k)) return ItemStack.EMPTY;
        return com.riverfishing.util.RegistryHelper.loadStack(root.getCompoundOrEmpty(k));
    }

    public static void set(ItemStack rod, ComponentSlot slot, ItemStack component) {
        StackNbt.mutate(rod, tag -> {
            CompoundTag root = tag.getCompoundOrEmpty(ROOT);
            String k = key(slot);
            if (component.isEmpty()) {
                root.remove(k);
            } else {
                root.put(k, com.riverfishing.util.RegistryHelper.saveStack(component));
            }
            tag.put(ROOT, root);
        });
        refreshIconLayers(rod);
    }

    /**
     * §26.1 §rod-layers: the composited rod icon is data-driven now — the client item definition
     * SELECTs overlay layers on custom_model_data STRINGS (0 = reel sprite, 1 = line type,
     * 2 = rig sprite). Kept in sync here on every component change (26.1 has no item-model
     * condition that can look inside custom_data).
     */
    public static void refreshIconLayers(ItemStack rod) {
        String reel = "", line = "", rig = "";
        if (get(rod, ComponentSlot.REEL).getItem() instanceof ReelItem ri) {
            reel = "reel_" + ri.size();
        }
        if (get(rod, ComponentSlot.LINE).getItem() instanceof LineItem li) {
            line = li.lineType().jsonKey();
        }
        if (get(rod, ComponentSlot.RIG).getItem() instanceof RigItem gi) {
            rig = rigSprite(gi.rigType());
        }
        rod.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                new net.minecraft.world.item.component.CustomModelData(
                        java.util.List.of(), java.util.List.of(),
                        java.util.List.of(reel, line, rig), java.util.List.of()));
    }

    /** Overlay sprite for a rig type — float_light/winter have no own glyph, nearest look-alike. */
    private static String rigSprite(RigType type) {
        return switch (type.jsonKey()) {
            case "float_light" -> "rig_float";
            case "winter" -> "rig_primitive";
            default -> "rig_" + type.jsonKey();
        };
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
        String d = StackNbt.get(rod).getStringOr("SetDepth", "");
        return d.isEmpty() ? "mid" : d;
    }

    public static void setDepth(ItemStack rod, String depth) {
        StackNbt.mutate(rod, tag -> tag.putString("SetDepth", depth));
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
        Item rigItem = BuiltInRegistries.ITEM.getValue(RiverFishing.id("rig_" + native_.jsonKey()));
        set(rod, ComponentSlot.RIG, rigItem != null ? new ItemStack(rigItem) : ItemStack.EMPTY);
    }
}
