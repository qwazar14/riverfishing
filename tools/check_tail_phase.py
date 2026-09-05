# -*- coding: utf-8 -*-
"""§tail-phase: the tail's sweep is continuous, at any age of world.

    py -X utf8 tools/check_tail_phase.py

The tail used to be drawn as `sin(time * (BASE + SWING * kick) + offset)` — a sine whose frequency is
modulated by multiplying it into the clock. Multiplying is not integrating: change the rate by dw and
the whole argument moves by dw × time at once. `time` is the game clock, so in a world a few hours old
that is hundreds of radians, and the beat changes the rate twice a cycle, forever. The tail did not
wobble, it teleported — reported twice as the fish having convulsions when they speed up.

This file re-implements both forms off the constants in ShoalSim and measures the per-frame step of
each. It fails if the drawn phase can move more than a radian in a frame (nothing that reads as a
swimming animation ever does) and it fails if the multiplied form comes back into the renderers.
"""
import io, math, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CLIENT = os.path.join(ROOT, "common/src/main/java/com/riverfishing/client")
SIM = io.open(os.path.join(CLIENT, "ShoalSim.java"), encoding="utf-8").read()

fails = []


def die(msg):
    fails.append(msg)


def num(pattern, what):
    m = re.search(pattern, SIM)
    if not m:
        print("FAILED: cannot read %s out of ShoalSim.java" % what)
        sys.exit(1)
    return float(m.group(1))


BASE = num(r"TAIL_BASE = ([\d.]+)f", "TAIL_BASE")
SWING = num(r"TAIL_SWING = ([\d.]+)f", "TAIL_SWING")
RATE = num(r"Mth\.sin\(time \* ([\d.]+)f \+ f\.phase \* 4f\)", "the beat rate")
GATE = num(r"beat > ([\d.]+)f \? \(beat", "the beat's gate")


def kick(ticks):
    beat = math.sin(RATE * ticks)
    r = (beat - GATE) / (1.0 - GATE) if beat > GATE else 0.0
    return r * r


# ---- the two forms, over one beat, in a world that has been running a while ------------------------
FPS, SECONDS = 60.0, 8.0
step_ticks = 20.0 / FPS
for age in (0.0, 1_000.0, 100_000.0, 5_000_000.0):     # ticks: fresh, an hour, a few days, a month
    t = age
    integrated, prev_old, prev_new = 0.0, None, None
    worst_old, worst_new = 0.0, 0.0
    while t < age + SECONDS * 20.0:
        k = kick(t)
        old = t * (BASE + SWING * k)                    # what the renderers used to draw
        integrated = (integrated + (BASE + SWING * k) * step_ticks) % (2 * math.pi)
        if prev_old is not None:
            worst_old = max(worst_old, abs(old - prev_old))
            d = abs(integrated - prev_new)
            worst_new = max(worst_new, min(d, 2 * math.pi - d))   # across the wrap
        prev_old, prev_new = old, integrated
        t += step_ticks
    if worst_new > 1.0:
        die("at %d ticks the integrated sweep moves %.2f rad in a frame — that is a flick, not a tail"
            % (age, worst_new))
    if age >= 1000 and worst_old < 10.0:
        die("the old multiplied form only moved %.1f rad a frame at %d ticks; this file is measuring "
            "the wrong thing" % (worst_old, age))
    if age == 100_000.0:
        report = (worst_old, worst_new)

# ---- and the idiom is gone from every renderer -----------------------------------------------------
for name in ("ShoalRenderer.java", "FishMesh.java"):
    p = os.path.join(CLIENT, name)
    if not os.path.exists(p):
        continue
    src = io.open(p, encoding="utf-8").read()
    for m in re.finditer(r"Mth\.sin\(\s*time \* \(([^)]*)\)", src):
        die("%s multiplies the clock by a MOVING rate (%s) — that is a phase jump every time the rate "
            "changes, which is the convulsion this file exists to keep out. Integrate it in ShoalSim "
            "and read the phase here." % (name, m.group(1).strip()))
    if "f.tail" not in src and "kick" in src:
        die("%s animates off the kick without reading the integrated phase" % name)

if "f.tail = (f.tail +" not in SIM:
    die("ShoalSim no longer integrates the tail phase")

if fails:
    print("FAILED:")
    for x in fails:
        print("  " + x)
    sys.exit(1)
print("tail phase: sweep %.2f..%.2f rad/tick, integrated; at 100k ticks a frame moves %.2f rad, "
      "the old multiplied form moved %.0f" % (BASE, BASE + SWING, report[1], report[0]))
