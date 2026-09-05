# -*- coding: utf-8 -*-
"""§pond-sign: the claim sign's texture, drawn as a sheet the block model reads 1:1.

    py -X utf8 tools/gen_pond_sign.py                 # this tree
    py -X utf8 tools/gen_pond_sign.py <other-root>    # …and the other two worktrees

The sign used to be `minecraft:block/cross` — the two crossed quads that draw a flower — with one flat
16x16 of plank and a blue splodge on it. From any angle you saw BOTH quads, which is why it read as a
folded piece of card rather than a sign, and the art was stretched over the whole of each quad.

It is a board on a post now, made of two boxes, and this file draws the sheet those boxes read. Every
face maps a region of it at ONE TEXEL PER PIXEL — no stretching anywhere — which is the whole reason the
layout below is a layout and not just a picture:

    cols  0..13, rows  0..8   the board's FACE          uv [0, 0, 14, 9]
    cols  0..13, rows  9..10  the board's top and底 edge uv [0, 9, 14, 11]
    cols 14..15, rows  0..8   the board's side edges    uv [14, 0, 16, 9]
    cols  0..1,  rows 11..15  the post                  uv [0, 11, 2, 16]

The picture on the face is a fish and a waterline, no words: this mod ships in three languages and a
sign that has to be read is a sign that is wrong in two of them.

The palette is the one the old texture already used, so the sign still belongs to the same set of
woodwork. To restyle it, edit the map below and re-run — or just replace the PNG, nothing reads it
except the model.
"""
import io, os, struct, sys, zlib

# ---- the palette, kept from the texture this replaces ---------------------------------------------
C = {
    "#": (0x4A, 0x33, 0x1A),   # frame, and the dark edge of everything
    ".": (0xC4, 0x9A, 0x62),   # plank
    "+": (0xDD, 0xB7, 0x7E),   # plank, lit
    ",": (0x9C, 0x74, 0x44),   # plank, grain
    "F": (0x3A, 0x2A, 0x18),   # the fish
    "e": (0xDD, 0xB7, 0x7E),   # its eye
    "w": (0x2E, 0x6B, 0xC4),   # water
    "W": (0x8F, 0xC6, 0xF2),   # water, lit
    "b": (0x8E, 0x67, 0x40),   # board edge
    "p": (0x6E, 0x4E, 0x2A),   # post
    " ": None,                 # nothing: this sheet is not full
}

# ---- the board's face, 14 x 9 ---------------------------------------------------------------------
# A forked tail on the left, the body, an eye near the nose, and the water it is claiming under it.
FACE = [
    "##############",
    "#+..,...,..+.#",
    "#.F...FF.....#",
    "#.FF.FFFFFF..#",
    "#.FFFFFFFFeF.#",
    "#.FF.FFFFFF..#",
    "#.F...FF.....#",
    "#wWwwWwwwWwww#",
    "##############",
]

# ---- the strips ------------------------------------------------------------------------------------
EDGE_TB = ["bbbbbbbbbbbbbb",      # the board seen from above
           "##############"]      # …and the shadow line under it
EDGE_LR = ["b#", "bb", "bb", "bb", "bb", "bb", "bb", "bb", "b#"]   # 2 wide, 9 tall
POST = ["p#", "pp", "p#", "pp", "p#"]                              # 2 wide, 5 tall

W = H = 16
ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "common/src/main/resources/assets/riverfishing/textures/block/pond_sign.png")

px = [[None] * W for _ in range(H)]


def blit(rows, x0, y0, what):
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if C[ch] is None:
                continue
            if x0 + x >= W or y0 + y >= H:
                raise SystemExit("%s runs off the sheet at %d,%d" % (what, x0 + x, y0 + y))
            if px[y0 + y][x0 + x] is not None:
                raise SystemExit("%s overlaps something already drawn at %d,%d — the model reads these "
                                 "regions by coordinate, so two of them cannot share a texel"
                                 % (what, x0 + x, y0 + y))
            px[y0 + y][x0 + x] = C[ch]


blit(FACE, 0, 0, "the face")
blit(EDGE_TB, 0, 9, "the top edge")
blit(EDGE_LR, 14, 0, "the side edges")
blit(POST, 0, 11, "the post")

raw = b""
for row in px:
    raw += b"\x00" + b"".join(bytes(p) + b"\xff" if p else b"\x00\x00\x00\x00" for p in row)


def chunk(tag, data):
    return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)


io.open(OUT, "wb").write(
    b"\x89PNG\r\n\x1a\n"
    + chunk(b"IHDR", struct.pack(">IIBBBBB", W, H, 8, 6, 0, 0, 0))
    + chunk(b"IDAT", zlib.compress(raw, 9))
    + chunk(b"IEND", b""))
used = sum(1 for r in px for p in r if p)
print("wrote %s: %dx%d, %d of %d texels used, %d colours"
      % (os.path.relpath(OUT, ROOT), W, H, used, W * H, len({c for c in C.values() if c})))
