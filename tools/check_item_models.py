# -*- coding: utf-8 -*-
"""Every item on 26.x needs a model AND an items/ definition, or it ships as the missing texture.

    python tools/check_item_models.py

1.21.4 split item rendering in two: assets/<ns>/models/item/<id>.json still describes the geometry, but
assets/<ns>/items/<id>.json is what actually binds an item to it. Ship one without the other and the
item renders as the pink-and-black checkerboard — no warning at build time, no error in the log, and
nothing wrong with the texture it is pointing at.

Which is how groundbait_soil went out. It had its texture, it had its model, both identical to canon's,
and it had no items/ definition, so the ballast the whole groundbait system rests on was an untextured
square on 26.1.2. Found by a player, not by any build.

Checked in BOTH directions: a model with no definition is invisible, and a definition with no model is a
log error on every resource reload.
"""
import glob, io, json, os, sys

TREES = {"rf26": r"C:\Users\Qwazar\wt\rf26"}
A = os.path.join("common", "src", "main", "resources", "assets", "riverfishing")

fails = []
for tag, tree in TREES.items():
    mdir = os.path.join(tree, A, "models", "item")
    idir = os.path.join(tree, A, "items")
    if not os.path.isdir(idir):
        print("  %-6s no items/ directory \u2014 tree not on the split-model versions?" % tag)
        continue
    models = {os.path.basename(p) for p in glob.glob(os.path.join(mdir, "*.json"))}
    defs = {os.path.basename(p) for p in glob.glob(os.path.join(idir, "*.json"))}

    for name in sorted(models - defs):
        fails.append("%s: %s has a model and NO items/ definition \u2014 ships as the missing texture"
                     % (tag, name[:-5]))
    for name in sorted(defs - models):
        fails.append("%s: %s has an items/ definition and NO model \u2014 a log error every reload"
                     % (tag, name[:-5]))

    # A definition that names a model path nobody wrote is the same failure wearing a different hat.
    for p in sorted(glob.glob(os.path.join(idir, "*.json"))):
        try:
            d = json.loads(io.open(p, encoding="utf-8").read())
        except ValueError as e:
            fails.append("%s: %s is not valid json (%s)" % (tag, os.path.basename(p), e))
            continue
        ref = (d.get("model") or {}).get("model")
        if isinstance(ref, str) and ref.startswith("riverfishing:item/"):
            want = os.path.join(mdir, ref.split("/", 1)[1] + ".json")
            if not os.path.exists(want):
                fails.append("%s: %s points at %s, which does not exist"
                             % (tag, os.path.basename(p), ref))
    print("  %-6s %d models, %d definitions" % (tag, len(models), len(defs)))

if fails:
    print("\nFAILED:")
    for f in fails:
        print("  " + f)
    sys.exit(1)
print("\nevery item has both halves, and every definition points at a model that exists")
