# -*- coding: utf-8 -*-
"""§g §breeding: the five water-body upgrade blocks, as 16x16 cube_all textures.

    py tools/gen_upgrade_tex.py

aerator          a grey steel box with a blue bubble column rising out of it
snag_pile        tangled brown logs, the kind of cover a pike sits under
gravel_bed       grey-beige stones, the bed a trout wants to spawn on
warm_outflow     a copper pipe with a red glow at its mouth
feeding_station  a plank box with a hopper mouth on top

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


def aerator():
    steel, steel_hi, steel_dk = rgb(0x8A8F94), rgb(0xB4B9BE), rgb(0x565B60)
    water, bubble, bubble_hi = rgb(0x2E6BC4), rgb(0x8FC6F2), rgb(0xE6F6FF)
    c = Canvas(steel)
    c.hatch(0, 0, 16, 16, steel, steel_dk, 4)
    c.rect(0, 0, 16, 1, steel_hi); c.rect(0, 0, 1, 16, steel_hi)
    c.rect(0, 15, 16, 16, steel_dk); c.rect(15, 0, 16, 16, steel_dk)
    # rivets in the corners
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        c.put(x, y, steel_dk); c.put(x + 1, y, steel_hi)
    # the bubble column up the middle, widening as it rises
    c.rect(6, 4, 10, 15, water)
    for x, y in ((7, 13), (8, 11), (6, 9), (9, 8), (7, 6), (8, 4), (6, 12), (9, 5)):
        c.put(x, y, bubble)
    for x, y in ((8, 10), (7, 5), (8, 13)):
        c.put(x, y, bubble_hi)
    c.rect(5, 14, 11, 15, steel_dk)          # the grille the air comes out of
    return c


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


def gravel_bed():
    base, stone_a, stone_b, stone_c, dark = rgb(0x8D8477), rgb(0xB5AC9B), rgb(0xA69C88), rgb(0xC9C0AD), rgb(0x5E574D)
    c = Canvas(base)
    stones = ((0, 0, 3, 2, stone_a), (4, 0, 7, 3, stone_b), (8, 1, 12, 3, stone_c), (13, 0, 16, 2, stone_a),
              (1, 3, 4, 6, stone_c), (5, 4, 9, 6, stone_a), (10, 4, 13, 7, stone_b), (14, 3, 16, 6, stone_c),
              (0, 7, 3, 9, stone_b), (3, 7, 7, 10, stone_c), (8, 8, 11, 10, stone_a), (12, 8, 16, 10, stone_b),
              (1, 11, 5, 13, stone_a), (6, 11, 9, 14, stone_b), (10, 11, 14, 13, stone_c), (15, 11, 16, 14, stone_a),
              (0, 14, 4, 16, stone_c), (5, 15, 8, 16, stone_a), (9, 14, 13, 16, stone_b), (14, 15, 16, 16, stone_c))
    for x0, y0, x1, y1, col in stones:
        c.rect(x0, y0, x1, y1, col)
        c.rect(x0, y1 - 1, x1, y1, dark)     # every stone throws a shadow down
    return c


def warm_outflow():
    copper, copper_hi, copper_dk, patina = rgb(0xB8672F), rgb(0xE0905A), rgb(0x7A4220), rgb(0x5C8A6A)
    stone = rgb(0x6E6E6E)
    glow, glow_hi, glow_core = rgb(0xC63A1E), rgb(0xFF7A3C), rgb(0xFFD070)
    c = Canvas(stone)
    c.hatch(0, 0, 16, 16, stone, rgb(0x5A5A5A), 4)
    # the pipe: a vertical copper tube down the middle with a flange near the top
    c.rect(5, 0, 11, 12, copper)
    c.rect(5, 0, 6, 12, copper_hi); c.rect(10, 0, 11, 12, copper_dk)
    c.rect(4, 2, 12, 4, copper); c.rect(4, 2, 12, 3, copper_hi); c.rect(4, 3, 12, 4, copper_dk)
    for x, y in ((6, 6), (9, 9), (7, 10)):
        c.put(x, y, patina)
    # the mouth, glowing
    c.rect(3, 11, 13, 16, glow)
    c.rect(4, 12, 12, 15, glow_hi)
    c.rect(6, 13, 10, 15, glow_core)
    c.put(3, 11, stone); c.put(12, 11, stone)
    return c


def feeding_station():
    plank, plank_hi, plank_dk, nail = rgb(0xA57A45), rgb(0xC49A62), rgb(0x6E4E2A), rgb(0x3A2A18)
    iron, iron_dk, iron_hi = rgb(0x5B5B5B), rgb(0x2F2F2F), rgb(0x8A8A8A)
    feed = rgb(0x8B6B3C)
    c = Canvas(plank)
    # four horizontal planks with a darker seam and a highlight on each
    for y0 in (0, 4, 8, 12):
        c.rect(0, y0, 16, y0 + 1, plank_hi)
        c.rect(0, y0 + 3, 16, y0 + 4, plank_dk)
        c.put(1, y0 + 2, nail); c.put(14, y0 + 2, nail)
    # the hopper mouth: an iron funnel set into the top half
    c.rect(2, 1, 14, 6, iron)
    c.rect(2, 1, 14, 2, iron_hi); c.rect(2, 1, 3, 6, iron_hi)
    c.rect(13, 1, 14, 6, iron_dk); c.rect(2, 5, 14, 6, iron_dk)
    c.rect(4, 2, 12, 5, iron_dk)             # the dark inside
    c.rect(5, 3, 11, 5, feed)                # groundbait sitting in it
    c.rect(6, 6, 10, 9, iron); c.rect(9, 6, 10, 9, iron_dk)   # the spout
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


REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(REPO, "common", "src", "main", "resources", "assets", "riverfishing", "textures", "block")

if __name__ == "__main__":
    for name, draw in (("aerator", aerator), ("snag_pile", snag_pile), ("gravel_bed", gravel_bed),
                       ("warm_outflow", warm_outflow), ("feeding_station", feeding_station)):
        c = draw()
        assert all(p != CLEAR for row in c.px for p in row), name + ": a cube texture must be opaque"
        path = os.path.join(OUT, name + ".png")
        png(path, c.px)
        print("%s  %dx%d" % (path, W, H))
