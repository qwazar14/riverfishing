# -*- coding: utf-8 -*-
"""§breeding: the roe on the aquarium floor, one 16x16 frame per incubation day, as an 80x16 strip.

    py tools/gen_aquarium_roe.py [preview.png]

Frames, left to right — the renderer picks one by (world time − incubation start) / 24000:
    0  day 1  loose pale-orange spheres, the clutch as it was laid
    1  day 2  bigger, deeper orange, a dark eye-spot in each
    2  day 3  eye-spots clear, the eggs darker, a few gone milky (dead)
    3  day 4  eggs splitting, tiny tails poking out, some empty shells
    4  hatched a shoal of tiny silver fry, black eye dots
The background is transparent: the frame lies on the gravel, the gravel shows through. Same raw
PNG writer as gen_roe_icon.py; an optional second path writes an 8x nearest-neighbour preview over a
gravel grey so the strip can be checked by eye.
"""
import os, struct, sys, zlib

FRAMES = 5
W, H = 16 * FRAMES, 16
CLEAR   = (0, 0, 0, 0)
PALE    = (0xFF, 0xC8, 0x8E, 0xFF)   # day 1 egg body
PALE_D  = (0xE0, 0x90, 0x50, 0xFF)   # day 1 rim
EGG     = (0xF0, 0x7A, 0x2E, 0xFF)   # day 2 body
EGG_D   = (0xB0, 0x44, 0x14, 0xFF)   # day 2 rim
EGG_HI  = (0xFF, 0xC0, 0x80, 0xFF)   # highlight, all days
RIPE    = (0xC8, 0x50, 0x1E, 0xFF)   # day 3-4 body, darker
RIPE_D  = (0x7A, 0x28, 0x0C, 0xFF)   # day 3-4 rim
EYE     = (0x1A, 0x10, 0x10, 0xFF)
DEAD    = (0xEC, 0xE6, 0xDA, 0xFF)   # milky, no eye
DEAD_D  = (0xB8, 0xB0, 0xA4, 0xFF)
SHELL   = (0xF4, 0xC4, 0x9C, 0x90)   # an empty, split shell: half transparent
TAIL    = (0xE6, 0xEC, 0xF2, 0xFF)   # a fry's silver — brighter than gravel, which is what it lies on
TAIL_D  = (0x5C, 0x66, 0x78, 0xFF)   # its back
BELLY   = (0xFF, 0xFF, 0xFF, 0xFF)

px = [[CLEAR] * W for _ in range(H)]


def put(x, y, c):
    if 0 <= x < W and 0 <= y < H:
        px[y][x] = c


def rect(x0, y0, x1, y1, c):
    for y in range(y0, y1):
        for x in range(x0, x1):
            put(x, y, c)


def egg2(x, y, body, rim):
    """A 2x2 egg: rim on the bottom-right, body top-left, so it still reads as a bead at 2 px."""
    put(x, y, body); put(x + 1, y, rim); put(x, y + 1, rim); put(x + 1, y + 1, rim)
    put(x, y, EGG_HI) if body is PALE else None


def egg3(x, y, body, rim, eye=None, hi=True):
    """A 3x3 sphere: dark rim, body, one highlight top-left; `eye` = (dx, dy) of a 1-px eye-spot."""
    rect(x, y, x + 3, y + 3, rim)
    put(x + 1, y, body); put(x, y + 1, body); put(x + 1, y + 1, body)
    if hi:
        put(x + 1, y, EGG_HI)
    if eye:
        put(x + eye[0], y + eye[1], EYE)


# ---- frame 0, day 1: a loose scatter of small pale eggs, spread across the floor ----
o = 0
for x, y in ((2, 3), (6, 2), (11, 3), (4, 7), (9, 6), (13, 8), (2, 11), (7, 10), (11, 12), (6, 13)):
    egg2(o + x, y, PALE, PALE_D)

# ---- frame 1, day 2: bigger, deeper orange, packed closer, an eye-spot appearing ----
o = 16
for x, y in ((2, 2), (6, 1), (10, 2), (4, 5), (8, 5), (12, 6), (2, 9), (6, 9), (10, 10), (5, 12)):
    egg3(o + x, y, EGG, EGG_D, eye=(1, 1))   # the spot in the middle: an eye forming, not a dent in the rim

# ---- frame 2, day 3: eyes clear (2 px), bodies darker, three eggs milky and dead ----
o = 32
alive = ((2, 2), (6, 1), (10, 2), (8, 5), (12, 6), (2, 9), (6, 9), (5, 12))
dead = ((4, 5), (10, 10))
for x, y in alive:
    egg3(o + x, y, RIPE, RIPE_D, hi=False)
    put(o + x + 1, y + 1, EYE); put(o + x + 2, y + 1, EYE)      # a two-pixel eye reads as "eyed up"
    put(o + x + 1, y, EGG_HI)
for x, y in dead:
    egg3(o + x, y, DEAD, DEAD_D, hi=False)

# ---- frame 3, day 4: eggs splitting — a shell ring with a gap, a tail out of the gap, shells left ----
o = 48
def hatching(x, y, tail):
    """A split egg: rim ring with the right side open, the eye still inside, a tail out to the right."""
    egg3(x, y, RIPE, RIPE_D, hi=False)
    put(x + 1, y + 1, EYE)
    put(x + 2, y + 1, SHELL)                        # the split
    for i, (dx, dy) in enumerate(tail):
        put(x + 3 + dx, y + 1 + dy, BELLY if i % 2 == 0 else TAIL_D)

hatching(o + 2, 2, ((0, 0), (1, 0), (2, 1)))
hatching(o + 8, 4, ((0, 0), (1, -1), (2, -1)))
hatching(o + 2, 9, ((0, 0), (1, 1), (2, 1)))
hatching(o + 9, 10, ((0, 0), (1, 0), (2, 0)))
egg3(o + 12, 1, RIPE, RIPE_D, hi=False); put(o + 13, 2, EYE)      # one not yet split
egg3(o + 6, 12, DEAD, DEAD_D, hi=False)                             # one that never will
for x, y in ((12, 7), (5, 6)):                                       # empty shells: a broken ring
    put(o + x, y, SHELL); put(o + x + 1, y, SHELL); put(o + x, y + 1, SHELL); put(o + x + 2, y + 2, SHELL)

# ---- frame 4, hatched: a shoal of seven fry, silver, one black eye each, all heading right ----
o = 64
def fry(x, y, flip=False):
    """4 px long: back dark, flank silver, belly light, eye on the head end, a 1 px tail."""
    d = -1 if flip else 1
    put(x, y, TAIL_D); put(x + d, y, TAIL_D); put(x + 2 * d, y, TAIL_D)
    put(x, y + 1, BELLY); put(x + d, y + 1, TAIL); put(x + 2 * d, y + 1, TAIL)
    put(x + 3 * d, y + 1, EYE)                                       # the eye marks the head
    put(x - d, y, TAIL_D); put(x - d, y + 1, TAIL)                   # tail root: 5 px with the eye

for x, y, f in ((3, 2, False), (8, 1, False), (2, 6, False), (9, 5, False), (12, 8, True),
                (4, 10, False), (9, 12, False)):
    fry(o + x, y, f)


def png(path, rows, w, h):
    raw = b"".join(b"\x00" + b"".join(struct.pack("4B", *p) for p in row) for row in rows)

    def chunk(tag, data):
        c = tag + data
        return struct.pack(">I", len(data)) + c + struct.pack(">I", zlib.crc32(c) & 0xFFFFFFFF)

    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n")
        f.write(chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)))
        f.write(chunk(b"IDAT", zlib.compress(raw, 9)))
        f.write(chunk(b"IEND", b""))


# The check: every day looks like something, and no two days look the same.
_frames = [tuple(px[y][x] for y in range(H) for x in range(f * 16, f * 16 + 16)) for f in range(FRAMES)]
assert all(any(p != CLEAR for p in f) for f in _frames), "an empty frame"
assert len(set(_frames)) == FRAMES, "two frames are identical"

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(REPO, "common", "src", "main", "resources", "assets", "riverfishing",
                   "textures", "block", "aquarium_roe.png")
png(OUT, px, W, H)
print("%s  %dx%d" % (OUT, W, H))

if len(sys.argv) > 1:
    # 8x preview over gravel grey, alpha composited, a 2-px gutter between frames so they read apart.
    S, GUT, BG = 8, 2, (0x86, 0x84, 0x80)
    pw = W * S + GUT * (FRAMES - 1)
    prev = []
    for y in range(H):
        row = []
        for x in range(W):
            r, g, b, a = px[y][x]
            c = tuple((c1 * a + c0 * (255 - a)) // 255 for c0, c1 in zip(BG, (r, g, b))) + (255,)
            row += [c] * S
            if x % 16 == 15 and x != W - 1:
                row += [(0x20, 0x20, 0x20, 255)] * GUT
        prev += [row] * S
    png(sys.argv[1], prev, pw, H * S)
    print(sys.argv[1])
