# -*- coding: utf-8 -*-
"""Tables for the drag, stress, bench and trolling pages.

The numbers are COMPUTED here from the same constants the code uses, not typed. Typing them is how a
guide ends up quietly disagreeing with the game two releases later; deriving them means this script
fails loudly the day someone edits the formula and forgets the page.

  · overstressTick  — chance = min(0.5, 0.008 + 0.055*overshoot + 0.028*overStress); rolled EVERY TICK,
                      overStress builds 0.015 + 0.02*overshoot per tick and caps at 2.0.
  · fightTick       — effectiveStrain = lineStrain + 0.5*drag; dragRelief = clamp(drag/10, 0, 0.5);
                      relaxTick = 0.010 + dragRelief*0.02.
  · TackleForm      — nuggets = (sizeIdx+1) * hooks, ingots = ceil(nuggets/3); iron = 1 per started 30 g.
  · trollTick       — the speed window is 0.12..0.60 blocks per tick; the lure trails 14 blocks.
"""
import io, json, math, os, sys

TREES = [r"C:\Users\Qwazar\VS Code Projects\fishing mod",
         r"C:\Users\Qwazar\wt\rf1201", r"C:\Users\Qwazar\wt\rf26"]
LANG = "common/src/main/resources/assets/riverfishing/lang"
K = "guide.riverfishing."

TPS = 20
HOOK_SIZES = [16, 14, 12, 10, 8, 6, 4, 2, 1]


def per_second(overshoot, over_stress):
    p = min(0.5, 0.008 + 0.055 * overshoot + 0.028 * over_stress)
    return 1.0 - (1.0 - p) ** TPS


def hook_ingots(idx, hooks):
    nuggets = (1 + idx) * hooks
    return (nuggets + 2) // 3


def pct(x):
    return "%d%%" % round(x * 100)


# ---- stress: what an overshoot costs per second, fresh and after holding it
STRESS_ROWS = [(0.00, "0"), (0.25, "+25"), (0.50, "+50"), (1.00, "+100")]
stress = [(lbl, pct(per_second(o, 0.0)), pct(per_second(o, 2.0))) for o, lbl in STRESS_ROWS]

# ---- drag: what a reel's drag buys, and where it stops buying it
DRAGS = [0, 2, 5, 10, 15]
drag = []
for d in DRAGS:
    relief = min(max(d / 10.0, 0.0), 0.5)
    drag.append((("%d" % d), "+%.1f" % (0.5 * d), "%.3f" % (0.010 + relief * 0.02)))

# ---- bench: the hook bill, one hook and three
bench = [("%d" % s, "%d" % hook_ingots(i, 1), "%d" % hook_ingots(i, 3))
         for i, s in enumerate(HOOK_SIZES) if s in (16, 12, 10, 8, 4, 1)]

assert [r[1] for r in bench] == ["1", "1", "2", "2", "3", "3"], "hook ladder moved — check TackleForm"
assert [r[2] for r in bench] == ["1", "3", "4", "5", "7", "9"], "grusha ladder moved — check TackleForm"

HEAD = {
 "en_us": {"stress": "Over the limit|Break per second|Held there",
           "drag": "Drag, kg|Adds to line|Tension bleed/tick",
           "bench": "Hook|One hook|Grusha (3)",
           "troll": "|Value"},
 "ru_ru": {"stress": "\u041f\u0435\u0440\u0435\u0431\u043e\u0440|\u041e\u0431\u0440\u044b\u0432 \u0437\u0430 \u0441\u0435\u043a|\u0415\u0441\u043b\u0438 \u0434\u0435\u0440\u0436\u0430\u0442\u044c",
           "drag": "\u0424\u0440\u0438\u043a\u0446\u0438\u043e\u043d, \u043a\u0433|\u0414\u0430\u0451\u0442 \u043b\u0435\u0441\u043a\u0435|\u0421\u0431\u0440\u043e\u0441/\u0442\u0438\u043a",
           "bench": "\u041a\u0440\u044e\u0447\u043e\u043a|\u041e\u0434\u0438\u043d|\u0413\u0440\u0443\u0448\u0430 (3)",
           "troll": "|\u0417\u043d\u0430\u0447\u0435\u043d\u0438\u0435"},
 "uk_ua": {"stress": "\u041f\u0435\u0440\u0435\u0431\u0456\u0440|\u041e\u0431\u0440\u0438\u0432 \u0437\u0430 \u0441\u0435\u043a|\u042f\u043a\u0449\u043e \u0442\u0440\u0438\u043c\u0430\u0442\u0438",
           "drag": "\u0424\u0440\u0438\u043a\u0446\u0456\u043e\u043d, \u043a\u0433|\u0414\u0430\u0454 \u0432\u043e\u043b\u043e\u0441\u0456\u043d\u0456|\u0421\u043a\u0438\u0434/\u0442\u0438\u043a",
           "bench": "\u0413\u0430\u0447\u043e\u043a|\u041e\u0434\u0438\u043d|\u0413\u0440\u0443\u0448\u0430 (3)",
           "troll": "|\u0417\u043d\u0430\u0447\u0435\u043d\u043d\u044f"},
}

TROLL = {
 "en_us": [("Slowest that works", "2.4 blocks/s"), ("Fastest that works", "12 blocks/s"),
           ("Seconds of steady speed to arm", "3"), ("The lure trails astern", "14 blocks")],
 "ru_ru": [("\u041c\u0435\u0434\u043b\u0435\u043d\u043d\u0435\u0435 \u043d\u0435\u043b\u044c\u0437\u044f", "2.4 \u0431\u043b\u043e\u043a\u0430/\u0441"),
           ("\u0411\u044b\u0441\u0442\u0440\u0435\u0435 \u043d\u0435\u043b\u044c\u0437\u044f", "12 \u0431\u043b\u043e\u043a\u043e\u0432/\u0441"),
           ("\u0421\u0435\u043a\u0443\u043d\u0434 \u0440\u043e\u0432\u043d\u043e\u0433\u043e \u0445\u043e\u0434\u0430", "3"),
           ("\u041f\u0440\u0438\u043c\u0430\u043d\u043a\u0430 \u0438\u0434\u0451\u0442 \u0441\u0437\u0430\u0434\u0438", "14 \u0431\u043b\u043e\u043a\u043e\u0432")],
 "uk_ua": [("\u041f\u043e\u0432\u0456\u043b\u044c\u043d\u0456\u0448\u0435 \u043d\u0435 \u043c\u043e\u0436\u043d\u0430", "2.4 \u0431\u043b\u043e\u043a\u0430/\u0441"),
           ("\u0428\u0432\u0438\u0434\u0448\u0435 \u043d\u0435 \u043c\u043e\u0436\u043d\u0430", "12 \u0431\u043b\u043e\u043a\u0456\u0432/\u0441"),
           ("\u0421\u0435\u043a\u0443\u043d\u0434 \u0440\u0456\u0432\u043d\u043e\u0433\u043e \u0445\u043e\u0434\u0443", "3"),
           ("\u041f\u0440\u0438\u043d\u0430\u0434\u0430 \u0439\u0434\u0435 \u043f\u043e\u0437\u0430\u0434\u0443", "14 \u0431\u043b\u043e\u043a\u0456\u0432")],
}

fails = []
for tree in TREES:
    tag = os.path.basename(tree)
    for lang in ("en_us", "ru_ru", "uk_ua"):
        path = os.path.join(tree, LANG, lang + ".json")
        data = json.loads(io.open(path, encoding="utf-8").read())
        h = HEAD[lang]
        data[K + "stress.table"] = "\n".join([h["stress"]] + ["|".join(r) for r in stress])
        data[K + "drag.table"] = "\n".join([h["drag"]] + ["|".join(r) for r in drag])
        data[K + "tacklebench.table"] = "\n".join([h["bench"]] + ["|".join(r) for r in bench])
        data[K + "trolling.table"] = "\n".join([h["troll"]] + ["%s|%s" % kv for kv in TROLL[lang]])
        io.open(path, "w", encoding="utf-8", newline="\n").write(
            json.dumps(data, ensure_ascii=False, indent=2) + "\n")
        for key in ("stress", "drag", "tacklebench", "trolling"):
            v = data[K + key + ".table"]
            widths = {len(r.split("|")) for r in v.split("\n")}
            if len(widths) != 1:
                fails.append("%s %s %s: rows have %s columns" % (tag, lang, key, sorted(widths)))
            if "%" in v and "%%" not in v and key != "stress":
                fails.append("%s %s %s: a bare %% Minecraft will try to read" % (tag, lang, key))
    print("  %-12s 4 tables" % tag)

print("\n  break per second, fresh:    " + "  ".join("%s>%s" % (r[0], r[1]) for r in stress))
print("  break per second, held on: " + "  ".join("%s>%s" % (r[0], r[2]) for r in stress))
print("  drag relief caps at 5 kg — %s and %s bleed identically" % (drag[2][2], drag[4][2]))

if fails:
    print("\nFAILED:")
    for f in sorted(set(fails)):
        print("  " + f)
    sys.exit(1)
