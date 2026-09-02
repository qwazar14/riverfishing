# -*- coding: utf-8 -*-
"""§breeding: the cast net as a 16x16 item icon.

    py tools/gen_cast_net_icon.py

A round net seen from above: mesh disc, lead weights around the rim, the hand line coiled at the
centre. Same raw-PNG writer as gen_contract_icon.py.
"""
import os, struct, zlib

W = H = 16
CLEAR    = (0, 0, 0, 0)
MESH     = (0x7A, 0x8C, 0x6E, 0xFF)
MESH_D   = (0x4E, 0x5C, 0x44, 0xFF)
MESH_HI  = (0xA4, 0xB4, 0x94, 0xFF)
ROPE     = (0xB8, 0x8E, 0x4E, 0xFF)
LEAD     = (0x5A, 0x5E, 0x66, 0xFF)
LEAD_HI  = (0x8A, 0x90, 0x9A, 0xFF)
OUTLINE  = (0x2E, 0x34, 0x2A, 0xFF)

px = [[CLEAR] * W for _ in range(H)]


def put(x, y, c):
    if 0 <= x < W and 0 <= y < H:
        px[y][x] = c


cx = cy = 7.5
# the disc: mesh inside radius 6.5, outline ring at the rim
for y in range(H):
    for x in range(W):
        d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
        if d <= 5.6:
            put(x, y, MESH_D if (x + y) % 3 == 0 else MESH)
        elif d <= 6.6:
            put(x, y, OUTLINE)
# a highlight on the upper-left quarter so it reads as a curved thing
for x, y in ((5, 3), (6, 3), (4, 4), (3, 5), (3, 6)):
    put(x, y, MESH_HI)
# lead weights around the rim, eight of them
for x, y in ((7, 1), (12, 3), (14, 8), (12, 12), (7, 14), (2, 12), (1, 7), (3, 3)):
    put(x, y, LEAD); put(x + 1, y, LEAD_HI)
# the hand line coiled at the centre
for x, y in ((7, 7), (8, 7), (7, 8), (8, 8), (9, 6), (6, 9)):
    put(x, y, ROPE)


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
                   "textures", "item", "cast_net.png")
png(OUT, px)
print("%s  %dx%d" % (OUT, W, H))
