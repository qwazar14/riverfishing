#!/usr/bin/env python3
"""§26.1 dynamic icons, fully data-driven (the BEWLR compositor replacement):

RODS (§rod-layers): items/<rod>.json selects on DISPLAY CONTEXT first —
  * gui/ground/head (fallback): drawn blank + reel + line + rig overlays (custom_model_data
    STRING selects: 0=reel sprite, 1=line type, 2=rig sprite; RodData.refreshIconLayers syncs them);
  * fixed (rod pod): blank + reel only — the line is out in the water, drawn in 3D (§pod-line);
  * hands: MIRRORED rod_m sprites with the tuned hand transforms (§rod-mirror/§rod-debug), and the
    line+rig overlays hide while the line is cast (custom_model_data FLAG 0, set by FishingManager).
  In EVERY context the base blank layer is a range_dispatch on custom_model_data FLOAT 0 — the
  §rod-bend bucket FishingManager writes onto the rod during a fight (RodData.setBend). Bucket 0
  falls back to the straight blank, so a rod that never fought looks exactly as it always did.

FISH (§fish-scale): items/<fish>.json range_dispatches on custom_model_data float[0] (written by
FishItem.create at the catch) into scale-bucket models that multiply every display-context scale.
"""
import io, json, os, re, shutil

ASSETS = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", "common", "src", "main", "resources", "assets", "riverfishing"))
ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), ".."))
MODELS = os.path.join(ASSETS, "models", "item")
ITEMS = os.path.join(ASSETS, "items")

RODS = ["stick", "bamboo", "pole", "ultralight", "spinning", "feeder", "bottom", "carp", "winter",
        "boat", "sea_spin", "surf", "trolling"]  # +sea quartet (0.5.0)
REELS = [1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000, 10000, 12000, 14000]
LINE_TYPES = ["mono", "braid", "fluoro"]
RIG_SPRITES = ["rig_primitive", "rig_float", "rig_grusha", "rig_feeder", "rig_flat_feeder",
               "rig_ground", "rig_predator", "rig_carp", "rig_catfish"]
BEND_BUCKETS = 6  # §rod-bend: must match RodData.BEND_BUCKETS and tools/GenRodBend.java's AMP length
# depth lift per overlay category so coplanar composite layers don't z-fight
Z_OFF = {"blank": 0.0, "reel": 0.03, "line": 0.06, "rig": 0.09}

HAND_CONTEXTS = ["thirdperson_righthand", "thirdperson_lefthand",
                 "firstperson_righthand", "firstperson_lefthand"]
# §rod-debug: the ACTUAL hand poses live in CODE (RodHandTransform, applied by the two in-hand
# mixins) so /rfrod can tune them live — the JSON hand displays only carry the per-layer depth lift.

# Derived, never hand-listed: a registered fish item is exactly one that has both a fish profile and an
# item model. The old hardcoded list said "ALL registered species (0.5.0: 66)" and silently stayed at 66
# when 0.6.0 added four — so those four shipped on 26.x with no item definition and rendered as the
# missing-texture checkerboard.
PROFILES = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", "common", "src", "main",
                                         "resources", "data", "riverfishing", "fish_profiles"))
FISH = sorted({f[:-5] for f in os.listdir(PROFILES) if f.endswith(".json")}
              & {f[:-5] for f in os.listdir(MODELS) if f.endswith(".json")})
# §fish-scale: the ladder FishItem.getIconScale walks (length/50, clamped 0.45..8.0). It used to stop
# at 2.0, which is the INVENTORY cap — so every fish over a metre rendered identically everywhere, and
# a 450 cm marlin in the hand was the same object as a 100 cm pike. The rungs above 2.0 exist for the
# hand and the ground; nothing needs to go past 5.0 because that is where those contexts cap.
BUCKETS = [0.45, 0.6, 0.75, 0.9, 1.05, 1.25, 1.55, 2.0, 2.5, 3.2, 4.0, 5.0]
# §fish-scale caps, per display context — the numbers the BEWLR applied on 1.21.1 before 26.x made the
# model do the sizing. A slot stays readable (0.8..2.0); in the hand, dropped or mounted the giants are
# the spectacle they are meant to be.
GUI_MIN, GUI_MAX, WORLD_MAX = 0.8, 2.0, 5.0
SLOT_CONTEXTS = ("gui", "fixed", "head")


def context_scale(ctx, bucket):
    if ctx == "gui":
        return min(GUI_MAX, max(GUI_MIN, bucket))
    if ctx in SLOT_CONTEXTS:
        return min(GUI_MAX, bucket)
    return min(WORLD_MAX, bucket)


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
        + [(bend_sprite(r, b), "blank") for r in RODS for b in range(1, BEND_BUCKETS + 1)] \
        + [("reel_%d" % r, "reel") for r in REELS] \
        + [("line_%s" % t, "line") for t in LINE_TYPES] \
        + [(s, "rig") for s in RIG_SPRITES]


def bend_sprite(rod, bucket):
    """§rod-bend: the arc-sheared blank for one bucket (tools/GenRodBend.java draws these)."""
    return "blank_%s_bend%d" % (rod, bucket)


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


# §morph on 26.x: BEWLR is gone, so the per-specimen multiply tint rides custom_model_data colors[0]
# (written by FishItem.stampIcon) and is read back by this tint source. Default -1 is opaque white,
# i.e. the sprite exactly as drawn — which is what a fish with no stamp should look like.
FISH_TINTS = [{"type": "minecraft:custom_model_data", "index": 0, "default": -1}]


def fish_node(model):
    d = model_node(model)
    d["tints"] = FISH_TINTS
    return d


def flat_species():
    """The flatfish, read out of FishPose.java so the two cannot drift apart.

    §fish-pose says a flounder, a halibut and a ray lie flat "in open water, in the aquarium and on the
    ground where you dropped it". The first two are code; the third was the BEWLR's doing and did not
    survive the port, so on 26.x they stood on their edge on the bank. It is a display transform now,
    which means it belongs in these generated models — and the species list has to come from the one
    place that already owns it.
    """
    src = os.path.join(ROOT, "common", "src", "main", "java", "com", "riverfishing",
                       "fish", "FishPose.java")
    text = io.open(src, encoding="utf-8").read()
    m = re.search(r"FLAT\s*=\s*Set\.of\(([^)]*)\)", text)
    if not m:
        raise SystemExit("FishPose.FLAT not found — the pose table moved, fix this reader")
    names = re.findall(r'"([^"]+)"', m.group(1))
    lay = re.search(r"return\s+(-?[\d.]+)f;", text[text.index("public static float lay()"):])
    if not lay:
        raise SystemExit("FishPose.lay() not found")
    return set(names), float(lay.group(1))


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


def blank_node(folder, rod):
    """§rod-bend: the base blank layer, dispatched on the bend bucket in custom_model_data FLOAT 0
    (RodData.setBend). Same range_dispatch technique the fish scale buckets use; bucket 0 — and any
    rod with no float stored at all — resolves to the fallback, i.e. the straight blank."""
    straight = model_node("riverfishing:item/%s/blank_%s" % (folder, rod))
    return {
        "type": "minecraft:range_dispatch",
        "property": "minecraft:custom_model_data",
        "index": 0,
        "entries": [{"threshold": b,
                     "model": model_node("riverfishing:item/%s/%s" % (folder, bend_sprite(rod, b)))}
                    for b in range(1, BEND_BUCKETS + 1)],
        "fallback": straight,
    }


def normalise_fish_base_models():
    """26.x draws fish from a real model, so their base model must be item/generated with a layer0.

    On 1.21.1 every fish parents minecraft:builtin/entity because a BEWLR renderer draws it. The port
    converted all 66 in one commit (557fb0d) and nothing kept doing it, so the four species added in
    0.6.0 arrived in the 1.21.1 shape: builtin/entity with only a `particle` texture, which renders as
    the missing-texture checkerboard in the inventory and on the ground. Idempotent — run it after
    adding a species.
    """
    fixed = []
    for sp in FISH:
        p = os.path.join(MODELS, sp + ".json")
        d = read(p)
        tex = "riverfishing:item/fish/" + sp
        if d.get("parent") == "minecraft:builtin/entity" or "layer0" not in d.get("textures", {}):
            d["parent"] = "minecraft:item/generated"
            d.setdefault("textures", {})["particle"] = tex
            d["textures"]["layer0"] = tex
            write(p, d)
            fixed.append(sp)
    print("fish base models: %d normalised%s"
          % (len(fixed), (" — " + ", ".join(fixed)) if fixed else " (all already item/generated)"))


def main():
    normalise_fish_base_models()
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
        out = [blank_node(folder, rod),
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
    flat, lay = flat_species()
    for sp in FISH:
        base = os.path.join(MODELS, sp + ".json")
        fish_display = read(base).get("display", {})
        # §fish-pose: a flatfish lies down where it is DROPPED. Only "ground" — in a slot or in the hand
        # the icon is a picture of the fish and should stay the picture that was drawn.
        if sp in flat:
            ground = dict(fish_display.get("ground", {"translation": [0, 2, 0]}))
            ground["rotation"] = [lay, 0, 0]
            fish_display["ground"] = ground
            d = read(base)
            d.setdefault("display", {})["ground"] = ground
            write(base, d)
        entries = []
        for i, s in enumerate(BUCKETS):
            scaled = {}
            for ctx, tr in fish_display.items():
                t = dict(tr)
                k = context_scale(ctx, s)
                t["scale"] = [round(v * k, 4) for v in t.get("scale", [1, 1, 1])]
                scaled[ctx] = t
            write(os.path.join(MODELS, "fish_scaled", "%s_%d.json" % (sp, i)), {
                "parent": "riverfishing:item/" + sp,
                "display": scaled,
            })
            entries.append({"threshold": s,
                            "model": fish_node("riverfishing:item/fish_scaled/%s_%d" % (sp, i))})
        write(os.path.join(ITEMS, sp + ".json"), {"model": {
            "type": "minecraft:range_dispatch",
            "property": "minecraft:custom_model_data",
            "index": 0,
            "entries": entries,
            "fallback": fish_node("riverfishing:item/" + sp),
        }})

    print("rods: %d defs, %d layer models x2 variants; fish: %d x %d buckets" %
          (len(RODS), len(rod_layers()), len(FISH), len(BUCKETS)))


if __name__ == "__main__":
    main()
