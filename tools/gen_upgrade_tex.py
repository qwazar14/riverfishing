# -*- coding: utf-8 -*-
"""§g §breeding: the SNAG PILE, as a 16x16 cube_all texture.

    py -X utf8 tools/gen_upgrade_tex.py [root]

snag_pile        tangled brown logs, the kind of cover a pike sits under

This drew all five water-body upgrades once. Four of them have since been redrawn properly — each with
its own generator, its own palette sampled from the vanilla blocks its recipe uses, and in three cases
its own top/side/bottom instead of one texture wrapped around a cube:

    tools/gen_aerator.py          aerator_{side,top,bottom}
    tools/gen_gravel_bed.py       gravel_bed
    tools/gen_warm_outflow.py     warm_outflow_{side,top,bottom}
    tools/gen_feeding_station.py  feeding_station_{side,top}0..3 + _bottom, and the models for them

Their functions are gone from here rather than left to rot: a generator that still knows how to write a
texture nothing reads is a loaded gun, and re-running this file would have quietly put four dead
cube_all sheets back over the new art. The snag pile is the one that has not been redrawn yet, so it is
the one that is left.

Same raw zlib PNG writer as tools/gen_contract_icon.py — one palette, one file, the same pixels every
time, and no image library to install on a machine that only has Python.
"""
import os, struct, zlib

W = H = 16
CLEAR = (0, 0, 0, 0)


def rgb(h):
    return ((h >> 16) & 0xFF, (h >> 8) & 0xFF, h & 0xFF, 0xFF)


class Canvas:
    def __init__(self, fill):
        self.px = [[fill] * W for _ in range(H)]

    def put(self, x, y, c):
        if 0 <= x < W and 0 <= y < H:
            self.px[y][x] = c

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.put(x, y, c)

    def hatch(self, x0, y0, x1, y1, a, b, period=3):
        """Two colours alternating on a diagonal — cheap texture for planks, stone, and metal."""
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.put(x, y, a if (x + y) % period else b)


def snag_pile():
    bark, bark_dk, bark_hi, wood = rgb(0x6B4A2B), rgb(0x3F2A16), rgb(0x8E6740), rgb(0xB48A5A)
    mud = rgb(0x4A3B2A)
    c = Canvas(mud)
    c.hatch(0, 0, 16, 16, mud, bark_dk, 5)
    # three logs crossing — one horizontal, two diagonal
    c.rect(0, 6, 16, 10, bark)
    c.rect(0, 6, 16, 7, bark_hi); c.rect(0, 9, 16, 10, bark_dk)
    for i in range(16):
        y = 2 + i * 12 // 16
        c.put(i, y, bark); c.put(i, y + 1, bark_dk)
        c.put(i, 13 - i * 10 // 16, bark_hi); c.put(i, 14 - i * 10 // 16, bark)
    # cut ends: a pale ring on the log faces
    for x, y in ((1, 7), (14, 8)):
        c.put(x, y, wood); c.put(x, y + 1, bark_hi)
    # twigs
    for x, y in ((3, 1), (12, 1), (5, 15), (11, 14)):
        c.put(x, y, bark_hi)
    return c


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


import sys
REPO = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(REPO, "common", "src", "main", "resources", "assets", "riverfishing", "textures", "block")

if __name__ == "__main__":
    for name, draw in (("snag_pile", snag_pile),):
        c = draw()
        assert all(p != CLEAR for row in c.px for p in row), name + ": a cube texture must be opaque"
        path = os.path.join(OUT, name + ".png")
        png(path, c.px)
        print("%s  %dx%d" % (path, W, H))
