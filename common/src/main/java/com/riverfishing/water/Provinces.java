package com.riverfishing.water;

/**
 * §provinces: which part of the world a piece of water is in.
 *
 * <p>Minecraft has no geography, only climate — the same swamp, taiga and jungle repeat to the world
 * border, so a fish gated on biomes alone lives everywhere its weather occurs. Real fish do not: a
 * peacock bass and a taimen never share a river however similar the water, because half a planet is
 * between them. This is that half a planet.
 *
 * <p>The world is cut into cells of {@link #CELL} blocks, each cell's centre pushed somewhere random
 * inside it by the world seed, and a point belongs to the nearest such centre — a Voronoi diagram, so
 * the borders are organic rather than a grid, and neighbouring cells that draw the same province
 * simply merge into one bigger region. Pure arithmetic on the seed and the coordinates: no state, no
 * saved data, no noise library, and the same answer for the same block forever.
 *
 * <p>The sea is deliberately not divided. Ocean species carry no province list at all — one ocean,
 * one fauna — and the division bites exactly where it should, on fresh water.
 */
public final class Provinces {

    /** In table order; a profile names these, and lang keys are {@code province.riverfishing.<id>}. */
    public static final String[] ALL = {"palearctic", "nearctic", "neotropic", "indomalaya"};

    /**
     * Cell size in blocks. Three thousand is a journey and not an expedition: a player who walks a
     * few thousand blocks in one direction meets a different fauna, and one who settles never has all
     * of it at home — which is what the keepnet, the fishermen and the breeding tank are for.
     */
    public static final int CELL = 3072;

    private Provinces() {}

    /** The province of a block, for this world's seed. */
    public static String at(long seed, int x, int z) {
        return ALL[index(seed, x, z)];
    }

    /** 0..ALL.length-1, the same answer {@link #at} names. */
    public static int index(long seed, int x, int z) {
        int cx = Math.floorDiv(x, CELL), cz = Math.floorDiv(z, CELL);
        long best = Long.MAX_VALUE;
        int pick = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int gx = cx + dx, gz = cz + dz;
                long h = mix(seed ^ ((long) gx * 0x9E3779B97F4A7C15L) ^ ((long) gz * 0xC2B2AE3D27D4EB4FL));
                // the cell's own centre, pushed off the middle so no border is a straight line
                long px = (long) gx * CELL + Math.floorMod(h >>> 17, CELL);
                long pz = (long) gz * CELL + Math.floorMod(h >>> 41, CELL);
                long ddx = px - x, ddz = pz - z;
                long d = ddx * ddx + ddz * ddz;
                if (d < best) {
                    best = d;
                    pick = (int) Math.floorMod(h, ALL.length);
                }
            }
        }
        return pick;
    }

    /** splitmix64's finalizer: cheap, and it spreads a small cell index across all 64 bits. */
    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
