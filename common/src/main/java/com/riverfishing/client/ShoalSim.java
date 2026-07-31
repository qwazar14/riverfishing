package com.riverfishing.client;

import com.riverfishing.network.ShoalPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * §shoal-sim: the fish in the water actually swim, instead of riding a circle.
 *
 * <p>Every earlier version placed a fish by formula — angle from the clock, radius from its lane — which
 * has two consequences you cannot tune away. A fish is wherever the formula says it is this frame, so it
 * can be somewhere impossible (through a bank) and it can jump when any input changes; and a shoal is a
 * carousel, which after ten seconds of watching is exactly what it looks like.
 *
 * <p>So each fish now owns a position and a heading and keeps them. Nothing teleports, because nothing is
 * ever placed: it is only ever moved from where it already was. That single change is what makes arrival
 * and departure smooth, and it is why this exists.
 *
 * <p>Four steers, in the order a fish would care about them:
 * <ol>
 *   <li><b>The bank.</b> {@link ShoalBank} knows how far the water goes on each bearing; a fish that is
 *       running out of room turns before it arrives, hard, and it turns hardest when closest.</li>
 *   <li><b>Fright.</b> A spooked shoal turns away from the player, swims much faster and goes deeper.
 *       This is the one steer that overrides wandering entirely — a frightened fish is not browsing.</li>
 *   <li><b>Home.</b> A gentle pull back toward its patch, so a shoal stays a shoal and does not wander
 *       into the next one over the course of a minute.</li>
 *   <li><b>Wander.</b> Two slow sines at incommensurable rates, phased per fish. Deterministic, free,
 *       and it never repeats visibly — which is all "random" has to mean here.</li>
 * </ol>
 *
 * <p>All of it is client-side and costs nothing the server has to know about: the server says what lives
 * in this water, and the swimming is the client's business.
 */
public final class ShoalSim {
    /** Body lengths per second. A fish cruises at about half its own length per second. */
    private static final double CRUISE = 0.55;
    /** ...and bolts at this multiple of it when frightened. */
    private static final double FLIGHT_SPEED = 3.2;
    /** Radians per second of turn available at cruise; a startled fish turns faster still. */
    private static final float TURN = 1.4f;
    /**
     * Radians per second the wander may swing the heading by — the LAZY turn, as opposed to TURN, which
     * is the corrective one a fish spends on a bank or on a player. At the peak of both sines this is
     * about fourteen degrees a second, and it is nowhere near the peak most of the time.
     */
    private static final float WANDER_TURN = 0.25f;

    /** One fish, as it exists between frames. */
    public static final class Fish {
        public double x, y, z;
        /** Where it is pointing, radians, world yaw. */
        public float heading;
        /** Its own wander phase, so a shoal does not sway in unison. */
        public final float phase;
        /** Cached from the packet so the renderer does not have to look it up. */
        public final ShoalPacket.Entry entry;

        Fish(ShoalPacket.Entry entry, double x, double y, double z, float heading, float phase) {
            this.entry = entry;
            this.x = x;
            this.y = y;
            this.z = z;
            this.heading = heading;
            this.phase = phase;
        }

        /** Metres per second this fish swims at rest. Length drives it, as it does in the water. */
        double cruise() {
            return Math.max(0.25, entry.lengthCm() / 100.0 * CRUISE);
        }
    }

    private ShoalSim() {}

    /**
     * Place a patch's fish for the first time: spread around the centre at their own depth, pointing
     * along the circle so the first second of motion looks deliberate rather than like a scatter.
     */
    public static Fish[] populate(Level level, ShoalPacket.Spot spot) {
        var list = spot.fish();
        Fish[] out = new Fish[list.size()];
        BlockPos c = spot.centre();
        double cx = c.getX() + 0.5, cy = c.getY() + 1.0, cz = c.getZ() + 0.5;
        long seed = c.asLong();
        for (int i = 0; i < out.length; i++) {
            ShoalPacket.Entry e = list.get(i);
            float a = (e.phase() / 64f) * Mth.TWO_PI + (i * 0.7f);
            double y = cy - 0.35 - e.depth();
            double want = 0.9 + (e.lane() + 1) * 0.8;
            double r = Math.min(want, ShoalBank.reach(level, c, y, a));
            out[i] = new Fish(e, cx + Math.cos(a) * r, y, cz + Math.sin(a) * r * 0.75,
                    a + Mth.HALF_PI, ((seed >> (i % 24)) & 63) / 63f * Mth.TWO_PI);
        }
        return out;
    }

    /**
     * Advance one patch by {@code dt} seconds.
     *
     * @param flight 0..1 — how frightened this shoal is (§shoal-spook)
     * @param eye    where the player's head is, to flee from
     */
    public static void advance(Level level, ShoalPacket.Spot spot, Fish[] fish, float flight,
                               Vec3 eye, float time, double dt) {
        if (fish.length == 0) return;
        BlockPos c = spot.centre();
        double cx = c.getX() + 0.5, cy = c.getY() + 1.0, cz = c.getZ() + 0.5;
        double home = Mth.clamp(spot.spread() * 0.9, 1.1, 5.0);
        dt = Math.min(0.1, dt);   // a stutter must not teleport the shoal across the pond

        for (Fish f : fish) {
            float want = f.heading;

            // 1. The bank. How much room is left dead ahead, measured on the bearing it is swimming.
            double reach = ShoalBank.reach(level, c, f.y, f.heading);
            double dx = f.x - cx, dz = (f.z - cz) / 0.75;
            double out = Math.sqrt(dx * dx + dz * dz);
            if (out > reach * 0.75) {
                // Turn back toward open water, harder the closer the bank is. At the very edge this is
                // a full reversal, which is what a fish in the shallows actually does.
                float inward = (float) Math.atan2(-dz * 0.75, -dx);
                want = inward;
            } else {
                // 3. Home, and 4. wander — only when there is room to think about them.
                // NOTE: time is in TICKS (20/s). These rates are per tick — 0.016 is about a tenth
                // of a hertz, which is a fish idly changing its mind, not a fish having a fit.
                float wander = Mth.sin(time * 0.016f + f.phase) * 0.6f
                        + Mth.sin(time * 0.0065f + f.phase * 1.7f) * 0.4f;
                // The wander is a turn RATE, and it has to be written as one. Adding it to the heading
                // as a bare offset asked for a turn of up to half a radian EVERY FRAME — a target the
                // fish could never reach, because it moved with the fish. So the clamp below did all the
                // deciding and the fish turned at its full corrective rate, eighty degrees a second,
                // reversing only when the slow sine changed sign. That is a fish circling every four
                // seconds, which is what "it turns far too often" looks like from the bank.
                //
                // Multiplied by dt it stays under the clamp, so the clamp stops deciding and the sines
                // do: the heading drifts at wander × WANDER_TURN radians a second, framerate-free.
                want = f.heading + wander * WANDER_TURN * (float) dt;
                if (out > home) {
                    float inward = (float) Math.atan2(-dz * 0.75, -dx);
                    want = Mth.rotLerp((float) Mth.clamp((out - home) / 2.0, 0.0, 1.0), want, inward);
                }
            }

            double speed = f.cruise();
            if (flight > 0.02f) {
                // 2. Fright beats everything: away from the player, fast, and down.
                float away = (float) Math.atan2(f.z - eye.z, f.x - eye.x);
                want = Mth.rotLerp(flight, want, away);
                speed *= 1.0 + (FLIGHT_SPEED - 1.0) * flight;
            }

            float turn = TURN * (1f + 2f * flight) * (float) dt;
            f.heading = approach(f.heading, want, turn);
            f.x += Math.cos(f.heading) * speed * dt;
            f.z += Math.sin(f.heading) * speed * dt * 0.75;
            // A fish holds its depth loosely. The rise and fall is part of the TARGET, not something
            // added to the position: adding it per frame made it accumulate with the framerate, which
            // is the hopping. As a target it is a slow, bounded drift the fish eases along, and a
            // frightened one carries the whole band down with it instead of being pushed each tick.
            double restY = cy - 0.35 - f.entry.depth() - 1.1 * flight
                    + Mth.sin(time * 0.035f + f.phase) * 0.16;
            f.y += (restY - f.y) * Math.min(1.0, dt * 1.6);
        }
    }

    /** Turn {@code from} toward {@code to} by at most {@code max} radians, the short way round. */
    private static float approach(float from, float to, float max) {
        float d = Mth.wrapDegrees((float) Math.toDegrees(to - from));
        float rad = (float) Math.toRadians(d);
        return from + Mth.clamp(rad, -max, max);
    }
}
