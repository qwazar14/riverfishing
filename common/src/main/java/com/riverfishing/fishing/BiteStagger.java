package com.riverfishing.fishing;

import net.minecraft.util.RandomSource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * §bite-stagger: lines that belong together — the rods on one pod, the rod in one angler's hands —
 * do not all bite in the same breath. Each group remembers its last take; a line whose clock runs
 * out inside ten seconds of it is pushed back by ten seconds to a hundred, at random. Groups are
 * keyed by the pod's position or the angler's own id, so one angler's rods never delay another's.
 */
public final class BiteStagger {
    private BiteStagger() {}

    public static final int TOO_SOON = 200, PUSH_MIN = 200, PUSH_MAX = 2000;   // ticks

    private static final Map<Long, Long> LAST = new LinkedHashMap<>(256, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, Long> e) { return size() > 1024; }
    };

    /** True when another line of this group took inside the last ten seconds — this one must wait. */
    public static synchronized boolean tooSoon(long group, long now) {
        Long last = LAST.get(group);
        return last != null && now - last < TOO_SOON;
    }

    /** Ten seconds to a hundred, for the line that has to wait. */
    public static long push(RandomSource r) {
        return PUSH_MIN + r.nextInt(PUSH_MAX - PUSH_MIN + 1);
    }

    /** This group just took. */
    public static synchronized void mark(long group, long now) {
        LAST.put(group, now);
    }

    public static long key(java.util.UUID player) { return player.getMostSignificantBits() ^ player.getLeastSignificantBits(); }
    public static long key(net.minecraft.core.BlockPos pos) { return pos.asLong() * 31L + 7L; }
}
