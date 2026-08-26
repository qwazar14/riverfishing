# -*- coding: utf-8 -*-
"""§groundbait-tint: split the speckles out of each groundbait icon into a tintable layer.

Minecraft tints a whole model LAYER, never selected pixels, so "recolour the white bits" has to become
"draw the white bits on their own layer and tint that". This keeps the drawing side to ONE file: paint
the jar, run this, and the overlay is generated.

    layer0  the icon exactly as drawn — untinted, so an undyed jar looks like the art
    layer1  the speckles alone, forced to pure white, tinted by the mix's own colour at render time

White is the right ink for layer1 because the tint MULTIPLIES: 0xFFFFFF x colour is the colour itself,
so a speckle comes out at exactly the mix's rgb with nothing of the original left in it.

WHICH PIXELS ARE SPECKLES: the icon's LIGHTEST TONE, whatever that happens to be. One rule and no
threshold to guess at — white speckles on new art are its lightest tone by construction, and on the
current three-tone ramps it picks the highlight, so the feature is visible before a single pixel is
redrawn. `--floor` only stops a muddy icon from having its darkest highlight tinted.

The speckles stay in layer0 as well. That is deliberate: an undyed preset jar gets no tint at all (the
provider returns -1) and must still look like the artist drew it, and a tinted layer1 lands on top of
identical pixels so nothing shows through at the edges.

Re-run after redrawing any icon. It only ever writes the *_tint.png files.

    python tools/gen_groundbait_tint.py
    python tools/gen_groundbait_tint.py --floor 200      # only tint genuinely pale speckles
"""
import argparse
import os
import sys

try:
    from PIL import Image
except ImportError:                                          # pragma: no cover
    sys.exit("needs Pillow:  python -m pip install pillow")

# The four jars a mix can come out in. groundbait_soil is ballast, never a mix, so it is not here.
JARS = ("powder", "grain", "pellet", "cake")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX = os.path.join(ROOT, "common", "src", "main", "resources",
                   "assets", "riverfishing", "textures", "item")

# A tone has to be at least this light to count as a speckle at all. Low enough that today's ramps
# qualify, high enough that an all-dark icon is left alone rather than having its least-dark tone tinted.
DEFAULT_FLOOR = 80


def split(name, floor):
    src = os.path.join(TEX, "groundbait_%s.png" % name)
    if not os.path.exists(src):
        return None, "no such icon"
    im = Image.open(src).convert("RGBA")
    out = Image.new("RGBA", im.size, (0, 0, 0, 0))

    pixels = [(x, y, im.getpixel((x, y))) for y in range(im.height) for x in range(im.width)]
    opaque = [(x, y, p) for x, y, p in pixels if p[3] > 0]
    if not opaque:
        return 0, "icon is empty"

    # "Lightest" by the darkest channel, so a saturated colour never beats a pale one on one channel.
    lightest = max(min(p[0], p[1], p[2]) for _, _, p in opaque)
    if lightest < floor:
        return 0, "lightest tone is %d, below the floor of %d" % (lightest, floor)

    kept = 0
    for x, y, p in opaque:
        if min(p[0], p[1], p[2]) == lightest:
            out.putpixel((x, y), (255, 255, 255, p[3]))
            kept += 1

    out.save(os.path.join(TEX, "groundbait_%s_tint.png" % name))
    return kept, "#%02X%02X%02X" % next(p[:3] for _, _, p in opaque if min(p[:3]) == lightest)


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--floor", type=int, default=DEFAULT_FLOOR,
                    help="a tone must be at least this light to be tinted (default %d)" % DEFAULT_FLOOR)
    args = ap.parse_args()

    for name in JARS:
        kept, note = split(name, args.floor)
        if kept is None:
            print("  %-8s %s" % (name, note))
        elif kept == 0:
            print("  %-8s nothing tinted — %s" % (name, note))
        else:
            print("  %-8s %3d px of %s -> groundbait_%s_tint.png" % (name, kept, note, name))


if __name__ == "__main__":
    main()
