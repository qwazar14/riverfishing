package com.riverfishing.integration.jei;

import com.riverfishing.RiverFishing;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraftforge.registries.ForgeRegistries;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;

import java.util.List;

/**
 * JEI entry point (§ pack integration). Only loaded when JEI is present, so the mod stays soft-dependent.
 * Registers the "Fishing" category and one entry per species, plus a couple of catalysts so players can
 * look up "how do I catch this" straight from the journal or a rod.
 */
@JeiPlugin
public class RiverFishingJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = RiverFishing.id("jei");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(
                new FishingRecipeCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(FishingRecipeCategory.TYPE, FishingRecipeCategory.recipes());

        // The oil-cake press is a custom (special) recipe, so JEI won't pick it up automatically —
        // add a display-only shapeless entry: sunflower + piston -> 3× oil cake (the piston is kept).
        var cake = ForgeRegistries.ITEMS.getValue(RiverFishing.id("groundbait_cake"));
        if (cake != null) {
            NonNullList<Ingredient> ings = NonNullList.create();
            ings.add(Ingredient.of(Items.SUNFLOWER));
            ings.add(Ingredient.of(Items.PISTON));
            ShapelessRecipe display = new ShapelessRecipe(RiverFishing.id("oil_cake_jei"), "",
                    CraftingBookCategory.MISC, new ItemStack(cake, 3), ings);
            registration.addRecipes(RecipeTypes.CRAFTING, List.of(display));
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        catalyst(registration, "fishing_journal");
        catalyst(registration, "spinning_rod");
        catalyst(registration, "feeder_rod");
    }

    private static void catalyst(IRecipeCatalystRegistration registration, String itemPath) {
        var item = ForgeRegistries.ITEMS.getValue(RiverFishing.id(itemPath));
        if (item != null) {
            registration.addRecipeCatalyst(new ItemStack(item), FishingRecipeCategory.TYPE);
        }
    }
}
