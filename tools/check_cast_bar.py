# -*- coding: utf-8 -*-
"""§gui-art: the three gauges draw the sheet the author actually painted.

    py tools/check_cast_bar.py

cast_bar.png is hand-drawn now (it used to come out of tools/gen_cast_bar.py, which drew a 128x48 sheet
and has been deleted along with gen_order_panel.py — both would have overwritten the art). So there is no
generator to compare the renderers against any more; the PNG itself is the source of truth, and this
reads the sprite rects straight out of its pixels.

Three files blit that one sheet — the cast gauge (ClientHud), the strike window (FloatTimingClient) and
the winter jig (JigClient) — and they draw at the same place on screen, so they have to agree. The way
this goes wrong is silent: a wrong texW/texH does not fail, it samples the wrong texels and squashes
them, which is exactly what the old 128, 48 arguments did the moment the new art landed.

Checked:
  * the sheet's opaque bands still are the frame / fill+blocked / sign rows the code assumes;
  * the frame's black trough is where the code puts the fill (tx, ty, TW, TH);
  * all three gauges declare the same FW/FH/TW/TH and the same recess offset;
  * every blit of the sheet, in every one of the three, names the real texture size.
"""
import io, os, re, struct, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PNG = "common/src/main/resources/assets/riverfishing/textures/gui/cast_bar.png"
GAUGES = ["common/src/main/java/com/riverfishing/client/ClientHud.java",
          "common/src/main/java/com/riverfishing/client/FloatTimingClient.java",
          "common/src/main/java/com/riverfishing/client/JigClient.java"]


def read(rel):
    return io.open(os.path.join(ROOT, rel), encoding="utf-8").read()


def bands(path):
    """The sheet's opaque row bands, as (top, bottom, left, right) — no PIL needed for the size, but the
    pixels do need decoding, so this uses PIL when it is there and falls back to the header alone."""
    w, h = struct.unpack(">II", io.open(path, "rb").read()[16:24])
    try:
        from PIL import Image
    except ImportError:
        return (w, h), None
    im = Image.open(path).convert("RGBA")
    a = im.getchannel("A").load()
    rows = [y for y in range(h) if any(a[x, y] > 8 for x in range(w))]
    out, start = [], None
    for y in range(h + 1):
        opaque = y in rows
        if opaque and start is None:
            start = y
        elif not opaque and start is not None:
            xs = [x for x in range(w) for yy in range(start, y) if a[x, yy] > 8]
            out.append((start, y - 1, min(xs), max(xs)))
            start = None
    return (w, h), out


def trough(path):
    """The frame's black recess: (x, y, w, h) inside the frame sprite."""
    from PIL import Image
    px = Image.open(path).convert("RGB").load()
    dark = lambda x, y: all(c < 50 for c in px[x, y])
    runs, s = [], None
    for x in range(256):
        if dark(x, 17):
            s = x if s is None else s
        elif s is not None:
            runs.append((s, x - 1)); s = None
    if s is not None:
        runs.append((s, 255))
    x0, x1 = max(runs, key=lambda r: r[1] - r[0])
    # a row counts as trough only where it is dark across the WHOLE width — the frame has dark
    # ornament above and below the recess, and a single column samples that too
    ys = [y for y in range(34) if sum(1 for x in range(x0, x1 + 1) if sum(px[x, y]) < 24) > (x1 - x0) * 0.9]
    return x0, min(ys), x1 - x0 + 1, max(ys) - min(ys) + 1


def main():
    path = os.path.join(ROOT, PNG)
    (w, h), rows = bands(path)
    fails = []
    if (w, h) != (256, 96):
        fails.append("cast_bar.png is %dx%d; the gauges are written for 256x96" % (w, h))

    if rows:
        want = [(0, 33), (36, 49), (51, 88)]          # frame · fill+blocked · sign
        got = [(a, b) for a, b, _, _ in rows]
        if got != want:
            fails.append("the sheet's opaque bands are %s, not the frame/fill/sign layout %s" % (got, want))
        else:
            _, _, fl, fr = rows[1]
            if (fl, fr) != (0, 251):
                fails.append("the fill row spans x %d..%d; the fill sprite is 0..229 and the blocked chip 234..251" % (fl, fr))
            _, _, sl, sr = rows[2]
            if (sl, sr) != (0, 99):
                fails.append("the sign spans x %d..%d, not the 100 wide the code blits" % (sl, sr))
        tx, ty, tw, th = trough(path)
        if (tx, ty) != (13, 10) or (tw, th) != (230, 14):
            fails.append("the frame's trough measures (%d,%d) %dx%d; the code fills at (13,10) 230x14"
                         % (tx, ty, tw, th))

    geom = {}
    for rel in GAUGES:
        src = read(rel)
        m = re.search(r"final int FW = (\d+), FH = (\d+), TW = (\d+), TH = (\d+);", src)
        if not m:
            fails.append("%s no longer declares the shared FW/FH/TW/TH block" % os.path.basename(rel))
            continue
        geom[os.path.basename(rel)] = tuple(int(g) for g in m.groups())
        for call in re.findall(r"blit\([^;]*?BAR[^;]*?\);", src, re.S):
            if not re.search(r"256,\s*96\s*\)", call):
                fails.append("%s blits the sheet without naming it 256x96: %s"
                             % (os.path.basename(rel), " ".join(call.split())[:90]))
    if len(set(geom.values())) > 1:
        fails.append("the three gauges disagree on the frame: %s" % geom)
    elif geom and next(iter(geom.values())) != (256, 34, 230, 14):
        fails.append("the gauges declare %s; the sheet says (256, 34, 230, 14)" % (next(iter(geom.values())),))

    if fails:
        print("FAILED:")
        for f in fails:
            print("  " + f)
        return 1
    print("cast bar ok: 256x96 sheet, frame 256x34, trough (13,10) 230x14, all three gauges agree")
    return 0


if __name__ == "__main__":
    sys.exit(main())
