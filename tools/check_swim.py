# -*- coding: utf-8 -*-
"""§swim-inertia: the fish speeds up the way a fish speeds up, at any framerate.

    py -X utf8 tools/check_swim.py

Nothing here imports Minecraft. The beat, the swing and the two lags are read straight out of
ShoalSim.java and the swim is re-integrated in Python, so retuning the water in Java retunes this file
with it — and a change that brings the lurch back fails here instead of on the bank.

Three things have to hold.

1. The acceleration is BOUNDED. The complaint was a jerky speed-up; the old model took the tail beat as
   the velocity outright, so every beat was a step change and the peak acceleration was whatever the
   beat's own slope happened to be. With inertia under it the peak comes down by more than half.
2. It is the same swim at 20 fps and at 240. A lag written as a fixed fraction per frame is not — that
   is the bug this file exists to keep out, and the mod has had it twice before.
3. A jumper moves ONCE per frame. The jump branch used to add a second `speed * dt` on top of the move
   every fish had already made, which doubled a jumper's speed for the length of the jump.
"""
import io, math, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = io.open(os.path.join(ROOT, "common/src/main/java/com/riverfishing/client/ShoalSim.java"),
              encoding="utf-8").read()

fails = []


def die(msg):
    fails.append(msg)


def num(pattern, what):
    m = re.search(pattern, SRC)
    if not m:
        print("FAILED: cannot read %s out of ShoalSim.java (%s)" % (what, pattern))
        sys.exit(1)
    return float(m.group(1))


CRUISE = num(r"double CRUISE = ([\d.]+);", "CRUISE")
BEAT_LOW = num(r"double BEAT_LOW = ([\d.]+), BEAT_SWING", "BEAT_LOW")
BEAT_SWING = num(r"BEAT_SWING = ([\d.]+);", "BEAT_SWING")
SPEED_LAG = num(r"double SPEED_LAG = ([\d.]+), FLIGHT_LAG", "SPEED_LAG")
FLIGHT_LAG = num(r"FLIGHT_LAG = ([\d.]+);", "FLIGHT_LAG")
RATE = num(r"Mth\.sin\(time \* ([\d.]+)f \+ f\.phase \* 4f\)", "the beat rate")
GATE = num(r"beat > ([\d.]+)f \? \(beat", "the beat's gate")

TICKS = 20.0            # the beat is written against the game clock, which is 20 a second
W = RATE * TICKS        # radians a second


def kick(t):
    beat = math.sin(W * t)
    r = (beat - GATE) / (1.0 - GATE) if beat > GATE else 0.0
    return r * r


def demand(t, old=False):
    """What the beat asks for, as a multiple of the fish's cruise."""
    return (0.7 + 0.9 * kick(t)) if old else (BEAT_LOW + BEAT_SWING * kick(t))


def swim(dt, seconds=24.0, lag=None, old=False):
    """Integrate the speed. Returns (peak |dv/dt| per second, distance, min, max), in cruise units."""
    v = demand(0.0, old)
    t, dist, peak, lo, hi = 0.0, 0.0, 0.0, v, v
    while t < seconds:
        want = demand(t, old)
        nv = want if (old or lag is None) else v + (want - v) * min(1.0, dt / lag)
        peak = max(peak, abs(nv - v) / dt)
        v = nv
        dist += v * dt
        lo, hi = min(lo, v), max(hi, v)
        t += dt
    return peak, dist, lo, hi


# ---- 1. the acceleration is bounded ---------------------------------------------------------------
dt = 1 / 60.0
old_peak = swim(dt, old=True)[0]
new_peak, _, lo, hi = swim(dt, lag=SPEED_LAG)
if new_peak > old_peak * 0.4:
    die("peak acceleration is %.2f cruise/s2, only %.0f%% down from the old %.2f — still a lurch"
        % (new_peak, 100 * (1 - new_peak / old_peak), old_peak))
if hi / lo > 1.9:
    die("the fish swims between %.2f and %.2f of its cruise — a factor of %.1f reads as a lurch"
        % (lo, hi, hi / lo))
if lo <= 0.0:
    die("the fish stops dead in the glide (%.2f of cruise)" % lo)

# a bolt still has to be a bolt: the short lag reaches most of a doubled demand inside a third of a second
v, t = 1.0, 0.0
while t < 0.30:
    v += (2.2 - v) * min(1.0, dt / FLIGHT_LAG)
    t += dt
if v < 1.9:
    die("a frightened fish only reaches %.2f of a 2.2 demand in a third of a second — FLIGHT_LAG=%.2f "
        "is too slow to be a bolt" % (v, FLIGHT_LAG))

# ---- 2. the same swim at any framerate -------------------------------------------------------------
slow = swim(1 / 20.0, lag=SPEED_LAG)[1]
fast = swim(1 / 240.0, lag=SPEED_LAG)[1]
if abs(slow - fast) / fast > 0.02:
    die("20 fps swims %.1f cruise-seconds and 240 fps swims %.1f — %.1f%% apart, the lag is framerate-bound"
        % (slow, fast, 100 * abs(slow - fast) / fast))

# ---- 3. a jumper moves once ------------------------------------------------------------------------
jump = SRC[SRC.index("if (f.entry.jumper())"):SRC.index("// A fish holds its depth loosely")]
if "f.x +=" in jump or "f.z +=" in jump:
    die("the jump branch moves the fish again — a jumper's speed doubles for the length of the jump")
if "f.speed" not in SRC or "dt / tau" not in SRC:
    die("the speed is not a state with a lag any more; §swim-inertia is gone")

if fails:
    print("FAILED:")
    for x in fails:
        print("  " + x)
    sys.exit(1)
print("swim: beat %.2f..%.2f of cruise every %.1fs, lag %.2fs (bolt %.2fs); peak accel %.2f cruise/s2, "
      "was %.2f; 20 and 240 fps agree to %.2f%%"
      % (BEAT_LOW, BEAT_LOW + BEAT_SWING, 2 * math.pi / W, SPEED_LAG, FLIGHT_LAG,
         new_peak, old_peak, 100 * abs(slow - fast) / fast))
