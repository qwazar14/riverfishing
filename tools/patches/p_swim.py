# -*- coding: utf-8 -*-
"""§swim-inertia: a fish gets up to speed, it does not change speed.

    py -X utf8 tools/patches/p_swim.py <root> [1211|1201|26]

Reported from the bank: the fish in the water sometimes speed up, and the speeding up is JERKY.

It was, and there were two reasons.

1. The speed was whatever the tail beat said THIS INSTANT — `cruise * (0.7 + 0.9 * kick)`, read fresh
   every frame and used as the velocity directly. A fish has mass and water has drag: a tail beat is a
   force, and the fish reaches the speed it implies over a body length of swimming. Written as a
   velocity it is a step change instead, twice a beat, forever. So the speed is now a state the fish
   owns and eases toward the beat's demand — slowly at cruise, sharply when bolting, because a
   frightened fish really does hit its speed almost at once.

2. A JUMPER MOVED TWICE. The jump branch added another `speed * dt` "so it keeps its way in the air"
   on top of the move every fish had already made that frame: the horizontal speed doubled the instant
   a jump started and halved the instant it ended. That is exactly "sometimes it speeds up", it is
   entirely wrong, and it is one line to delete — the way it already had was the first move.

The beat's own swing comes down with it (2.3x between glide and drive was a lurch even with inertia
under it), and both ends are named constants because water is a thing you tune by looking at it.
"""
import io, os, sys

ROOT = sys.argv[1]
D = sys.argv[2] if len(sys.argv) > 2 else "1211"
P = os.path.join(ROOT, "common/src/main/java/com/riverfishing/client/ShoalSim.java")

s = io.open(P, encoding="utf-8").read()
if "swim-inertia" in s:
    print("  already patched")
    sys.exit(0)

# ---- 1. the constants ---------------------------------------------------------------------------
old = """    /** Radians per second of turn available at cruise; a startled fish turns faster still. */
    private static final float TURN = 1.4f;"""
assert old in s, "TURN moved"
s = s.replace(old, """    /** Radians per second of turn available at cruise; a startled fish turns faster still. */
    private static final float TURN = 1.4f;
    /**
     * §swim-inertia: what one tail beat is worth. The fish glides at {@code BEAT_LOW} of its cruise and
     * drives at {@code BEAT_LOW + BEAT_SWING} of it, and the swing used to be 0.7..1.6 — a factor of
     * 2.3 between one half of a beat and the other, which is a lurch and not a swim.
     */
    private static final double BEAT_LOW = 0.78, BEAT_SWING = 0.55;
    /**
     * §swim-inertia: seconds to reach a new speed, cruising and bolting. The beat is a FORCE; the speed
     * is what the fish gets to over about a body length of swimming. Taking the beat as the velocity
     * outright — which is what this did — is a step change in velocity twice a beat, and that is what
     * "the acceleration is jerky" looks like from the bank. A frightened fish is allowed to be abrupt:
     * that is what a bolt is.
     */
    private static final double SPEED_LAG = 0.55, FLIGHT_LAG = 0.12;""", 1)

# ---- 2. the fish owns its speed -----------------------------------------------------------------
old = """        /** §shoal-kick: 0..1, how hard the tail is beating this frame — the renderer swings it by this. */
        public float kick;"""
assert old in s, "the kick field moved"
s = s.replace(old, old + """
        /** §swim-inertia: metres a second, right now — eased toward what the beat asks for, never taken. */
        public double speed;""", 1)

old = """            this.heading = heading;
            this.phase = phase;
        }"""
assert old in s, "the Fish constructor moved"
s = s.replace(old, """            this.heading = heading;
            this.phase = phase;
            this.speed = cruise();     // §swim-inertia: it is already swimming when you first see it
        }""", 1)

# ---- 3. the beat asks, the fish answers over time ------------------------------------------------
old = """            double speed = f.cruise() * (0.7 + 0.9 * f.kick);
            if (flight > 0.02f) {
                // 2. Fright beats everything: away from the player, fast, and down.
                float away = (float) Math.atan2(f.z - eye.z, f.x - eye.x);
                want = Mth.rotLerp(flight, want, away);
                speed *= 1.0 + (FLIGHT_SPEED - 1.0) * flight;
            }"""
assert old in s, "the speed block moved"
s = s.replace(old, """            double demand = f.cruise() * (BEAT_LOW + BEAT_SWING * f.kick);
            if (flight > 0.02f) {
                // 2. Fright beats everything: away from the player, fast, and down.
                float away = (float) Math.atan2(f.z - eye.z, f.x - eye.x);
                want = Mth.rotLerp(flight, want, away);
                demand *= 1.0 + (FLIGHT_SPEED - 1.0) * flight;
            }
            // §swim-inertia: and the fish eases onto it. Framerate-free — dt/tau, not a fixed fraction
            // a frame — so the same fish swims the same way at 30 and at 300.
            double tau = Mth.lerp((double) flight, SPEED_LAG, FLIGHT_LAG);
            f.speed += (demand - f.speed) * Math.min(1.0, dt / tau);
            double speed = f.speed;""", 1)

# ---- 4. a jumper moved twice ---------------------------------------------------------------------
old = """                        f.pitch = -(1f - 2f * u) * 40f;
                        f.x += Math.cos(f.heading) * speed * dt;   // it keeps its way in the air
                        f.z += Math.sin(f.heading) * speed * dt * 0.75;
                        continue;"""
assert old in s, "the jump arc moved"
s = s.replace(old, """                        f.pitch = -(1f - 2f * u) * 40f;
                        // §swim-inertia: it keeps the way it already has — the move at the top of the
                        // loop was that move. Adding a second one here doubled a jumper's speed for the
                        // nine tenths of a second it was in the air and halved it on the way in.
                        continue;""", 1)

io.open(P, "w", encoding="utf-8", newline="\n").write(s)
print("  ShoalSim: speed is a state with a lag, the beat swings 0.78..1.33, a jumper moves once")
print("done (%s)" % D)
