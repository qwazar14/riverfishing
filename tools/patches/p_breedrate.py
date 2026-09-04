# -*- coding: utf-8 -*-
"""§breed-rate §gynogenesis: a cross has a strength, and one fish clones instead of crossing.

    py -X utf8 tools/patches/p_breedrate.py <root> [1211|1201|26]

§breeds-with shipped as a flat list, which says "these two can hybridise" and nothing else. That covers
a carp and a sazan, which are one animal and breed without reservation, and a bream and a blue bream,
whose young are a rarity that survives badly — with the same word and the same result in the tank. It
is the one distinction the biology is actually about.

So the field is a MAP of id to rate now, and the rate scales the clutch: a pair that is really one
species fills the roe, a pair that barely works lays a handful. Legible without a tooltip — you watch
the egg count and you know what you have got — and it needs no new RNG, no new status and no new screen.

§gynogenesis is the other half. A silver crucian hen does not use the male's genes at all: his milt
starts her unfertilised egg dividing and what hatches is a copy of HER. It is why she displaces the
golden crucian wherever the two meet, and in the tank it means her clutch carries her genome exactly,
whoever the father was. One boolean in the profile, one branch at the cross.
"""
import io, os, sys

ROOT = sys.argv[1]
D = sys.argv[2] if len(sys.argv) > 2 else "1211"
RL = "Identifier" if D == "26" else "ResourceLocation"
J = os.path.join(ROOT, "common/src/main/java/com/riverfishing/")


def rd(p): return io.open(p, encoding="utf-8").read()


def wr(p, s): io.open(p, "w", encoding="utf-8", newline="\n").write(s)


def sub(s, old, new, what):
    assert old in s, "%s moved" % what
    return s.replace(old, new, 1)


# ---- 1. the profile: a map, and a boolean --------------------------------------------------------
p = J + "fish/FishProfile.java"
s = rd(p)
if "breedRates" not in s:
    s = sub(s, "    public final java.util.Set<String> breedsWith;",
            """    public final java.util.Map<String, Double> breedRates;

    /**
     * §gynogenesis: this hen does not need the male's genes — his milt only starts her unfertilised egg
     * dividing, and what hatches is a copy of her. True for the silver crucian, which is exactly how she
     * displaces the golden one wherever the two meet.
     */
    public final boolean gynogenesis;""", "the breedsWith field")
    s = sub(s, "        this.breedsWith = b.breedsWith;",
            "        this.breedRates = b.breedRates;\n        this.gynogenesis = b.gynogenesis;",
            "the breedsWith assignment")
    # The trailing marker comment is spaced differently per tree — match the whole line.
    import re as _re
    old = _re.search(r'        b\.breedsWith = readStringSet\(json, "breeds_with"\);[^\n]*\n', s).group(0)
    s = sub(s, old,
            '''        // §breed-rate: a map of id to how well the cross takes. Written as a bare ARRAY in the
        // first cut of §breeds-with and by any third-party profile that copied it — those read as
        // "everything at full strength", which is what they meant, rather than failing the load.
        b.breedRates = new java.util.HashMap<>();
        if (json.has("breeds_with") && json.get("breeds_with").isJsonArray()) {
            for (String o : readStringSet(json, "breeds_with")) b.breedRates.put(o, 1.0);
        } else {
            b.breedRates.putAll(readDoubleMap(GsonHelper.getAsJsonObject(json, "breeds_with", new JsonObject())));
        }
        b.gynogenesis = GsonHelper.getAsBoolean(json, "gynogenesis", false);   // §gynogenesis
''', "the breeds_with read")
    s = sub(s, "        java.util.Set<String> breedsWith = new java.util.HashSet<>();",
            "        Map<String, Double> breedRates = new HashMap<>();\n        boolean gynogenesis;",
            "the builder's breedsWith")
    wr(p, s)
    print("  FishProfile: breeds_with is a map, plus gynogenesis")

# ---- 2. the tank reads the rate ------------------------------------------------------------------
p = J + "block/AquariumBreeding.java"
s = rd(p)
if "breed-rate" not in s:
    s = sub(s, """    private static boolean mates(%s a, %s b) {
        if (a == null || b == null) return false;
        if (a.equals(b)) return true;
        FishProfile pa = profile(a), pb = profile(b);
        return (pa != null && pa.breedsWith.contains(b.getPath()))
                || (pb != null && pb.breedsWith.contains(a.getPath()));
    }""" % (RL, RL),
            """    private static boolean mates(%s a, %s b) {
        return rate(a, b) > 0.0;
    }

    /**
     * §breed-rate: how well this cross takes, 0 for not at all and 1 for a pair of the same fish. It
     * scales the clutch, so the difference between a carp with a sazan (one animal) and a bream with a
     * blue bream (a rarity that survives badly) is something you read off the egg count rather than out
     * of a wiki. The higher of the two directions wins — a table written only one way round is a
     * mistake in the data, and refusing the pair would hide it instead of reporting it.
     */
    private static double rate(%s a, %s b) {
        if (a == null || b == null) return 0.0;
        if (a.equals(b)) return 1.0;
        FishProfile pa = profile(a), pb = profile(b);
        double ra = pa == null ? 0.0 : pa.breedRates.getOrDefault(b.getPath(), 0.0);
        double rb = pb == null ? 0.0 : pb.breedRates.getOrDefault(a.getPath(), 0.0);
        return Math.max(ra, rb);
    }""" % (RL, RL, RL, RL), "the mates test")

    s = sub(s, """        String gm = Genome.of(mother), gf = Genome.of(pair[1]);
        String genome = Genome.cross(gm, gf, RNG);""",
            """        String gm = Genome.of(mother), gf = Genome.of(pair[1]);
        // §gynogenesis: the silver crucian's trick. The male's milt only starts the egg dividing — his
        // genes are not in it — so the clutch is the MOTHER, copied, whoever the father was. Nothing to
        // cross and nothing that can come out lethal that was not already lethal in her.
        String genome = p.gynogenesis ? gm : Genome.cross(gm, gf, RNG);""", "the cross")
    s = sub(s, "        for (int i = 0; i < 8 && Genome.lethal(genome); i++) genome = Genome.cross(gm, gf, RNG);",
            "        for (int i = 0; !p.gynogenesis && i < 8 && Genome.lethal(genome); i++) {\n"
            "            genome = Genome.cross(gm, gf, RNG);\n"
            "        }", "the lethal re-cross")
    wr(p, s)
    print("  AquariumBreeding: the rate, and the crucian's clone")

# ---- 3. …and the clutch pays for it --------------------------------------------------------------
s = rd(p)
if "breed-rate scales" not in s:
    import re
    m = re.search(r"    private static int clutch\(([^)]*)\) \{\n", s)
    assert m, "clutch() moved"
    end = s.index("\n    }", m.end())
    body = s[m.end():end]
    assert "return " in body, "clutch() has no return"
    at = body.rindex("return ")
    s = (s[:m.end()] + body[:at]
         + "// §breed-rate scales what is left: a cross that barely works lays a handful, never nothing.\n"
           "        double cross = rate(FishItem.getSpecies(pair[0]), FishItem.getSpecies(pair[1]));\n"
           "        return Math.max(1, (int) Math.round(cross * ("
         + body[at + len("return "):].rstrip().rstrip(";") + ")));"
         + s[end:])
    wr(p, s)
    print("  AquariumBreeding: the clutch is scaled by the cross")
print("done (%s)" % D)
