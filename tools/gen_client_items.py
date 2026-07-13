#!/usr/bin/env python3
"""§26.1 client item definitions: every item now needs assets/<ns>/items/<name>.json describing its
model. Generated 1:1 from the existing models/item/*.json. Dyeable predator lures (§lure-color) get a
data-driven minecraft:dye tint — this replaces the old registerItemColors code path entirely."""
import json, os, shutil

ASSETS = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", "common", "src", "main", "resources", "assets", "riverfishing"))
MODELS = os.path.join(ASSETS, "models", "item")
ITEMS = os.path.join(ASSETS, "items")

# §lure-color: artificial lures are dyed like leather armour; tint layer0 from DYED_COLOR, white default.
DYEABLE = {"mormyshka", "spinner", "spoon", "wobbler", "silicone", "popper", "crankbait", "jig", "castmaster"}


def main():
    shutil.rmtree(ITEMS, ignore_errors=True)
    os.makedirs(ITEMS, exist_ok=True)
    n = 0
    for f in sorted(os.listdir(MODELS)):
        if not f.endswith(".json"):
            continue
        name = f[:-5]
        model = {"type": "minecraft:model", "model": "riverfishing:item/" + name}
        if name in DYEABLE:
            model["tints"] = [{"type": "minecraft:dye", "default": -1}]
        with open(os.path.join(ITEMS, f), "w", encoding="utf-8", newline="\n") as out:
            json.dump({"model": model}, out, indent=2)
            out.write("\n")
        n += 1
    print("wrote %d client item definitions" % n)


if __name__ == "__main__":
    main()
