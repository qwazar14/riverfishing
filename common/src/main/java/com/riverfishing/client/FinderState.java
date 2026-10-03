package com.riverfishing.client;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

/**
 * §finder-hud: the last sounding this client took, and who wants it.
 *
 * <p>Two consumers, one reading. A right-click opens {@link FinderScreen} on it; holding the finder
 * runs a live strip on the HUD off the same tag, refreshed as the player walks. Keeping ONE latest
 * sounding means the strip and the screen can never show two different waters — open the screen off a
 * strip you were watching and it is that water, not the one you last clicked.
 */
public final class FinderState {
    /** How long a sounding stays worth drawing on the strip, in ticks. Two seconds of silence blanks it. */
    private static final int STALE = 40;

    /** How many soundings the strip remembers. At one a second, a minute of bank walked. */
    public static final int TRACE = 64;

    private static CompoundTag last = new CompoundTag();
    private static long stamp = Long.MIN_VALUE;

    /**
     * §finder-hud: the trace. One column per sounding — the depth here, the bed, and every fish the
     * sounding heard — pushed on the right and scrolled left, which is what a paper sounder did and why
     * the picture reads as a place rather than a number. No names: the strip has no room for them and the
     * screen is where names live.
     *
     * <p>§finder-strip: each column carries its number, so a fish drawn on it keeps its place as it scrolls,
     * and each fish its band, how many of them there are and whether they hunt.
     */
    public record Col(long seq, int depth, int bed, int[] dmin, int[] dmax, int[] n, boolean[] pred) {}

    private static final java.util.ArrayDeque<Col> trace = new java.util.ArrayDeque<>();
    private static long seq, pushedAt;
    /** How far apart the soundings come, ms — the server sends one a second, the network decides the rest. */
    private static long interval = 1000;

    public static java.util.List<Col> trace() {
        return new java.util.ArrayList<>(trace);
    }

    /** §finder-strip: how far the newest sounding has slid in, 0..1 — the trace glides between soundings. */
    public static float slide() {
        if (pushedAt == 0) return 1f;
        return Math.min(1f, (net.minecraft.util.Util.getMillis() - pushedAt) / (float) interval);
    }

    private FinderState() {}

    public static void accept(CompoundTag data, boolean hud) {
        // §chart-server: a parcel of somebody's chart, not a sounding — it is not the last reading, it
        // does not belong on the trace and it must not open a screen.
        if (data != null && data.getBooleanOr("chartsync", false)) {
            ClientSoundings.absorb(data);
            return;
        }
        last = data == null ? new CompoundTag() : data;
        Minecraft mc = Minecraft.getInstance();
        stamp = mc.level == null ? Long.MIN_VALUE : mc.level.getGameTime();
        absorbShoals(last, stamp);
        push(last);
        ClientSoundings.merge(last);   // §depth-map: every window the server hands out is kept
        if (!hud && !last.isEmpty()) {
            //? if <26.2 {
            /*mc.setScreen(new FinderScreen(last));
            *///?} else {
            mc.setScreenAndShow(new FinderScreen(last));
            //?}
        }
    }

    /** One column off one sounding. */
    private static void push(CompoundTag data) {
        net.minecraft.nbt.CompoundTag w = data.getCompoundOrEmpty("water");
        if (w.isEmpty()) return;
        net.minecraft.nbt.ListTag here = data.getListOrEmpty("here");
        int k = here.size();
        int[] dmin = new int[k], dmax = new int[k], n = new int[k];
        boolean[] pred = new boolean[k];
        for (int i = 0; i < k; i++) {
            net.minecraft.nbt.CompoundTag t = here.getCompoundOrEmpty(i);
            dmin[i] = t.getIntOr("dmin", 0);
            dmax[i] = t.getIntOr("dmax", 0);
            n[i] = t.getIntOr("n", 0);          // 0 on the old engine: a species that may be here, not a count
            pred[i] = t.getBooleanOr("pred", false);
        }
        long now = net.minecraft.util.Util.getMillis();
        if (pushedAt != 0) interval = Math.max(300, Math.min(3000, (interval * 3 + (now - pushedAt)) / 4));
        pushedAt = now;
        trace.addLast(new Col(seq++, w.getIntOr("depth", 0), w.getByteOr("bed", (byte) 0), dmin, dmax, n, pred));
        while (trace.size() > TRACE) trace.removeFirst();
    }

    // ---- §sonar-live: the shoals every sweep has picked up, as the angler walks with the finder ----

    /** How long a shoal the sounder saw stays on the chart, ticks — it fades out over this, fish move on. */
    public static final long SHOAL_MEMORY = 2400;
    /** (x, z) of a zone → [x, z, n, topn, predator?1:0, tick seen] and its top species. */
    private static final java.util.Map<Long, long[]> halos = new java.util.HashMap<>();
    private static final java.util.Map<Long, String> haloSpecies = new java.util.HashMap<>();

    private static void absorbShoals(CompoundTag data, long now) {
        net.minecraft.nbt.ListTag shoals = data.getListOrEmpty("shoals");
        for (int i = 0; i < shoals.size(); i++) {
            CompoundTag s = shoals.getCompoundOrEmpty(i);
            long key = ((long) s.getIntOr("x", 0) << 32) ^ (s.getIntOr("z", 0) & 0xFFFFFFFFL);
            halos.put(key, new long[]{s.getIntOr("x", 0), s.getIntOr("z", 0), s.getIntOr("n", 0), s.getIntOr("topn", 0),
                    s.getBooleanOr("pred", false) ? 1 : 0, now});
            haloSpecies.put(key, s.getStringOr("sp", ""));
        }
        halos.values().removeIf(h -> now - h[5] > SHOAL_MEMORY);
        haloSpecies.keySet().retainAll(halos.keySet());
    }

    /** Every shoal still on the chart. */
    public static java.util.Collection<long[]> halos() {
        return halos.values();
    }

    public static String haloSpecies(long[] h) {
        return haloSpecies.getOrDefault((h[0] << 32) ^ (h[1] & 0xFFFFFFFFL), "");
    }

    public static CompoundTag latest() {
        return last;
    }

    /** Whether the strip still has something true to show. */
    public static boolean fresh() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && !last.isEmpty() && mc.level.getGameTime() - stamp <= STALE;
    }

    /** The strip blanks when the finder leaves the hand, so it can never show a water you walked away from. */
    public static void clear() {
        last = new CompoundTag();
        stamp = Long.MIN_VALUE;
        trace.clear();
        pushedAt = 0;
    }
}
