# -*- coding: utf-8 -*-
"""§aquarium-window: the tank window's background, 256x256 with the 176x222 panel at (0,0).

    py tools/gen_aquarium_gui.py

Dark blue-green glass instead of the shop's parchment: it is a window INTO the tank, not paperwork.
Slot frames sit exactly where menu/AquariumMenu.SLOT_XY puts the slots (frame = slot - 1, 18x18);
the arrow and feed-bar geometry is shared with client/AquariumScreen — change both or they drift.
The lit arrow (176,0 20x8) and the bar fill (176,8 112x10) live off-panel to the right.
"""
import os, struct, zlib

W = H = 256
PANEL_W, PANEL_H = 176, 222
CLEAR = (0, 0, 0, 0)
EDGE      = (0x0B, 0x1C, 0x20, 0xFF)   # outer line
GLASS     = (0x17, 0x3A, 0x42, 0xFF)   # panel body
GLASS_HI  = (0x2C, 0x5E, 0x66, 0xFF)   # top/left bevel
GLASS_LO  = (0x0E, 0x28, 0x2E, 0xFF)   # bottom/right bevel
WELL      = (0x0C, 0x22, 0x28, 0xFF)   # slot inside
WELL_DK   = (0x06, 0x14, 0x18, 0xFF)   # slot top/left
WELL_LT   = (0x3E, 0x78, 0x80, 0xFF)   # slot bottom/right
MODULE    = (0x8C, 0x6A, 0x2E, 0xFF)   # module slots: brass frame so they read as fittings, not fish
TROUGH    = (0x08, 0x18, 0x1C, 0xFF)
ARROW     = (0x2A, 0x50, 0x56, 0xFF)
ARROW_LIT = (0x7C, 0xE0, 0xC8, 0xFF)
BAR_LIT   = (0x3F, 0xA8, 0x8A, 0xFF)
BAR_LIT_HI= (0x6C, 0xD4, 0xB0, 0xFF)

# Same numbers as AquariumMenu.SLOT_XY (tank) + the vanilla inventory grid the menu adds.
SLOTS = [(44, 20), (62, 20), (80, 20), (44, 38), (62, 38), (80, 38),
         (8, 66), (30, 66), (8, 20), (126, 29)]
MODULES = [(152, 20), (152, 38)]
INV = [(8 + c * 18, 140 + r * 18) for r in range(3) for c in range(9)] + [(8 + c * 18, 198) for c in range(9)]
ARROW_X, ARROW_Y, ARROW_W, ARROW_H = 102, 34, 20, 8
BAR_X, BAR_Y, BAR_W, BAR_H = 55, 70, 112, 10

px = [[CLEAR] * W for _ in range(H)]


def put(x, y, c):
    if 0 <= x < W and 0 <= y < H:
        px[y][x] = c


def rect(x0, y0, x1, y1, c):
    for y in range(y0, y1):
        for x in range(x0, x1):
            put(x, y, c)


def bevel(x0, y0, x1, y1, body, hi, lo):
    rect(x0, y0, x1, y1, body)
    for x in range(x0, x1):
        put(x, y0, hi); put(x, y1 - 1, lo)
    for y in range(y0, y1):
        put(x0, y, hi); put(x1 - 1, y, lo)


def slot(x, y, lo=WELL_DK, hi=WELL_LT):
    # vanilla convention: the 18x18 frame starts one pixel up-left of the item square
    bevel(x - 1, y - 1, x + 17, y + 17, WELL, lo, hi)


def arrow(x, y, c):
    # a 20x8 right arrow: 12px shaft, 8px head
    rect(x, y + 2, x + 12, y + 6, c)
    for i in range(4):
        rect(x + 12 + i, y + i, x + 12 + i + 1, y + 8 - i, c)
    put(x + 16, y + 3, c); put(x + 16, y + 4, c)


# panel
rect(0, 0, PANEL_W, PANEL_H, EDGE)
bevel(1, 1, PANEL_W - 1, PANEL_H - 1, GLASS, GLASS_HI, GLASS_LO)
# a faint waterline band across the tank area so the top half reads as "the tank"
rect(2, 17, PANEL_W - 2, 18, GLASS_LO)
rect(2, 60, PANEL_W - 2, 61, GLASS_LO)
# the player inventory area, one shade darker: it is the player's, not the tank's
rect(2, 136, PANEL_W - 2, PANEL_H - 2, GLASS_LO)
rect(2, 136, PANEL_W - 2, 137, EDGE)

for s in SLOTS + INV:
    slot(*s)
for s in MODULES:
    slot(s[0], s[1], MODULE, MODULE)

# feed bar trough (one pixel around the fill) and the grey arrow
bevel(BAR_X - 1, BAR_Y - 1, BAR_X + BAR_W + 1, BAR_Y + BAR_H + 1, TROUGH, WELL_DK, WELL_LT)
arrow(ARROW_X, ARROW_Y, ARROW)

# off-panel sprites: lit arrow, bar fill
arrow(176, 0, ARROW_LIT)
rect(176, ARROW_H, 176 + BAR_W, ARROW_H + BAR_H, BAR_LIT)
rect(176, ARROW_H, 176 + BAR_W, ARROW_H + 1, BAR_LIT_HI)


def png(path, rows):
    raw = b"".join(b"\x00" + b"".join(struct.pack("4B", *p) for p in row) for row in rows)

    def chunk(tag, data):
        c = tag + data
        return struct.pack(">I", len(data)) + c + struct.pack(">I", zlib.crc32(c) & 0xFFFFFFFF)

    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n")
        f.write(chunk(b"IHDR", struct.pack(">IIBBBBB", W, H, 8, 6, 0, 0, 0)))
        f.write(chunk(b"IDAT", zlib.compress(raw, 9)))
        f.write(chunk(b"IEND", b""))


REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(REPO, "common", "src", "main", "resources", "assets", "riverfishing",
                   "textures", "gui", "aquarium.png")
png(OUT, px)
print("%s  %dx%d" % (OUT, W, H))
