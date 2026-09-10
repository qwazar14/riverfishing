package com.riverfishing.fishing;

import com.riverfishing.network.JigGaugePacket;
import com.riverfishing.network.ModNetwork;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * §ice-rhythm: the winter rod's jig.
 *
 * <p>Hold use and the rod works the mormyshka on its own — a stop every half period, the lift and the
 * drop. A left click that lands ON a stop is an accent: it pulls the bite harder and grows the combo.
 * A click anywhere else is a MISS: the combo goes back to nothing and the fish backs off the bait.
 *
 * <p>§jig-3: the combo has no ceiling any more. Winter fishing is an arcade rhythm — the run is as long
 * as the player can keep it, and {@link #JIG_MAX} is now only the number the advancement asks for. The
 * old cap of eight stopped the game dead at eight clicks, which is exactly what nobody wanted.
 *
 * <p>(It used to live inside the fly rod's cast, which shared the same needle. The fly is gone; the jig
 * was never its.)
 */
public final class JigRhythm {
    public static final int JIG_PERIOD = 16;
    /** §jig-3: the combo the "In Time" advancement asks for — NOT a cap on the combo itself. */
    public static final int JIG_MAX = 8;
    /**
     * §jig-3: how much of the sweep either stop counts as. Was 0.22 — a ±1.8 tick window, which a
     * player on any ping could see themselves hitting and still be judged late, and that is what the
     * "the highlight doesn't match the hitbox" report was. ±2.4 ticks is still a rhythm, not a hold.
     */
    public static final float JIG_ZONE_HALF = 0.30f;

    /** A click on the beat. */
    public static final int ACCENT = 1;
    /** A click nowhere near it — the combo is gone. */
    public static final int MISS = -1;
    /** A second click on a stop already accented: not a beat, but not a mistake either. */
    public static final int IGNORED = 0;

    private static final class State {
        long start;
        int beats;          // accents landed in a row — the combo
        int lastEnd = -1;   // the stroke index of the last accent
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private JigRhythm() {}

    /** Triangle wave 0..1 across one full period. The client draws the same. */
    public static float marker(long elapsed, int period) {
        if (period <= 0) return 0.5f;
        float phase = (Math.floorMod(elapsed, period)) / (float) period;
        return phase < 0.5f ? phase * 2f : 2f - phase * 2f;
    }

    /** The hold began: the rod starts jigging and the client is told to draw it. */
    public static void beginJig(ServerPlayer sp, long now) {
        State s = new State();
        s.start = now;
        STATES.put(sp.getUUID(), s);
        send(sp, s, true);
    }

    public static boolean isJigging(ServerPlayer sp) {
        return STATES.containsKey(sp.getUUID());
    }

    /** The stroke the jig is on — a new number every stop; -1 when it is not jigging. */
    public static int jigStroke(ServerPlayer sp, long now) {
        State s = STATES.get(sp.getUUID());
        return s == null ? -1 : (int) Math.floorDiv(now - s.start, JIG_PERIOD / 2L);
    }

    /** The accents landed in a row — the combo. */
    public static int jigCombo(ServerPlayer sp) {
        State s = STATES.get(sp.getUUID());
        return s == null ? 0 : s.beats;
    }

    /**
     * A left click while the rod jigs: {@link #ACCENT} on a stop, {@link #MISS} anywhere else,
     * {@link #IGNORED} for the second click on a stop already counted (a stop is worth one beat, but
     * a fumbled double-tap should not cost the run).
     *
     * <p>A miss deliberately does NOT claim the stroke: spamming the button is a string of misses, and
     * a string of misses is a fish that leaves.
     */
    public static int jigAccent(ServerPlayer sp, long now) {
        State s = STATES.get(sp.getUUID());
        if (s == null) return IGNORED;
        long el = now - s.start;
        float m = marker(el, JIG_PERIOD);
        float off = Math.min(m, 1f - m);
        int st = (int) Math.floorDiv(el, JIG_PERIOD / 2L);
        if (off <= JIG_ZONE_HALF) {
            if (st == s.lastEnd) return IGNORED;
            s.beats++;
            s.lastEnd = st;
            send(sp, s, true);
            return ACCENT;
        }
        s.beats = 0;
        send(sp, s, true);
        return MISS;
    }

    /** The hold ended: forget the jig and take the gauge off the screen. */
    public static void cancel(ServerPlayer sp) {
        State s = STATES.remove(sp.getUUID());
        send(sp, s == null ? new State() : s, false);
    }

    private static void send(ServerPlayer sp, State s, boolean active) {
        ModNetwork.toPlayer(sp, new JigGaugePacket(active, s.start, JIG_PERIOD, JIG_ZONE_HALF, s.beats));
    }
}
