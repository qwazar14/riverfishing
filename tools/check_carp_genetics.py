# -*- coding: utf-8 -*-
"""§scale-genes: the carp's two scale loci, checked against the Punnett squares they claim to teach.

    py -X utf8 tools/check_carp_genetics.py

The mod says a carp's scale cover is a real two-locus Mendelian system: K scaled (dominant) / k mirror,
N nude (dominant) / n normal, and NN never develops. That claim is the whole point of the feature — a
child reading the tank should be able to check it against a textbook — so it is checked here rather than
trusted.

Nothing here imports Minecraft. The words and the constants are READ OUT of Genome.java (the variety
names, the loci string, the default pair an old card gets, the lethal test) so that renaming "naked" to
"leather" in Java fails this file instead of silently making it a liar; the biology below is written out
by hand, because that is the part that must not follow the code.
"""
import io, itertools, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GENOME = os.path.join(ROOT, "common/src/main/java/com/riverfishing/fish/Genome.java")
BREEDING = os.path.join(ROOT, "common/src/main/java/com/riverfishing/block/AquariumBreeding.java")
CARD = os.path.join(ROOT, "common/src/main/java/com/riverfishing/fish/CatchCard.java")

fails = []


def die(msg):
    fails.append(msg)


src = io.open(GENOME, encoding="utf-8").read()

# ---- what Java says ------------------------------------------------------------------------------

m = re.search(r'String LOCI = "([A-Za-z]+)"', src)
if not m:
    print("no LOCI in Genome.java")
    sys.exit(1)
LOCI = m.group(1)

# carpVariety's four words, in the order the ternary writes them: K&N, K&!N, !K&N, !K&!N.
m = re.search(r'carpVariety\(String genome\) \{.*?return dominant\(genome, \'K\'\) \? \(nude \? '
              r'"(\w+)" : "(\w+)"\) : \(nude \? "(\w+)" : "(\w+)"\);', src, re.S)
if not m:
    print("carpVariety in Genome.java is not the shape this check knows — read it and update this file")
    sys.exit(1)
LINEAR, SCALED, NAKED, MIRROR = m.groups()

# the pair() default for a card written before the scale loci existed
m = re.search(r"return locus == 'K' \? \"(\w\w)\" : locus == 'N' \? \"(\w\w)\"", src)
OLD_K, OLD_N = m.groups() if m else ("", "")

# the species ids that are one fish in different scales
IDS = dict(re.findall(r'"(\w+)", "(scaled|mirror|linear|naked)"', src))


def variety(k, n):
    """carpVariety, as Genome.java writes it: a capital anywhere in the pair is the dominant allele."""
    dom_k, dom_n = any(c.isupper() for c in k), any(c.isupper() for c in n)
    return (LINEAR if dom_n else SCALED) if dom_k else (NAKED if dom_n else MIRROR)


def lethal(n):
    return n == "NN"


# ---- the table the design doc draws (docs/design/breeding-api.md, Layer 7) ------------------------

if LOCI != "SCVFKN":
    die("LOCI is %r — the scale loci must be K then N, after the four every fish carries" % LOCI)
if (OLD_K, OLD_N) != ("KK", "nn"):
    die("an old card's missing pair defaults to %r/%r, not KK/nn: every carp in a chest becomes a "
        "mirror or a leather" % (OLD_K, OLD_N))
for want_id, want_v in (("carp", "scaled"), ("mirror_carp", "mirror"),
                        ("linear_carp", "linear"), ("naked_carp", "naked")):
    if IDS.get(want_id) != want_v:
        die("Genome.varietyOfSpecies maps %s to %r, not %r" % (want_id, IDS.get(want_id), want_v))

# All sixteen genotypes: four ways to write each pair, both orders included.
KP = ["KK", "Kk", "kK", "kk"]
NP = ["NN", "Nn", "nN", "nn"]
for k, n in itertools.product(KP, NP):
    scales = "K" in k
    nude = "N" in n
    want = LINEAR if (scales and nude) else SCALED if scales else NAKED if nude else MIRROR
    got = variety(k, n)
    if got != want:
        die("%s %s reads as %r, the table says %r" % (k, n, got, want))
if not lethal("NN") or lethal("Nn") or lethal("nn"):
    die("the lethal is not NN")


# ---- the Punnett squares -------------------------------------------------------------------------

def cross(mother, father):
    """Every equally-likely child of two (K pair, N pair) parents, as counts of phenotype."""
    out = {}
    for a in mother[0]:
        for b in father[0]:
            for c in mother[1]:
                for d in father[1]:
                    n = "".join(sorted(c + d, reverse=True))       # NN / Nn / nn
                    key = "dead" if lethal(n) else variety(a + b, n)
                    out[key] = out.get(key, 0) + 1
    return out


def expect(name, mother, father, want):
    got = cross(mother, father)
    total = float(sum(got.values()))
    share = dict((k, round(v / total, 4)) for k, v in got.items())
    if share != want:
        die("%s: %s, expected %s" % (name, share, want))
    return share


# A mirror is kk: it has no scaled allele to hand on, so a mirror pair breeds true forever. This is
# why a mirror strain is a strain and the leather one can never be.
expect("mirror x mirror", ("kk", "nn"), ("kk", "nn"), {MIRROR: 1.0})

# Two leather carp: a quarter of the eggs are NN and never develop, half are leather again, and the
# last quarter come out mirror. The lost quarter is what the tank charges for a leather clutch.
expect("naked x naked", ("kk", "Nn"), ("kk", "Nn"), {"dead": 0.25, NAKED: 0.5, MIRROR: 0.25})

# A pure scaled fish over a mirror: every child is Kk — scaled, and every one of them carrying k.
expect("KK scaled x mirror", ("KK", "nn"), ("kk", "nn"), {SCALED: 1.0})
# The same cross one generation on, when the scaled parent is itself Kk: the mirrors come back at half.
expect("Kk scaled x mirror", ("Kk", "nn"), ("kk", "nn"), {SCALED: 0.5, MIRROR: 0.5})

# Linear over scaled: no NN is possible (one parent is nn), so nothing dies — and the row of scales
# shows on half the fry, over the three-to-one the K locus is doing underneath.
expect("linear x scaled", ("Kk", "Nn"), ("Kk", "nn"),
       {SCALED: 0.375, LINEAR: 0.375, MIRROR: 0.125, NAKED: 0.125})


# ---- the two places the rules live in code -------------------------------------------------------

b = io.open(BREEDING, encoding="utf-8").read()
if not re.search(r"dominant\(Genome\.of\(mother\), 'N'\).*?dominant\(Genome\.of\(pair\[1\]\), 'N'\)", b, re.S):
    die("AquariumBreeding no longer shortens a clutch from two nude parents")
if "eggs * 3 / 4" not in b:
    die("AquariumBreeding's nude clutch is not a quarter short any more")
if "Genome.lethal(genome)" not in b:
    die("AquariumBreeding no longer re-crosses away from an NN egg")

c = io.open(CARD, encoding="utf-8").read()
if 'c.putString("Variety", Genome.carpVariety(g.toString()))' not in c:
    die("CatchCard no longer writes the variety onto the card")

if fails:
    print("FAILED:")
    for f in fails:
        print("  " + f)
    sys.exit(1)
print("carp genetics: %s/%s/%s/%s, NN lethal, five Punnett squares and all 16 genotypes agree"
      % (SCALED, MIRROR, LINEAR, NAKED))
