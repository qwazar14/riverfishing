package com.riverfishing.water;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Classifies a spot as puddle / pond / lake / river / swamp / sea (§10.2) and measures the water's
 * span for the narrow-water cast limit (§4.1).
 *
 * <p>Performance (server-friendly):
 * <ul>
 *   <li>Ocean / river / swamp are decided <b>by biome</b> with no flood-fill at all — so classifying
 *       a spot in the ocean never floods thousands of blocks.</li>
 *   <li>Width is measured by four short cardinal scans from the cast point (cheap, and a correct
 *       cross/along span for rivers), not a bounding box.</li>
 *   <li>Only ambiguous still inland water runs a flood-fill, and that is hard-capped so it can only
 *       ever distinguish puddle/pond/lake — it stops long before it could lag the server.</li>
 * </ul>
 * Results are additionally cached by {@link WaterBodyCache}, so repeated casts at a spot don't recompute.
 */
public final class WaterBodyDetector {
    /** Max blocks the inland flood-fill will ever visit. Only needs to tell pond from lake. */
    private static final int FLOOD_CAP = 400;
    private static final int PUDDLE_MAX = 12;
    private static final int POND_MAX = 200;
    /** Max distance a cardinal width scan walks before giving up (well past "wide enough"). */
    private static final int CARDINAL_CAP = 64;

    private WaterBodyDetector() {}

    public static boolean isWater(Level level, BlockPos pos) {
        return level.getFluidState(pos).is(FluidTags.WATER);
    }

    public static WaterBody classify(Level level, BlockPos start) {
        if (!isWater(level, start)) {
            return WaterBody.NONE;
        }

        Holder<Biome> biome = level.getBiome(start);
        boolean river = biome.is(BiomeTags.IS_RIVER);
        boolean ocean = biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN);
        boolean swamp = biome.is(ModBiomeTags.IS_SWAMP);

        double width = measureWidth(level, start);

        // Biome-defined bodies: no flood-fill needed.
        if (ocean) {
            return new WaterBody(WaterType.SEA, -1, width, false, false, true);
        }
        if (river) {
            return new WaterBody(WaterType.RIVER, -1, width, true, false, false);
        }
        if (swamp) {
            return new WaterBody(WaterType.SWAMP, -1, width, false, true, false);
        }

        // Inland still water: a small, capped flood-fill is enough to tell puddle/pond/lake apart.
        int count = floodCount(level, start);
        WaterType type = count < PUDDLE_MAX ? WaterType.PUDDLE
                : count < POND_MAX ? WaterType.POND
                : WaterType.LAKE;
        return new WaterBody(type, count, width, false, false, false);
    }

    /** Max open-water span through the cast point (cross or along), in blocks. */
    private static double measureWidth(Level level, BlockPos pos) {
        int east = runLength(level, pos, 1, 0);
        int west = runLength(level, pos, -1, 0);
        int north = runLength(level, pos, 0, -1);
        int south = runLength(level, pos, 0, 1);
        double spanX = east + west + 1;
        double spanZ = north + south + 1;
        return Math.max(spanX, spanZ);
    }

    private static int runLength(Level level, BlockPos pos, int dx, int dz) {
        BlockPos.MutableBlockPos cursor = pos.mutable();
        int n = 0;
        for (int i = 1; i <= CARDINAL_CAP; i++) {
            cursor.set(pos.getX() + dx * i, pos.getY(), pos.getZ() + dz * i);
            if (!isWater(level, cursor)) break;
            n++;
        }
        return n;
    }

    /** Bounded flood-fill; returns the connected water-block count, capped at {@link #FLOOD_CAP}. */
    private static int floodCount(Level level, BlockPos start) {
        Set<Long> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start.asLong());
        int count = 0;
        while (!queue.isEmpty() && count < FLOOD_CAP) {
            BlockPos pos = queue.poll();
            count++;
            for (BlockPos next : neighbours(pos)) {
                long key = next.asLong();
                if (visited.contains(key)) continue;
                if (isWater(level, next)) {
                    visited.add(key);
                    queue.add(next);
                }
            }
        }
        return count;
    }

    private static BlockPos[] neighbours(BlockPos pos) {
        return new BlockPos[]{
                pos.north(), pos.south(), pos.east(), pos.west(), pos.above(), pos.below()
        };
    }
}
