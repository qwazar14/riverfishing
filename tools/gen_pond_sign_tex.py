# -*- coding: utf-8 -*-
"""§pond §breeding: the private-pond sign, as a 16x16 cross-model texture.

    py tools/gen_pond_sign_tex.py

A weathered wooden post with a small board nailed across it, a blue fish painted on the board — the
thing you hammer in at the bank of your own pond. Transparent where there is no wood, because the
block renders it as two crossed planes (minecraft:block/cross), sapling-style, and the item icon is the
same sheet.

Same raw zlib PNG writer as tools/gen_contract_icon.py — one palette, one file, the same pixels every
time, and no image library to install on a machine that only has Python.
"""
import os, struct, zlib

W = H = 16
CLEAR = (0, 0, 0, 0)


def rgb(h):
    return ((h >> 16) & 0xFF, (h >> 8) & 0xFF, h & 0xFF, 0xFF)


px = [[CLEAR] * W for _ in range(H)]


def put(x, y, c):
    if 0 <= x < W and 0 <= y < H:
        px[y][x] = c


def rect(x0, y0, x1, y1, c):
    for y in range(y0, y1):
        for x in range(x0, x1):
            put(x, y, c)


POST, POST_HI, POST_DK = rgb(0x6E4E2A), rgb(0x8E6740), rgb(0x4A331A)
BOARD, BOARD_HI, BOARD_DK = rgb(0xC49A62), rgb(0xDDB77E), rgb(0x9C7444)
NAIL = rgb(0x3A2A18)
FISH, FISH_HI = rgb(0x2E6BC4), rgb(0x8FC6F2)

# the post: two pixels wide, top to bottom, lit from the left
rect(7, 0, 9, 16, POST)
rect(7, 0, 8, 16, POST_HI)
rect(8, 12, 9, 16, POST_DK)
put(7, 0, POST_DK); put(8, 0, POST_DK)         # the sawn top

# the board: nailed across the upper half, a shadow along its bottom edge
rect(2, 2, 14, 9, BOARD)
rect(2, 2, 14, 3, BOARD_HI)
rect(2, 8, 14, 9, BOARD_DK)
rect(2, 2, 3, 9, BOARD_HI); rect(13, 2, 14, 9, BOARD_DK)
for x, y in ((3, 3), (12, 3), (3, 7), (12, 7)):
    put(x, y, NAIL)

# the fish on the board: body, tail, eye
for x in range(5, 10):
    put(x, 5, FISH)
put(6, 4, FISH); put(7, 4, FISH); put(8, 4, FISH)
put(6, 6, FISH); put(7, 6, FISH); put(8, 6, FISH)
put(10, 4, FISH); put(10, 6, FISH); put(11, 4, FISH); put(11, 6, FISH)   # the tail fork
put(5, 4, FISH_HI)                                                        # the eye


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
OUT = os.path.join(REPO, "common", "src", "main", "resources", "assets", "riverfishing", "textures", "block", "pond_sign.png")

if __name__ == "__main__":
    assert any(p == CLEAR for row in px for p in row), "a cross texture needs air around the post"
    png(OUT, px)
    print("%s  %dx%d" % (OUT, W, H))
