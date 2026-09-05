# -*- coding: utf-8 -*-
"""§cast-bar: the sheet and the renderer agree on where everything is.

    py tools/check_cast_bar.py [--selftest]

gen_cast_bar.py draws a 128x48 sheet and prints its layout; ClientHud.renderCastPower blits off that
sheet by numbers it carries itself. Two copies of one layout, in two languages, and nothing in either
build knows about the other: move the fill strip in the generator and the gauge draws hatch where the
charge should be, with no error anywhere — just a bar that looks wrong to a player and right to a test.

So the numbers are read out of both and compared: the sheet's size off the PNG header, the strip
offsets off the generator's own layout constants, and the renderer's blit arguments off the Java.
"""
import io, os, re, struct, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PNG = "common/src/main/resources/assets/riverfishing/textures/gui/cast_bar.png"
GEN = "tools/gen_cast_bar.py"
HUD = "common/src/main/java/com/riverfishing/client/ClientHud.java"


def png_size(path):
    with open(path, "rb") as f:
        f.read(16)
        return struct.unpack(">II", f.read(8))


def gen_layout(text):
    """The strips the generator says it draws: name -> (u, v, w, h)."""
    out = {"frame": (0, 0, 120, 16), "recess": (4, 4, 112, 8)}
    m = re.search(r'print\("  fill (\d+)x(\d+) at \((\d+),(\d+)\); hatch (\d+)x(\d+) at \((\d+),(\d+)\); plaque (\d+)x(\d+) at \((\d+),(\d+)\)"', text)
    assert m, "the generator no longer prints its layout"
    g = [int(x) for x in m.groups()]
    out["fill"] = (g[2], g[3], g[0], g[1])
    out["hatch"] = (g[6], g[7], g[4], g[5])
    out["plaque"] = (g[10], g[11], g[8], g[9])
    return out


def hud_layout(text):
    """What the renderer believes: the sheet size it passes to every blit, and each strip's v offset."""
    i = text.index("private static void renderCastPower(")
    body = text[i:]
    sizes = set(re.findall(r"\b(\d+), (\d+)\)\s*;", body))          # the trailing ", 128, 48)"
    sheet = None
    for a, b in sizes:
        if (int(a), int(b)) == (128, 48):
            sheet = (128, 48)
    fw = re.search(r"FW = (\d+), FH = (\d+), TW = (\d+), TH = (\d+)", body)
    vs = [int(v) for v in re.findall(r", 0f, (\d+)f,", body)]     # every blit's v: 0, 16, 24, 32
    return dict(sheet=sheet, frame=(int(fw.group(1)), int(fw.group(2))) if fw else None,
                track=(int(fw.group(3)), int(fw.group(4))) if fw else None, vs=sorted(set(vs)))


def faults(png, gen, hud):
    out = []
    if png != hud["sheet"]:
        out.append("the PNG is %sx%s but the renderer blits against %s" % (png[0], png[1], hud["sheet"]))
    if hud["frame"] != gen["frame"][2:]:
        out.append("frame: renderer %s, generator %s" % (hud["frame"], gen["frame"][2:]))
    if hud["track"] != gen["recess"][2:]:
        out.append("track: renderer %s, generator recess %s" % (hud["track"], gen["recess"][2:]))
    want = sorted({gen[k][1] for k in ("frame", "fill", "hatch", "plaque")})
    if hud["vs"] != want:
        out.append("strip rows: renderer blits v=%s, generator draws at v=%s" % (hud["vs"], want))
    return out


def selftest():
    gen = {"frame": (0, 0, 120, 16), "recess": (4, 4, 112, 8), "fill": (0, 16, 112, 8),
           "hatch": (0, 24, 8, 8), "plaque": (0, 32, 48, 16)}
    hud = dict(sheet=(128, 48), frame=(120, 16), track=(112, 8), vs=[0, 16, 24, 32])
    assert not faults((128, 48), gen, hud), faults((128, 48), gen, hud)
    moved = dict(gen, fill=(0, 20, 112, 8))
    assert faults((128, 48), moved, hud), "a moved fill strip must be caught"
    assert faults((128, 64), gen, hud), "a resized sheet must be caught"
    print("self-test ok: a moved strip or a resized sheet reads as a mismatch, and the real layout passes")
    return 0


def main():
    png = png_size(os.path.join(ROOT, PNG))
    gen = gen_layout(io.open(os.path.join(ROOT, GEN), encoding="utf-8").read())
    hud = hud_layout(io.open(os.path.join(ROOT, HUD), encoding="utf-8").read())
    print("sheet %dx%d; renderer frame %s track %s rows %s" % (png[0], png[1], hud["frame"], hud["track"], hud["vs"]))
    bad = faults(png, gen, hud)
    if bad:
        print()
        for b in bad:
            print("  %s" % b)
        return 1
    print("the gauge draws the sheet the generator drew")
    return 0


if __name__ == "__main__":
    sys.exit(selftest() if "--selftest" in sys.argv else main())
