package com.riverfishing.registry;

import com.riverfishing.RiverFishing;
import com.riverfishing.item.LivebaitRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;

public final class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> REGISTER =
            DeferredRegister.create(RiverFishing.MODID, Registries.RECIPE_SERIALIZER);

    public static void init() {
        REGISTER.register();
    }

    public static final RegistrySupplier<RecipeSerializer<LivebaitRecipe>> LIVEBAIT =
            REGISTER.register("crafting_livebait",
                    () -> new SimpleCraftingRecipeSerializer<>(LivebaitRecipe::new));


    public static final RegistrySupplier<RecipeSerializer<com.riverfishing.item.LureDyeRecipe>> LURE_DYE =
            REGISTER.register("crafting_lure_dye",
                    () -> new SimpleCraftingRecipeSerializer<>(com.riverfishing.item.LureDyeRecipe::new));

    // §tackle-box: dye the inserts — colour is how you tell four boxes apart.
    public static final RegistrySupplier<RecipeSerializer<com.riverfishing.item.TackleBoxDyeRecipe>> TACKLE_BOX_DYE =
            REGISTER.register("crafting_tackle_box_dye",
                    () -> new SimpleCraftingRecipeSerializer<>(com.riverfishing.item.TackleBoxDyeRecipe::new));

    // §groundbait-mix (0.8.0): stir your own groundbait — one item in the grid is one spoon.
    public static final RegistrySupplier<RecipeSerializer<com.riverfishing.groundbait.GroundbaitMixRecipe>>
            GROUNDBAIT_MIX = REGISTER.register("crafting_groundbait_mix",
                    () -> new SimpleCraftingRecipeSerializer<>(
                            com.riverfishing.groundbait.GroundbaitMixRecipe::new));

    private ModRecipes() {}
}
