# -*- coding: utf-8 -*-
"""§aqua-view: the aquarium's water leaves the block model and becomes a texture the renderer tints.

    py -X utf8 tools/gen_aquarium_water.py            # all three trees
    py -X utf8 tools/gen_aquarium_water.py <root>...

Two things, into every tree named:
  1. textures/block/aquarium_water.png — 16x16, near-white with a few paler ripple texels, so that
     (marking colour × texture) is water with a faint surface, never a flat card. The renderer binds it
     straight from its path (like aquarium_roe.png) and multiplies the quality colour over it.
  2. models/block/aquarium_tank_left.json / _right.json — the element textured "#water" is removed and
     the "water" texture key with it. The glass, the gravel and the kelp stay in the model; the water is
     AquariumRenderer's now, because a block model cannot change colour with a number in a block entity.
"""
import io, json, os, struct, sys, zlib

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TREES = [HERE, r"C:\Users\Qwazar\wt\rf1201", r"C:\Users\Qwazar\wt\rf26"]
A = "common/src/main/resources/assets/riverfishing"


def png(path, rows):
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in r) for r in rows)

    def ch(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)

    io.open(path, "wb").write(b"\x89PNG\r\n\x1a\n" + ch(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
                              + ch(b"IDAT", zlib.compress(raw, 9)) + ch(b"IEND", b""))


# the ripples: fixed, hand-placed, so the file is byte-identical on every run
RIPPLE = {(1, 2), (2, 2), (3, 2), (6, 5), (7, 5), (8, 5), (9, 5), (12, 3), (13, 3),
          (3, 9), (4, 9), (5, 9), (10, 11), (11, 11), (12, 11), (13, 11), (0, 14), (1, 14), (7, 13), (8, 13)}
rows = [[(252, 254, 255, 255) if (x, y) in RIPPLE else (232, 238, 244, 255) for x in range(16)] for y in range(16)]

for root in (sys.argv[1:] or TREES):
    png(os.path.join(root, A, "textures/block/aquarium_water.png"), rows)
    for side in ("left", "right"):
        p = os.path.join(root, A, "models/block/aquarium_tank_%s.json" % side)
        d = json.load(io.open(p, encoding="utf-8"))
        before = len(d["elements"])
        d["elements"] = [e for e in d["elements"] if not any(f.get("texture") == "#water" for f in e["faces"].values())]
        d["textures"].pop("water", None)
        io.open(p, "w", encoding="utf-8", newline="\n").write(json.dumps(d, indent=2) + "\n")
        print("%s: tank_%s %d -> %d elements" % (os.path.basename(root), side, before, len(d["elements"])))
print("aquarium_water.png written")
