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
 * §fly: the rhythm cast. Holding use on a fly rod swings a needle end to end at the rod's period; the
 * two ends are the two STOPS of a real cast (the backcast stops behind you, the forward cast stops in
 * front). A sneak tap on a stop is a false cast — one more block of line in the air. Releasing use on a
 * stop delivers a tight loop; releasing between them dumps the line (a splash), and releasing well away
 * from either is a tailing loop that knots the tippet. Everything is judged on the server's clock.
 *
 * <p>ponytail: the beat is timed when the packet ARRIVES, so a laggy link eats into a 0.16 zone
 * (~1.4 ticks each side at period 18). Put the client's own tick in FlyBeatPacket if remote play needs it.
 */
public final class FlyCast {
    /** Ticks for one full sweep (end → end → back). */
    public static final int PERIOD = 18;
    /** How close to an end a tap or the release must be, in needle units (0..1). */
    public static final float ZONE_HALF = 0.16f;
    /** Further than this from either end on the release = a tailing loop = a wind knot. */
    public static final float KNOT_OFF = 0.35f;
    /**
     * Blocks of line already out of the tip when the rhythm starts. The design's "castRangeBase + one
     * per beat, capped at castRangeMax" reads as this pickup length growing into the rod's reach —
     * castRangeBase(FLY) IS castRangeMax for a fly rod (no cast weight), so starting there would leave
     * the false casts nothing to add.
     */
    public static final double PICKUP = 6.0;

    private static final class State {
        long start;
        int beats;
        int maxBeats;
        boolean openLoop;
        /** The end the last GOOD beat landed on (0 or 1), -1 when either end is fair game. */
        int lastEnd = -1;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    /** The delivery's quality, kept until FishingManager reads it once after the cast lands. */
    private static final Map<UUID, Integer> QUALITY = new HashMap<>();

    private FlyCast() {}

    /** Triangle wave 0..1, matching {@link com.riverfishing.client.FloatTimingClient}. */
    private static float marker(long elapsed, int period) {
        if (period <= 0) return 0.5f;
        float phase = (Math.floorMod(elapsed, period)) / (float) period;
        return phase < 0.5f ? phase * 2f : 2f - phase * 2f;
    }

    private static ItemStack flyRod(ServerPlayer sp) {
        ItemStack main = sp.getMainHandItem();
        if (main.getItem() instanceof RodItem ri && ri.rodType() == RodType.FLY) return main;
        return sp.getOffhandItem();
    }

    /** The hold began: the needle starts its sweep and the client is told to draw it. */
    public static void begin(ServerPlayer sp, long now) {
        State s = new State();
        s.start = now;
        s.maxBeats = (int) Math.max(0, Math.floor(FishingManager.castRangeMax(flyRod(sp)) - PICKUP));
        STATES.put(sp.getUUID(), s);
        QUALITY.remove(sp.getUUID());
        send(sp, s, true);
    }

    /** A sneak tap while holding: a stop of the backcast or the forward cast. */
    public static void beat(ServerPlayer sp, long now) {
        State s = STATES.get(sp.getUUID());
        if (s == null) return;
        float m = marker(now - s.start, PERIOD);
        int end = m < 0.5f ? 0 : 1;
        float off = Math.min(m, 1f - m);
        if (off <= ZONE_HALF && end != s.lastEnd) {
            s.beats = Math.min(s.maxBeats, s.beats + 1);
            s.openLoop = false;     // one clean stop closes the loop again — the rhythm is recoverable
            s.lastEnd = end;
        } else {
            s.beats = 0;            // the loop collapsed: the line in the air is lost, start over
            s.openLoop = true;
            s.lastEnd = -1;
        }
        send(sp, s, true);
    }

    /**
     * The delivery. Returns the power for {@code chargedCast} — the inverse of {@code castDistance}, so
     * the line lands at exactly the metres the gauge showed — and remembers the loop's quality.
     */
    public static float release(ServerPlayer sp, ItemStack rod, long now) {
        State s = STATES.remove(sp.getUUID());
        double max = FishingManager.castRangeMax(rod);
        int quality = 0;
        double distance = PICKUP;
        if (s != null) {
            float m = marker(now - s.start, PERIOD);
            int end = m < 0.5f ? 0 : 1;
            float off = Math.min(m, 1f - m);
            // the delivery is the NEXT stop: on it with the rhythm intact = tight; anything else dumps the
            // line (a splash); and a rhythm already broken, dumped between the stops, knots the tippet
            boolean onStop = off <= ZONE_HALF && end != s.lastEnd;
            if (onStop && !s.openLoop) quality = 0;
            else if (s.openLoop && off > KNOT_OFF) quality = 2;
            else quality = 1;
            distance = Math.min(PICKUP + s.beats, max);
            if (quality != 0) distance *= 0.6;
            send(sp, s, false);
        }
        QUALITY.put(sp.getUUID(), quality);
        return (float) Mth.clamp((distance - 2.0) / Math.max(1e-3, max - 2.0), 0.0, 1.0);
    }

    /** 0 tight, 1 splash, 2 wind knot — read once after startCast succeeded, then cleared. */
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

    private static void send(ServerPlayer sp, State s, boolean active) {
        ModNetwork.toPlayer(sp, new FlyCastPacket(active, s.start, PERIOD, ZONE_HALF, s.beats, s.maxBeats, s.openLoop, (byte) s.lastEnd));
    }
}
