# -*- coding: utf-8 -*-
"""§oil-brew: the fish the stand renders down are the fish the furnace does.

    py -X utf8 tools/check_oil_brew.py

The list lives twice on purpose and cannot live once: `oily_fish` is an item tag, read when a datapack
loads, and the brewing table is built before any datapack exists — there is nothing to look a tag up
in at that moment. So ModPotions carries its own copy, and this is what stops the two drifting.

It also checks the other half of the promise: that every fish on the list is a real species, and that
the furnace and campfire recipes that make the oil ITEM are still there. "There is no way to get fish
oil" has been reported once already; it should not be reportable twice.
"""
import io, json, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = io.open(os.path.join(ROOT, "common/src/main/java/com/riverfishing/registry/ModPotions.java"),
               encoding="utf-8").read()
DATA = os.path.join(ROOT, "common/src/main/resources/data/riverfishing")

fails = []


def die(msg):
    fails.append(msg)


m = re.search(r"String\[\] OILY = \{(.*?)\};", JAVA, re.S)
if not m:
    print("FAILED: ModPotions has no OILY list — the stand takes no fish")
    sys.exit(1)
java_list = re.findall(r'"(\w+)"', m.group(1))

tag = json.load(io.open(os.path.join(DATA, "tags/item/oily_fish.json"), encoding="utf-8"))
tag_list = [v.split(":")[-1] for v in tag["values"]]

if sorted(java_list) != sorted(tag_list):
    only_java = sorted(set(java_list) - set(tag_list))
    only_tag = sorted(set(tag_list) - set(java_list))
    die("the stand and the furnace disagree about what an oily fish is%s%s"
        % (" — only the stand: " + ", ".join(only_java) if only_java else "",
           " — only the furnace: " + ", ".join(only_tag) if only_tag else ""))

for sp in java_list:
    if not os.path.exists(os.path.join(DATA, "fish_profiles", sp + ".json")):
        die("%s is on the oily list and is not a species" % sp)

# the item's own two recipes, and the mix that turns it into the potion
folder = "recipe" if os.path.isdir(os.path.join(DATA, "recipe")) else "recipes"
for r in ("fish_oil_smelting", "fish_oil_campfire"):
    p = os.path.join(DATA, folder, r + ".json")
    if not os.path.exists(p):
        die("%s.json is gone — the oil ITEM has one source fewer" % r)
        continue
    j = json.load(io.open(p, encoding="utf-8"))
    if j.get("ingredient", {}).get("tag") != "riverfishing:oily_fish":
        die("%s no longer reads the oily_fish tag" % r)

# §oil-brew-item: the empty-bottle route, and the reason it is not everywhere. Vanilla's brewing tables
# type-check BOTH ends as potions — 1.20.1 throws on the glass bottle, 1.21.1's builder calls
# expectPotion on the input and the output, and Fabric's registerItemRecipe forwards to the same check.
# So an item output is a loader's own list or nothing, and this asserts nobody has quietly reached for
# the vanilla one again.
if "builder.addContainerRecipe" in JAVA or "addContainer(Items.GLASS_BOTTLE)" in JAVA:
    die("something is registering the glass bottle through VANILLA's container table — it type-checks "
        "both ends as potions and throws on sight. Use the loader's own item-recipe list.")
if "addOilBrews" not in JAVA or "Items.GLASS_BOTTLE" not in JAVA:
    die("the empty-bottle recipe is gone from ModPotions")

doors = []
for rel, need in (("neoforge/src/main/java/com/riverfishing/platform/neoforge/PlatformHelperImpl.java",
                   "addOilBrews"),
                  ("forge/src/main/java/com/riverfishing/platform/forge/PlatformHelperImpl.java",
                   "addOilBrews")):
    p2 = os.path.join(ROOT, rel)
    if os.path.exists(p2):
        doors.append((rel.split("/")[0], need in io.open(p2, encoding="utf-8").read()))
if not doors or not any(ok for _, ok in doors):
    die("no loader in this tree pours addOilBrews anywhere — the empty-bottle route is dead code")
for loader, ok in doors:
    if not ok:
        die("%s has a door for an item output and is not using it" % loader)

if "Potions.AWKWARD, fish.get(), FISH_OIL" not in JAVA:
    die("the stand no longer takes the fish itself")
if "Potions.WATER, ModItems.FISH_OIL.get(), FISH_OIL" not in JAVA:
    die("the oil item no longer brews the potion")

if fails:
    print("FAILED:")
    for x in fails:
        print("  " + x)
    sys.exit(1)
print("fish oil: %d oily fish, the same list in the stand and the furnace; the ITEM from a furnace, a "
      "campfire, or an empty bottle in a stand on %s; the POTION from the oil or from a fish over an "
      "awkward base, on every loader"
      % (len(java_list), " and ".join(l for l, ok in doors if ok) or "no loader in this tree"))
