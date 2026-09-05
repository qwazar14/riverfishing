# -*- coding: utf-8 -*-
"""One chest holding every carp there is: the four scale varieties and every koi variety.

    py -X utf8 tools/gen_variety_chest.py [outfile]

A test command, but not a hand-written one: the genotypes are BUILT from Genome.java's own variety
table by the same rule koiGenome uses (a row's fixed pair where it fixes one, heterozygous where it
only asks for a dominant, recessive where it asks for none), then read back through the same matcher.
So the chest cannot drift from the genetics — add a variety in Java and it appears here, spelled
correctly, or this file says why it cannot be.

Too long for chat on any version: paste into a command block.
"""
import io, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GENOME = os.path.join(ROOT, "common/src/main/java/com/riverfishing/fish/Genome.java")
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "carp_chest_commands.txt")

src = io.open(GENOME, encoding="utf-8").read()
KOI_LOCI = re.search(r'String KOI_LOCI = "([A-Z]+)"', src)
KOI_LOCI = KOI_LOCI.group(1) if KOI_LOCI else "WRB"
rows = re.search(r"String\[\] KOI_TABLE = \{(.*?)\};", src, re.S).group(1)
KOI_TABLE = [r.split("=") for r in re.findall(r'"([^"]+=\w+)"', rows)]

# the six pairs every carp carries; a koi is a scaled carp with colour on top
COMMON = "SS CC VV FF KK nn"


def pairs_for(pattern):
    """koiGenome's forced pass: the pair each locus gets when nothing is left to chance."""
    out = []
    for i, locus in enumerate(KOI_LOCI):
        want, low = pattern[i * 2 + 1], locus.lower()
        out.append(low + low if want in (low, "*") else locus + (locus if want == locus else low))
    return out


def variety_of(genome_pairs):
    """koiVariety, read back: the first row that fits."""
    got = dict(zip(KOI_LOCI, genome_pairs))
    for pattern, name in KOI_TABLE:
        ok = True
        for i, locus in enumerate(KOI_LOCI):
            want, pair = pattern[i * 2 + 1], got[locus]
            dom = any(c.isupper() for c in pair)
            if want == "*":
                continue
            ok = ok and (dom if want == "_" else (dom and pair[0] == pair[1]) if want.isupper() else not dom)
        if ok:
            return name
    return None


# ---- the fish ---------------------------------------------------------------------------------
# carp: K_ nn scaled | kk nn mirror | K_ Nn linear | kk Nn leather
CARP = [("scaled", "KK nn", 2400, 52), ("mirror", "kk nn", 3100, 58),
        ("linear", "KK Nn", 2750, 55), ("naked", "kk Nn", 1980, 48)]

fish = []
for i, (v, kn, grams, cm) in enumerate(CARP):
    fish.append(("riverfishing:carp", v, "SS CC VV FF " + kn, grams, cm))
for i, (pattern, name) in enumerate(KOI_TABLE):
    pairs = pairs_for(pattern)
    back = variety_of(pairs)
    if back != name:
        sys.exit("%s would be written %s, which reads back as %s — fix pairs_for before shipping this"
                 % (name, " ".join(pairs), back))
    fish.append(("riverfishing:koi_carp", "koi_" + name, COMMON + " " + " ".join(pairs),
                 1000 + i * 55, 36 + i))

# indices spread across the twelve families, none of them a gem: a gem paints the whole fish one
# colour, which is the last thing a chest full of varieties wants to show.
PATTERN = [42, 137, 250, 366, 60, 155, 268, 372, 455, 530, 618, 690, 940, 88, 205, 310, 420, 505,
           640, 760, 900]


def command(dialect):
    out = []
    for slot, (item, v, genes, grams, cm) in enumerate(fish):
        card = ('{Variety:"%s",Genes:"%s",Pattern:%d,Nature:%db,Size:%db,Sex:%db,Value:%d,Eco:"stocked"}'
                % (v, genes, PATTERN[slot % len(PATTERN)], slot % 4, 2 + slot % 2, slot % 2, 20 + slot * 7))
        data = ('{Species:"%s",WeightG:%d,LengthCm:%d,Legal:1b,Card:%s}' % (item, grams, cm, card))
        if dialect == "new":
            out.append('{slot:%d,item:{id:"%s",count:1,components:{"minecraft:custom_data":%s}}}'
                       % (slot, item, data))
        else:
            out.append('{Slot:%db,id:"%s",Count:1b,tag:%s}' % (slot, item, data))
    return ("/give @p minecraft:chest[minecraft:container=[%s]]" % ",".join(out) if dialect == "new"
            else "/give @p minecraft:chest{BlockEntityTag:{Items:[%s]}}" % ",".join(out))


new, old = command("new"), command("old")
io.open(OUT, "w", encoding="utf-8", newline="\n").write(
    "%d carps in one chest: %d scale varieties + %d koi varieties, each with a full catch card.\n"
    "Too long for chat - paste into a COMMAND BLOCK. You get the chest as an item; place it and open it.\n\n"
    "=== 1.21.1 (fabric / neoforge) ===\n\n%s\n\n=== 1.20.1 (fabric / forge) ===\n\n%s\n\n"
    "On 26.x the 1.21.1 command spawns the right fish, but the icons stay unpainted: that branch draws\n"
    "a fish from a custom_model_data stamp the mod writes when one is CAUGHT, and an item made by hand\n"
    "never passed through it. The cards, the genes and the varieties are all correct.\n"
    % (len(fish), len(CARP), len(KOI_TABLE), new, old))
print("%d fish · 1.21.1 %d chars · 1.20.1 %d chars · %s"
      % (len(fish), len(new), len(old), os.path.relpath(OUT, ROOT)))
