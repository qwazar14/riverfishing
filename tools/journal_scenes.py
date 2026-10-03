# -*- coding: utf-8 -*-
"""Render the journal illustrations the mod ships: 240x160, indexed, no metadata, ~20 kB each.

    py tools/journal_scenes.py                       # re-render every shipped illustration in place
    py tools/journal_scenes.py <srcdir> <dstdir>     # a delivery of new art -> the resource folder

§journal-240 (0.9.1): the frame was 480x320 and ~60 kB, which is 1:1 at GUI scale 2 and looked it —
but at 267 species that is 13.8 MB of the 20 MB jar, and the table is heading for four figures. 240x160
is what the journal actually LAYS OUT (JournalScreen.ILLUS_W/H), so it is the honest size to ship; the
480 masters stay in art-pack-journal, which is where a future re-render comes from.

Source, per species, in order: the raw delivery in art-pack-journal/scenes (best), else the shipped PNG
itself (the older art, whose masters are not in this repo). Downscaling an already-quantised frame costs
a little, and it costs less than not having the art.

The output carries IHDR + PLTE + IDAT + IEND and nothing else — no iCCP, no tEXt, no gAMA, no pHYs, no
timestamp. Two reasons: bytes, and a resource reload that never parses a colour profile it will ignore.
Pillow writes ancillary chunks only for what it finds in `Image.info`, so the frame is rebuilt from raw
pixels and saved with the info dict emptied — a stray chunk cannot survive that.
"""
import io, os, sys, glob
from PIL import Image

W, H = 240, 160
# The centre box the palette is weighted towards — the fish — as a fraction of the frame, so it follows
# the frame size instead of being 480-shaped numbers that quietly stop meaning anything.
CENTRE = (140 / 480, 110 / 320, 200 / 480, 100 / 320)
TARGET, LO, HI = 20_000, 17_000, 23_000

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SHIPPED = os.path.join(REPO, "common/src/main/resources/assets/riverfishing/textures/gui/journal/fish")
MASTERS = os.path.join(REPO, "art-pack-journal/scenes")


def render(src, ncolors):
    w, h = src.size
    ch = round(w * H / W)                     # centre-crop to 3:2 first
    src = src.crop((0, (h - ch) // 2, w, (h - ch) // 2 + ch)) if ch < h else src
    # LANCZOS, not NEAREST: the source is now usually LARGER than the frame, and nearest-neighbour
    # downsampling of a painted scene throws away every second pixel of it.
    img = src.resize((W, H), Image.LANCZOS if src.size[0] > W else Image.NEAREST)
    cx, cy, cw, chh = (round(CENTRE[0] * W), round(CENTRE[1] * H), round(CENTRE[2] * W), round(CENTRE[3] * H))
    ncen = max(8, ncolors * 3 // 5)           # centre (the fish) owns most of the palette
    cols = []
    for part, n in ((img.crop((cx, cy, cx + cw, cy + chh)), ncen), (img, ncolors - ncen)):
        p = part.quantize(colors=n, method=Image.Quantize.MEDIANCUT).getpalette()[:n * 3]
        cols += [tuple(p[i:i + 3]) for i in range(0, len(p), 3)]
    cols = list(dict.fromkeys(cols))[:256]
    pal = Image.new('P', (1, 1))
    pal.putpalette([c for rgb in cols for c in rgb] + [0, 0, 0] * (256 - len(cols)))
    out = img.quantize(palette=pal, dither=Image.Dither.NONE)
    out.info = {}                              # §journal-240: nothing ancillary reaches the file
    buf = io.BytesIO(); out.save(buf, 'PNG', optimize=True)
    return buf.getvalue()


def best(path):
    src = Image.open(path).convert('RGB')
    lo, hi, pick = 8, 256, None
    while lo <= hi:                            # fewest colours that still lands in the band
        mid = (lo + hi) // 2
        data = render(src, mid)
        if pick is None or abs(len(data) - TARGET) < abs(len(pick) - TARGET):
            pick = data
        if len(data) < LO: lo = mid + 1
        elif len(data) > HI: hi = mid - 1
        else: return data
    return pick


def in_place():
    """Re-render everything the mod ships, preferring each species' raw master when there is one."""
    total = 0
    files = sorted(glob.glob(os.path.join(SHIPPED, '*.png')))
    for i, dst in enumerate(files, 1):
        name = os.path.basename(dst)
        master = os.path.join(MASTERS, name.replace('.png', '.raw.png'))
        src = master if os.path.exists(master) else dst
        data = best(src)
        open(dst, 'wb').write(data)
        total += len(data)
        print('%4d/%d  %-34s %5.1f kB  %s' % (i, len(files), name, len(data) / 1000,
                                              'master' if src is master else 'downscale'), flush=True)
    print('%d illustrations, %.2f MB' % (len(files), total / 1048576))


if __name__ == '__main__':
    if len(sys.argv) == 1:
        in_place()
    else:
        srcdir, dstdir = sys.argv[1], sys.argv[2]
        os.makedirs(dstdir, exist_ok=True)
        for f in sorted(glob.glob(os.path.join(srcdir, '*.png'))):
            name = os.path.basename(f).replace('.raw.png', '.png')
            data = best(f)
            open(os.path.join(dstdir, name), 'wb').write(data)
            print('%s %.1f kB' % (name, len(data) / 1000), flush=True)
