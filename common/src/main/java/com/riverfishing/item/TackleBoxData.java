package com.riverfishing.item;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;

/**
 * §tackle-box (0.7.0): what is in the box, stored in the box's own NBT.
 *
 * <p>Flat slots, not the keepnet's tetris grid — tackle is small and interchangeable, and a spatial
 * puzzle for hooks would be busywork with none of the meaning it has for a netful of fish.
 *
 * <p>{@link #isTackle} is the ONE definition of what may go in. Every gate — the slot, shift-click, the
 * hopper, the villager's ready-made sets — asks this function, because the last container in this mod
 * grew three different opinions about what a fish was and they disagreed.
 */
public final class TackleBoxData {
    public static final String TAG_ITEMS = "Box";

    private TackleBoxData() {}

    /**
     * Tackle, as the request defined it: line, hooks, rigs, lures and baits.
     *
     * <p>Deliberately NOT rods and reels — those are the things you hold, not the things you rummage for,
     * and a box that swallowed a rod would be a backpack wearing a tackle box's name. A leader counts: it
     * is a length of line with a swivel on it.
     */
    public static boolean isTackle(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getItem() instanceof LineItem
                || stack.getItem() instanceof HookItem
                || stack.getItem() instanceof RigItem
                || stack.getItem() instanceof BaitItem      // both live baits and artificial lures
                || stack.getItem() instanceof LeaderItem
                || stack.is(TACKLE);
    }

    /**
     * The odd ones out: things that are obviously tackle but are not one of the classes above — a float is
     * a plain {@code Item} with no behaviour of its own, and so is anything a pack adds. The classes carry
     * the families; the tag carries the exceptions and the extension point.
     */
    public static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> TACKLE =
            net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                    com.riverfishing.RiverFishing.id("tackle"));

    public static TackleBoxTier tierOf(ItemStack box) {
        return box.getItem() instanceof TackleBoxItem b ? b.tier() : TackleBoxTier.SMALL;
    }

    /** The box's contents, always exactly the tier's slot count long. */
    public static NonNullList<ItemStack> load(ItemStack box) {
        int size = tierOf(box).slots();
        NonNullList<ItemStack> out = NonNullList.withSize(size, ItemStack.EMPTY);
        ListTag list = StackNbt.get(box).getList(TAG_ITEMS, 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            int slot = t.getInt("s");
            if (slot < 0 || slot >= size) continue;   // a downgraded box drops what no longer fits
            ItemStack s = ItemStack.parseOptional(
                    com.riverfishing.util.RegistryHelper.provider(), t.getCompound("i"));
            if (!s.isEmpty()) out.set(slot, s);
        }
        return out;
    }

    public static void save(ItemStack box, NonNullList<ItemStack> contents) {
        ListTag list = new ListTag();
        for (int i = 0; i < contents.size(); i++) {
            ItemStack s = contents.get(i);
            if (s.isEmpty()) continue;
            CompoundTag t = new CompoundTag();
            t.putInt("s", i);
            t.put("i", s.save(com.riverfishing.util.RegistryHelper.provider(), new CompoundTag()));
            list.add(t);
        }
        StackNbt.mutate(box, tag -> {
            if (list.isEmpty()) {
                tag.remove(TAG_ITEMS);
            } else {
                tag.put(TAG_ITEMS, list);
            }
        });
    }

    /** How many slots are taken — the tooltip's whole story. */
    public static int used(ItemStack box) {
        return StackNbt.get(box).getList(TAG_ITEMS, 10).size();
    }

    public static boolean isEmpty(ItemStack box) {
        return used(box) == 0;
    }
}
