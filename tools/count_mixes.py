# -*- coding: utf-8 -*-
"""How many groundbait mixes can actually be stirred?

    python tools/count_mixes.py

Counted from the rules the recipe enforces, not estimated:

  · the grid is 3x3, and ONE SLOT IS ONE ITEM (GroundbaitMixRecipe: a stack of 64 in a slot is one);
  · a mix must contain the base (GroundbaitMix.qualifiesAsMix);
  · without a dye it must also contain at least one additive — plain base is not a mix;
  · with a dye in the grid, plain base IS a mix, and the dye eats a slot;
  · the pantry is read from GroundbaitMix.java, so this number moves when the pantry does.

Two mixes are the same mix when their PARTS match: GroundbaitMix.signature() sorts the parts and
deliberately leaves the colour out, which is the whole reason dye is cosmetic — a red jar and a blue
jar of the same recipe feed a swim identically.
"""
import io, math, os, re, sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(REPO, "common/src/main/java/com/riverfishing/groundbait/GroundbaitMix.java")

text = io.open(SRC, encoding="utf-8").read()
# The base is registered as put(BASE_ID, ...) with no quotes, so a regex that only reads quoted ids
# silently counts one component short and every number below comes out wrong. Match both spellings and
# then insist the base is among them.
pantry = re.findall(r'^\s*put\(\s*(BASE_ID|"[^"]+")', text, re.M)
SLOTS = 9
VANILLA_DYES = 16

n = len(pantry)
assert n > 1, "could not read the pantry out of GroundbaitMix.java"
assert "BASE_ID" in pantry, "the base is not in the pantry — the count would be a component short"
assert len(set(pantry)) == n, "the pantry has a duplicate id"


def multisets(types, size):
    """How many multisets of `size` items drawn from `types` kinds."""
    return math.comb(types + size - 1, size) if size >= 0 else 0


def with_base(total_items):
    """Multisets of this size that contain at least one base: reserve one, spread the rest freely."""
    return multisets(n, total_items - 1)


print("  pantry: %d components (the base plus %d things to put in it)" % (n, n - 1))
print("  grid:   %d slots, one item per slot\n" % SLOTS)

undyed = 0
print("  WITHOUT DYE — base + at least one additive")
for k in range(2, SLOTS + 1):
    # every all-base grid is one multiset, and it is the one case that needs a dye to count
    c = with_base(k) - 1
    undyed += c
    print("    %d slots%s %14s" % (k, " " * 2, "{:,}".format(c)))
print("    %-9s %14s\n" % ("total", "{:,}".format(undyed)))

dyed_recipes = 0
print("  WITH DYE — the dye takes a slot, so parts fit in what is left; plain base now counts")
for p in range(1, SLOTS):
    c = with_base(p)
    dyed_recipes += c
    print("    %d parts + dye%s %11s" % (p, " " * 1, "{:,}".format(c)))
print("    %-13s %11s" % ("total", "{:,}".format(dyed_recipes)))

new_only = with_base(1)  # plain base, which only a dye can turn into a mix
print("\n  of those, %d is a recipe DYE ALONE unlocks (plain base, no additive)" % new_only)
print("  the other %s are recipes you could already stir undyed"
      % "{:,}".format(dyed_recipes - new_only))

print("\n  colour: %d vanilla dyes, blended 60%% per dye and applied in slot order, so more than one" % VANILLA_DYES)
print("  dye keeps shifting the shade. signature() leaves colour out — dye is looks, never bite.")

print("\n  %s distinct mixes that fish differently." % "{:,}".format(undyed + new_only))
