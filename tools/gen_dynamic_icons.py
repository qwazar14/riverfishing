#!/usr/bin/env python3
"""§26.1 dynamic icons, fully data-driven (the BEWLR compositor replacement):

RODS (§rod-layers): items/<rod>.json selects on DISPLAY CONTEXT first —
  * gui/ground/head (fallback): drawn blank + reel + line + rig overlays (custom_model_data
    STRING selects: 0=reel sprite, 1=line type, 2=rig sprite; RodData.refreshIconLayers syncs them);
  * fixed (rod pod): blank + reel only — the line is out in the water, drawn in 3D (§pod-line);
  * hands: MIRRORED rod_m sprites with the tuned hand transforms (§rod-mirror/§rod-debug), and the
    line+rig overlays hide while the line is cast (custom_model_data FLAG 0, set by FishingManager).

FISH (§fish-scale): items/<fish>.json range_dispatches on custom_model_data float[0] (written by
FishItem.create at the catch) into scale-bucket models that multiply every display-context scale.
"""
import json, os, shutil

ASSETS = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", "common", "src", "main", "resources", "assets", "riverfishing"))
MODELS = os.path.join(ASSETS, "models", "item")
ITEMS = os.path.join(ASSETS, "items")

RODS = ["stick", "bamboo", "pole", "ultralight", "spinning", "feeder", "bottom", "carp", "winter",
        "boat", "sea_spin", "surf", "trolling"]  # +sea quartet (0.5.0)
REELS = [1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000, 10000, 12000, 14000]
LINE_TYPES = ["mono", "braid", "fluoro"]
RIG_SPRITES = ["rig_primitive", "rig_float", "rig_grusha", "rig_feeder", "rig_flat_feeder",
               "rig_ground", "rig_predator", "rig_carp", "rig_catfish"]
# depth lift per overlay category so coplanar composite layers don't z-fight
Z_OFF = {"blank": 0.0, "reel": 0.03, "line": 0.06, "rig": 0.09}

HAND_CONTEXTS = ["thirdperson_righthand", "thirdperson_lefthand",
                 "firstperson_righthand", "firstperson_lefthand"]
# §rod-debug: the ACTUAL hand poses live in CODE (RodHandTransform, applied by the two in-hand
# mixins) so /rfrod can tune them live — the JSON hand displays only carry the per-layer depth lift.

FISH = ["asp", "barracuda", "bleak", "blue_bream", "blue_marlin", "bluegill", "bream", "burbot",
        "carp", "carp_koi_asagi", "carp_koi_bekko", "carp_koi_kohaku", "carp_koi_showa_sanke",
        "carp_koi_tancho_sanke", "catfish", "channel_catfish", "char", "chub", "cod", "conger",
        "crucian_carp", "eel", "flounder", "garfish", "grass_carp", "grayling", "gudgeon",
        "halibut", "herring", "ide", "largemouth_bass", "lenok", "mackerel", "mahi", "mako",
        "mirror_carp", "nase", "perch", "pike", "pink_salmon", "rainbow_trout", "ray", "roach",
        "rotan", "rudd", "ruffe", "sabrefish", "sailfish", "saithe", "salmon", "seabass",
        "silver_carp", "smelt", "sterlet", "sturgeon", "swordfish", "taimen", "tench", "trout",
        "vimba", "wahoo", "white_bream", "whitefish", "wild_carp", "yellowfin_tuna", "zander"]  # ALL registered species (0.5.0: 66)
BUCKETS = [0.45, 0.6, 0.75, 0.9, 1.05, 1.25, 1.55, 2.0]


def read(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def rod_layers():
    return [("blank_%s" % r, "blank") for r in RODS] \
        + [("reel_%d" % r, "reel") for r in REELS] \
        + [("line_%s" % t, "line") for t in LINE_TYPES] \
        + [(s, "rig") for s in RIG_SPRITES]


def normal_display(base_display, dz):
    """gui/ground/fixed/head from the rod's base display, depth-lifted along z (their view axis)."""
    out = {}
    for ctx, tr in base_display.items():
        if ctx in HAND_CONTEXTS:
            continue
        t = dict(tr)
        trans = list(t.get("translation", [0, 0, 0]))
        trans[2] = round(trans[2] + dz, 4)
        t["translation"] = trans
        out[ctx] = t
    return out


def hand_display(dz):
    """Hands: pose comes from code (RodHandTransform via the mixins, BEFORE this display applies),
    so the JSON entry is just the per-layer depth lift along the sprite's local normal (+Z).
    (Vanilla's left-hand rotation negation is moot — rotation here is identity.)"""
    return {ctx: {"translation": [0, 0, round(dz, 4)]} for ctx in HAND_CONTEXTS}


def model_node(model):
    return {"type": "minecraft:model", "model": model}


def str_select(index, cases):
    return {
        "type": "minecraft:select",
        "property": "minecraft:custom_model_data",
        "index": index,
        "cases": [{"when": when, "model": model_node("riverfishing:item/%s/%s" % (folder, layer))}
                  for when, folder, layer in cases],
        "fallback": {"type": "minecraft:empty"},
    }


def composite(models):
    return {"type": "minecraft:composite", "models": models}


def main():
    for d in ("rod_layer", "rod_layer_m", "fish_scaled"):
        shutil.rmtree(os.path.join(MODELS, d), ignore_errors=True)

    # ---- rod layer models: normal (gui/ground/fixed) + mirrored (hands, tuned poses) ----
    base_display = read(os.path.join(MODELS, "bamboo_rod.json")).get("display", {})
    for sprite, cat in rod_layers():
        write(os.path.join(MODELS, "rod_layer", sprite + ".json"), {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": "riverfishing:item/rod/" + sprite},
            "display": normal_display(base_display, Z_OFF[cat]),
        })
        write(os.path.join(MODELS, "rod_layer_m", sprite + ".json"), {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": "riverfishing:item/rod_m/" + sprite},
            "display": hand_display(Z_OFF[cat]),
        })

    # ---- rod item definitions ----
    def parts(folder, rod, with_tackle):
        out = [model_node("riverfishing:item/%s/blank_%s" % (folder, rod)),
               str_select(0, [("reel_%d" % r, folder, "reel_%d" % r) for r in REELS])]
        if with_tackle:
            out.append(str_select(1, [(t, folder, "line_%s" % t) for t in LINE_TYPES]))
            out.append(str_select(2, [(s, folder, s) for s in RIG_SPRITES]))
        return composite(out)

    for rod in RODS:
        hands = {
            # FLAG 0 = the line is cast/out: the 3D line+bobber represents the tackle then.
            "type": "minecraft:condition",
            "property": "minecraft:custom_model_data",
            "index": 0,
            "on_true": parts("rod_layer_m", rod, False),
            "on_false": parts("rod_layer_m", rod, True),
        }
        cases = [{"when": "fixed", "model": parts("rod_layer", rod, False)}]
        cases += [{"when": ctx, "model": hands} for ctx in HAND_CONTEXTS]
        write(os.path.join(ITEMS, "%s_rod.json" % rod), {"model": {
            "type": "minecraft:select",
            "property": "minecraft:display_context",
            "cases": cases,
            "fallback": parts("rod_layer", rod, True),
        }})

    # ---- fish ----
    for sp in FISH:
        fish_display = read(os.path.join(MODELS, sp + ".json")).get("display", {})
        entries = []
        for i, s in enumerate(BUCKETS):
            scaled = {}
            for ctx, tr in fish_display.items():
                t = dict(tr)
                t["scale"] = [round(v * s, 4) for v in t.get("scale", [1, 1, 1])]
                scaled[ctx] = t
            write(os.path.join(MODELS, "fish_scaled", "%s_%d.json" % (sp, i)), {
                "parent": "riverfishing:item/" + sp,
                "display": scaled,
            })
            entries.append({"threshold": s,
                            "model": model_node("riverfishing:item/fish_scaled/%s_%d" % (sp, i))})
        write(os.path.join(ITEMS, sp + ".json"), {"model": {
            "type": "minecraft:range_dispatch",
            "property": "minecraft:custom_model_data",
            "index": 0,
            "entries": entries,
            "fallback": model_node("riverfishing:item/" + sp),
        }})

    print("rods: %d defs, %d layer models x2 variants; fish: %d x %d buckets" %
          (len(RODS), len(rod_layers()), len(FISH), len(BUCKETS)))


if __name__ == "__main__":
    main()
