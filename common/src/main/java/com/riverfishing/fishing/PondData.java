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
    /** Biggest body a sign may claim — a dug pit, a village pond, a small lake; not a river. */
    public static final int MAX_BLOCKS = 600;
    /** How far from the sign the water may be. */
    public static final int REACH = 3;

    public static final class Claim {
        public final long sign;
        public final UUID owner;
        public final String ownerName;
        final long[] water;

        Claim(long sign, UUID owner, String ownerName, long[] water) {
            this.sign = sign;
            this.owner = owner;
            this.ownerName = ownerName;
            this.water = water;
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

    @Nullable
    private Claim claimAt(BlockPos pos) {
        Claim c = byWater.get(pos.asLong());
        return c != null ? c : byWater.get(pos.below().asLong());
    }

    // ---- the sign's verbs ------------------------------------------------------------------------

    /**
     * The water body nearest the sign, as packed positions, or null when no water is within {@link #REACH}.
     * Flood-filled six ways with a cap one past {@link #MAX_BLOCKS}, so "too big" costs 601 blocks and
     * not a lake.
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
        List<Long> out = new ArrayList<>();
        queue.add(start);
        seen.add(start.asLong());
        while (!queue.isEmpty() && out.size() <= MAX_BLOCKS) {
            BlockPos p = queue.poll();
            out.add(p.asLong());
            for (BlockPos n : new BlockPos[]{p.north(), p.south(), p.east(), p.west(), p.above(), p.below()}) {
                if (seen.add(n.asLong()) && isWater(level, n)) queue.add(n);
            }
        }
        return out;
    }

    private static boolean isWater(ServerLevel level, BlockPos p) {
        return level.getFluidState(p).is(FluidTags.WATER);
    }

    /** Record the claim; a sign already at this position is replaced (re-placing refreshes the flood). */
    public void put(BlockPos sign, ServerPlayer owner, List<Long> water) {
        remove(sign);
        long[] arr = new long[water.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = water.get(i);
        Claim c = new Claim(sign.asLong(), owner.getUUID(), owner.getGameProfile().getName(), arr);
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

    public static PondData load(CompoundTag tag) {
        PondData d = new PondData();
        ListTag list = tag.getList("ponds", 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            Claim c = new Claim(t.getLong("sign"), t.getUUID("owner"), t.getString("name"), t.getLongArray("water"));
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
            t.putLongArray("water", c.water);
            list.add(t);
        }
        tag.put("ponds", list);
        return tag;
    }
}
