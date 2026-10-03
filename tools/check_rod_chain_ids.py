"""§reel-26x: every piece RodChain draws must be an ITEM-DEFINITION id with a file behind it.

piece() asks for items/<path>.json and quietly draws nothing when it is absent — which is how the reel
went missing from every 26.x rod: it was handed model ids (item/rod/reel_N_3d) and looked for
items/item/rod/… . This fails if a piece() call uses any other id builder, or a reel def is missing.
"""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CLIENT = ROOT / "common/src/main/java/com/riverfishing/client"
ITEMS = ROOT / "common/src/main/resources/assets/riverfishing/items"

chain = (CLIENT / "RodChain.java").read_text(encoding="utf-8")
builders = set(re.findall(r"piece\(stack, RodModelLayers\.(\w+)\(", chain))
assert builders == {"segmentItemModel", "reelItemModel"}, f"piece() handed ids from {builders}"

layers = (CLIENT / "RodModelLayers.java").read_text(encoding="utf-8")
for b in builders:
    body = re.search(r"Identifier " + b + r"\([^)]*\) \{\s*return ([^;]+);", layers).group(1)
    assert '"rod/' in body, f"{b} does not build an items/rod/ id: {body}"

sizes = [1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000, 10000, 12000, 14000]
missing = [f"reel_{s}{p}_3d" for s in sizes for p in ("", "_handle", "_knob")
           if not (ITEMS / "rod" / f"reel_{s}{p}_3d.json").exists()]
assert not missing, f"reel definitions missing: {missing}"
print(f"rod chain ids: piece() takes {sorted(builders)}, {len(sizes) * 3} reel definitions present")
