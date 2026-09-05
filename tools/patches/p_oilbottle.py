# -*- coding: utf-8 -*-
"""§oil-brew-item: an empty bottle under a fish, where the loader has a door for it.

    py -X utf8 tools/patches/p_oilbottle.py <root> [1211|1201|26]

The other half of §oil-brew, at the second attempt. The first one is worth writing down because it was
a good idea that is wrong, and the next person will have it too.

VANILLA'S CONTAINER TABLE CANNOT DO THIS. It looked like it could: the container mixes are keyed on the
bottle ITEM and output an ITEM (they are what turns a potion into a splash potion), and the stand's
slots already accept a glass bottle. But both ends are type-checked — 1.20.1's addContainer throws
"Expected a potion, got: minecraft:glass_bottle" on sight, and 1.21.1's Builder calls expectPotion on
the input AND the output. Fabric's registerItemRecipe calls the same check, which is why its 1.20.1
signature is typed PotionItem to PotionItem: the API is not narrow by accident, the table is
potion-only by design.

So the door is not vanilla's, it is the loader's, and only one loader family has one:

  NeoForge 1.21.1 / 26.x   PotionBrewing.Builder.addRecipe(Ingredient, Ingredient, ItemStack) — a
                           NeoForge-patched list beside the vanilla ones, with an ItemStack output and
                           no opinion about what it is.
  Forge 1.20.1             BrewingRecipeRegistry.addRecipe(IBrewingRecipe) — an arbitrary recipe object.
  Fabric, all versions     nothing. Fabric API forwards to the vanilla check, so an item output needs a
                           behaviour mixin on PotionBrewing (isIngredient, hasMix, mix) rather than a
                           registration. That is a bigger and riskier thing than a registration and it
                           is not in this patch.

Fabric keeps the awkward-potion route, which works everywhere and is most of what was asked for; the
oil ITEM is a furnace or a campfire there, as it has always been.
"""
import io, os, sys

ROOT = sys.argv[1]
D = sys.argv[2] if len(sys.argv) > 2 else "1211"


def rd(p): return io.open(p, encoding="utf-8").read()


def wr(p, s): io.open(p, "w", encoding="utf-8", newline="\n").write(s)


def sub(s, old, new, what):
    assert old in s, "%s moved" % what
    return s.replace(old, new, 1)


# ---- 1. the list, once, and a sink to pour it into ------------------------------------------------
p = os.path.join(ROOT, "common/src/main/java/com/riverfishing/registry/ModPotions.java")
s = rd(p)
if "oil-brew-item" not in s:
    anchor = "    private ModPotions() {}"
    assert anchor in s, "ModPotions' constructor moved"
    s = s.replace(anchor, '''    /** §oil-brew-item: where one "empty bottle + fish -> oil" recipe goes, per loader. */
    public interface OilSink {
        void add(net.minecraft.world.item.Item bottle, net.minecraft.world.item.Item fish,
                 net.minecraft.world.item.Item oil);
    }

    /**
     * §oil-brew-item: the oily fish, each as a glass bottle rendered into oil. NOT registered here,
     * because vanilla's brewing tables are potion-only at both ends — they type-check the input and the
     * output, and a glass bottle and a tin of oil are neither. Only NeoForge and Forge open a door for
     * an item output; each PlatformHelperImpl that has one pours this into it, and Fabric keeps the
     * awkward-potion route and the furnace.
     */
    public static void addOilBrews(OilSink sink) {
        for (String sp : OILY) {
            var fish = ModItems.FISH_ITEMS.get(com.riverfishing.RiverFishing.id(sp));
            if (fish != null) {
                sink.add(Items.GLASS_BOTTLE, fish.get(), ModItems.FISH_OIL.get());
            }
        }
    }

''' + anchor, 1)
    wr(p, s)
    print("  ModPotions: the oily fish as bottle-to-oil recipes")

# ---- 2. NeoForge (1.21.1, 26.x): the builder's own item-output list --------------------------------
if D != "1201":
    p = os.path.join(ROOT, "neoforge/src/main/java/com/riverfishing/platform/neoforge/PlatformHelperImpl.java")
    s = rd(p)
    if "oil-brew-item" not in s:
        s = sub(s, "                event -> com.riverfishing.registry.ModPotions.addMixes(event.getBuilder()));",
                """                event -> {
                    com.riverfishing.registry.ModPotions.addMixes(event.getBuilder());
                    // §oil-brew-item: an EMPTY bottle with a fish over it. NeoForge's builder keeps its
                    // own list beside the vanilla ones, with an ItemStack output and no opinion about
                    // whether it is a potion — which vanilla's container table very much has.
                    com.riverfishing.registry.ModPotions.addOilBrews((bottle, fish, oil) ->
                            event.getBuilder().addRecipe(
                                    net.minecraft.world.item.crafting.Ingredient.of(bottle),
                                    net.minecraft.world.item.crafting.Ingredient.of(fish),
                                    new net.minecraft.world.item.ItemStack(oil)));
                });""", "the NeoForge brewing hook")
        wr(p, s)
        print("  neoforge PlatformHelperImpl: bottle + fish -> oil")

# ---- 3. Forge (1.20.1): an arbitrary recipe object -------------------------------------------------
if D == "1201":
    p = os.path.join(ROOT, "forge/src/main/java/com/riverfishing/platform/forge/PlatformHelperImpl.java")
    s = rd(p)
    if "oil-brew-item" not in s:
        s = sub(s, """                e.enqueueWork(() -> com.riverfishing.registry.ModPotions.addMixes(
                        (from, ingredient, to) -> BrewingRecipeRegistry.addRecipe(new PotionMix(from, ingredient, to)))));""",
                """                e.enqueueWork(() -> {
                    com.riverfishing.registry.ModPotions.addMixes((from, ingredient, to) ->
                            BrewingRecipeRegistry.addRecipe(new PotionMix(from, ingredient, to)));
                    // §oil-brew-item: and an empty bottle with a fish over it.
                    com.riverfishing.registry.ModPotions.addOilBrews((bottle, fish, oil) ->
                            BrewingRecipeRegistry.addRecipe(new OilMix(bottle, fish, oil)));
                }));""", "the Forge brewing hook")
        old = "    /** One potion→potion mix, matched on the potion in the bottle rather than on the bottle. */"
        s = sub(s, old, """    /**
     * §oil-brew-item: an EMPTY bottle with a fish over it, rendered into oil. Forge's brewing registry
     * takes any recipe object, which is the whole reason this exists here and not in the shared code:
     * vanilla's own tables type-check both ends as potions, so a glass bottle in and a tin of oil out
     * is not something they will hold on any version.
     */
    private record OilMix(Item bottle, Item fish, Item oil) implements IBrewingRecipe {
        @Override
        public boolean isInput(ItemStack stack) {
            return stack.is(bottle);
        }

        @Override
        public boolean isIngredient(ItemStack stack) {
            return stack.is(fish);
        }

        @Override
        public ItemStack getOutput(ItemStack input, ItemStack ingredient) {
            return isInput(input) && isIngredient(ingredient) ? new ItemStack(oil) : ItemStack.EMPTY;
        }
    }

""" + old, "the Forge PotionMix record")
        wr(p, s)
        print("  forge PlatformHelperImpl: an OilMix recipe")
print("done (%s)" % D)
