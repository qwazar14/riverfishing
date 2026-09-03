# -*- coding: utf-8 -*-
"""§breeding stream H: the population's genome is an average, and it changes the fish you catch.

    py -X utf8 tools/patches/p_h.py <repo root> [1211|1201|26]

Anchor replacement on four existing files (StockedData, FishingManager, JournalOpenPacket, JournalScreen);
every insert carries a "§h" marker so a rerun finds it and does nothing. Exit 1 with the missing anchor
when a tree has drifted. Written once in the 1.21.1 dialect; 26.x gets the NBT getters and
ServerPlayer.level() rewritten by to26 — applied to the ANCHORS too, because stream C's inserts already
sit in the 26 tree in that dialect. 1.20.1 reads the 1.21.1 text unchanged for everything touched here.

Calls stream F's com.riverfishing.fishing.Ecosystem (frySurvival, weightScale) by contract, unstubbed.
"""
import io, os, re, sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
DIALECT = sys.argv[2] if len(sys.argv) > 2 else "1211"
SRC = os.path.join(ROOT, "common/src/main/java/com/riverfishing")
MARK = "§h"


def read(path):
    with io.open(path, encoding="utf-8") as f:
        return f.read()


def write(path, text):
    with io.open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


def to26(java):
    """The 26.x dialect of a 1.21.1 snippet: only the idioms this stream's text actually uses."""
    if DIALECT != "26":
        return java
    java = re.sub(r"\.getInt\(([^()]+)\)", r".getIntOr(\1, 0)", java)
    java = re.sub(r"\.getString\(([^()]+)\)", r'.getStringOr(\1, "")', java)
    java = re.sub(r"\.getCompound\(([^()]+)\)", r".getCompoundOrEmpty(\1)", java)
    java = java.replace("sp.serverLevel()", "sp.level()")
    java = re.sub(r"new ChunkPos\((\S+?)\)\.toLong\(\)", r"ChunkPos.pack(\1)", java)
    return java


def sub1(rel, old, new):
    """Exactly one anchor, replaced once. A tree already carrying the insert (its §h marker, or the
    literal replacement) is left alone — that is what makes a rerun a no-op."""
    path = os.path.join(SRC, rel)
    text = read(path)
    old, new = to26(old), to26(new)
    if new in text:
        return
    if text.count(old) != 1:
        sys.exit("p_h: anchor not found once in %s (%d hits):\n%s" % (rel, text.count(old), old))
    write(path, text.replace(old, new))


# ---------------------------------------------------------------- StockedData: Dom/Tot and the average
SD = "fishing/StockedData.java"

sub1(SD,
     '''        t.putInt(side, t.getInt(side) + 1);
        stamp(t, day, genome, owner);''',
     '''        t.putInt(side, t.getInt(side) + 1);
        tally(t, genome);   // §h
        stamp(t, day, genome, owner);''')

sub1(SD,
     '''        t.putInt("Fry", t.getInt("Fry") + Math.max(0, count));
        stamp(t, day, genome, owner);''',
     '''        t.putInt("Fry", t.getInt("Fry") + Math.max(0, count));
        tally(t, genome);   // §h: a fry stack is one spawn's worth — two alleles, like one fish
        stamp(t, day, genome, owner);''')

sub1(SD,
     '''    /** The population's genome — the last brood or fry released; "" when nobody stocked it. */
    public String genome(long region, String species) {
        CompoundTag t = brood.get(key(region, species));
        return t == null ? "" : t.getString("Genome");
    }
''',
     '''    // ---- §h §breeding (0.9.0): the population's genome is an AVERAGE, not the last writer -----------
    // Dom0..Dom3 count the strong alleles per locus (S C V F) over every brood fish and fry stack ever
    // recorded, Tot the alleles counted (two each). They live in the entry compound so save/load carry
    // them for free, and tickSettle leaves them: a settled water remembers what it was stocked with.
    private static void tally(CompoundTag t, String genome) {
        if (genome == null || genome.isEmpty()) return;
        for (int i = 0; i < com.riverfishing.fish.Genome.LOCI.length(); i++) {
            char l = com.riverfishing.fish.Genome.LOCI.charAt(i);
            int dom = !com.riverfishing.fish.Genome.dominant(genome, l) ? 0
                    : com.riverfishing.fish.Genome.pure(genome, l) ? 2 : 1;
            t.putInt("Dom" + i, t.getInt("Dom" + i) + dom);
        }
        t.putInt("Tot", t.getInt("Tot") + 2);
    }

    /** Share of strong alleles per locus (S C V F), 0..1; all 0 where nothing was ever recorded. */
    public double[] shares(long region, String species) {
        CompoundTag t = brood.get(key(region, species));
        double[] out = new double[4];
        int tot = t == null ? 0 : t.getInt("Tot");
        if (tot > 0) for (int i = 0; i < 4; i++) out[i] = t.getInt("Dom" + i) / (double) tot;
        return out;
    }

    /**
     * The population's genome, averaged: a locus is SS at a strong-allele share of 2/3 or more, Ss at
     * 1/3, ss below. "" when nobody stocked it; a ledger from before the tally (Tot 0) still answers
     * with the last genome it wrote, so old worlds keep their string.
     */
    public String genome(long region, String species) {
        CompoundTag t = brood.get(key(region, species));
        if (t == null) return "";
        if (t.getInt("Tot") <= 0) return t.getString("Genome");
        double[] s = shares(region, species);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            char u = com.riverfishing.fish.Genome.LOCI.charAt(i), l = Character.toLowerCase(u);
            if (i > 0) out.append(' ');
            out.append(s[i] >= 0.66 ? u : l).append(s[i] >= 0.33 ? u : l);
        }
        return out.toString();
    }

    /** Every species with a genome in a region, species → string: what the journal shows for "here". */
    public CompoundTag genomes(long region) {
        CompoundTag out = new CompoundTag();
        String prefix = region + "|";
        for (String k : brood.keySet()) {
            if (!k.startsWith(prefix)) continue;
            String g = genome(region, k.substring(prefix.length()));
            if (!g.isEmpty()) out.putString(k.substring(prefix.length()), g);
        }
        return out;
    }
''')

# ---------------------------------------------------------------- FishingManager: the effects
FM = "fishing/FishingManager.java"

# the helper, parked in front of the fish-generation section it serves
sub1(FM,
     '''    // ---- fish generation ----
''',
     '''    /**
     * §h §breeding: the strong-allele share (0..1) at one locus of the population stocked here — 0 for a
     * species that is not SETTLED in the region, because until then the brood is a handful of fish, not a
     * population. Locus by Genome.LOCI index: 0 size, 1 colour, 2 vigour, 3 fertility.
     */
    private static double hShare(ServerLevel level, BlockPos pos, String species, int locus) {
        StockedData stocked = StockedData.get(level);
        long region = StockedData.region(pos);
        return stocked.isStocked(region, species) ? stocked.shares(region, species)[locus] : 0.0;
    }

    // ---- fish generation ----
''')

# weight: rollFish learns the level (its two callers have one), then S share and the ecosystem scale
# the weight BEFORE it is rounded, so the allometric length below follows it.
sub1(FM,
     '''    private static void rollFish(RandomSource random, FishProfile p, FishingSession session, double luck,
                                 int livebaitWeightG, double match) {''',
     '''    private static void rollFish(ServerLevel level, RandomSource random, FishProfile p, FishingSession session, double luck,
                                 int livebaitWeightG, double match) {   // §h: level for the population's size genes''')
sub1(FM,
     '''                    rollFish(random, fresh, session, session.rollLuck, session.rollLivebaitG,''',
     '''                    rollFish(level, random, fresh, session, session.rollLuck, session.rollLivebaitG,   // §h''')
sub1(FM,
     '''        rollFish(random, profile, session, session.rollLuck, livebaitW, match);''',
     '''        rollFish(level, random, profile, session, session.rollLuck, livebaitW, match);   // §h''')
sub1(FM,
     '''        double weight = p.weightMin + (p.weightMax - p.weightMin) * biased;
        session.weightG = (int) Math.round(weight);''',
     '''        double weight = p.weightMin + (p.weightMax - p.weightMin) * biased;
        // §h §breeding: a settled population's size genes — all-ss stock runs 10% light, all-SS 15% heavy
        // (0.9 + 0.25 × share) — then the ecosystem's word (a predator thinning the small cyprinids fattens
        // the rest, §F). Applied before the rounding so the length keeps tracking the weight.
        weight *= (0.9 + 0.25 * hShare(level, session.target, p.id.getPath(), 0))
                * com.riverfishing.fishing.Ecosystem.weightScale(level, session.target, p.id);
        session.weightG = (int) Math.round(weight);''')

# colour: FishMorph.roll rolls rng < chance per morph; a second roll taken with probability shareC gives
# chance × (1 + shareC × (1 − chance)) — for the rare chances morphs have, that is × (1 + shareC), without
# touching FishMorph's signature (nets and streams D/E may call it).
sub1(FM,
     '''        var morph = com.riverfishing.fish.FishMorph.roll(path, age, settled, surplus, level.getRandom());
        if (morph == null) return;''',
     '''        var morph = com.riverfishing.fish.FishMorph.roll(path, age, settled, surplus, level.getRandom());
        // §h §breeding: strong colour genes in the population double the morph chance at a full share — a
        // second roll, taken with probability shareC, is chance × (1 + shareC) to within the chance itself.
        if (morph == null && level.getRandom().nextDouble() < hShare(level, where, path, 1)) {
            morph = com.riverfishing.fish.FishMorph.roll(path, age, settled, surplus, level.getRandom());
        }
        if (morph == null) return;''')

# vigour: the regen clock. popRegen is one number shared by every species at both call sites (reEvaluate
# on session.target, environmentAt on waterPos), so the multiplier goes on the per-species call.
sub1(FM,
     '''        long popChunk = new ChunkPos(session.target).toLong();
        double popRegen = spawnRegen(level);
        ctx.speciesFactor = id -> popData.speciesAttractiveness(popChunk, id.getPath(), now, popRegen);''',
     '''        long popChunk = new ChunkPos(session.target).toLong();
        double popRegen = spawnRegen(level);
        ctx.speciesFactor = id -> popData.speciesAttractiveness(popChunk, id.getPath(), now,
                popRegen * (1.0 + 0.5 * hShare(level, session.target, id.getPath(), 2)));   // §h: vigorous stock recovers faster''')
sub1(FM,
     '''        long popChunk = new ChunkPos(waterPos).toLong();
        double popRegen = spawnRegen(level);
        ctx.speciesFactor = id -> popData.speciesAttractiveness(popChunk, id.getPath(), now, popRegen);''',
     '''        long popChunk = new ChunkPos(waterPos).toLong();
        double popRegen = spawnRegen(level);
        ctx.speciesFactor = id -> popData.speciesAttractiveness(popChunk, id.getPath(), now,
                popRegen * (1.0 + 0.5 * hShare(level, waterPos, id.getPath(), 2)));   // §h: vigorous stock recovers faster''')

# wild fry: the water decides how many live. The checklist message prints the ledger's fry count, so it
# shows the survivors without a change of its own.
sub1(FM,
     '''        release(level, pos, p, count * 0.02, thrower, (stocked, region) ->
                stocked.addFry(region, species.getPath(), count, StockedData.worldDay(level), genome,''',
     '''        // §h §breeding: fry thrown into open water are eaten — 70% make it in bare water, up to 100% with
        // snags to hide in (§F's frySurvival). Stock units and the ledger both count the survivors.
        int alive = Math.max(1, (int) Math.round(count * (0.7 + com.riverfishing.fishing.Ecosystem.frySurvival(level, pos))));
        release(level, pos, p, alive * 0.02, thrower, (stocked, region) ->
                stocked.addFry(region, species.getPath(), alive, StockedData.worldDay(level), genome,''')

# ---------------------------------------------------------------- Journal: "population here"
sub1("network/JournalOpenPacket.java",
     '''        copy.put("cards", com.riverfishing.fish.FishCard.buildAll());''',
     '''        copy.put("cards", com.riverfishing.fish.FishCard.buildAll());
        // §h §breeding: the genomes stocked in the region the player STANDS in — the client has no ledger.
        copy.put("pop", com.riverfishing.fishing.StockedData.get(sp.serverLevel())
                .genomes(com.riverfishing.fishing.StockedData.region(sp.blockPosition())));''')

sub1("client/JournalScreen.java",
     '''            y = line(g, y, "guide.riverfishing.water", waters(c));''',
     '''            String pop = data.getCompound("pop").getString(sp);   // §h: the population where the player stands
            if (!pop.isEmpty()) y = line(g, y, "journal.riverfishing.pop_here", pop);
            y = line(g, y, "guide.riverfishing.water", waters(c));''')

print("p_h: ok (%s)" % DIALECT)
