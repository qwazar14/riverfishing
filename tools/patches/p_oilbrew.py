# -*- coding: utf-8 -*-
"""§oil-brew: a fatty fish goes in the brewing stand.

    py -X utf8 tools/patches/p_oilbrew.py <root> [1211|1201|26]

Fish oil had exactly one source — a FURNACE, `oily_fish` → `fish_oil` — and a player who reaches for a
smoker finds nothing, because a smoker only runs `minecraft:smoking` recipes and that one is a smelt.
Since the oily fish also stopped having a cooked form, the smoker does nothing at all with them, which
reads as "there is no way to get fish oil".

So the stand takes the fish directly: an AWKWARD potion (мутное зелье — nether wart first, the way
every other potion starts) plus any fish on the oily list is a Potion of Fish Oil. The oil ITEM still
brews the same potion, and still comes out of a furnace or a campfire.

The list of fish has to be written twice — the tag is datapack-time and brewing is built before any
datapack is read — so tools/check_oil_brew.py holds the two copies to each other.

Not done, deliberately: an empty glass bottle plus a fish giving the oil ITEM. Vanilla's brewing table
only maps potion to potion; an item output needs a loader-specific call in each of the six platform
builds (Fabric's registerItemRecipe, NeoForge's addRecipe(Ingredient, Ingredient, ItemStack), and
whatever 1.20.1 and 26.x each want). It is doable and it is not one line, so it is a decision rather
than an oversight.
"""
import io, os, sys

ROOT = sys.argv[1]
D = sys.argv[2] if len(sys.argv) > 2 else "1211"
P = os.path.join(ROOT, "common/src/main/java/com/riverfishing/registry/ModPotions.java")

s = io.open(P, encoding="utf-8").read()
if "oil-brew" in s:
    print("  already patched")
    sys.exit(0)

# The signature and the way a potion is named differ per tree; the mix itself does not.
import re
m = re.search(r"( *)builder\.addMix\(Potions\.WATER, ModItems\.FISH_OIL\.get\(\), (.+?)\);\n", s)
assert m, "the water mix moved"
OIL = m.group(2)
old = m.group(0)
s = s.replace(old, old + """
        // §oil-brew: and the FISH itself, over an awkward base. The oil had one source, a furnace, and a
        // smoker runs no smelting recipe — so the obvious tool for a fish did nothing and the oil looked
        // unobtainable. The rendering step is still there for anyone who wants the ingredient.
        for (String sp : OILY) {
            var fish = ModItems.FISH_ITEMS.get(com.riverfishing.RiverFishing.id(sp));
            if (fish != null) builder.addMix(Potions.AWKWARD, fish.get(), %s);
        }
        builder.addMix(Potions.AWKWARD, ModItems.FISH_OIL.get(), %s);
""" % (OIL, OIL), 1)

anchor = re.search(r" *public static void addMixes\(.*?\) \{\n", s).group(0)
s = s.replace(anchor, """    /**
     * §oil-brew: the fish a stand will render down — the same nine as the {@code oily_fish} item tag the
     * furnace reads. It has to be written twice: a tag is datapack-time and the brewing table is built
     * before any datapack is read, so there is nothing to look the tag up in. tools/check_oil_brew.py
     * is what keeps the two copies the same list.
     */
    private static final String[] OILY = {"herring", "mackerel", "salmon", "pink_salmon", "sabrefish",
                                          "eel", "bluefish", "bluefin_tuna", "pollock"};

""" + anchor, 1)

io.open(P, "w", encoding="utf-8", newline="\n").write(s)
print("  ModPotions: an awkward bottle and a fatty fish")
print("done (%s)" % D)
