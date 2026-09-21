# -*- coding: utf-8 -*-
"""§ice-hole-cast: a drilled hole is a way down to the water, not a floor.

    py -X utf8 tools/patches/p_icehole.py <root>

Reported (0.9.0, NeoForge 1.21.1): a winter rod cast into a drilled hole answers "no water to cast".
§cast-ceiling (0.9.0) made the water scan stop at the first block that would stop the tackle falling,
letting ICE through so a frozen lake still says "drill a hole" — but it asked IceAugerItem.isIce(),
which names the four vanilla ices and not the mod's own IceHoleBlock. The hole has a collision shape,
so the scan stopped ON the hole and never reached the water under it. Every ice angler in 0.9.0 hit
this on the first cast.
"""
import io, os, sys

ROOT = sys.argv[1]
P = os.path.join(ROOT, "common/src/main/java/com/riverfishing/fishing/FishingManager.java")
s = io.open(P, encoding="utf-8").read()
if "§ice-hole-cast" in s:
    print("  already patched"); sys.exit(0)
old = "            if (com.riverfishing.item.IceAugerItem.isIce(st)) continue;\n"
assert s.count(old) == 1, "the ice exception moved"
s = s.replace(old, old.replace("isIce(st))", "isIce(st)\n"
              "                    // §ice-hole-cast: the drilled hole is the way DOWN to the water, not a floor\n"
              "                    || st.getBlock() instanceof com.riverfishing.block.IceHoleBlock)"), 1)
io.open(P, "w", encoding="utf-8", newline="\n").write(s)
print("  FishingManager: the water scan falls through a drilled hole")
