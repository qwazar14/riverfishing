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
    public static final int TRACE = 60;

    private static CompoundTag last = new CompoundTag();
    private static long stamp = Long.MIN_VALUE;

    /**
     * §finder-hud: the trace. One column per sounding — the depth here, and the depth of every fish the
     * sounding found — pushed on the right and scrolled left, which is what a paper sounder did and why
     * the picture reads as a place rather than a number. Depths only: the strip has no room for names
     * and the screen is where names live.
     */
    private static final java.util.ArrayDeque<int[]> trace = new java.util.ArrayDeque<>();

    public static java.util.List<int[]> trace() {
        return new java.util.ArrayList<>(trace);
    }

    private FinderState() {}

    public static void accept(CompoundTag data, boolean hud) {
        last = data == null ? new CompoundTag() : data;
        Minecraft mc = Minecraft.getInstance();
        stamp = mc.level == null ? Long.MIN_VALUE : mc.level.getGameTime();
        push(last);
        ClientSoundings.merge(last);   // §depth-map: every window the server hands out is kept
        if (!hud && !last.isEmpty()) {
            //? if <26.2 {
            mc.setScreen(new FinderScreen(last));
            //?} else {
            /*mc.setScreenAndShow(new FinderScreen(last));
            *///?}
        }
    }

    /** One column: [depth, then the middle of each species' band]. */
    private static void push(CompoundTag data) {
        net.minecraft.nbt.CompoundTag w = data.getCompoundOrEmpty("water");
        if (w.isEmpty()) return;
        net.minecraft.nbt.ListTag here = data.getListOrEmpty("here");
        int[] col = new int[1 + here.size()];
        col[0] = w.getIntOr("depth", 0);
        for (int i = 0; i < here.size(); i++) {
            net.minecraft.nbt.CompoundTag t = here.getCompoundOrEmpty(i);
            col[i + 1] = (t.getIntOr("dmin", 0) + t.getIntOr("dmax", 0)) / 2;
        }
        trace.addLast(col);
        while (trace.size() > TRACE) trace.removeFirst();
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
    }
}
