# -*- coding: utf-8 -*-
"""§breeding: hatched fry, as a 16x16 item icon.

    py tools/gen_fry_icon.py

Three tiny silver fish, staggered the way a shoal hangs in the water: a light belly, a darker back, one
black eye and a forked tail each. Generated like the contract icon — one palette, one file, the same
pixels every time.
"""
import os, struct, zlib

W = H = 16
CLEAR   = (0, 0, 0, 0)
SILVER  = (0xD4, 0xDC, 0xE4, 0xFF)
BELLY   = (0xF2, 0xF6, 0xFA, 0xFF)
BACK    = (0x7C, 0x8C, 0x9C, 0xFF)
OUTLINE = (0x3C, 0x48, 0x54, 0xFF)
EYE     = (0x14, 0x18, 0x1C, 0xFF)

px = [[CLEAR] * W for _ in range(H)]


def put(x, y, c):
    if 0 <= x < W and 0 <= y < H:
        px[y][x] = c


def fish(x, y):
    """One fry, 7 wide x 3 tall, nose at (x, y+1), tail to the right; the body sits on rows y..y+2."""
    # outline first, then the body over it, so the dark rim is one pixel everywhere
    for dx in range(0, 5):
        put(x + dx, y, OUTLINE); put(x + dx, y + 2, OUTLINE)
    put(x - 1, y + 1, OUTLINE)
    put(x + 5, y, OUTLINE); put(x + 5, y + 2, OUTLINE); put(x + 6, y, OUTLINE); put(x + 6, y + 2, OUTLINE)
    put(x + 5, y + 1, OUTLINE)
    # body: back dark, belly light
    for dx in range(0, 5):
        put(x + dx, y, BACK)
        put(x + dx, y + 1, SILVER)
    for dx in range(1, 4):
        put(x + dx, y + 2, BELLY)
    put(x, y + 2, SILVER); put(x + 4, y + 2, SILVER)
    # forked tail
    put(x + 5, y + 1, SILVER); put(x + 6, y, SILVER); put(x + 6, y + 2, SILVER)
    # eye
    put(x + 1, y + 1, EYE)


fish(2, 2)
fish(7, 6)
fish(3, 10)


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
                   "textures", "item", "fry.png")
png(OUT, px)
print("%s  %dx%d" % (OUT, W, H))
