# -*- coding: utf-8 -*-
"""§pattern-mask: the eleven pattern families as HARD masks, cut to each patterned sprite's silhouette.

    py -X utf8 tools/gen_pattern_masks.py [--preview]        # writes into all three trees
    py -X utf8 tools/gen_pattern_masks.py <root> [<root>...]  # ...or the trees you name

The pattern index (§pattern) named twelve families — mask, banded, speckled, marbled… — and drew none of
them: it turned the hue a few degrees. This is the art it was missing. One greyscale-alpha mask per
family per patterned sprite, white where the marking is, transparent elsewhere, clipped to the sprite's
own body (the six silhouettes overlap only 82–96%, so a shared mask would spill off a carp's back) and
kept three texels off the outline so the marking sits ON the fish, not over its edge.

NO FEATHER. Every alpha is 0 or 255. The author redrew the koi layers by hand because a 3 px feather
made the texture look blurred at icon size, and the check (tools/check_pattern_masks.py) refuses any
mask with a half-transparent texel.

The shapes are procedural but DETERMINISTIC — value noise seeded per family, the same field on every
sprite — so a speckled carp and a speckled koi carry the same speckle, and re-running this script
produces byte-identical files. The head is found, not assumed: the end of the body whose columns are
fuller is the head (a tail is a fork, a head is a block), and every shape is written in body
coordinates u (head 0 → tail 1) and v (back 0 → belly 1).

Where the mask is white the client paints Pattern.marking(): a darker (or, on a dark fish, paler)
cut of the ground colour, turned by the family's hue and the in-band index. The mask decides WHERE,
the marking decides WHAT COLOUR, and the index moves both a notch. Colour is the renderer's business;
this file only draws shapes.
"""
import io, math, os, struct, sys, zlib

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TREES = [HERE, r"C:\Users\Qwazar\wt\rf1201", r"C:\Users\Qwazar\wt\rf26"]
REL = "common/src/main/resources/assets/riverfishing/textures/item/fish"
DRAWS = ["koi_carp", "carp", "wild_carp", "mirror_carp", "linear_carp", "naked_carp"]
# band order from Pattern.FAMILY, minus "plain" which is no mask at all
FAMILIES = ["drift", "crown", "banded", "speckled", "mask", "marbled", "veined", "dappled", "ghost", "ember", "aurora"]
RIM = 3


# ---- png in / out (palette + rgba, no PIL) ---------------------------------------------------------
def read(path):
    d = io.open(path, "rb").read()
    i, idat, plte, trns, w, h, depth, ct = 8, b"", None, None, 0, 0, 8, 6
    while i < len(d):
        n = struct.unpack(">I", d[i:i + 4])[0]
        t, body = d[i + 4:i + 8], d[i + 8:i + 8 + n]
        if t == b"IHDR": w, h, depth, ct = struct.unpack(">IIBB", body[:10])
        elif t == b"PLTE": plte = body
        elif t == b"tRNS": trns = body
        elif t == b"IDAT": idat += body
        i += 12 + n
    ch = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ct]
    bpl, f = (w * ch * depth + 7) // 8, max(1, ch * depth // 8)
    raw, prev, k, rows = zlib.decompress(idat), bytearray(bpl), 0, []
    for _ in range(h):
        ft = raw[k]; k += 1
        line = bytearray(raw[k:k + bpl]); k += bpl
        for x in range(bpl):
            a = line[x - f] if x >= f else 0
            b = prev[x]
            c = prev[x - f] if x >= f else 0
            if ft == 1: line[x] = (line[x] + a) & 255
            elif ft == 2: line[x] = (line[x] + b) & 255
            elif ft == 3: line[x] = (line[x] + (a + b) // 2) & 255
            elif ft == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        row = []
        for x in range(w):
            if ct == 3:
                v = line[x]
                row.append((plte[3 * v], plte[3 * v + 1], plte[3 * v + 2], trns[v] if trns and v < len(trns) else 255))
            elif ct == 6:
                row.append(tuple(line[4 * x:4 * x + 4]))
            elif ct == 2:
                row.append(tuple(line[3 * x:3 * x + 3]) + (255,))
            else:
                s = line[x * ch:(x + 1) * ch]; row.append((s[0], s[0], s[0], s[1] if ch == 2 else 255))
        rows.append(row); prev = line
    return w, h, rows


def write_rgba(path, rows):
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in r) for r in rows)

    def ch(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)

    os.makedirs(os.path.dirname(path), exist_ok=True)
    io.open(path, "wb").write(b"\x89PNG\r\n\x1a\n"
                              + ch(b"IHDR", struct.pack(">IIBBBBB", len(rows[0]), len(rows), 8, 6, 0, 0, 0))
                              + ch(b"IDAT", zlib.compress(raw, 9)) + ch(b"IEND", b""))


# ---- deterministic noise ---------------------------------------------------------------------------
def hsh(x, y, s):
    n = (x * 374761393 + y * 668265263 + s * 1274126177) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def vnoise(x, y, cell, s):
    gx, gy = x / cell, y / cell
    ix, iy = int(math.floor(gx)), int(math.floor(gy))
    fx, fy = gx - ix, gy - iy
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a, b, c, d = hsh(ix, iy, s), hsh(ix + 1, iy, s), hsh(ix, iy + 1, s), hsh(ix + 1, iy + 1, s)
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


# ---- the body ---------------------------------------------------------------------------------------
def silhouette(rows):
    return {(x, y) for y, r in enumerate(rows) for x, p in enumerate(r) if p[3] > 8}


def head_on_left(body, x0, x1, y0, y1):
    """A snout is a point and a caudal fin is a wall: at the very tip, the head end's columns are SHORT
    and the tail end's are tall (the fork spans most of the body's height). So the end whose outermost
    columns are lower is the head. (Two earlier guesses — "the fuller end" and "the end with a peduncle
    dip" — each got a fish backwards; the tip height is the one feature every drawing shares.)"""
    heights = {}
    for (x, y) in body:
        heights[x] = heights.get(x, 0) + 1
    tip = max(2, int((x1 - x0) * 0.04))
    left = sum(heights.get(x, 0) for x in range(x0, x0 + tip)) / tip
    right = sum(heights.get(x, 0) for x in range(x1 - tip + 1, x1 + 1)) / tip
    return left < right


def column_v(body):
    """v measured on the COLUMN, not the bounding box: 0 at this column's top edge, 1 at its bottom.
    The box's top is the dorsal fin's tip, so a box-relative 'top of the head' sat in empty air."""
    top, bot = {}, {}
    for (x, y) in body:
        top[x] = min(top.get(x, y), y); bot[x] = max(bot.get(x, y), y)
    return lambda x, y: (y - top[x]) / max(1, bot[x] - top[x])


def shape(fam, u, v, x, y, vc=None):
    """Hard shapes in body space. u: head 0 → tail 1. v: back 0 → belly 1 over the BOX; vc the same
    over this column alone (crown and ember use it — fins make the box lie). x,y: texels, for noise."""
    if vc is None: vc = v
    if fam == "mask":     return u < 0.22
    if fam == "crown":    return u < 0.28 and vc < 0.36
    if fam == "banded":   return 0.10 < u < 0.92 and ((u - 0.10) * 8.0) % 1.0 < 0.42
    if fam == "speckled": return vnoise(x, y, 7, 3) > 0.68
    if fam == "marbled":  return vnoise(x, y, 22, 5) > 0.55
    if fam == "veined":   return abs(math.sin(u * 14.0 + vnoise(x, y, 18, 7) * 4.0)) < 0.12
    if fam == "dappled":  return vnoise(x, y, 13, 9) > 0.60
    if fam == "drift":    return u > 0.60 + 0.06 * math.sin(v * 6.0)
    if fam == "ghost":    return True
    if fam == "ember":    return vc > 0.62 + 0.05 * math.sin(u * 9.0)
    if fam == "aurora":   return ((u * 6.0 + v * 2.0) % 1.0) < 0.5
    return False


def masks_for(sprite_path):
    w, h, rows = read(sprite_path)
    body = silhouette(rows)
    xs, ys = [p[0] for p in body], [p[1] for p in body]
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
    bw, bh = max(1, x1 - x0), max(1, y1 - y0)
    left = head_on_left(body, x0, x1, y0, y1)
    # the rim: a texel within RIM of the outside is never marked — hard clip, not a feather
    inner = {(x, y) for (x, y) in body
             if all((x + dx, y + dy) in body for dx in range(-RIM, RIM + 1) for dy in range(-RIM, RIM + 1))}
    out = {}
    colv = column_v(body)
    for fam in FAMILIES:
        px = [[(0, 0, 0, 0)] * w for _ in range(h)]
        n = 0
        for (x, y) in inner:
            u = (x - x0) / bw
            if not left: u = 1.0 - u
            v = (y - y0) / bh
            if shape(fam, u, v, x, y, colv(x, y)):
                px[y][x] = (255, 255, 255, 255); n += 1
        out[fam] = (px, n)
    mean = tuple(sum(rows[y][x][i] for (x, y) in body) // len(body) for i in range(3))
    return w, h, rows, out, left, mean


def main(argv):
    preview = "--preview" in argv
    roots = [a for a in argv if not a.startswith("--")] or TREES
    strip = []
    for draw in DRAWS:
        src = os.path.join(HERE, REL, draw + ".png")
        w, h, rows, out, left, mean = masks_for(src)
        print("%-12s head %s  mean #%02X%02X%02X  " % (draw, "left " if left else "right", *mean)
              + " ".join("%s:%d" % (f[:3], out[f][1]) for f in FAMILIES))
        for root in roots:
            for fam, (px, n) in out.items():
                write_rgba(os.path.join(root, REL, "pattern", "%s_%s.png" % (draw, fam)), px)
        if preview:
            row = []
            for fam in ["plain"] + FAMILIES:
                cell = [[(255, 0, 255, 255)] * w for _ in range(h)]
                for y in range(h):
                    for x in range(w):
                        r, g, b, a = rows[y][x]
                        if a > 8:
                            cell[y][x] = (r, g, b, 255)
                            if fam != "plain" and out[fam][0][y][x][3]:
                                cell[y][x] = (int(r * 0.45), int(g * 0.35), int(b * 0.35), 255)
                row.append(cell)
            for y in range(h):
                strip.append(sum((c[y] + [(255, 0, 255, 255)] * 4 for c in row), []))
    if preview:
        p = os.path.join(HERE, "..", "pattern_masks_preview.png")
        p = os.path.join(os.environ.get("TEMP", "."), "pattern_masks_preview.png")
        write_rgba(p, strip)
        print("preview:", p)
    print("wrote %d masks into %d tree(s)" % (len(DRAWS) * len(FAMILIES), len(roots)))


if __name__ == "__main__":
    main(sys.argv[1:])
