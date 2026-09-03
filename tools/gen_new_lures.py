# -*- coding: utf-8 -*-
"""§more-lures-2: the four 0.9.0 lures as 16x16 item icons, in the existing lures' palette.

    py tools/gen_new_lures.py

Same reason gen_trolling_lures.py exists — one palette, one file, the same pixels every time — but
written against gen_contract_icon.py's raw zlib PNG writer instead of Pillow, so the icons regenerate
on a machine with nothing but a stock Python.

The art stays light and low-saturation on purpose: ClientPlatformImpl tints layer0 by the dye colour,
so every pixel here is MULTIPLIED by whatever the player dyed the lure. A saturated base eats the dye
and the whole §lure-color feature stops reading. The wacky worm is the deliberate exception — its
colour is its name, and it is the one lure people picture in pink.
"""
import os, struct, zlib

W = H = 16
CLEAR = (0, 0, 0, 0)

# Sampled from spoon.png / castmaster.png — the same names gen_trolling_lures.py uses.
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
# Sampled from silicone.png — the soft-plastic family the swimbait joins.
SOFT_O = (0x6C, 0x60, 0x74, 255)
SOFT_D = (0xBA, 0xB4, 0xC8, 255)
SOFT = (0xE2, 0xDE, 0xEA, 255)
SOFT_L = (0xF8, 0xF6, 0xFC, 255)
# The worm's own pink-purple.
WORM_O = (0x5E, 0x3A, 0x6E, 255)
WORM_D = (0x9A, 0x4E, 0xA6, 255)
WORM = (0xC8, 0x74, 0xC4, 255)
WORM_L = (0xE6, 0xA4, 0xDC, 255)


def blank():
    return [[CLEAR] * W for _ in range(H)]


def put(g, x, y, c):
    if 0 <= x < W and 0 <= y < H:
        g[y][x] = c


def line(g, pts, c):
    for x, y in pts:
        put(g, x, y, c)


def body(g, y0, rows, outline, edge, mid, back):
    """A blade/body given as (x_start, width) per row, outlined all the way round.

    The outline is what makes a 16px lure read against gravel, water and a dark inventory alike — every
    existing lure has one. Highlight down the leading edge, shadow along the trailing one.
    """
    for i, (x0, w) in enumerate(rows):
        y = y0 + i
        put(g, x0 - 1, y, outline)
        put(g, x0 + w, y, outline)
        for x in range(x0, x0 + w):
            g[y][x] = edge if x <= x0 else (mid if x < x0 + w - 1 else back)
    # Cap the ends, or the shape leaks into the background top and bottom.
    for x in range(rows[0][0], rows[0][0] + rows[0][1]):
        put(g, x, y0 - 1, outline)
    for x in range(rows[-1][0], rows[-1][0] + rows[-1][1]):
        put(g, x, y0 + len(rows), outline)


def spinnerbait():
    """Wire arm: willow blade up on one leg, lead head and skirt down on the other."""
    g = blank()
    body(g, 1, [(11, 3), (10, 4), (10, 4), (11, 3)], OUTLINE, SHINE, STEEL_L, STEEL)
    # The bent wire — one pixel wide, because two reads as a rod at this size.
    line(g, [(9, 3), (8, 4), (7, 5), (6, 6), (5, 7), (4, 8), (5, 9)], STEEL_D)
    # Lead head at the foot of the lower arm.
    for i, (x0, w) in enumerate([(6, 3), (6, 4), (6, 4)]):
        y = 9 + i
        put(g, x0 - 1, y, OUTLINE)
        put(g, x0 + w, y, OUTLINE)
        for x in range(x0, x0 + w):
            g[y][x] = STEEL_L if x == x0 else LEAD
    put(g, 7, 10, EYE)
    # Skirt: uneven strand lengths are the whole reason it reads as a skirt and not a block.
    for k, ln in enumerate([2, 4, 3, 4, 3, 4, 2]):
        for j in range(ln):
            put(g, 5 + k, 12 + j, SKIRT_L if k % 3 == 0 else (SKIRT if j < ln - 1 else SKIRT_D))
    return g


def bladebait():
    """A flat vibrating blade: teardrop body, two tie holes along the back, treble underneath."""
    g = blank()
    body(g, 2, [(6, 4), (5, 6), (4, 8), (4, 8), (5, 6), (5, 6), (6, 4), (7, 2)],
         OUTLINE, SHINE, STEEL_L, STEEL)
    # The two holes are the item's signature — a bladebait is tied through one or the other, and which
    # one you pick is the whole "vertical or cast" choice the real bait offers.
    put(g, 6, 3, LEAD)
    put(g, 9, 3, LEAD)
    line(g, [(7, 10), (7, 11), (7, 12), (7, 13)], STEEL_D)          # centre shank + point
    line(g, [(6, 12), (5, 13), (5, 14), (8, 12), (9, 13), (9, 14)], STEEL_D)  # the two outer barbs
    return g


def swimbait():
    """Soft fish body with a paddle tail; the joint line is what separates it from the silicone worm."""
    g = blank()
    body(g, 4, [(3, 3), (2, 5), (1, 7), (1, 7), (2, 6), (3, 4)], SOFT_O, SOFT_L, SOFT, SOFT_D)
    put(g, 3, 6, SOFT_O)                                            # eye
    for y in range(5, 9):
        put(g, 5, y, SOFT_O)                                        # the joint — the swimbait's tell
    # Paddle tail: TALL and NARROW on a two-pixel wrist. A wide one reads as a second body, which is
    # exactly how the first draft came out.
    put(g, 9, 6, SOFT_D)
    put(g, 9, 7, SOFT_D)
    body(g, 3, [(12, 1), (11, 2), (11, 2), (11, 2), (11, 2), (11, 2), (12, 1)],
         SOFT_O, SOFT, SOFT, SOFT_D)
    put(g, 6, 3, STEEL_D)                                           # hook point out of the back —
    put(g, 6, 2, STEEL_D)                                           # without it this is just a fish
    return g


def wacky_worm():
    """A worm hooked dead centre, both ends hanging free — the whole point of the wacky rig."""
    g = blank()
    # A shallow V draped over the hook: (x_start, width) spans per row, both tails falling away.
    rows = [(5, [(6, 4)]), (6, [(4, 2), (10, 2)]), (7, [(2, 2), (12, 2)]),
            (8, [(2, 1), (13, 1)]), (9, [(1, 2), (13, 2)]), (10, [(1, 1), (14, 1)])]
    for y, spans in rows:
        for x0, w in spans:
            for x in range(x0, x0 + w):
                put(g, x, y, WORM_L if y <= 6 else (WORM if y <= 8 else WORM_D))
            put(g, x0 - 1, y, WORM_O)
            put(g, x0 + w, y, WORM_O)
    for x in range(6, 10):
        put(g, x, 4, WORM_O)                                        # cap the crest
    for x0, w in rows[-1][1]:
        for x in range(x0 - 1, x0 + w + 1):
            put(g, x, 11, WORM_O)                                   # cap the two tails
    # The hook, drawn last: it goes THROUGH the worm, so it has to win every shared pixel.
    line(g, [(7, 1), (8, 1), (6, 2), (9, 2), (7, 3), (8, 3),        # the eye
             (8, 4), (8, 5), (8, 6), (8, 7), (8, 8),                # shank, straight through the crest
             (9, 9), (10, 9), (11, 8), (11, 7), (12, 8)], STEEL_D)  # bend, point and barb
    return g


def png(path, rows):
    raw = b"".join(b"\x00" + b"".join(struct.pack("4B", *p) for p in row) for row in rows)

    def chunk(tag, data):
        c = tag + data
        return struct.pack(">I", len(data)) + c + struct.pack(">I", zlib.crc32(c) & 0xFFFFFFFF)

    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n")
        f.write(chunk(b"IHDR", struct.pack(">IIBBBBB", W, H, 8, 6, 0, 0, 0)))
        f.write(chunk(b"IDAT", zlib.compress(raw, 9)))
        f.write(chunk(b"IEND", b""))


REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(REPO, "common", "src", "main", "resources", "assets", "riverfishing",
                   "textures", "item")


def main():
    for name, fn in (("spinnerbait", spinnerbait), ("bladebait", bladebait),
                     ("swimbait", swimbait), ("wacky_worm", wacky_worm)):
        g = fn()
        png(os.path.join(OUT, name + ".png"), g)
        # The preview is the check: these are hand-placed pixels and nothing else will tell you that
        # the treble ended up inside the blade.
        print("\n%s  (%d opaque)" % (name, sum(1 for r in g for c in r if c[3])))
        for row in g:
            print("  " + "".join("." if c[3] == 0 else "#" for c in row))


if __name__ == "__main__":
    main()
