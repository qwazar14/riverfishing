# -*- coding: utf-8 -*-
"""§provinces §biomes-require: the geography gate, checked against the map it draws and the fish it hides.

    py -X utf8 tools/check_provinces.py

A province gate is the harshest rule in the mod: a species that names provinces is ABSENT from every
other one, however right the water looks. Three things have to hold or that is a bug rather than a
feature — the map has to be fair, every province has to be worth living in, and the gate has to still
be in the code.

Nothing here imports Minecraft or Java: the province list, the cell size and the Voronoi are read out
of Provinces.java and re-implemented, so retuning the map in Java retunes this file with it, and the
species ranges are read out of the profiles the game actually loads.
"""
import io, json, glob, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = os.path.join(ROOT, "common/src/main/java/com/riverfishing")
PROF = os.path.join(ROOT, "common/src/main/resources/data/riverfishing/fish_profiles")
LANG = os.path.join(ROOT, "common/src/main/resources/assets/riverfishing/lang/en_us.json")

fails = []


def die(msg):
    fails.append(msg)


src = io.open(os.path.join(JAVA, "water/Provinces.java"), encoding="utf-8").read()
ALL = re.findall(r'"(\w+)"', re.search(r"String\[\] ALL = \{(.*?)\};", src, re.S).group(1))
CELL = int(re.search(r"int CELL = (\d+);", src).group(1))

# ---- the map, re-implemented from Provinces.java ---------------------------------------------------
MASK = (1 << 64) - 1


def mix(z):
    z &= MASK
    z = ((z ^ (z >> 30)) * 0xBF58476D1CE4E5B9) & MASK
    z = ((z ^ (z >> 27)) * 0x94D049BB133111EB) & MASK
    return (z ^ (z >> 31)) & MASK


def signed(v):
    return v - (1 << 64) if v >= (1 << 63) else v


def index(seed, x, z):
    cx, cz = x // CELL, z // CELL
    best, pick = None, 0
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            gx, gz = cx + dx, cz + dz
            h = mix((seed ^ (gx * 0x9E3779B97F4A7C15) ^ (gz * 0xC2B2AE3D27D4EB4F)) & MASK)
            px = gx * CELL + ((h >> 17) % CELL)
            pz = gz * CELL + ((h >> 41) % CELL)
            d = (px - x) ** 2 + (pz - z) ** 2
            if best is None or d < best:
                best, pick = d, signed(h) % len(ALL)
    return pick


# ---- the map is fair -------------------------------------------------------------------------------
SEEDS = [0, 1, 12345, 0x5DEECE66D, -8172634891273]
STEP = 384
SPAN = 24576          # 8 cells each way from the origin
for seed in SEEDS:
    seen = {}
    for x in range(-SPAN, SPAN, STEP):
        for z in range(-SPAN, SPAN, STEP):
            seen[index(seed, x, z)] = seen.get(index(seed, x, z), 0) + 1
    total = float(sum(seen.values()))
    if len(seen) < len(ALL):
        die("seed %d draws only %d of the %d provinces over %d blocks — a world can be missing a fauna"
            % (seed, len(seen), len(ALL), 2 * SPAN))
    for i, share in seen.items():
        if share / total < 0.10:
            die("seed %d gives %s only %.1f%% of the world" % (seed, ALL[i], 100 * share / total))

# determinism: the same block is the same province, always
if any(index(7, 1234, -5678) != index(7, 1234, -5678) for _ in range(3)):
    die("the map is not deterministic")
if "Random" in src or "currentTimeMillis" in src or "getGameTime" in src:
    die("Provinces.java reaches for something that is not the seed and the coordinates")

# how far a player walks to leave one: along a straight line, how often the province changes
seed = 12345
runs, last, run = [], None, 0
for x in range(0, 200000, 64):
    p = index(seed, x, 0)
    if p != last:
        if last is not None:
            runs.append(run)
        last, run = p, 0
    run += 64
mean_run = sum(runs) / float(len(runs)) if runs else 0
if mean_run < CELL * 0.6:
    die("a straight walk changes province every %d blocks; the cell is %d — the map is noise, not geography"
        % (mean_run, CELL))

# ---- every province is worth living in -------------------------------------------------------------
species, open_range = {}, []
for f in sorted(glob.glob(os.path.join(PROF, "*.json"))):
    sp = os.path.basename(f)[:-5]
    d = json.load(io.open(f, encoding="utf-8"))
    prov = d.get("provinces", [])
    for name in prov:
        if name not in ALL:
            die("%s lists the province %r, which does not exist" % (sp, name))
    if prov:
        species[sp] = prov
    else:
        open_range.append(sp)

counts = {p: 0 for p in ALL}
for sp, ps in species.items():
    for p in ps:
        counts[p] += 1
for p, n in counts.items():
    if n < 12:
        die("%s has only %d species of its own — a player who starts there has no mod to play" % (p, n))

# ---- the groups a `biomes_require` list may name ----------------------------------------------------
mgr = io.open(os.path.join(JAVA, "fishing/FishingManager.java"), encoding="utf-8").read()
GROUPS = set(re.findall(r'groups\.add\("(\w+)"\)', mgr))
GROUPS |= set(re.findall(r'\? "(\w+)" : \("(\w+)"', mgr)[0]) if re.findall(r'\? "(\w+)" : \("(\w+)"', mgr) else set()
GROUPS |= {"cold", "temperate", "warm"}
for f in sorted(glob.glob(os.path.join(PROF, "*.json"))):
    sp = os.path.basename(f)[:-5]
    d = json.load(io.open(f, encoding="utf-8"))
    req = d.get("biomes_require", [])
    for g in req:
        if g not in GROUPS:
            die("%s requires the biome group %r, which nothing in the world ever sets" % (sp, g))
    if len(req) > 3:
        die("%s requires %d groups at once — that is a fish nobody will ever meet" % (sp, len(req)))

# ---- the gate is still in the code ------------------------------------------------------------------
eng = io.open(os.path.join(JAVA, "engine/BiteEngine.java"), encoding="utf-8").read()
if "p.provinces.contains(c.province)" not in eng:
    die("BiteEngine no longer gates on the province — every fish is everywhere again")
if "c.biomeGroups.containsAll(p.biomesRequire)" not in eng:
    die("BiteEngine no longer enforces biomes_require")
if "privatePond" not in eng.split("§provinces")[1][:600]:
    die("the province gate no longer exempts a private pond — a player could not farm a foreign fish")

lang = io.open(LANG, encoding="utf-8").read()
for p in ALL:
    if '"province.riverfishing.%s"' % p not in lang:
        die("no name for the province %s in en_us.json" % p)
if '"finder.riverfishing.province"' not in lang:
    die("the sounder has no label for the province line")

# ---- §chart-far: the chart draws the same map the engine gates on --------------------------------
fin = io.open(os.path.join(JAVA, "client/FinderScreen.java"), encoding="utf-8").read()
snd = io.open(os.path.join(JAVA, "client/ClientSoundings.java"), encoding="utf-8").read()
mgr2 = io.open(os.path.join(JAVA, "fishing/FishingManager.java"), encoding="utf-8").read()

colours = re.findall(r"0x[0-9A-Fa-f]{8}", re.search(r"int\[\] PROV = \{(.*?)\};", fin, re.S).group(1))
if len(colours) != len(ALL):
    die("the chart has %d province colours for %d provinces — %s would be drawn as another"
        % (len(colours), len(ALL), ALL[min(len(colours), len(ALL) - 1)]))

# The client is handed a SCRAMBLE of the seed, never the seed. A world seed is every structure in it.
if "Provinces.mapSeed(level.getSeed())" not in mgr2:
    die("the sounding no longer sends the province map seed — the chart cannot draw the regions")
if re.search(r'put(Long|Int)\("seed",\s*level\.getSeed\(\)', mgr2):
    die("the raw world seed is being sent to the client — that is every structure and every ore vein")
if "index(mapSeed(worldSeed), x, z)" not in src:
    die("Provinces.at no longer derives the map seed, so the server gates on a different map than the "
        "chart draws")
if 'mapSeed = t.' not in snd or 'putLong("seed", mapSeed)' not in snd:
    die("the chart's seed is not kept on disk — the regions would vanish until the next sounding")

# The zoom table: strictly wider each step out, and wide enough at the end to hold a province.
steps = [(int(a), int(b)) for a, b in
         re.findall(r"\{(\d+), (\d+)\}", re.search(r"STEPS = \{(.*?)\};", fin, re.S).group(1))]
span = [420.0 * a / b for a, b in steps]        # blocks across the face, w = W - 2 * VIEW_X
if span != sorted(span, reverse=True):
    die("the zoom steps are not ordered from the widest to the closest: %s" % span)
if len(set(span)) != len(span):
    die("two zoom steps show the same width of world")
if span[0] < CELL * 2:
    die("the widest zoom shows %d blocks and a province cell is %d — a player can never see a border"
        % (span[0], CELL))
if span[-1] > 64:
    die("the closest zoom shows %d blocks; the chart has to stay usable for one swim" % span[-1])
gate = re.search(r"cols \* step >= (\d+)", fin)
if not gate:
    die("the province layer no longer has a width gate; it would wash a close chart in one flat colour")

if fails:
    print("FAILED:")
    for x in fails:
        print("  " + x)
    sys.exit(1)
print("provinces: %d over %d-block cells, %d seeds all fair (>=10%% each), a straight walk holds one for "
      "%d blocks; %s; %d species left open"
      % (len(ALL), CELL, len(SEEDS), mean_run,
         ", ".join("%s %d" % (p, counts[p]) for p in ALL), len(open_range)))
print("chart: %d zoom steps, %d blocks across at the widest (%.1f province cells), regions from %s blocks; "
      "the client gets a scrambled seed" % (len(steps), span[0], span[0] / CELL, gate.group(1)))
