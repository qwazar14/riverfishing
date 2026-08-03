# -*- coding: utf-8 -*-
"""§order-panel: the sign that hangs over the fisherman's counter, drawn rather than painted.

    python tools/gen_order_panel.py

Writes assets/riverfishing/textures/gui/order_panel.png — 256x64, of which the top 48 rows are the sign.

It is generated because the thing is made of repetition: plank grain, a bevel on four rectangles, rope
coils, nail heads. Hand-painting that gets you one panel and no way to change the palette; a generator
gets you a palette at the top of the file and the same panel every time. The one rule the code keeps is
that every recess is lit the same way — dark on the top-left, light on the bottom-right — because a
bevel that disagrees with itself is what makes pixel art look wrong without anyone being able to say why.

The panel is CHROME ONLY. The fish, the emerald and the caption are drawn over it at runtime: the fish
has to be the species the server named, the emerald has to carry the real count, and the caption has to
be in the player's language. A texture cannot hold any of the three.
"""
import io, os, struct, zlib

W, H, SIGN_H = 256, 64, 48

# ---- the palette, straight off the reference: aged oak, hemp rope, brass, parchment.
CLEAR       = (0, 0, 0, 0)
WOOD_SHADOW = (0x21, 0x16, 0x0D, 0xFF)
WOOD_DARK   = (0x33, 0x22, 0x15, 0xFF)
WOOD_BASE   = (0x4A, 0x33, 0x1F, 0xFF)
WOOD_MID    = (0x5E, 0x42, 0x28, 0xFF)
WOOD_LIGHT  = (0x7A, 0x58, 0x36, 0xFF)
WOOD_HI     = (0x93, 0x6E, 0x46, 0xFF)
RECESS      = (0x1C, 0x12, 0x0A, 0xFF)
ROPE_DARK   = (0x7A, 0x60, 0x3C, 0xFF)
ROPE_MID    = (0xA8, 0x8A, 0x5C, 0xFF)
ROPE_LIGHT  = (0xCE, 0xB0, 0x80, 0xFF)
BRASS_DARK  = (0x8A, 0x6A, 0x1E, 0xFF)
BRASS       = (0xC9, 0xA2, 0x27, 0xFF)
BRASS_HI    = (0xE8, 0xCB, 0x6A, 0xFF)
PARCH_EDGE  = (0xA9, 0x8F, 0x63, 0xFF)
PARCH_LOW   = (0xD2, 0xC0, 0x97, 0xFF)
PARCH       = (0xE6, 0xD9, 0xB8, 0xFF)
PARCH_HI    = (0xF3, 0xEB, 0xD6, 0xFF)
NAIL        = (0x6B, 0x5A, 0x44, 0xFF)
NAIL_HI     = (0x9A, 0x86, 0x68, 0xFF)

px = [[CLEAR] * W for _ in range(H)]


def put(x, y, c):
    if 0 <= x < W and 0 <= y < H:
        px[y][x] = c


def rect(x0, y0, x1, y1, c):
    for y in range(y0, y1):
        for x in range(x0, x1):
            put(x, y, c)


def noise(x, y, salt=0):
    """A deterministic hash. Nothing here may depend on a random seed — the same source must always
    produce the same PNG, or a rebuild becomes a diff nobody can review."""
    n = (x * 374761393 + y * 668265263 + salt * 2147483647) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return (n ^ (n >> 16)) & 0xFF


def bevel(x0, y0, x1, y1, light, dark, inset=True):
    """One rectangle's edge lighting. Inset means the light comes from the top-left, so the top and left
    edges are DARK and the bottom and right are LIGHT — a hole. Flipped, the same call is a raised plate."""
    top, bottom = (dark, light) if inset else (light, dark)
    for x in range(x0, x1):
        put(x, y0, top)
        put(x, y1 - 1, bottom)
    for y in range(y0, y1):
        put(x0, y, top)
        put(x1 - 1, y, bottom)


# ---------------------------------------------------------------- the board: two planks, grain along x
#
# A plank is a CYLINDER, not a rectangle: light along its upper third, shadow along its lower edge. That
# vertical ramp is what the first attempt was missing — it had grain and no volume, so it read as noise
# on brown. Everything else here is detail hung on that ramp.
PLANK_H = SIGN_H // 2
RAMP = [WOOD_MID, WOOD_LIGHT, WOOD_HI, WOOD_LIGHT, WOOD_MID, WOOD_BASE, WOOD_BASE, WOOD_DARK]

for y in range(SIGN_H):
    plank, ly = divmod(y, PLANK_H)
    band = RAMP[min(len(RAMP) - 1, ly * len(RAMP) // PLANK_H)]
    for x in range(W):
        c = band
        # Grain: long horizontal runs, because that is the way a sawn plank shows its fibre. Each run is
        # one step darker or lighter than the ramp, never a colour of its own.
        g = noise(x // 11, ly, plank * 3 + 1)
        if g < 46:
            c = {WOOD_HI: WOOD_LIGHT, WOOD_LIGHT: WOOD_MID, WOOD_MID: WOOD_BASE,
                 WOOD_BASE: WOOD_DARK, WOOD_DARK: WOOD_SHADOW}[c]
        elif g > 218:
            c = {WOOD_SHADOW: WOOD_DARK, WOOD_DARK: WOOD_BASE, WOOD_BASE: WOOD_MID,
                 WOOD_MID: WOOD_LIGHT, WOOD_LIGHT: WOOD_HI, WOOD_HI: WOOD_HI}[c]
        # A vignette into both ends: the sign is lit from in front, so its corners fall away.
        edge = min(x, W - 1 - x)
        if edge < 10 and noise(x, y, 5) < (10 - edge) * 22:
            c = {WOOD_HI: WOOD_MID, WOOD_LIGHT: WOOD_BASE, WOOD_MID: WOOD_BASE,
                 WOOD_BASE: WOOD_DARK, WOOD_DARK: WOOD_SHADOW, WOOD_SHADOW: WOOD_SHADOW}[c]
        put(x, y, c)

# the seam: a shadow the upper plank casts, and the lit top edge of the one below it
rect(0, PLANK_H - 2, W, PLANK_H, WOOD_SHADOW)
rect(0, PLANK_H, W, PLANK_H + 1, WOOD_HI)

# knots, because perfectly clean timber is the thing that looks generated
for kx, ky in ((41, 8), (163, 33), (222, 11), (117, 40)):
    for dy in range(-4, 5):
        for dx in range(-5, 6):
            d = dx * dx * 0.45 + dy * dy * 1.5
            if d < 3:
                put(kx + dx, ky + dy, WOOD_SHADOW)
            elif d < 8:
                put(kx + dx, ky + dy, WOOD_DARK)
            elif d < 14 and noise(kx + dx, ky + dy, 13) < 150:
                put(kx + dx, ky + dy, WOOD_BASE)

# ---------------------------------------------------------------- the board's own frame
bevel(0, 0, W, SIGN_H, WOOD_HI, WOOD_SHADOW, inset=False)
bevel(1, 1, W - 1, SIGN_H - 1, WOOD_LIGHT, WOOD_DARK, inset=False)

# ---------------------------------------------------------------- left plaque: the trade row lives here
rect(15, 6, 88, SIGN_H - 6, WOOD_DARK)
for y in range(6, SIGN_H - 6):
    for x in range(15, 88):
        n = noise(x // 9, y, 3)
        if n < 70:
            put(x, y, WOOD_BASE)
        elif n > 210:
            put(x, y, WOOD_SHADOW)
bevel(15, 6, 88, SIGN_H - 6, WOOD_LIGHT, WOOD_SHADOW, inset=True)
bevel(16, 7, 87, SIGN_H - 7, WOOD_MID, WOOD_SHADOW, inset=True)

# two item wells, 18x18, holding a 16x16 stack each at +1,+1
for wx in (20, 62):
    rect(wx - 1, 14, wx + 19, 34, BRASS_DARK)
    bevel(wx - 1, 14, wx + 19, 34, BRASS_HI, BRASS_DARK, inset=False)
    rect(wx, 15, wx + 18, 33, RECESS)
    bevel(wx, 15, wx + 18, 33, BRASS, WOOD_SHADOW, inset=True)
    bevel(wx + 1, 16, wx + 17, 32, WOOD_SHADOW, RECESS, inset=True)

# the arrow between them, brass, lit from the top-left like everything else
ay = 23
for i in range(8):
    put(41 + i, ay, BRASS)
    put(41 + i, ay + 1, BRASS_DARK)
for i in range(5):
    put(49 + i, ay - 4 + i, BRASS_HI if i < 3 else BRASS)
    put(49 + i, ay + 5 - i, BRASS_DARK)
    for j in range(1, 5 - i):
        put(49 + i, ay - 3 + i + j, BRASS)

# ---------------------------------------------------------------- rope, lashed round both ends
def rope_wrap(x0):
    """Two coils with a visible twist, lashed round the end of the board. The twist is the whole trick:
    a rope drawn as flat bands is a ladder, and one drawn as diagonals is rope. It goes at the ENDS —
    binding is what holds a board together, and a band across the middle is just a stripe."""
    for dx, w in ((0, 4), (5, 4)):
        for y in range(1, SIGN_H - 1):
            for k in range(w):
                t = (y * 2 + k * 3) % 8
                c = ROPE_LIGHT if t < 2 else (ROPE_MID if t < 5 else ROPE_DARK)
                put(x0 + dx + k, y, c)
        for y in range(1, SIGN_H - 1):                      # the shadow the coil throws
            put(x0 + dx + w, y, WOOD_SHADOW)
        put(x0 + dx - 1, 1, ROPE_DARK)


rope_wrap(2)
rope_wrap(243)

# ---------------------------------------------------------------- right parchment: the caption goes here
PX0, PY0, PX1, PY1 = 96, 5, 240, SIGN_H - 5
# its cast shadow, first, so the sheet sits ON the board rather than in it
rect(PX0 + 2, PY1, PX1 + 2, PY1 + 2, WOOD_SHADOW)
for y in range(PY0 + 2, PY1 + 2):
    put(PX1, y, WOOD_SHADOW)
    put(PX1 + 1, y, WOOD_SHADOW)

rect(PX0, PY0, PX1, PY1, PARCH)
cx, cy = (PX0 + PX1) / 2.0, (PY0 + PY1) / 2.0
for y in range(PY0, PY1):
    for x in range(PX0, PX1):
        # A sheet is brightest where it bulges and dirtiest at its edges; the falloff does most of the work.
        d = ((x - cx) / (PX1 - PX0) * 2) ** 2 + ((y - cy) / (PY1 - PY0) * 2) ** 2
        n = noise(x, y, 7)
        if d > 0.82 or n < 26:
            c = PARCH_LOW
        elif d < 0.20 and n > 150:
            c = PARCH_HI
        else:
            c = PARCH
        # Foxing — the brown blooms of old paper. Sparse and SOFT: a bloom is a low-frequency blob with
        # a ragged rim, and the first pass had it as one-pixel specks on a grid, which reads as dirt.
        b = noise(x // 14, y // 9, 17)
        if b < 24:
            edge = noise(x // 3, y // 2, 19)
            c = PARCH_EDGE if edge < 90 else PARCH_LOW
        put(x, y, c)

for x in range(PX0, PX1):
    put(x, PY0, PARCH_EDGE)
    put(x, PY1 - 1, PARCH_EDGE)
for y in range(PY0, PY1):
    put(PX0, y, PARCH_EDGE)
    put(PX1 - 1, y, PARCH_EDGE)
# torn corners — a bite out of each, which is what stops it reading as a rectangle
for cx2, cy2, sx, sy in ((PX0, PY0, 1, 1), (PX1 - 1, PY0, -1, 1),
                         (PX0, PY1 - 1, 1, -1), (PX1 - 1, PY1 - 1, -1, -1)):
    put(cx2, cy2, CLEAR)
    put(cx2 + sx, cy2, PARCH_EDGE)
    put(cx2, cy2 + sy, PARCH_EDGE)

# four nails holding it to the board
for nx, ny in ((PX0 + 5, PY0 + 5), (PX1 - 6, PY0 + 5), (PX0 + 5, PY1 - 6), (PX1 - 6, PY1 - 6)):
    for dy in range(-1, 2):
        for dx in range(-1, 2):
            put(nx + dx, ny + dy, NAIL)
    put(nx - 1, ny - 1, NAIL_HI)
    put(nx, ny - 1, NAIL_HI)
    put(nx + 1, ny + 1, WOOD_SHADOW)
    put(nx + 2, ny + 2, PARCH_EDGE)

# ---------------------------------------------------------------- PNG, no dependencies
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
                   "textures", "gui", "order_panel.png")
png(OUT, px)
print("%s  %dx%d" % (OUT, W, H))
# These three are the contract with the renderer. They are PRINTED rather than left in a comment because
# the panel moves when the art moves, and a coordinate copied by hand into Java is one that goes stale.
print("  sign occupies the top %d rows; the two 16x16 stacks go at (21,16) and (63,16)" % SIGN_H)
print("  parchment text area: x %d..%d, y %d..%d" % (PX0 + 8, PX1 - 8, PY0 + 4, PY1 - 4))
