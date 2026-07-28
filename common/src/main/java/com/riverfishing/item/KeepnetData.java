package com.riverfishing.item;

import com.riverfishing.fish.FishShape;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * §keepnet (0.7.0): what is in the box and where, stored in the keepnet's own NBT.
 *
 * <p>The grid is not slots. A fish occupies several cells at once — a shape from {@link FishShape} — so
 * the contents are a list of placements and the occupancy map is rebuilt from them whenever anything is
 * asked. That is cheap at this size (a few dozen cells) and it means there is one source of truth: the
 * list. A cached grid that disagreed with the list would be a whole class of bug for nothing.
 *
 * <p>Everything here is pure model. Nothing knows about a screen, and every operation returns whether it
 * worked rather than assuming — the caller decides what to tell the player.
 */
public final class KeepnetData {
    public static final String TAG_ITEMS = "Net";

    /** One placed thing: the stack, where its shape's top-left corner sits, and whether it is turned. */
    public record Placed(ItemStack stack, int x, int y, int rot) {
        public FishShape shape() {
            return FishShape.of(stack).rotated(rot);
        }
    }

    private final KeepnetTier tier;
    private final List<Placed> items;

    private KeepnetData(KeepnetTier tier, List<Placed> items) {
        this.tier = tier;
        this.items = items;
    }

    public KeepnetTier tier() { return tier; }

    public List<Placed> items() { return items; }

    public static KeepnetData read(ItemStack net) {
        KeepnetTier tier = net.getItem() instanceof KeepnetItem k ? k.tier() : KeepnetTier.WICKER;
        List<Placed> out = new ArrayList<>();
        ListTag list = StackNbt.get(net).getList(TAG_ITEMS, 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            ItemStack s = ItemStack.parseOptional(
                    com.riverfishing.util.RegistryHelper.provider(), t.getCompound("i"));
            if (!s.isEmpty()) out.add(new Placed(s, t.getInt("x"), t.getInt("y"), t.getInt("r")));
        }
        return new KeepnetData(tier, out);
    }

    public void write(ItemStack net) {
        ListTag list = new ListTag();
        for (Placed p : items) {
            CompoundTag t = new CompoundTag();
            t.putInt("x", p.x());
            t.putInt("y", p.y());
            t.putInt("r", p.rot());
            t.put("i", p.stack().save(com.riverfishing.util.RegistryHelper.provider(), new CompoundTag()));
            list.add(t);
        }
        StackNbt.mutate(net, tag -> tag.put(TAG_ITEMS, list));
    }

    // ---- geometry -------------------------------------------------------------------------------

    /** The occupancy map, rebuilt from the list. index = y * width + x, or -1 where nothing is. */
    public int[] occupancy() {
        int[] grid = new int[tier.width() * tier.height()];
        java.util.Arrays.fill(grid, -1);
        for (int i = 0; i < items.size(); i++) {
            Placed p = items.get(i);
            FishShape s = p.shape();
            for (int y = 0; y < s.height(); y++) {
                for (int x = 0; x < s.width(); x++) {
                    if (!s.at(x, y)) continue;
                    int gx = p.x() + x, gy = p.y() + y;
                    if (gx >= 0 && gy >= 0 && gx < tier.width() && gy < tier.height()) {
                        grid[gy * tier.width() + gx] = i;
                    }
                }
            }
        }
        return grid;
    }

    /** Which placed item covers this cell, or -1. */
    public int at(int x, int y) {
        if (x < 0 || y < 0 || x >= tier.width() || y >= tier.height()) return -1;
        return occupancy()[y * tier.width() + x];
    }

    /** Can this stack sit here, in this rotation, without leaving the box or touching anything? */
    public boolean fits(ItemStack stack, int px, int py, int rot) {
        boolean fish = isFish(stack);
        FishShape s = FishShape.of(stack).rotated(rot);
        int[] grid = occupancy();
        for (int y = 0; y < s.height(); y++) {
            for (int x = 0; x < s.width(); x++) {
                if (!s.at(x, y)) continue;
                int gx = px + x, gy = py + y;
                if (gx < 0 || gy < 0 || gx >= tier.width() || gy >= tier.height()) return false;
                // The gear columns are for what you carry, not what you caught — and a fish is never
                // allowed to spill into them, whichever way round it is turned.
                if (fish && tier.isGearCell(gx)) return false;
                if (!fish && !tier.isGearCell(gx)) return false;
                if (grid[gy * tier.width() + gx] != -1) return false;
            }
        }
        return true;
    }

    public boolean place(ItemStack stack, int x, int y, int rot) {
        if (stack.isEmpty() || !fits(stack, x, y, rot)) return false;
        items.add(new Placed(stack.copy(), x, y, rot));
        return true;
    }

    /** Take out whatever covers this cell. Returns it, or empty. */
    public ItemStack take(int x, int y) {
        int i = at(x, y);
        if (i < 0) return ItemStack.EMPTY;
        return items.remove(i).stack();
    }

    /**
     * Find somewhere for this stack and put it there. Tries both rotations at every cell, reading
     * left-to-right and top-to-bottom, so the box packs from the corner the way a person would fill it.
     *
     * @return true if it went in
     */
    public boolean autoPlace(ItemStack stack) {
        if (stack.isEmpty()) return false;
        int rots = FishShape.of(stack).width() == FishShape.of(stack).height() ? 1 : 2;
        for (int y = 0; y < tier.height(); y++) {
            for (int x = 0; x < tier.width(); x++) {
                for (int rot = 0; rot < rots; rot++) {
                    if (fits(stack, x, y, rot)) {
                        items.add(new Placed(stack.copy(), x, y, rot));
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Repack everything from scratch, biggest first. This is the tidy-up button, and it exists because
     * the developer of the game this is borrowed from was explicit that a spatial inventory without one
     * is a frustration generator.
     */
    public void repack() {
        List<ItemStack> all = new ArrayList<>();
        for (Placed p : items) all.add(p.stack());
        all.sort((a, b) -> Integer.compare(FishShape.of(b).cells(), FishShape.of(a).cells()));
        items.clear();
        List<ItemStack> spilled = new ArrayList<>();
        for (ItemStack s : all) {
            if (!autoPlace(s)) spilled.add(s);
        }
        // Anything that no longer fits after a repack has to go back to the player, not vanish.
        this.spilled = spilled;
    }

    private List<ItemStack> spilled = List.of();

    /** What a {@link #repack()} could not fit back in. Empty in every normal case. */
    public List<ItemStack> spilled() { return spilled; }

    public static boolean isFish(ItemStack stack) {
        return stack.getItem() instanceof FishItem;
    }

    /** How full the box is, 0..1 — for the label on the screen and the tooltip. */
    public double fullness() {
        int used = 0;
        for (Placed p : items) used += p.shape().cells();
        int water = tier.width() * tier.height();
        return water <= 0 ? 0 : Math.min(1.0, used / (double) water);
    }
}
