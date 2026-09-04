# -*- coding: utf-8 -*-
"""§pattern: the pattern index, checked against the promises it makes.

    py -X utf8 tools/check_pattern.py

Every landed fish carries an int 0..999 that decides what family it belongs to, how its colours are
turned, and — twelve times in a thousand — that it is a gem. Four things have to hold or the feature is
a lie:

  * the twelve bands cover 0..999 with no gap and no overlap, so every index names exactly one family;
  * the twelve gem indices are unique, and no band holds two of them;
  * inheritance stays inside 0..999 whatever the parents are, and drifts rather than jumps;
  * THE TINT IS STABLE. A pattern whose colour changes between two loads is the one bug this feature
    cannot have, so the paint is re-implemented here from the tables and checked for determinism, and
    the Java is read for anything that could make it wander (a clock, a Random, an object hash).

Like tools/check_koi_genetics.py, nothing here imports Minecraft or Java: the band table, the hue
turns, the gems and the koi's four paint layers are READ OUT of the source, so renaming a family or
moving a band fails this file instead of quietly making it a liar.
"""
import io, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PATTERN = os.path.join(ROOT, "common/src/main/java/com/riverfishing/fish/Pattern.java")
MORPH = os.path.join(ROOT, "common/src/main/java/com/riverfishing/fish/FishMorph.java")
LANG = os.path.join(ROOT, "common/src/main/resources/assets/riverfishing/lang/en_us.json")

fails = []


def die(msg):
    fails.append(msg)


src = io.open(PATTERN, encoding="utf-8").read()

# ---- what Java says -------------------------------------------------------------------------------


def ints(name):
    m = re.search(r"int\[\] %s = \{(.*?)\};" % name, src, re.S)
    if not m:
        sys.exit("no %s table in Pattern.java" % name)
    return [int(x, 0) for x in re.findall(r"-?0x[0-9A-Fa-f]+|-?\d+", m.group(1))]


def words(name):
    m = re.search(r"String\[\] %s = \{(.*?)\};" % name, src, re.S)
    if not m:
        sys.exit("no %s table in Pattern.java" % name)
    return re.findall(r'"([^"]+)"', m.group(1))


m = re.search(r"int MAX = (\d+);", src)
MAX = int(m.group(1)) if m else 999
BAND, FAMILY, HUE = ints("BAND"), words("FAMILY"), ints("HUE")
GEM_AT, GEM, GEM_RGB = ints("GEM_AT"), words("GEM"), ints("GEM_RGB")

if not (len(BAND) == len(FAMILY) == len(HUE) == 12):
    die("the band table is %d starts / %d names / %d hue turns — the design asks for twelve of each"
        % (len(BAND), len(FAMILY), len(HUE)))
if not (len(GEM_AT) == len(GEM) == len(GEM_RGB)):
    die("the gems are %d indices / %d names / %d colours" % (len(GEM_AT), len(GEM), len(GEM_RGB)))


# ---- the bands cover 0..999 exactly once ----------------------------------------------------------

def family_index(p):
    """familyIndex, as Pattern.java walks it: the last band that starts at or below the index."""
    i = len(BAND) - 1
    while i > 0 and p < BAND[i]:
        i -= 1
    return i


if BAND[0] != 0:
    die("the first band starts at %d, not 0 — nothing names index 0" % BAND[0])
if BAND != sorted(BAND) or len(set(BAND)) != len(BAND):
    die("the band starts are not strictly increasing: %s" % BAND)

covered = {}
for p in range(0, MAX + 1):
    covered[p] = family_index(p)
if len(set(covered.values())) != len(FAMILY):
    missing = [FAMILY[i] for i in range(len(FAMILY)) if i not in set(covered.values())]
    die("these families own no index at all: %s" % missing)
widths = [0] * len(FAMILY)
for p, i in covered.items():
    widths[i] += 1
if sum(widths) != MAX + 1:
    die("the bands cover %d indices, not %d" % (sum(widths), MAX + 1))
if min(widths) < 20:
    die("the narrowest band is %d wide — a family nobody can land is not a family" % min(widths))
if len(set(widths)) == 1:
    die("every band is the same width; the design asks for uneven ones")


# ---- the gems -------------------------------------------------------------------------------------

if len(set(GEM_AT)) != len(GEM_AT):
    dupes = sorted(set(x for x in GEM_AT if GEM_AT.count(x) > 1))
    die("gem indices repeat: %s" % dupes)
for i, at in enumerate(GEM_AT):
    if not 0 <= at <= MAX:
        die("gem %s sits at %d, outside 0..%d" % (GEM[i], at, MAX))
bands_hit = [family_index(at) for at in GEM_AT]
if len(set(bands_hit)) != len(GEM_AT):
    twice = sorted(FAMILY[b] for b in set(bands_hit) if bands_hit.count(b) > 1)
    die("two gems share a band (%s) — one per family, or a band becomes the lucky one" % twice)
if len(set(GEM)) != len(GEM):
    die("two gems share a name: %s" % sorted(set(g for g in GEM if GEM.count(g) > 1)))
if len(set(GEM_RGB)) != len(GEM_RGB):
    die("two gems share a colour — they must be told apart by eye")
rate = len(GEM_AT) / float(MAX + 1)
if not 0.008 <= rate <= 0.02:
    die("one fish in %.0f is a gem; the design says about one in 83" % (1 / rate))


# ---- inheritance ----------------------------------------------------------------------------------
# Pattern.inherit: the parents' mean plus a gaussian of about 12, clamped. Re-implemented rather than
# imported, because what matters is the RANGE and the DRIFT, not the exact draw.

m = re.search(r"rng\.nextGaussian\(\) \* ([\d.]+)", src)
if not m:
    die("inherit no longer mutates by a gaussian — the line cannot drift toward a family")
SD = float(m.group(1)) if m else 12.0
if not 4.0 <= SD <= 30.0:
    die("the mutation is a gaussian of %.1f: under 4 a line never moves, over 30 it is a re-roll" % SD)

import random as _r


def inherit(mother, father, rng):
    m_, f_ = (mother if 0 <= mother <= MAX else father), (father if 0 <= father <= MAX else mother)
    if not 0 <= m_ <= MAX:
        return -1
    return max(0, min(MAX, (m_ + f_) // 2 + int(round(rng.gauss(0, SD)))))


rng = _r.Random(20250903)
for trial in range(20000):
    a, b = rng.randint(0, MAX), rng.randint(0, MAX)
    kid = inherit(a, b, rng)
    if not 0 <= kid <= MAX:
        die("inherit(%d, %d) gave %d, outside 0..%d" % (a, b, kid, MAX))
        break
if inherit(-1, -1, rng) != -1:
    die("two parents with no pattern must give no pattern, not an invented one")
for parent in (0, 500, MAX):
    if not 0 <= inherit(parent, -1, rng) <= MAX:
        die("a single known parent (%d) must still breed in range" % parent)

# Ten generations of a line bred toward itself: it must stay in the family it started in far more
# often than not, or "breeding toward a mark" means nothing.
held = 0
for line in range(2000):
    rng2 = _r.Random(line)
    start = rng2.randint(0, MAX)
    a = b = start
    for gen in range(10):
        a, b = inherit(a, b, rng2), inherit(a, b, rng2)
    if family_index((a + b) // 2) == family_index(start):
        held += 1
if held < 1000:
    die("only %d lines in 2000 were still in their own family after ten generations — the index is "
        "not heritable enough to breed toward" % held)


# ---- the tint is stable ----------------------------------------------------------------------------
# Pattern.paint / shift / hsv, re-implemented from the same tables, plus the koi's four paint layers
# out of FishMorph.java. Nothing here may consult a clock or a random source, and neither may the Java.

morph = io.open(MORPH, encoding="utf-8").read()
mm = re.search(r"KOI_PAINT = java\.util\.Map\.of\((.*?)\);", morph, re.S)
if not mm:
    sys.exit("no KOI_PAINT table in FishMorph.java")
KOI = {}
for name, body in re.findall(r'"(\w+)",\s*new int\[\]\{([^}]*)\}', mm.group(1)):
    KOI[name] = [int(x, 0) for x in re.findall(r"-?0x[0-9A-Fa-f]+|-?\d+", body)]
if len(KOI) < 9:
    die("only %d koi varieties have paint; the genetics name nine" % len(KOI))


def hue_shift(p):
    i = family_index(p)
    start = BAND[i]
    end = BAND[i + 1] if i + 1 < len(BAND) else MAX + 1
    return HUE[i] + ((p - start) / float(end - start) - 0.5) * 20.0


def offset(p):
    return (p * 7 + p // 13) % 5 - 2


def shift(rgb, degrees, lift):
    r, g, b = ((rgb >> 16) & 0xFF) / 255.0, ((rgb >> 8) & 0xFF) / 255.0, (rgb & 0xFF) / 255.0
    mx, mn = max(r, g, b), min(r, g, b)
    d = mx - mn
    if d <= 0.0:
        h = 0.0
    elif mx == r:
        h = ((g - b) / d + 6.0) % 6.0
    elif mx == g:
        h = (b - r) / d + 2.0
    else:
        h = (r - g) / d + 4.0
    s = 0.0 if mx <= 0.0 else d / mx
    h = ((h * 60.0 + degrees) % 360.0 + 360.0) % 360.0
    if s < 0.08:
        s = min(0.07, s + 0.07)
    v = max(0.0, min(1.0, mx + lift))
    c = v * s
    x = c * (1.0 - abs((h / 60.0) % 2.0 - 1.0))
    o = v - c
    sector = int(h / 60.0) % 6
    r, g, b = [(c, x, 0), (x, c, 0), (0, c, x), (0, x, c), (x, 0, c), (c, 0, x)][sector]
    return (byte(r + o) << 16) | (byte(g + o) << 8) | byte(b + o)


def byte(v):
    return max(0, min(255, int(round(v * 255.0))))


def paint(rgb, p, patch):
    if not 0 <= p <= MAX:
        return rgb
    if p in GEM_AT:
        return GEM_RGB[GEM_AT.index(p)]
    return shift(rgb, hue_shift(p), offset(p) * 0.03 if patch else 0.0)


def koi_tint(variety, layer, p):
    paint_row = KOI.get(variety, KOI["kohaku"])
    c = paint_row[layer] if 0 <= layer < len(paint_row) else -1
    return paint(paint_row[0] if c < 0 else c, p, layer > 0 and c >= 0)


seen = {}
for variety in sorted(KOI):
    for layer in range(4):
        for p in range(0, MAX + 1):
            once, twice = koi_tint(variety, layer, p), koi_tint(variety, layer, p)
            if once != twice:
                die("koiTint(%s, %d, %d) is not a function of its arguments" % (variety, layer, p))
                break
            if not 0 <= once <= 0xFFFFFF:
                die("koiTint(%s, %d, %d) is not a colour: %r" % (variety, layer, p, once))
            seen[(variety, layer, p)] = once

# A gem takes the WHOLE fish: every layer, one colour, or a gem koi would come out a gem with patches.
for variety in sorted(KOI):
    for at, rgb in zip(GEM_AT, GEM_RGB):
        got = set(koi_tint(variety, layer, at) for layer in range(4))
        if got != {rgb}:
            die("gem #%d on a %s paints %s, not one colour" % (at, variety, sorted(got)))

# Neighbours are cousins, the ends of a band are not twins: within one family the hue must move, and
# it must move by less between #n and #n+1 than across the whole band.
for i, name in enumerate(FAMILY):
    start = BAND[i]
    end = (BAND[i + 1] if i + 1 < len(BAND) else MAX + 1) - 1
    if abs(hue_shift(start) - hue_shift(end)) < 5.0:
        die("the %s band turns only %.1f degrees end to end — every fish in it is a twin"
            % (name, abs(hue_shift(start) - hue_shift(end))))
    if abs(hue_shift(start) - hue_shift(start + 1)) > 1.0:
        die("#%d and #%d differ by more than a degree — neighbours should be cousins" % (start, start + 1))

# And the Java itself must not be able to wander. inherit is the ONE method allowed a random source.
body_without_inherit = re.sub(r"public static int inherit\(.*?\n    \}", "", src, flags=re.S)
for token in ("Math.random", "currentTimeMillis", "nanoTime", "hashCode", "nextInt", "nextDouble",
              "nextGaussian", "System.identityHashCode"):
    if token in body_without_inherit:
        die("Pattern.java reaches for %s outside inherit — the same fish would change colour between "
            "two loads, which is the one bug this feature cannot have" % token)


# ---- the words exist -------------------------------------------------------------------------------

lang = io.open(LANG, encoding="utf-8").read()
for name in FAMILY:
    if '"pattern.riverfishing.%s"' % name not in lang:
        die("no name for the %s family in en_us.json" % name)
for name in GEM:
    if '"gem.riverfishing.%s"' % name not in lang:
        die("no name for the %s gem in en_us.json" % name)
for key in ("card.riverfishing.pattern", "journal.riverfishing.patterns", "tooltip.riverfishing.pattern"):
    if '"%s"' % key not in lang:
        die("no %s in en_us.json" % key)


# ---- the value term --------------------------------------------------------------------------------

mv = re.search(r"return isGem\(pattern\) \? ([\d.]+) : topBand\(pattern\) \? ([\d.]+) : ([\d.]+);", src)
if not mv:
    die("Pattern.value is no longer gem / top band / everything else")
elif (float(mv.group(1)), float(mv.group(2)), float(mv.group(3))) != (6.0, 1.5, 1.0):
    die("the counter pays x%s for a gem, x%s for the top band, x%s otherwise — the design says 6 / 1.5 / 1"
        % mv.groups())


if fails:
    print("FAILED:")
    for x in fails:
        print("  " + x)
    sys.exit(1)
print("pattern index: %d bands covering 0..%d (widths %s), %d gems one per band (1 in %.0f), "
      "inheritance in range over 20000 crosses and %d/2000 lines held their family for ten "
      "generations, %d (variety, layer, pattern) tints stable"
      % (len(FAMILY), MAX, ",".join(str(w) for w in widths), len(GEM_AT), 1 / rate, held, len(seen)))
