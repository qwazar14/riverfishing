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
 * <p>Hold use over the hole and the rod works the mormyshka on its own — a stop every half period, the
 * lift and the drop. A left click that lands ON a stop is an accent: it pulls the bite harder and grows
 * the combo, up to {@link #JIG_MAX}. The gauge the client draws is drawn from what this class sends.
 *
 * <p>(It used to live inside the fly rod's cast, which shared the same needle. The fly is gone; the jig
 * was never its.)
 */
public final class JigRhythm {
    public static final int JIG_PERIOD = 16, JIG_MAX = 8;
    public static final float JIG_ZONE_HALF = 0.22f;

    private static final class State {
        long start;
        int beats;          // accents landed
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

    /** The hold began over the hole: the rod starts jigging and the client is told to draw it. */
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

    /** The accents landed so far — the combo. */
    public static int jigCombo(ServerPlayer sp) {
        State s = STATES.get(sp.getUUID());
        return s == null ? 0 : s.beats;
    }

    /** A left click on a jig stop: an accent. Anywhere else it is nothing, and costs nothing. */
    public static boolean jigAccent(ServerPlayer sp, long now) {
        State s = STATES.get(sp.getUUID());
        if (s == null) return false;
        long el = now - s.start;
        float m = marker(el, JIG_PERIOD);
        float off = Math.min(m, 1f - m);
        int st = (int) Math.floorDiv(el, JIG_PERIOD / 2L);
        if (off <= JIG_ZONE_HALF && st != s.lastEnd && s.beats < JIG_MAX) {
            s.beats++;
            s.lastEnd = st;
            send(sp, s, true);
            return true;
        }
        return false;
    }

    /** The hold ended: forget the jig and take the gauge off the screen. */
    public static void cancel(ServerPlayer sp) {
        State s = STATES.remove(sp.getUUID());
        send(sp, s == null ? new State() : s, false);
    }

    private static void send(ServerPlayer sp, State s, boolean active) {
        ModNetwork.toPlayer(sp, new JigGaugePacket(active, s.start, JIG_PERIOD, JIG_ZONE_HALF,
                s.beats, JIG_MAX, (byte) s.lastEnd));
    }
}
