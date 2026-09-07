# -*- coding: utf-8 -*-
"""§hook-mouth §strength-by-size §sea-roamers: three answers to the first afternoon in the Afrotropical.
  1. The hook floors the fish. A #10 was taking 1 g catfish and 3 g clarias: the size gradient's gate
     (0.34 at K=0.25) lets a hook eight sizes off through. Now a hook has a MOUTH — the smallest fish
     that can take it, 40 g at #8 and halving every two sizes down — that floors the weight roll (capped
     at 60 % of the range, like the lure rule) and refuses a species whose biggest specimen is smaller.
  2. Strength follows the specimen. requiredKg used the profile's strength whole, so a 72 g barbel pulled
     like the 2 kg fish the table describes; it is scaled by sqrt(weight/mean), floored at 0.35.
  3. The sea species' provinces go back to empty — the oceans carry them everywhere, and an old world must
     not lose its cod. The importer keeps that rule for the next table.
Idempotent; run on each tree.  py tools/patches/p_hook_mouth.py [tree ...]"""
import io, json, os, sys

TREES = [r"C:/Users/Qwazar/VS Code Projects/fishing mod", r"C:/Users/Qwazar/wt/rf1201", r"C:/Users/Qwazar/wt/rf26"]
FM = "common/src/main/java/com/riverfishing/fishing/FishingManager.java"
BE = "common/src/main/java/com/riverfishing/engine/BiteEngine.java"
PROF = "common/src/main/resources/data/riverfishing/fish_profiles"
OCEAN_ROAMERS = {"bull_shark", "jack_crevalle", "mullet"}   # sea AND river, and everywhere before the table


def rd(p): return io.open(p, encoding="utf-8").read()
def wr(p, s): io.open(p, "w", encoding="utf-8", newline="\n").write(s)


def sub(s, old, new, what):
    if new in s: return s
    assert old in s, what
    return s.replace(old, new, 1)


def sea_roamer(sid, p):
    wb = p.get("water_bodies", {})
    sea_only = wb.get("sea", 0) > 0 and all(wb.get(k, 0) <= 0 for k in ("river", "lake", "pond", "swamp", "puddle"))
    return sea_only or sid in OCEAN_ROAMERS


def run(tree):
    be = os.path.join(tree, BE); s = rd(be)
    s = sub(s, "            best = Math.max(best, gradient(size, p.hookIdeal, p.hookTolerance));\n        }\n        return best;\n",
            "            best = Math.max(best, gradient(size, p.hookIdeal, p.hookTolerance));\n        }\n"
            "        // §hook-mouth: a species whose biggest specimen cannot get the smallest hook on the rig into its\n"
            "        // mouth does not take it, whatever the size gradient says\n"
            "        if (p.weightMax < mouthG(c.hookSizes)) return 0.0;\n"
            "        return best;\n    }\n\n"
            "    /**\n"
            "     * §hook-mouth: the smallest fish that can take a hook of this size, in grams — 40 g at #8, halving\n"
            "     * every two sizes down (#16: 2.5 g) and doubling every two up (#2: 320 g). The smallest hook on the\n"
            "     * rig sets it; nothing is asked of an empty rig.\n"
            "     */\n"
            "    public static double mouthG(java.util.Collection<Integer> hookSizes) {\n"
            "        if (hookSizes.isEmpty()) return 0.0;\n"
            "        int smallest = java.util.Collections.max(hookSizes);   // bigger number, smaller hook\n"
            "        return 40.0 * Math.pow(2.0, (8 - smallest) / 2.0);\n", "hookScore anchor")
    wr(be, s)

    fm = os.path.join(tree, FM); s = rd(fm)
    s = sub(s, "        double weight = p.weightMin + (p.weightMax - p.weightMin) * biased;\n",
            "        // §hook-mouth: the same physics for the HOOK — a fish that took a #8 is one whose mouth fits a #8.\n"
            "        // The smallest hook on the rig floors the roll (capped at 60 % of the range so the roll stays a\n"
            "        // roll); this is what ends the 3 g clarias on a #10.\n"
            "        double mouthW = session.ctx != null ? BiteEngine.mouthG(session.ctx.hookSizes) : 0;\n"
            "        if (mouthW > 0 && p.weightMax > p.weightMin) {\n"
            "            double minW = Mth.clamp(mouthW, p.weightMin, p.weightMin + (p.weightMax - p.weightMin) * 0.6);\n"
            "            double floor = (minW - p.weightMin) / (p.weightMax - p.weightMin);\n"
            "            biased = floor + (1.0 - floor) * biased;\n"
            "        }\n\n"
            "        double weight = p.weightMin + (p.weightMax - p.weightMin) * biased;\n", "rollFish anchor")
    s = sub(s, "                profile.fightStrength * (1.0 + fightMassKg(weightKg)) * 2.0);\n",
            "                profile.fightStrength * sizeStrength(profile, weightKg) * (1.0 + fightMassKg(weightKg)) * 2.0);\n", "requiredKg anchor")
    s = sub(s, "    public static double fightMassKg(double kg) {",
            "    /**\n"
            "     * §strength-by-size: the profile's strength is the species' — a mean specimen's. A 72 g barbel is not\n"
            "     * the 2 kg fish the table describes: strength scales with sqrt(weight / mean), floored at 0.35 and\n"
            "     * capped at 1 (a heavier fish already pulls harder through fightMassKg).\n"
            "     */\n"
            "    public static double sizeStrength(FishProfile p, double weightKg) {\n"
            "        return Mth.clamp(Math.sqrt(weightKg * 1000.0 / Math.max(1.0, p.weightMean)), 0.35, 1.0);\n"
            "    }\n\n"
            "    public static double fightMassKg(double kg) {", "fightMassKg anchor")
    if "import com.riverfishing.engine.BiteEngine;" not in s:
        s = s.replace("import com.riverfishing.engine.BiteContext;", "import com.riverfishing.engine.BiteContext;\nimport com.riverfishing.engine.BiteEngine;", 1)
    wr(fm, s)

    d = os.path.join(tree, PROF); reset = []
    for f in sorted(os.listdir(d)):
        if not f.endswith(".json"): continue
        p = json.load(io.open(os.path.join(d, f), encoding="utf-8"))
        if sea_roamer(f[:-5], p) and p.get("provinces"):
            p["provinces"] = []; reset.append(f[:-5])
            io.open(os.path.join(d, f), "w", encoding="utf-8", newline="\n").write(json.dumps(p, ensure_ascii=False, indent=2) + "\n")
    print("  patched", os.path.basename(tree.rstrip("/\\")), "sea provinces reset:", len(reset))


for t in (sys.argv[1:] or TREES): run(t)
