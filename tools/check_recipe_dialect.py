# -*- coding: utf-8 -*-
"""§ingredient-dialect: every recipe in this tree must speak this tree's Minecraft version.

    py tools/check_recipe_dialect.py

Minecraft 1.21.2 replaced the ingredient object with the id itself:

    1.20.1 / 1.21.1     "ingredients": [ { "item": "minecraft:bread" } ]
    1.21.2+ / 26.x      "ingredients": [ "minecraft:bread" ]

Both parse as JSON, so a file copied from the wrong branch loads, fails silently in the codec, and
leaves an EMPTY ingredient list — which the game reports as

    Couldn't parse data file ... DataResult.Error['List is too short: 0, expected range [1-9]']

and the recipe simply does not exist in game. Reported as issue #15 against 0.8.1: the two groundbait
recipes had been ported from the 1.21.1 tree verbatim, months after the other 37 were converted, so
Base Groundbait — the item every mix is built on — could not be crafted on 26.x at all.

Shaped recipes carry the same values under "key", so both shapes are checked.
"""
import io, json, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def mc_versions():
    """Every Minecraft version this tree's shared resources have to satisfy.

    A Stonecutter tree keeps its real versions under common/versions/<mc>/gradle.properties and leaves
    the ROOT gradle.properties on whatever the Architectury template shipped — 1.21.1 here — so reading
    the root alone answers for the wrong game entirely. One resource folder serves every version, so
    every one of them has to accept the dialect.
    """
    versions = os.path.join(ROOT, "common/versions")
    if os.path.isdir(versions):
        out = sorted(d for d in os.listdir(versions)
                     if os.path.isfile(os.path.join(versions, d, "gradle.properties")))
        if out:
            return out
    p = os.path.join(ROOT, "gradle.properties")
    m = re.search(r"^minecraft_version=(.+)$", io.open(p, encoding="utf-8").read(), re.M)
    assert m, "no minecraft_version in " + p
    return [m.group(1).strip()]


def wants_strings(v):
    """True when this version takes the id itself rather than an ingredient object."""
    major = int(v.split(".")[0])
    if major >= 26:
        return True
    parts = [int(x) for x in v.split(".")]
    return parts[:2] >= [1, 21] and (len(parts) < 3 or parts[2] >= 2)


def values(d):
    return list(d.get("ingredients") or []) + list((d.get("key") or {}).values())


def main():
    vs = mc_versions()
    votes = {wants_strings(v) for v in vs}
    assert len(votes) == 1, ("this tree serves versions on both sides of the 1.21.2 ingredient change "
                             "with one resource folder: %s" % vs)
    strings = votes.pop()
    v = ", ".join(vs)
    want = "the id itself" if strings else 'an object: {"item": ...}'
    base = os.path.join(ROOT, "common/src/main/resources/data/riverfishing")
    folder = os.path.join(base, "recipe")
    if not os.path.isdir(folder):
        folder = os.path.join(base, "recipes")

    # RECURSIVE on purpose: recipe/ has held subfolders before (recipe/cutting/ for Farmer's Delight),
    # and a non-recursive scan would call a tree clean while a nested file spoke the wrong dialect.
    files = []
    for base_dir, _, names in os.walk(folder):
        files += [os.path.join(base_dir, n) for n in names if n.endswith(".json")]

    wrong, total = [], 0
    for path in sorted(files):
        f = os.path.relpath(path, folder).replace("\\", "/")
        total += 1
        d = json.load(io.open(path, encoding="utf-8"))
        vals = values(d)
        if not vals:
            continue
        objects = [x for x in vals if isinstance(x, dict)]
        if strings and objects:
            wrong.append((f, "object form on a version that takes the id itself"))
        elif not strings and any(isinstance(x, str) for x in vals):
            wrong.append((f, "bare id on a version that takes an ingredient object"))

    print("minecraft %s -> an ingredient is %s" % (v, want))
    print("%d recipes in %s" % (total, os.path.relpath(folder, ROOT).replace("\\", "/")))
    if wrong:
        print("\n%d recipe(s) speak the WRONG dialect and will load with an empty ingredient list:"
              % len(wrong))
        for f, why in wrong:
            print("  %-30s %s" % (f, why))
        return 1
    print("every recipe speaks this tree's dialect")
    return 0


if __name__ == "__main__":
    sys.exit(main())


# §recipe-result: 1.20.1 reads the result as {"item": ...}; "id" arrived in 1.20.5 and loads as
# "Missing item" here. Eleven recipes shipped that way once — a checker that only looked at the
# ingredients called them clean.
import json as _json, glob as _glob, os as _os, sys as _sys
_bad = []
for _f in _glob.glob(_os.path.join(_os.path.dirname(_os.path.dirname(_os.path.abspath(__file__))),
                                   "common", "src", "main", "resources", "data", "riverfishing", "recipes", "*.json")):
    _r = _json.load(open(_f, encoding="utf-8")).get("result")
    if isinstance(_r, dict) and "id" in _r and "item" not in _r:
        _bad.append(_os.path.basename(_f))
if _bad:
    _sys.exit("result_key: these recipes use \"id\" for the result, which 1.20.1 cannot read: " + ", ".join(_bad))
print("every recipe result uses the 1.20.1 result key")
