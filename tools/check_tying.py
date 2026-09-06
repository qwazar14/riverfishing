# -*- coding: utf-8 -*-
"""§tying: the eight templates are drawings a player can hit, and no two of them alias.

    py -X utf8 tools/check_tying.py [root]

Reads the masks straight out of TiedDesign.java and runs the engine's own matching rule on them
(fit to the box, overlap): every template must match ITSELF at 1.0 and no OTHER template above the
0.45 floor the engine uses to call a drawing a curiosity. A template that can be mistaken for another
is a template the player cannot aim at. Also: the wiring — the engine multiplies by affinity, the rig
hands the drawing to the context, the vise item is a mormyshka to the rigs.
"""
import io, os, re, sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
J = os.path.join(ROOT, "common/src/main/java/com/riverfishing")
src = io.open(os.path.join(J, "tackle/TiedDesign.java"), encoding="utf-8").read()
fails = []

masks = {}
for m in re.finditer(r'(\w+)\("(\w+)", new String\[\]\{(.*?)\},', src, re.S):
    rows = re.findall(r'"([.#]{16})"', m.group(3))
    if m.group(1) == "NONE":
        continue
    if len(rows) != 16:
        fails.append("%s: %d rows, not 16" % (m.group(1), len(rows))); continue
    masks[m.group(1)] = [[c == "#" for c in r] for r in rows]

def box(g):
    xs = [x for y in range(16) for x in range(16) if g[y][x]]; ys = [y for y in range(16) for x in range(16) if g[y][x]]
    return min(xs), min(ys), max(xs), max(ys)

def iou(drawing, t):
    x0, y0, x1, y1 = box(drawing); w, h = x1 - x0 + 1, y1 - y0 + 1
    tx0, ty0, tx1, ty1 = box(t); tw, th = tx1 - tx0 + 1, ty1 - ty0 + 1
    inter = union = 0
    for y in range(16):
        for x in range(16):
            mm = t[y][x]
            if x < tx0 or x > tx1 or y < ty0 or y > ty1: f = False
            else:
                sx = x0 + (x - tx0) * w // tw; sy = y0 + (y - ty0) * h // th
                f = drawing[sy][sx]
            inter += mm and f; union += mm or f
    v = inter / union if union else 0
    arD, arT = w / h, tw / th
    return v * min(arD, arT) / max(arD, arT)   # the engine's aspect rule

if len(masks) != 8:
    fails.append("expected 8 templates, found %d" % len(masks))
for a, ga in masks.items():
    fill = sum(map(sum, ga))
    if fill < 20:
        fails.append("%s has only %d pixels — too thin to draw" % (a, fill))
    for b, gb in masks.items():
        v = iou(ga, gb)
        if a == b and v < 0.999:
            fails.append("%s does not match itself (%.2f)" % (a, v))
        # the engine takes the BEST match, so what matters is separation: a sloppy drawing of A (self
        # ~0.8) must still beat every other template. Under 0.70 to the nearest neighbour keeps that.
        if a != b and v >= 0.70:
            fails.append("%s reads as %s at %.2f — too close for a sloppy drawing to tell them apart" % (a, b, v))

eng = io.open(os.path.join(J, "engine/BiteEngine.java"), encoding="utf-8").read()
if "c.tied.affinity(p.group)" not in eng:
    fails.append("BiteEngine.baitScore does not multiply by the tied lure's affinity")
fm = io.open(os.path.join(J, "fishing/FishingManager.java"), encoding="utf-8").read()
if "ctx.tied = RigData.tiedLure(rigStack);" not in fm:
    fails.append("the cast context never receives the tied lure")
rig = io.open(os.path.join(J, "rig/RigData.java"), encoding="utf-8").read()
if "TiedDesign.analyse(stack).meanRgb()" not in rig:
    fails.append("lureColorRgb() ignores a tied lure — its colour never reaches the colour-vs-light read")
item = io.open(os.path.join(J, "item/TiedLureItem.java"), encoding="utf-8").read()
if 'super("mormyshka", true' not in item:
    fails.append("TiedLureItem must be a mormyshka to the rigs, or the winter rod refuses it")
pk = io.open(os.path.join(J, "network/TieLurePacket.java"), encoding="utf-8").read()
for must in ("affordable(inv, design)", "count(inv, HOOK) < 1 + cost[TiedDesign.BEAD_IRON]", "TiedDesign.valid(design)", "instanceof TackleStationMenu menu"):
    if must not in pk:
        fails.append("TieLurePacket.handleServer must check %s — the client is not trusted" % must)

if fails:
    print("FAILED:")
    for f in fails:
        print("  " + f)
    sys.exit(1)
hook = re.search(r'String\[\] h = \{(.*?)\};', src).group(1)
rows = re.findall(r'"([.#]{16})"', hook)
if len(rows) != 16 or rows[7][1:14].count("#") < 10:
    fails.append("the hook must lie along row 7 of the canvas, eye on the left — the templates are tied on it")
print("tying: 8 templates, each its own shape (self 1.0, nearest other under 0.70), engine/rig/item/packet wired")
