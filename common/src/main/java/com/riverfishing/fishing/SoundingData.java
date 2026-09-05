package com.riverfishing.fishing;

import net.minecraft.core.BlockPos;
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
 *
 * <p>§finder2: bucketed by chunk. Every reader — the cast asking "am I on a spot", the strip's needle
 * once a second, the screen's map window — wants what is NEAR a point, and a flat map answered by
 * walking the whole world. A lake sounded flat is a hundred thousand columns; the readers now open the
 * handful of chunks they can see into. And a feature is ONE per 4×4 cell per kind: five strands a cast,
 * cast twenty times over the same drop-off, wrote the same ledge as a hundred markers, and the chart
 * drew every one of them.
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
    /** §finder2: one feature of a kind per this many blocks square — the marker cast's own strand pitch. */
    private static final int CELL_SHIFT = 2;

    /** chunk key -> column key -> depth in blocks. */
    private final Map<Long, Map<Long, Integer>> depth = new HashMap<>();
    /** chunk key -> column key -> which kind of feature was found there. */
    private final Map<Long, Map<Long, String>> spots = new HashMap<>();
    /** §finder2: 4×4 cell key -> bit 0 a hole, bit 1 a ledge — the dedupe. */
    private final Map<Long, Byte> cells = new HashMap<>();

    /** §finder2: what a reader does with one sounded column. */
    @FunctionalInterface
    public interface Visitor {
        void visit(int x, int z, int depth, String spot);
    }

    public static SoundingData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(SoundingData::load, SoundingData::new, NAME);
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

    private static long chunkOf(int x, int z) {
        return key(x >> 4, z >> 4);
    }

    public Integer depthAt(int x, int z) {
        Map<Long, Integer> m = depth.get(chunkOf(x, z));
        return m == null ? null : m.get(key(x, z));
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
            depth.computeIfAbsent(chunkOf(xs[i], zs[i]), k -> new HashMap<>()).put(key(xs[i], zs[i]), line[i]);
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
            // Already on the map — this column or its cell; finding it twice is not news.
            if (putSpot(xs[i], zs[i], kind)) found.add(new BlockPos(xs[i], y, zs[i]));
        }
        if (!found.isEmpty() || line.length > 0) setDirty();
        return found;
    }

    /** §finder2: a feature goes on the map unless its 4×4 cell already holds one of that kind. */
    private boolean putSpot(int x, int z, String kind) {
        long cell = key(x >> CELL_SHIFT, z >> CELL_SHIFT);
        byte bit = (byte) ("hole".equals(kind) ? 1 : 2);
        byte have = cells.getOrDefault(cell, (byte) 0);
        if ((have & bit) != 0) return false;
        cells.put(cell, (byte) (have | bit));
        spots.computeIfAbsent(chunkOf(x, z), k -> new HashMap<>()).put(key(x, z), kind);
        return true;
    }

    /** The feature this cast landed on, or null — {@link #SPOT_RADIUS} blocks of slack, not a pixel. */
    public String spotAt(BlockPos pos) {
        int[] near = nearest(pos, SPOT_RADIUS);
        return near == null ? null : (near[2] == 0 ? "hole" : "ledge");
    }

    /**
     * §ledge-arrow: the nearest found feature within {@code range} blocks of a point, as
     * {dx, dz, kindIndex} — or null. Walks the chunks the range covers and nothing else.
     */
    public int[] nearest(BlockPos pos, int range) {
        int[] best = null;
        long bestD = (long) range * range + 1;
        for (int cx = (pos.getX() - range) >> 4; cx <= (pos.getX() + range) >> 4; cx++) {
            for (int cz = (pos.getZ() - range) >> 4; cz <= (pos.getZ() + range) >> 4; cz++) {
                Map<Long, String> m = spots.get(key(cx, cz));
                if (m == null) continue;
                for (Map.Entry<Long, String> e : m.entrySet()) {
                    int dx = keyX(e.getKey()) - pos.getX();
                    int dz = keyZ(e.getKey()) - pos.getZ();
                    long d = (long) dx * dx + (long) dz * dz;
                    if (d < bestD) {
                        bestD = d;
                        best = new int[]{dx, dz, "hole".equals(e.getValue()) ? 0 : 1};
                    }
                }
            }
        }
        return best;
    }

    /** §finder2: every sounded column within {@code reach} blocks (a square) of the centre, with its feature if any. */
    public void forEachNear(BlockPos centre, int reach, Visitor visitor) {
        for (int cx = (centre.getX() - reach) >> 4; cx <= (centre.getX() + reach) >> 4; cx++) {
            for (int cz = (centre.getZ() - reach) >> 4; cz <= (centre.getZ() + reach) >> 4; cz++) {
                long chunk = key(cx, cz);
                Map<Long, Integer> m = depth.get(chunk);
                if (m == null) continue;
                Map<Long, String> s = spots.get(chunk);
                for (Map.Entry<Long, Integer> e : m.entrySet()) {
                    int x = keyX(e.getKey()), z = keyZ(e.getKey());
                    if (Math.abs(x - centre.getX()) > reach || Math.abs(z - centre.getZ()) > reach) continue;
                    visitor.visit(x, z, e.getValue(), s == null ? null : s.get(e.getKey()));
                }
            }
        }
    }

    // ---- persistence ---------------------------------------------------------------------------

    /**
     * Flat arrays (§finder2): the first format was a list of one compound per column, which for a lake
     * sounded flat was megabytes of NBT to gzip on every autosave. The old lists still load, and their
     * duplicate features fall out through {@link #putSpot} — an old world is cleaned on first load.
     */
    public static SoundingData load(CompoundTag tag) {
        SoundingData d = new SoundingData();
        long[] bk = tag.getLongArray("bk");
        int[] bd = tag.getIntArray("bd");
        for (int i = 0; i < bk.length && i < bd.length; i++) {
            int x = keyX(bk[i]), z = keyZ(bk[i]);
            d.depth.computeIfAbsent(chunkOf(x, z), k -> new HashMap<>()).put(bk[i], bd[i]);
        }
        long[] sk = tag.getLongArray("sk");
        byte[] sv = tag.getByteArray("sv");
        for (int i = 0; i < sk.length && i < sv.length; i++) {
            d.putSpot(keyX(sk[i]), keyZ(sk[i]), sv[i] == 0 ? "hole" : "ledge");
        }
        ListTag beds = tag.getList("beds", 10);
        for (int i = 0; i < beds.size(); i++) {
            CompoundTag t = beds.getCompound(i);
            long k = t.getLong("k");
            d.depth.computeIfAbsent(chunkOf(keyX(k), keyZ(k)), c -> new HashMap<>()).put(k, t.getInt("d"));
        }
        ListTag marks = tag.getList("spots", 10);
        for (int i = 0; i < marks.size(); i++) {
            CompoundTag t = marks.getCompound(i);
            long k = t.getLong("k");
            d.putSpot(keyX(k), keyZ(k), t.getString("t"));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        int n = 0;
        for (Map<Long, Integer> m : depth.values()) n += m.size();
        long[] bk = new long[n];
        int[] bd = new int[n];
        int i = 0;
        for (Map<Long, Integer> m : depth.values()) {
            for (Map.Entry<Long, Integer> e : m.entrySet()) {
                bk[i] = e.getKey();
                bd[i++] = e.getValue();
            }
        }
        tag.putLongArray("bk", bk);
        tag.putIntArray("bd", bd);
        n = 0;
        for (Map<Long, String> m : spots.values()) n += m.size();
        long[] sk = new long[n];
        byte[] sv = new byte[n];
        i = 0;
        for (Map<Long, String> m : spots.values()) {
            for (Map.Entry<Long, String> e : m.entrySet()) {
                sk[i] = e.getKey();
                sv[i++] = (byte) ("hole".equals(e.getValue()) ? 0 : 1);
            }
        }
        tag.putLongArray("sk", sk);
        tag.putByteArray("sv", sv);
        return tag;
    }
}
