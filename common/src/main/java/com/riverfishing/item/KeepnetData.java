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
        KeepnetTier tier = net.getItem() instanceof KeepnetItem k ? k.tier() : KeepnetTier.SMALL;
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
        return isFish(stack) && fits(occupied(), FishShape.of(stack).rotated(rot), px, py);
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
     * Find the best place for this stack and put it there.
     *
     * <p>"Best" is not "first that fits". First-fit reads left to right and drops a piece in the first
     * hole big enough, which strands single cells all over a box — and in a box where the whole point is
     * deciding what to keep, wasted cells are the mechanic misfiring. Every legal position in both
     * rotations is scored by how much of the piece's edge ends up against a wall or another fish, and the
     * most contact wins; ties go to the highest, then leftmost. That is the standard maximal-contact
     * heuristic and it costs nothing at this size.
     */
    public boolean autoPlace(ItemStack stack) {
        if (stack.isEmpty() || !isFish(stack)) return false;
        boolean[] used = occupied();
        Spot best = bestSpot(stack, used);
        if (best == null) return false;
        items.add(new Placed(stack.copy(), best.x, best.y, best.rot));
        return true;
    }

    /**
     * Repack from scratch, biggest and most awkward first. This is the tidy-up button, and it exists
     * because the designer this borrows from was explicit that a spatial inventory without one is a
     * frustration generator.
     *
     * <p>It keeps ONE occupancy grid and updates it as it goes, instead of rebuilding the map from the
     * item list on every trial placement — the first cut did that thousands of times per press.
     */
    public void repack() {
        List<ItemStack> all = new ArrayList<>();
        for (Placed p : items) all.add(p.stack());
        // Area first, then the longest side: a 6x1 eel is small but far harder to fit than a 2x2 bream,
        // and packing the awkward pieces while the box is empty is the whole trick.
        all.sort((a, b) -> {
            FishShape sa = FishShape.of(a), sb = FishShape.of(b);
            int byArea = Integer.compare(sb.cells(), sa.cells());
            return byArea != 0 ? byArea
                    : Integer.compare(Math.max(sb.width(), sb.height()), Math.max(sa.width(), sa.height()));
        });
        items.clear();
        boolean[] used = new boolean[tier.width() * tier.height()];
        List<ItemStack> over = new ArrayList<>();
        for (ItemStack st : all) {
            Spot spot = bestSpot(st, used);
            if (spot == null) {
                over.add(st);
                continue;
            }
            FishShape sh = FishShape.of(st).rotated(spot.rot);
            mark(used, sh, spot.x, spot.y);
            items.add(new Placed(st, spot.x, spot.y, spot.rot));
        }
        // Anything that no longer fits after a repack goes back to the player, never nowhere.
        this.spilled = over;
    }

    private record Spot(int x, int y, int rot, int contact) {}

    /** The placement with the most edge against a wall or another fish; ties to the top-left. */
    private Spot bestSpot(ItemStack stack, boolean[] used) {
        FishShape base = FishShape.of(stack);
        int rots = base.width() == base.height() ? 1 : 2;
        Spot best = null;
        for (int rot = 0; rot < rots; rot++) {
            FishShape sh = base.rotated(rot);
            for (int y = 0; y + sh.height() <= tier.height(); y++) {
                for (int x = 0; x + sh.width() <= tier.width(); x++) {
                    if (!fits(used, sh, x, y)) continue;
                    int c = contact(used, sh, x, y);
                    if (best == null || c > best.contact()) best = new Spot(x, y, rot, c);
                }
            }
        }
        return best;
    }

    private boolean fits(boolean[] used, FishShape sh, int px, int py) {
        for (int y = 0; y < sh.height(); y++) {
            for (int x = 0; x < sh.width(); x++) {
                if (!sh.at(x, y)) continue;
                int gx = px + x, gy = py + y;
                if (gx < 0 || gy < 0 || gx >= tier.width() || gy >= tier.height()) return false;
                if (used[gy * tier.width() + gx]) return false;
            }
        }
        return true;
    }

    /** How many of the piece's cell edges land against a wall or something already in the box. */
    private int contact(boolean[] used, FishShape sh, int px, int py) {
        int n = 0;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int y = 0; y < sh.height(); y++) {
            for (int x = 0; x < sh.width(); x++) {
                if (!sh.at(x, y)) continue;
                for (int[] d : dirs) {
                    int sx = x + d[0], sy = y + d[1];
                    if (sx >= 0 && sy >= 0 && sx < sh.width() && sy < sh.height() && sh.at(sx, sy)) {
                        continue;   // its own body, not contact
                    }
                    int gx = px + x + d[0], gy = py + y + d[1];
                    if (gx < 0 || gy < 0 || gx >= tier.width() || gy >= tier.height()
                            || used[gy * tier.width() + gx]) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    private void mark(boolean[] used, FishShape sh, int px, int py) {
        for (int y = 0; y < sh.height(); y++) {
            for (int x = 0; x < sh.width(); x++) {
                if (sh.at(x, y)) used[(py + y) * tier.width() + px + x] = true;
            }
        }
    }

    /** The occupancy of the box as flags, built once by whoever needs it. */
    private boolean[] occupied() {
        boolean[] used = new boolean[tier.width() * tier.height()];
        for (Placed p : items) mark(used, p.shape(), p.x(), p.y());
        return used;
    }

    private List<ItemStack> spilled = List.of();

    /** What a {@link #repack()} could not fit back in. Empty in every normal case. */
    public List<ItemStack> spilled() { return spilled; }

    /**
     * A keepnet holds this mod's fish and nothing else — not another mod's fish, not a pickaxe. Everything
     * that decides what may go in asks here.
     */
    public static boolean isFish(ItemStack stack) {
        return stack.getItem() instanceof FishItem;
    }

    /** How full the box is, 0..1 — for the label on the screen and the tooltip. */
    public double fullness() {
        int used = 0;
        for (Placed p : items) used += p.shape().cells();
        int water = tier.cells();
        return water <= 0 ? 0 : Math.min(1.0, used / (double) water);
    }
}
