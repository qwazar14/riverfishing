# -*- coding: utf-8 -*-
"""§j: fish oil — a corked glass bottle of amber oil, as a 16x16 item icon.

    py tools/gen_fish_oil_icon.py

Glass walls, a cork, amber oil to the shoulder with a highlight down the left side and a darker
meniscus. Generated like the contract icon — one palette, one file, the same pixels every time. The
oil is the pantry's 0xD89A30, so the jar stains the way the icon looks.
"""
import os, struct, zlib

W = H = 16
CLEAR    = (0, 0, 0, 0)
GLASS    = (0x5A, 0x6E, 0x78, 0xFF)
GLASS_HI = (0xC8, 0xDC, 0xE4, 0xFF)
OIL      = (0xD8, 0x9A, 0x30, 0xFF)
OIL_HI   = (0xF0, 0xBE, 0x5A, 0xFF)
OIL_DK   = (0xB0, 0x76, 0x1E, 0xFF)
CORK     = (0xA8, 0x7A, 0x48, 0xFF)
CORK_DK  = (0x74, 0x50, 0x2C, 0xFF)

px = [[CLEAR] * W for _ in range(H)]


def put(x, y, c):
    if 0 <= x < W and 0 <= y < H:
        px[y][x] = c


def rect(x0, y0, x1, y1, c):
    for y in range(y0, y1):
        for x in range(x0, x1):
            put(x, y, c)


# cork
rect(6, 0, 10, 3, CORK)
for y in range(0, 3):
    put(9, y, CORK_DK)
# neck: two glass walls, air between
for y in range(3, 6):
    put(5, y, GLASS); put(10, y, GLASS)
    put(6, y, GLASS_HI)
# shoulders
put(4, 6, GLASS); put(11, 6, GLASS)
put(3, 7, GLASS); put(12, 7, GLASS)
put(5, 6, GLASS_HI)
# body: oil to the shoulder, a light streak on the left, shadow on the right
for y in range(8, 15):
    put(2, y, GLASS); put(13, y, GLASS)
    put(3, y, OIL_HI)
    for x in range(4, 12):
        put(x, y, OIL)
    put(12, y, OIL_DK)
for x in range(3, 13):
    put(x, 15, GLASS)
for y in (9, 10, 11):
    put(3, y, GLASS_HI)
# the meniscus, dark at the glass and bright in the middle
for x in range(4, 12):
    put(x, 7, OIL_DK if x in (4, 11) else OIL_HI)


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
                   "textures", "item", "fish_oil.png")
png(OUT, px)
print("%s  %dx%d" % (OUT, W, H))
