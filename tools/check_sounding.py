# -*- coding: utf-8 -*-
"""§sounding: a feature you can fish must be a feature you can see.

    py tools/check_sounding.py [--selftest]

Two numbers have to agree and live in different files:

    SoundingData.SPOT_RADIUS   how far from a found hole a cast still counts as fishing it
    FishingManager MAP_REACH   how far from the spot the screen's map is sent at all

If the radius ever grows past the reach, a cast picks up the bite bonus from a feature that is not on
the map the player is looking at — the tool would be paying out for something it does not show, which
reads as random luck rather than as a swim you learned. Nothing would throw; it would just quietly
stop making sense.

The rest is the shape of a feature. A drop-off has to be a bigger step than the dip that makes a hole,
or every hole is also a ledge and the two names stop meaning different things.
"""
import io, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = "common/src/main/java/com/riverfishing/fishing/SoundingData.java"
MANAGER = "common/src/main/java/com/riverfishing/fishing/FishingManager.java"


def const(text, name):
    m = re.search(r"\b%s\s*=\s*(-?[\d.]+)" % re.escape(name), text)
    assert m, "constant %s not found" % name
    return float(m.group(1))


def faults(radius, reach, hole, ledge, bonus):
    out = []
    if radius > reach:
        out.append("SPOT_RADIUS %g reaches past the map's %g — a bonus off a spot nobody is shown"
                   % (radius, reach))
    if ledge <= hole:
        out.append("LEDGE_STEP %g is not bigger than HOLE_DROP %g — every hole is also a ledge"
                   % (ledge, hole))
    if hole < 1:
        out.append("HOLE_DROP %g calls flat bed a hole" % hole)
    if bonus <= 1.0:
        out.append("SPOT_BONUS %g is not a bonus" % bonus)
    return out


def selftest():
    assert not faults(4, 24, 2, 3, 1.35), faults(4, 24, 2, 3, 1.35)
    assert faults(30, 24, 2, 3, 1.35), "a radius past the map's reach must be caught"
    assert faults(4, 24, 3, 3, 1.35), "a ledge no bigger than a hole must be caught"
    assert faults(4, 24, 0, 3, 1.35), "a zero-deep hole must be caught"
    assert faults(4, 24, 2, 3, 1.0), "a bonus of one must be caught"
    print("self-test ok: catches a radius past the map, a ledge that is a hole, and a bonus of nothing")
    return 0


def main():
    d = io.open(os.path.join(ROOT, DATA), encoding="utf-8").read()
    m = io.open(os.path.join(ROOT, MANAGER), encoding="utf-8").read()
    radius, hole = const(d, "SPOT_RADIUS"), const(d, "HOLE_DROP")
    ledge, bonus = const(d, "LEDGE_STEP"), const(d, "SPOT_BONUS")
    # MAP_REACH is a class constant now: the map window and the water mask both read it, and a
    # number two methods share is a number that lives on the class.
    reach = const(m, "MAP_REACH")

    print("spot radius %g, map reach %g, hole %g, ledge %g, bonus x%g"
          % (radius, reach, hole, ledge, bonus))
    bad = faults(radius, reach, hole, ledge, bonus)
    if bad:
        print()
        for b in bad:
            print("  %s" % b)
        return 1
    print("what the finder pays for is what the finder shows")
    return 0


if __name__ == "__main__":
    sys.exit(selftest() if "--selftest" in sys.argv else main())
