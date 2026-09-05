# -*- coding: utf-8 -*-
"""§n §breeding: the pond-centred weight roll and the fry trap's water gate, run without a JVM.

    py -X utf8 tools/check_pond_weight.py

Mirrors the two rules stream N puts in (tools/patches/p_n.py, BaitTrapBlockEntity): a settled water with a
known average specimen re-centres the weight roll on ITS average, keeping the profile's spread and range;
and the fry trap lifts a brood only from its own water. Exit 1 on the first rule that breaks.
"""
import sys


def clamp(x, lo, hi):
    return lo if x < lo else hi if x > hi else x


def roll(biased, wmin, wmax, avg):
    """FishingManager.rollFish, from the final `biased` (livebait/lure floors already in it)."""
    if avg > 0:                                   # settled here and the ledger measured a specimen
        return clamp(avg * (0.6 + 0.8 * biased), wmin, wmax)
    return wmin + (wmax - wmin) * biased          # the profile's own roll, as before


def same_water(trap_claim, ledger_claim, ledger_known):
    """BaitTrapBlockEntity.sameWater: claims compared by identity, None = wild water."""
    if not ledger_known:                          # no Pos on the ledger: it belongs to open water
        return trap_claim is None
    return trap_claim == ledger_claim


fail = []


def ck(name, cond):
    if not cond:
        fail.append(name)


# ---- the weight roll -------------------------------------------------------------------------
WMIN, WMAX = 200.0, 12000.0

# 1. a pond of small fish gives small fish; the profile's mean stops deciding
small = [roll(b / 100.0, WMIN, WMAX, 800.0) for b in range(101)]
ck("small pond stays small", max(small) <= 800.0 * 1.4 + 1e-9)
ck("small pond median is its average", abs(roll(0.5, WMIN, WMAX, 800.0) - 800.0) < 1e-9)

# 2. never outside the species' range, whatever the pond grew to
ck("clamped high", roll(1.0, WMIN, WMAX, 20000.0) == WMAX)
ck("clamped low", roll(0.0, WMIN, WMAX, 100.0) == WMIN)
for avg in (250.0, 800.0, 5000.0, 11000.0):
    for b in range(0, 101):
        w = roll(b / 100.0, WMIN, WMAX, avg)
        ck("in range avg=%s" % avg, WMIN - 1e-9 <= w <= WMAX + 1e-9)

# 3. the livebait / lure floors still work: they raise `biased`, and weight rises with it
ck("floors still lift the fish",
   roll(0.6, WMIN, WMAX, 3000.0) > roll(0.1, WMIN, WMAX, 3000.0))
ck("monotone in the roll",
   all(roll(b / 100.0, WMIN, WMAX, 3000.0) <= roll((b + 1) / 100.0, WMIN, WMAX, 3000.0) for b in range(100)))

# 4. an unsettled water (or a world from before the head count) rolls exactly as it always did
ck("no ledger = old behaviour", roll(0.25, WMIN, WMAX, 0.0) == WMIN + (WMAX - WMIN) * 0.25)

# 5. the spread is the profile's shape, not a fixed band: 0.6..1.4 of the pond's average
ck("spread low", abs(roll(0.0, WMIN, WMAX, 1000.0) - 600.0) < 1e-9)
ck("spread high", abs(roll(1.0, WMIN, WMAX, 1000.0) - 1400.0) < 1e-9)

# ---- the fry trap's water --------------------------------------------------------------------
MINE, NEXT_DOOR = "claim#1", "claim#2"

ck("my pond's fry", same_water(MINE, MINE, True))
ck("not the neighbour's fry", not same_water(MINE, NEXT_DOOR, True))       # the bug this fixes
ck("a pond takes no wild fry", not same_water(MINE, None, True))
ck("wild trap takes no pond fry", not same_water(None, MINE, True))
ck("wild takes wild", same_water(None, None, True))
ck("unrecorded brood is wild", same_water(None, None, False))
ck("a pond does not inherit an unrecorded brood", not same_water(MINE, None, False))

if fail:
    sys.exit("check_pond_weight: FAILED\n  " + "\n  ".join(fail))
print("check_pond_weight: ok (weight roll + trap water gate)")
