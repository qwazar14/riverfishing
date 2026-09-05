# -*- coding: utf-8 -*-
"""§lake-26.2: every minecraft:lake feature the mod ships carries the fields 26.2 refuses to load without.

    py -X utf8 tools/check_lake_feature.py [root]

26.2 gave the lake feature three block predicates — can_place_feature, can_replace_with_air_or_fluid,
can_replace_with_barrier — and made them required. A lake JSON in the 26.1.2 shape fails registry
loading, which is not a missing pond: it is the world refusing to open, on 26.2 Fabric and NeoForge
both. That shipped once (riverfishing:cherry_pond).

Older versions ignore keys their codec does not know, so ONE file in the union shape serves every tree,
and this check asks the same of every tree: a lake without the three keys is a 26.2 crash waiting in a
file that looks fine everywhere else.
"""
import io, json, os, sys, glob

ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
D = os.path.join(ROOT, "common/src/main/resources/data")
NEED = ("can_place_feature", "can_replace_with_air_or_fluid", "can_replace_with_barrier")
fails, lakes = [], 0
for p in glob.glob(os.path.join(D, "**", "configured_feature", "*.json"), recursive=True):
    d = json.load(io.open(p, encoding="utf-8"))
    if d.get("type") != "minecraft:lake":
        continue
    lakes += 1
    c = d.get("config", {})
    for k in NEED:
        if k not in c:
            fails.append("%s: no %s — 26.2 refuses to load it and the world will not open" % (os.path.relpath(p, ROOT), k))
    for k in ("fluid", "barrier"):
        if k not in c:
            fails.append("%s: no %s" % (os.path.relpath(p, ROOT), k))
if fails:
    print("FAILED:")
    for x in fails:
        print("  " + x)
    sys.exit(1)
print("lake features: %d, each in the 26.2 shape (older versions ignore the extra keys)" % lakes)
