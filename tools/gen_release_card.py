#!/usr/bin/env python3
"""The 1920x1080 release card (docs/patchnotes/<version>-card.png), in the look of the 0.9.0 one.

The 0.9.0 card was drawn in a session and its script was never kept; this is the script. Text is set in
Minecraft's own bitmap font, read out of the client jar Loom already has in the Gradle cache, so the card
needs no font file. Icons are the mod's textures. Run:  py -X utf8 tools/gen_release_card.py
"""
import io, math, os, sys, zipfile
from PIL import Image, ImageDraw

REPO = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
TEX = os.path.join(REPO, "common", "src", "main", "resources", "assets", "riverfishing", "textures")
JAR = os.path.expanduser("~/.gradle/caches/fabric-loom/1.21.1/minecraft-client.jar")

W, H = 1920, 1080
BG, GOLD, CREAM, DIM, SHADOW = (9, 19, 40), (201, 162, 75), (240, 230, 208), (150, 164, 190), (4, 9, 20)

CARD = {
    "version": "1.0.0",
    "meta": ["RIVER FISHING", "SINCE 0.9.0", "1.20.1 - 1.21.1 - 26.1.2 - 26.2"],
    "fixed": [("WINTER ROD", "item/rig_winter"), ("AQUARIUM", "item/aquarium"), ("FLAT FISH", "item/fish/flounder"),
              ("OLD PONDS", "block/pond_sign"), ("NETS", "item/seine_net"), ("LIVE BAIT", "item/livebait"),
              ("RELEASE", "item/fish/carp"), ("STATION", "block/tackle_station")],
    "reworked": [("FLY ROD", "item/reel_fly"), ("THE FIGHT", "item/fish/pike"), ("THE POND", "block/pond_sign"),
                 ("KOI", "item/fish/koi_carp"), ("TYING", "item/jig"), ("RECIPES", "item/rod"),
                 ("SOUNDS", "item/bell"), ("WINTER JIG", "item/mormyshka")],
    "systems": [("CURRENT", "item/float"), ("COOKING", "item/fish/cooked/salmon"), ("FISH OIL", "item/fish_oil"),
                ("LINES", "item/line_fly_wf_f"), ("POND BOOK", "item/journal"), ("MORPHS", "item/fish/tench"),
                ("HYBRIDS", "item/fry"), ("LB / OZ", "item/contract")],
    "panels": [
        ("160 NEW SPECIES", "grid", 4, ["african_arowana", "alligator_gar", "electric_eel", "giant_freshwater_stingray",
                                          "nile_perch", "tigerfish", "redtail_catfish", "payara",
                                          "kaluga_sturgeon", "giant_snakehead", "frontosa_cichlid", "adonis_pleco"]),
        ("5 FLY RODS - 5 REELS - 9 LINES", "items", 5, ["reel_fly_3", "reel_fly", "reel_fly_7", "reel_fly_9", "reel_fly_11",
                                                                 "line_fly_wf_f", "line_fly_wf_i", "line_fly_wf_s", "line_fly_dt_f",
                                                                 "line_fly_dt_i", "line_fly_dt_s", "line_fly_sh_f", "line_fly_sh_i",
                                                                 "line_fly_sh_s"]),
        ("EVERY FISH COOKS", "cooked", 4, ["carp", "pike", "salmon", "perch", "catfish", "zander", "trout", "bream"]),
        ("A POND THAT REMEMBERS ITS FISH", "badges", 2, [("POND SIGN", "block/pond_sign"), ("AERATOR", "block/aerator_side"),
                                                         ("GRAVEL BED", "block/gravel_bed"), ("FEEDER", "block/feeding_station_side0")]),
    ],
}

# ---------------------------------------------------------------- the font
def load_font():
    with zipfile.ZipFile(JAR) as z:
        sheet = Image.open(io.BytesIO(z.read("assets/minecraft/textures/font/ascii.png"))).convert("RGBA")
    cell = sheet.width // 16
    glyphs = {}
    for code in range(32, 127):
        g = sheet.crop(((code % 16) * cell, (code // 16) * cell, (code % 16 + 1) * cell, (code // 16 + 1) * cell)).split()[3]
        box = g.getbbox()
        glyphs[chr(code)] = (g, (box[2] if box else cell // 2))
    return glyphs, cell

GLYPHS, CELL = load_font()

def text_mask(s):
    width = sum(GLYPHS.get(c, GLYPHS["?"])[1] + 1 for c in s)
    m = Image.new("L", (max(1, width), CELL), 0)
    x = 0
    for c in s:
        g, w = GLYPHS.get(c, GLYPHS["?"])
        m.paste(g, (x, 0))
        x += w + 1
    return m

def text_width(s, scale): return text_mask(s).width * scale

def draw_text(img, xy, s, scale, color=CREAM, shadow=True, anchor="l", spacing=0):
    if spacing: s = (" " * spacing).join(s) if spacing > 0 else s
    m = text_mask(s)
    m = m.resize((m.width * scale, m.height * scale), Image.NEAREST)
    x, y = xy
    if anchor == "c": x -= m.width // 2
    if anchor == "r": x -= m.width
    if shadow: img.paste(Image.new("RGB", m.size, SHADOW), (x + scale, y + scale), m)
    img.paste(Image.new("RGB", m.size, color), (x, y), m)
    return m.width

# ---------------------------------------------------------------- the icons
def icon(path, size):
    f = os.path.join(TEX, path + ".png")
    if not os.path.exists(f):
        print("  missing texture:", path); return None
    im = Image.open(f).convert("RGBA")
    if im.height > im.width: im = im.crop((0, 0, im.width, im.width))   # an animated strip: its first frame
    box = im.getbbox()
    if box: im = im.crop(box)
    k = size / max(im.size)
    big = im.width > 64
    return im.resize((max(1, round(im.width * k)), max(1, round(im.height * k))), Image.LANCZOS if big else Image.NEAREST)

def paste_center(img, ic, cx, cy):
    if ic is not None: img.paste(ic, (cx - ic.width // 2, cy - ic.height // 2), ic)

def first_existing(names, folder, n):
    out = [x for x in names if os.path.exists(os.path.join(TEX, folder, x + ".png"))]
    if len(out) < n:   # the wish list is a wish list: top up from what the mod really has
        have = sorted(f[:-4] for f in os.listdir(os.path.join(TEX, folder)) if f.endswith(".png"))
        out += [x for x in have[::max(1, len(have) // (n * 2))] if x not in out]
    return out[:n]

# ---------------------------------------------------------------- the pieces
def ribbon(img):
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for r, col in ((84, (13, 40, 70, 255)), (66, (15, 48, 82, 255)), (46, (17, 56, 94, 255)), (24, (19, 63, 104, 255))):
        for y in range(-100, H + 100, 26):
            x = 960 + 120 * math.sin(y / 190.0 + 0.6) - 40 * math.sin(y / 71.0)
            d.ellipse((x - r, y - r, x + r, y + r), fill=col)
    img.paste(layer, (0, 0), layer)

PIX = {
    "wrench": [".....XX..", "....XX...", "....XX..X", "....XXXXX", "...XXXXX.", "..XXX....", ".XXX.....", "XXX......", "XX......."],
    "cycle":  ["..XXXX.X.", ".XX...XXX", "XX....XXX", "X........", "X.......X", "........X", "XXX....XX", "XXX...XX.", ".X.XXXX.."],
    "gear":   ["...X.X...", ".X.XXX.X.", "..XXXXX..", "XXX...XXX", ".XX...XX.", "XXX...XXX", "..XXXXX..", ".X.XXX.X.", "...X.X..."],
    "plus":   ["....X....", "....X....", "....X....", "....X....", "XXXXXXXXX", "....X....", "....X....", "....X....", "....X...."],
}

def pixel_icon(img, x, y, name, color, scale=5):
    d = ImageDraw.Draw(img)
    for pass_color, off in ((SHADOW, scale // 2 + 1), (color, 0)):
        for j, row in enumerate(PIX[name]):
            for i, ch in enumerate(row):
                if ch == "X":
                    d.rectangle((x + i * scale + off, y + j * scale + off, x + (i + 1) * scale - 1 + off, y + (j + 1) * scale - 1 + off), fill=pass_color)

def heading(img, x, y, word, glyph, color, right):
    d = ImageDraw.Draw(img)
    draw_text(img, (x + 60, y), word, 6, CREAM, spacing=0)
    pixel_icon(img, x, y + 1, glyph, color)
    d.line((x, y + 62, right, y + 62), fill=GOLD, width=2)

def badges(img, x0, y0, items, per_row=6, dx=128, dy=128):
    d = ImageDraw.Draw(img)
    for i, (label, tex) in enumerate(items):
        cx, cy = x0 + (i % per_row) * dx, y0 + (i // per_row) * dy
        d.ellipse((cx - 40, cy - 40, cx + 40, cy + 40), fill=(14, 28, 58), outline=GOLD, width=3)
        paste_center(img, icon(tex, 52), cx, cy)
        draw_text(img, (cx, cy + 48), label, 2, CREAM, anchor="c")

def systems(img, x0, y0, items):
    d = ImageDraw.Draw(img)
    colw = [0, 210, 420, 622]
    for i, (label, tex) in enumerate(items):
        x, y = x0 + colw[i % 4], y0 + (i // 4) * 92
        d.rectangle((x, y, x + 78, y + 78), fill=(14, 28, 58), outline=GOLD, width=3)
        paste_center(img, icon(tex, 56), x + 39, y + 39)
        draw_text(img, (x + 92, y + 30), label, 2, CREAM)

def panel(img, x, y, w, h, caption, kind, cols, items):
    d = ImageDraw.Draw(img)
    for i in range(h):   # a little depth: darker at the bottom, like water
        t = i / h
        d.line((x, y + i, x + w, y + i), fill=(int(16 + 10 * (1 - t)), int(34 + 22 * (1 - t)), int(72 + 30 * (1 - t))))
    d.rectangle((x, y, x + w, y + h), outline=GOLD, width=3)
    if kind == "badges":
        rows = (len(items) + cols - 1) // cols
        for i, (label, tex) in enumerate(items):
            cx = x + (i % cols + 0.5) * w / cols
            cy = y + (i // cols) * (h / rows) + 46
            d.rectangle((cx - 30, cy - 30, cx + 30, cy + 30), fill=(14, 28, 58), outline=GOLD, width=2)
            paste_center(img, icon(tex, 44), int(cx), int(cy))
            draw_text(img, (int(cx), int(cy + 38)), label, 2, CREAM, anchor="c")
    else:
        folder = {"grid": "item/fish", "cooked": "item/fish/cooked", "items": "item"}[kind]
        names = first_existing(items, folder, len(items))
        rows = (len(names) + cols - 1) // cols
        bw, bh = (w / cols) * (0.88 if kind != "items" else 0.5), (h / rows) * (0.86 if kind != "items" else 0.74)
        for i, n in enumerate(names):
            in_row = min(cols, len(names) - (i // cols) * cols)          # a short last row sits centred
            cx = x + (i % cols + 0.5 + (cols - in_row) / 2.0) * w / cols
            cy = y + (i // cols + 0.5) * h / rows
            ic = icon(folder + "/" + n, 512)
            if ic is not None:
                k = min(bw / ic.width, bh / ic.height)
                ic = ic.resize((max(1, round(ic.width * k)), max(1, round(ic.height * k))), Image.LANCZOS if kind != "items" else Image.NEAREST)
            paste_center(img, ic, int(cx), int(cy))
    draw_text(img, (x + w // 2, y + h + 12), caption, 2, CREAM, anchor="c")

# ---------------------------------------------------------------- the card
def main():
    c = CARD
    img = Image.new("RGB", (W, H), BG)
    ribbon(img)
    logo = Image.open(os.path.join(REPO, "logo128.png")).convert("RGBA").resize((104, 104), Image.LANCZOS)
    img.paste(logo, (66, 76), logo)
    draw_text(img, (196, 60), c["version"], 11, CREAM)
    draw_text(img, (198, 156), "PATCH NOTES", 5, GOLD)
    for i, line in enumerate(c["meta"]):
        draw_text(img, (640, 82 + i * 22), line, 2, DIM, shadow=False, spacing=0)

    heading(img, 76, 232, "FIXED", "wrench", (232, 148, 48), 880)
    badges(img, 150, 346, c["fixed"])
    heading(img, 76, 582, "REWORKED", "cycle", (72, 196, 120), 880)
    badges(img, 150, 696, c["reworked"])

    heading(img, 1066, 138, "SYSTEMS", "gear", (201, 162, 75), 1850)
    systems(img, 1066, 214, c["systems"])
    heading(img, 1066, 408, "NEW", "plus", (70, 190, 235), 1850)
    px, py, pw, ph = [1070, 1472], [486, 752], 372, 214
    for i, (cap, kind, cols, items) in enumerate(c["panels"]):
        panel(img, px[i % 2], py[i // 2], pw, ph, cap, kind, cols, items)

    draw_text(img, (76, 1030), "discord.gg/Kk2nKvsuRh", 2, DIM, shadow=False)
    out = os.path.join(REPO, "docs", "patchnotes", c["version"] + "-card.png")
    img.save(out, optimize=True)
    print("wrote", out, img.size)

if __name__ == "__main__":
    main()
