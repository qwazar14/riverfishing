# -*- coding: utf-8 -*-
"""§cast-bar: the cast-power gauge, drawn rather than painted.

    py tools/gen_cast_bar.py

Writes assets/riverfishing/textures/gui/cast_bar.png, a 128x48 sheet:

    frame   (0,0)   120x16   oak plank frame, brass-rimmed recess 112x8 at (4,4), nails, rope ends
    fill    (0,16)  112x8    the charge, green through amber to red, lit like a glass tube
    hatch   (0,24)  8x8      the dead band an under-loaded rig cannot reach, tiled
    plaque  (0,32)  48x16    the plate the metres are printed on

Generated for the same reason the order panel is: the thing is made of repetition — plank ramp, bevel,
nail heads — and a palette at the top of the file beats a PNG nobody can re-tint. Same lighting rule:
every recess is dark on the top-left and light on the bottom-right, every plate the reverse.

The sheet is CHROME ONLY. The fill is clipped to the charge at runtime, the hatch is tiled over the dead
band, and the metres are text: the number has to be the real throw, which a texture cannot hold.
"""
import os, struct, zlib

W, H = 128, 48

CLEAR       = (0, 0, 0, 0)
WOOD_SHADOW = (0x21, 0x16, 0x0D, 0xFF)
WOOD_DARK   = (0x33, 0x22, 0x15, 0xFF)
WOOD_BASE   = (0x5C, 0x3F, 0x26, 0xFF)
WOOD_MID    = (0x74, 0x50, 0x30, 0xFF)
WOOD_LIGHT  = (0x8E, 0x66, 0x3E, 0xFF)
WOOD_HI     = (0xA8, 0x7E, 0x50, 0xFF)
BRASS_DARK  = (0x6E, 0x54, 0x22, 0xFF)
BRASS       = (0xB0, 0x8D, 0x3C, 0xFF)
BRASS_HI    = (0xE0, 0xC0, 0x6A, 0xFF)
TRACK       = (0x14, 0x10, 0x0C, 0xFF)
TRACK_DEEP  = (0x0B, 0x08, 0x06, 0xFF)
NAIL        = (0x6B, 0x5A, 0x44, 0xFF)
NAIL_HI     = (0x9A, 0x86, 0x68, 0xFF)
ROPE        = (0xB9, 0x9A, 0x62, 0xFF)
ROPE_DARK   = (0x7E, 0x64, 0x3B, 0xFF)
PARCH       = (0xE3, 0xD6, 0xB8, 0xFF)
PARCH_DARK  = (0xC4, 0xB2, 0x8C, 0xFF)
PARCH_HI    = (0xF3, 0xEB, 0xD6, 0xFF)

px = [[CLEAR] * W for _ in range(H)]


def put(x, y, c):
    if 0 <= x < W and 0 <= y < H:
        px[y][x] = c


def rect(x0, y0, x1, y1, c):
    for y in range(y0, y1):
        for x in range(x0, x1):
            put(x, y, c)


def noise(x, y, salt=0):
    n = (x * 374761393 + y * 668265263 + salt * 2147483647) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return (n ^ (n >> 16)) & 0xFF


def bevel(x0, y0, x1, y1, light, dark, inset=True):
    top, bottom = (dark, light) if inset else (light, dark)
    for x in range(x0, x1):
        put(x, y0, top)
        put(x, y1 - 1, bottom)
    for y in range(y0, y1):
        put(x0, y, top)
        put(x1 - 1, y, bottom)


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))


# ---------------------------------------------------------------- the frame, 120x16 at (0,0)
FX, FY, FW, FH = 0, 0, 120, 16
RAMP = [WOOD_LIGHT, WOOD_HI, WOOD_LIGHT, WOOD_MID, WOOD_BASE, WOOD_DARK]
for y in range(FY, FY + FH):
    band = RAMP[min(len(RAMP) - 1, (y - FY) * len(RAMP) // FH)]
    for x in range(FX, FX + FW):
        c = band
        if noise(x // 7, y, 3) < 40:
            c = {WOOD_HI: WOOD_LIGHT, WOOD_LIGHT: WOOD_MID, WOOD_MID: WOOD_BASE,
                 WOOD_BASE: WOOD_DARK, WOOD_DARK: WOOD_SHADOW}.get(c, c)
        put(x, y, c)
bevel(FX, FY, FX + FW, FY + FH, WOOD_HI, WOOD_SHADOW, inset=False)     # the plank is raised

# the recess the charge sits in: brass rim, then a dark track lit as a hole
RX0, RY0, RX1, RY1 = 4, 4, 116, 12
rect(RX0 - 1, RY0 - 1, RX1 + 1, RY1 + 1, BRASS)
put(RX0 - 1, RY0 - 1, BRASS_HI)
put(RX1, RY1, BRASS_DARK)
rect(RX0, RY0, RX1, RY1, TRACK)
bevel(RX0, RY0, RX1, RY1, WOOD_MID, TRACK_DEEP, inset=True)

# nails in the four corners of the plank, rope wraps at the ends
for nx, ny in ((2, 2), (FW - 3, 2), (2, FH - 3), (FW - 3, FH - 3)):
    put(nx, ny, NAIL)
    put(nx, ny - 1, NAIL_HI)
for rx in (RX0 - 3, RX1 + 1):
    for y in range(FY + 1, FY + FH - 1):
        put(rx, y, ROPE if (y % 2 == 0) else ROPE_DARK)

# ---------------------------------------------------------------- the fill, 112x8 at (0,16)
GREEN = (0x62, 0xB0, 0x3A, 0xFF)
AMBER = (0xF0, 0xC0, 0x3C, 0xFF)
RED   = (0xE0, 0x52, 0x40, 0xFF)
for x in range(112):
    t = x / 111.0
    c = lerp(GREEN, AMBER, t / 0.55) if t < 0.55 else lerp(AMBER, RED, (t - 0.55) / 0.45)
    hi = lerp(c, (0xFF, 0xFF, 0xFF, 0xFF), 0.45)
    lo = lerp(c, (0, 0, 0, 0xFF), 0.35)
    for y in range(8):
        put(x, 16 + y, hi if y == 0 else (lo if y == 7 else (lerp(c, hi, 0.2) if y == 1 else c)))

# ---------------------------------------------------------------- the hatch, 8x8 at (0,24)
for y in range(8):
    for x in range(8):
        put(x, 24 + y, (0xB0, 0x30, 0x30, 0xB0) if (x + y) % 4 == 0 else (0x40, 0x10, 0x10, 0x70))

# ---------------------------------------------------------------- the plaque, 48x16 at (0,32)
rect(0, 32, 48, 48, PARCH)
for y in range(32, 48):
    for x in range(48):
        if noise(x, y, 9) < 28:
            put(x, y, PARCH_DARK)
bevel(0, 32, 48, 48, PARCH_HI, PARCH_DARK, inset=False)
bevel(-1, 31, 49, 49, BRASS_HI, BRASS_DARK, inset=False)
rect(0, 31, 48, 32, BRASS)
rect(0, 47, 48, 48, BRASS_DARK)


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
                   "textures", "gui", "cast_bar.png")
png(OUT, px)
print("%s  %dx%d" % (OUT, W, H))
print("  frame 120x16 at (0,0); recess 112x8 at (4,4)  <- the fill's home")
print("  fill 112x8 at (0,16); hatch 8x8 at (0,24); plaque 48x16 at (0,32)")
