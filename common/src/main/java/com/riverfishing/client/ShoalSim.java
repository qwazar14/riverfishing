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
    private static final double FLIGHT_SPEED = 2.2;   // a scatter, not a jump-cut (was 3.2)
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
        /** §shoal-kick: 0..1, how hard the tail is beating this frame — the renderer swings it by this. */
        public float kick;
        /** §shoal-jump: nose-up/down, degrees, while in the air; 0 in the water. */
        public float pitch;
        /** §shoal-jump: seconds into a jump, or -1 in the water. */
        public float jumpT = -1f;
        /** §shoal-jump: the game second the next jump is due. */
        public double nextJump = -1;
        /**
         * §shoal-cloud: this fish's own height within its band, blocks. Depths come off the packet as
         * whole blocks, so without this every fish of a school sat on one plane and the school read
         * as a column. A school is a cloud: each member is given its own layer, spread across the
         * block, and a loner a little jitter of its own.
         */
        public double yBias;
        /** §fish-item: the stack the item renderer draws for this fish; built once, on first draw. */
        public net.minecraft.world.item.ItemStack stack;

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
        // Which member of its lane each fish is, so a school can be dealt out along its ring and
        // through its band instead of every member being placed on the same point of both.
        int[] laneSize = new int[8], laneIdx = new int[out.length];
        for (int i = 0; i < out.length; i++) {
            int ln = Math.max(0, Math.min(7, list.get(i).lane()));
            laneIdx[i] = laneSize[ln]++;
        }
        for (int i = 0; i < out.length; i++) {
            ShoalPacket.Entry e = list.get(i);
            int ln = Math.max(0, Math.min(7, e.lane()));
            int n = laneSize[ln], k = laneIdx[i];
            // §shoal-cloud: a school is dealt around its ring — half a radian between members —
            // and each member gets its own layer of the block; a loner gets a little jitter.
            float a = (e.phase() / 64f) * Mth.TWO_PI + (i * 0.7f) + (n > 1 ? (k - (n - 1) / 2f) * 0.5f : 0f);
            double bias = n > 1 ? Mth.clamp((k - (n - 1) / 2.0) * 0.32, -0.75, 0.75)
                    : ((seed >> (i % 20)) & 15) / 15.0 * 0.5 - 0.25;
            double y = Math.min(cy - 0.35 - e.depth() + bias, ceilingY(cy, e));   // §under-water
            double want = 0.9 + (e.lane() + 1) * 0.8 + (n > 1 ? (k % 3) * 0.3 : 0);
            double r = Math.min(want, ShoalBank.reach(level, c, y, a));
            out[i] = new Fish(e, cx + Math.cos(a) * r, y, cz + Math.sin(a) * r * 0.75,
                    a + Mth.HALF_PI, ((seed >> (i % 24)) & 63) / 63f * Mth.TWO_PI);
            out[i].yBias = bias;
        }
        return out;
    }

    /**
     * Advance one patch by {@code dt} seconds.
     *
     * @param flight 0..1 — how frightened this shoal is (§shoal-spook)
     * @param eye    where the player's head is, to flee from
     */
    /** §shoal-school: how far a schooling fish looks for its neighbours, and how close is too close. */
    private static final double SCHOOL_SEE = 2.2, SCHOOL_TOO_CLOSE = 0.75;
    /** §shoal-look: a predator notices a bait this far off, and holds this far short of it. */
    private static final double LOOK_RANGE = 6.0, LOOK_HOLD = 1.1;
    /**
     * §under-water: the highest this fish may sit and still be a fish IN the water — the surface,
     * less half its own body. A surface-lane fish was placed 0.35 under the top and then lifted by
     * up to 0.75 of school bias and 0.16 of drift, and a metre-long pike drawn at its real length
     * stood half out of the lake. Jumps ignore this on purpose: that is the whole point of a jump.
     */
    private static double ceilingY(double cy, ShoalPacket.Entry e) {
        double half = Mth.clamp(e.lengthCm() / 200.0, 0.08, 0.5);
        return cy - 0.30 - half;
    }

    /** §shoal-jump: seconds in the air, and how high the arc goes over the surface. */
    private static final float JUMP_SECONDS = 0.9f, JUMP_HEIGHT = 0.8f;

    public static void advance(Level level, ShoalPacket.Spot spot, Fish[] fish, float flight,
                               Vec3 eye, float time, double dt) {
        advance(level, spot, fish, flight, eye, null, time, dt);
    }

    /**
     * @param bait where this player's own line meets the water, or null — predators go and look
     */
    public static void advance(Level level, ShoalPacket.Spot spot, Fish[] fish, float flight,
                               Vec3 eye, Vec3 bait, float time, double dt) {
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

                // §shoal-school: a fish that moves in numbers keeps its numbers. Three rules, the
                // classic three — line up with the neighbours, close toward their middle, and do
                // not sit on one. The old "school" was three fish given the same phase on one
                // circuit, which held for a minute and then strung out into three loners.
                if (f.entry.shoaling()) {
                    double sx = 0, sz = 0, hx = 0, hz = 0, ax = 0, az = 0;
                    int n = 0;
                    for (Fish o : fish) {
                        if (o == f || o.entry.lane() != f.entry.lane()) continue;
                        double ox = o.x - f.x, oz = o.z - f.z;
                        double d2 = ox * ox + oz * oz;
                        if (d2 > SCHOOL_SEE * SCHOOL_SEE) continue;
                        n++;
                        sx += ox; sz += oz;
                        hx += Math.cos(o.heading); hz += Math.sin(o.heading);
                        if (d2 < SCHOOL_TOO_CLOSE * SCHOOL_TOO_CLOSE && d2 > 1e-6) {
                            ax -= ox / d2; az -= oz / d2;
                        }
                    }
                    if (n > 0) {
                        float align = (float) Math.atan2(hz, hx);
                        float cohere = (float) Math.atan2(sz / n, sx / n);
                        want = Mth.rotLerp(0.35f, want, align);
                        double far = Math.sqrt(sx * sx + sz * sz) / n;
                        if (far > 0.9) want = Mth.rotLerp((float) Math.min(0.5, (far - 0.9) * 0.4), want, cohere);
                        if (ax != 0 || az != 0) want = Mth.rotLerp(0.5f, want, (float) Math.atan2(az, ax));
                    }
                }

                // §shoal-look: a predator that notices a bait goes to see, and holds off it — the
                // picture you get before a take, and the one you never got. Curious for a few
                // seconds, bored for a dozen, on its own clock, so a pike does not park on your
                // float for the whole session.
                if (bait != null && f.entry.predator() && flight < 0.1f) {
                    double bx = bait.x - f.x, bz = bait.z - f.z;
                    double bd = Math.sqrt(bx * bx + bz * bz);
                    boolean curious = ((time / 20f + f.phase * 3f) % 16f) < 5f;
                    if (curious && bd < LOOK_RANGE && bd > LOOK_HOLD) {
                        want = Mth.rotLerp(0.6f, want, (float) Math.atan2(bz, bx));
                    }
                }
                if (out > home) {
                    float inward = (float) Math.atan2(-dz * 0.75, -dx);
                    want = Mth.rotLerp((float) Mth.clamp((out - home) / 2.0, 0.0, 1.0), want, inward);
                }
            }

            // §shoal-kick: a fish does not motor — it beats and glides. The beat is a periodic kick,
            // phased per fish and shaped to be brief; the renderer swings the tail by the same number,
            // so what you see beating is what is pushing.
            float beat = Mth.sin(time * 0.09f + f.phase * 4f);
            f.kick = beat > 0.3f ? (beat - 0.3f) / 0.7f : 0f;
            f.kick = f.kick * f.kick;
            double speed = f.cruise() * (0.7 + 0.9 * f.kick);
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

            // §shoal-jump: a carp rolls, a salmon clears the water. Rare, on the fish's own clock,
            // never while frightened, and never for a fish drawn far under the surface. The arc is a
            // parabola over the surface with the nose following it; the splash is the water's, in
            // and out.
            if (f.entry.jumper()) {
                double sec = time / 20.0;
                if (f.nextJump < 0) f.nextJump = sec + 20 + f.phase * 12;
                if (f.jumpT < 0 && flight < 0.05f && f.entry.depth() <= 2 && sec >= f.nextJump) {
                    f.jumpT = 0f;
                    splash(level, f.x, cy, f.z);
                }
                if (f.jumpT >= 0f) {
                    f.jumpT += (float) dt;
                    float u = f.jumpT / JUMP_SECONDS;
                    if (u >= 1f) {
                        f.jumpT = -1f;
                        f.pitch = 0f;
                        f.nextJump = sec + 45 + f.phase * 15;
                        splash(level, f.x, cy, f.z);
                    } else {
                        float arc = 4f * u * (1f - u);
                        f.y = cy + arc * JUMP_HEIGHT - 0.1;
                        f.pitch = -(1f - 2f * u) * 40f;
                        f.x += Math.cos(f.heading) * speed * dt;   // it keeps its way in the air
                        f.z += Math.sin(f.heading) * speed * dt * 0.75;
                        continue;
                    }
                }
            }
            // A fish holds its depth loosely. The rise and fall is part of the TARGET, not something
            // added to the position: adding it per frame made it accumulate with the framerate, which
            // is the hopping. As a target it is a slow, bounded drift the fish eases along, and a
            // frightened one carries the whole band down with it instead of being pushed each tick.
            double restY = Math.min(cy - 0.35 - f.entry.depth() + f.yBias
                    + Mth.sin(time * 0.035f + f.phase) * 0.16, ceilingY(cy, f.entry)) - 1.1 * flight;
            f.y += (restY - f.y) * Math.min(1.0, dt * 1.6);
        }
    }

    /** §shoal-jump: the water's own splash, at the surface, client-side only. */
    private static void splash(Level level, double x, double y, double z) {
        for (int i = 0; i < 6; i++) {
            level.addParticle(net.minecraft.core.particles.ParticleTypes.SPLASH,
                    x + (Math.random() - 0.5) * 0.6, y + 0.05, z + (Math.random() - 0.5) * 0.6, 0, 0.1, 0);
        }
        level.playLocalSound(x, y, z, net.minecraft.sounds.SoundEvents.FISHING_BOBBER_SPLASH,
                net.minecraft.sounds.SoundSource.NEUTRAL, 0.35f, 1.3f + (float) Math.random() * 0.3f, false);
    }

    /** Turn {@code from} toward {@code to} by at most {@code max} radians, the short way round. */
    private static float approach(float from, float to, float max) {
        float d = Mth.wrapDegrees((float) Math.toDegrees(to - from));
        float rad = (float) Math.toRadians(d);
        return from + Mth.clamp(rad, -max, max);
    }
}
