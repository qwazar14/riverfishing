# -*- coding: utf-8 -*-
"""§breeds-with: which species will spawn with which, HOW READILY, and who clones instead of crossing.

    py -X utf8 tools/add_breeds.py                 # this tree
    py -X utf8 tools/add_breeds.py <other-root>    # …and the 1.20.1 / 26.x worktrees, which keep
                                                   # their own copy of the profiles

One table, in this file, the same shape as tools/add_provinces.py — a wrong pairing is one line to fix.

A POOL is a set of ids that will spawn with each other, and a RATE is how well the cross actually takes:
it scales the clutch, so a pair that barely works lays a handful of eggs and a pair that is really one
species lays a full one. That distinction is the whole point of the numbers below — "they can hybridise"
covers everything from a carp and a sazan, which are the same animal, to a bream and a blue bream, whose
young are a rarity that survives badly.

  1.0   the same biological species: a carp, a sazan and a koi; the two crucians
  0.8   the sturgeons — the whole family crosses, and where the chromosome counts match (beluga 120 +
        sterlet 120) the young are fully fertile. Bester has been farmed on that fact since the fifties
  0.5   deliberately bred crosses that take well: salmon x brown trout (1-3% of the juveniles in some
        European rivers are natural hybrids), char x trout — the tiger trout
  0.35  crosses that are made rather than found: zander x volga zander (the American saugeye is bred by
        the tonne; in the Volga and the Dnipro ichthyologists log single cases), roach x rudd
  0.3   nelma x whitefish — different genera, close relatives, done in hatcheries on the Ob and Yenisei
  0.2   bream x blue bream, bream x white-eye bream: possible, rare, and the young survive badly, because
        those two are specialists with a long anal fin and a mouth built for one thing

…except bream x white bream, which is one of the commonest hybrids in European water and gets 0.9.

GYNOGENESIS is the other half of the biology. A silver crucian hen does not need the male's genes at all
— his milt only starts her unfertilised egg dividing, and what hatches is a copy of HER. That is exactly
how she displaces the golden crucian wherever the two meet, and it is why she is listed here rather than
being treated as an ordinary cross.

DELIBERATELY NOT HERE, though just as real: bream x roach, roach x bleak, ide x roach, chub x roach and
the rest of the European cyprinid swarm. Wiring those up makes one clique of nearly every silver fish in
the mod, and "a pair" stops meaning anything — you would never need to catch two of the same fish again.

The fry are the MOTHER's species. A pool moves blood between ids; it never makes a third id.
"""
import io, json, os, glob, re, sys

# ---- the pools: (ids, the rate they cross at) ----------------------------------------------------
POOLS = [
    # One animal at three prices: the domestic carp, the wild sazan, the koi in its kimono. Fully
    # fertile in every direction, and the koi's colour washes out of the line within a few generations
    # back toward the wild type — which the genome does on its own, nothing here has to arrange it.
    (["carp", "wild_carp", "koi_carp"], 1.0),
    # Carassius. See GYNOGENESIS below: the silver hen clones herself off any male in this pool.
    (["crucian_carp", "golden_crucian"], 1.0),
    # Acipenseridae cross across the family and, at matching ploidy, the young breed on.
    (["beluga", "sterlet", "sturgeon"], 0.8),
    # Salmo x Salmo and Salmo x Salvelinus. Both are made on purpose; both mostly give sterile young,
    # which the mod does not model yet — see the note at the end of this file.
    (["salmon", "trout", "char"], 0.5),
    # Sander lucioperca x S. volgensis: different depths, different niches, different spawning dates.
    (["zander", "volga_zander"], 0.35),
    # Coregonus x Stenodus — intergeneric, rare, and done in hatcheries on the northern rivers.
    (["whitefish", "nelma"], 0.3),
    # Abramis and Blicca. The default is the rare end; the common one is the override below.
    (["bream", "white_bream", "blue_bream", "white_eye_bream"], 0.2),
    # Rutilus x Scardinius: found in the wild, not often, and anglers misread it more often than it happens.
    (["roach", "rudd"], 0.35),
]

# One pair out of the bream complex is not rare at all — it is everywhere.
OVERRIDES = {("bream", "white_bream"): 0.9}

# The hen whose eggs need starting and not fertilising: the fry are copies of her.
GYNOGENESIS = ["crucian_carp"]

REPO = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DIR = os.path.join(REPO, "common", "src", "main", "resources", "data", "riverfishing", "fish_profiles")

rates = {}
for pool, rate in POOLS:
    for a in pool:
        for b in pool:
            if a != b:
                rates.setdefault(a, {})[b] = OVERRIDES.get(tuple(sorted((a, b))), rate)


def put(raw, key, body):
    """Write one field after "biomes" (or "display"), textually — one line in the diff."""
    line = '  "%s": %s,\n' % (key, body)
    raw = re.sub(r'^\s*"%s":.*\n' % key, "", raw, flags=re.M)
    m = re.search(r'^(\s*)"biomes":', raw, flags=re.M) or re.search(r'^(\s*)"display":.*\n', raw, flags=re.M)
    if not m:
        return raw.replace("{\n", "{\n" + line, 1)
    at = raw.rindex("\n", 0, m.start()) + 1 if raw[m.start():].startswith(" ") else m.start()
    return raw[:at] + line.replace("  ", m.group(1), 1) + raw[at:]


written, missing = 0, []
for sp in sorted(set(list(rates) + GYNOGENESIS)):
    f = os.path.join(DIR, sp + ".json")
    if not os.path.exists(f):
        missing.append(sp)
        continue
    raw = io.open(f, encoding="utf-8").read()
    out = raw
    if sp in rates:
        body = "{" + ", ".join('"%s": %s' % (o, rates[sp][o]) for o in sorted(rates[sp])) + "}"
        out = put(out, "breeds_with", body)
    if sp in GYNOGENESIS:
        out = put(out, "gynogenesis", "true")
    if out != raw:
        io.open(f, "w", encoding="utf-8", newline="\n").write(out)
        written += 1

print("written %d profiles in %d pools" % (written, len(POOLS)))
for pool, rate in POOLS:
    over = [("%s+%s %.2f" % (a, b, r)) for (a, b), r in OVERRIDES.items() if a in pool and b in pool]
    print("  %-52s %.2f%s" % (" + ".join(pool), rate, ("   [" + ", ".join(over) + "]") if over else ""))
print("gynogenesis: " + ", ".join(GYNOGENESIS))
if missing:
    print("NO SUCH SPECIES: " + ", ".join(missing))

# NOT MODELLED YET, and worth writing down rather than forgetting: the STERILITY of a hybrid. A tiger
# trout is a dead end and a bester is not, which is a real difference and a good mechanic — but a bred
# fish never comes back as an identifiable individual. The fry go into the water as stock of the
# mother's species and are re-rolled when somebody catches one, so today there is nothing for a sterile
# flag to travel on. It needs hybrids to be identifiable first; that is the feature, and this is the note.
