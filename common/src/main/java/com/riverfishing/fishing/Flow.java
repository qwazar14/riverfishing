package com.riverfishing.fishing;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * §flow: the current at a point of water. THE one entry point for water motion — the rope, the
 * drift, the lies and later the fish all ask here and nowhere else, so a real water simulation
 * replaces this class and nothing above it changes.
 *
 * <p>Vanilla rivers do not move, so river-biome water is given a current from its shape: the
 * river's long axis from how far water runs in each direction, downstream toward the nearest ocean,
 * fastest in the middle and deepest, an eddy in the shadow behind any block standing in it, and a
 * little noise so the surface is lanes rather than a slab. Everything else is still: zeros.
 *
 * <p>Vanilla asks a fluid for its flow in exactly two places — the liquid renderer (which picks the
 * moving texture and turns it to the vector) and entity pushing (boats, items, swimmers) — and
 * {@code FlowFluidMixin} answers both from here for source water, so a river LOOKS like it runs and
 * carries what falls in, on the client and the server alike.
 */
public final class Flow {
    private Flow() {}

    static final int AXIS_REACH = 24, OCEAN_REACH = 192, OCEAN_STEP = 8, SHADOW = 3;
    static final double V_MIN = 0.4, V_MAX = 1.2, EDDY = -0.25, LANES = 0.15;

    private static final int[][] DIRS = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
    private static final double SQRT_H = Math.sqrt(0.5);
    private static final int CACHE_SIZE = 1 << 16;
    private static final long CACHE_LIFE_NS = 20_000_000_000L;   // a placed block reshapes an eddy within 20 s
    private static final Map<Long, float[]> CACHE = new LinkedHashMap<>(CACHE_SIZE, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, float[]> e) { return size() > CACHE_SIZE; }
    };
    private static LevelReader cachedFor;
    private static long cacheBorn;
    /** The client's level, for callers that only hold a render region (no biomes there). */
    public static volatile Level clientLevel;

    /** Current at (x, y, z) into out[0..2]. Zero for air, still water and every non-river biome. */
    public static void at(BlockGetter getter, double x, double y, double z, double[] out) {
        out[0] = out[1] = out[2] = 0;
        BlockPos pos = BlockPos.containing(x, y, z);
        float[] v = at(getter, pos);
        if (v == null) return;
        // Lanes: a little sideways wobble that moves with the surface, so the current reads as water.
        double wob = 1 + LANES * Math.sin(x * 0.9 + z * 1.3);
        out[0] = v[0] * wob;
        out[2] = v[2] * wob;
    }

    /** The cached block current, or null when the block is not water or there is no world to ask. */
    public static float[] at(BlockGetter getter, BlockPos pos) {
        if (getter == null || getter.getFluidState(pos).isEmpty()) return null;
        LevelReader biomes = getter instanceof LevelReader lr ? lr : clientLevel;
        if (biomes == null) return null;
        synchronized (CACHE) {   // chunk meshing asks from worker threads
            long now = System.nanoTime();
            if (biomes != cachedFor || now - cacheBorn > CACHE_LIFE_NS) {
                CACHE.clear();
                cachedFor = biomes;
                cacheBorn = now;
            }
            float[] v = CACHE.get(pos.asLong());
            if (v == null) {
                v = compute(getter, biomes, pos);
                CACHE.put(pos.asLong(), v);
            }
            return v;
        }
    }

    private static final float[] STILL = new float[3];

    private static float[] compute(BlockGetter level, LevelReader biomes, BlockPos pos) {
        if (!biomes.getBiome(pos).is(BiomeTags.IS_RIVER)) return STILL;
        // The surface row is where the shape is read; a deep point takes the surface's direction.
        BlockPos surf = pos;
        while (!level.getFluidState(surf.above()).isEmpty()) surf = surf.above();

        int[] run = new int[8];
        for (int d = 0; d < 8; d++) run[d] = waterRun(level, surf, DIRS[d][0], DIRS[d][1]);
        int axis = 0, best = -1;
        for (int d = 0; d < 4; d++) {
            int len = run[d] + run[d + 4];
            if (len > best) { best = len; axis = d; }
        }
        if (best < 4) return STILL;   // a puddle, not a river
        int across = (axis + 2) & 3;
        int bankL = run[across], bankR = run[across + 4];
        double half = Math.max(1, (bankL + bankR) / 2.0);
        double t = Math.min(1, Math.min(bankL, bankR) / half);
        int depth = 0;
        for (BlockPos p = surf; !level.getFluidState(p).isEmpty() && depth < 6; p = p.below()) depth++;
        double vMax = Math.min(V_MAX, V_MIN + 0.2 * (depth - 1));
        double speed = vMax * (1 - (1 - t) * (1 - t));

        double dx = DIRS[axis][0], dz = DIRS[axis][1];
        if ((axis & 1) == 1) { dx *= SQRT_H; dz *= SQRT_H; }
        int sign = downstream(biomes, surf, DIRS[axis][0], DIRS[axis][1]);
        dx *= sign; dz *= sign;

        // Eddy: a block standing in the water upstream of here throws a slow reverse shadow.
        for (int s = 1; s <= SHADOW; s++) {
            BlockPos up = surf.offset((int) Math.round(-dx * s), 0, (int) Math.round(-dz * s));
            if (!level.getBlockState(up).getCollisionShape(level, up).isEmpty()) {
                speed *= EDDY;
                break;
            }
        }
        return new float[] {(float) (dx * speed), 0f, (float) (dz * speed)};
    }

    /** Water blocks in a straight line from pos (exclusive), up to AXIS_REACH. */
    private static int waterRun(BlockGetter level, BlockPos pos, int sx, int sz) {
        int n = 0;
        BlockPos p = pos;
        while (n < AXIS_REACH) {
            p = p.offset(sx, 0, sz);
            if (level.getFluidState(p).isEmpty()) break;
            n++;
        }
        return n;
    }

    /** +1 if the ocean lies along +axis, -1 along -axis; a seed-stable coin if neither is in reach. */
    private static int downstream(LevelReader level, BlockPos pos, int sx, int sz) {
        for (int r = OCEAN_STEP; r <= OCEAN_REACH; r += OCEAN_STEP) {
            var plus = level.getBiome(pos.offset(sx * r, 0, sz * r));
            var minus = level.getBiome(pos.offset(-sx * r, 0, -sz * r));
            boolean po = plus.is(BiomeTags.IS_OCEAN) || plus.is(BiomeTags.IS_DEEP_OCEAN);
            boolean mo = minus.is(BiomeTags.IS_OCEAN) || minus.is(BiomeTags.IS_DEEP_OCEAN);
            if (po != mo) return po ? 1 : -1;
        }
        // The same river cell always picks the same way, so a river never runs at itself.
        long cell = ((long) (pos.getX() >> 8) * 73856093L) ^ ((long) (pos.getZ() >> 8) * 19349663L);
        return (cell & 1) == 0 ? 1 : -1;
    }
}
