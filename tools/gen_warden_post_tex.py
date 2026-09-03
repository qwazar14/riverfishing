# -*- coding: utf-8 -*-
"""§i §breeding: the warden's post — his job-site block — as a 16x16 cube_all texture.

    py tools/gen_warden_post_tex.py

A plank booth with a dark hatch in the middle and a green sign board across the top carrying a yellow
badge: the fishing stall's cousin, painted the colour of the man who stands in it. Same raw zlib PNG
writer as tools/gen_upgrade_tex.py, for the same reason — the same pixels every time, no image library.
"""
import os, struct, zlib

W = H = 16


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


def warden_post():
    plank, plank_hi, plank_dk, nail = rgb(0xA57A45), rgb(0xC49A62), rgb(0x6E4E2A), rgb(0x3A2A18)
    sign, sign_hi, sign_dk = rgb(0x2F6B3A), rgb(0x4C8F55), rgb(0x1C4224)
    badge, badge_hi = rgb(0xD8A822), rgb(0xF2D04A)
    hatch, hatch_dk = rgb(0x2A1E12), rgb(0x150E08)
    c = Canvas(plank)
    # three horizontal planks under the sign, each with a highlight, a seam and two nails
    for y0 in (6, 10, 14):
        c.rect(0, y0, 16, y0 + 1, plank_hi)
        c.rect(0, y0 + 3, 16, y0 + 4, plank_dk)
        c.put(1, y0 + 2, nail); c.put(14, y0 + 2, nail)
    c.rect(0, 15, 16, 16, plank_dk)
    # the sign board across the top: green with a pale top edge and a dark underside
    c.rect(0, 0, 16, 6, sign)
    c.rect(0, 0, 16, 1, sign_hi)
    c.rect(0, 5, 16, 6, sign_dk)
    c.put(0, 1, sign_hi); c.put(15, 4, sign_dk)
    # the badge on the sign
    c.rect(6, 1, 10, 5, badge)
    c.put(6, 1, sign); c.put(9, 1, sign); c.put(6, 4, sign); c.put(9, 4, sign)   # rounded
    c.put(7, 2, badge_hi)
    # the hatch the warden looks out of
    c.rect(5, 8, 11, 13, hatch)
    c.rect(5, 8, 11, 9, hatch_dk); c.rect(5, 8, 6, 13, hatch_dk)
    c.rect(4, 13, 12, 14, plank_dk)          # the counter lip under it
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
OUT = os.path.join(REPO, "common", "src", "main", "resources", "assets", "riverfishing", "textures", "block", "warden_post.png")

if __name__ == "__main__":
    c = warden_post()
    assert all(p[3] == 0xFF for row in c.px for p in row), "a cube texture must be opaque"
    png(OUT, c.px)
    print("%s  %dx%d" % (OUT, W, H))
