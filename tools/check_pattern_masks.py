# -*- coding: utf-8 -*-
"""§pattern-mask: every family has a hard mask on every patterned sprite, and the client can reach it.

    py -X utf8 tools/check_pattern_masks.py [root]

  1. 6 draws × 11 families = 66 masks, each the size of its sprite, each with EVERY alpha 0 or 255.
     The author's rule: no feather — a half-transparent texel is the blur he redrew the koi to be rid
     of, and it is exactly what a "small improvement" to the generator would reintroduce.
  2. A mask never marks a texel the sprite does not have — a marking hanging off the fish's outline.
  3. 1.21.1/1.20.1: the flat model exists per mask with tintindex 5, FishTint answers tintindex 5 with
     the marking, ClientModels lists the models (an unlisted model is never baked — the renderer would
     get the missing model and draw nothing, silently), and the renderer draws the mask after the body.
  4. 26.x: the pattern models and their twelve scaled children exist, every patterned item definition
     is a composite whose second branch selects the family on strings[1], and stampIcon writes that
     string and the marking colour.
"""
import io, json, os, re, struct, sys, zlib

ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
A = os.path.join(ROOT, "common/src/main/resources/assets/riverfishing")
J = os.path.join(ROOT, "common/src/main/java/com/riverfishing")
DRAWS = ["koi_carp", "carp", "wild_carp", "mirror_carp", "linear_carp", "naked_carp"]
FAMILIES = ["drift", "crown", "banded", "speckled", "mask", "marbled", "veined", "dappled", "ghost", "ember", "aurora"]
fails = []

sys.path.insert(0, os.path.join(ROOT, "tools"))
try:
    from gen_pattern_masks import read, silhouette
except ImportError:
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from gen_pattern_masks import read, silhouette

is26 = os.path.exists(os.path.join(A, "items", "koi_carp.json"))

# 1 + 2: the masks
checked = 0
for draw in DRAWS:
    sprite = os.path.join(A, "textures/item/fish/%s.png" % draw)
    if not os.path.exists(sprite):
        fails.append("no sprite %s" % draw); continue
    w, h, rows = read(sprite)
    body = silhouette(rows)
    for fam in FAMILIES:
        p = os.path.join(A, "textures/item/fish/pattern/%s_%s.png" % (draw, fam))
        if not os.path.exists(p):
            fails.append("missing mask %s_%s" % (draw, fam)); continue
        mw, mh, mrows = read(p)
        if (mw, mh) != (w, h):
            fails.append("%s_%s is %dx%d, its sprite is %dx%d" % (draw, fam, mw, mh, w, h)); continue
        soft = sum(1 for r in mrows for px in r if 0 < px[3] < 255)
        if soft:
            fails.append("%s_%s has %d half-transparent texels — that is a feather, and the rule is none" % (draw, fam, soft))
        off = sum(1 for y, r in enumerate(mrows) for x, px in enumerate(r) if px[3] == 255 and (x, y) not in body)
        if off:
            fails.append("%s_%s marks %d texels outside the fish" % (draw, fam, off))
        if not any(px[3] == 255 for r in mrows for px in r):
            fails.append("%s_%s is empty — the family draws nothing on this fish" % (draw, fam))
        checked += 1

# 3 / 4: the wiring
if not is26:
    for draw in DRAWS:
        for fam in FAMILIES:
            p = os.path.join(A, "models/item/pattern/%s_%s.json" % (draw, fam))
            if not os.path.exists(p):
                fails.append("no model item/pattern/%s_%s" % (draw, fam)); continue
            m = json.load(io.open(p, encoding="utf-8"))
            if not any(f.get("tintindex") == 5 for e in m.get("elements", []) for f in e["faces"].values()):
                fails.append("item/pattern/%s_%s does not carry tintindex 5 — the body's tint would paint it" % (draw, fam))
    tint = io.open(os.path.join(J, "client/FishTint.java"), encoding="utf-8").read()
    if "tintIndex == 5" not in tint or "patternTint" not in tint:
        fails.append("FishTint.itemColor does not answer tintIndex 5 with the marking")
    cm = io.open(os.path.join(J, "client/ClientModels.java"), encoding="utf-8").read()
    if "patternModel(" not in cm:
        fails.append("ClientModels.allCandidates does not list the pattern models — never baked, never drawn")
    fr = io.open(os.path.join(J, "client/FishItemRenderer.java"), encoding="utf-8").read()
    if '"item/pattern/"' not in fr:
        fails.append("FishItemRenderer.patternModel does not point at models/item/pattern/")
    # the CALL, not the declaration: the declaration sits above the render method
    a, b = fr.find("ov == net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY ? overlay : ov, model);"), fr.find("patternModel(draw, ")
    if b < 0:
        fails.append("FishItemRenderer never draws the mask")
    elif b < a:
        fails.append("FishItemRenderer draws the mask BEFORE the body — the body paints over it")
else:
    for draw in DRAWS:
        for fam in FAMILIES:
            if not os.path.exists(os.path.join(A, "models/item/pattern/%s_%s.json" % (draw, fam))):
                fails.append("no model item/pattern/%s_%s" % (draw, fam))
            for i in range(12):
                if not os.path.exists(os.path.join(A, "models/item/pattern_scaled/%s_%s_%d.json" % (draw, fam, i))):
                    fails.append("no model item/pattern_scaled/%s_%s_%d" % (draw, fam, i)); break
    for item in DRAWS:
        d = json.load(io.open(os.path.join(A, "items/%s.json" % item), encoding="utf-8"))
        m = d["model"]
        if m.get("type") != "minecraft:composite" or len(m.get("models", [])) != 2:
            fails.append("items/%s.json is not a composite of [body, pattern]" % item); continue
        s = json.dumps(m["models"][1])
        if '"index": 1' not in s or "pattern_scaled" not in s or "minecraft:empty" not in s:
            fails.append("items/%s.json: the pattern branch must select strings[1] and fall back to empty" % item)
    fi = io.open(os.path.join(J, "item/FishItem.java"), encoding="utf-8").read()
    if "Pattern.family(" not in fi or "patternTint" not in fi:
        fails.append("FishItem.stampIcon does not write the family string and the marking colour")

if fails:
    print("FAILED:")
    for x in fails[:40]:
        print("  " + x)
    if len(fails) > 40: print("  … and %d more" % (len(fails) - 40))
    sys.exit(1)
print("pattern masks: %d masks, every alpha 0 or 255, none off the fish; wiring present for %s"
      % (checked, "26.x" if is26 else "1.21.1/1.20.1"))
