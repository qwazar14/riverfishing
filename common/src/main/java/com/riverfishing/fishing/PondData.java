package com.riverfishing.fishing;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * §pond §breeding (0.9.0): the private ponds — which water bodies a player has CLAIMED with a
 * {@link com.riverfishing.block.PondSignBlock}, per dimension.
 *
 * <p>Why this exists: the community hash treats every water block in the world as natural water. Dig a
 * pit, fill it, and the seed hands you rotan and crucian you never wanted, the habitat gates call your
 * two-deep pit "unsuitable" for the fry you bought, and your own seine calls you a poacher for netting
 * what you stocked. A claim draws the line the simulation could not see: inside it there is no wild
 * community, no depth/width gate, and the owner's net is legal.
 *
 * <p>A claim IS the set of water blocks the sign found — flood-filled once, at placement, and stored as
 * packed positions. That is the stable identity the water body never had ({@code WaterBody} is a
 * classification, not a thing with an id): membership is one map lookup per bite, and "is this the
 * same pond" never has to be re-derived from a flood that could come out differently tomorrow.
 *
 * <p>ponytail: the claim is frozen at placement. Dig the pond bigger and the new blocks are wild until
 * the sign is re-placed; a heal-on-read that re-floods when a claimed block borders unclaimed water is
 * the upgrade if that ever bites.
 */
public final class PondData extends SavedData {
    private static final String NAME = "riverfishing_ponds";
    /**
     * Biggest body a sign may claim, as SURFACE — distinct x/z columns of water, whatever the depth
     * (§pond-columns: 100×100; the count was 600 then 2 500 blocks and a deep pond paid for its depth,
     * which is the one thing a farm pond should be allowed to have). A lake or a river still refuses.
     */
    public static final int MAX_BLOCKS = 10_000;
    /** How far from the sign the water may be. */
    public static final int REACH = 3;

    public static final class Claim {
        public final long sign;
        public final UUID owner;
        public final String ownerName;
        /** §pond-name: what the owner called it on the sign; "" until they do. */
        public String name = "";
        /** §pond-columns: packed x/z columns ({@link #column}); a column is the pond's at every depth. */
        public final long[] water;

        Claim(long sign, UUID owner, String ownerName, long[] water) {
            this.sign = sign;
            this.owner = owner;
            this.ownerName = ownerName;
            this.water = water;
        }

        public int size() {
            return water.length;
        }

        private Set<Long> set;

        public boolean holds(long packed) {
            if (set == null) { set = new HashSet<>(); for (long w : water) set.add(w); }
            return set.contains(packed);
        }
    }

    private final Map<Long, Claim> bySign = new HashMap<>();
    private final Map<Long, Claim> byWater = new HashMap<>();

    public static PondData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(PondData::load, PondData::new, NAME);
    }

    // ---- the questions the simulation asks --------------------------------------------------------

    /** Is this water somebody's pond? Also true one block above the surface, where a thrown fish lands. */
    public static boolean isClaimed(ServerLevel level, BlockPos pos) {
        return get(level).claimAt(pos) != null;
    }

    @Nullable
    public static UUID owner(ServerLevel level, BlockPos pos) {
        Claim c = get(level).claimAt(pos);
        return c == null ? null : c.owner;
    }

    /** The owner's name for messages and the finder; "" when unclaimed. Stored at claim time: no profile lookup. */
    public static String ownerName(ServerLevel level, BlockPos pos) {
        Claim c = get(level).claimAt(pos);
        return c == null ? "" : c.ownerName;
    }

    /**
     * §n §breeding: are these two spots the same water, as far as a claim can tell — the same pond, or
     * both wild? The fry trap asks it: the brood ledger is keyed by a ~128-block region, which happily
     * holds two ponds and a river, and the claim is the only line between them that a player drew
     * himself. Identity comparison: there is exactly one Claim object per claim, and null means wild.
     */
    public static boolean sameWater(ServerLevel level, BlockPos a, BlockPos b) {
        PondData d = get(level);
        return d.claimAt(a) == d.claimAt(b);
    }

    /** §pond-ledger: the claim under a water block, or null for wild water. */
    @Nullable
    public static Claim claim(ServerLevel level, BlockPos pos) {
        return get(level).claimAt(pos);
    }

    /** §pond-ledger: the claims whose sign stands within {@code r} blocks of a spot — the per-player tick's list. */
    public static List<Claim> near(ServerLevel level, BlockPos pos, int r) {
        List<Claim> out = new ArrayList<>();
        for (Claim c : get(level).bySign.values()) {
            BlockPos s = BlockPos.of(c.sign);
            if (Math.abs(s.getX() - pos.getX()) <= r && Math.abs(s.getZ() - pos.getZ()) <= r) out.add(c);
        }
        return out;
    }

    /** §pond-name: the claim a sign stands for, or null when the sign has none. */
    @Nullable
    public Claim bySign(BlockPos sign) {
        return bySign.get(sign.asLong());
    }

    /** §pond-name: the owner writes the name; nothing else about the claim moves. */
    public void rename(BlockPos sign, String name) {
        Claim c = bySign.get(sign.asLong());
        if (c == null) return;
        c.name = name;
        setDirty();
    }

    /** §pond-one-sign: the claim a packed column belongs to, or null. */
    @Nullable
    public Claim claimOfColumn(long column) {
        return byWater.get(column);
    }

    @Nullable
    private Claim claimAt(BlockPos pos) {
        return byWater.get(column(pos));
    }

    /** §pond-columns: the x/z column of a block, packed — the unit a claim is made of. */
    public static long column(BlockPos pos) {
        return (((long) pos.getX()) << 32) ^ (pos.getZ() & 0xFFFFFFFFL);
    }

    public static BlockPos columnPos(long column) {
        return new BlockPos((int) (column >> 32), 0, (int) column);
    }

    // ---- the sign's verbs ------------------------------------------------------------------------

    /**
     * The water body nearest the sign, as packed COLUMNS, or null when no water is within {@link #REACH}.
     * Flood-filled six ways through the water, but only the footprint is counted — the cap is one past
     * {@link #MAX_BLOCKS} columns, so "too big" costs a lake's surface and not its volume.
     */
    @Nullable
    public static List<Long> flood(ServerLevel level, BlockPos sign) {
        BlockPos start = null;
        double best = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(sign.offset(-REACH, -REACH, -REACH), sign.offset(REACH, REACH, REACH))) {
            if (!isWater(level, p)) continue;
            double d = p.distSqr(sign);
            if (d < best) { best = d; start = p.immutable(); }
        }
        if (start == null) return null;
        Set<Long> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<Long> columns = new java.util.LinkedHashSet<>();
        queue.add(start);
        seen.add(start.asLong());
        while (!queue.isEmpty() && columns.size() <= MAX_BLOCKS) {
            BlockPos p = queue.poll();
            columns.add(column(p));
            for (BlockPos n : new BlockPos[]{p.north(), p.south(), p.east(), p.west(), p.above(), p.below()}) {
                if (seen.add(n.asLong()) && isWater(level, n)) queue.add(n);
            }
        }
        return new ArrayList<>(columns);
    }

    private static boolean isWater(ServerLevel level, BlockPos p) {
        return level.getFluidState(p).is(FluidTags.WATER);
    }

    /** Record the claim; a sign already at this position is replaced (re-placing refreshes the flood). */
    public void put(BlockPos sign, ServerPlayer owner, List<Long> water) {
        Claim old = bySign.get(sign.asLong());
        String keepName = old == null ? "" : old.name;
        remove(sign);
        long[] arr = new long[water.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = water.get(i);
        Claim c = new Claim(sign.asLong(), owner.getUUID(), owner.getGameProfile().getName(), arr);
        c.name = keepName;   // re-planting refreshes the water, not the name
        bySign.put(c.sign, c);
        for (long w : arr) byWater.put(w, c);
        setDirty();
    }

    /** The sign is gone: the water is wild again. True when there was a claim to release. */
    public boolean remove(BlockPos sign) {
        Claim c = bySign.remove(sign.asLong());
        if (c == null) return false;
        for (long w : c.water) byWater.remove(w, c);
        setDirty();
        return true;
    }

    // ---- persistence ---------------------------------------------------------------------------

    private static long[] toColumns(long[] blocks) {
        Set<Long> cols = new java.util.LinkedHashSet<>();
        for (long b : blocks) cols.add(column(BlockPos.of(b)));
        long[] out = new long[cols.size()];
        int i = 0;
        for (long c : cols) out[i++] = c;
        return out;
    }

    public static PondData load(CompoundTag tag) {
        PondData d = new PondData();
        ListTag list = tag.getList("ponds", 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            // §pond-columns: a 0.9/1.0 claim stored block positions; fold them into columns on read.
            long[] water = t.contains("cols") ? t.getLongArray("cols") : toColumns(t.getLongArray("water"));
            Claim c = new Claim(t.getLong("sign"), t.getUUID("owner"), t.getString("name"), water);
            c.name = t.getString("pond");   // §pond-name: absent on a 0.9 claim, which reads as ""
            d.bySign.put(c.sign, c);
            for (long w : c.water) d.byWater.put(w, c);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Claim c : bySign.values()) {
            CompoundTag t = new CompoundTag();
            t.putLong("sign", c.sign);
            t.putUUID("owner", c.owner);
            t.putString("name", c.ownerName);
            t.putString("pond", c.name);
            t.putLongArray("cols", c.water);
            list.add(t);
        }
        tag.put("ponds", list);
        return tag;
    }
}
