package com.riverfishing.water;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Per-level cache of water classifications (§12 "кэш водоёмов"). Avoids re-running the detector on
 * every cast: a player hammering casts at one spot pays the cost once. Entries expire after a TTL
 * (water can be edited) and the map is size-capped (LRU) so it can't grow without bound.
 *
 * <p>Server-thread only (called from the fishing tick / cast), so no synchronisation is needed.
 */
public final class WaterBodyCache {
    private static final long TTL_TICKS = 6000;   // ~5 minutes
    private static final int MAX_ENTRIES = 1024;

    private static final Map<Level, WaterBodyCache> CACHES = new WeakHashMap<>();

    private final LinkedHashMap<Long, Entry> entries =
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<Long, WaterBodyCache.Entry> eldest) {
                    return size() > MAX_ENTRIES;
                }
            };

    private WaterBodyCache() {}

    public static WaterBodyCache forLevel(Level level) {
        return CACHES.computeIfAbsent(level, l -> new WaterBodyCache());
    }

    public WaterBody get(Level level, BlockPos pos) {
        long key = columnKey(pos.getX(), pos.getZ());
        long now = level.getGameTime();
        Entry cached = entries.get(key);
        if (cached != null && now - cached.tick < TTL_TICKS) {
            return cached.body;
        }
        WaterBody body = WaterBodyDetector.classify(level, pos);
        entries.put(key, new Entry(body, now));
        return body;
    }

    private static long columnKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private record Entry(WaterBody body, long tick) {}
}
