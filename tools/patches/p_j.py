# -*- coding: utf-8 -*-
"""§breeding stream J: fish meal and fish oil — registration and the two pantry entries.

    py -X utf8 tools/patches/p_j.py <repo root> [1211|1201|26]

Anchor replacement on TWO existing files; every insert carries a "§j" marker so a rerun finds it and
does nothing. Exit 1 with the missing anchor printed when a tree has drifted. The dialect argument only
changes how ModItems spells Item.Properties (26.x carries the id); the items themselves are the NEW
files item/FishMealItem.java and item/FishOilItem.java, which the integrator ports like any new file.
"""
import io, os, sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
DIALECT = sys.argv[2] if len(sys.argv) > 2 else "1211"
SRC = os.path.join(ROOT, "common/src/main/java/com/riverfishing")
MARK = "§j"


def read(path):
    with io.open(path, encoding="utf-8") as f:
        return f.read()


def write(path, text):
    with io.open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


def props(name):
    # §26.1: every Item.Properties carries its registry id.
    return 'props("%s")' % name if DIALECT == "26" else "props()"


def sub1(rel, old, new):
    """Exactly one anchor, replaced once; a file already carrying the insert is left alone."""
    path = os.path.join(SRC, rel)
    text = read(path)
    if new in text:
        print("already patched: " + rel)
        return
    if text.count(old) != 1:
        sys.exit("p_j: anchor not found once in %s (%d hits):\n%s" % (rel, text.count(old), old))
    write(path, text.replace(old, new))
    print("patched: " + rel)


# ---------------------------------------------------------------- ModItems: beside the tank's produce
CAST_NET = ('    public static final RegistrySupplier<Item> CAST_NET = reg("cast_net", '
            '() -> new com.riverfishing.item.CastNetItem(%s));\n' % props("cast_net"))
sub1("registry/ModItems.java", CAST_NET, CAST_NET + '''    // %s (0.9.0): what small fish and oily fish become. Meal is bone meal on a crop and protein in the
    // groundbait bowl; oil is scent in the bowl and nothing else. Both are pantry entries in GroundbaitMix.
    public static final RegistrySupplier<Item> FISH_MEAL = reg("fish_meal", () -> new com.riverfishing.item.FishMealItem(%s));
    public static final RegistrySupplier<Item> FISH_OIL = reg("fish_oil", () -> new com.riverfishing.item.FishOilItem(%s));
''' % (MARK, props("fish_meal"), props("fish_oil")))

# ---------------------------------------------------------------- GroundbaitMix: two more things in the pantry
CHUM = '        put("fish_strip", 0.75, 0.80, "fish_strip", 0x976661); // chum: ground fish lies on the deck\n'
sub1("groundbait/GroundbaitMix.java", CHUM, CHUM + '''        // %s: what the grinder and the furnace make of a fish. Both read as "livebait" — the one diet
        // word the freshwater predators (pike, zander, perch, catfish) score, and the sturgeon and the big
        // game with them; "fish_strip" is the SEA word and pike does not answer it at all. Meal is dense
        // protein that lies on the bottom; oil is fraction 0 on purpose — a slick, all scent and no grain.
        put("fish_meal", 0.90, 0.30, "fish_strip", 0xC8A870); // a diet must be a bait the pantry holds
        put("fish_oil", 0.60, 0.00, "fish_strip", 0xD89A30);
''' % MARK)
