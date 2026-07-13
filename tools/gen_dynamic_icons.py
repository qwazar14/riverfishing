#!/usr/bin/env python3
"""§26.1 dynamic icons, fully data-driven (the BEWLR compositor replacement):

RODS (§rod-layers): items/<rod>.json becomes a minecraft:composite — the blank base model plus three
minecraft:select overlays keyed on custom_model_data STRINGS (0=reel sprite, 1=line type, 2=rig
sprite), which RodData.refreshIconLayers keeps in sync with the installed components.
(26.1 does NOT register the component_matches condition, so custom_data can't be matched directly.)

FISH (§fish-scale): items/<fish>.json becomes a minecraft:range_dispatch on custom_model_data
float[0] — FishItem.create() writes the icon scale there at the catch — dispatching to scale-bucket
models that multiply every display-context scale.
"""
import json, os, shutil

ASSETS = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", "common", "src", "main", "resources", "assets", "riverfishing"))
MODELS = os.path.join(ASSETS, "models", "item")
ITEMS = os.path.join(ASSETS, "items")

RODS = ["stick", "bamboo", "pole", "ultralight", "spinning", "feeder", "bottom", "carp", "winter"]
REELS = [1000, 2000, 3000, 4000, 5000, 6000, 7000]
LINES = {"mono": ["010", "014", "018", "025", "030", "040"],
         "braid": ["016", "020", "025", "030"],
         "fluoro": ["014", "016", "020", "025", "030"]}
# rig item id suffix -> overlay sprite (float_light/winter have no own sprite — nearest glyph)
RIGS = {"primitive": "rig_primitive", "float_light": "rig_float", "float": "rig_float",
        "winter": "rig_primitive", "grusha": "rig_grusha", "feeder": "rig_feeder",
        "flat_feeder": "rig_flat_feeder", "ground": "rig_ground", "predator": "rig_predator",
        "carp": "rig_carp", "catfish": "rig_catfish"}
# tiny z-lift per overlay category so coplanar composite layers don't z-fight in hand/gui
Z_OFF = {"blank": 0.0, "reel": 0.02, "line": 0.04, "rig": 0.06}

FISH = ["asp", "bleak", "bream", "burbot", "carp", "carp_koi_asagi", "carp_koi_bekko",
        "carp_koi_kohaku", "carp_koi_showa_sanke", "carp_koi_tancho_sanke", "catfish", "chub",
        "crucian_carp", "eel", "grass_carp", "grayling", "gudgeon", "ide", "mirror_carp", "perch",
        "pike", "roach", "rudd", "ruffe", "sterlet", "tench", "trout", "white_bream", "wild_carp",
        "zander"]
# icon-scale buckets (FishItem.getIconScale clamps to 0.45..2.0); fallback = the 1.0 base model
BUCKETS = [0.45, 0.6, 0.75, 0.9, 1.05, 1.25, 1.55, 2.0]


def read(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def shifted_display(display, dz):
    out = {}
    for ctx, tr in display.items():
        t = dict(tr)
        trans = list(t.get("translation", [0, 0, 0]))
        trans[2] = round(trans[2] + dz, 4)
        t["translation"] = trans
        out[ctx] = t
    return out


def scaled_display(display, s):
    out = {}
    for ctx, tr in display.items():
        t = dict(tr)
        sc = t.get("scale", [1, 1, 1])
        t["scale"] = [round(v * s, 4) for v in sc]
        out[ctx] = t
    return out


def select(index, cases):
    """A custom_model_data STRING select: strings[index] -> overlay layer model, absent -> empty."""
    return {
        "type": "minecraft:select",
        "property": "minecraft:custom_model_data",
        "index": index,
        "cases": [{"when": when, "model": {"type": "minecraft:model",
                                           "model": "riverfishing:item/rod_layer/" + layer}}
                  for when, layer in cases],
        "fallback": {"type": "minecraft:empty"},
    }


def main():
    shutil.rmtree(os.path.join(MODELS, "rod_layer"), ignore_errors=True)
    shutil.rmtree(os.path.join(MODELS, "fish_scaled"), ignore_errors=True)

    # ---- rods ----
    rod_display = read(os.path.join(MODELS, "bamboo_rod.json")).get("display", {})
    # the BASE of every composite is the DRAWN blank sprite from textures/item/rod/blank_<key>.png —
    # NOT the old standalone <rod>.png (that's the pre-compositor icon, §rod-layers)
    layers = [("blank_%s" % rod, "blank") for rod in RODS] \
        + [("reel_%d" % r, "reel") for r in REELS] \
        + [("line_%s" % t, "line") for t in LINES] \
        + sorted({(s, "rig") for s in RIGS.values()})
    for sprite, cat in layers:
        write(os.path.join(MODELS, "rod_layer", sprite + ".json"), {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": "riverfishing:item/rod/" + sprite},
            "display": shifted_display(rod_display, Z_OFF[cat]),
        })

    reel_select = select(0, [("reel_%d" % r, "reel_%d" % r) for r in REELS])
    line_select = select(1, [(t, "line_%s" % t) for t in LINES])
    rig_select = select(2, sorted({(s, s) for s in RIGS.values()}))
    for rod in RODS:
        models = [{"type": "minecraft:model", "model": "riverfishing:item/rod_layer/blank_%s" % rod},
                  reel_select, line_select, rig_select]
        write(os.path.join(ITEMS, "%s_rod.json" % rod), {"model": {"type": "minecraft:composite", "models": models}})

    # ---- fish ----
    for sp in FISH:
        base_display = read(os.path.join(MODELS, sp + ".json")).get("display", {})
        entries = []
        for i, s in enumerate(BUCKETS):
            write(os.path.join(MODELS, "fish_scaled", "%s_%d.json" % (sp, i)), {
                "parent": "riverfishing:item/" + sp,
                "display": scaled_display(base_display, s),
            })
            entries.append({"threshold": s,
                            "model": {"type": "minecraft:model",
                                      "model": "riverfishing:item/fish_scaled/%s_%d" % (sp, i)}})
        write(os.path.join(ITEMS, sp + ".json"), {"model": {
            "type": "minecraft:range_dispatch",
            "property": "minecraft:custom_model_data",
            "index": 0,
            "entries": entries,
            "fallback": {"type": "minecraft:model", "model": "riverfishing:item/" + sp},
        }})

    print("rods: %d composites, %d layer models; fish: %d x %d buckets" %
          (len(RODS), len(layers), len(FISH), len(BUCKETS)))


if __name__ == "__main__":
    main()
