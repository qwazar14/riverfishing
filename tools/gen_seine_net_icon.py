# -*- coding: utf-8 -*-
"""§breeding: the seine net as a 16x16 item icon.

    py tools/gen_seine_net_icon.py

A long net rolled up for carrying: a float line of corks along the top, the mesh below, a lead line
underneath. Same raw-PNG writer and the same reason as gen_contract_icon.py — one script, the same
pixels every time.
"""
import os, struct, zlib

W = H = 16
CLEAR    = (0, 0, 0, 0)
MESH     = (0x7A, 0x8C, 0x6E, 0xFF)
MESH_D   = (0x4E, 0x5C, 0x44, 0xFF)
MESH_HI  = (0xA4, 0xB4, 0x94, 0xFF)
ROPE     = (0xB8, 0x8E, 0x4E, 0xFF)
ROPE_D   = (0x7C, 0x5A, 0x2A, 0xFF)
CORK     = (0xE0, 0xC4, 0x86, 0xFF)
CORK_D   = (0xA8, 0x86, 0x4A, 0xFF)
LEAD     = (0x5A, 0x5E, 0x66, 0xFF)
OUTLINE  = (0x2E, 0x34, 0x2A, 0xFF)

px = [[CLEAR] * W for _ in range(H)]


def put(x, y, c):
    if 0 <= x < W and 0 <= y < H:
        px[y][x] = c


# the mesh body: a slanted roll, dark diagonals for the knots
for y in range(4, 14):
    for x in range(1, 15):
        put(x, y, MESH_D if (x + y) % 3 == 0 else MESH)
for x in range(1, 15):
    put(x, 4, MESH_HI)
# outline so it reads on any ground
for x in range(1, 15):
    put(x, 3, OUTLINE); put(x, 14, OUTLINE)
for y in range(3, 15):
    put(0, y, OUTLINE); put(15, y, OUTLINE)
# float line: rope along the top with corks sitting on it
for x in range(1, 15):
    put(x, 2, ROPE if x % 2 else ROPE_D)
for x in (2, 6, 10, 13):
    put(x, 1, CORK); put(x + 1, 1, CORK_D)
    put(x, 0, CORK_D)
# lead line: the weighted bottom rope
for x in range(1, 15):
    put(x, 13, LEAD if x % 3 else ROPE_D)
for x in (3, 8, 12):
    put(x, 15, LEAD)


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
                   "textures", "item", "seine_net.png")
png(OUT, px)
print("%s  %dx%d" % (OUT, W, H))
