#!/usr/bin/env python3
"""The Tackle Station's bench: a walnut worktop in a dark-oak frame with brass corner plates, a rail for the
material wells and a drawer for the inventory — drawn 1:1 at the screen's own size (248x264 GUI pixels) —
and, below it on the same sheet, the parts the screen places itself: the brass-framed tab labels, the tray
compartments of the form grid, the wells, the result cup, the hook-picker plate and the paper tag the
item's name and bill are written on.

    python tools/gen_tackle_bench.py

Writes textures/gui/tackle_station/bench.png (256x400). Seeded, so a rerun draws the same bench. The
sprite rectangles are TackleStationScreen's SPRITE constants — move one here, move it there.
"""
import os
import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "common/src/main/resources/assets/riverfishing/textures/gui/tackle_station")
W, H = 248, 264                     # TackleStationScreen.imageWidth x imageHeight
TEX_W, TEX_H = 256, 400
rng = np.random.default_rng(2611)

# sprite sheet, all below the bench (x, y, w, h) — mirrored in TackleStationScreen
TAG = (0, 264, 112, 117)
TAB_ON, TAB_OFF = (120, 264, 64, 14), (120, 280, 64, 14)
CELL, CELL_SEL = (188, 264, 20, 20), (210, 264, 20, 20)
WELL, CUP = (188, 286, 18, 18), (210, 286, 26, 26)
PLATE = (232, 264, 18, 18)
KNOB = (120, 296, 7, 11)

# the rows the screen puts things on
RAIL = (144, 176)                   # the wells' rail: wells at y 149..167, their counts at 169
DRAWER = (176, 258)                 # inventory rows at 179/197/215, hotbar at 239


def smooth_noise(h, w, cy, cx):
    """Value noise on a (cy x cx) grid, bilinearly upsampled. -1..1. Long cx = grain along x."""
    gh, gw = int(h // cy) + 2, int(w // cx) + 2
    grid = rng.uniform(-1, 1, (gh, gw))
    ys, xs = np.arange(h) / cy, np.arange(w) / cx
    y0, x0 = ys.astype(int), xs.astype(int)
    fy, fx = (ys - y0)[:, None], (xs - x0)[None, :]
    a, b = grid[y0][:, x0], grid[y0][:, x0 + 1]
    c, d = grid[y0 + 1][:, x0], grid[y0 + 1][:, x0 + 1]
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


def wood(h, w, base, plank=0, grain=1.0):
    """Planks running along x: each its own tone, streaked grain, a dark seam between, the odd knot."""
    rgb = np.zeros((h, w, 3))
    rgb[:] = base
    streak = smooth_noise(h, w, 1.3, 38) * 9 * grain + smooth_noise(h, w, 3, 90) * 6 * grain
    rgb += (streak + rng.normal(0, 2.2, (h, w)))[..., None]
    if plank:
        for top in range(0, h, plank):
            rgb[top:top + plank] += rng.uniform(-7, 7)
            if top:
                rgb[top] -= 26
                rgb[top - 1] += 6 if top > 1 else 0
        for _ in range(max(1, h * w // 9000)):             # knots: a dark eye with rings round it
            ky, kx = rng.integers(3, h - 3), rng.integers(6, w - 6)
            ys, xs = np.ogrid[:h, :w]
            r = np.hypot((ys - ky) * 1.9, (xs - kx) * 0.55)
            rgb += (np.where(r < 2.2, -30, 0) + np.where((r > 3) & (r < 7), np.sin(r * 2.2) * 5, 0))[..., None]
    return rgb


def bevel(rgb, x0, y0, x1, y1, light=22, dark=-26, width=1):
    for i in range(width):
        rgb[y0 + i, x0 + i:x1 - i] += light
        rgb[y0 + i:y1 - i, x0 + i] += light
        rgb[y1 - 1 - i, x0 + i:x1 - i] += dark
        rgb[y0 + i:y1 - i, x1 - 1 - i] += dark


def recess(rgb, x0, y0, x1, y1, depth=46):
    """A hole cut into the wood: darker inside, shadow on its top and left wall, a lit lip on the far side."""
    rgb[y0:y1, x0:x1] -= depth
    rgb[y0, x0:x1] -= 20
    rgb[y0:y1, x0] -= 20
    rgb[y0 + 1, x0 + 1:x1] -= 9
    rgb[y0 + 1:y1, x0 + 1] -= 9
    rgb[y1 - 1, x0 + 1:x1] += 14
    rgb[y0 + 1:y1, x1 - 1] += 14


BRASS = np.array([190, 146, 62], float)


def brass(rgb, x0, y0, x1, y1, domed=True):
    h, w = y1 - y0, x1 - x0
    ys = np.linspace(-1, 1, h)[:, None]
    xs = np.linspace(-1, 1, w)[None, :]
    shade = -ys * 26 - xs * 10 if domed else -ys * 14
    rgb[y0:y1, x0:x1] = BRASS + shade[..., None] + rng.normal(0, 3, (h, w))[..., None]
    rgb[y0, x0:x1] += 40
    rgb[y1 - 1, x0:x1] -= 50
    rgb[y0:y1, x0] += 18
    rgb[y0:y1, x1 - 1] -= 36


def screw(rgb, x, y):
    rgb[y - 1:y + 2, x - 1:x + 2] = [92, 70, 34]
    rgb[y - 1, x - 1:x + 2] += 40
    rgb[y, x - 1:x + 2] = [150, 116, 52]


def bench():
    rgb = np.zeros((H, W, 3))
    # the frame: dark oak, the grain running round it
    rgb[:] = wood(H, W, (58, 38, 25), 0, 0.8)
    # the worktop: walnut planks, a rail, a drawer
    top = wood(RAIL[0] - 24, W - 12, (98, 68, 44), 20)
    rgb[24:RAIL[0], 6:W - 6] = top
    rgb[RAIL[0]:RAIL[1], 6:W - 6] = wood(RAIL[1] - RAIL[0], W - 12, (74, 50, 33), 0, 0.7)
    rgb[DRAWER[0]:DRAWER[1], 6:W - 6] = wood(DRAWER[1] - DRAWER[0], W - 12, (86, 58, 38), 0, 0.9)
    # the tab shelf behind the labels
    rgb[6:24, 6:W - 6] = wood(18, W - 12, (66, 44, 29), 0, 0.6)

    # frame bevels: the outer edge catches light, the inner lip falls into shadow
    rgb[0, :] = [24, 16, 10]; rgb[-1, :] = [24, 16, 10]; rgb[:, 0] = [24, 16, 10]; rgb[:, -1] = [24, 16, 10]
    bevel(rgb, 1, 1, W - 1, H - 1, 30, -22)
    bevel(rgb, 6, 6, W - 6, H - 6, -30, 18)                 # the frame's inner edge: a step down
    # the shelf under the tabs drops onto the worktop
    rgb[23, 6:W - 6] -= 30
    rgb[24, 6:W - 6] -= 14
    # the rail stands proud of the worktop; the drawer front has a lip and a shadow under the rail
    rgb[RAIL[0], 6:W - 6] += 26
    rgb[RAIL[0] - 1, 6:W - 6] -= 24
    rgb[RAIL[1] - 1, 6:W - 6] -= 28
    rgb[DRAWER[0], 6:W - 6] -= 16
    rgb[DRAWER[0] + 1, 6:W - 6] += 18
    bevel(rgb, 8, DRAWER[0] + 2, W - 8, DRAWER[1] - 2, 12, -18)

    # the inventory's pockets: 3 rows and the hotbar
    for row in range(3):
        for col in range(9):
            recess(rgb, 42 + col * 18, 179 + row * 18, 59 + col * 18, 196 + row * 18, 34)
    for col in range(9):
        recess(rgb, 42 + col * 18, 239, 59 + col * 18, 256, 34)

    # brass: corner plates, drawer pulls
    for cx, cy in ((1, 1), (W - 12, 1), (1, H - 12), (W - 12, H - 12)):
        brass(rgb, cx, cy, cx + 11, cy + 11)
        screw(rgb, cx + 5, cy + 5)
    for px in (14, W - 34):
        brass(rgb, px, 226, px + 20, 231)
        rgb[231:233, px + 2:px + 18] -= 30                 # the pull's shadow on the drawer
        screw(rgb, px + 2, 228)
        screw(rgb, px + 17, 228)
    return rgb


def tag():
    """A manila shipping tag: fibrous card, dog-eared corners cut off, a brass grommet on the left edge."""
    x, y, w, h = TAG
    rgb = np.zeros((h, w, 3))
    rgb[:] = (224, 204, 158)
    rgb += (smooth_noise(h, w, 6, 6) * 6 + rng.normal(0, 3.2, (h, w)))[..., None]
    fibres = (rng.random((h, w)) < 0.025) * rng.uniform(-18, 10, (h, w))
    rgb += fibres[..., None]
    # age at the edges
    ys, xs = np.ogrid[:h, :w]
    edge = np.minimum(np.minimum(ys, h - 1 - ys), np.minimum(xs, w - 1 - xs))
    rgb -= np.clip(6 - edge, 0, 6)[..., None] * 4
    a = np.full((h, w), 255.0)
    for cy, cx in ((0, 0), (h - 1, 0)):                     # the left end's corners cut off
        cut = (np.abs(ys - cy) + np.abs(xs - cx)) < 7
        a[cut] = 0
    # grommet: a brass ring round a hole, halfway down the left end
    gy, gx = h // 2, 4
    r = np.hypot(ys - gy, xs - gx)
    ring = (r >= 1.6) & (r < 3.6)
    rgb[ring] = BRASS
    rgb[(r >= 1.6) & (r < 2.4)] -= 30
    a[r < 1.6] = 0
    # a faint ruled line under the name
    rgb[16, 10:w - 6] -= 22
    # drop shadow on the bench is drawn by the screen; the tag itself stays square-edged
    return rgb, a


def tab(active):
    x, y, w, h = TAB_ON if active else TAB_OFF
    rgb = np.zeros((h, w, 3))
    brass(rgb, 0, 0, w, h, domed=False)
    inner = (226, 206, 160) if active else (150, 128, 92)
    rgb[2:h - 2, 2:w - 2] = inner
    rgb[2:h - 2, 2:w - 2] += (smooth_noise(h - 4, w - 4, 4, 4) * 5 + rng.normal(0, 2.5, (h - 4, w - 4)))[..., None]
    rgb[2, 2:w - 2] -= 22                                   # the label sits under the frame's lip
    rgb[2:h - 2, 2] -= 16
    return rgb


def cell(selected):
    x, y, w, h = CELL_SEL if selected else CELL
    rgb = wood(h, w, (70, 47, 31), 0, 0.5)
    if selected:
        brass(rgb, 0, 0, w, h, domed=False)
        rgb[2:h - 2, 2:w - 2] = wood(h - 4, w - 4, (112, 80, 50), 0, 0.5)
        recess(rgb, 2, 2, w - 2, h - 2, -6)
    else:
        bevel(rgb, 0, 0, w, h, 16, -20)
        recess(rgb, 1, 1, w - 1, h - 1, 26)
    return rgb


def well():
    x, y, w, h = WELL
    rgb = wood(h, w, (74, 50, 33), 0, 0.6)
    recess(rgb, 0, 0, w, h, 38)
    return rgb


def cup():
    """The result: a brass cup set into the rail round an 18px well."""
    x, y, w, h = CUP
    rgb = np.zeros((h, w, 3))
    brass(rgb, 0, 0, w, h)
    ys, xs = np.ogrid[:h, :w]
    a = np.full((h, w), 255.0)
    for cy, cx in ((0, 0), (0, w - 1), (h - 1, 0), (h - 1, w - 1)):
        a[(np.abs(ys - cy) + np.abs(xs - cx)) < 3] = 0
    rgb[4:h - 4, 4:w - 4] = wood(h - 8, w - 8, (60, 40, 27), 0, 0.5)
    recess(rgb, 4, 4, w - 4, h - 4, 10)
    return rgb, a


def plate():
    x, y, w, h = PLATE
    rgb = np.zeros((h, w, 3))
    brass(rgb, 0, 0, w, h)
    rgb[2:h - 2, 2:w - 2] -= 34                             # engraved field the hook lies on
    rgb[2, 2:w - 2] -= 18
    return rgb


def knob():
    x, y, w, h = KNOB
    rgb = np.zeros((h, w, 3))
    brass(rgb, 0, 0, w, h)
    rgb[h // 2, 1:w - 1] -= 30                               # a grip line across it
    return rgb


def main():
    img = np.zeros((TEX_H, TEX_W, 4))
    img[:H, :W, :3] = bench()
    img[:H, :W, 3] = 255

    def put(rect, rgb, alpha=None):
        x, y, w, h = rect
        img[y:y + h, x:x + w, :3] = rgb
        img[y:y + h, x:x + w, 3] = 255 if alpha is None else alpha

    t, ta = tag()
    put(TAG, t, ta)
    put(TAB_ON, tab(True))
    put(TAB_OFF, tab(False))
    put(CELL, cell(False))
    put(CELL_SEL, cell(True))
    put(WELL, well())
    c, ca = cup()
    put(CUP, c, ca)
    put(PLATE, plate())
    put(KNOB, knob())

    os.makedirs(OUT, exist_ok=True)
    out = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA")
    out.save(os.path.join(OUT, "bench.png"))
    print("wrote", os.path.join(OUT, "bench.png"))


if __name__ == "__main__":
    main()
