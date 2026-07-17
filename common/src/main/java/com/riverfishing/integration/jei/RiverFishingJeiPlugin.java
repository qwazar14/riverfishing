package com.riverfishing.integration.jei;

import com.riverfishing.RiverFishing;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapelessRecipe;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI entry point (§pack-integration, 0.4.0). Lives in COMMON: JEI discovers {@code @JeiPlugin} by
 * annotation scan on both loaders, and the API is compileOnly, so without JEI installed this class is
 * simply never loaded — the mod stays soft-dependent. (1.20.1 dialect: JEI 15, no RecipeHolder,
 * ShapelessRecipe carries its own id.)
 *
 * <p>The mod's three special crafts are {@code CustomRecipe}s, invisible to JEI's automatic crafting
 * scan — register display-only shapeless stand-ins so players can look them up: the oil-cake press
 * (piston is kept!), livebait from any small fish, and lure dyeing. The "how do I catch X" knowledge
 * deliberately stays in the in-game journal, which reads live fish profiles.
 */
@JeiPlugin
public class RiverFishingJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = RiverFishing.id("jei");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<CraftingRecipe> recipes = new ArrayList<>();

        // Oil cake: sunflower pressed by a piston (the piston survives the craft).
        Item cake = item("groundbait_cake");
        if (cake != Items.AIR) {
            recipes.add(display("jei_oil_cake", new ItemStack(cake, 6),
                    Ingredient.of(Items.SUNFLOWER), Ingredient.of(Items.PISTON)));
        }
        // Livebait: any small caught fish (≤150 g) becomes one live bait.
        Item livebait = item("livebait");
        if (livebait != Items.AIR) {
            recipes.add(display("jei_livebait", new ItemStack(livebait),
                    Ingredient.of(net.minecraft.tags.TagKey.create(
                            net.minecraft.core.registries.Registries.ITEM, RiverFishing.id("fishes")))));
        }
        // Lure dyeing: an artificial lure + any dyes, leather-armour style (popper as the stand-in).
        Item popper = item("popper");
        if (popper != Items.AIR) {
            recipes.add(display("jei_lure_dye", new ItemStack(popper),
                    Ingredient.of(popper),
                    Ingredient.of(Items.RED_DYE, Items.YELLOW_DYE, Items.LIME_DYE, Items.LIGHT_BLUE_DYE)));
        }
        registration.addRecipes(RecipeTypes.CRAFTING, recipes);
    }

    private static Item item(String path) {
        return BuiltInRegistries.ITEM.get(RiverFishing.id(path));
    }

    private static CraftingRecipe display(String id, ItemStack result, Ingredient... ings) {
        NonNullList<Ingredient> list = NonNullList.create();
        for (Ingredient i : ings) list.add(i);
        return new ShapelessRecipe(RiverFishing.id(id), "riverfishing.jei", CraftingBookCategory.MISC, result, list);
    }
}
