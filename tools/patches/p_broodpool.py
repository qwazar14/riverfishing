# -*- coding: utf-8 -*-
"""§brood-pool: the water crosses the fish that are IN it, instead of averaging them into a cloud.

    py -X utf8 tools/patches/p_broodpool.py <root> [1211|1201|26]

Reported: a pond stocked with kin hi utsuri and kohaku handed back a PLATINUM. Simulating the pond by
the game's own rules showed the platinum was the least of it —

    stocked: 10 kin hi utsuri, 10 kohaku
    sakura_ogon 33.6%   kin_hi_utsuri 20.4%   kohaku 19.0%   tancho 12.3%   hi_utsuri 11.6%
    ogon 1.3%   platinum 0.7%   yamabuki 0.7%   karasu 0.4%

— the commonest fish in that water is a variety nobody released, and a third of the haul is varieties
nobody released. The platinum itself is honest (it is W_ rr bb; both parents show R_, and koiGenome
writes such a pair as RR or Rr on a coin, so both can carry a hidden r) but 33% sakura ogon is not.

§stocked-genes counted alleles and drew each locus INDEPENDENTLY from the population's frequencies.
That is Hardy-Weinberg, and Hardy-Weinberg is the equilibrium a large randomly-mating population
reaches after many generations. Applying it to two founders on the day they are released skips every
one of those generations: kohaku's W and R meet the utsuri's metallic G immediately, and the pond
produces its own F5 before the first pair has spawned once. I wrote that yesterday, and I wrote the
sentence "what went in comes back, in the proportions it went in" over the top of it, which is not what
it does.

So the ledger keeps a small ROSTER of the genomes actually released, and a fish taken out of the water
is the cross of two of them, by Mendel, through the same Genome.cross the aquarium uses. One variety in
the pond gives that variety back. Twenty kohaku and one showa gives mostly kohaku. Kohaku with kin hi
utsuri gives the F1 it should give, and the recessives surface when two carriers meet — at the rate
Mendel says, not at equilibrium.

The allele counters stay: shares() feeds the size genes and genome() feeds the journal's "what lives
here", and an average is the right answer for both. They are also the fallback for a water stocked
before this existed, where there is a count and no roster.
"""
import io, os, sys

ROOT = sys.argv[1]
D = sys.argv[2] if len(sys.argv) > 2 else "1211"
P = os.path.join(ROOT, "common/src/main/java/com/riverfishing/fishing/StockedData.java")

LIST = (lambda t, k: '%s.getListOrEmpty("%s")' % (t, k)) if D == "26" \
    else (lambda t, k: '%s.getList("%s", Tag.TAG_STRING)' % (t, k))
ELEM = (lambda l, i: '%s.getStringOr(%s, "")' % (l, i)) if D == "26" \
    else (lambda l, i: '%s.getString(%s)' % (l, i))

s = io.open(P, encoding="utf-8").read()
if "brood-pool" in s:
    print("  already patched")
    sys.exit(0)

# ---- 1. the roster: every genome released here, bounded -------------------------------------------
old = """    private void stamp(CompoundTag t, long day, String genome, java.util.UUID owner) {
        if (genome != null && !genome.isEmpty()) t.putString("Genome", genome);"""
assert old in s, "stamp moved"
s = s.replace(old, """    private void stamp(CompoundTag t, long day, String genome, java.util.UUID owner) {
        if (genome != null && !genome.isEmpty()) {
            t.putString("Genome", genome);
            roster(t, genome);   // §brood-pool
        }""", 1)

old = """    private static boolean ready(CompoundTag t) {"""
assert old in s, "ready() moved"
s = s.replace(old, """    /**
     * §brood-pool: how many released genomes a water remembers. Small on purpose — this is the brood
     * standing in the pond, not its whole history, and a roster long enough to hold every fish ever
     * released would drift back toward the average it exists to avoid. The oldest drops out, so a
     * water that has been restocked is the fish that are in it now.
     */
    private static final int ROSTER = 12;

    /** §brood-pool: remember this genome as one of the fish that is actually in this water. */
    private static void roster(CompoundTag t, String genome) {
        ListTag list = %s;
        list.add(net.minecraft.nbt.StringTag.valueOf(genome));
        while (list.size() > ROSTER) list.remove(0);
        t.put("Pool", list);
    }

    private static boolean ready(CompoundTag t) {""" % LIST("t", "Pool"), 1)

# ---- 2. a fish out of this water is the cross of two that went in ---------------------------------
old = """    public String overlay(long region, String species, String rolled, java.util.Random rng) {
        CompoundTag t = brood.get(key(region, species));
        if (t == null) return rolled;"""
assert old in s, "overlay moved"
s = s.replace(old, """    public String overlay(long region, String species, String rolled, java.util.Random rng) {
        CompoundTag t = brood.get(key(region, species));
        if (t == null) return rolled;
        // §brood-pool: the water crosses two of the fish that are IN it. This is the whole difference
        // between "you get back what you put in" and a gene cloud: two founders make an F1, not an
        // equilibrium, and a recessive waits until two carriers meet instead of arriving on day one.
        ListTag pool = %s;
        if (!pool.isEmpty()) {
            String a = %s;
            String b = %s;
            String bred = com.riverfishing.fish.Genome.cross(a, b, rng);
            for (int i = 0; i < 8 && com.riverfishing.fish.Genome.lethal(bred); i++) {
                bred = com.riverfishing.fish.Genome.cross(a, b, rng);
            }
            return lay(bred, rolled);
        }""" % (LIST("t", "Pool"),
                ELEM("pool", "rng.nextInt(pool.size())"),
                ELEM("pool", "rng.nextInt(pool.size())")), 1)

# ---- 3. …and the one place that knows how a pooled genome sits over a rolled one -------------------
old = """    /**
     * §stocked-genes: the rolled genome with the POOL's alleles laid over it"""
assert old in s, "overlay's doc moved"
s = s.replace(old, """    /**
     * §brood-pool: the bred genome laid over the rolled one, locus by locus — the water's answer wins
     * where it has one, and a locus the brood does not carry keeps whatever the roll gave it. That is
     * what lets a pond of carp still hand out the size and vigour the water itself decides.
     */
    private static String lay(String bred, String rolled) {
        if (bred == null || bred.isEmpty()) return rolled;
        String[] mine = bred.split(" ");
        String[] had = rolled == null || rolled.isEmpty() ? new String[0] : rolled.split(" ");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < Math.max(mine.length, had.length); i++) {
            if (i > 0) out.append(' ');
            out.append(i < mine.length ? mine[i] : had[i]);
        }
        return out.toString();
    }

    /**
     * §stocked-genes: the rolled genome with the POOL's alleles laid over it""", 1)

io.open(P, "w", encoding="utf-8", newline="\n").write(s)
print("  StockedData: a roster of %d, and a fish is the cross of two of them" % 12)
print("done (%s)" % D)
