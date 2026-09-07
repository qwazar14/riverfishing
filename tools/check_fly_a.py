# -*- coding: utf-8 -*-
"""§fly Stream A: the tackle is wired end to end, or the fly rod is a name with nothing behind it.

    py -X utf8 tools/check_fly_a.py [root]
"""
import io, json, os, sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
J = os.path.join(ROOT, "common/src/main/java/com/riverfishing")
R = os.path.join(ROOT, "common/src/main/resources")
fails = []


def src(rel):
    return io.open(os.path.join(J, rel), encoding="utf-8").read()


def need(text, needle, msg):
    if needle not in text:
        fails.append(msg)


rod, rig, layout = src("component/RodType.java"), src("component/RigType.java"), src("rig/RigLayout.java")
need(rod, 'FLY       ("fly",        9,   true,     1000,   2000,   0,      0,      false)', "RodType.FLY missing or not (9, reel 1000-2000, cast 0/0)")
need(rod, "WINTER, FLY -> RodClass.FLOAT", "FLY must fish the FLOAT flow")
need(rod, "case FLY -> RigType.FLY;", "FLY's native rig must be RigType.FLY")
need(rod, 'public String modelKey() { return this == FLY ? "ultralight" : jsonKey; }', "modelKey must borrow the ultralight blank for FLY only")
need(rig, 'FLY       ("fly",          2,   1,   true)', "RigType.FLY missing or not (2 g, 1 hook, leader)")
need(layout, "case FLY -> new SlotRole[]{LEADER, LURE};", "the fly rig must lay out LEADER, LURE")

menu = src("menu/RigMenu.java")
need(menu, "if (type == RigType.FLY && role == SlotRole.LURE) return stack.getItem() instanceof com.riverfishing.item.TiedLureItem;",
     "the fly rig's LURE slot must accept only TiedLureItem")

# the two client lookups of the 3D blank go through modelKey(); no blank lookup by jsonKey() remains
rr = src("client/RodItemRenderer.java")
need(rr, "rod.rodType().modelKey()", "drawPodBlank must key the blank by modelKey()")
need(rr, "r.rodType().modelKey() : \"bamboo\"", "renderByItem must key the blank by modelKey()")
if "rodType().jsonKey()" in rr:
    fails.append("RodItemRenderer still looks a blank up by jsonKey()")
need(src("client/RodModelLayers.java"), "§fly", "RodModelLayers carries no §fly note on ROD_KEYS")

# registration: the loops cover every enum value, so the durability decision is the one line to check
need(src("registry/ModItems.java"), 'if ("fly".equals(key)) return 144;', "fly_rod has no durability decision")
need(src("registry/ModItems.java"), 'for (RodType type : RodType.values())', "rods are no longer registered from RodType.values() — fly_rod would not exist")
need(src("registry/ModItems.java"), 'for (RigType type : RigType.values())', "rigs are no longer registered from RigType.values() — rig_fly would not exist")
need(src("item/RodItem.java"), 'tooltip.riverfishing.rod_class.fly', "RodItem shows no fly class line")

for rel in ("data/riverfishing/recipe/fly_rod.json", "assets/riverfishing/models/item/fly_rod.json",
            "assets/riverfishing/models/item/rig_fly.json"):
    p = os.path.join(R, rel)
    if not os.path.exists(p):
        fails.append("missing " + rel)
        continue
    d = json.load(io.open(p, encoding="utf-8"))
    if rel.startswith("data/") and d.get("result", {}).get("id") != "riverfishing:fly_rod":
        fails.append("fly_rod.json does not craft riverfishing:fly_rod")

lang = json.load(io.open(os.path.join(ROOT, "tools/patches/lang_fly_a.json"), encoding="utf-8"))
keys = {k: set(v) for k, v in lang.items()}
if set(keys) != {"en_us", "ru_ru", "uk_ua"} or len({frozenset(v) for v in keys.values()}) != 1:
    fails.append("lang_fly_a.json: the three languages do not share one key set")
for k in ("item.riverfishing.fly_rod", "item.riverfishing.rig_fly", "tooltip.riverfishing.rod_class.fly"):
    if k not in keys.get("en_us", ()):
        fails.append("lang_fly_a.json lacks " + k)

if fails:
    print("FAILED:")
    for f in fails:
        print("  " + f)
    sys.exit(1)
print("fly-a: FLY in RodType/RigType/RigLayout, the lure slot takes only a tied fly, both blank lookups use modelKey, "
      "recipe + models + lang present")
