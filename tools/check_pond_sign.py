# -*- coding: utf-8 -*-
"""§pond-sign: every face of the sign reads a part of the sheet somebody actually drew.

    py -X utf8 tools/check_pond_sign.py

The sign's texture is not a picture, it is a SHEET: four regions, and the block model names each of
them by coordinate. Nothing in Minecraft checks that — a face pointing at a corner nobody painted draws
a transparent hole, and a face pointing at the wrong strip draws the post's wood on the board's front.
Both look like art mistakes and neither is.

So this reads the model's uv rectangles, samples the PNG the generator wrote, and fails if any face
lands on an unpainted texel or on a region that is not the one it means. It also holds the two boxes to
the shape the block's own hitbox claims, because those are written twice as well.
"""
import io, json, os, struct, sys, zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
A = os.path.join(ROOT, "common/src/main/resources/assets/riverfishing")
MODEL = json.load(io.open(os.path.join(A, "models/block/pond_sign.json"), encoding="utf-8"))
PNG = os.path.join(A, "textures/block/pond_sign.png")

fails = []


def die(msg):
    fails.append(msg)


# ---- the sheet -------------------------------------------------------------------------------------
def read_png(path):
    d = io.open(path, "rb").read()
    i, idat, w, h = 8, b"", 0, 0
    while i < len(d):
        n = struct.unpack(">I", d[i:i + 4])[0]
        tag = d[i + 4:i + 8]
        if tag == b"IHDR":
            w, h, depth, ctype = struct.unpack(">IIBB", d[i + 8:i + 18])
            if depth != 8 or ctype != 6:
                print("FAILED: pond_sign.png is not 8-bit RGBA")
                sys.exit(1)
        elif tag == b"IDAT":
            idat += d[i + 8:i + 8 + n]
        i += 12 + n
    raw, out, prev, k = zlib.decompress(idat), [], bytearray(w * 4), 0
    for _ in range(h):
        f = raw[k]
        k += 1
        line = bytearray(raw[k:k + w * 4])
        k += w * 4
        for x in range(w * 4):
            a = line[x - 4] if x >= 4 else 0
            b = prev[x]
            c = prev[x - 4] if x >= 4 else 0
            if f == 1:
                line[x] = (line[x] + a) & 255
            elif f == 2:
                line[x] = (line[x] + b) & 255
            elif f == 3:
                line[x] = (line[x] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        out.append(bytes(line))
        prev = line
    return w, h, out


W, H, px = read_png(PNG)
if (W, H) != (16, 16):
    die("the sheet is %dx%d; every uv in the model is written for 16x16" % (W, H))


def opaque(x, y):
    return px[y][x * 4 + 3] != 0


# ---- every face lands on paint -----------------------------------------------------------------------
REGIONS = {                      # what each rectangle is FOR, so a face on the wrong strip is caught
    (0, 0, 14, 9): "the board's face",
    (0, 9, 14, 11): "the board's top and bottom edge",
    (14, 0, 16, 9): "the board's side edges",
    (0, 11, 2, 16): "the post",
    (0, 11, 2, 13): "the post's end",
}
seen = set()
for el in MODEL["elements"]:
    for side, face in el["faces"].items():
        uv = tuple(face["uv"])
        x0, y0, x1, y1 = min(uv[0], uv[2]), min(uv[1], uv[3]), max(uv[0], uv[2]), max(uv[1], uv[3])
        if x1 > 16 or y1 > 16 or x0 < 0 or y0 < 0:
            die("%s.%s reads %s, which is off the sheet" % (el["name"], side, list(uv)))
            continue
        blank = [(x, y) for y in range(int(y0), int(y1)) for x in range(int(x0), int(x1)) if not opaque(x, y)]
        if blank:
            die("%s.%s reads %s and %d of those texels are unpainted (first at %s) — that face would "
                "draw a hole" % (el["name"], side, list(uv), len(blank), blank[0]))
        if uv not in REGIONS:
            die("%s.%s reads %s, which is not one of the sheet's regions (%s)"
                % (el["name"], side, list(uv), "; ".join(str(list(r)) for r in REGIONS)))
        else:
            seen.add(uv)
        if (el["name"] == "board") != (uv in {(0, 0, 14, 9), (0, 9, 14, 11), (14, 0, 16, 9)}):
            die("%s.%s reads %s — that is %s" % (el["name"], side, list(uv), REGIONS.get(uv, "?")))

unused = [REGIONS[r] for r in REGIONS if r not in seen and r != (0, 11, 2, 13)]
if unused:
    die("nothing on the model reads " + ", ".join(unused) + " — a drawn region no face uses")

# ---- the model and the hitbox describe the same object ------------------------------------------------
java = io.open(os.path.join(ROOT, "common/src/main/java/com/riverfishing/block/PondSignBlock.java"),
               encoding="utf-8").read()
for name, box in ((e["name"], (e["from"], e["to"])) for e in MODEL["elements"]):
    if name == "post" and "Block.box(6, 0, 6, 10, 7, 10)" not in java:
        die("the model's post is %s..%s and the hitbox does not match it" % box)
    if name == "board" and "Block.box(1, 6, 6, 15, 15, 9)" not in java:
        die("the model's board is %s..%s and the hitbox does not match it" % box)
if "FACING" not in java or "getStateForPlacement" not in java:
    die("the sign has no facing again — it cannot be aimed, and the blockstate names four of them")

if fails:
    print("FAILED:")
    for x in fails:
        print("  " + x)
    sys.exit(1)
print("pond sign: %d elements, %d faces, all on painted texels; %d regions used; hitbox matches the model"
      % (len(MODEL["elements"]), sum(len(e["faces"]) for e in MODEL["elements"]), len(seen)))
