# -*- coding: utf-8 -*-
"""§strike-tune: the hookset gauge borrows the cast gauge's frame, so it must borrow its numbers too.

    py -X utf8 tools/check_strike_bar.py [root]

FloatTimingClient now draws the strike window on textures/gui/cast_bar.png — the same sheet, the same
120x16 frame, the same 112x8 recess at (4,4) that ClientHud's cast gauge fills. Nothing enforces that:
the two files each hold their own copy of the geometry, and if the sheet is ever redrawn, whoever
updates the cast gauge will not think to look in a file about strike timing. The bar would then paint
its zones over the frame's brass, which looks like a rendering bug rather than a stale constant.

So: the numbers must match, they must fit inside the sheet, and the recess must sit inside the frame.
Also that the zones are still drawn into the RECESS (tx/ty) and not at the frame's own origin — off by
those four pixels is the exact mistake this geometry invites, and it is subtle enough on screen to ship.
"""
import io, os, re, sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
C = os.path.join(ROOT, "common/src/main/java/com/riverfishing/client")
HUD = os.path.join(C, "ClientHud.java")
STRIKE = os.path.join(C, "FloatTimingClient.java")

fails = []


def geometry(path, what):
    s = io.open(path, encoding="utf-8").read()
    m = re.search(r"final int FW = (\d+), FH = (\d+), TW = (\d+), TH = (\d+);", s)
    if not m:
        fails.append("%s no longer declares the gauge geometry as `final int FW…TH`" % what)
        return None, s
    return tuple(int(x) for x in m.groups()), s


cast, hud = geometry(HUD, "ClientHud")
strike, sk = geometry(STRIKE, "FloatTimingClient")

if "strike-tune" not in sk:
    print("this tree has not been restyled (§strike-tune) — nothing to check")
    sys.exit(0)

if cast and strike and cast != strike:
    fails.append("the cast gauge is %s but the strike gauge is %s. They share one sheet; whichever was "
                 "changed, the other now paints over the frame." % (cast, strike))

if strike:
    fw, fh, tw, th = strike
    if fw > 128 or fh > 48:
        fails.append("the frame %dx%d does not fit the 128x48 sheet" % (fw, fh))
    if tw + 8 > fw or th + 8 > fh:
        fails.append("the %dx%d recess does not sit inside the %dx%d frame with a 4px border"
                     % (tw, th, fw, fh))

# the zones and the needle must be drawn into the recess, not at the frame's corner
for name, var in (("zones", "tx"), ("zones", "ty")):
    if not re.search(r"g\.fill\(\s*(?:os|zs)[^)]*\)", sk):
        fails.append("the strike zones are no longer drawn with fill()")
        break
for bad in re.findall(r"int (?:os|zs) = (\w+) \+", sk):
    if bad != "tx":
        fails.append("a strike zone starts from %r, not from the recess (tx) — it will sit four pixels "
                     "left of the tube and overlap the frame" % bad)

sheet = os.path.join(ROOT, "common/src/main/resources/assets/riverfishing/textures/gui/cast_bar.png")
if not os.path.exists(sheet):
    fails.append("textures/gui/cast_bar.png is gone, and both gauges are drawn from it")
else:
    import struct
    w, h = struct.unpack(">II", io.open(sheet, "rb").read()[16:24])
    if (w, h) != (128, 48):
        fails.append("cast_bar.png is %dx%d but both gauges blit against a 128x48 sheet" % (w, h))

if fails:
    print("FAILED:")
    for x in fails:
        print("  " + x)
    sys.exit(1)
print("strike bar: frame %dx%d, recess %dx%d, shared with the cast gauge on a 128x48 sheet" % strike)
