package com.riverfishing.registry;

import com.riverfishing.RiverFishing;
import com.riverfishing.item.LivebaitRecipe;
import com.riverfishing.item.OilCakeRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Custom recipe serializers: the NBT-aware livebait conversion + the piston-press oil cake. */
public final class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> REGISTER =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, RiverFishing.MODID);

    public static final RegistryObject<RecipeSerializer<LivebaitRecipe>> LIVEBAIT =
            REGISTER.register("crafting_livebait",
                    () -> new SimpleCraftingRecipeSerializer<>(LivebaitRecipe::new));

    public static final RegistryObject<RecipeSerializer<OilCakeRecipe>> OIL_CAKE =
            REGISTER.register("crafting_oil_cake",
                    () -> new SimpleCraftingRecipeSerializer<>(OilCakeRecipe::new));

    private ModRecipes() {}
}
