# -*- coding: utf-8 -*-
"""§tail-phase: a phase is integrated, never multiplied — the fish stop convulsing.

    py -X utf8 tools/patches/p_tailphase.py <root> [1211|1201|26]

Reported twice: when a fish speeds up its animation goes strange, "like it is having convulsions". The
first report I answered by fixing the SPEED — which was also wrong, and was not this. This is the tail.

The tail sweep was written as

    float phase = time * (0.35f + 0.45f * f.kick) + f.phase * 5f;

— a sine whose FREQUENCY is modulated by the beat. That is not how you modulate a frequency. The
argument of the sine has to be the INTEGRAL of the rate, and this is the rate times the clock, so the
instant the rate changes by dw the whole accumulated argument jumps by dw x time. `time` is the game
clock, in the thousands and climbing: a beat that lifts the rate by 0.45 moves the tail's phase by
several hundred radians, i.e. to a random angle, and it does it twice a beat forever. On the bank that
reads as a fish having a fit exactly when it should be swimming hardest.

So the tail carries its own phase now, advanced by the current rate each frame and wrapped at 2*pi.
Same numbers, same beat, and the sweep is continuous through every change of speed — which is what a
tail does. The three places that drew it (the flat sprite, the 3D body, the mesh) all read the one
phase, so they cannot drift apart either.
"""
import io, os, sys

ROOT = sys.argv[1]
D = sys.argv[2] if len(sys.argv) > 2 else "1211"
J = os.path.join(ROOT, "common/src/main/java/com/riverfishing/client/")

OLD = "time * (0.35f + 0.45f * f.kick) + f.phase * 5f"
NEW = "f.tail + f.phase * 5f"


def rd(p): return io.open(p, encoding="utf-8").read()


def wr(p, s): io.open(p, "w", encoding="utf-8", newline="\n").write(s)


# ---- 1. the fish owns its tail phase --------------------------------------------------------------
p = J + "ShoalSim.java"
s = rd(p)
if "tail-phase" not in s:
    old = """        /** §swim-inertia: metres a second, right now — eased toward what the beat asks for, never taken. */
        public double speed;"""
    assert old in s, "the speed field moved"
    s = s.replace(old, old + """
        /**
         * §tail-phase: where the tail is in its sweep, radians, wrapped. NOT {@code time × rate} — the
         * rate moves with the beat, and rate × clock jumps by (change in rate) × clock the instant it
         * moves. The clock is in the thousands, so that is a new random angle rather than a wobble, and
         * it lands exactly when the fish speeds up. A phase is integrated; this is where.
         */
        public float tail;""", 1)

    old = """    private static final float WANDER_TURN = 0.25f;"""
    assert old in s, "WANDER_TURN moved"
    s = s.replace(old, old + """
    /**
     * §tail-phase: radians a TICK the tail sweeps, gliding and at full drive — the two numbers that
     * used to sit inline in three renderers. Kept per tick because that is the clock they were tuned
     * against; the integration below converts.
     */
    private static final float TAIL_BASE = 0.35f, TAIL_SWING = 0.45f;""", 1)

    old = """            f.kick = f.kick * f.kick;"""
    assert old in s, "the kick moved"
    s = s.replace(old, old + """
            // §tail-phase: integrate the sweep at the rate the beat asks for THIS frame, and wrap it.
            // Done here, before anything can `continue` past it, so a jumping fish keeps beating too.
            f.tail = (f.tail + (TAIL_BASE + TAIL_SWING * f.kick) * 20f * (float) dt) % Mth.TWO_PI;""", 1)
    wr(p, s)
    print("  ShoalSim: the tail carries its own phase")

# ---- 2. everything that draws a tail reads it -----------------------------------------------------
for name in ("ShoalRenderer.java", "FishMesh.java"):
    p = J + name
    s = rd(p)
    if OLD not in s:
        print("  %s: nothing to fix" % name)
        continue
    n = s.count(OLD)
    s = s.replace(OLD, NEW)
    wr(p, s)
    print("  %s: %d sweep%s off the integrated phase" % (name, n, "" if n == 1 else "s"))
print("done (%s)" % D)
