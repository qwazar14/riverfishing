package com.riverfishing.fishing;

import com.riverfishing.component.RodType;
import com.riverfishing.item.RodItem;
import com.riverfishing.network.FlyCastPacket;
import com.riverfishing.network.ModNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * §fly-3 (0.10.0): the cast, and nothing but the right mouse button.
 *
 * <p>Hold use and the rod false-casts on its own: back, stop, forward, stop, one full cycle every
 * {@link #SWING_PERIOD} ticks, with a swish at each end. Every completed cycle carries
 * {@link #METERS_PER_CYCLE} more metres into the air, from {@link #BASE_METERS} up to what the rod holds.
 * <b>Let go as the rod comes forward</b> and the loop unrolls: within {@link #RELEASE_WINDOW} ticks of the
 * forward stop it turns over cleanly and lands quietly at the full distance; near it the loop opens and
 * lands at four fifths; with the rod still behind you the line piles up short and loud.
 *
 * <p>There is nothing to click. The left button belongs to the drift (the mend) and to the take (the
 * strike), and no amount of tapping during the cast changes the distance — the release is the whole skill.
 *
 * <p>The class also runs the winter rod's jig on the same needle (mode 1) on the same needle.
 */
public final class FlyCast {
    /** One full swing: back, stop, forward, stop. The forward stop is halfway through. */
    public static final int SWING_PERIOD = 30;
    /** Ticks either side of the forward stop that turn the loop over cleanly. */
    public static final int RELEASE_WINDOW = 3;
    /** Ticks either side of it that still fly, open and a little short. */
    public static final int NORMAL_WINDOW = 9;
    /** Metres of line out of the tip on the first swing, and what each completed cycle adds. */
    public static final double BASE_METERS = 6.0, METERS_PER_CYCLE = 3.0;

    /** What the release did to the loop. */
    public static final int PERFECT = 0, NORMAL = 1, BAD = 2;
    private static final double[] QUALITY_KEEP = {1.0, 0.8, 0.5};

    private static final class State {
        long start;
        int beats;          // cast: completed swing cycles; jig: accents landed
        int maxBeats;       // cast: the rod's reach in metres; jig: JIG_MAX
        int lastEnd = -1;   // jig: the stroke index of the last accent
        int mode;           // 0 the fly cast, 1 the jig
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    /** The delivery's quality, kept until the cast lands and reads it once. */
    private static final Map<UUID, Integer> QUALITY = new HashMap<>();

    private FlyCast() {}

    /** Triangle wave 0..1: 0 at the back stop, 1 at the forward stop. The client draws the same. */
    public static float marker(long elapsed, int period) {
        if (period <= 0) return 0.5f;
        float phase = (Math.floorMod(elapsed, period)) / (float) period;
        return phase < 0.5f ? phase * 2f : 2f - phase * 2f;
    }

    /** Completed swing cycles after this long on the hold. */
    public static int cycles(long elapsed) {
        return (int) Math.max(0, Math.floorDiv(elapsed, (long) SWING_PERIOD));
    }

    /** Metres in the air after this long on the hold, capped at the rod's reach. */
    public static double lineOut(long elapsed, int maxMetres) {
        return Math.min(maxMetres, BASE_METERS + METERS_PER_CYCLE * cycles(elapsed));
    }

    /** Ticks from the forward stop right now — 0 is the moment the loop wants to go. */
    public static int fromForwardStop(long elapsed) {
        int half = SWING_PERIOD / 2;
        return Math.abs((int) Math.floorMod(elapsed, (long) SWING_PERIOD) - half);
    }

    private static ItemStack flyRod(ServerPlayer sp) {
        ItemStack main = sp.getMainHandItem();
        if (main.getItem() instanceof RodItem ri && ri.rodType() == RodType.FLY) return main;
        return sp.getOffhandItem();
    }

    public static boolean isCasting(ServerPlayer sp) {
        State s = STATES.get(sp.getUUID());
        return s != null && s.mode == 0;
    }

    /** The hold began: the rod starts false-casting and the client is told to draw it. */
    public static void begin(ServerPlayer sp, long now) {
        State s = new State();
        s.start = now;
        s.maxBeats = (int) Math.max(BASE_METERS, Math.floor(FishingManager.castRangeMax(flyRod(sp))));
        STATES.put(sp.getUUID(), s);
        QUALITY.remove(sp.getUUID());
        send(sp, s, true);
    }

    /**
     * The delivery. Returns the power for {@code chargedCast} — the inverse of {@code castDistance}, so the
     * line lands at the metres the HUD showed — and remembers what the release did to the loop.
     */
    public static float release(ServerPlayer sp, ItemStack rod, long now) {
        State s = STATES.remove(sp.getUUID());
        double max = FishingManager.castRangeMax(rod);
        int quality = NORMAL;
        double distance = BASE_METERS;
        if (s != null && s.mode == 0) {
            long el = now - s.start;
            int off = fromForwardStop(el);
            quality = off <= RELEASE_WINDOW ? PERFECT : off <= NORMAL_WINDOW ? NORMAL : BAD;
            distance = Math.min(lineOut(el, s.maxBeats), max) * QUALITY_KEEP[quality];
            send(sp, s, false);
        } else if (s != null) {
            STATES.put(sp.getUUID(), s);   // the jig is not ours to end
        }
        QUALITY.put(sp.getUUID(), quality);
        return (float) Mth.clamp((distance - 2.0) / Math.max(1e-3, max - 2.0), 0.0, 1.0);
    }

    /** {@link #PERFECT} / {@link #NORMAL} / {@link #BAD} — read once after the cast landed. */
    public static int takeQuality(ServerPlayer sp) {
        Integer q = QUALITY.remove(sp.getUUID());
        return q == null ? NORMAL : q;
    }

    /** The hold ended without a cast: forget the swing and take the gauge off the screen. */
    public static void cancel(ServerPlayer sp) {
        State s = STATES.remove(sp.getUUID());
        QUALITY.remove(sp.getUUID());
        send(sp, s == null ? new State() : s, false);
    }

    // ---- §ice-rhythm: the winter rod's jig on the same needle — the stops are the LIFT and the DROP ----
    public static final int JIG_PERIOD = 16, JIG_MAX = 8;
    public static final float JIG_ZONE_HALF = 0.22f;

    /** The hold began over the hole: the rod starts jigging and the client is told to draw it. */
    public static void beginJig(ServerPlayer sp, long now) {
        State s = new State();
        s.start = now;
        s.maxBeats = JIG_MAX;
        s.mode = 1;
        STATES.put(sp.getUUID(), s);
        send(sp, s, true);
    }

    public static boolean isJigging(ServerPlayer sp) {
        State s = STATES.get(sp.getUUID());
        return s != null && s.mode == 1;
    }

    /** The stroke the jig is on — a new number every stop; -1 when it is not jigging. */
    public static int jigStroke(ServerPlayer sp, long now) {
        State s = STATES.get(sp.getUUID());
        return s == null || s.mode != 1 ? -1 : (int) Math.floorDiv(now - s.start, JIG_PERIOD / 2L);
    }

    /** The accents landed so far — the combo. */
    public static int jigCombo(ServerPlayer sp) {
        State s = STATES.get(sp.getUUID());
        return s == null || s.mode != 1 ? 0 : s.beats;
    }

    /** A left-click on a jig stop: an accent. Anywhere else it is nothing, and costs nothing. */
    public static boolean jigAccent(ServerPlayer sp, long now) {
        State s = STATES.get(sp.getUUID());
        if (s == null || s.mode != 1) return false;
        long el = now - s.start;
        float m = marker(el, JIG_PERIOD);
        float off = Math.min(m, 1f - m);
        int st = (int) Math.floorDiv(el, JIG_PERIOD / 2L);
        if (off <= JIG_ZONE_HALF && st != s.lastEnd && s.beats < s.maxBeats) {
            s.beats++;
            s.lastEnd = st;
            send(sp, s, true);
            return true;
        }
        return false;
    }

    private static void send(ServerPlayer sp, State s, boolean active) {
        ModNetwork.toPlayer(sp, new FlyCastPacket(active, s.start, s.mode == 1 ? JIG_PERIOD : SWING_PERIOD,
                s.mode == 1 ? JIG_ZONE_HALF : RELEASE_WINDOW / (float) (SWING_PERIOD / 2),
                s.beats, s.maxBeats, false, (byte) s.lastEnd, (byte) s.mode));
    }
}
