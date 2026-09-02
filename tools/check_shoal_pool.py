# -*- coding: utf-8 -*-
"""§shoal-honest: the water still shows fish.

    py tools/check_shoal_pool.py [--selftest]

Two knobs in ShoalTracker decide what the water is allowed to show, and both fail SILENTLY:

    RARE_BASE   a species whose bite rate is under this is shown in passes, one hour in RARE_ONE_IN.
                Raise it past most of the species list and the water is empty three hours in four —
                no error, just a lake that looks dead to a player and fine to a test.

    JUMPERS     a list of species ids that leap. A name that is not a species is a jumper nobody sees;
                the code logs it at DEBUG, which is the same as not logging it.

So: the rare set has to be a MINORITY of the species list, read off the profiles' own base rates, and
every jumper has to be a real species.
"""
import glob, io, json, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TRACKER = "common/src/main/java/com/riverfishing/fishing/ShoalTracker.java"
PROFILES = "common/src/main/resources/data/riverfishing/fish_profiles"
ITEMS = "common/src/main/java/com/riverfishing/registry/ModItems.java"

# Past this share of the list, "rare" stops meaning rare.
RARE_SHARE_MAX = 0.4


def rare_base(java):
    m = re.search(r"RARE_BASE\s*=\s*([0-9.]+)", java)
    assert m, "RARE_BASE not found"
    return float(m.group(1))


def jumpers(java):
    i = java.index("String[] want = {")
    return re.findall(r'"([a-z_]+)"', java[i:java.index("}", i)])


def species(items):
    m = re.search(r"FISH_SPECIES\s*=\s*\{(.*?)\}", items, re.S)
    return set(re.findall(r'"([a-z0-9_]+)"', m.group(1)))


def bases():
    out = {}
    for p in glob.glob(os.path.join(ROOT, PROFILES, "*.json")):
        d = json.load(io.open(p, encoding="utf-8"))
        out[os.path.basename(p)[:-5]] = float(d.get("base", 1.0))
    return out


def faults(rare, base_by_species, jump, all_species):
    out = []
    n = len(base_by_species)
    rare_n = sum(1 for b in base_by_species.values() if b < rare)
    if n and rare_n / n > RARE_SHARE_MAX:
        out.append("RARE_BASE %g makes %d of %d species rare — the water would be empty most of the time"
                   % (rare, rare_n, n))
    for j in jump:
        if j not in all_species:
            out.append("jumper '%s' is not a species" % j)
    return out


def selftest():
    b = {"roach": 1.0, "bream": 0.9, "pike": 0.9, "beluga": 0.1, "marlin": 0.25}
    sp = set(b)
    assert not faults(0.3, b, ["pike"], sp), faults(0.3, b, ["pike"], sp)
    assert faults(0.95, b, ["pike"], sp), "a RARE_BASE that makes most fish rare must be caught"
    assert faults(0.3, b, ["pikee"], sp), "a jumper that is not a species must be caught"
    print("self-test ok: an emptying rare bar and a phantom jumper both read as faults")
    return 0


def main():
    java = io.open(os.path.join(ROOT, TRACKER), encoding="utf-8").read()
    items = io.open(os.path.join(ROOT, ITEMS), encoding="utf-8").read()
    rare, jump, sp, b = rare_base(java), jumpers(java), species(items), bases()
    rare_n = sum(1 for v in b.values() if v < rare)
    print("%d species, %d rare under base %g, %d jumpers" % (len(b), rare_n, rare, len(jump)))
    bad = faults(rare, b, jump, sp)
    if bad:
        print()
        for x in bad:
            print("  %s" % x)
        return 1
    print("the water keeps its fish, and every jumper is a fish")
    return 0


if __name__ == "__main__":
    sys.exit(selftest() if "--selftest" in sys.argv else main())
