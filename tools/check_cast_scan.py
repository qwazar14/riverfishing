# -*- coding: utf-8 -*-
"""§cast-ceiling / §ice-hole-cast: the water scan stops at floors, and falls through ice AND the hole in it.

    py -X utf8 tools/check_cast_scan.py [root]
"""
import io, os, re, sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
s = io.open(os.path.join(ROOT, "common/src/main/java/com/riverfishing/fishing/FishingManager.java"), encoding="utf-8").read()
m = re.search(r"private static BlockPos findWaterColumn\(.*?\n    \}\n", s, re.S)
fails = []
if not m:
    fails.append("findWaterColumn() not found")
else:
    body = m.group(0)
    if "getCollisionShape" not in body:
        fails.append("the scan no longer stops at a floor — a cast tunnels into cave lakes again (§cast-ceiling)")
    ice, hole, floor = body.find("isIce(st)"), body.find("instanceof com.riverfishing.block.IceHoleBlock"), body.find("getCollisionShape")
    if ice < 0 or hole < 0:
        fails.append("the scan must fall through ice (isIce) AND the drilled hole (IceHoleBlock) — the winter rod said 'no water' in 0.9.0")
    elif not (ice < floor and hole < floor):
        fails.append("the ice/hole exception must come BEFORE the floor test, or the hole is a floor")
if fails:
    print("FAILED:"); [print("  " + f) for f in fails]; sys.exit(1)
print("cast scan: stops at floors, falls through ice and the drilled hole")
