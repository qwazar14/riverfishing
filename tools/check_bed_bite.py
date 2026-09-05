# -*- coding: utf-8 -*-
"""§bed-bite: the bottom is a nudge, never a gate.

    py tools/check_bed_bite.py [--selftest]

FishProfile.bedFactor() multiplies the environment score by how much a species likes the bed it is
over. The whole design rests on that number staying inside 0.85..1.2: depth and water type already
decide whether a fish EXISTS here, and a bed factor that could reach zero would be a second hard
gate stacked on top of them — half the swims in the game emptied by a bottom the player cannot see
without the finder and cannot change at all.

Two places can break it, and both are checked:

    the family table in FishProfile.bedFactor()   every literal in the switch stays in range
    a profile's own "bed" map                      a hand-written override stays in range too

Neither would throw. A 0.0 in either place simply makes a species vanish from every swim with that
bottom, and the bug report would say "the tench are gone" and nothing else.
"""
import glob, io, json, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PROFILE_JAVA = "common/src/main/java/com/riverfishing/fish/FishProfile.java"
PROFILES = "common/src/main/resources/data/riverfishing/fish_profiles"

LO, HI = 0.85, 1.2


def table_literals(java):
    """Every factor the family table hands out."""
    i = java.index("public double bedFactor(")
    body = java[i:java.index("\n    }\n", i)]
    # 1.15, 0.85, 1.0 ... but not the "1.0" of an unrelated line: only the ones after '->'
    return [float(x) for x in re.findall(r"->\s*([0-9]+\.[0-9]+)", body)]


def faults_table(vals):
    return ["family table hands out %g, outside %g..%g" % (v, LO, HI) for v in vals if v < LO or v > HI]


def faults_profile(name, prof):
    out = []
    for key, v in (prof.get("bed") or {}).items():
        try:
            v = float(v)
        except (TypeError, ValueError):
            out.append("%s: bed.%s is not a number" % (name, key))
            continue
        if v < LO or v > HI:
            out.append("%s: bed.%s = %g, outside %g..%g" % (name, key, v, LO, HI))
    return out


def selftest():
    good = 'public double bedFactor(int c) {\n switch (g) { case "x": return switch (k) { case "mud" -> 1.15; default -> 1.0; }; }\n    }\n'
    bad = good.replace("1.15", "0.0")
    assert not faults_table(table_literals(good)), faults_table(table_literals(good))
    assert faults_table(table_literals(bad)), "a zero in the family table must be caught"
    assert not faults_profile("ok", {"bed": {"sand": 1.1}})
    assert faults_profile("gate", {"bed": {"sand": 0.0}}), "a zero override must be caught"
    assert faults_profile("wild", {"bed": {"sand": 3.0}}), "a x3 override must be caught"
    assert not faults_profile("silent", {}), "no map is no fault"
    print("self-test ok: a zero in the table or in a profile reads as a gate, and a nudge passes")
    return 0


def main():
    java = io.open(os.path.join(ROOT, PROFILE_JAVA), encoding="utf-8").read()
    vals = table_literals(java)
    bad = faults_table(vals)
    n = 0
    for path in sorted(glob.glob(os.path.join(ROOT, PROFILES, "*.json"))):
        n += 1
        bad += faults_profile(os.path.basename(path)[:-5], json.load(io.open(path, encoding="utf-8")))
    print("%d factors in the family table, %d profiles read" % (len(vals), n))
    if bad:
        print("\n%d bed factor(s) that would act as a gate:" % len(bad))
        for b in bad:
            print("  %s" % b)
        return 1
    print("every bed factor is a nudge, %g..%g" % (LO, HI))
    return 0


if __name__ == "__main__":
    sys.exit(selftest() if "--selftest" in sys.argv else main())
