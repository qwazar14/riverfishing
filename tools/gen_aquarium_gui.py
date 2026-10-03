#!/usr/bin/env python3
"""§aquarium-window (1.1.0): the tank's window as a cabinet — the Tackle Station's dark-oak frame and walnut
worktop, a pane of tank glass in a brass rim for the water, the fish and the modules, a groove for the
breeding run that breaks where the fish have to leave (roe -> incubation), a brass cup for the result, and
the inventory in a drawer. The parts the screen places itself sit off-panel to the right.

    python tools/gen_aquarium_gui.py

Writes textures/gui/aquarium.png (256x296, the 232x290 panel at 0,0). Every position here is a slot's or a
drawing's in menu/AquariumMenu.SLOT_XY and client/AquariumScreen — move one, move both. Seeded.
"""
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gen_tackle_bench as B   # the bench's wood, brass and recesses: one cabinet-maker for both

ROOT = os.path.dirname(HERE)
ASSETS = os.path.join(ROOT, "common/src/main/resources/assets/riverfishing")
OUT = os.path.join(ASSETS, "textures/gui/aquarium.png")
W, H = 232, 240   # §aquarium-fit: 240 is the least height the auto GUI scale promises
TEX_W, TEX_H = 256, 256
B.rng = np.random.default_rng(3107)
rng = B.rng

# ---- the panel (AquariumMenu.SLOT_XY is each well + 1) ----
TANK = (10, 16, 222, 70)
WATER = (16, 24)
TUBE = (38, 20, 45, 66)
FISH = [(70 + c * 24, 23 + r * 24) for r in range(2) for c in range(3)]
MODULES = [(172, 28), (196, 28)]
NODES = [26 + i * 38 for i in range(5)]
NODE_Y = 92
CUP = (201, 83)
FOOD, GROUNDBAIT = (16, 104), (36, 104)
BAR = (58, 108, 190, 118)
DRAWER = (157, 239)
PLATE = (76, 2, 156, 15)   # the nameplate, set into the top of the frame
INV_X = 35

# ---- off-panel sprites (x, y, w, h), mirrored in AquariumScreen ----
NODE_OFF, NODE_DONE, NODE_CUR = (232, 0, 15, 15), (232, 15, 15, 15), (232, 30, 15, 15)
GLOW = (232, 45, 21, 21)
HEART_ON, HEART_OFF = (232, 66, 7, 6), (240, 66, 7, 6)
GLASS_ON, GLASS_OFF = (248, 66, 5, 5), (248, 72, 5, 5)
BADGE = (232, 74, 7, 8)

HEART = ['.XX.XX.', 'XXXXXXX', 'XXXXXXX', '.XXXXX.', '..XXX..', '...X...']
HOURGLASS = ['XXXXX', '.XXX.', '..X..', '.X.X.', 'XXXXX']
BANG = ['XXXXXXX', 'XXX.XXX', 'XXX.XXX', 'XXX.XXX', 'XXXXXXX', 'XXX.XXX', 'XXXXXXX', '.......']


def glass(rgb, x0, y0, x1, y1):
    """A pane of tank glass: deep teal, lit from above, a soft ray or two, in a brass rim."""
    h, w = y1 - y0, x1 - x0
    ys = np.linspace(0, 1, h)[:, None]
    g = np.zeros((h, w, 3)) + np.array([22, 58, 64], float)
    g += (18 * (1 - ys))[..., None] * np.array([0.6, 1.0, 1.0])
    xs = np.arange(w)[None, :]
    ray = np.clip(1 - np.abs(((xs - ys * 30) % 70) - 35) / 9, 0, 1) * (1 - ys) * 10
    g += ray[..., None] * np.array([0.5, 1, 1])
    g += rng.normal(0, 1.6, (h, w))[..., None]
    rgb[y0:y1, x0:x1] = g
    rgb[y0, x0:x1] = [200, 160, 80]
    rgb[y1 - 1, x0:x1] = [120, 88, 38]
    rgb[y0:y1, x0] = [180, 140, 64]
    rgb[y0:y1, x1 - 1] = [120, 88, 38]
    rgb[y0 + 1, x0 + 1:x1 - 1] -= 20
    rgb[y0 + 1:y1 - 1, x0 + 1] -= 14


def glass_well(rgb, x, y):
    rgb[y:y + 18, x:x + 18] = [10, 30, 36]
    rgb[y, x:x + 18] = [5, 18, 22]
    rgb[y:y + 18, x] = [5, 18, 22]
    rgb[y + 17, x:x + 18] = [60, 118, 126]
    rgb[y:y + 18, x + 17] = [60, 118, 126]


def fish_icon(size):
    im = Image.open(os.path.join(ASSETS, "textures/item/fish/crucian_carp.png")).convert("RGBA")
    return np.asarray(im.resize((size, size), Image.NEAREST)).astype(float)


def panel():
    rgb = B.wood(H, W, (58, 38, 25), 0, 0.8)
    rgb[6:DRAWER[0], 6:W - 6] = B.wood(DRAWER[0] - 6, W - 12, (98, 68, 44), 20)
    rgb[DRAWER[0]:DRAWER[1], 6:W - 6] = B.wood(DRAWER[1] - DRAWER[0], W - 12, (86, 58, 38), 0, 0.9)
    rgb[0, :] = rgb[-1, :] = [24, 16, 10]
    rgb[:, 0] = rgb[:, -1] = [24, 16, 10]
    B.bevel(rgb, 1, 1, W - 1, H - 1, 30, -22)
    B.bevel(rgb, 6, 6, W - 6, H - 1, -30, 18)   # the drawer runs to the foot: the frame is 1 px there
    rgb[DRAWER[0], 6:W - 6] -= 16
    rgb[DRAWER[0] + 1, 6:W - 6] += 18
    B.bevel(rgb, 8, DRAWER[0] + 2, W - 8, DRAWER[1], 12, -18)
    for row in range(3):
        for col in range(9):
            B.recess(rgb, INV_X - 1 + col * 18, DRAWER[0] + 5 + row * 18, INV_X + 16 + col * 18, DRAWER[0] + 22 + row * 18, 34)
    for col in range(9):
        B.recess(rgb, INV_X - 1 + col * 18, DRAWER[0] + 63, INV_X + 16 + col * 18, DRAWER[0] + 80, 34)
    for cx, cy in ((1, 1), (W - 12, 1), (1, H - 12), (W - 12, H - 12)):
        B.brass(rgb, cx, cy, cx + 11, cy + 11)
        B.screw(rgb, cx + 5, cy + 5)
    # the nameplate the title is engraved on, set into the top of the frame
    x0, y0, x1, y1 = PLATE
    B.brass(rgb, x0, y0, x1, y1)
    rgb[y0 + 2:y1 - 2, x0 + 2:x1 - 2] -= 16
    B.screw(rgb, x0 + 3, (y0 + y1) // 2)
    B.screw(rgb, x1 - 3, (y0 + y1) // 2)

    glass(rgb, *TANK)
    glass_well(rgb, *WATER)
    for x, y in FISH:
        glass_well(rgb, x, y)
    for x, y in MODULES:
        B.brass(rgb, x - 2, y - 2, x + 20, y + 20, domed=False)
        glass_well(rgb, x, y)
    x0, y0, x1, y1 = TUBE                               # the test tube the water reads on
    rgb[y0:y1, x0:x1] = [14, 34, 40]
    rgb[y0:y1, x0] += 40
    rgb[y0:y1, x1 - 1] += 20
    rgb[y1 - 1, x0:x1] += 30

    # the breeding run: a groove from node to node, broken between roe and incubation, where the fish leave
    for a, b in ((NODES[0], NODES[2]), (NODES[3], NODES[-1]), (NODES[-1], CUP[0] - 4)):
        rgb[NODE_Y - 1:NODE_Y + 2, a:b] -= 38
        rgb[NODE_Y + 2, a:b] += 12
    for x in range(NODES[2] + 9, NODES[3] - 8, 3):
        rgb[NODE_Y, x] -= 40
    ys, xs = np.ogrid[:H, :W]
    for nx in NODES:
        r = np.hypot(ys - NODE_Y, xs - nx)
        rgb[r < 8.5] = [120, 88, 38]
        rgb[r < 7.5] = [52, 36, 24]
    # the hand-off: a fish struck through, so the rule is on the board before it is needed
    fx, fy = (NODES[2] + NODES[3]) // 2 - 5, NODE_Y - 5
    ic = fish_icon(10)
    a = ic[..., 3:4] / 255 * 0.9
    rgb[fy:fy + 10, fx:fx + 10] = rgb[fy:fy + 10, fx:fx + 10] * (1 - a) + ic[..., :3] * a
    for k in range(11):
        rgb[NODE_Y + 4 - k:NODE_Y + 6 - k, fx + k] = [210, 60, 50]
    cup = np.zeros((26, 26, 3))
    B.brass(cup, 0, 0, 26, 26)
    rgb[CUP[1] - 4:CUP[1] + 22, CUP[0] - 4:CUP[0] + 22] = cup
    B.recess(rgb, CUP[0], CUP[1], CUP[0] + 18, CUP[1] + 18, 40)

    for x, y in (FOOD, GROUNDBAIT):
        B.recess(rgb, x, y, x + 18, y + 18, 40)
    x0, y0, x1, y1 = BAR
    rgb[y0:y1, x0:x1] = [34, 22, 14]
    rgb[y0, x0:x1] -= 10
    rgb[y1 - 1, x0:x1] += 30
    return rgb


def disc(size, fill, alpha_ring=None):
    img = np.zeros((size, size, 4))
    c = (size - 1) / 2
    ys, xs = np.ogrid[:size, :size]
    r = np.hypot(ys - c, xs - c)
    if alpha_ring:
        m = (r > c - 1.2) & (r <= c + 0.2)
        img[m] = list(fill) + [alpha_ring]
    else:
        m = r <= c + 0.2
        img[m] = list(fill) + [255]
        img[(r > c - 1.2) & m, :3] *= 0.8                 # a darker lip round the edge
    return img


def pixels(pattern, rgb):
    h, w = len(pattern), len(pattern[0])
    img = np.zeros((h, w, 4))
    for y, row in enumerate(pattern):
        for x, v in enumerate(row):
            if v == 'X':
                img[y, x] = list(rgb) + [255]
    return img


def main():
    tex = np.zeros((TEX_H, TEX_W, 4))
    tex[:H, :W, :3] = panel()
    tex[:H, :W, 3] = 255

    def put(rect, img):
        x, y, w, h = rect
        tex[y:y + h, x:x + w] = img

    put(NODE_OFF, disc(15, (66, 46, 30)))
    put(NODE_DONE, disc(15, (214, 172, 84)))
    put(NODE_CUR, disc(15, (250, 214, 120)))
    put(GLOW, disc(21, (255, 230, 150), 140))
    put(HEART_ON, pixels(HEART, (200, 60, 110)))
    put(HEART_OFF, pixels(HEART, (120, 80, 70)))
    put(GLASS_ON, pixels(HOURGLASS, (60, 40, 20)))
    put(GLASS_OFF, pixels(HOURGLASS, (130, 100, 70)))
    badge = pixels(BANG, (200, 50, 40))
    for y, row in enumerate(BANG):
        for x, v in enumerate(row):
            if v == '.' and y < 7:
                badge[y, x] = [255, 240, 220, 255]
    put(BADGE, badge)

    Image.fromarray(np.clip(tex, 0, 255).astype(np.uint8), "RGBA").save(OUT)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
