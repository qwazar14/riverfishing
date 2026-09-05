# -*- coding: utf-8 -*-
"""§pattern-mask: the models that put a family's mask over a fish, per loader generation.

    py -X utf8 tools/wire_pattern_models.py <root> [1211|1201|26]

1.21.1 / 1.20.1 — the fish is drawn by FishItemRenderer (a BEWLR) at a pose it scales itself, so the
mask is ONE flat model per draw×family, rendered a second time at the same pose:

    models/item/pattern/<draw>_<family>.json     a 16×16 quad at z 7.4..8.6 (just outside the
                                                 generated sprite's 7.5..8.5, so no z-fight),
                                                 tintindex 5 — FishTint answers 5 with the marking

66 files. They are registered through ClientModels.allCandidates(), not referenced by any item.

26.x — there is no BEWLR; the item definition draws everything, and the scale is a range_dispatch into
twelve models that differ only in `display`. So the mask needs the same twelve, and the definition
grows a composite: [what it drew before, select(strings[1] = family) → range_dispatch(scale) → the
mask at that scale, tinted by one more custom_model_data colour]. A family the stack does not name
("" — plain, or a gem) falls back to minecraft:empty.

    models/item/pattern/<draw>_<family>.json           item/generated, layer0 = the mask
    models/item/pattern_scaled/<draw>_<family>_<n>.json  parent above + the display of fish_scaled/<draw>_<n>
    items/<item>.json                                    composite [body, pattern]

66 + 792 files on 26.x. Ugly in the jar, invisible in the source: this script owns all of them.
"""
import copy, io, json, os, sys

ROOT = sys.argv[1]
D = sys.argv[2] if len(sys.argv) > 2 else "1211"
A = os.path.join(ROOT, "common/src/main/resources/assets/riverfishing")
DRAWS = ["koi_carp", "carp", "wild_carp", "mirror_carp", "linear_carp", "naked_carp"]
FAMILIES = ["drift", "crown", "banded", "speckled", "mask", "marbled", "veined", "dappled", "ghost", "ember", "aurora"]
# which custom_model_data colour carries the marking on 26.x: the koi already use 0..3
COLOUR_INDEX = {"koi_carp": 4}
TINT_INDEX_1211 = 5


def dump(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    io.open(path, "w", encoding="utf-8", newline="\n").write(json.dumps(obj, indent=2) + "\n")


def tex(draw, fam):
    return "riverfishing:item/fish/pattern/%s_%s" % (draw, fam)


n = 0
if D != "26":
    for draw in DRAWS:
        for fam in FAMILIES:
            dump(os.path.join(A, "models/item/pattern/%s_%s.json" % (draw, fam)), {
                "gui_light": "front",
                "textures": {"particle": tex(draw, fam), "mask": tex(draw, fam)},
                "elements": [{
                    "from": [0, 0, 7.4], "to": [16, 16, 8.6],
                    "faces": {
                        "south": {"uv": [0, 0, 16, 16], "texture": "#mask", "tintindex": TINT_INDEX_1211},
                        "north": {"uv": [16, 0, 0, 16], "texture": "#mask", "tintindex": TINT_INDEX_1211},
                    },
                }],
            })
            n += 1
    print("  %d flat mask models (tintindex %d)" % (n, TINT_INDEX_1211))
    sys.exit(0)

# ---- 26.x ------------------------------------------------------------------------------------------
for draw in DRAWS:
    for fam in FAMILIES:
        dump(os.path.join(A, "models/item/pattern/%s_%s.json" % (draw, fam)),
             {"parent": "minecraft:item/generated", "textures": {"layer0": tex(draw, fam)}})
        n += 1
        for i in range(12):
            scaled = json.load(io.open(os.path.join(A, "models/item/fish_scaled/%s_%d.json" % (draw, i)), encoding="utf-8"))
            dump(os.path.join(A, "models/item/pattern_scaled/%s_%s_%d.json" % (draw, fam, i)),
                 {"parent": "riverfishing:item/pattern/%s_%s" % (draw, fam), "display": scaled["display"]})
            n += 1
print("  %d mask models" % n)


def pattern_branch(draw, thresholds, colour_index):
    """select(family) → range_dispatch(scale) → the mask at that scale, tinted by colours[colour_index]."""
    return {
        "type": "minecraft:select",
        "property": "minecraft:custom_model_data", "index": 1,
        "cases": [{
            "when": fam,
            "model": {
                "type": "minecraft:range_dispatch",
                "property": "minecraft:custom_model_data", "index": 0,
                "entries": [{"threshold": t, "model": {
                    "type": "minecraft:model",
                    "model": "riverfishing:item/pattern_scaled/%s_%s_%d" % (draw, fam, i),
                    "tints": [{"type": "minecraft:custom_model_data", "index": colour_index, "default": -1}],
                }} for i, t in enumerate(thresholds)],
                "fallback": {"type": "minecraft:model",
                             "model": "riverfishing:item/pattern/%s_%s" % (draw, fam),
                             "tints": [{"type": "minecraft:custom_model_data", "index": colour_index, "default": -1}]},
            },
        } for fam in FAMILIES],
        "fallback": {"type": "minecraft:empty"},
    }


def first_dispatch(node):
    """The range_dispatch the body uses — its thresholds are the scale buckets the mask must match."""
    if node.get("type") == "minecraft:range_dispatch":
        return node
    for k in ("fallback",):
        if k in node:
            r = first_dispatch(node[k])
            if r: return r
    for c in node.get("cases", []):
        r = first_dispatch(c["model"])
        if r: return r
    return None


wired = 0
for item in ["carp", "wild_carp", "mirror_carp", "linear_carp", "naked_carp", "koi_carp"]:
    p = os.path.join(A, "items/%s.json" % item)
    d = json.load(io.open(p, encoding="utf-8"))
    if d["model"].get("type") == "minecraft:composite" and "pattern_scaled" in json.dumps(d["model"]["models"][-1]):
        print("  items/%s.json: already wired" % item)
        continue
    body = d["model"]
    disp = first_dispatch(body)
    assert disp, "items/%s.json has no range_dispatch to copy the scale buckets from" % item
    thresholds = [e["threshold"] for e in disp["entries"]]
    colour_index = COLOUR_INDEX.get(item, 1)
    if body.get("type") == "minecraft:select" and body.get("index", 0) == 0:
        # a carp definition already selects the DRAW on strings[0]; the mask must follow the same draw
        cases = [{"when": c["when"], "model": pattern_branch(c["when"], thresholds, colour_index)} for c in body["cases"]]
        pattern = {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
                   "cases": cases, "fallback": pattern_branch(item, thresholds, colour_index)}
    else:
        pattern = pattern_branch(item, thresholds, colour_index)
    d["model"] = {"type": "minecraft:composite", "models": [body, pattern]}
    dump(p, d)
    wired += 1
print("  %d item definitions wrapped in a composite with the pattern branch" % wired)
