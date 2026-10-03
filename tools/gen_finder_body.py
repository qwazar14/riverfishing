#!/usr/bin/env python3
"""The fish finder's body: a rugged graphite casing with a recessed screen, corner screws, an info plate
under the glass and a speaker grille — and the rubber key its three views are picked with. Drawn 1:1 at the
screen's own size (440x252 GUI pixels), so nothing is stretched.

    python tools/gen_finder_body.py

Writes textures/gui/finder/body.png (512x256, the body at 0,0) and textures/gui/finder/key.png (84x20).
The screen's glass is left for the code to fill: the section, the chart and the sample each paint it.
Seeded, so a rerun draws the same device.
"""
import os
import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "common/src/main/resources/assets/riverfishing/textures/gui/finder")
W, H = 440, 252               # FinderScreen.W x H
SX0, SY0, SX1, SY1 = 10, 30, 430, 180   # the glass: VIEW_X, VIEW_Y .. W - VIEW_X, VIEW_Y + VIEW_H
rng = np.random.default_rng(2207)


def smooth_noise(h, w, cell):
    gh, gw = h // cell + 2, w // cell + 2
    grid = rng.uniform(-1, 1, (gh, gw))
    ys, xs = np.arange(h) / cell, np.arange(w) / cell
    y0, x0 = ys.astype(int), xs.astype(int)
    fy, fx = (ys - y0)[:, None], (xs - x0)[None, :]
    a, b = grid[y0][:, x0], grid[y0][:, x0 + 1]
    c, d = grid[y0 + 1][:, x0], grid[y0 + 1][:, x0 + 1]
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


def rounded_mask(h, w, r):
    ys, xs = np.ogrid[:h, :w]
    m = np.ones((h, w), bool)
    for cy, cx in ((r, r), (r, w - 1 - r), (h - 1 - r, r), (h - 1 - r, w - 1 - r)):
        corner = ((ys < r) if cy == r else (ys > h - 1 - r)) & ((xs < r) if cx == r else (xs > w - 1 - r))
        m &= ~(corner & ((ys - cy) ** 2 + (xs - cx) ** 2 > r * r + r))
    return m


def body():
    rgb = np.zeros((H, W, 3))
    base = np.array([38, 48, 44.])
    rgb[:] = base
    # a moulded, faintly textured plastic, a touch lighter toward the top
    rgb += (smooth_noise(H, W, 30) * 3 + rng.normal(0, 1.6, (H, W)))[..., None]
    rgb += np.linspace(8, -6, H)[:, None, None]
    # rim: lit above, shaded below
    rgb[1:3, :] += 28
    rgb[:, 1:3] += 16
    rgb[-3:-1, :] -= 18
    rgb[:, -3:-1] -= 14

    # the recessed glass: a dark bezel round it, lit on its lower lip where the case turns toward you
    b = 5
    rgb[SY0 - b:SY1 + b, SX0 - b:SX1 + b] = np.array([18, 23, 21.])
    rgb[SY0 - b:SY0 - b + 2, SX0 - b:SX1 + b] -= 8          # shadow under the top lip
    rgb[SY1 + b - 1, SX0 - b:SX1 + b] += 30                 # the lit lower lip
    rgb[SY0 - b:SY1 + b, SX1 + b - 1] += 16
    rgb[SY0:SY1, SX0:SX1] = np.array([11, 30, 34.])          # the glass itself (the code paints over it)

    # the info plate under the glass
    py0, py1, px0, px1 = SY1 + 10, H - 8, 8, W - 8
    rgb[py0:py1, px0:px1] = np.array([30, 38, 35.]) + rng.normal(0, 1.2, (py1 - py0, px1 - px0))[..., None]
    rgb[py0, px0:px1] -= 14
    rgb[py1 - 1, px0:px1] += 12

    # the name plate top left, and the lamp's window top right (the code lights it)
    rgb[6:20, 14:132] = np.array([27, 34, 31.])
    rgb[6, 14:132] -= 10
    rgb[19, 14:132] += 12
    rgb[10:15, W - 26:W - 21] = (10, 12, 11)
    rgb[15, W - 26:W - 21] += 20
    # a thin amber strip across the top, the colour rugged gear wears to be found in the grass
    rgb[22:24, 140:W - 34] = (150, 104, 40)
    rgb[22, 140:W - 34] += 30

    # screws in the corners: a dished head with a slot
    for cy, cx in ((7, 7), (7, W - 8), (H - 8, 7), (H - 8, W - 8)):
        for dy in range(-2, 3):
            for dx in range(-2, 3):
                if dx * dx + dy * dy <= 5:
                    rgb[cy + dy, cx + dx] = (96, 104, 100) if dy < 0 else (70, 76, 73)
        rgb[cy, cx - 1:cx + 2] = (40, 44, 42)

    # a speaker grille, bottom right of the plate
    for gy in range(4):
        for gx in range(8):
            yy, xx = H - 20 + gy * 3, W - 44 + gx * 4
            rgb[yy, xx] = (14, 18, 16)
            rgb[yy + 1, xx] = (52, 62, 58)

    rgb[0, :] = rgb[-1, :] = (12, 15, 14)
    rgb[:, 0] = rgb[:, -1] = (12, 15, 14)
    img = np.zeros((256, 512, 4))
    img[:H, :W, :3] = rgb
    img[:H, :W, 3] = rounded_mask(H, W, 9) * 255
    return img


def key():
    kw, kh = 84, 20
    rgb = np.zeros((kh, kw, 3)) + np.array([46, 54, 51.])
    rgb += rng.normal(0, 2.2, (kh, kw))[..., None]   # rubber
    rgb[1, :] += 26
    rgb[2, :] += 10
    rgb[-2, :] -= 16
    rgb[-3, :] -= 8
    rgb[:, 0] = rgb[0, :] = rgb[-1, :] = (14, 17, 16)
    rgb[3:kh - 3, 3:6] = (20, 24, 22)                  # the lamp's window (lit by the code)
    img = np.zeros((kh, kw, 4))
    img[..., :3] = rgb
    img[..., 3] = rounded_mask(kh, kw + 8, 4)[:, :kw] * 255
    return img


def save(a, name):
    Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGBA").save(os.path.join(OUT, name))


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    save(body(), "body.png")
    save(key(), "key.png")
    print("body.png, key.png ->", OUT)
