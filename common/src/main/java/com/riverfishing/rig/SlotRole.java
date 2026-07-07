package com.riverfishing.rig;

import com.riverfishing.item.BaitItem;
import com.riverfishing.item.GroundbaitItem;
import com.riverfishing.item.HookItem;
import com.riverfishing.item.LeaderItem;
import com.riverfishing.registry.ModItems;
import net.minecraft.world.item.ItemStack;

/** The kind of tackle a rig slot accepts (Module 4). */
public enum SlotRole {
    HOOK("hook"),
    BAIT("bait"),
    GROUNDBAIT("groundbait"),
    FLOAT("float"),
    LEADER("leader"),
    LURE("lure");

    private final String key;

    SlotRole(String key) {
        this.key = key;
    }

    public String langKey() {
        return "rig.riverfishing.role." + key;
    }

    public boolean accepts(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return switch (this) {
            case HOOK -> stack.getItem() instanceof HookItem;
            case BAIT -> stack.getItem() instanceof BaitItem b && !b.artificial();
            case GROUNDBAIT -> stack.getItem() instanceof GroundbaitItem;
            case FLOAT -> stack.getItem() == ModItems.FLOAT.get();
            case LEADER -> stack.getItem() instanceof LeaderItem;
            // A predator lure slot takes an artificial lure or a live bait.
            case LURE -> stack.getItem() instanceof BaitItem b && (b.artificial() || b.baitId().equals("livebait"));
        };
    }
}
