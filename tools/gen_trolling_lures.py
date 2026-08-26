# -*- coding: utf-8 -*-
"""Draw the two 0.7.0 trolling lures, 16x16, in the same style as the existing eight.

    python tools/gen_trolling_lures.py

Both were asked for by name: "some lures trolling lures such as the skirted octopus jigs and giant
spoons". They are drawn rather than palette-swapped because neither has a donor — the existing lures are
all small diagonal blades, and the point of these two is that they are BIG.

Style lifted from spoon.png and castmaster.png: a dark outline, a steel body with a highlight down one
edge, and a split ring at the tail. The octopus jig adds the skirt, which is the whole reason a trolling
angler reaches for it.
"""
import io, os, sys

try:
    from PIL import Image
except ImportError:
    sys.exit("needs Pillow: python -m pip install pillow")

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "common/src/main/resources/assets/riverfishing/textures/item")

# The existing lures' palette, sampled from spoon.png / castmaster.png.
OUTLINE = (0x60, 0x56, 0x36, 255)
STEEL_D = (0x7A, 0x7C, 0x82, 255)
STEEL = (0x92, 0x98, 0xA6, 255)
STEEL_L = (0xC4, 0xCA, 0xD4, 255)
SHINE = (0xF2, 0xF6, 0xFC, 255)
LEAD = (0x5A, 0x5E, 0x66, 255)
SKIRT_D = (0x8E, 0x24, 0x1E, 255)
SKIRT = (0xC0, 0x39, 0x2B, 255)
SKIRT_L = (0xE0, 0x60, 0x48, 255)
EYE = (0xF0, 0xD8, 0x60, 255)
CLEAR = (0, 0, 0, 0)


def blank():
    return [[CLEAR] * 16 for _ in range(16)]


def put(g, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        g[y][x] = c


def ring(g, x, y):
    """The split ring every lure in this mod hangs from."""
    put(g, x, y, STEEL_D); put(g, x + 1, y, STEEL_D)
    put(g, x - 1, y + 1, STEEL_D); put(g, x + 2, y + 1, STEEL_D)
    put(g, x - 1, y + 2, STEEL_D); put(g, x + 2, y + 2, STEEL_D)
    put(g, x, y + 3, STEEL_D); put(g, x + 1, y + 3, STEEL_D)


def giant_spoon():
    """A big oval blade — the whole point is that it is far larger than the 10-60 g spoon."""
    g = blank()
    # Widest at the middle, tapering to both ends: rows are (x_start, width).
    body = [(7, 2), (6, 4), (5, 6), (4, 7), (3, 8), (3, 8), (2, 8), (2, 8), (2, 7), (3, 6), (3, 4)]
    for i, (x0, w) in enumerate(body):
        y = i + 1
        put(g, x0 - 1, y, OUTLINE)
        put(g, x0 + w, y, OUTLINE)
        for x in range(x0, x0 + w):
            # Highlight down the leading edge, shadow along the trailing one.
            g[y][x] = SHINE if x <= x0 + 1 else (STEEL_L if x < x0 + w - 2 else STEEL)
    for x in range(6, 10):
        put(g, x, 12, OUTLINE)
    ring(g, 7, 12)
    return g


def octopus_jig():
    """A skirted octopus jig: lead head with an eye, and a hanging skirt of tentacles."""
    g = blank()
    # Head — a rounded lead cone, widest just above the skirt.
    head = [(7, 2), (6, 4), (5, 6), (5, 6), (5, 6)]
    for i, (x0, w) in enumerate(head):
        y = i + 1
        put(g, x0 - 1, y, OUTLINE)
        put(g, x0 + w, y, OUTLINE)
        for x in range(x0, x0 + w):
            g[y][x] = STEEL_L if x <= x0 + 1 else (LEAD if x >= x0 + w - 1 else STEEL)
    put(g, 6, 3, OUTLINE); put(g, 7, 3, EYE)          # the eye, the one detail that reads at 16px
    # Skirt — tentacles of unequal length, which is what makes it read as a skirt and not a blade.
    lengths = [4, 6, 7, 5, 7, 6, 4]
    for k, ln in enumerate(lengths):
        x = 4 + k
        for j in range(ln):
            y = 6 + j
            if y > 15:
                break
            g[y][x] = SKIRT_L if k % 3 == 0 else (SKIRT if j < ln - 1 else SKIRT_D)
    put(g, 3, 6, OUTLINE); put(g, 11, 6, OUTLINE)
    ring(g, 7, 0)
    return g


def save(name, g):
    im = Image.new("RGBA", (16, 16))
    im.putdata([g[y][x] for y in range(16) for x in range(16)])
    im.save(os.path.join(OUT, name + ".png"))
    print("  %-16s %d opaque pixels" % (name, sum(1 for r in g for c in r if c[3])))


def main():
    save("giant_spoon", giant_spoon())
    save("octopus_jig", octopus_jig())
    return 0


if __name__ == "__main__":
    sys.exit(main())
