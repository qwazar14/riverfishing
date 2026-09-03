# -*- coding: utf-8 -*-
"""§i §breeding: the warden's villager skin, derived from the fisherman's.

    py tools/gen_warden_villager.py

fisherman.png is hand-drawn (no generator in tools/), so the warden is a RECOLOUR of it rather than a
second drawing: the olive coat goes forest green, the teal hat goes with it, and a yellow badge lands
on the left chest of the jacket. Trousers, belt and everything brown stay — the two men shop at the
same store. The zombie variant is the same file, exactly as the fisherman's is.

Raw zlib in both directions — the reverse of tools/gen_contract_icon.py's writer — because the only
Python on the build machine is the bare interpreter.
"""
import os, struct, zlib

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX = os.path.join(REPO, "common", "src", "main", "resources", "assets", "riverfishing", "textures", "entity")
SRC = os.path.join(TEX, "villager", "profession", "fisherman.png")
OUTS = (os.path.join(TEX, "villager", "profession", "warden.png"),
        os.path.join(TEX, "zombie_villager", "profession", "warden.png"))
MCMETA = '{\n    "villager": {\n        "hat": "full"\n    }\n}\n'

# The jacket's front face in the 64x64 villager map: jacket cube at texOffs(0,38), 8 wide, front u 6..14, side rows 44..56 — the badge sits on the left breast.
BADGE_AT = ((8, 46), (9, 46), (8, 47), (9, 47))
BADGE_HI, BADGE, BADGE_DK = (0xF2, 0xD0, 0x4A, 0xFF), (0xD8, 0xA8, 0x22, 0xFF), (0x8A, 0x66, 0x10, 0xFF)


def decode(path):
    """8-bit RGBA, non-interlaced — the one shape every texture in this repo has."""
    d = open(path, "rb").read()
    pos, idat, w, h = 8, b"", 0, 0
    while pos < len(d):
        n = struct.unpack(">I", d[pos:pos + 4])[0]
        tag, body = d[pos + 4:pos + 8], d[pos + 8:pos + 8 + n]
        pos += 12 + n
        if tag == b"IHDR":
            w, h, depth, ctype, _, _, interlace = struct.unpack(">IIBBBBB", body)
            assert (depth, ctype, interlace) == (8, 6, 0), "expected 8-bit RGBA, got %r" % ((depth, ctype, interlace),)
        elif tag == b"IDAT":
            idat += body
    raw, stride, bpp = zlib.decompress(idat), w * 4, 4
    rows, prev, p = [], bytearray(stride), 0
    for _ in range(h):
        f, cur = raw[p], bytearray(raw[p + 1:p + 1 + stride])
        p += 1 + stride
        for i in range(stride):
            a = cur[i - bpp] if i >= bpp else 0
            b = prev[i]
            c = prev[i - bpp] if i >= bpp else 0
            if f == 1:
                cur[i] = (cur[i] + a) & 255
            elif f == 2:
                cur[i] = (cur[i] + b) & 255
            elif f == 3:
                cur[i] = (cur[i] + (a + b) // 2) & 255
            elif f == 4:
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                cur[i] = (cur[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        rows.append([tuple(cur[i:i + 4]) for i in range(0, stride, 4)])
        prev = cur
    return rows


def png(path, rows):
    raw = b"".join(b"\x00" + b"".join(struct.pack("4B", *p) for p in row) for row in rows)

    def chunk(tag, data):
        c = tag + data
        return struct.pack(">I", len(data)) + c + struct.pack(">I", zlib.crc32(c) & 0xFFFFFFFF)

    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n")
        f.write(chunk(b"IHDR", struct.pack(">IIBBBBB", len(rows[0]), len(rows), 8, 6, 0, 0, 0)))
        f.write(chunk(b"IDAT", zlib.compress(raw, 9)))
        f.write(chunk(b"IEND", b""))


def clamp(v):
    return max(0, min(255, int(round(v))))


def recolour(p):
    r, g, b, a = p
    if a == 0:
        return p
    if g >= r > b and r < 0x60:                 # the olive coat: green-grey with a brown floor
        return (clamp(r * 0.55), clamp(g * 1.15), clamp(b * 0.6), a)
    if b > r and g > r and g >= 0x50:           # the teal hat and cuffs: pulled toward the coat
        return (clamp(r * 0.7), clamp(g * 1.05), clamp(b * 0.72), a)
    return p                                    # trousers, belt, shadows: untouched


if __name__ == "__main__":
    rows = decode(SRC)
    out = [[recolour(p) for p in row] for row in rows]
    for x, y in BADGE_AT:
        out[y][x] = BADGE
    out[46][8] = BADGE_HI
    out[47][9] = BADGE_DK
    changed = sum(1 for y in range(len(rows)) for x in range(len(rows[0])) if rows[y][x] != out[y][x])
    assert changed > 100, "the recolour touched only %d pixels — the coat's colour class moved" % changed
    for path in OUTS:
        png(path, out)
        with open(path + ".mcmeta", "w", newline="\n") as f:
            f.write(MCMETA)
        print("%s  %dx%d  (%d px recoloured)" % (path, len(out[0]), len(out), changed))
