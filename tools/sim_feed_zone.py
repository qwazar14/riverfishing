# -*- coding: utf-8 -*-
"""§groundbait-one-jar: re-derive the fed-spot balance after changing any of its constants.

The numbers that decide whether groundbait has a decision in it at all — the ceiling in FeedZoneData and
the four terms in BiteEngine.groundbaitScore — cannot be checked by an assert, because the answer is not
a value but a SHAPE: which mix wins, for which fish. This is that check. Run it after touching any of
them and read the tables.

What they must show, or the feature is not a feature:

    a plain jar          ->  clearly better than nothing, clearly worse than a blend
    a blend built for X  ->  the best row for X, and only for X
    a blend built for Y  ->  no better than an unfed swim when fished for X
    coarse               ->  wins for the heavy fish, loses for the small ones
    more components      ->  a higher ceiling than fewer, at the same richness

The failure this was written to catch: with fullness deleted (§no-overfeeding), nutrition stops being a
cost and quietly becomes strictly dominant — a rich mix would beat a lean one for every fish in the game
and the whole pantry would collapse to "use boilies". It does not, because nutrition is MATCHED against
the species now rather than merely spent, and the second table is what proves it.

    python tools/sim_feed_zone.py
    python tools/sim_feed_zone.py --ceiling-variety 0.4
"""
import argparse
import json
import math
import os

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PROF = os.path.join(REPO, "common/src/main/resources/data/riverfishing/fish_profiles")

# ---- the pantry, transcribed from GroundbaitMix ----
# id -> (nutrition, fraction, diet or None)
PANTRY = {
    "groundbait_powder": (0.50, 0.50, None),
    "groundbait_soil": (0.00, 0.35, None),
    "minecraft:clay_ball": (0.00, 0.55, None),
    "bread": (0.25, 0.10, "bread"),
    "dough": (0.60, 0.30, "dough"),
    "corn": (0.85, 0.90, "corn"),
    "pea": (0.80, 0.75, "pea"),
    "pearl_barley": (0.70, 0.70, "pearl_barley"),
    "boilie": (0.95, 1.00, "boilie"),
    "maggot": (0.65, 0.55, "maggot"),
    "worm": (0.70, 0.65, "worm"),
    "bloodworm": (0.30, 0.20, "bloodworm"),
    "chicken_liver": (0.80, 0.65, "chicken_liver"),
    "fish_strip": (0.75, 0.80, "fish_strip"),
    # The vanilla half of the pantry. It is here because a player's FIRST mix is made entirely of it —
    # leaving it out meant the one recipe most people will ever build could not be checked.
    "minecraft:wheat": (0.55, 0.45, "pearl_barley"),
    "minecraft:wheat_seeds": (0.30, 0.15, None),
    "minecraft:bread": (0.45, 0.20, "bread"),
    "minecraft:potato": (0.60, 0.50, "dough"),
    "minecraft:carrot": (0.45, 0.55, "corn"),
    "minecraft:beetroot": (0.40, 0.45, "corn"),
    "minecraft:sweet_berries": (0.35, 0.40, "boilie"),
    "minecraft:sugar": (0.20, 0.05, None),
    "minecraft:cocoa_beans": (0.35, 0.25, "boilie"),
    "minecraft:sunflower": (0.60, 0.25, "corn"),
    "minecraft:pumpkin_seeds": (0.55, 0.35, "pea"),
    "minecraft:melon_seeds": (0.50, 0.30, "pea"),
    "minecraft:dried_kelp": (0.40, 0.30, "fish_strip"),
}


def stir(parts):
    """GroundbaitMix.of, for the subset of the pantry this simulation uses."""
    total = sum(n for _, n in parts)
    nutrition = sum(PANTRY[i][0] * n for i, n in parts) / total
    fraction = sum(PANTRY[i][1] * n for i, n in parts) / total
    diets = {}
    for i, n in parts:
        d = PANTRY[i][2]
        if d:
            diets[d] = diets.get(d, 0) + n
    return dict(nutrition=nutrition, fraction=fraction, variety=len(parts), diets=diets)


def ceiling(mix, variety_weight):
    """FeedZoneData.ceiling — how strong a spot this mix can ever build."""
    var = min(1.0, max(0.0, (mix["variety"] - 1) / 4.0))
    return min(1.0, 0.25 + (0.75 - variety_weight) * mix["nutrition"] + variety_weight * var)


def gb_score(mix, fish):
    """BiteEngine.groundbaitScore."""
    spoons = sum(mix["diets"].values())
    if spoons == 0:
        menu = 0.75
    else:
        menu = 0.45 + 0.75 * sum(min(1.0, fish["bait"].get(d, 0.0)) * n
                                 for d, n in mix["diets"].items()) / spoons
    frac = 1.0 - min(1.0, abs(mix["fraction"] - fish["fraction"]))
    nutr = 1.0 - min(1.0, abs(mix["nutrition"] - fish["nutrition"]))
    var = 0.90 + 0.10 * min(1.0, (mix["variety"] - 1) / 4.0)
    return min(1.0, menu * (0.45 + 0.55 * frac) * (0.60 + 0.40 * nutr) * var)


def load(name):
    data = json.loads(open(os.path.join(PROF, name + ".json"), encoding="utf-8").read())
    gb = data["ideal"]["groundbait"]
    return dict(name=name, bait=data["ideal"].get("bait", {}),
                fraction=gb["fraction"], nutrition=gb["nutrition"],
                kg=data["weight_g"].get("mean", 0) / 1000.0)


BASE = "groundbait_powder"

# Every one of these must be CRAFTABLE, which since §base-groundbait means every one of them starts with
# the base. A reference list you could not actually make would be a slow way to mislead yourself, so the
# assertion below checks it rather than trusting the table.
MIXES = [
    ("plain base", [(BASE, 1)]),
    ("+ bloodworm, half soil", [(BASE, 3), ("bloodworm", 1), ("groundbait_soil", 4)]),
    ("silver-fish blend", [(BASE, 2), ("bread", 2), ("bloodworm", 2), ("maggot", 1)]),
    ("bream blend", [(BASE, 3), ("pearl_barley", 2), ("worm", 2), ("maggot", 1)]),
    ("carp blend", [(BASE, 1), ("boilie", 3), ("corn", 3), ("pea", 2)]),
    ("base + boilie only", [(BASE, 1), ("boilie", 4)]),
    ("meat, for a predator", [(BASE, 1), ("chicken_liver", 3), ("fish_strip", 3), ("worm", 2)]),
]
FISH = ["bleak", "roach", "bream", "tench", "carp", "catfish", "burbot"]

UNFED = 0.4


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ceiling-variety", type=float, default=0.30,
                    help="how much of the ceiling variety buys (rest goes to nutrition)")
    args = ap.parse_args()

    for label, parts in MIXES:
        ids = [i for i, _ in parts]
        assert BASE in ids, "%s has no base — it could not be crafted" % label
        adds = sum(n for i, n in parts if i != BASE)
        assert adds <= 8, "%s has %d additives; the grid leaves room for 8" % (label, adds)
        assert sum(n for _, n in parts) <= 9, "%s does not fit a 3x3 grid" % label

    mixes = [(label, stir(parts)) for label, parts in MIXES]
    fish = [load(f) for f in FISH]

    print("\nHOW STRONG A SPOT EACH MIX CAN BUILD  (FeedZoneData.ceiling)")
    print("  %-24s %9s %9s %4s %9s" % ("mix", "nutrition", "fraction", "var", "ceiling"))
    print("  " + "-" * 60)
    for label, m in mixes:
        print("  %-24s %9.2f %9.2f %4d %9.2f"
              % (label, m["nutrition"], m["fraction"], m["variety"],
                 ceiling(m, args.ceiling_variety)))

    print("\nGROUNDBAIT SCORE  (unfed swim = %.2f)" % UNFED)
    print("  %-24s %s" % ("mix", "".join("%9s" % f["name"][:8] for f in fish)))
    print("  " + "-" * (24 + 9 * len(fish)))
    for label, m in mixes:
        print("  %-24s %s" % (label, "".join("%9.2f" % gb_score(m, f) for f in fish)))
    print("  %-24s %s" % ("(species wants fraction)", "".join("%9.2f" % f["fraction"] for f in fish)))
    print("  %-24s %s" % ("(species wants nutrition)", "".join("%9.2f" % f["nutrition"] for f in fish)))

    # ---- the three shapes the feature depends on ----
    print()
    ok = True
    plain = dict(mixes)["plain base"]
    for f in fish:
        best = max(mixes, key=lambda lm: gb_score(lm[1], f))
        if gb_score(plain, f) >= gb_score(best[1], f) - 1e-9:
            print("  BROKEN: nothing beats a plain jar for %s" % f["name"]); ok = False
    # A blend built for one end of the water must not be the answer at the other end.
    small, big = load("bleak"), load("carp")
    if gb_score(dict(mixes)["carp blend"], small) >= gb_score(dict(mixes)["silver-fish blend"], small):
        print("  BROKEN: the carp blend is not punished on bleak"); ok = False
    if gb_score(dict(mixes)["silver-fish blend"], big) >= gb_score(dict(mixes)["carp blend"], big):
        print("  BROKEN: the fine blend is not punished on carp"); ok = False
    # Richness must not be free: the richest mix in the list must lose somewhere.
    richest = max(mixes, key=lambda lm: lm[1]["nutrition"])
    if all(gb_score(richest[1], f) >= max(gb_score(m, f) for _, m in mixes) - 1e-9 for f in fish):
        print("  BROKEN: '%s' wins for every fish — nutrition is dominant again" % richest[0]); ok = False
    print("  balance holds" if ok else "  FIX THE NUMBERS ABOVE")


main()
