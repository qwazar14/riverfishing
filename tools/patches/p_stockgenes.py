# -*- coding: utf-8 -*-
"""§stocked-genes: the water gives back the fish that were put in it.

    py -X utf8 tools/patches/p_stockgenes.py <root> [1211|1201|26]

Reported, and right: stock a pond with every koi variety there is, net it, and one variety comes up —
without even a name on it. Three things were true at once.

1. The population ledger tallied FOUR loci, S C V F, with a comment saying a population has no average
   scale cover. The carp's K/N and the koi's W R B G were never recorded at all, so nothing a player
   stocks about a fish's LOOK was ever written down.
2. A caught koi's variety was rolled from the WILD table (`maybeKoi` → `Genome.wildKoi`), 0.5% on carp
   tackle, 35% in a cherry grove — the same roll in a pond stocked with two hundred kohaku as in a
   ditch nobody has touched.
3. A netted `koi_carp` got no variety AT ALL: the card asks `varietyOfSpecies("koi_carp")`, which is
   empty because the variety lives in the genotype, so nothing was written and the fish drew as a base
   koi. That is the missing name in the report.

So the ledger now tallies EVERY locus with its own allele count, and a fish taken out of stocked water
has those loci drawn from the pool — each allele independently at the frequency the pool holds, which
is Hardy-Weinberg and is exactly "you get back what you put in, in the proportions you put it in".
Release twenty kohaku and one showa and the water is mostly kohaku. Loci nobody stocked keep the roll
they always had, so every unstocked water in every old save behaves as before.
"""
import io, os, sys

ROOT = sys.argv[1]
D = sys.argv[2] if len(sys.argv) > 2 else "1211"
J = os.path.join(ROOT, "common/src/main/java/com/riverfishing/")
def GI(expr):
    """A tag read whose KEY is built at runtime: 26.x wants a default, the others do not."""
    return ("t.getIntOr(%s, 0)" % expr) if D == "26" else ("t.getInt(%s)" % expr)


def rd(p): return io.open(p, encoding="utf-8").read()


def wr(p, s): io.open(p, "w", encoding="utf-8", newline="\n").write(s)


def sub(s, old, new, what):
    assert old in s, "%s moved" % what
    return s.replace(old, new, 1)


# ---- 0. the ledger needs to know how long a genome is ---------------------------------------------
p = J + "fish/Genome.java"
s = rd(p)
if "private static int pairs(String genome)" in s:
    s = sub(s, "    private static int pairs(String genome) {",
            "    public static int pairs(String genome) {   // §stocked-genes: the ledger counts by it too",
            "Genome.pairs")
    wr(p, s)
    print("  Genome: pairs() is public")

# ---- 1. the ledger remembers every locus ----------------------------------------------------------
p = J + "fishing/StockedData.java"
s = rd(p)
if "stocked-genes" not in s:
    old = """    private static void tally(CompoundTag t, String genome) {
        if (genome == null || genome.isEmpty()) return;
        // §scale-genes: the four loci every fish carries, never the carp's K/N — a population
        // has no average scale cover, and Dom4/Dom5 would be counters nothing ever reads.
        for (int i = 0; i < com.riverfishing.fish.Genome.COMMON_LOCI.length(); i++) {
            char l = com.riverfishing.fish.Genome.COMMON_LOCI.charAt(i);
            int dom = !com.riverfishing.fish.Genome.dominant(genome, l) ? 0
                    : com.riverfishing.fish.Genome.pure(genome, l) ? 2 : 1;
            t.putInt("Dom" + i, %s + dom);
        }
        t.putInt("Tot", %s + 2);
    }""" % (GI('"Dom" + i'), GI('"Tot"'))
    assert old in s, "tally moved"
    s = s.replace(old, """    private static void tally(CompoundTag t, String genome) {
        if (genome == null || genome.isEmpty()) return;
        // §stocked-genes: EVERY locus, not the four common ones. The old comment said a population has
        // no average scale cover, which is true and beside the point — a population has a scale-gene
        // FREQUENCY, and without it nothing a player stocks about how a fish looks is written down at
        // all. Dom0..3 keep their meaning (and the global Tot with them, for shares()); the rest are
        // new counters with their own totals, because a locus only the carps carry is only counted on
        // the fish that carry it.
        String loci = com.riverfishing.fish.Genome.LOCI;
        int pairs = com.riverfishing.fish.Genome.pairs(genome);
        for (int i = 0; i < loci.length(); i++) {
            if (i >= pairs) break;                       // this fish does not carry that locus
            char l = loci.charAt(i);
            int dom = !com.riverfishing.fish.Genome.dominant(genome, l) ? 0
                    : com.riverfishing.fish.Genome.pure(genome, l) ? 2 : 1;
            t.putInt("Dom" + i, %s + dom);
            t.putInt("Tot" + i, %s + 2);
        }
        t.putInt("Tot", %s + 2);
    }

    /**
     * §stocked-genes: the rolled genome with the POOL's alleles laid over it — one independent draw
     * per allele at the frequency this water holds, which is what makes a stocked pond give back what
     * was stocked in it and in the proportions it was stocked. A locus nobody ever released here keeps
     * whatever the roll gave it, so unstocked water behaves exactly as it always did.
     */
    public String overlay(long region, String species, String rolled, java.util.Random rng) {
        CompoundTag t = brood.get(key(region, species));
        if (t == null) return rolled;
        String loci = com.riverfishing.fish.Genome.LOCI;
        String[] had = rolled == null || rolled.isEmpty() ? new String[0] : rolled.split(" ");
        StringBuilder out = new StringBuilder();
        boolean any = false;
        for (int i = 0; i < loci.length(); i++) {
            char u = loci.charAt(i), l = Character.toLowerCase(u);
            int tot = %s;
            String pair;
            if (tot > 0) {
                double share = %s / (double) tot;
                char a = rng.nextDouble() < share ? u : l, b = rng.nextDouble() < share ? u : l;
                if (a == l && b == u) { a = u; b = l; }   // dominant first, as it is written
                pair = "" + a + b;
                any = true;
            } else if (i < had.length) {
                pair = had[i];
            } else {
                // Never stocked and never rolled: the defaults Genome.pair() hands out for a string
                // that stops short — a scaled carp, and the recessive of everything else.
                pair = u == 'K' ? "KK" : u == 'N' ? "nn" : ("" + l + l);
            }
            if (i > 0) out.append(' ');
            out.append(pair);
        }
        return any ? out.toString() : rolled;
    }""" % (GI('"Dom" + i'), GI('"Tot" + i'), GI('"Tot"'),
            GI('"Tot" + i'), GI('"Dom" + i')), 1)
    wr(p, s)
    print("  StockedData: every locus tallied, and an overlay to draw from")

# ---- 2. the card asks the water --------------------------------------------------------------------
p = J + "fish/CatchCard.java"
s = rd(p)
if "stocked-genes" not in s:
    old = """        if (v.startsWith("koi_")) {
            g = new StringBuilder(Genome.koiGenome(g.toString(), v.substring(4), rng));
            c.putString("Variety", "koi_" + Genome.koiVariety(g.toString()));
        } else if (!v.isEmpty()) {"""
    assert old in s, "the variety block moved"
    s = s.replace(old, """        // §stocked-genes: a koi SPECIES with no variety word is a netted koi — the water drew the
        // species and nobody drew a look. It still has a genotype, and the genotype is the variety.
        boolean koi = v.startsWith("koi_") || (p != null && Genome.isKoiId(p.id.getPath()));
        if (koi) {
            g = new StringBuilder(v.startsWith("koi_")
                    ? Genome.koiGenome(g.toString(), v.substring(4), rng)
                    : Genome.koiGenome(g.toString(), Genome.wildKoi(rng.nextDouble()), rng));
        } else if (!v.isEmpty()) {""", 1)

    old = """            g.append(' ').append(scaled ? (rng.nextBoolean() ? "KK" : "Kk") : "kk");
            g.append(' ').append(nude ? "Nn" : "nn");
            // Read back off the genotype rather than stored beside it: the card cannot then say
            // "mirror" over a pair of alleles that spell a leather carp.
            c.putString("Variety", Genome.carpVariety(g.toString()));
        }
        c.putString("Genes", g.toString());"""
    assert old in s, "the carp variety block moved"
    s = s.replace(old, """            g.append(' ').append(scaled ? (rng.nextBoolean() ? "KK" : "Kk") : "kk");
            g.append(' ').append(nude ? "Nn" : "nn");
        }
        // §stocked-genes: and THEN the water has its say. Where this species has been stocked here the
        // pool's alleles replace the rolled ones, locus by locus, so the fish that comes out is one of
        // the fish that went in. The variety is read back off the finished genotype either way — the
        // card cannot then say "mirror" over a pair of alleles that spell a leather carp, and a koi
        // that was never given a variety word ends up with the one its genes actually make.
        String genes = pool == null ? g.toString() : pool.apply(g.toString());
        if (koi) {
            c.putString("Variety", "koi_" + Genome.koiVariety(genes));
        } else if (!v.isEmpty()) {
            c.putString("Variety", Genome.carpVariety(genes));
        }
        c.putString("Genes", genes);""", 1)

    # the parameter, and both callers
    s = s.replace("""    private static void body(CompoundTag c, FishProfile p, int weightG, String morph, Random rng,
                             byte natureIn, String variety, int pattern) {""",
                  """    private static void body(CompoundTag c, FishProfile p, int weightG, String morph, Random rng,
                             byte natureIn, String variety, int pattern,
                             java.util.function.UnaryOperator<String> pool) {""", 1)
    assert "UnaryOperator<String> pool" in s, "body's signature moved"
    # Both public builders know their water, so each makes its own pool and hands it down.
    POOL = ("""        // §stocked-genes: what this water has been stocked with, if anything — a no-op everywhere else.
        java.util.function.UnaryOperator<String> pool = p == null ? null
                : genes -> com.riverfishing.fishing.StockedData.get(level).overlay(
                        com.riverfishing.fishing.StockedData.region(%s), p.id.getPath(), genes, rng);
""")
    s = sub(s, "        body(c, p, weightG, morph, rng, s.nature, s.variety, pattern);",
            POOL % "s.target" + "        body(c, p, weightG, morph, rng, s.nature, s.variety, pattern, pool);",
            "build's body call")
    s = sub(s, '        body(c, p, weightG, "", rng, (byte) -1, "", pattern);',
            POOL % "pos" + '        body(c, p, weightG, "", rng, (byte) -1, "", pattern, pool);',
            "netted's body call")
    wr(p, s)
    print("  CatchCard: the pool overlays the roll, and a netted koi gets its name")
print("done (%s)" % D)
