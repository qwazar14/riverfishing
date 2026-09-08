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
 * §fly-2 (0.10.0): the cast, the way a real one is taught. Hold use and the rod false-casts on its own —
 * back, stop, forward, stop — and every stop carries two more metres of line into the air, up to what the
 * rod can hold. The needle on the gauge is the rod: it sweeps from the backcast stop (left) to the forward
 * stop (right) and back, once every {@link #PERIOD} ticks. <b>Release on the forward stop</b> (the green
 * at the right end) and the loop unrolls tight and lands soft. Release a little early and the loop opens —
 * the line falls short. Release with the rod behind you and the whole thing piles up on the water.
 *
 * <p>The only skill input besides the timing of the release is the <b>haul</b>: a left-click as the needle
 * touches either stop pulls line with the other hand — two metres more in the air, at once. A haul
 * anywhere else does nothing at all; there is no way to break the cast by trying.
 *
 * <p>The same class runs the winter rod's jig (mode 1) and carries the drift's status to the HUD (mode 2),
 * all on the one packet.
 */
public final class FlyCast {
    /** Ticks for one full sweep: backcast stop → forward stop → back. A stop every half of it. */
    public static final int PERIOD = 32;
    /** How close to a stop, in needle units 0..1, the release (or a haul) has to be. */
    public static final float ZONE_HALF = 0.18f;
    /** Releasing this far from the forward stop still flies, open — beyond it the line piles. */
    public static final float OPEN_HALF = 0.42f;
    /** Metres of line already out of the tip when the hold begins. */
    public static final double PICKUP = 6.0;
    /** Metres each stop adds while the rod false-casts, and each haul on top. */
    public static final double STROKE_GAIN = 2.0, HAUL_GAIN = 2.0;

    private static final class State {
        long start;
        int beats;          // cast: hauls landed; jig: strokes in rhythm
        int maxBeats;       // cast: the rod's reach in metres; jig: JIG_MAX
        boolean openLoop;   // unused since §jig-2 — kept on the wire for the packet's shape
        int lastEnd = -1;   // the stroke index of the last haul / accent
        int mode;           // 0 the fly cast, 1 the jig
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    /** The delivery's quality, kept until FishingManager reads it once after the cast lands. */
    private static final Map<UUID, Integer> QUALITY = new HashMap<>();

    private FlyCast() {}

    /** Triangle wave 0..1: 0 at the backcast stop, 1 at the forward stop. Matches the client's. */
    public static float marker(long elapsed, int period) {
        if (period <= 0) return 0.5f;
        float phase = (Math.floorMod(elapsed, period)) / (float) period;
        return phase < 0.5f ? phase * 2f : 2f - phase * 2f;
    }

    /** Which stop the needle is on or heading away from: the stroke index, one per half sweep. */
    private static int stroke(long elapsed) {
        return (int) Math.floorDiv(elapsed, PERIOD / 2L);
    }

    /** Metres of line in the air after this many stops and hauls, capped at the rod's reach. */
    public static double lineOut(long elapsed, int hauls, int maxMetres) {
        return Math.min(maxMetres, PICKUP + STROKE_GAIN * Math.max(0, stroke(elapsed)) + HAUL_GAIN * hauls);
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
        s.maxBeats = (int) Math.max(PICKUP, Math.floor(FishingManager.castRangeMax(flyRod(sp))));
        STATES.put(sp.getUUID(), s);
        QUALITY.remove(sp.getUUID());
        send(sp, s, true);
    }

    /**
     * A left-click while the rod works: on the cast a haul if the needle is on a stop that has not been
     * hauled yet — two metres more, at once; on the jig an accent on a stop — the combo grows and the bite
     * comes closer. Anywhere else it is nothing, and costs nothing. Returns whether it landed.
     */
    public static boolean beat(ServerPlayer sp, long now) {
        State s = STATES.get(sp.getUUID());
        if (s == null) return false;
        int period = s.mode == 1 ? JIG_PERIOD : PERIOD;
        float zone = s.mode == 1 ? JIG_ZONE_HALF : ZONE_HALF;
        long el = now - s.start;
        float m = marker(el, period);
        float off = Math.min(m, 1f - m);
        int st = (int) Math.floorDiv(el, period / 2L);
        boolean room = s.mode == 1 ? s.beats < s.maxBeats : lineOut(el, s.beats, s.maxBeats) < s.maxBeats;
        if (off <= zone && st != s.lastEnd && room) {
            s.beats++;
            s.lastEnd = st;
            send(sp, s, true);
            return true;
        }
        return false;
    }

    /**
     * The delivery. Returns the power for {@code chargedCast} — the inverse of {@code castDistance}, so
     * the line lands at the metres the gauge showed — and remembers the loop's quality: 0 tight (on the
     * forward stop), 1 open (near it: 85 % of the line), 2 piled (the rod was behind you: 60 %, a slap).
     */
    public static float release(ServerPlayer sp, ItemStack rod, long now) {
        State s = STATES.remove(sp.getUUID());
        double max = FishingManager.castRangeMax(rod);
        int quality = 0;
        double distance = PICKUP;
        if (s != null && s.mode == 0) {
            long el = now - s.start;
            float m = marker(el, PERIOD);
            float fromForward = 1f - m;
            quality = fromForward <= ZONE_HALF ? 0 : fromForward <= OPEN_HALF ? 1 : 2;
            distance = Math.min(lineOut(el, s.beats, s.maxBeats), max);
            if (quality == 1) distance *= 0.85;
            if (quality == 2) distance *= 0.6;
            send(sp, s, false);
        } else if (s != null) {
            STATES.put(sp.getUUID(), s);   // the jig is not ours to end
        }
        QUALITY.put(sp.getUUID(), quality);
        return (float) Mth.clamp((distance - 2.0) / Math.max(1e-3, max - 2.0), 0.0, 1.0);
    }

    /** 0 tight, 1 open, 2 piled — read once after startCast succeeded, then cleared. */
    public static int takeQuality(ServerPlayer sp) {
        Integer q = QUALITY.remove(sp.getUUID());
        return q == null ? 0 : q;
    }

    /** The hold ended without a cast: forget the rhythm and take the gauge off the screen. */
    public static void cancel(ServerPlayer sp) {
        State s = STATES.remove(sp.getUUID());
        QUALITY.remove(sp.getUUID());
        send(sp, s == null ? new State() : s, false);
    }

    // ---- §fly-2: the drift's status on the HUD, on the same packet (mode 2) ----

    /** 0 dead drift, 1 dragging, 2 the line straight below; {@code onRise} = the fly is over a feeding fish. */
    public static void drift(ServerPlayer sp, int state, int metres, boolean onRise) {
        ModNetwork.toPlayer(sp, new FlyCastPacket(true, 0L, 0, 0f, state, metres, onRise, (byte) 0, (byte) 2));
    }

    public static void driftOff(ServerPlayer sp) {
        ModNetwork.toPlayer(sp, new FlyCastPacket(false, 0L, 0, 0f, 0, 0, false, (byte) 0, (byte) 2));
    }

    // ---- §jig-2: the winter rod's jig on the same needle, by the same rule — hold, and accent on a stop ----
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

    private static void send(ServerPlayer sp, State s, boolean active) {
        ModNetwork.toPlayer(sp, new FlyCastPacket(active, s.start, s.mode == 1 ? JIG_PERIOD : PERIOD,
                s.mode == 1 ? JIG_ZONE_HALF : ZONE_HALF, s.beats, s.maxBeats, s.openLoop, (byte) s.lastEnd, (byte) s.mode));
    }
}
