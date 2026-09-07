# -*- coding: utf-8 -*-
"""§fight-bar: the fight's boss bar, drawn rather than painted — a placeholder for the artist's sheet.

    py tools/gen_fight_bar.py [tree ...]

Writes assets/riverfishing/textures/gui/fight_bar.png, a 256x96 sheet. FightBarHud reads these regions:

    frame     (0,0)    256x56  carved oak frame; the WINDOW (28,20)-(228,40) is transparent — the water shows through
    plate     (78,0)   100x14  the name plate, part of the frame (two floats flank it)
    wave      (0,56)   40x20   the filled water, tiled left to right and scrolled a pixel a tick
    deep      (40,56)  40x20   the unfilled water, tiled
    fish      (80,56)  24x16   the fish silhouette that rides the fill's edge, head to the LEFT
    fish2     (104,56) 24x16   the same, tail the other way (two frames, alternated while it runs)
    sign      (0,76)   88x20   the stone plate the cue is printed on (its chains hang from the frame)

Every region is chrome only: the fill is clipped to the land progress at runtime, the name and the cue
are text. Repaint any region in place and the HUD picks it up — nothing here is measured from the pixels.
"""
import math, os, sys
from PIL import Image, ImageDraw

W, H = 256, 96
OUT = "common/src/main/resources/assets/riverfishing/textures/gui/fight_bar.png"

WOOD_SHADOW = (0x21, 0x16, 0x0D)
WOOD_DARK = (0x3A, 0x26, 0x16)
WOOD_BASE = (0x5C, 0x3F, 0x26)
WOOD_MID = (0x74, 0x50, 0x30)
WOOD_LIGHT = (0x8E, 0x66, 0x3E)
WOOD_HI = (0xA8, 0x7E, 0x50)
BRASS_DARK = (0x6E, 0x54, 0x22)
BRASS = (0xB0, 0x8D, 0x3C)
BRASS_HI = (0xE0, 0xC0, 0x6A)
CHAIN = (0x5A, 0x5E, 0x66)
CHAIN_HI = (0x8A, 0x90, 0x9A)
STONE_DARK = (0x5E, 0x5C, 0x56)
STONE = (0x8C, 0x89, 0x80)
STONE_HI = (0xB4, 0xB0, 0xA4)
TEAL_DEEP = (0x0B, 0x3E, 0x42)
TEAL = (0x17, 0x7A, 0x74)
TEAL_HI = (0x3F, 0xC9, 0xA8)
TEAL_FOAM = (0xA8, 0xF0, 0xDC)
DEEP0 = (0x0C, 0x1A, 0x26)
DEEP1 = (0x12, 0x2A, 0x3A)
DEEP2 = (0x1B, 0x3A, 0x4C)
FISH = (0x2E, 0x8C, 0x7A)
FISH_HI = (0x8F, 0xE9, 0xC9)
FISH_EYE = (0x10, 0x20, 0x24)
FLOAT_RED = (0xD8, 0x3A, 0x2E)
FLOAT_WHITE = (0xF2, 0xEE, 0xE2)


def hash01(x, y, salt=0):
    n = (x * 374761393 + y * 668265263 + salt * 1274126177) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c)


def plank(d, x0, y0, x1, y1, salt=0):
    """A plank: base tone, grain lines, bevelled edges (light top-left, dark bottom-right)."""
    for y in range(y0, y1):
        for x in range(x0, x1):
            g = hash01(x // 3, y, salt)
            c = WOOD_BASE if g < 0.55 else WOOD_MID if g < 0.85 else WOOD_DARK
            if (y * 7 + x // 9) % 11 == 0: c = WOOD_DARK
            d.point((x, y), c)
    d.line([(x0, y0), (x1 - 1, y0)], WOOD_HI); d.line([(x0, y0), (x0, y1 - 1)], WOOD_LIGHT)
    d.line([(x0, y1 - 1), (x1 - 1, y1 - 1)], WOOD_SHADOW); d.line([(x1 - 1, y0), (x1 - 1, y1 - 1)], WOOD_DARK)


def rivet(d, x, y):
    d.point((x, y), BRASS); d.point((x + 1, y), BRASS_DARK); d.point((x, y + 1), BRASS_DARK); d.point((x - 1, y - 1), BRASS_HI)


def scales(d, x0, y0, x1, y1, salt=0):
    """The carved fish-scale boss on the frame's ends: overlapping arcs cut into the wood."""
    for y in range(y0, y1):
        for x in range(x0, x1):
            d.point((x, y), WOOD_MID if hash01(x, y, salt) < 0.8 else WOOD_BASE)
    r = 4
    for row in range(0, (y1 - y0) // 3 + 2):
        off = (r if row % 2 else 0)
        cy = y0 + row * 3
        for cx in range(x0 + off, x1 + r, r * 2):
            for a in range(0, 181, 12):
                px = cx + int(round(r * math.cos(math.radians(a)))); py = cy + int(round(r * math.sin(math.radians(a)) * 0.6))
                if x0 <= px < x1 and y0 <= py < y1:
                    d.point((px, py), WOOD_SHADOW if a > 90 else WOOD_HI)


def curl(d, cx, cy, r, flip=False):
    """A corner scroll — the carved curl at each corner of the frame."""
    for t in range(0, 270, 6):
        rr = r * (1 - t / 400.0)
        a = math.radians(t if not flip else -t)
        x = cx + int(round(rr * math.cos(a))); y = cy + int(round(rr * math.sin(a)))
        d.point((x, y), WOOD_HI); d.point((x, y + 1), WOOD_SHADOW)


def fish_silhouette(d, x0, y0, tail_up):
    """A 24x16 fish, head left: body ellipse, forked tail, an eye and a dorsal fin."""
    for y in range(16):
        for x in range(24):
            dx = (x - 9) / 9.0; dy = (y - 8) / 4.5
            body = dx * dx + dy * dy <= 1.0
            tail = x >= 16 and abs(y - 8 - (x - 16) * (0.9 if tail_up else -0.9)) <= max(1, (x - 15) * 0.9) and x <= 23
            fin = 5 <= x <= 12 and y < 8 and y >= 8 - (x - 4) // 2 - 1 and y >= 2
            if body or tail or fin:
                c = FISH_HI if (body and dy < -0.3) else FISH
                if body and dy > 0.6: c = shade(FISH, 0.75)
                d.point((x0 + x, y0 + y), c)
    d.point((x0 + 4, y0 + 7), FISH_EYE); d.point((x0 + 2, y0 + 8), shade(FISH, 0.7))


def build():
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    # ---- the frame: two rails, two end bosses, the window cut out, rivets, curls ----
    plank(d, 0, 12, 256, 48, 1)                       # the body
    plank(d, 0, 12, 256, 20, 2); plank(d, 0, 40, 256, 48, 3)   # the rails
    scales(d, 2, 14, 28, 46, 4); scales(d, 228, 14, 254, 46, 5)
    d.rectangle([28, 20, 227, 39], fill=(0, 0, 0, 0))  # the window
    d.line([(27, 19), (228, 19)], BRASS_DARK); d.line([(27, 40), (228, 40)], BRASS_HI)
    d.line([(27, 20), (27, 39)], BRASS_DARK); d.line([(228, 20), (228, 39)], BRASS_HI)
    for x in range(34, 228, 24): rivet(d, x, 15); rivet(d, x, 44)
    for cx, cy, fl in ((6, 15, False), (249, 15, True), (6, 44, True), (249, 44, False)):
        curl(d, cx, cy, 5, fl)
    # eye and mouth on each boss — the carved fish heads the frame's ends are
    d.point((10, 24), WOOD_SHADOW); d.point((11, 24), BRASS_HI); d.point((244, 24), WOOD_SHADOW); d.point((245, 24), BRASS_HI)
    # ---- the name plate, hung over the top rail ----
    plank(d, 78, 0, 178, 14, 6)
    d.rectangle([80, 2, 175, 11], outline=BRASS_DARK)
    for (fx, fy) in ((70, 4), (182, 4)):   # the floats
        d.rectangle([fx, fy, fx + 3, fy + 3], fill=FLOAT_RED); d.rectangle([fx, fy + 4, fx + 3, fy + 6], fill=FLOAT_WHITE)
        d.line([(fx + 1, fy - 2), (fx + 1, fy - 1)], WOOD_SHADOW)
    # ---- the chains from the bottom rail to the sign (drawn on the frame, 48..56) ----
    for x in (96, 160):
        for y in range(48, 56):
            d.point((x, y), CHAIN if y % 2 else CHAIN_HI); d.point((x + 1, y), CHAIN)
    # ---- the wave tile (0,56) 40x20 ----
    for y in range(20):
        for x in range(40):
            crest = 6 + 3 * math.sin(x / 40.0 * 2 * math.pi) + 1.5 * math.sin(x / 20.0 * 2 * math.pi + 1)
            depth = y - crest
            if depth < -1: c = (0, 0, 0, 0)
            elif depth < 0.5: c = TEAL_FOAM
            elif depth < 3: c = TEAL_HI
            elif depth < 9: c = TEAL if hash01(x, y, 7) < 0.85 else TEAL_HI
            else: c = TEAL_DEEP if hash01(x, y, 8) < 0.9 else TEAL
            if c != (0, 0, 0, 0) and depth >= -1 and depth < 0.5: c = TEAL_FOAM
            d.point((x, 56 + y), c if len(c) == 4 else c + (255,))
    for y in range(20):   # nothing transparent in the fill — above the crest is the deeper teal too
        for x in range(40):
            if im.getpixel((x, 56 + y))[3] == 0: d.point((x, 56 + y), TEAL_DEEP + (255,))
    # ---- the deep tile (40,56) 40x20 ----
    for y in range(20):
        for x in range(40):
            h = hash01(x, y, 9)
            c = DEEP0 if y > 14 else DEEP1 if h < 0.8 else DEEP2
            if (x + y * 3) % 17 == 0 and y < 12: c = DEEP2
            d.point((40 + x, 56 + y), c + (255,))
    # ---- the fish (80,56) and (104,56) ----
    fish_silhouette(d, 80, 56, True); fish_silhouette(d, 104, 56, False)
    # ---- the sign (0,76) 88x20: a stone plate with a brass rim and chain rings ----
    for y in range(20):
        for x in range(88):
            h = hash01(x, y, 10)
            d.point((x, 76 + y), (STONE if h < 0.8 else STONE_DARK if h < 0.9 else STONE_HI) + (255,))
    d.rectangle([0, 76, 87, 95], outline=STONE_DARK); d.rectangle([1, 77, 86, 94], outline=STONE_HI)
    d.rectangle([2, 78, 85, 93], outline=BRASS_DARK)
    for x in (8, 78): d.rectangle([x, 76, x + 3, 78], fill=CHAIN_HI)
    return im


def main():
    trees = sys.argv[1:] or [os.path.dirname(os.path.dirname(os.path.abspath(__file__)))]
    im = build()
    for t in trees:
        p = os.path.join(t, OUT); os.makedirs(os.path.dirname(p), exist_ok=True); im.save(p); print("wrote", p)


if __name__ == "__main__":
    main()
