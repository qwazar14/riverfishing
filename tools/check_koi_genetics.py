# -*- coding: utf-8 -*-
"""§koi-genes §koi-metal: the koi's four loci, checked against the Punnett squares they claim to teach.

    py -X utf8 tools/check_koi_genetics.py

A koi variety is a genotype and the mod says so out loud — the journal page prints the table. So the
table is checked here rather than trusted: every genotype names exactly one variety, all seventeen
are reachable, a kohaku pair breeds kohaku plus the platinum hiding in it, a tancho needs both homozygotes
at once, and the WILD table never rolls the two that must be bred.

Like tools/check_carp_genetics.py, nothing here imports Minecraft or Java. The words, the loci string,
the variety table, the wild weights and the old-card defaults are READ OUT of Genome.java, so renaming
a variety in Java fails this file instead of quietly making it a liar; the biology underneath is
written out by hand, because that is the half that must not follow the code.
"""
import io, itertools, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GENOME = os.path.join(ROOT, "common/src/main/java/com/riverfishing/fish/Genome.java")
CARD = os.path.join(ROOT, "common/src/main/java/com/riverfishing/fish/CatchCard.java")
MANAGER = os.path.join(ROOT, "common/src/main/java/com/riverfishing/fishing/FishingManager.java")

fails = []


def die(msg):
    fails.append(msg)


src = io.open(GENOME, encoding="utf-8").read()

# ---- what Java says -------------------------------------------------------------------------------

m = re.search(r'String LOCI = "([A-Za-z]+)"', src)
if not m:
    sys.exit("no LOCI in Genome.java")
LOCI = m.group(1)

def table(name):
    m = re.search(r"String\[\] %s = \{(.*?)\};" % name, src, re.S)
    if not m:
        sys.exit("no %s table in Genome.java" % name)
    return re.findall(r'"([^"]+)"', m.group(1))

KOI_TABLE = [r.split("=") for r in table("KOI_TABLE")]
mk = re.search(r'String KOI_LOCI = "([A-Z]+)"', src)
KOI_LOCI = mk.group(1) if mk else "WRB"
WILD = [(r.split("=")[0], int(r.split("=")[1])) for r in table("WILD_KOI")]
NAMES = [v for _, v in KOI_TABLE]
OLD = dict(re.findall(r"locus == '([WRB])' \? \"(\w\w)\"", src))
OLD_IDS = dict(re.findall(r'"(carp_koi_\w+)", "(\w+)"', src))


def variety(w, r, b, g="gg"):
    """koiVariety, as Genome.java walks it: the FIRST row that fits names the fish.

    §koi-metal added G, the lustre, as a fourth locus and "*" as a want that does not care — which is
    what every row written before it carries, and why an old koi's variety cannot change."""
    got = {"W": w, "R": r, "B": b, "G": g}
    for pat, name in KOI_TABLE:
        ok = True
        for i, locus in enumerate(KOI_LOCI):
            want, pair = pat[i * 2:i * 2 + 2], got[locus]
            dom = any(c.isupper() for c in pair)
            if want[1] == "*":
                continue
            if want[1] == "_":
                ok = ok and dom
            elif want[1].isupper():
                ok = ok and dom and pair[0] == pair[1]
            else:
                ok = ok and not dom
        if ok:
            return name
    return None


# ---- the table the design doc draws (docs/design/breeding-api.md, Layer 9) -------------------------

if LOCI != "SCVFKNWRBG":
    die("LOCI is %r — the koi loci must be W, R, B then G, after the six a carp carries" % LOCI)
if KOI_LOCI != "WRBG":
    die("KOI_LOCI is %r — the variety rows are written W R B G" % KOI_LOCI)
if (OLD.get("W"), OLD.get("R"), OLD.get("B")) != ("WW", "Rr", "bb"):
    die("a koi card written before the colour loci defaults to %r, not WW/Rr/bb (kohaku): every koi "
        "already in a chest changes colour" % OLD)
for want_id, want_v in (("carp_koi_kohaku", "kohaku"), ("carp_koi_tancho_sanke", "tancho"),
                        ("carp_koi_showa_sanke", "showa"), ("carp_koi_asagi", "asagi"),
                        ("carp_koi_bekko", "bekko")):
    if OLD_IDS.get(want_id) != want_v:
        die("Genome maps the old id %s to %r, not %r — an old world's koi changes variety"
            % (want_id, OLD_IDS.get(want_id), want_v))

# The eight matt varieties, the tancho above them, and §koi-metal's eight: the same eight colour bases
# again with the lustre on. Gold is a karasu made metallic, which is the one that has to be bred for.
METALLIC = {"kohaku": "sakura_ogon", "taisho_sanke": "yamatonishiki", "showa": "kin_showa",
            "bekko": "gin_bekko", "asagi": "kujaku", "platinum": "ogon",
            "hi_utsuri": "kin_hi_utsuri", "karasu": "yamabuki"}
WANT = ["kohaku", "taisho_sanke", "showa", "bekko", "asagi", "platinum", "hi_utsuri", "karasu",
        "tancho"] + sorted(METALLIC.values())
if sorted(NAMES) != sorted(WANT):
    die("the varieties are %s, the design doc names %s" % (sorted(NAMES), sorted(WANT)))

# Every genotype: four ways to write each pair (both allele orders), 4^4 = 256 in all.
PAIRS = {"W": ["WW", "Ww", "wW", "ww"], "R": ["RR", "Rr", "rR", "rr"], "B": ["BB", "Bb", "bB", "bb"],
         "G": ["GG", "Gg", "gG", "gg"]}
seen = {}
for w, r, b, g in itertools.product(PAIRS["W"], PAIRS["R"], PAIRS["B"], PAIRS["G"]):
    v = variety(w, r, b, g)
    if v is None:
        die("%s %s %s %s falls through the table and names no variety" % (w, r, b, g))
        continue
    seen.setdefault(v, []).append((w, r, b, g))

    # The biology, written out by hand — the doc's table, read as three yes/no questions.
    W, R, B = "W" in w, "R" in r, "B" in b
    if W and R and not B and w in ("WW",) and r in ("RR",):
        want = "tancho"          # WW RR bb: both homozygotes at once, and nothing else is
    elif W and R and not B:
        want = "kohaku"
    elif W and R and B:
        want = "taisho_sanke"
    elif not W and R and B:
        want = "showa"
    elif W and not R and B:
        want = "bekko"
    elif not W and not R and B:
        want = "asagi"
    elif W and not R and not B:
        want = "platinum"
    elif not W and R and not B:
        want = "hi_utsuri"
    else:
        want = "karasu"
    # §koi-metal: the lustre renames the fish it is on — except a tancho, which outranks it.
    if "G" in g and want != "tancho":
        want = METALLIC[want]
    if v != want:
        die("%s %s %s %s reads as %r, the table says %r" % (w, r, b, g, v, want))

for name in WANT:
    if name not in seen:
        die("no genotype at all produces %s — the variety is unreachable" % name)

# Tancho is the one variety that needs BOTH homozygotes; nothing else may.
for w, r, b, g in seen.get("tancho", []):
    if (w, r, b) != ("WW", "RR", "bb"):
        die("tancho also comes out of %s %s %s — it must be WW RR bb and nothing else" % (w, r, b))
if len(seen.get("tancho", [])) != len(PAIRS["G"]):
    die("tancho has %d genotypes, expected WW RR bb with each of the four lustre pairs"
        % len(seen.get("tancho", [])))


# ---- the Punnett squares --------------------------------------------------------------------------

def cross(mother, father):
    """Every equally-likely child of two (W, R, B, G) parents, counted by variety.

    A three-pair parent is read as gg — matt — which is exactly how Genome.pair answers for a koi
    card written before the lustre locus existed."""
    m = tuple(mother) + ("gg",) * (4 - len(mother))
    f = tuple(father) + ("gg",) * (4 - len(father))
    out = {}
    for aw in m[0]:
        for bw in f[0]:
            for ar in m[1]:
                for br in f[1]:
                    for ab in m[2]:
                        for bb_ in f[2]:
                            for ag in m[3]:
                                for bg in f[3]:
                                    key = variety(sort(aw + bw), sort(ar + br), sort(ab + bb_),
                                                  sort(ag + bg))
                                    out[key] = out.get(key, 0) + 1
    return out


def sort(pair):
    return "".join(sorted(pair, key=lambda c: (c.islower(), c)))


def expect(name, mother, father, want):
    got = cross(mother, father)
    total = float(sum(got.values()))
    share = dict((k, round(v / total, 4)) for k, v in got.items())
    if share != want:
        die("%s: %s, expected %s" % (name, share, want))


# Two heterozygous kohaku: mostly kohaku, with the platinum that was hiding in the r allele coming out
# at a quarter of the W_ fish — and a tancho only from the corner where both go homozygous. That is the
# lesson: a kohaku pair does NOT breed only kohaku, and the prize is one square in sixteen.
expect("kohaku x kohaku (Ww Rr bb)", ("Ww", "Rr", "bb"), ("Ww", "Rr", "bb"),
       {"kohaku": 0.5, "tancho": 0.0625, "platinum": 0.1875, "hi_utsuri": 0.1875, "karasu": 0.0625})

# Two tancho breed true forever: both parents are homozygous at every locus that matters.
expect("tancho x tancho", ("WW", "RR", "bb"), ("WW", "RR", "bb"), {"tancho": 1.0})

# A kohaku over a bekko: the black comes back on every fish (BB x bb is all Bb), so nothing is a
# kohaku any more — this is why sanke are easy and kohaku strains are kept apart.
expect("kohaku x bekko (BB)", ("WW", "RR", "bb"), ("WW", "rr", "BB"), {"taisho_sanke": 1.0})

# A showa (ww) over a kohaku (WW): every child is Ww, so the dark ground disappears for a generation.
expect("showa x kohaku", ("ww", "RR", "BB"), ("WW", "RR", "bb"), {"taisho_sanke": 1.0})


# ---- the draw round-trips ------------------------------------------------------------------------
# koiGenome writes the genotype a fish DRAWN as a named variety carries, and koiVariety reads it back.
# Both directions have to agree or the water hands out a fish whose card contradicts the draw. The
# ambiguity is real: a kohaku is "W_ R_ bb", and one of its four W/R combinations is WW RR bb, which is
# the tancho above it — so the generator's last, forced-heterozygous pass is what has to be safe.

def generate(pattern, homozygous):
    """koiGenome's rule: a fixed pair where the row fixes it, otherwise the caller's coin."""
    out = []
    for i, locus in enumerate(KOI_LOCI):
        want = pattern[i * 2 + 1]
        low = locus.lower()
        # §koi-metal: a locus the row does not care about is written RECESSIVE, so a wild kohaku is
        # never accidentally metallic.
        out.append(low + low if want in (low, "*")
                   else locus + (locus if want == locus or homozygous else low))
    return out


for pattern, name in KOI_TABLE:
    forced = generate(pattern, homozygous=False)          # the guaranteed last try
    if variety(*forced) != name:
        die("a %s drawn from the water writes %s, which reads back as %r — the draw and the card "
            "disagree" % (name, " ".join(forced), variety(*forced)))
    both = generate(pattern, homozygous=True)             # the luckiest coin the loop can throw
    # kohaku is W_ R_ bb, and its luckiest coin is WW RR bb — the tancho above it. Sakura ogon is the
    # same row with the lustre on, so it shades into the same place for the same reason.
    if variety(*both) != name and name not in ("kohaku", "sakura_ogon"):
        die("%s can come out homozygous as %s, which reads as %r — only kohaku may shade into "
            "another variety (into tancho, which is the point)" % (name, " ".join(both), variety(*both)))


# §koi-metal: two kujaku (ww rr B_ G_) — the metallic the water does give you. A sixteenth of the
# clutch is the gold nobody can catch: bb out of the black pair, still metallic. That is the whole
# breeding project in one square, and the reason the lustre had to be findable in the wild at all.
expect("kujaku x kujaku (ww rr Bb Gg)", ("ww", "rr", "Bb", "Gg"), ("ww", "rr", "Bb", "Gg"),
       {"kujaku": 0.5625, "yamabuki": 0.1875, "asagi": 0.1875, "karasu": 0.0625})


# ---- the wild table -------------------------------------------------------------------------------

if not WILD:
    die("no WILD_KOI table in Genome.java")
for name, _ in WILD:
    if name in ("platinum", "tancho", "yamabuki"):
        die("the wild table rolls %s — the prizes must be BRED, not found, or the tank is pointless"
            % name)
    if name not in NAMES:
        die("the wild table rolls %r, which is not a variety" % name)
for name in ("kohaku", "taisho_sanke", "bekko"):
    if name not in dict(WILD):
        die("the wild table never rolls %s, which the doc calls a common variety" % name)
common = sum(w for n, w in WILD if n in ("kohaku", "taisho_sanke", "bekko"))
rare = sum(w for n, w in WILD if n not in ("kohaku", "taisho_sanke", "bekko"))
if common <= rare:
    die("the wild table is weighted %d common to %d rare — the common ones must dominate"
        % (common, rare))


# ---- the places the rules live in code ------------------------------------------------------------

c = io.open(CARD, encoding="utf-8").read()
if 'Genome.koiGenome(g.toString(), v.substring(4), rng)' not in c:
    die("CatchCard no longer writes the koi's colour loci onto the card")
if 'c.putString("Variety", "koi_" + Genome.koiVariety(g.toString()))' not in c:
    die("CatchCard no longer reads the variety BACK off the genotype it just wrote")

f = io.open(MANAGER, encoding="utf-8").read()
if "Genome.wildKoi(random.nextDouble())" not in f:
    die("the draw no longer rolls a wild koi variety out of the table")
if "carp_koi_kohaku" in f:
    die("FishingManager still hands out one of the five old koi ids")


# ---- the table the patchnote prints ---------------------------------------------------------------
# 0.9.0's note lists all seventeen with their genotypes, and a player will breed from that list. So it
# is checked against the same rows the game reads: a variety renamed in Java, or a row whose pattern
# changed, fails here rather than teaching somebody a cross that cannot work.

NOTE = os.path.join(ROOT, "docs/patchnotes/0.9.0.md")
if os.path.exists(NOTE):
    note = io.open(NOTE, encoding="utf-8").read()
    for pattern, name in KOI_TABLE:
        want = " ".join(pattern[i * 2:i * 2 + 2] for i in range(len(KOI_LOCI))
                        if pattern[i * 2 + 1] != "*")
        if ("`%s`" % want) not in note:
            die("the patchnote does not print %s as `%s` — the list a breeder works from is wrong"
                % (name, want))
    for name in NAMES:
        pretty = name.replace("_", " ").title().replace("Ogon", "Ogon")
        if pretty.lower() not in note.lower():
            die("the patchnote never names %s" % pretty)

if fails:
    print("FAILED:")
    for x in fails:
        print("  " + x)
    sys.exit(1)
print("koi genetics: %d varieties over %d loci, all %d genotypes agree with the table, tancho is "
      "WW RR bb alone, five Punnett squares, wild %d:%d common:rare with none of the three prizes"
      % (len(NAMES), len(KOI_LOCI), sum(len(v) for v in seen.values()), common, rare))
