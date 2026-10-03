"""Boilie item textures: a little pile of three balls, drawn light grey so the tints colour them.

  boilie_a.png     every ball whole — tinted with the first flavour (items/boilie.json tint 0)
  boilie_b.png     the right half of every ball — tinted with the second flavour, or the first again (tint 1)
  boilie_drip.png  a dipped boilie only: a wet sheen on the balls and a drop that swells and falls, 8 frames
                   (boilie_drip.png.mcmeta), tinted with the dip's colour (tint 2)

Run: py tools/gen_boilie_textures.py
"""
import json, pathlib, struct, zlib

OUT = pathlib.Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/riverfishing/textures/item"
BALLS = [(4.0, 4.0), (11.0, 8.0), (4.5, 12.0)]
R = 3.2
SPECKS = [(-1, 0), (1, 1), (0, -2), (-2, 1)]          # darker grains in the paste, per ball
CLEAR = (0, 0, 0, 0)


def grey(v):
    return (v, v, v, 255)


def ball_pixel(x, y):
    """(ball index, grey level, right half?) of a pixel, or None off the balls."""
    for i, (cx, cy) in enumerate(BALLS):
        dx, dy = x + 0.5 - cx, y + 0.5 - cy
        d = (dx * dx + dy * dy) ** 0.5
        if d > R:
            continue
        if d > R - 1.0:
            v = 150                                                   # rim
        else:
            v = int(max(175, min(255, 222 - 34 * (dx * 0.55 + dy * 0.85) / R)))   # lit from the top left
            if (round(dx), round(dy)) in SPECKS:
                v -= 38
        return i, v, dx > 0
    return None


def frame(fill):
    return [[fill(x, y) for x in range(16)] for y in range(16)]


def png(path, rows):
    raw = b"".join(b"\x00" + bytes(v for p in row for v in p) for row in rows)
    chunk = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    ihdr = struct.pack(">IIBBBBB", len(rows[0]), len(rows), 8, 6, 0, 0, 0)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


a = frame(lambda x, y: grey(b[1]) if (b := ball_pixel(x, y)) else CLEAR)
bz = frame(lambda x, y: grey(b[1]) if (b := ball_pixel(x, y)) and b[2] else CLEAR)

# the drip: a sheen at each ball's top left, and a drop under the middle ball — swelling, falling, gone
SHEEN = [(int(cx - 1.6), int(cy - 1.6)) for cx, cy in BALLS] + [(int(cx - 0.6), int(cy - 2.2)) for cx, cy in BALLS]
DROP_X, DROP_TOP = 11, int(BALLS[1][1] + R)
DROP = [[], [(0, 0)], [(0, 0), (0, 1)], [(0, 1), (0, 2), (-1, 1)], [(0, 3), (0, 4)], [(0, 5)], [(0, 6)], []]
frames = []
for f in range(len(DROP)):
    px = {(x, y): (255, 255, 255, 255) for x, y in SHEEN}
    for dx, dy in DROP[f]:
        x, y = DROP_X + dx, DROP_TOP + dy
        if 0 <= y < 16 and ball_pixel(x, y) is None:
            px[(x, y)] = (205, 205, 205, 255) if dy == 0 and f < 4 else (240, 240, 240, 255)
    frames += frame(lambda x, y: px.get((x, y), CLEAR))

png(OUT / "boilie_a.png", a)
png(OUT / "boilie_b.png", bz)
png(OUT / "boilie_drip.png", frames)
(OUT / "boilie_drip.png.mcmeta").write_text(json.dumps({"animation": {"frametime": 3}}, indent=2) + "\n", encoding="utf-8")
print("boilie_a, boilie_b, boilie_drip (%d frames) ->" % len(DROP), OUT)
