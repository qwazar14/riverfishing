#!/usr/bin/env python3
"""The journal's book: a leather cover with a stitched edge and brass corners around one parchment page,
and the leather bookmark the four sections hang from. Drawn 1:1 at the journal's own size (470x420 GUI
pixels), so nothing is stretched — the old 64x64 nine-slice smeared its stripes across the whole page.

    python tools/gen_journal_book.py

Writes textures/gui/journal/book.png (512x512, the book at 0,0) and textures/gui/journal/bookmark.png
(100x22, grey — the screen tints it per section). Seeded, so a rerun draws the same book.
"""
import os
import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "common/src/main/resources/assets/riverfishing/textures/gui/journal")
W, H = 470, 420          # JournalScreen.MAX_W x H
COVER = 7                # leather on every side
STACK = 3                # page edges showing on the right and the bottom
rng = np.random.default_rng(1405)


def smooth_noise(h, w, cell):
    """Value noise: a coarse random grid, bilinearly upsampled. -1..1."""
    gh, gw = h // cell + 2, w // cell + 2
    grid = rng.uniform(-1, 1, (gh, gw))
    ys = np.arange(h) / cell
    xs = np.arange(w) / cell
    y0, x0 = ys.astype(int), xs.astype(int)
    fy, fx = (ys - y0)[:, None], (xs - x0)[None, :]
    a = grid[y0][:, x0]
    b = grid[y0][:, x0 + 1]
    c = grid[y0 + 1][:, x0]
    d = grid[y0 + 1][:, x0 + 1]
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


def rounded_mask(h, w, r):
    m = np.ones((h, w), bool)
    for cy, cx in ((r, r), (r, w - 1 - r), (h - 1 - r, r), (h - 1 - r, w - 1 - r)):
        ys, xs = np.ogrid[:h, :w]
        corner = ((ys < r) if cy == r else (ys > h - 1 - r)) & ((xs < r) if cx == r else (xs > w - 1 - r))
        m &= ~(corner & ((ys - cy) ** 2 + (xs - cx) ** 2 > r * r + r))
    return m


def book():
    img = np.zeros((512, 512, 4), float)
    rgb = np.zeros((H, W, 3), float)

    # ---- leather: warm brown, pebbled, blotchy ----
    leather = np.array([78, 48, 29], float)
    blot = smooth_noise(H, W, 18) * 7 + smooth_noise(H, W, 5) * 4
    grain = rng.normal(0, 4.5, (H, W))
    pebble = (rng.random((H, W)) < 0.06) * -10
    rgb[:] = leather
    rgb += (blot + grain + pebble)[..., None]

    # bevel: light on the top/left rim, dark on the bottom/right
    rgb[1, 1:-1] += 26
    rgb[1:-1, 1] += 20
    rgb[-2, 1:-1] -= 22
    rgb[1:-1, -2] -= 22

    # stitching, 3 px in, dash 3 gap 2, with its own shadow
    s = 3
    thread = np.array([200, 166, 112], float)
    for x in range(s + 2, W - s - 2):
        if (x - s) % 5 < 3:
            rgb[s, x] = thread; rgb[s + 1, x] -= 18
            rgb[H - 1 - s, x] = thread; rgb[H - s, x] -= 18
    for y in range(s + 2, H - s - 2):
        if (y - s) % 5 < 3:
            rgb[y, s] = thread; rgb[y, s + 1] -= 18
            rgb[y, W - 1 - s] = thread; rgb[y, W - s] -= 18

    # ---- page edges: a few leaves showing under the top one, right and bottom ----
    px0, py0, px1, py1 = COVER, COVER, W - COVER, H - COVER
    leaves = [np.array(c, float) for c in ((222, 208, 176), (188, 170, 134), (214, 199, 165))]
    for i in range(STACK):
        rgb[py0 + 1 + i:py1, px1 - 1 - i] = leaves[i]
        rgb[py1 - 1 - i, px0 + 1 + i:px1] = leaves[i]
    rgb[py0:py1, px1] = (60, 38, 22)            # the cover's shadow under the leaves
    rgb[py1, px0:px1 + 1] = (60, 38, 22)

    # ---- the page ----
    ph, pw = py1 - STACK - py0, px1 - STACK - px0
    base = np.array([230, 217, 187], float)
    page = np.zeros((ph, pw, 3)) + base
    page += (smooth_noise(ph, pw, 40) * 5 + smooth_noise(ph, pw, 11) * 2.5)[..., None]
    page += rng.normal(0, 1.6, (ph, pw))[..., None]
    # fibres: faint horizontal streaks
    fib = smooth_noise(ph, 1, 3)[:, 0] * 1.8
    page += fib[:, None, None]
    # edges darken to a warm tan, the way old paper does
    ys, xs = np.mgrid[:ph, :pw]
    edge = np.minimum(np.minimum(ys, ph - 1 - ys), np.minimum(xs, pw - 1 - xs)).astype(float)
    age = np.clip(1 - edge / 18.0, 0, 1) ** 1.6
    page -= (age * 24)[..., None] * np.array([0.8, 1.0, 1.35])
    # foxing: a few pale brown spots
    for _ in range(26):
        cy, cx = rng.integers(6, ph - 6), rng.integers(6, pw - 6)
        r = rng.uniform(0.8, 2.6)
        d = np.sqrt((ys - cy) ** 2 + (xs - cx) ** 2)
        k = np.clip(1 - d / r, 0, 1) * rng.uniform(5, 11)
        page -= k[..., None] * np.array([0.6, 0.9, 1.4])
    # the page sits under the cover's lip: a hairline shadow on its top and left
    page[0, :] -= 26
    page[:, 0] -= 22
    page[1, :] -= 9
    page[:, 1] -= 7
    rgb[py0:py0 + ph, px0:px0 + pw] = page

    # ---- brass corners ----
    brass_hi, brass, brass_lo = np.array([232, 196, 108.]), np.array([186, 142, 62.]), np.array([116, 82, 30.])
    L = 12
    for flip_y in (False, True):
        for flip_x in (False, True):
            for y in range(L):
                for x in range(L):
                    if x + y > L:
                        continue
                    yy = H - 1 - y if flip_y else y
                    xx = W - 1 - x if flip_x else x
                    c = brass.copy()
                    if y <= 1 or x <= 1:
                        c = brass_hi
                    elif x + y >= L - 1:
                        c = brass_lo
                    rgb[yy, xx] = c + rng.normal(0, 3)
            # a rivet
            ry = H - 1 - 4 if flip_y else 4
            rx = W - 1 - 4 if flip_x else 4
            rgb[ry, rx] = brass_lo
            rgb[ry - (1 if not flip_y else -1), rx - (1 if not flip_x else -1)] = brass_hi + 15

    # outline + rounded corners
    rgb[0, :] = rgb[-1, :] = (30, 18, 10)
    rgb[:, 0] = rgb[:, -1] = (30, 18, 10)
    mask = rounded_mask(H, W, 5)
    img[:H, :W, :3] = rgb
    img[:H, :W, 3] = mask * 255
    return img


def bookmark():
    bw, bh = 100, 22
    rgb = np.zeros((bh, bw, 3)) + 205.0
    rgb += (smooth_noise(bh, bw, 6) * 7 + rng.normal(0, 4, (bh, bw)))[..., None]
    rgb[1, :] += 30
    rgb[2, :] += 12
    rgb[-3, :] -= 30
    rgb[-2, :] -= 50
    for x in range(6, bw):
        if x % 5 < 3:
            rgb[3, x] = 250
            rgb[bh - 5, x] = 250
            rgb[bh - 4, x] -= 30
    for y in range(6, bh - 6):
        if y % 5 < 3:
            rgb[y, 3] = 250
    rgb[0, :] = rgb[-1, :] = 58
    rgb[:, 0] = 58
    img = np.zeros((bh, bw, 4))
    img[..., :3] = rgb
    m = rounded_mask(bh, bw + 8, 4)[:, :bw]   # rounded on the left only; the right end is under the book
    img[..., 3] = m * 255
    return img


def save(a, name):
    Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGBA").save(os.path.join(OUT, name))


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    save(book(), "book.png")
    save(bookmark(), "bookmark.png")
    print("book.png, bookmark.png ->", OUT)
