"""Split two-block-tall models into their block halves.

Export the Blockbench project as a Java Block Model to the source path, then run:
    py tools/split_tall_model.py                      (all of them)
    py tools/split_tall_model.py 3D/corn/corn_stage4.json
Sources: models/item/reed.json, models/item/cattail.json (the item uses the whole plant) and
3D/corn/corn_stage3.json, corn_stage4.json (not shipped: only the halves are).
Elements at or above y=16 go to models/block/<stem>_top.json (moved down 16), the rest to <stem>_bottom.json.
An element that crosses y=16 must be two elements; the script refuses it. A rotated element sits on the side
its from-y is on (a stalk tilting from y=16 is top).
"""
import copy, json, pathlib, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
M = ROOT / "common/src/main/resources/assets/riverfishing/models"
DEFAULT = [M / "item/reed.json", M / "item/cattail.json", ROOT / "3D/corn/corn_stage3.json", ROOT / "3D/corn/corn_stage4.json"]

for src in [ROOT / a for a in sys.argv[1:]] or DEFAULT:
    full = json.loads(src.read_text(encoding="utf-8"))
    halves = {"bottom": [], "top": []}
    for e in full["elements"]:
        y0, y1 = e["from"][1], e["to"][1]
        if y0 < 16 < y1:
            sys.exit(f"{src.name}/{e.get('name')}: {y0}..{y1} crosses y=16, cut it in two")
        e = copy.deepcopy(e)
        if y0 >= 16:
            e["from"][1] -= 16
            e["to"][1] -= 16
            if "rotation" in e:
                e["rotation"]["origin"][1] -= 16
        halves["top" if y0 >= 16 else "bottom"].append(e)
    for half, els in halves.items():
        out = {k: v for k, v in full.items() if k not in ("elements", "display", "groups")}
        out["elements"] = els
        (M / f"block/{src.stem}_{half}.json").write_text(json.dumps(out, indent=2) + "\n", encoding="utf-8")
        print(f"{src.stem}_{half}", len(els))
