#!/usr/bin/env python3
"""§26.1: ingredients are plain strings now — {"item": "x"} → "x", {"tag": "t"} → "#t".
Recipe results keep the {"id": ...} object form (that part still parses)."""
import json, os

DIR = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", "common", "src", "main", "resources", "data", "riverfishing", "recipe"))


def convert(node):
    if isinstance(node, dict):
        if set(node.keys()) == {"item"}:
            return node["item"]
        if set(node.keys()) == {"tag"}:
            return "#" + node["tag"]
        return {k: (v if k in ("result",) and isinstance(v, dict) and "id" in v else convert(v))
                for k, v in node.items()}
    if isinstance(node, list):
        return [convert(x) for x in node]
    return node


n = 0
for f in sorted(os.listdir(DIR)):
    if not f.endswith(".json"):
        continue
    p = os.path.join(DIR, f)
    with open(p, encoding="utf-8") as fh:
        d = json.load(fh)
    d2 = convert(d)
    if d2 != d:
        with open(p, "w", encoding="utf-8", newline="\n") as fh:
            json.dump(d2, fh, indent=2, ensure_ascii=False)
            fh.write("\n")
        n += 1
print("converted %d recipes" % n)
