package com.riverfishing.registry;

import com.mojang.serialization.MapCodec;
import com.riverfishing.RiverFishing;
import com.riverfishing.item.LivebaitRecipe;
import com.riverfishing.item.LureDyeRecipe;
import com.riverfishing.item.OilCakeRecipe;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
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

    // §26.1: SimpleCraftingRecipeSerializer is gone — RecipeSerializer is a record(MapCodec, StreamCodec),
    // and our special recipes are stateless, so a unit codec around one shared instance is the whole story.
    private static <T extends Recipe<?>> RecipeSerializer<T> unit(T instance) {
        return new RecipeSerializer<>(MapCodec.unit(instance), StreamCodec.unit(instance));
    }

    public static final RegistrySupplier<RecipeSerializer<LivebaitRecipe>> LIVEBAIT =
            REGISTER.register("crafting_livebait", () -> unit(new LivebaitRecipe()));

    public static final RegistrySupplier<RecipeSerializer<OilCakeRecipe>> OIL_CAKE =
            REGISTER.register("crafting_oil_cake", () -> unit(new OilCakeRecipe()));

    public static final RegistrySupplier<RecipeSerializer<LureDyeRecipe>> LURE_DYE =
            REGISTER.register("crafting_lure_dye", () -> unit(new LureDyeRecipe()));

    private ModRecipes() {}
}
