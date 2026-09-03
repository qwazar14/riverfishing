# -*- coding: utf-8 -*-
"""§j: fish meal — a heap of ground small fish, as a 16x16 item icon.

    py tools/gen_fish_meal_icon.py

A sandy-brown mound with darker flecks (the bits that did not grind), lit from the top-left, on a
one-pixel ink rim so it reads on any ground. Generated like the contract icon — one palette, one file,
the same pixels every time. The mound colour is the pantry's 0xC8A870, so the jar stains the way the
icon looks.
"""
import os, struct, zlib

W = H = 16
CLEAR    = (0, 0, 0, 0)
MEAL     = (0xC8, 0xA8, 0x70, 0xFF)
MEAL_HI  = (0xE2, 0xC6, 0x90, 0xFF)
MEAL_DK  = (0xA6, 0x86, 0x54, 0xFF)
FLECK    = (0x6E, 0x54, 0x36, 0xFF)
RIM      = (0x4A, 0x36, 0x20, 0xFF)

px = [[CLEAR] * W for _ in range(H)]


def put(x, y, c):
    if 0 <= x < W and 0 <= y < H:
        px[y][x] = c


# the mound: each row is (first x, one past last x); wider the lower it sits
SPAN = {5: (7, 9), 6: (6, 10), 7: (5, 11), 8: (4, 12), 9: (3, 13),
        10: (2, 14), 11: (2, 14), 12: (1, 15), 13: (1, 15)}
# rim first, then the fill over it, so the dark edge is exactly one pixel everywhere
for y, (x0, x1) in SPAN.items():
    for x in range(x0 - 1, x1 + 1):
        put(x, y, RIM)
for x in range(6, 10):
    put(x, 4, RIM)
for x in range(0, 16):
    put(x, 14, RIM)
for y, (x0, x1) in SPAN.items():
    for x in range(x0, x1):
        put(x, y, MEAL)
# light from the top-left, shadow at the bottom-right
for y, (x0, x1) in SPAN.items():
    put(x0, y, MEAL_HI)
    if y < 9:
        put(x0 + 1, y, MEAL_HI)
    put(x1 - 1, y, MEAL_DK)
    if y > 10:
        put(x1 - 2, y, MEAL_DK)
# flecks: the bones and scales that did not grind
for x, y in ((6, 8), (9, 7), (4, 11), (8, 10), (11, 12), (7, 13), (12, 10), (10, 13)):
    put(x, y, FLECK)


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
                   "textures", "item", "fish_meal.png")
png(OUT, px)
print("%s  %dx%d" % (OUT, W, H))
