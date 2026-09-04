# -*- coding: utf-8 -*-
"""§koi-genes: split the author's white koi into the layers that let ONE sprite paint every variety.

    py -X utf8 tools/gen_koi_layers.py            # writes into all three trees
    py -X utf8 tools/gen_koi_layers.py --preview  # ...and a composite of the nine, to look at

A koi's variety is three colour loci (white ground / red hi / black sumi), and there are nine named
varieties. Drawing nine sprites would mean nine drawings to keep in step; tinting ONE sprite in one
colour cannot show a two-colour fish at all. So the icon model gets four layers cut out of the same
drawing, and the client colours each from the genotype (FishTint.itemColor / FishItem.stampIcon on
26.x, both through FishMorph.koiTint):

    layer0  the whole fish        — the GROUND (white, steel, blue-grey or near-black)
    layer1  patches over the back — the hi   (red), or the ground colour when the fish wears none
    layer2  smaller patches       — the sumi (black), likewise
    layer3  one spot on the head  — the tancho crown, which only `WW RR bb` lights up

The masks are cut FROM THE SPRITE, pixel for pixel, so a layer painted the ground colour reproduces
exactly the ground underneath it and vanishes — that is what lets four layers serve nine varieties with
no per-variety image. They are feathered by ~3 px so a patch's edge blends instead of stepping.

The design doc asked for a three-layer split "by luminance bands". That is not achievable from this
drawing: it is one flat white fish with shading and an outline, so a luminance band gives back the
outline and the shadow, not a red field and a black marking — colouring the outline red paints a red
outline, not a kohaku. The patches are therefore PLACED (the fourth layer, the crown, is why tancho
reads as tancho at all) rather than found, which is the documented fallback's spirit at higher
fidelity: one sprite, no per-variety images.
"""
import io, json, os, sys

import numpy as np
from PIL import Image

MAIN = r"C:/Users/Qwazar/VS Code Projects/fishing mod"
TREES = [MAIN, r"C:/Users/Qwazar/wt/rf1201", r"C:/Users/Qwazar/wt/rf26"]
A = "common/src/main/resources/assets/riverfishing"
SP = "koi_carp"
LAYERS = ["", "_hi", "_sumi", "_crown"]      # layer0 is the sprite itself — no second copy of it

# Patches as (x, y, radius) in the BODY's own box, 0..1, x from the head; the radius is a fraction of
# the body's LENGTH. Three fields of hi over the shoulder, the back and the flank; three smaller sumi
# markings; and one crown, on top of the head, which is the whole of a tancho.
HI = [(0.16, 0.32, 0.115), (0.48, 0.24, 0.135), (0.78, 0.42, 0.10)]
SUMI = [(0.33, 0.55, 0.075), (0.62, 0.22, 0.085), (0.87, 0.60, 0.065)]
CROWN = [(0.10, 0.47, 0.055)]


def erode(mask, r):
    """Shrink a boolean mask by r pixels — the fins are thinner than that, the body is not."""
    out = mask.copy()
    for dy in range(-r, r + 1):
        for dx in range(-r, r + 1):
            if dx * dx + dy * dy > r * r:
                continue
            out &= np.roll(np.roll(mask, dy, 0), dx, 1)
    return out


def blob(h, w, cx, cy, rad, seed, feather=3.0):
    """A wobbled disc, 0..1 with a soft edge — a koi's patches have no compass in them."""
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float64)
    dx, dy = xx - cx, yy - cy
    ang, dist = np.arctan2(dy, dx), np.hypot(dx, dy)
    rng = np.random.default_rng(seed)
    # Three low harmonics only: a fourth turns a small patch into a starfish, which no koi has.
    ph, amp = rng.uniform(0, 2 * np.pi, 3), rng.uniform(0.06, 0.16, 3)
    rr = rad * (1.0 + sum(amp[k] * np.sin((k + 2) * ang + ph[k]) for k in range(3)))
    return np.clip((rr - dist) / feather + 0.5, 0.0, 1.0)


def masks(src):
    """(hi, sumi, crown) as 0..1 fields over the sprite, clipped to the body."""
    a = np.array(Image.open(src).convert("RGBA"))
    h, w = a.shape[:2]
    body = erode(a[..., 3] > 150, 6)
    if not body.any():                       # a differently drawn sprite: fall back to the silhouette
        body = a[..., 3] > 150
    ys, xs = np.nonzero(body)
    x0, x1, y0, y1 = xs.min(), xs.max(), ys.min(), ys.max()
    bw, bh = max(1, x1 - x0), max(1, y1 - y0)
    soft = np.clip(erode(a[..., 3] > 60, 2).astype(np.float64), 0, 1)

    out = []
    for seed, spec in enumerate((HI, SUMI, CROWN)):
        m = np.zeros((h, w))
        for i, (fx, fy, fr) in enumerate(spec):
            m = np.maximum(m, blob(h, w, x0 + fx * bw, y0 + fy * bh, fr * bw, seed * 17 + i))
        out.append(m * soft)
    return a, out


def write_layers(tree, a, ms):
    d = os.path.join(tree, A, "textures/item/fish")
    for name, m in zip(LAYERS[1:], ms):
        px = a.copy()
        px[..., 3] = np.clip(px[..., 3] * m, 0, 255).astype(np.uint8)
        px[px[..., 3] == 0] = 0          # a flat transparent field compresses; stray colour does not
        Image.fromarray(px).save(os.path.join(d, SP + name + ".png"), optimize=True)


def layer_textures():
    return {"layer%d" % i: "riverfishing:item/fish/" + SP + n for i, n in enumerate(LAYERS)}


def write_models(tree):
    """Add the four layers wherever this tree draws the koi from a generated model.

    fish_icon/ is what 1.20.1 and 1.21.1 render (the BEWLR draws that model); on 26.x the item model
    itself is the generated one and fish_scaled/ + items/ are derived from it, so both are updated and
    gen_dynamic_icons.py is re-run afterwards by the caller.
    """
    touched = []
    for sub in ("models/item/fish_icon", "models/item"):
        p = os.path.join(tree, A, sub, SP + ".json")
        if not os.path.isfile(p):
            continue
        m = json.load(io.open(p, encoding="utf-8"))
        tex = m.get("textures", {})
        if "layer0" not in tex:              # builtin/entity: it draws fish_icon, not itself
            continue
        tex.update(layer_textures())
        m["textures"] = tex
        io.open(p, "w", encoding="utf-8", newline="\n").write(
            json.dumps(m, ensure_ascii=False, indent=2) + "\n")
        touched.append(sub)
    return touched


# The palette FishMorph.koiTint paints with — duplicated here ONLY for the preview image.
PREVIEW = {
    "kohaku": (0xF4F2EC, 0xD8342A, None, None),
    "taisho_sanke": (0xF4F2EC, 0xD8342A, 0x2A2622, None),
    "showa": (0x4A423C, 0xC8302A, 0x1E1A18, None),
    "bekko": (0xF4F2EC, None, 0x2A2622, None),
    "asagi": (0x7C93AE, None, 0x46586E, None),
    "platinum": (0xFFFDF6, None, None, None),
    "hi_utsuri": (0x3A322C, 0xD2382A, None, None),
    "karasu": (0x2A2622, None, None, None),
    "tancho": (0xF4F2EC, None, None, 0xD8342A),
}


def preview(a, ms, path):
    h, w = a.shape[:2]
    sheet = Image.new("RGBA", (w * 3, h * 3), (0, 0, 0, 0))
    for i, (name, cols) in enumerate(PREVIEW.items()):
        img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        for layer, c in enumerate(cols):
            rgb = cols[0] if c is None else c
            px = a.astype(np.float64).copy()
            px[..., 0] *= ((rgb >> 16) & 255) / 255.0
            px[..., 1] *= ((rgb >> 8) & 255) / 255.0
            px[..., 2] *= (rgb & 255) / 255.0
            if layer:
                px[..., 3] *= ms[layer - 1]
            img.alpha_composite(Image.fromarray(px.astype(np.uint8)))
        sheet.alpha_composite(img, ((i % 3) * w, (i // 3) * h))
    sheet.save(path)
    print("preview:", path, "-", ", ".join(PREVIEW))


def main():
    src = os.path.join(MAIN, A, "textures/item/fish", SP + ".png")
    a, ms = masks(src)
    for tree in TREES:
        write_layers(tree, a, ms)
        print("  %-28s layers %s, models %s"
              % (os.path.basename(tree), len(LAYERS) - 1, ",".join(write_models(tree)) or "none"))
    if "--preview" in sys.argv:
        preview(a, ms, os.path.join(MAIN, "tools", "koi_preview.png"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
