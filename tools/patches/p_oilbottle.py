# -*- coding: utf-8 -*-
"""§oil-brew-item: an empty bottle under a fish, and the stand renders the oil itself.

    py -X utf8 tools/patches/p_oilbottle.py <root> [1211|1201|26]

The other half of §oil-brew. A brewing stand's mix table maps potion to potion, which is why the potion
was easy and the ITEM looked impossible — but that is not the only table. Vanilla has a second one, the
CONTAINER mixes, which is how a potion becomes a splash potion: it is keyed on the bottle ITEM and its
output is an ITEM. Nothing about it says the output has to be a potion.

And the stand already accepts a glass bottle in its three bottle slots — {@code BrewingStandMenu} has
allowed it since forever. So the whole feature is two vanilla calls: make the glass bottle a container
the table knows, and add a container mix from it to the oil for every oily fish.

Where those two calls live is the only thing that differs by platform, and only on 1.20.1:

  1.21.1, 26.1.2, 26.2   `PotionBrewing.Builder` has addContainer and addContainerRecipe as PUBLIC
                         methods, and all four builds already hand that builder to addMixes. No loader
                         API is touched at all.
  1.20.1 Forge           there is no Builder; BrewingRecipeRegistry.addRecipe(IBrewingRecipe) takes an
                         arbitrary recipe object, so the mix is a nine-line record next to the one the
                         potions already use.
  1.20.1 Fabric          there is no Builder and no Forge registry either. The vanilla methods exist
                         (PotionBrewing.addContainerRecipe / addContainer) and are private static, so
                         an @Invoker mixin opens them — the same shape as PoiTypesInvoker, which is
                         already in this tree for the same class of reason. Fabric's own
                         FabricBrewingRecipeRegistry cannot do it: its registerItemRecipe is typed
                         PotionItem to PotionItem, and fish oil is not a potion item.

The list of fish stays in ModPotions, once, and tools/check_oil_brew.py holds it to the furnace's tag.
"""
import io, json, os, sys

ROOT = sys.argv[1]
D = sys.argv[2] if len(sys.argv) > 2 else "1211"
J = os.path.join(ROOT, "common/src/main/java/com/riverfishing/")


def rd(p): return io.open(p, encoding="utf-8").read()


def wr(p, s): io.open(p, "w", encoding="utf-8", newline="\n").write(s)


def sub(s, old, new, what):
    assert old in s, "%s moved" % what
    return s.replace(old, new, 1)


# ---- 1. the list, once, and a sink to pour it into ------------------------------------------------
p = J + "registry/ModPotions.java"
s = rd(p)
if "oil-brew-item" not in s:
    anchor = "    private ModPotions() {}"
    assert anchor in s, "ModPotions' constructor moved"
    s = s.replace(anchor, '''    /**
     * §oil-brew-item: where one "empty bottle + fish -> oil" recipe goes. The three numbers are the
     * same everywhere; only the call that records them differs, and only on 1.20.1 (see the two
     * PlatformHelperImpl.registerBrewing). Everything above this line is potion-to-potion and needs no
     * such thing.
     */
    public interface OilSink {
        void add(net.minecraft.world.item.Item bottle, net.minecraft.world.item.Item fish,
                 net.minecraft.world.item.Item oil);
    }

    /**
     * §oil-brew-item: the oily fish, each as a container mix from a glass bottle to the oil. Vanilla's
     * CONTAINER table — the one that turns a potion into a splash potion — is keyed on the bottle item
     * and outputs an item, so a non-potion output is not a special case, it is what that table does.
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

    if D != "1201":
        # The builder these three trees already pass around has both calls, public.
        old = '        builder.addMix(Potions.AWKWARD, ModItems.FISH_OIL.get(), '
        i = s.index(old)
        end = s.index("\n", i) + 1
        s = s[:end] + '''        // §oil-brew-item: and the empty bottle. addContainer makes the glass bottle something the
        // table will look mixes up for (it is already something the stand's slots accept); the mixes
        // themselves are the fish. Both calls are vanilla and public here, so no loader API is used.
        builder.addContainer(Items.GLASS_BOTTLE);
        addOilBrews((bottle, fish, oil) -> builder.addContainerRecipe(bottle, fish, oil));
''' + s[end:]
    wr(p, s)
    print("  ModPotions: the oily fish as container mixes")

# ---- 2. 1.20.1 has no builder: one door per loader ------------------------------------------------
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
     * takes any recipe object, so this needs nothing from vanilla — 1.20.1 has no PotionBrewing.Builder
     * to ask, which is the whole reason the other three trees do it in one shared line and this one does
     * not.
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

    # …and Fabric, where the vanilla methods are private static.
    p = os.path.join(ROOT, "fabric/src/main/java/com/riverfishing/fabric/mixin/PotionBrewingInvoker.java")
    if not os.path.exists(p):
        wr(p, '''package com.riverfishing.fabric.mixin;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.PotionBrewing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * §oil-brew-item: 1.20.1 has no {@code PotionBrewing.Builder}, and the two calls that add a CONTAINER
 * mix — the table that turns a potion into a splash potion, keyed on the bottle item with an item for
 * an output — are private static. Forge opens them through its own brewing registry; on Fabric this
 * invoker is the door.
 *
 * <p>Fabric API's own {@code FabricBrewingRecipeRegistry.registerItemRecipe} cannot be used: it is
 * typed {@code PotionItem} to {@code PotionItem}, and fish oil is a plain item. Same shape and same
 * reason as {@link PoiTypesInvoker} next door.
 */
@Mixin(PotionBrewing.class)
public interface PotionBrewingInvoker {
    @Invoker("addContainerRecipe")
    static void riverfishing$addContainerRecipe(Item from, Item ingredient, Item to) {
        throw new AssertionError();
    }

    @Invoker("addContainer")
    static void riverfishing$addContainer(Item container) {
        throw new AssertionError();
    }
}
''')
        print("  fabric mixin/PotionBrewingInvoker.java")

    p = os.path.join(ROOT, "fabric/src/main/resources/riverfishing-fabric.mixins.json")
    cfg = json.load(io.open(p, encoding="utf-8"))
    if "PotionBrewingInvoker" not in cfg["mixins"]:
        cfg["mixins"].append("PotionBrewingInvoker")
        cfg["mixins"].sort()
        wr(p, json.dumps(cfg, indent=2, ensure_ascii=False) + "\n")
        print("  fabric mixins.json: +PotionBrewingInvoker")

    p = os.path.join(ROOT, "fabric/src/main/java/com/riverfishing/platform/fabric/PlatformHelperImpl.java")
    s = rd(p)
    if "oil-brew-item" not in s:
        old = """                        from, net.minecraft.world.item.crafting.Ingredient.of(ingredient), to));
    }"""
        s = sub(s, old, """                        from, net.minecraft.world.item.crafting.Ingredient.of(ingredient), to));
        // §oil-brew-item: the empty-bottle recipe, through the invoker — see PotionBrewingInvoker for
        // why Fabric's own registerItemRecipe cannot carry it.
        com.riverfishing.registry.ModPotions.addOilBrews((bottle, fish, oil) -> {
            com.riverfishing.fabric.mixin.PotionBrewingInvoker.riverfishing$addContainer(bottle);
            com.riverfishing.fabric.mixin.PotionBrewingInvoker.riverfishing$addContainerRecipe(bottle, fish, oil);
        });
    }""", "the Fabric brewing hook")
        wr(p, s)
        print("  fabric PlatformHelperImpl: the invoker call")
print("done (%s)" % D)
