# -*- coding: utf-8 -*-
"""The 0.10.0 tune-up, five mechanical pieces in one idempotent script (run on each tree):
  §bite-spread   the long rod's random spread 0..45 s -> 0..60 s and the ice clamp 120 s -> 160 s (a third longer),
                 and a dead water that comes back to life re-clocks each line with its own phase, so three rods
                 on a pod stop ringing together.
  §finder-reach  the sounder's section reads 36 m instead of 23 and shows up to 16 fish instead of 10.
  §snag-sense    a seventh perk — Bottom Sense: -8 %/rank snags, a dead snag rarer again, -5 %/rank breaks under strain.
  §line-repair   four string around a worn line gives it back fresh — one shaped recipe per line item.
  §stall-only    the crafting recipes of tackle the fisherman sells assembled, or the bench ties, are gone:
                 ten rods, nine reels, twelve lines, eleven lures, six rigs, two leaders. What nobody sells keeps its recipe.
    py tools/patches/p_tuneup_0100.py [tree ...]"""
import io, json, os, sys
from collections import OrderedDict

TREES = [r"C:/Users/Qwazar/VS Code Projects/fishing mod", r"C:/Users/Qwazar/wt/rf1201", r"C:/Users/Qwazar/wt/rf26"]
FM = "common/src/main/java/com/riverfishing/fishing/FishingManager.java"
FS = "common/src/main/java/com/riverfishing/client/FinderScreen.java"
AS = "common/src/main/java/com/riverfishing/fishing/AnglerSkills.java"
JS = "common/src/main/java/com/riverfishing/client/JournalScreen.java"
LANG = "common/src/main/resources/assets/riverfishing/lang"

LINES = (["line_mono_%03d" % round(d * 100) for d in (0.10, 0.14, 0.18, 0.25, 0.30, 0.40, 0.50, 0.60, 0.70, 0.80)]
         + ["line_braid_%03d" % round(d * 100) for d in (0.16, 0.20, 0.25, 0.30, 0.40, 0.50, 0.60)]
         + ["line_fluoro_%03d" % round(d * 100) for d in (0.14, 0.16, 0.20, 0.25, 0.30, 0.40)])

# §stall-only: sold assembled by the fisherman (ModVillagers) or tied at the bench (TackleForm)
REMOVE = (["%s_rod" % r for r in ("bamboo", "spinning", "feeder", "winter", "carp", "bottom", "sea_spin", "surf", "boat", "trolling")]
          + ["reel_%d" % r for r in (2000, 3000, 5000, 6000, 7000, 8000, 10000, 12000, 14000)]
          + ["line_braid_016"] + ["upgrade_line_%s" % s for s in ("fluoro_020", "fluoro_040", "mono_050", "mono_060", "mono_070", "mono_080",
                                                                  "braid_030", "braid_040", "braid_050", "braid_060")]
          + ["bladebait", "castmaster", "crankbait", "jig", "popper", "spinner", "spinnerbait", "spoon", "swimbait", "wacky_worm", "mormyshka"]
          + ["rig_%s" % r for r in ("carp", "catfish", "feeder", "flat_feeder", "ground", "grusha")]
          + ["leader_fluoro", "leader_titanium"])

PERK = {
    "en_us": ("Bottom Sense", "-8%/rank snags, a dead snag rarer again, -5%/rank line breaks under strain."),
    "ru_ru": ("Чутьё дна", "−8%/ур. зацепов, глухой зацеп ещё реже, −5%/ур. обрывов под нагрузкой."),
    "uk_ua": ("Чуття дна", "−8%/рів. зачепів, глухий зачіп ще рідше, −5%/рів. обривів під навантаженням."),
}


def rd(p): return io.open(p, encoding="utf-8").read()
def wr(p, s): io.open(p, "w", encoding="utf-8", newline="\n").write(s)


def sub(path, old, new, what, count=1):
    s = rd(path)
    if new in s: return
    assert old in s, what + " @ " + path
    wr(path, s.replace(old, new, count))


def run(tree):
    j = lambda *a: os.path.join(tree, *a)
    name = os.path.basename(tree.rstrip("/\\"))
    fm = j(FM)
    # ---- §bite-spread ----
    sub(fm, "case BOTTOM -> Math.max(660, (long) (delay * 1.5)) + level.getRandom().nextInt(900);",
        "case BOTTOM -> Math.max(660, (long) (delay * 1.5)) + level.getRandom().nextInt(1200);   // §bite-spread: 0..60 s, was 0..45", "bottom spread")
    sub(fm, "* AnglerSkills.biteSpeedMult(sp), 200, 2400);", "* AnglerSkills.biteSpeedMult(sp), 200, 3200);   // §bite-spread: 160 s, was 120", "ice clamp")
    sub(fm, "            session.biteAtTick = now + Math.max(100L,\n                    (long) (-(BiteEngine.T_MIN_TICKS / sNew) * Math.log(1.0 - random.nextDouble())));",
        "            // §bite-spread: plus a phase of its own, or every line on a pod re-clocks from the same tick\n"
        "            session.biteAtTick = now + random.nextInt(300) + Math.max(100L,\n                    (long) (-(BiteEngine.T_MIN_TICKS / sNew) * Math.log(1.0 - random.nextDouble())));", "revival")
    # ---- §finder-reach ----
    sub(fm, "public static final int PROFILE_FROM = 2, PROFILE_N = 23;", "public static final int PROFILE_FROM = 2, PROFILE_N = 36;   // §finder-reach: 36 m, was 23", "profile n")
    sub(j(FS), "private static final int ICON = 24, MAX_FISH = 10;", "private static final int ICON = 24, MAX_FISH = 16;   // §finder-reach", "max fish")
    # ---- §snag-sense ----
    sub(j(AS), '        STRONG_LINE("strong_line", "hand"),\n', '        STRONG_LINE("strong_line", "hand"),\n        SNAG_SENSE("snag_sense", "hand"),   // §snag-sense\n', "perk enum")
    sub(j(AS), "    /** Рыбацкая удача: flat trophy-chance bonus added to the roll (+1%/rank). */",
        "    /** §snag-sense Чутьё дна: snag-chance multiplier (−8%/rank); a dead snag takes it twice. */\n"
        "    public static double snagMult(Player player) {\n"
        "        return 1.0 - rank(player, Perk.SNAG_SENSE) * 0.08;\n"
        "    }\n\n"
        "    /** §snag-sense: the over-strain break roll's multiplier (−5%/rank). */\n"
        "    public static double breakMult(Player player) {\n"
        "        return 1.0 - rank(player, Perk.SNAG_SENSE) * 0.05;\n"
        "    }\n\n"
        "    /** Рыбацкая удача: flat trophy-chance bonus added to the roll (+1%/rank). */", "perk methods")
    sub(j(JS), '            case FRUGAL, QUICK_BITE, NATURALIST, STRONG_LINE -> "+" + (rank * 5) + "%";',
        '            case FRUGAL, QUICK_BITE, NATURALIST, STRONG_LINE -> "+" + (rank * 5) + "%";\n            case SNAG_SENSE -> "-" + (rank * 8) + "%";', "perk label")
    s = rd(fm)
    if "§snag-sense" not in s:
        assert s.count("double sc = RiverFishingConfig.snagChance();") == 2, "snag sites"
        s = s.replace("double sc = RiverFishingConfig.snagChance();", "double sc = RiverFishingConfig.snagChance() * AnglerSkills.snagMult(sp);   // §snag-sense")
        old = "session.snagOutcome = sroll < SNAG_DEAD_CHANCE * sc ? 2 : (sroll < SNAG_TOTAL_CHANCE * sc ? 1 : 0);"
        assert old in s, "spin dead snag"
        s = s.replace(old, "session.snagOutcome = sroll < SNAG_DEAD_CHANCE * sc * AnglerSkills.snagMult(sp) ? 2 : (sroll < SNAG_TOTAL_CHANCE * sc ? 1 : 0);")
        old = "if (sroll < SNAG_DEAD_CHANCE * sc) {          // 3% dead"
        assert old in s, "still dead snag"
        s = s.replace(old, "if (sroll < SNAG_DEAD_CHANCE * sc * AnglerSkills.snagMult(sp)) {   // 3% dead")
        old = "(0.008 + 0.055 * overshoot + 0.028 * session.overStress) * RiverFishingConfig.breakSensitivity());"
        assert old in s, "break roll"
        s = s.replace(old, "(0.008 + 0.055 * overshoot + 0.028 * session.overStress) * RiverFishingConfig.breakSensitivity()\n                        * AnglerSkills.breakMult(sp));   // §snag-sense")
        wr(fm, s)
    for code, (n, d) in PERK.items():
        p = j(LANG, code + ".json"); data = json.loads(rd(p), object_pairs_hook=OrderedDict)
        if "skill.riverfishing.snag_sense" in data: continue
        out = OrderedDict()
        for k, v in data.items():
            out[k] = v
            if k == "skill.riverfishing.finesse.desc":
                out["skill.riverfishing.snag_sense"] = n; out["skill.riverfishing.snag_sense.desc"] = d
        wr(p, json.dumps(out, ensure_ascii=False, indent=2) + "\n")
    # ---- recipes: dialect by tree ----
    rdir = j("common/src/main/resources/data/riverfishing/recipes") if name == "rf1201" else j("common/src/main/resources/data/riverfishing/recipe")
    assert os.path.isdir(rdir), rdir
    old_dialect = name == "rf1201"; string_keys = name == "rf26"
    ing = (lambda i: i) if string_keys else (lambda i: {"item": i})
    res = (lambda i: {"item": i, "count": 1}) if old_dialect else (lambda i: {"id": i, "count": 1})
    added = 0
    for ln in LINES:
        p = os.path.join(rdir, "repair_" + ln + ".json")
        if os.path.exists(p): continue
        r = {"type": "minecraft:crafting_shaped", "pattern": [" S ", "SLS", " S "],
             "key": {"S": ing("minecraft:string"), "L": ing("riverfishing:" + ln)}, "result": res("riverfishing:" + ln)}
        wr(p, json.dumps(r, indent=2) + "\n"); added += 1
    removed = 0
    for r in REMOVE:
        p = os.path.join(rdir, r + ".json")
        if os.path.exists(p): os.remove(p); removed += 1
    print("  patched %-12s repair recipes +%d, tackle recipes -%d" % (name, added, removed))


for t in (sys.argv[1:] or TREES): run(t)
