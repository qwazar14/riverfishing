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

# §oil-brew-item: the empty-bottle route. The list is shared; only the call that records it differs,
# and only on 1.20.1 — so check the shared half here and each door where it lives.
if "addOilBrews" not in JAVA or "Items.GLASS_BOTTLE" not in JAVA:
    die("the empty-bottle recipe is gone from ModPotions — the oil ITEM is back to a furnace only")
doors = []
for rel, need in (("fabric/src/main/java/com/riverfishing/platform/fabric/PlatformHelperImpl.java", "addOilBrews"),
                  ("forge/src/main/java/com/riverfishing/platform/forge/PlatformHelperImpl.java", "addOilBrews"),
                  ("neoforge/src/main/java/com/riverfishing/platform/neoforge/PlatformHelperImpl.java", None)):
    p2 = os.path.join(ROOT, rel)
    if os.path.exists(p2):
        doors.append((rel.split("/")[0], need is None or need in io.open(p2, encoding="utf-8").read()))
# On a tree whose builder is vanilla's, the shared line covers every loader and no door needs its own.
shared = "builder.addContainerRecipe" in JAVA
if not shared:
    for loader, ok in doors:
        if not ok:
            die("%s has no empty-bottle recipe: this tree has no PotionBrewing.Builder, so each loader "
                "needs its own door and that one is shut" % loader)

if "Potions.AWKWARD, fish.get(), FISH_OIL" not in JAVA:
    die("the stand no longer takes the fish itself")
if "Potions.WATER, ModItems.FISH_OIL.get(), FISH_OIL" not in JAVA:
    die("the oil item no longer brews the potion")

if fails:
    print("FAILED:")
    for x in fails:
        print("  " + x)
    sys.exit(1)
print("fish oil: %d oily fish, the same list in the stand and the furnace; item from a furnace, a "
      "campfire or an EMPTY BOTTLE in a stand (%s); potion from the oil or from the fish over an "
      "awkward base"
      % (len(java_list), "one shared vanilla line" if shared
         else "a door per loader: " + ", ".join(l for l, ok in doors if ok)))
