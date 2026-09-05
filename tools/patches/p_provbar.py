# -*- coding: utf-8 -*-
"""§provinces: the region is on the instrument bar, under the water body.

    py -X utf8 tools/patches/p_provbar.py <root> [1211|1201|26]

It was on the water-sample view, which is the third page and the one a player opens on purpose. The
province is not a detail — it is half the answer to "why is that fish not in this river", and the other
half (the water body) is already the first line of the bar under the face. So it goes directly beneath
it, on every view, where you cannot open the sounder without reading it.
"""
import io, os, sys

ROOT = sys.argv[1]
D = sys.argv[2] if len(sys.argv) > 2 else "1211"
P = os.path.join(ROOT, "common/src/main/java/com/riverfishing/client/FinderScreen.java")
GET = 'getStringOr("prov", "")' if D == "26" else 'getString("prov")'
TYPE = 'getStringOr("type", "")' if D == "26" else 'getString("type")'

s = io.open(P, encoding="utf-8").read()
if "provbar" in s:
    print("  already patched")
    sys.exit(0)

old = """        y = pair(g, x, y, "finder.riverfishing.water",
                Component.translatable("water.riverfishing." + w.%s));""" % TYPE
assert old in s, "the bar's water line moved"
s = s.replace(old, old + """
        // §provbar: and which part of the world that water is in. The two lines together are the whole
        // of why a fish is or is not here, and they belong next to each other rather than three pages
        // apart. Empty on the strip's short sounding, which sends no province.
        String prov = w.%s;
        if (!prov.isEmpty()) {
            y = pair(g, x, y, "finder.riverfishing.province",
                    Component.translatable("province.riverfishing." + prov));
        }""" % GET, 1)

io.open(P, "w", encoding="utf-8", newline="\n").write(s)
print("  FinderScreen: the region sits under the water body on the bar")
print("done (%s)" % D)
