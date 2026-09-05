# -*- coding: utf-8 -*-
"""§provinces §biomes-require: which part of the world this water is in, and lists that must ALL match.

    py -X utf8 tools/patches/p_prov.py <root> [1211|1201|26]

Vanilla biomes repeat everywhere: a swamp here and a swamp ten thousand blocks away are the same
swamp, so a mod that gates fish on biomes alone cannot have geography — only weather. Two things fix
that, and this patch is both.

§provinces — the world is cut into faunal provinces (palearctic, nearctic, neotropic, indomalaya) by a
jittered Voronoi grid off the world seed. A species that lists provinces lives in those and nowhere
else, so a peacock bass and a taimen stop sharing a river because the temperature happened to match.
Two ways out, and both are the mod's own economy: travel, or bring it home — a private pond is exempt,
and §stocked-survival already lets a settled species live outside its range at a quarter rate.

§biomes-require — the existing `biomes` map is best-of (any one group scores). The new
`biomes_require` list is all-of: every group in it must be present or the fish is absent. That is what
lets a taimen ask for cold AND a river AND mountains without the old map having to mean something
different than it always did.
"""
import io, os, sys

ROOT = sys.argv[1]
D = sys.argv[2] if len(sys.argv) > 2 else "1211"
J = os.path.join(ROOT, "common/src/main/java/com/riverfishing/")
GET = (lambda k: 'getStringOr("%s", "")' % k) if D == "26" else (lambda k: 'getString("%s")' % k)


def rd(p): return io.open(p, encoding="utf-8").read()
def wr(p, s): io.open(p, "w", encoding="utf-8", newline="\n").write(s)


# ---- 1. the map itself ------------------------------------------------------------------------
PROVINCES = '''package com.riverfishing.water;

/**
 * §provinces: which part of the world a piece of water is in.
 *
 * <p>Minecraft has no geography, only climate — the same swamp, taiga and jungle repeat to the world
 * border, so a fish gated on biomes alone lives everywhere its weather occurs. Real fish do not: a
 * peacock bass and a taimen never share a river however similar the water, because half a planet is
 * between them. This is that half a planet.
 *
 * <p>The world is cut into cells of {@link #CELL} blocks, each cell's centre pushed somewhere random
 * inside it by the world seed, and a point belongs to the nearest such centre — a Voronoi diagram, so
 * the borders are organic rather than a grid, and neighbouring cells that draw the same province
 * simply merge into one bigger region. Pure arithmetic on the seed and the coordinates: no state, no
 * saved data, no noise library, and the same answer for the same block forever.
 *
 * <p>The sea is deliberately not divided. Ocean species carry no province list at all — one ocean,
 * one fauna — and the division bites exactly where it should, on fresh water.
 */
public final class Provinces {

    /** In table order; a profile names these, and lang keys are {@code province.riverfishing.<id>}. */
    public static final String[] ALL = {"palearctic", "nearctic", "neotropic", "indomalaya"};

    /**
     * Cell size in blocks. Three thousand is a journey and not an expedition: a player who walks a
     * few thousand blocks in one direction meets a different fauna, and one who settles never has all
     * of it at home — which is what the keepnet, the fishermen and the breeding tank are for.
     */
    public static final int CELL = 3072;

    private Provinces() {}

    /** The province of a block, for this world's seed. */
    public static String at(long seed, int x, int z) {
        return ALL[index(seed, x, z)];
    }

    /** 0..ALL.length-1, the same answer {@link #at} names. */
    public static int index(long seed, int x, int z) {
        int cx = Math.floorDiv(x, CELL), cz = Math.floorDiv(z, CELL);
        long best = Long.MAX_VALUE;
        int pick = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int gx = cx + dx, gz = cz + dz;
                long h = mix(seed ^ ((long) gx * 0x9E3779B97F4A7C15L) ^ ((long) gz * 0xC2B2AE3D27D4EB4FL));
                // the cell's own centre, pushed off the middle so no border is a straight line
                long px = (long) gx * CELL + Math.floorMod(h >>> 17, CELL);
                long pz = (long) gz * CELL + Math.floorMod(h >>> 41, CELL);
                long ddx = px - x, ddz = pz - z;
                long d = ddx * ddx + ddz * ddz;
                if (d < best) {
                    best = d;
                    pick = (int) Math.floorMod(h, ALL.length);
                }
            }
        }
        return pick;
    }

    /** splitmix64's finalizer: cheap, and it spreads a small cell index across all 64 bits. */
    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
'''
p = J + "water/Provinces.java"
if not os.path.exists(p):
    wr(p, PROVINCES)
    print("  water/Provinces.java")

# ---- 2. the profile: two new fields ------------------------------------------------------------
p = J + "fish/FishProfile.java"
s = rd(p)
if "provinces" not in s:
    old = """    // …and only in these biome groups (group -> factor; empty = anywhere; no match = 0).
    public final Map<String, Double> biomes;"""
    assert old in s, "the biomes field moved"
    s = s.replace(old, old + """

    /**
     * §provinces: the parts of the world this species lives in ({@link com.riverfishing.water.Provinces}).
     * EMPTY MEANS EVERYWHERE, which is what every sea fish and every profile written before this wants:
     * one ocean, one fauna. A non-empty list is a hard gate — the species is absent from every province
     * it does not name, however right the water looks.
     */
    public final java.util.Set<String> provinces;

    /**
     * §biomes-require: biome groups that must ALL be present, where {@link #biomes} above is best-of.
     * This is how a specialist is written: a taimen asks for cold AND a river AND mountains, and the
     * old map goes on meaning what it always meant.
     */
    public final java.util.Set<String> biomesRequire;""", 1)
    s = s.replace("        this.biomes = b.biomes;",
                  "        this.biomes = b.biomes;\n"
                  "        this.provinces = b.provinces;\n"
                  "        this.biomesRequire = b.biomesRequire;", 1)
    s = s.replace('        b.biomes = readDoubleMap(GsonHelper.getAsJsonObject(json, "biomes", new JsonObject()));',
                  '        b.biomes = readDoubleMap(GsonHelper.getAsJsonObject(json, "biomes", new JsonObject()));\n'
                  '        b.provinces = readStringSet(json, "provinces");          // §provinces\n'
                  '        b.biomesRequire = readStringSet(json, "biomes_require"); // §biomes-require', 1)
    s = s.replace("        Map<String, Double> biomes = new HashMap<>();",
                  "        Map<String, Double> biomes = new HashMap<>();\n"
                  "        java.util.Set<String> provinces = new java.util.HashSet<>();\n"
                  "        java.util.Set<String> biomesRequire = new java.util.HashSet<>();", 1)
    wr(p, s)
    print("  FishProfile: provinces + biomes_require")

# ---- 3. the context carries the province -------------------------------------------------------
p = J + "engine/BiteContext.java"
s = rd(p)
if "province" not in s:
    old = "    public java.util.Set<String> biomeGroups = new java.util.HashSet<>();"
    assert old in s, "biomeGroups moved"
    s = s.replace(old, old + '''
    /**
     * §provinces: the faunal province this water is in — palearctic, nearctic, neotropic, indomalaya.
     * Empty only where nothing set it (a test, a probe with no level), which reads as "no gate".
     */
    public String province = "";''', 1)
    wr(p, s)
    print("  BiteContext: province")

# ---- 4. the gates ------------------------------------------------------------------------------
p = J + "engine/BiteEngine.java"
s = rd(p)
if "§provinces" not in s:
    old = """        double fBiome = biomeGroupFactor(p, c);"""
    assert old in s, "the biome gate moved"
    s = s.replace(old, """        // §provinces: half a planet, the one gate a biome cannot express. A species that names
        // provinces is absent from every other one — no factor, no half rate, absent. A private pond is
        // exempt (its owner put the fish there), and §stocked-survival above already lets a settled
        // species live outside its range at a quarter of full activity: travel to find it, or bring it
        // home and breed it. Those are the two ways, and both of them are the point.
        if (!c.privatePond && !p.provinces.isEmpty()
                && !c.province.isEmpty() && !p.provinces.contains(c.province)) {
            return 0.0;
        }
        // §biomes-require: every group in the list, not the best of them. A specialist says what it
        // needs all at once — cold AND a river AND mountains — and a water missing any of it has none.
        if (!c.privatePond && !p.biomesRequire.isEmpty() && !c.biomeGroups.containsAll(p.biomesRequire)) {
            return 0.0;
        }

        double fBiome = biomeGroupFactor(p, c);""", 1)
    wr(p, s)
    print("  BiteEngine: the two gates")

# ---- 5. who sets it, and the finder payload ----------------------------------------------------
p = J + "fishing/FishingManager.java"
s = rd(p)
if "§provinces" not in s:
    n = 0
    for var, pos in (("env", "pos"), ("ctx", "waterPos")):
        old = "        %s.biomeGroups = biomeGroups(level, %s, body);" % (var, pos)
        if old not in s:
            continue
        s = s.replace(old, old + """
        %s.province = com.riverfishing.water.Provinces.at(level.getSeed(), %s.getX(), %s.getZ());  // §provinces""" % (var, pos, pos), 1)
        n += 1
    assert n == 2, "expected two biomeGroups assignments, patched %d" % n
    old = '            w.putString("groups", String.join(";", new java.util.TreeSet<>(env.biomeGroups)));'
    assert old in s, "the finder payload moved"
    s = s.replace(old, old + '''
            // §provinces: which part of the world this is. The sounder is where a player finds out why
            // the fish they are hunting is not in this river, so it is the sounder that has to say it.
            w.putString("prov", env.province);''', 1)
    wr(p, s)
    print("  FishingManager: the province is set and sent")

# ---- 6. the sounder shows it -------------------------------------------------------------------
p = J + "client/FinderScreen.java"
s = rd(p)
if "§provinces" not in s:
    old = '''        out.add(pairLine("finder.riverfishing.climate", groups.isEmpty() ? Component.literal("—") : climate));'''
    assert old in s, "the climate line moved"
    s = s.replace(old, old + '''
        // §provinces: the part of the world, above the season — it changes nothing about today and
        // everything about what lives here, which is exactly the order a player wants to read it in.
        String prov = w.%s;
        if (!prov.isEmpty()) {
            out.add(pairLine("finder.riverfishing.province",
                    Component.translatable("province.riverfishing." + prov)));
        }''' % GET("prov"), 1)
    wr(p, s)
    print("  FinderScreen: the province line")

# ---- 7. lang -----------------------------------------------------------------------------------
NAMES = {
    "en_us": [("province", "Province"), ("palearctic", "Palearctic"), ("nearctic", "Nearctic"),
              ("neotropic", "Neotropic"), ("indomalaya", "Indomalaya")],
    "ru_ru": [("province", "Регион"), ("palearctic", "Палеарктика"), ("nearctic", "Неарктика"),
              ("neotropic", "Неотропика"), ("indomalaya", "Индомалайя")],
    "uk_ua": [("province", "Регіон"), ("palearctic", "Палеарктика"), ("nearctic", "Неарктика"),
              ("neotropic", "Неотропіка"), ("indomalaya", "Індомалайя")],
}
for loc, rows in NAMES.items():
    p = os.path.join(ROOT, "common/src/main/resources/assets/riverfishing/lang", loc + ".json")
    s = rd(p)
    if '"province.riverfishing.palearctic"' in s:
        continue
    i = s.index('"finder.riverfishing.climate":')
    end = s.index("\n", i) + 1
    add = "".join('  "%s": "%s",\n' % ("finder.riverfishing.province" if k == "province"
                                       else "province.riverfishing." + k, v) for k, v in rows)
    wr(p, s[:end] + add + s[end:])
    print("  lang %s: +%d" % (loc, len(rows)))
print("done (%s)" % D)
