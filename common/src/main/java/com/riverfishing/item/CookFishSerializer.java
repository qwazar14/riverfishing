package com.riverfishing.item;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;

/**
 * §cooking (1.20.1): vanilla's SimpleCookingSerializer takes a package-private factory, so this is
 * the same reader with a public one — group, category, ingredient, a string result, experience, time.
 */
public final class CookFishSerializer<T extends AbstractCookingRecipe> implements RecipeSerializer<T> {
    public interface Factory<T> {
        T create(ResourceLocation id, String group, CookingBookCategory category, Ingredient ingredient, ItemStack result, float experience, int cookingTime);
    }

    private final Factory<T> factory;
    private final int defaultTime;

    public CookFishSerializer(Factory<T> factory, int defaultTime) {
        this.factory = factory;
        this.defaultTime = defaultTime;
    }

    @Override
    public T fromJson(ResourceLocation id, JsonObject json) {
        String group = GsonHelper.getAsString(json, "group", "");
        CookingBookCategory category = CookingBookCategory.CODEC.byName(GsonHelper.getAsString(json, "category", null), CookingBookCategory.MISC);
        Ingredient ingredient = Ingredient.fromJson(GsonHelper.isArrayNode(json, "ingredient") ? GsonHelper.getAsJsonArray(json, "ingredient") : GsonHelper.getAsJsonObject(json, "ingredient"), false);
        String resultId = GsonHelper.getAsString(json, "result");
        ItemStack result = new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(resultId)));
        float experience = GsonHelper.getAsFloat(json, "experience", 0.0f);
        int time = GsonHelper.getAsInt(json, "cookingtime", defaultTime);
        return factory.create(id, group, category, ingredient, result, experience, time);
    }

    @Override
    public T fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
        String group = buf.readUtf();
        CookingBookCategory category = buf.readEnum(CookingBookCategory.class);
        Ingredient ingredient = Ingredient.fromNetwork(buf);
        ItemStack result = buf.readItem();
        float experience = buf.readFloat();
        int time = buf.readVarInt();
        return factory.create(id, group, category, ingredient, result, experience, time);
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf, T recipe) {
        buf.writeUtf(recipe.getGroup());
        buf.writeEnum(recipe.category());
        recipe.getIngredients().get(0).toNetwork(buf);
        buf.writeItem(recipe.getResultItem(null));
        buf.writeFloat(recipe.getExperience());
        buf.writeVarInt(recipe.getCookingTime());
    }
}
