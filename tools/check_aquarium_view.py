# -*- coding: utf-8 -*-
"""§aqua-view: the tank's water is the renderer's, coloured by quality, and the modules are drawn.

    py -X utf8 tools/check_aquarium_view.py [root]

  1. Neither tank model carries a "#water" element any more — a model water would sit inside the
     renderer's and paint the murk blue again.
  2. textures/block/aquarium_water.png exists (the renderer binds it by path; a missing file is a
     purple-black box, silently).
  3. AquariumRenderer draws the water and the modules BEFORE the "no fish, no roe" early return, reads
     slots 10 and 11, and colours by waterColor(); the block entity exposes getWater().
  4. waterColor() is the same three stops on every tree — read back and compared.
"""
import io, json, os, re, sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
A = os.path.join(ROOT, "common/src/main/resources/assets/riverfishing")
J = os.path.join(ROOT, "common/src/main/java/com/riverfishing")
fails = []

for side in ("left", "right"):
    d = json.load(io.open(os.path.join(A, "models/block/aquarium_tank_%s.json" % side), encoding="utf-8"))
    if any(f.get("texture") == "#water" for e in d["elements"] for f in e["faces"].values()) or "water" in d["textures"]:
        fails.append("aquarium_tank_%s.json still draws water — the renderer's box would sit inside it" % side)
if not os.path.exists(os.path.join(A, "textures/block/aquarium_water.png")):
    fails.append("textures/block/aquarium_water.png is missing — the water box would render as the missing texture")

r = io.open(os.path.join(J, "client/AquariumRenderer.java"), encoding="utf-8").read()
be = io.open(os.path.join(J, "block/AquariumBlockEntity.java"), encoding="utf-8").read()
if "public int getWater()" not in be:
    fails.append("AquariumBlockEntity has no getWater()")
if "WATER_LAYER" not in r or "waterColor(" not in r:
    fails.append("AquariumRenderer does not draw a coloured water box")
early = r.find("if (fishes.isEmpty() && roe.isEmpty()) return;")
water = max(r.find("renderWater(be,"), r.find("s.waterArgb = be.getWater()"))
if early < 0 or water < 0 or water > early:
    fails.append("the water must be drawn/extracted BEFORE the no-fish early return — an empty tank has water in it")
if "slot = 10; slot <= 11" not in r:
    fails.append("AquariumRenderer does not read the two module slots (10, 11)")
stops = re.findall(r"int\[\] good = \{([^}]*)\}, mid = \{([^}]*)\}, bad = \{([^}]*)\}", r)
if not stops:
    fails.append("waterColor() has lost its three stops")
elif [s.replace(" ", "") for s in stops[0]] != ["0x6F,0xC0,0xF0,0x6C", "0x7A,0xB4,0x8A,0x8C", "0x6E,0x58,0x2E,0xC0"]:
    fails.append("waterColor()'s stops differ from the other trees': %s" % (stops[0],))

if fails:
    print("FAILED:")
    for x in fails:
        print("  " + x)
    sys.exit(1)
print("aquarium view: water is the renderer's (blue 100 / green 50 / brown 0), modules in slots 10-11 drawn, model water gone")
