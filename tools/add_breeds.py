# -*- coding: utf-8 -*-
"""§breeds-with: which species will spawn with which, written into the profiles.

    py -X utf8 tools/add_breeds.py                 # this tree
    py -X utf8 tools/add_breeds.py <other-root>   # …and the 1.20.1 / 26.x worktrees, which keep their
                                                  # own copy of the profiles

One table, in this file, the same shape as tools/add_provinces.py — a wrong pairing is one line to fix.

A POOL is a set of ids that will spawn with each other. Two rules kept it short.

1. Two ids that are ONE ANIMAL. The carp, the sazan and the koi are all Cyprinus carpio — a domestic
   form, a wild form and a colour form — and the mod only separates them because they are worth
   different money and fight differently. Same for the two crucians.
2. Crosses that really happen in water these fish share AND leave fertile young. The sturgeons are the
   famous case: bester is beluga x sterlet, it is farmed by the tonne, and it breeds. Salmon x brown
   trout, char x trout, zander x volga zander, whitefish x nelma are all documented naturals.

Deliberately NOT here, though they are just as real: bream x roach, roach x bleak, ide x roach, chub x
roach, and the rest of the European cyprinid hybrid swarm. Those are genuine and common in the wild,
but wiring them up would make one clique of nearly every silver fish in the mod, and "a pair" would
stop meaning anything — you would never need to catch two of the same fish again. The bream complex and
roach x rudd are in because they are small closed cliques; the swarm is out because it is not.

The fry are the MOTHER's species. A pool moves blood between ids; it never makes a third id.
"""
import io, json, os, glob, sys

# ---- the pools -----------------------------------------------------------------------------------
POOLS = [
    # One animal, three prices: the domestic carp, the wild sazan, and the koi in its kimono.
    ["carp", "wild_carp", "koi_carp"],
    # Carassius: the golden crucian and the silver one interbreed wherever they meet, constantly.
    ["crucian_carp", "golden_crucian"],
    # Acipenseridae hybridise across the whole family and the young are fertile. Bester — beluga x
    # sterlet — has been farmed since the fifties precisely because of it.
    ["beluga", "sterlet", "sturgeon"],
    # Salmo salar x Salmo trutta, found in rivers that hold both; and char x trout, likewise.
    ["salmon", "trout", "char"],
    # Sander lucioperca x S. volgensis — the natural hybrid of the lower Volga and the Dnipro.
    ["zander", "volga_zander"],
    # Coregonus x Stenodus: whitefish and nelma spawn on the same northern gravel.
    ["whitefish", "nelma"],
    # The bream complex — Abramis and Blicca — hybridise so freely that anglers name the crosses.
    ["bream", "white_bream", "blue_bream", "white_eye_bream"],
    # And the other classic pair of the same water.
    ["roach", "rudd"],
]

REPO = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DIR = os.path.join(REPO, "common", "src", "main", "resources", "data", "riverfishing", "fish_profiles")

mates = {}
for pool in POOLS:
    for sp in pool:
        mates.setdefault(sp, set()).update(x for x in pool if x != sp)


def put(raw, key, values):
    """Write one array field after "biomes" (or after "display"), textually — one line in the diff."""
    import re
    line = '  "%s": [%s],\n' % (key, ", ".join('"%s"' % v for v in sorted(values)))
    raw = re.sub(r'^\s*"%s":.*\n' % key, "", raw, flags=re.M)
    m = re.search(r'^(\s*)"biomes":', raw, flags=re.M) or re.search(r'^(\s*)"display":.*\n', raw, flags=re.M)
    if not m:
        return raw.replace("{\n", "{\n" + line, 1)
    at = raw.rindex("\n", 0, m.start()) + 1 if raw[m.start():].startswith(" ") else m.start()
    return raw[:at] + line.replace("  ", m.group(1), 1) + raw[at:]


written, missing = 0, []
for sp, others in sorted(mates.items()):
    f = os.path.join(DIR, sp + ".json")
    if not os.path.exists(f):
        missing.append(sp)
        continue
    raw = io.open(f, encoding="utf-8").read()
    out = put(raw, "breeds_with", others)
    if out != raw:
        io.open(f, "w", encoding="utf-8", newline="\n").write(out)
        written += 1

print("written %d profiles in %d pools" % (written, len(POOLS)))
for pool in POOLS:
    print("  " + " + ".join(pool))
if missing:
    print("NO SUCH SPECIES: " + ", ".join(missing))
