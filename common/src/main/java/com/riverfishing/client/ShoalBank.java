package com.riverfishing.client;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;


import java.util.Map;

/**
 * §shoal-bank: the shape of the water around one shoal, so its fish follow the shoreline.
 *
 * <p>A shoal circles a single surface block. Near a bank part of that circle is over grass, and the
 * renderer used to handle it by simply not drawing a fish whose lap had left the water — so a fish
 * winked out for a second every time round, which reads as fish popping in and out rather than fish
 * swimming. Worse, the ones that were drawn cut straight through the bank.
 *
 * <p>So the water is measured instead. Sixteen directions out from the centre, walking outward until
 * the water stops, giving the furthest each bearing may reach. A fish takes the smaller of its own lap
 * and what the water allows in the direction it currently faces, which means a lap near a bank comes in
 * to hug the shoreline and goes back out over open water — and nothing has to be hidden.
 *
 * <p>Measured once per patch and kept until the patch is dropped: sixteen bearings by nine steps is 144
 * block lookups for a shoal that then costs nothing for the minutes it is on screen. The probe is taken
 * at the shoal's own depth, because an overhang above and a shelf below are different questions.
 */
public final class ShoalBank {
    private static final int BEARINGS = 16;
    private static final double STEP = 0.6;
    private static final double MAX_R = 5.5;
    /** Fish keep this much of a margin off the bank so a broad body does not clip it. */
    private static final double MARGIN = 0.35;

    /**
     * Bounded, least-recently-used: the key is a world position, so an unbounded map would grow for as
     * long as the player keeps walking past new water. Five hundred patches is far more than can ever be
     * on screen and costs a few kilobytes.
     */
    private static final Map<Long, float[]> CACHE = new java.util.LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, float[]> eldest) {
            return size() > 512;
        }
    };
    private static Level cachedLevel;

    private ShoalBank() {}

    /** Drop everything: a new level, or a reload, invalidates every measurement. */
    public static synchronized void clear() {
        CACHE.clear();
        cachedLevel = null;
    }

    /**
     * The furthest a lap may reach on the bearing {@code angle} (radians) around this shoal.
     * The z axis is squashed the same way the renderer squashes it, so this measures the actual ellipse.
     */
    public static synchronized double reach(Level level, BlockPos centre, double y, float angle) {
        if (level != cachedLevel) {
            CACHE.clear();
            cachedLevel = level;
        }
        float[] profile = CACHE.computeIfAbsent(centre.asLong(), k -> measure(level, centre, y));
        // Between two measured bearings, take the nearer of them rather than interpolating: a lap that
        // rounds a point of land should turn early, not cut the corner.
        float step = Mth.TWO_PI / BEARINGS;
        int i = Mth.floor((angle % Mth.TWO_PI + Mth.TWO_PI) % Mth.TWO_PI / step);
        int j = (i + 1) % BEARINGS;
        return Math.min(profile[i], profile[j % BEARINGS]);
    }

    private static float[] measure(Level level, BlockPos centre, double y) {
        float[] out = new float[BEARINGS];
        double cx = centre.getX() + 0.5, cz = centre.getZ() + 0.5;
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (int b = 0; b < BEARINGS; b++) {
            double a = b * (Mth.TWO_PI / BEARINGS);
            double dx = Math.cos(a), dz = Math.sin(a) * 0.75;
            double reach = 0.0;
            for (double r = STEP; r <= MAX_R; r += STEP) {
                probe.set(Mth.floor(cx + dx * r), Mth.floor(y), Mth.floor(cz + dz * r));
                if (level.getFluidState(probe).isEmpty()) break;
                reach = r;
            }
            out[b] = (float) Math.max(0.0, reach - MARGIN);
        }
        return out;
    }
}
