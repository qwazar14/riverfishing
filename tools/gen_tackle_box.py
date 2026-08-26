# -*- coding: utf-8 -*-
"""Draw the four tackle boxes and the block faces of the placed one, 16x16.

    python tools/gen_tackle_box.py

Two layers per item icon, because the box is dyeable: `<id>.png` is the shell and `<id>_inserts.png` is
the part that takes the colour (tint layer 1). The inserts are drawn WHITE so a dye multiplies to exactly
the dye's own colour — the same trick leather armour uses, and the reason the default box reads as
off-white rather than as "no colour chosen".

The four sizes are the same box drawn bigger, with the latch count rising — a player should be able to
tell a HUGE box from a SMALL one in a hotbar without reading anything.
"""
import os, sys

try:
    from PIL import Image
except ImportError:
    sys.exit("needs Pillow: python -m pip install pillow")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ITEM = os.path.join(ROOT, "common/src/main/resources/assets/riverfishing/textures/item")
BLOCK = os.path.join(ROOT, "common/src/main/resources/assets/riverfishing/textures/block")

# Sampled from the mod's existing tackle sprites: olive-grey plastic with a hard dark outline.
OUTLINE = (0x2A, 0x2C, 0x26, 255)
SHELL_D = (0x3E, 0x46, 0x3C, 255)
SHELL = (0x55, 0x5E, 0x50, 255)
SHELL_L = (0x6C, 0x77, 0x66, 255)
METAL = (0xA8, 0xAE, 0xB4, 255)
METAL_D = (0x70, 0x76, 0x7C, 255)
WHITE = (0xFF, 0xFF, 0xFF, 255)
CLEAR = (0, 0, 0, 0)

# id -> (left, right, top, bottom) of the box in the 16x16 canvas, and how many latches.
SIZES = {
    "tackle_box_small":  (3, 12, 6, 12, 1),
    "tackle_box_medium": (2, 13, 5, 12, 2),
    "tackle_box_large":  (1, 14, 4, 13, 2),
    "tackle_box_huge":   (0, 15, 2, 14, 3),
}


def blank():
    return [[CLEAR] * 16 for _ in range(16)]


def write(px, path):
    img = Image.new("RGBA", (16, 16))
    img.putdata([px[y][x] for y in range(16) for x in range(16)])
    img.save(path)
    print("  ", os.path.basename(path))


def box(px, ins, l, r, t, b, latches):
    """Shell into px, the dyeable parts into ins. The lid seam is where the colour lives."""
    lid = t + max(2, (b - t) // 3)          # the lid/base seam
    for y in range(t, b + 1):
        for x in range(l, r + 1):
            edge = x in (l, r) or y in (t, b)
            if edge:
                px[y][x] = OUTLINE
            elif y < lid:
                px[y][x] = SHELL_L if y == t + 1 else SHELL
            else:
                px[y][x] = SHELL if y == lid + 1 else SHELL_D

    # The dyed band: the seam row plus the front panel inserts, on the tint layer.
    for x in range(l + 1, r):
        ins[lid][x] = WHITE
    panel_top, panel_bot = lid + 2, b - 1
    if panel_bot >= panel_top:
        for y in range(panel_top, panel_bot + 1):
            for x in range(l + 2, r - 1):
                ins[y][x] = WHITE

    # Handle: a metal loop standing off the lid.
    mid = (l + r) // 2
    for x in range(mid - 1, mid + 2):
        px[t - 1][x] = METAL if t > 0 else px[t - 1][x]
    if t > 0:
        px[t - 1][mid - 2] = METAL_D
        px[t - 1][mid + 2] = METAL_D

    # Latches across the seam — one more per size up, which is the size tell in a hotbar.
    span = r - l - 2
    for i in range(latches):
        x = l + 2 + (span * (i * 2 + 1)) // (latches * 2)
        px[lid][x] = METAL
        px[lid - 1][x] = METAL_D


def main():
    os.makedirs(ITEM, exist_ok=True)
    os.makedirs(BLOCK, exist_ok=True)
    print("items:")
    for name, (l, r, t, b, latches) in SIZES.items():
        px, ins = blank(), blank()
        box(px, ins, l, r, t, b, latches)
        write(px, os.path.join(ITEM, name + ".png"))
        write(ins, os.path.join(ITEM, name + "_inserts.png"))

    # ---- block faces: one shared set, because the placed box is the same object at four capacities and
    # the model varies its SIZE instead. Twelve near-identical textures would be twelve things to keep
    # in step for no visible gain.
    print("block:")
    side = blank()
    for y in range(16):
        for x in range(16):
            side[y][x] = OUTLINE if x in (0, 15) or y in (0, 15) else (SHELL_L if y < 5 else SHELL)
    for x in range(1, 15):
        side[9][x] = SHELL_D
    write(side, os.path.join(BLOCK, "tackle_box_side.png"))

    top = blank()
    for y in range(16):
        for x in range(16):
            top[y][x] = OUTLINE if x in (0, 15) or y in (0, 15) else SHELL_L
    for x in range(5, 11):                      # the handle, seen from above
        top[7][x] = METAL
        top[8][x] = METAL_D
    write(top, os.path.join(BLOCK, "tackle_box_top.png"))

    front = blank()
    for y in range(16):
        for x in range(16):
            front[y][x] = OUTLINE if x in (0, 15) or y in (0, 15) else (SHELL_L if y < 5 else SHELL)
    for x in range(1, 15):
        front[9][x] = SHELL_D
    for x in (4, 11):                           # latches
        front[8][x] = METAL
        front[10][x] = METAL_D
    write(front, os.path.join(BLOCK, "tackle_box_front.png"))

    band = blank()                              # the dyed band, tinted at render time
    for y in range(16):
        for x in range(16):
            band[y][x] = WHITE
    write(band, os.path.join(BLOCK, "tackle_box_band.png"))


if __name__ == "__main__":
    main()
