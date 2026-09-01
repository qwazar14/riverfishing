# -*- coding: utf-8 -*-
"""§craft-grid: every recipe the journal draws has to fit the bench it draws.

    py tools/check_recipe_grid.py

The journal's "how to get" is a real 3x3 grid now, built by JournalScreen.craftOf():

    shaped     -> the pattern's own width x height
    shapeless  -> no pattern exists, so the ingredients are packed three to a row

Anything wider or taller than 3 is skipped with a `continue`, and a skipped recipe is INVISIBLE: the
page falls back to "found in the world or from the fisherman", which for a craftable item is simply a
lie. Nothing logs, nothing throws — the same silent shape the §ingredient-dialect bug had, where a
mis-ported recipe left an empty ingredient list and Base Groundbait could not be crafted on 26.x at
all while every check still passed.

So the invariant is checked in the data, where it can be broken by hand:

    a shaped pattern is at most 3 rows of at most 3 columns, and its rows are all the same length
    a shapeless recipe has 1..9 ingredients

Both hold for vanilla crafting by definition. They stop holding the moment a recipe is written for a
bigger bench from another mod and dropped into this folder, which is when the journal would quietly
start lying instead of failing.

Run with --selftest to prove the check catches both.
"""
import io, json, os, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def recipe_folder():
    base = os.path.join(ROOT, "common/src/main/resources/data/riverfishing")
    folder = os.path.join(base, "recipe")
    return folder if os.path.isdir(folder) else os.path.join(base, "recipes")


def faults(d):
    """Every reason this recipe would not survive craftOf(), or an empty list."""
    out = []
    kind = str(d.get("type", ""))

    if "pattern" in d:
        rows = d["pattern"]
        if not isinstance(rows, list) or not rows:
            return ["pattern is empty"]
        h = len(rows)
        widths = {len(r) for r in rows}
        if h > 3:
            out.append("pattern is %d rows tall, the bench is 3" % h)
        if len(widths) != 1:
            out.append("pattern rows are ragged: widths %s" % sorted(widths))
        w = max(widths)
        if w > 3:
            out.append("pattern is %d columns wide, the bench is 3" % w)
        return out

    if "ingredients" in d:
        ings = d["ingredients"]
        if not isinstance(ings, list) or not ings:
            return ["shapeless with no ingredients"]
        n = len(ings)
        if n > 9:
            # packed three to a row, so ten ingredients ask for a fourth row
            out.append("shapeless with %d ingredients packs to %d rows, the bench is 3"
                       % (n, (n + 2) // 3))
        return out

    # smelting, stonecutting, custom serializers: the journal has no grid for these and does not
    # pretend to. Not a fault — just nothing to draw.
    if "crafting" not in kind:
        return []
    return []


def selftest():
    bad_shaped = {"type": "minecraft:crafting_shaped", "pattern": ["####", "####"]}
    bad_ragged = {"type": "minecraft:crafting_shaped", "pattern": ["##", "#"]}
    bad_tall = {"type": "minecraft:crafting_shaped", "pattern": ["#", "#", "#", "#"]}
    bad_many = {"type": "minecraft:crafting_shapeless", "ingredients": ["x"] * 10}
    good_shaped = {"type": "minecraft:crafting_shaped", "pattern": ["###", " # ", " # "]}
    good_many = {"type": "minecraft:crafting_shapeless", "ingredients": ["x"] * 9}
    for name, d in (("4 wide", bad_shaped), ("ragged", bad_ragged),
                    ("4 tall", bad_tall), ("10 shapeless", bad_many)):
        assert faults(d), "the check MISSED %s" % name
        print("  caught: %-14s %s" % (name, faults(d)[0]))
    for name, d in (("3x3", good_shaped), ("9 shapeless", good_many)):
        assert not faults(d), "the check wrongly flagged %s" % name
        print("  passed: %s" % name)
    print("self-test ok")
    return 0


def main():
    if "--selftest" in sys.argv:
        return selftest()

    folder = recipe_folder()
    files = []
    for base_dir, _, names in os.walk(folder):
        files += [os.path.join(base_dir, n) for n in names if n.endswith(".json")]

    bad, drawable = [], 0
    for path in sorted(files):
        rel = os.path.relpath(path, folder).replace("\\", "/")
        d = json.load(io.open(path, encoding="utf-8"))
        if "pattern" in d or "ingredients" in d:
            drawable += 1
        for why in faults(d):
            bad.append((rel, why))

    print("%d recipes in %s, %d of them a grid the journal draws"
          % (len(files), os.path.relpath(folder, ROOT).replace("\\", "/"), drawable))
    if bad:
        print("\n%d recipe(s) do NOT fit the bench — the journal will silently show "
              '"found in the world" instead:' % len(bad))
        for rel, why in bad:
            print("  %-34s %s" % (rel, why))
        return 1
    print("every recipe fits the 3x3 the journal draws")
    return 0


if __name__ == "__main__":
    sys.exit(main())
