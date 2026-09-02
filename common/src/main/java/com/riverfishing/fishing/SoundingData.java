package com.riverfishing.fishing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * §sounding (0.8.3): the bottom, as far as anyone has bothered to measure it.
 *
 * <p>Every real angler's first hour on new water is spent finding out what is under it, and the mod had
 * no way to do that: depth was a number the engine knew and the player could only guess at. A marker
 * weight cast across the swim writes a LINE of depths in here, and enough lines make a map.
 *
 * <p>What the map is FOR is the second half. A bed that is flat everywhere is a bed with nothing to
 * find, so the interesting parts are named as they are discovered — a hole, a drop-off — and fish hold
 * on them. Finding one is worth a real bite bonus, which is the difference between a map that decorates
 * the screen and a map that is worth making.
 *
 * <p>Keyed on the column, not the block: two soundings of the same spot from different banks are one
 * fact about that spot. Stored per world in the overworld's data storage, like the market and the
 * fishing pressure, because a swim is a place rather than a player's possession — a friend who sounds
 * the far bank has sounded it for everyone.
 */
public final class SoundingData extends SavedData {
    private static final String NAME = "riverfishing_soundings";

    /** How far a discovered feature reaches. A cast inside this ring is a cast on the spot. */
    public static final int SPOT_RADIUS = 4;
    /** What a hole has to be worth to be called one: this much deeper than the bed around it. */
    private static final int HOLE_DROP = 2;
    /** And what counts as a drop-off: this big a step between two adjacent metres of bed. */
    private static final int LEDGE_STEP = 3;
    /** The bite bonus for fishing a found feature. Worth crossing the swim for, not worth abandoning it. */
    public static final double SPOT_BONUS = 1.35;

    /** column key -> depth in blocks. */
    private final Map<Long, Integer> depth = new HashMap<>();
    /** column key -> which kind of feature was found there. */
    private final Map<Long, String> spots = new HashMap<>();

    public static SoundingData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(SoundingData::new, SoundingData::load,
                        (net.minecraft.util.datafix.DataFixTypes) null), NAME);
    }

    /** One column, x and z packed — y is what is being stored, so it has no business in the key. */
    public static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    public static int keyX(long k) {
        return (int) (k >> 32);
    }

    public static int keyZ(long k) {
        return (int) k;
    }

    public Integer depthAt(int x, int z) {
        return depth.get(key(x, z));
    }

    public Map<Long, Integer> depths() {
        return depth;
    }

    public Map<Long, String> spots() {
        return spots;
    }

    /**
     * Write a cast's worth of bed and read what it found.
     *
     * <p>{@code line} is the depth at each metre out from the rod, in order, with a negative for a
     * metre that was not water. Features are looked for ALONG the line, because that is the only place
     * this cast has evidence — inferring a hole from two casts that never met would be inventing bed.
     *
     * @return the features discovered by this cast that were not already known
     */
    public List<BlockPos> record(int y, int[] xs, int[] zs, int[] line) {
        List<BlockPos> found = new ArrayList<>();
        for (int i = 0; i < line.length; i++) {
            if (line[i] < 0) continue;
            depth.put(key(xs[i], zs[i]), line[i]);
        }
        for (int i = 1; i < line.length - 1; i++) {
            int d = line[i], before = line[i - 1], after = line[i + 1];
            if (d < 0 || before < 0 || after < 0) continue;
            String kind = null;
            if (d - before >= HOLE_DROP && d - after >= HOLE_DROP) {
                kind = "hole";                        // deeper than the bed on both sides of it
            } else if (Math.abs(d - before) >= LEDGE_STEP) {
                kind = "ledge";                       // the bed steps away here
            }
            if (kind == null) continue;
            long k = key(xs[i], zs[i]);
            if (spots.containsKey(k)) continue;       // already on the map; finding it twice is not news
            spots.put(k, kind);
            found.add(new BlockPos(xs[i], y, zs[i]));
        }
        if (!found.isEmpty() || line.length > 0) setDirty();
        return found;
    }

    /**
     * The feature this cast landed on, or null — {@link #SPOT_RADIUS} blocks of slack, not a pixel.
     *
     * <p>ponytail: linear over every spot in the world, once per cast. A world with a few hundred found
     * features costs nothing; index by chunk key if a server ever gets into the thousands.
     */
    public String spotAt(BlockPos pos) {
        for (Map.Entry<Long, String> e : spots.entrySet()) {
            int dx = keyX(e.getKey()) - pos.getX();
            int dz = keyZ(e.getKey()) - pos.getZ();
            if (dx * dx + dz * dz <= SPOT_RADIUS * SPOT_RADIUS) return e.getValue();
        }
        return null;
    }

    // ---- persistence ---------------------------------------------------------------------------

    public static SoundingData load(CompoundTag tag, HolderLookup.Provider registries) {
        SoundingData d = new SoundingData();
        ListTag beds = tag.getList("beds", 10);
        for (int i = 0; i < beds.size(); i++) {
            CompoundTag t = beds.getCompound(i);
            d.depth.put(t.getLong("k"), t.getInt("d"));
        }
        ListTag marks = tag.getList("spots", 10);
        for (int i = 0; i < marks.size(); i++) {
            CompoundTag t = marks.getCompound(i);
            d.spots.put(t.getLong("k"), t.getString("t"));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag beds = new ListTag();
        for (Map.Entry<Long, Integer> e : depth.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putLong("k", e.getKey());
            t.putInt("d", e.getValue());
            beds.add(t);
        }
        tag.put("beds", beds);
        ListTag marks = new ListTag();
        for (Map.Entry<Long, String> e : spots.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putLong("k", e.getKey());
            t.putString("t", e.getValue());
            marks.add(t);
        }
        tag.put("spots", marks);
        return tag;
    }
}
