# -*- coding: utf-8 -*-
"""§species-table (0.10) — the Java half of the spreadsheet import, idempotent, one tree per run:
  py tools/patches/p_species_table.py <tree>
- six new families (panfish, catfish, cichlid, characin, exotic, ray) in FishGroup;
- a profile's DIET (predator / omnivore / peaceful / insectivore) and its hybrid parents;
- the bite engine stops reading rod, rig, reel, distance and hook tolerance; the angler level only
  thins the bite (never below 15 %) on the new 0-50 ladder;
- flies are rated by diet; the fifth faunal province, Afrotropical, carved out of the warm cells so
  existing worlds keep their other four; the chart's fifth colour; the order board drops rig/rod;
- a cross whose hybrid the table names spawns THAT species; the journal card shows the diet."""
import io, os, re, sys
NL = chr(10)

R = sys.argv[1]
J = R + "/common/src/main/java/com/riverfishing"
MARK = "§species-table"


def rd(p): return io.open(p, encoding="utf-8").read()
def wr(p, s): io.open(p, "w", encoding="utf-8", newline="\n").write(s)
def sub1(s, a, b, what):
    n = s.count(a)
    if n != 1: print("p_species_table: anchor for %s occurs %d times:\n%s" % (what, n, a)); sys.exit(1)
    return s.replace(a, b, 1)
def patch(rel, marker, edits):
    p = os.path.join(J, rel)
    if not os.path.exists(p): print("  (no %s here)" % rel); return
    s = rd(p)
    if marker in s: print("  %s: already patched" % rel); return
    for a, b, what in edits: s = sub1(s, a, b, what)
    wr(p, s); print("  %s: patched" % rel)


# ---- 1. the families ----
patch("fish/FishGroup.java", MARK, [
    ("    public static final String BIG_GAME = \"big_game\";\n",
     "    public static final String BIG_GAME = \"big_game\";\n"
     "    // " + MARK + ": the six families the author's table filed its species under\n"
     "    public static final String PANFISH = \"panfish\";\n    public static final String CATFISH = \"catfish\";\n"
     "    public static final String CICHLID = \"cichlid\";\n    public static final String CHARACIN = \"characin\";\n"
     "    public static final String EXOTIC = \"exotic\";\n    public static final String RAY = \"ray\";\n", "FishGroup constants"),
    ("            List.of(CYPRINID, PREDATOR, SALMONID, STURGEON, KOI, SEA, BIG_GAME, OTHER);\n",
     "            List.of(CYPRINID, PANFISH, PREDATOR, CATFISH, SALMONID, CICHLID, CHARACIN, STURGEON, RAY, KOI, SEA, BIG_GAME, EXOTIC, OTHER);\n", "FishGroup order"),
])

# ---- 2. the profile: diet, hybrid parents, the hook's tolerance, the new families' habits ----
patch("fish/FishProfile.java", MARK, [
    ("    public final String group;\n",
     "    public final String group;\n"
     "    /** " + MARK + ": what it eats — predator / omnivore / peaceful / insectivore; the family's habit when unsaid. */\n"
     "    public final String diet;\n"
     "    /** " + MARK + ": the two species whose cross spawns this one, or empty. */\n"
     "    public final java.util.List<String> hybridOf;\n", "profile fields"),
    ("        this.group = b.group;\n", "        this.group = b.group;\n        this.diet = b.diet;\n        this.hybridOf = b.hybridOf;\n", "profile ctor"),
    ("        b.group = GsonHelper.getAsString(json, \"group\", FishGroup.OTHER);\n",
     "        b.group = GsonHelper.getAsString(json, \"group\", FishGroup.OTHER);\n"
     "        b.diet = GsonHelper.getAsString(json, \"diet\", defaultDiet(b.group));   // " + MARK + "\n"
     "        b.hybridOf = new java.util.ArrayList<>(readStringSet(json, \"hybrid_of\"));\n", "profile parse"),
    ("        b.hookTolerance = Math.max(1, GsonHelper.getAsInt(hook, \"tolerance\", 2));\n",
     "        // " + MARK + ": the tolerance left the table; one band either side of the ideal is the rule for all\n"
     "        b.hookTolerance = Math.max(1, GsonHelper.getAsInt(hook, \"tolerance\", 3));\n", "hook tolerance"),
    ("    public static Season defaultSpawnSeason(String group) {\n        return switch (group == null ? \"\" : group) {\n",
     "    /** " + MARK + ": the family's diet when the profile does not say. */\n"
     "    public static String defaultDiet(String group) {\n"
     "        return switch (group == null ? \"\" : group) {\n"
     "            case FishGroup.PREDATOR, FishGroup.BIG_GAME, FishGroup.SEA, FishGroup.CATFISH, FishGroup.CHARACIN, FishGroup.RAY -> \"predator\";\n"
     "            case FishGroup.SALMONID, FishGroup.PANFISH -> \"insectivore\";\n"
     "            case FishGroup.CYPRINID, FishGroup.KOI, FishGroup.STURGEON -> \"peaceful\";\n"
     "            default -> \"omnivore\";\n"
     "        };\n    }\n\n"
     "    public static Season defaultSpawnSeason(String group) {\n        return switch (group == null ? \"\" : group) {\n"
     "            case FishGroup.CATFISH, FishGroup.CICHLID, FishGroup.CHARACIN, FishGroup.EXOTIC, FishGroup.RAY -> Season.SUMMER;   // " + MARK + "\n", "spawn defaults"),
    ("            case \"sea\", \"big_game\":                // sand and rock both work; mud is a harbour\n"
     "                return switch (key) { case \"sand\", \"rock\" -> 1.1; case \"mud\" -> 0.9; default -> 1.0; };\n",
     "            case \"sea\", \"big_game\":                // sand and rock both work; mud is a harbour\n"
     "                return switch (key) { case \"sand\", \"rock\" -> 1.1; case \"mud\" -> 0.9; default -> 1.0; };\n"
     "            // " + MARK + ": the table's families\n"
     "            case \"catfish\", \"ray\":                // grub the soft bottom\n"
     "                return switch (key) { case \"mud\", \"sand\" -> 1.15; case \"rock\" -> 0.85; default -> 1.0; };\n"
     "            case \"panfish\", \"cichlid\":            // sand and gravel beds, where they nest\n"
     "                return switch (key) { case \"sand\", \"gravel\" -> 1.1; case \"mud\" -> 0.95; default -> 1.0; };\n", "bed habits"),
])

# ---- 3. the engine ----
patch("engine/BiteEngine.java", MARK, [
    ("        double sRig = c.rig != null && p.idealRigs.contains(c.rig.jsonKey()) ? 1.0 : 0.15;\n"
     "        double sRod = p.idealRods.contains(c.rod.jsonKey()) ? 1.0 : 0.35;\n"
     "        double sLine = lineScore(p, c);\n"
     "        double sHook = hookScore(p, c);\n"
     "        double sReel = reelScore(p, c);\n"
     "\n"
     "        return 0.30 * sBait\n"
     "                + 0.15 * sGround\n"
     "                + 0.13 * sRig\n"
     "                + 0.12 * sRod\n"
     "                + 0.12 * sLine\n"
     "                + 0.10 * sHook\n"
     "                + 0.08 * sReel;\n",
     "        // " + MARK + ": the rod, the rig and the reel left the match — a species asks for bait, feed,\n"
     "        // line and hook, and how you deliver them is your business\n"
     "        double sLine = lineScore(p, c);\n"
     "        double sHook = hookScore(p, c);\n"
     "\n"
     "        return 0.45 * sBait\n"
     "                + 0.20 * sGround\n"
     "                + 0.20 * sLine\n"
     "                + 0.15 * sHook;\n", "matchScore"),
    ("        double d = c.castDistance;\n"
     "        if (d < p.distMin) {\n"
     "            double t = p.distMin <= 0 ? 1.0 : d / p.distMin;\n"
     "            return 0.6 + 0.4 * Math.max(0.0, Math.min(1.0, t));\n"
     "        }\n"
     "        if (d > p.distMax) {\n"
     "            return 0.85;\n"
     "        }\n"
     "        return 1.1;\n",
     "        // " + MARK + ": the species' own distance band is gone — where the fish holds is the water's\n"
     "        // business (depth, width, bed), not a number per profile\n"
     "        return 1.0;\n", "distanceFactor"),
    ("        if (p.minAnglerLevel > 0 && c.anglerLevel < p.minAnglerLevel) {\n"
     "            int deficit = p.minAnglerLevel - c.anglerLevel;\n"
     "            w *= Math.max(0.03, Math.pow(0.6, deficit));\n"
     "        }\n",
     "        // " + MARK + ": the ladder runs 0-50 now and the level never forbids — short of the rung the bite\n"
     "        // thins in proportion, to a floor of 15 %, so a novice CAN fluke the fish and a veteran fishes it steadily\n"
     "        if (p.minAnglerLevel > 0 && c.anglerLevel < p.minAnglerLevel) {\n"
     "            double deficit = (p.minAnglerLevel - c.anglerLevel) / (double) p.minAnglerLevel;\n"
     "            w *= Math.max(0.15, 1.0 - 0.85 * deficit);\n"
     "        }\n", "level"),
    ("            else best *= c.tied.affinity(p.group);\n", "            else best *= c.tied.affinity(p.diet, p.group);   // " + MARK + ": by what it eats\n", "fly affinity"),
])

# ---- 4. flies by diet ----
patch("tackle/TiedDesign.java", MARK, [
    ("        double family(String group) {\n            if (group == null) return (cyprinid + predator + salmonid) / 3.0;\n",
     "        /** " + MARK + ": the diet says it first — a predator takes the streamer whatever family it is filed under. */\n"
     "        double family(String diet, String group) {\n"
     "            if (\"sea\".equals(group)) return sea;\n"
     "            if (diet != null) {\n"
     "                switch (diet) {\n"
     "                    case \"predator\": return predator;\n"
     "                    case \"insectivore\": return salmonid;\n"
     "                    case \"peaceful\": return cyprinid;\n"
     "                    case \"omnivore\": return (cyprinid + predator) / 2.0;\n"
     "                    default: break;\n"
     "                }\n"
     "            }\n"
     "            return family(group);\n"
     "        }\n\n"
     "        double family(String group) {\n            if (group == null) return (cyprinid + predator + salmonid) / 3.0;\n", "template family"),
])
# the Analysis record's affinity(group) gets a diet-aware twin — find its body and add the overload beside it
p = os.path.join(J, "tackle/TiedDesign.java"); s = rd(p)
if "affinity(String diet, String group)" not in s:
    m = re.search(r"( +)public double affinity\(String group\) \{\n( +)(.*?)\n( +)\}\n", s, re.S)
    assert m, "Analysis.affinity"
    ind = m.group(1)
    body = m.group(3)
    twin = (ind + "/** " + MARK + ": by diet first, family second. */\n"
            + ind + "public double affinity(String diet, String group) {\n"
            + m.group(2) + body.replace("family(group)", "family(diet, group)") + "\n" + ind + "}\n")
    assert "family(diet, group)" in twin, body
    s = s[:m.end()] + "\n" + twin + s[m.end():]
    wr(p, s); print("  tackle/TiedDesign.java: affinity twin")

# ---- 5. the catch's nature reads the diet ----
patch("fish/CatchCard.java", MARK, [
    ("        boolean hunter = p != null && (p.group.equals(\"predator\") || p.group.equals(\"big_game\") || p.group.equals(\"sea\"));\n",
     "        boolean hunter = p != null && (\"predator\".equals(p.diet)   // " + MARK + "\n"
     "                || p.group.equals(\"predator\") || p.group.equals(\"big_game\") || p.group.equals(\"sea\"));\n", "nature"),
])

# ---- 6. the fifth province, carved out of the warm cells ----
patch("water/Provinces.java", MARK, [
    ("    public static final String[] ALL = {\"palearctic\", \"nearctic\", \"neotropic\", \"indomalaya\"};\n",
     "    public static final String[] ALL = {\"palearctic\", \"nearctic\", \"neotropic\", \"indomalaya\", \"afrotropical\"};\n"
     "    /** " + MARK + ": the four the map was first cut into — the fifth is carved out of two of them, below. */\n"
     "    private static final int FIRST_FOUR = 4;\n", "ALL"),
    ("                    pick = (int) Math.floorMod(h, ALL.length);\n",
     "                    pick = (int) Math.floorMod(h, FIRST_FOUR);\n"
     "                    // " + MARK + ": Afrotropical is a third of what used to be Neotropic and Indomalaya, off\n"
     "                    // another slice of the same hash — so a world's Palearctic and Nearctic cells are exactly\n"
     "                    // where they were, and two warm cells in three are too\n"
     "                    if (pick >= 2 && Math.floorMod(h >>> 9, 3) == 0) pick = 4;\n", "carve"),
])
patch("client/FinderScreen.java", MARK, [
    ("    private static final int[] PROV = {0xFF101C2C, 0xFF101F14, 0xFF241609, 0xFF1E132A};\n",
     "    private static final int[] PROV = {0xFF101C2C, 0xFF101F14, 0xFF241609, 0xFF1E132A, 0xFF2A1A0A};   // " + MARK + ": the fifth, Afrotropical\n", "chart colours"),
])

# ---- 7. the order board no longer asks for a rig or a rod ----
patch("fishing/OrderBoard.java", MARK, [
    ("        // 6. Rig, and 7. rod: the tackle the species expects.\n"
     "        boolean rigOk = rigStack.getItem() instanceof RigItem ri && p.idealRigs.contains(ri.rigType().jsonKey());\n"
     "        rows.add(row(\"order.riverfishing.rig\", keys(\"item.riverfishing.rig_\", p.idealRigs), rigOk));\n"
     "        boolean rodOk = rod.getItem() instanceof RodItem ri && p.idealRods.contains(ri.rodType().jsonKey());\n"
     "        rows.add(rowSuffix(\"order.riverfishing.rod\", \"item.riverfishing.\", p.idealRods, \"_rod\", rodOk));\n",
     "        // " + MARK + ": rig and rod left the species' asks — how you deliver the bait is your business\n", "order rows"),
])

# ---- 8. a cross the table names as a hybrid spawns the hybrid ----
_aq = os.path.join(J, "block/AquariumBreeding.java")
RL = "Identifier" if os.path.exists(_aq) and "Identifier species" in rd(_aq) else "ResourceLocation"
patch("block/AquariumBreeding.java", MARK, [
    ("        be.roe = RoeItem.of(FishItem.getSpecies(mother), genome, clutch(be, pair, p), now / DAY);" + NL,
     "        be.roe = RoeItem.of(hybridOr(FishItem.getSpecies(mother), FishItem.getSpecies(pair[1])), genome, clutch(be, pair, p), now / DAY);   // " + MARK + NL, "roe species"),
    ("    private static FishProfile profile(" + RL + " species) {" + NL,
     "    /** " + MARK + ": the species a cross of these two is filed as — the hybrid the table names, else the mother. */" + NL
     + "    private static " + RL + " hybridOr(" + RL + " mother, " + RL + " father) {" + NL
     + "        if (mother == null || father == null || mother.equals(father)) return mother;" + NL
     + "        for (FishProfile h : FishProfileManager.get().all()) {" + NL
     + "            if (h.hybridOf.size() == 2 && h.hybridOf.contains(mother.getPath()) && h.hybridOf.contains(father.getPath())) return h.id;" + NL
     + "        }" + NL + "        return mother;" + NL + "    }" + NL + NL
     + "    private static FishProfile profile(" + RL + " species) {" + NL, "hybridOr"),
])

# ---- 9. the journal card carries the diet ----
patch("fish/FishCard.java", MARK, [
    ("        c.putInt(\"lvl\", p.minAnglerLevel);\n", "        c.putInt(\"lvl\", p.minAnglerLevel);\n        c.putString(\"diet\", p.diet);   // " + MARK + "\n", "card write"),
    ("    public int reelSize() { return tag.getInt(\"reel\"); }\n",
     "    public int reelSize() { return tag.getInt(\"reel\"); }\n    public String diet() { return tag.getString(\"diet\"); }   // " + MARK + "\n", "card read"),
])
patch("client/JournalScreen.java", MARK, [
    ("        y = railLine(g, \"journal.riverfishing.stat_runs\", Integer.toString(c.fightRuns()), x, y, w);\n",
     "        if (!c.diet().isEmpty()) {   // " + MARK + "\n"
     "            y = railLine(g, \"journal.riverfishing.diet\", Component.translatable(\"diet.riverfishing.\" + c.diet()).getString(), x, y, w);\n"
     "        }\n"
     "        y = railLine(g, \"journal.riverfishing.stat_runs\", Integer.toString(c.fightRuns()), x, y, w);\n", "journal diet line"),
])
print("done", R)
