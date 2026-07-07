package com.riverfishing.registry;

import com.riverfishing.RiverFishing;
import com.riverfishing.item.LivebaitRecipe;
import com.riverfishing.item.OilCakeRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;

/** Custom recipe serializers: the NBT-aware livebait conversion + the piston-press oil cake. */
public final class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> REGISTER =
            DeferredRegister.create(RiverFishing.MODID, Registries.RECIPE_SERIALIZER);

    public static void init() {
        REGISTER.register();
    }

    public static final RegistrySupplier<RecipeSerializer<LivebaitRecipe>> LIVEBAIT =
            REGISTER.register("crafting_livebait",
                    () -> new SimpleCraftingRecipeSerializer<>(LivebaitRecipe::new));

    public static final RegistrySupplier<RecipeSerializer<OilCakeRecipe>> OIL_CAKE =
            REGISTER.register("crafting_oil_cake",
                    () -> new SimpleCraftingRecipeSerializer<>(OilCakeRecipe::new));

    private ModRecipes() {}
}
