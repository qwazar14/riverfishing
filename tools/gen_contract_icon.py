# -*- coding: utf-8 -*-
"""§contracts: the poster you take off the fisherman's board, as a 16x16 item icon.

    py tools/gen_contract_icon.py

A rolled sheet of parchment with a wax seal and a ribbon: the same palette the order panel and the
cast gauge use, so it is obviously the fisherman's paperwork. Generated for the same reason they are —
one palette, one file, the same pixels every time.
"""
import os, struct, zlib

W = H = 16
CLEAR = (0, 0, 0, 0)
PARCH      = (0xE3, 0xD6, 0xB8, 0xFF)
PARCH_HI   = (0xF3, 0xEB, 0xD6, 0xFF)
PARCH_DARK = (0xC4, 0xB2, 0x8C, 0xFF)
INK        = (0x3A, 0x2A, 0x18, 0xFF)
INK_LIGHT  = (0x6E, 0x5A, 0x3C, 0xFF)
WAX        = (0xA0, 0x2A, 0x22, 0xFF)
WAX_HI     = (0xD0, 0x4A, 0x3A, 0xFF)
RIBBON     = (0xB0, 0x8D, 0x3C, 0xFF)
RIBBON_D   = (0x6E, 0x54, 0x22, 0xFF)

px = [[CLEAR] * W for _ in range(H)]


def put(x, y, c):
    if 0 <= x < W and 0 <= y < H:
        px[y][x] = c


def rect(x0, y0, x1, y1, c):
    for y in range(y0, y1):
        for x in range(x0, x1):
            put(x, y, c)


# the sheet, slightly rolled at the top-left and bottom-right corners
rect(3, 1, 14, 15, PARCH)
for x in range(3, 14):
    put(x, 1, PARCH_HI)
    put(x, 14, PARCH_DARK)
for y in range(1, 15):
    put(3, y, PARCH_HI)
    put(13, y, PARCH_DARK)
put(3, 1, CLEAR); put(13, 14, CLEAR)
put(2, 2, PARCH_DARK); put(2, 3, PARCH_DARK); put(14, 12, PARCH_DARK); put(14, 13, PARCH_DARK)
# an outline so it reads on any ground
for x in range(3, 14):
    put(x, 0, INK_LIGHT); put(x, 15, INK_LIGHT)
for y in range(1, 15):
    put(2, y, INK_LIGHT) if y > 3 else None
    put(14, y, INK_LIGHT) if y < 12 else None
# lines of writing
for y, w in ((4, 7), (6, 8), (8, 6), (10, 7)):
    for x in range(5, 5 + w):
        put(x, y, INK if (x + y) % 3 else INK_LIGHT)
# the wax seal, bottom right, with a ribbon tail
rect(9, 10, 13, 14, WAX)
put(9, 10, CLEAR); put(12, 10, CLEAR); put(9, 13, CLEAR); put(12, 13, CLEAR)
put(10, 11, WAX_HI)
put(8, 12, RIBBON); put(7, 13, RIBBON); put(6, 14, RIBBON_D)


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
                   "textures", "item", "contract.png")
png(OUT, px)
print("%s  %dx%d" % (OUT, W, H))
