#!/usr/bin/env python3
"""§26.1 dynamic icons, fully data-driven (the BEWLR compositor replacement):

RODS (§rod-layers): items/<rod>.json becomes a minecraft:composite — the blank base model plus
conditional overlay layers (per-size reel, per-type line, per-type rig), each gated by a
minecraft:component_matches condition on the rod's custom_data (RodComponents sub-NBT subset match).

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
Z_OFF = {"reel": 0.02, "line": 0.04, "rig": 0.06}

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


def cond(nbt_subset, layer_model):
    return {
        "type": "minecraft:condition",
        "property": "minecraft:component_matches",
        "predicate": "minecraft:custom_data",
        "value": {"RodComponents": nbt_subset},
        "on_true": {"type": "minecraft:model", "model": "riverfishing:item/rod_layer/" + layer_model},
        "on_false": {"type": "minecraft:empty"},
    }


def main():
    shutil.rmtree(os.path.join(MODELS, "rod_layer"), ignore_errors=True)
    shutil.rmtree(os.path.join(MODELS, "fish_scaled"), ignore_errors=True)

    # ---- rods ----
    rod_display = read(os.path.join(MODELS, "bamboo_rod.json")).get("display", {})
    layers = [("reel_%d" % r, "reel") for r in REELS] \
        + [("line_%s" % t, "line") for t in LINES] \
        + sorted({(s, "rig") for s in RIGS.values()})
    for sprite, cat in layers:
        write(os.path.join(MODELS, "rod_layer", sprite + ".json"), {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": "riverfishing:item/rod/" + sprite},
            "display": shifted_display(rod_display, Z_OFF[cat]),
        })

    for rod in RODS:
        models = [{"type": "minecraft:model", "model": "riverfishing:item/%s_rod" % rod}]
        for r in REELS:
            models.append(cond({"Reel": {"id": "riverfishing:reel_%d" % r}}, "reel_%d" % r))
        for t, dias in LINES.items():
            for d in dias:
                models.append(cond({"Line": {"id": "riverfishing:line_%s_%s" % (t, d)}}, "line_%s" % t))
        for rig, sprite in RIGS.items():
            models.append(cond({"Rig": {"id": "riverfishing:rig_%s" % rig}}, sprite))
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
