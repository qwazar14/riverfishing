"""Plant textures (32x32), the sheets 3D/reed, 3D/cattail and 3D/corn (.bbmodel) are UV-mapped onto.

reed / cattail
  cols 0-3   stalk strips, row r = height 32-r (a stalk's faces read their own height off the strip)
  cols 4-7   leaf strips   reed: rows 20-31, tip at row 20      cattail: row r = height 32-r, like the stalks
  cols 8-31  reed: plume speckle, rows 0-23 (each plume face takes its own window)
             cattail: head speckle, rows 0-6 = the head's 7 rows top to bottom (ends darker), rows 8-15 caps
corn / barley (sit on farmland, so their ground is y=-1: row r = height 31-r)
  cols 0-3   two 2-wide stalk strips
  cols 4-15  rows 0-7 leaf, darker at col 4 (by the stalk) to lighter at col 15 (the tip); rows 8-15 husk
  cols 16-31 rows 0-7 tassel, rows 8-15 cob kernels
barley: cols 0-3 green stalks, 4-7 turning (yellow-green tips), 8-11 ripe (green foot, gold top);
        cols 12-31 rows 0-7 ripe ear, rows 8-15 green ear

No alpha anywhere: every face is solid, so the blocks render solid. Run: py tools/gen_plant_textures.py
"""
import random, struct, zlib, pathlib

TEX = pathlib.Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/riverfishing/textures/block"
W = H = 32


def hexc(s):
    return tuple(int(s[i:i + 2], 16) for i in (1, 3, 5))


def lerp(a, b, t):
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def stalks(px, base, mid, top, dark, light, tint):
    """Dark at the root, lighter up the cane; a soft node ring every 6-7 rows with a pale lip above it."""
    for c in range(4):
        phase, step = (0, 3, 5, 2)[c], (7, 6, 7, 6)[c]
        for r in range(H):
            h = 32 - r
            t = h / 32
            col = lerp(base, mid, t * 2) if t < .5 else lerp(mid, top, (t - .5) * 2)
            if h > 3 and (h + phase) % step == 0:
                col = lerp(col, dark, .45)                     # node: a soft ring, not a band
            elif h > 3 and (h + phase) % step == 1:
                col = lerp(col, light, .35)
            px[r][c] = lerp(col, tint, .15 * (c % 2))


def speckle(rng, pal):
    cols = [hexc(h) for h, n in pal for _ in range(n)]
    return lambda: rng.choice(cols)


def reed():
    rng = random.Random(1717)
    px = [[(0, 0, 0)] * W for _ in range(H)]
    stalks(px, hexc("#4A6B34"), hexc("#5F8A44"), hexc("#7FA557"), hexc("#35512A"), hexc("#9CC270"), hexc("#6E8F3E"))
    # leaves: 12 rows, root dark, tip bleached
    lroot, ltip, lend = hexc("#3E6630"), hexc("#7FA955"), hexc("#A8B86A")
    for c in range(4, 8):
        for r in range(H):
            h = 32 - r
            if h > 12:                      # unused: root colour so mipmaps don't pull in black
                px[r][c] = lroot
                continue
            col = lerp(lroot, ltip, (h - 1) / 11)             # a leaf of height n maps rows 20..20+n: tip always shows
            px[r][c] = lerp(lend if h == 12 else col, hexc("#5C7A34"), .12 * (c - 4))
    # plume: weighted speckle, a touch lighter towards the top rows, olive flecks for depth
    fleck = speckle(rng, [("#EFE8C4", 25), ("#E2D79A", 20), ("#CFC47E", 15), ("#B8BE72", 14),
                          ("#F7F3DC", 10), ("#A9A660", 10), ("#C9B27A", 6)])
    for r in range(H):
        for c in range(8, W):
            col = fleck()
            px[r][c] = lerp(col, hexc("#F7F3DC"), .25) if r < 8 else col
    return px


def cattail():
    rng = random.Random(4242)
    px = [[(0, 0, 0)] * W for _ in range(H)]
    stalks(px, hexc("#557F40"), hexc("#6E9A52"), hexc("#8DB569"), hexc("#3F6130"), hexc("#A6C97E"), hexc("#7A9A48"))
    # leaves: by height like the stalks, no nodes; a slightly different green per column
    lroot, ltip = hexc("#4A7838"), hexc("#88B262")
    for c in range(4, 8):
        for r in range(H):
            px[r][c] = lerp(lerp(lroot, ltip, (32 - r) / 24 if r > 8 else 1), hexc("#6A8A3C"), .1 * (c - 4))
    # head: brown speckle; the top and bottom row of the sausage darker so its ends read rounded
    fleck = speckle(rng, [("#8A5638", 35), ("#7E4E33", 25), ("#96603F", 20), ("#A06A47", 10), ("#744830", 10)])
    end = hexc("#4E2E1E")
    for r in range(H):
        for c in range(8, W):
            col = fleck()
            if r in (0, 6):
                col = lerp(col, end, .3)
            elif 8 <= r < 16:
                col = lerp(col, end, .2)                       # caps: the flat top and bottom of the head
            elif r >= 16:
                col = hexc("#6E4430")                          # unused
            px[r][c] = col
    return px


def corn():
    rng = random.Random(3131)
    px = [[hexc("#4E8C36")] * W for _ in range(H)]
    # stalk: 2-wide strips, height = 31 - row; nodes every 5-6 rows
    base, mid, top = hexc("#557F33"), hexc("#6B9A40"), hexc("#84B050")
    dark, light = hexc("#3F6526"), hexc("#9CC266")
    for c in range(4):
        phase, step = (0, 0, 3, 3)[c], (5, 5, 6, 6)[c]
        for r in range(H):
            h = 31 - r
            t = max(h, 0) / 31
            col = lerp(base, mid, t * 2) if t < .5 else lerp(mid, top, (t - .5) * 2)
            if h > 1 and (h + phase) % step == 0:
                col = lerp(col, dark, .5)
            elif h > 1 and (h + phase) % step == 1:
                col = lerp(col, light, .35)
            px[r][c] = lerp(col, hexc("#7A9A48"), .1 * (c % 2))    # the two faces of a strip differ a touch
    # leaf: three greens, lighter and yellower towards the tip column
    greens = [hexc("#3F7A2C"), hexc("#4E8C36"), hexc("#62A043")]
    tip = hexc("#8DB453")
    for c in range(4, 16):
        for r in range(8):
            px[r][c] = lerp(rng.choice(greens), tip, (c - 4) / 11 * .6)
    husk = speckle(rng, [("#7FAE4E", 35), ("#8DBB58", 30), ("#A2C36A", 20), ("#6E9E44", 15)])
    for c in range(4, 16):
        for r in range(8, 16):
            px[r][c] = husk()
    tassel = speckle(rng, [("#E3B84A", 30), ("#D4A53A", 25), ("#F0CC62", 20), ("#B8892E", 15), ("#C9A04A", 10)])
    for c in range(16, W):
        for r in range(8):
            px[r][c] = tassel()
    # cob: a checker of kernels and the darker seams between them, a few pale ones
    for c in range(16, W):
        for r in range(8, 16):
            col = hexc("#F5CE4E") if (r + c) % 2 == 0 else hexc("#E0AE32")
            px[r][c] = hexc("#F8DE7A") if rng.random() < .08 else col
    return px


def barley():
    rng = random.Random(2727)
    px = [[hexc("#5FA834")] * W for _ in range(H)]

    def strip(c, stops, tint):
        """Height strip, row r = height 31 - r (y -1..15 on rows 32..16); stops = [(y, colour)] up the stalk."""
        for r in range(H):
            y = min(max(31 - r, -1), 15)
            for (y0, c0), (y1, c1) in zip(stops, stops[1:]):
                if y0 <= y <= y1:
                    px[r][c] = lerp(lerp(c0, c1, (y - y0) / (y1 - y0)), tint, .12 * (c % 2))
                    break

    green = [(-1, hexc("#3F8A2A")), (15, hexc("#6FB23A"))]
    turning = [(-1, hexc("#3F8A2A")), (8, hexc("#74B43A")), (15, hexc("#A8C23E"))]
    ripe = [(-1, hexc("#4F9A2A")), (5, hexc("#8DBA2E")), (11, hexc("#D2BE3A")), (15, hexc("#DDB63C"))]
    for c in range(4):
        strip(c, green, hexc("#4E7A28"))
        strip(c + 4, turning, hexc("#6E8A30"))
        strip(c + 8, ripe, hexc("#9A8A30"))
    ear = speckle(rng, [("#D9A93A", 30), ("#E8C04E", 25), ("#C08A2A", 20), ("#F0D060", 15), ("#B07A25", 10)])
    unripe = speckle(rng, [("#8DB840", 30), ("#A2C24A", 25), ("#7AA838", 20), ("#B5CC5A", 15), ("#6E9A30", 10)])
    for c in range(12, W):
        for r in range(16):
            px[r][c] = ear() if r < 8 else unripe()
    return px


# pea: flat 16x16 sprites with see-through gaps for a "+" of two planes (models/block/crop_cross.json), in the
# look of the reference. Each stage is drawn as its left half, cols 0-7 (col 7 = the stalk), and mirrored onto 8-14.
PEA_PAL = {"s": "#5FA040", "d": "#3E7A2C", "m": "#56963A", "l": "#74B04A", "q": "#5E9E3C", "p": "#7CBF50",
           "P": "#9ED468", "w": "#F2F2E8", "y": "#E8E0A0"}
PEA = [
    ["........"] * 11 + [".......l", ".....m.s", "......ms", ".......s", ".......s"],
    ["........"] * 6 + [".......l", ".......s", ".....m.s", "....mm.s", ".....mls", "..dm...s", "...dmmls",
                        ".....dms", ".......s", ".......s"],
    ["........"] * 2 + [".......s", "......ms", "..dm..ls", ".d.mm.ls", ".w..mdms", "wy...dms", ".w...wms",
                        "...m.dms", ".dmmm.ls", "..dmlmms", "....mlds", ".....mms", "......ds", ".......s"],
    [".......s", ".......s", ".....mls", ".ddm.mls", "dmmmdmls", ".q.mm.ms", "qPp..q.s", "qPp.qPps", "qPp.qPps",
     ".q.m.q.s", "ddmmmlls", ".ddmlmms", "....mlds", ".....mms", "......ds", ".......s"],
]


def pea(stage):
    assert len(PEA[stage]) == 16 and all(len(h) == 8 for h in PEA[stage]), f"pea stage {stage}: 16 rows of 8"
    rows = []
    for half in PEA[stage]:
        full = half + half[6::-1] + "."
        rows.append([hexc(PEA_PAL[ch]) + (255,) if ch != "." else (0, 0, 0, 0) for ch in full])
    return rows


def png(path, rows):
    alpha = len(rows[0][0]) == 4
    raw = b"".join(b"\x00" + bytes(v for p in row for v in p) for row in rows)
    chunk = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    ihdr = struct.pack(">IIBBBBB", len(rows[0]), len(rows), 8, 6 if alpha else 2, 0, 0, 0)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


for name, make in (("reed", reed), ("cattail", cattail), ("corn", corn), ("barley", barley)):
    png(TEX / f"{name}.png", make())
    print(TEX / f"{name}.png")
for stage in range(4):
    png(TEX / f"pea_stage{stage}.png", pea(stage))
    print(TEX / f"pea_stage{stage}.png")


# §no-seeds: the ripe harvest, 16x16 item sprites drawn from geometry (RGBA, see-through around them)
ITEM = TEX.parent / "item"


def seg_dist(px, py, ax, ay, bx, by):
    """Distance from a pixel centre to the segment a-b, and where along it (0..1) it falls."""
    dx, dy = bx - ax, by - ay
    t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)))
    return ((px - ax - t * dx) ** 2 + (py - ay - t * dy) ** 2) ** 0.5, t


def corn_cob():
    rows = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
    ax, ay, bx, by = 4.5, 11.5, 12.0, 3.5          # the cob's axis, base bottom-left, tip top-right
    for y in range(16):
        for x in range(16):
            d, t = seg_dist(x + .5, y + .5, ax, ay, bx, by)
            r = 2.6 - 0.9 * max(0.0, t - 0.75) / 0.25  # a rounded, narrowing tip
            if d <= r:
                lit = (x + y) % 2 == 0                 # kernels in a checker, the seams darker
                col = hexc("#F5CE4E") if lit else hexc("#D9A62E")
                if d < r - 1.6 and lit: col = hexc("#FBE38A")
                rows[y][x] = col + (255,)
            elif d <= r + 0.9:
                rows[y][x] = hexc("#8A6A1E") + (255,)
    # the husk: two leaves peeling back from the base, and the stub of stalk
    husk = [(1, 14, "H"), (2, 13, "g"), (2, 14, "H"), (3, 12, "g"), (3, 13, "h"), (4, 13, "g"), (1, 11, "H"), (2, 11, "g"),
            (2, 12, "h"), (3, 10, "g"), (5, 14, "H"), (6, 14, "g"), (6, 13, "h"), (7, 12, "g"), (0, 15, "H"), (1, 15, "H")]
    for x, y, c in husk:
        rows[y][x] = hexc({"H": "#3F6526", "g": "#6E9E44", "h": "#9CC266"}[c]) + (255,)
    return rows


def pea_pod():
    rows = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
    import math
    pts = [(3.0 + 10 * s, 12.5 - 9 * s - 3.0 * math.sin(math.pi * s)) for s in [i / 40 for i in range(41)]]
    peas = [pts[10], pts[20], pts[30]]
    for y in range(16):
        for x in range(16):
            cx, cy = x + .5, y + .5
            d = min(((cx - px) ** 2 + (cy - py) ** 2) ** 0.5 for px, py in pts)
            bulge = min(((cx - px) ** 2 + (cy - py) ** 2) ** 0.5 for px, py in peas)
            if d <= 1.9 or bulge <= 2.2:
                col = hexc("#7CBF50")
                if bulge <= 1.3: col = hexc("#A6D86E")       # a pea pressing through the skin
                elif d <= 0.9: col = hexc("#8FCB5C")
                rows[y][x] = col + (255,)
            elif d <= 2.7 or bulge <= 3.0:
                rows[y][x] = hexc("#3E7A2C") + (255,)
    for x, y in ((13, 2), (14, 1), (14, 2)):                   # the stem
        rows[y][x] = hexc("#6B8A2E") + (255,)
    return rows


png(ITEM / "corn_cob.png", corn_cob())
png(ITEM / "pea_pod.png", pea_pod())
print(ITEM / "corn_cob.png", ITEM / "pea_pod.png")
