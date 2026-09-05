# -*- coding: utf-8 -*-
"""§breeding: a clutch of roe out of the aquarium, as a 16x16 item icon.

    py tools/gen_roe_icon.py

A pale, slightly rounded patch of ground with a cluster of orange-red eggs on it, each with one bright
highlight and a darker rim so they read as spheres at 16 px. Generated like the contract icon — one
palette, one file, the same pixels every time.
"""
import os, struct, zlib

W = H = 16
CLEAR    = (0, 0, 0, 0)
GROUND   = (0xE8, 0xE0, 0xCC, 0xFF)
GROUND_D = (0xC9, 0xBE, 0xA4, 0xFF)
GROUND_O = (0x8C, 0x7E, 0x62, 0xFF)
EGG      = (0xE8, 0x5A, 0x22, 0xFF)
EGG_D    = (0xA8, 0x30, 0x12, 0xFF)
EGG_HI   = (0xFF, 0xB2, 0x70, 0xFF)

px = [[CLEAR] * W for _ in range(H)]


def put(x, y, c):
    if 0 <= x < W and 0 <= y < H:
        px[y][x] = c


def rect(x0, y0, x1, y1, c):
    for y in range(y0, y1):
        for x in range(x0, x1):
            put(x, y, c)


# the ground: a pale rounded patch with an outline so it reads on any background
rect(2, 3, 14, 14, GROUND)
for x in range(2, 14):
    put(x, 13, GROUND_D)
for x in range(3, 13):
    put(x, 2, GROUND_O); put(x, 14, GROUND_O)
for y in range(3, 14):
    put(1, y, GROUND_O); put(14, y, GROUND_O)
put(2, 3, GROUND_O); put(13, 3, GROUND_O); put(2, 13, GROUND_O); put(13, 13, GROUND_O)


def egg(x, y):
    """A 3x3 sphere with its top-left corner at (x, y): dark rim, bright body, one highlight."""
    rect(x, y, x + 3, y + 3, EGG_D)
    put(x + 1, y, EGG); put(x, y + 1, EGG); put(x + 1, y + 1, EGG)
    put(x + 1, y + 1, EGG_HI)


# the cluster: seven eggs packed the way they lie, back row first so the front ones overlap
for x, y in ((4, 4), (8, 4), (6, 6), (10, 7), (3, 8), (7, 9), (5, 10)):
    egg(x, y)


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
                   "textures", "item", "roe.png")
png(OUT, px)
print("%s  %dx%d" % (OUT, W, H))
