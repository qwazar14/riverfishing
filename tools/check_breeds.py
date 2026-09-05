# -*- coding: utf-8 -*-
"""§breeds-with: the breeding table is symmetric, closed, and cannot make a fish nobody wanted.

    py -X utf8 tools/check_breeds.py

Four things have to hold or a pool is a bug rather than a feature.

1. Every id named exists. A typo would simply never pair, silently, forever.
2. It is SYMMETRIC. The Java accepts a pair when EITHER side names the other — refusing would hide the
   mistake instead of reporting it — so this is where a one-sided table gets reported.
3. It is TRANSITIVE, i.e. a pool is a closed clique. "A spawns with B, B spawns with C, A will not
   spawn with C" is not a thing that happens in water, and a player would read it as a bug.
4. No pool crosses a weight so far apart that the fry would be absurd — a fish and something forty
   times its weight are not going to manage it.
5. §breed-rate: every rate is in (0, 1], and the two directions of a pair agree. A rate is a property
   of the PAIR, so a table that says 0.9 one way and 0.2 the other is two claims about one fact.
6. §gynogenesis: the hen who clones is in a pool at all — cloning off nobody is just parthenogenesis,
   which is a different fish and not this one.
"""
import io, glob, json, os, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PROF = os.path.join(ROOT, "common/src/main/resources/data/riverfishing/fish_profiles")
JAVA = os.path.join(ROOT, "common/src/main/java/com/riverfishing")

fails = []


def die(msg):
    fails.append(msg)


table, weight, rates, clones = {}, {}, {}, []
for f in sorted(glob.glob(os.path.join(PROF, "*.json"))):
    sp = os.path.basename(f)[:-5]
    d = json.load(io.open(f, encoding="utf-8"))
    w = d.get("weight_g", {})
    weight[sp] = w.get("mean", w.get("max", 1000))
    bw = d.get("breeds_with")
    if isinstance(bw, list):          # the first cut of the field; the Java still reads it as 1.0 each
        bw = {o: 1.0 for o in bw}
    if bw:
        table[sp] = set(bw)
        rates[sp] = bw
    if d.get("gynogenesis"):
        clones.append(sp)

# ---- 1. the ids exist ------------------------------------------------------------------------------
for sp, others in table.items():
    for o in sorted(others):
        if o not in weight:
            die("%s breeds with %r, which is not a species" % (sp, o))
        if o == sp:
            die("%s lists itself; the same id always pairs and the field is for the others" % sp)

# ---- 2. symmetric ----------------------------------------------------------------------------------
for sp, others in table.items():
    for o in sorted(others):
        if o in weight and sp not in table.get(o, set()):
            die("%s names %s but %s does not name %s — the pair works, and the table is a lie"
                % (sp, o, o, sp))

# ---- 3. transitive: a pool is a closed clique -------------------------------------------------------
seen, pools = set(), []
for sp in sorted(table):
    if sp in seen:
        continue
    pool, edge = {sp}, [sp]
    while edge:
        cur = edge.pop()
        for o in table.get(cur, ()):  # walk the whole component, however it is wired
            if o not in pool and o in weight:
                pool.add(o)
                edge.append(o)
    seen |= pool
    pools.append(sorted(pool))
    for a in pool:
        for b in pool:
            if a != b and b not in table.get(a, set()):
                die("%s and %s are in one pool (through the others) but do not name each other — a pool "
                    "has to be a closed clique or the same three fish pair differently by which two you "
                    "put in the tank" % (a, b))

# ---- 4. sane pools ---------------------------------------------------------------------------------
for pool in pools:
    lo = min(weight[s] for s in pool)
    hi = max(weight[s] for s in pool)
    if hi > lo * 40:
        die("the pool %s spans %.1f kg to %.1f kg — twenty times the weight is not a pair, it is lunch"
            % (" + ".join(pool), lo / 1000.0, hi / 1000.0))

# ---- 5. the rates ----------------------------------------------------------------------------------
for sp, row in rates.items():
    for o, r in sorted(row.items()):
        if not isinstance(r, (int, float)) or not (0.0 < r <= 1.0):
            die("%s x %s crosses at %r; a rate is how well it takes, in (0, 1]" % (sp, o, r))
        back = rates.get(o, {}).get(sp)
        if back is not None and abs(back - r) > 1e-9:
            die("%s says %s x %s takes at %.2f and %s says %.2f — a rate belongs to the pair, not to "
                "one side of it" % (sp, sp, o, r, o, back))

# ---- 6. the cloner is in a pool ----------------------------------------------------------------------
for sp in clones:
    if sp not in table:
        die("%s clones by gynogenesis but breeds with nobody — she needs a male in the pool to start "
            "the egg dividing, even though none of his genes get in" % sp)

# ---- and the code still reads it --------------------------------------------------------------------
prof = io.open(os.path.join(JAVA, "fish/FishProfile.java"), encoding="utf-8").read()
tank = io.open(os.path.join(JAVA, "block/AquariumBreeding.java"), encoding="utf-8").read()
if '"breeds_with"' not in prof:
    die("FishProfile no longer reads breeds_with; every pool in the data is dead text")
if "gynogenesis" not in prof or "gynogenesis" not in tank:
    die("§gynogenesis is gone: the silver crucian would cross like anything else")
if "cross * (" not in tank:
    die("the clutch is no longer scaled by the cross rate — every hybrid lays a full clutch again")
if "mates(sp, FishItem.getSpecies(m))" not in tank:
    die("the tank pairs on the id again — §breeds-with is gone")
if "pair[0]" not in tank or "RoeItem.of(FishItem.getSpecies(mother)" not in tank:
    die("the roe is no longer built from the mother, so a cross no longer has a definite species")

if fails:
    print("FAILED:")
    for x in fails:
        print("  " + x)
    sys.exit(1)
print("breeds_with: %d pools, %d species, symmetric and closed; %d clone by gynogenesis"
      % (len(pools), len(seen), len(clones)))
for pool in pools:
    rs = sorted({rates[a][b] for a in pool for b in pool if a != b})
    print("  %-52s %s" % (" + ".join(pool), ", ".join("%.2f" % r for r in rs)))
