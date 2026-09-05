# -*- coding: utf-8 -*-
"""§breeds-with: two ids that are one fish can spawn together.

    py -X utf8 tools/patches/p_breeds.py <root> [1211|1201|26]

The tank paired a ♀ and a ♂ of the SAME id, which put a wall through the middle of one species. A carp,
a mirror carp and a naked carp all land as `carp` since §scale-genes, so those cross freely — but the
sazan keeps its own id (its own price, its own fight, it is the wild form) and could therefore not spawn
with the domestic carp it literally is. Cyprinus carpio on both sides of a wall.

So a profile can now name the ids it will spawn with. The test is tolerant in both directions — either
side naming the other is enough — because a one-sided table is a data mistake and not a reason to refuse
a pair; tools/check_breeds.py is what keeps the table honest.

The fry are the MOTHER's species, which is already what the tank does, and their genome is crossed from
both parents. That is the whole feature in one sentence: a sazan hen and a carp cock give sazan fry
carrying the domestic scale alleles, and the other way round gives carp fry with wild blood in them.
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


# ---- 1. the profile field ------------------------------------------------------------------------
p = J + "fish/FishProfile.java"
s = rd(p)
if "breedsWith" not in s:
    s = sub(s, """    public final java.util.Set<String> biomesRequire;""",
            """    public final java.util.Set<String> biomesRequire;

    /**
     * §breeds-with: the other species ids this one will spawn with, by path. EMPTY MEANS ITSELF ONLY,
     * which is what almost every fish wants. It is for the cases where two ids are one animal — the
     * carp and the sazan are both Cyprinus carpio, the koi is a carp in a kimono — and for the few
     * crosses that make fertile young in the water they actually share.
     */
    public final java.util.Set<String> breedsWith;""", "the biomesRequire field")
    s = sub(s, "        this.biomesRequire = b.biomesRequire;",
            "        this.biomesRequire = b.biomesRequire;\n        this.breedsWith = b.breedsWith;",
            "the biomesRequire assignment")
    # The trailing marker comment is not in every tree — anchor on the statement itself.
    old = '        b.biomesRequire = readStringSet(json, "biomes_require");'
    s = sub(s, old, old + '\n        b.breedsWith = readStringSet(json, "breeds_with");   // §breeds-with',
            "the biomes_require read")
    s = sub(s, "        java.util.Set<String> biomesRequire = new java.util.HashSet<>();",
            "        java.util.Set<String> biomesRequire = new java.util.HashSet<>();\n"
            "        java.util.Set<String> breedsWith = new java.util.HashSet<>();",
            "the builder's biomesRequire")
    wr(p, s)
    print("  FishProfile: breeds_with")

# ---- 2. the tank pairs on it ---------------------------------------------------------------------
p = J + "block/AquariumBreeding.java"
s = rd(p)
if "breeds-with" not in s:
    s = sub(s, "                        && sp.equals(FishItem.getSpecies(m))) {",
            "                        && mates(sp, FishItem.getSpecies(m))) {", "the pair test")
    s = sub(s, """    /** Both at least an adult (Card.Size 2): babies and juveniles keep growing, they do not spawn. */""",
            """    /**
     * §breeds-with: whether these two will spawn together. The same id always will; beyond that a
     * profile names the ids it accepts, and EITHER side naming the other is enough — a table written
     * only one way round is a mistake in the data, and refusing the pair would hide it rather than
     * report it. tools/check_breeds.py is what reports it.
     *
     * <p>The fry are the mother's species either way (the roe is built from {@code pair[0]}), so a
     * cross is a way of moving BLOOD between two ids, never a way of making a third one.
     */
    private static boolean mates(%s a, %s b) {
        if (a == null || b == null) return false;
        if (a.equals(b)) return true;
        FishProfile pa = profile(a), pb = profile(b);
        return (pa != null && pa.breedsWith.contains(b.getPath()))
                || (pb != null && pb.breedsWith.contains(a.getPath()));
    }

    /** Both at least an adult (Card.Size 2): babies and juveniles keep growing, they do not spawn. */""" % (RL, RL),
            "the maturity check")
    wr(p, s)
    print("  AquariumBreeding: a pair is two fish that breed, not two of one id")
print("done (%s)" % D)
