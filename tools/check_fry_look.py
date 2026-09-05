# -*- coding: utf-8 -*-
"""§fry-look: every renderer that draws a fry draws the fish it will be, through FryItem.look().

    py -X utf8 tools/check_fry_look.py [root]

Reported on 26.2: fry white in the hand, the wrong fish in the aquarium. Each renderer had built its
own picture from a bare species stack — no card, so no variety, no colours. The rule now: nobody
builds a fry's picture but FryItem.look(), and look() writes a card with Variety, Genes and Pattern
(and stamps it on 26.x). A renderer that reaches for `new ItemStack(fish)` or FishItem.create again
is the bug coming back with a different face.
"""
import io, os, re, sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
J = os.path.join(ROOT, "common/src/main/java/com/riverfishing")
fails = []

fry = io.open(os.path.join(J, "item/FryItem.java"), encoding="utf-8").read()
look = re.search(r"public static ItemStack look\(ItemStack fry\) \{.*?\n    \}", fry, re.S)
if not look:
    fails.append("FryItem.look() is missing")
else:
    b = look.group(0)
    for need in ('"Variety"', '"Genes"', "Pattern.TAG", "koiVariety(", "carpVariety(", "CatchCard.TAG"):
        if need not in b:
            fails.append("FryItem.look() does not write %s" % need)
    is26 = os.path.exists(os.path.join(ROOT, "common/src/main/resources/assets/riverfishing/items/fry.json"))
    if is26 and "stampIcon(" not in b:
        fails.append("FryItem.look() does not stamp the icon — on 26.x the stack IS the picture")

renderers = ["client/AquariumRenderer.java", "client/FrySpecialRenderer.java", "client/FryItemRenderer.java"]
seen = 0
for rel in renderers:
    p = os.path.join(J, rel)
    if not os.path.exists(p):
        continue
    seen += 1
    s = io.open(p, encoding="utf-8").read()
    if "FryItem.look(" not in s:
        fails.append("%s draws a fry without FryItem.look()" % rel)
    # the old ways of building the picture
    fry_part = s
    if rel.endswith("AquariumRenderer.java"):
        m = re.search(r"(private (?:static )?void (?:renderFry|extractFry)\(.*?\n    \})", s, re.S)
        fry_part = m.group(1) if m else s
    if "FishItem.create(" in fry_part or re.search(r"new ItemStack\(fish\.get\(\)\)", fry_part):
        fails.append("%s still builds a fry's picture from a bare species stack" % rel)

if seen < 2:
    fails.append("expected the aquarium renderer and one fry renderer, found %d" % seen)

if fails:
    print("FAILED:")
    for x in fails:
        print("  " + x)
    sys.exit(1)
print("fry look: %d renderers draw a fry through FryItem.look() — variety, genes, pattern%s"
      % (seen, ", stamped" if is26 else ""))
