package com.riverfishing.mixin;

import com.riverfishing.item.CookFishRecipe;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * §cooking: the furnace (and the smoker, which is the same block entity) never hands a cooking recipe
 * its input — it asks {@code getResultItem} for the static result, which for a cook-fish recipe is
 * the placeholder perch. For those recipes the result is asked of the fish actually in the slot.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class FurnaceCookFishMixin {

    @Redirect(method = {"canBurn", "burn"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/crafting/Recipe;getResultItem(Lnet/minecraft/core/RegistryAccess;)Lnet/minecraft/world/item/ItemStack;"),
            require = 0)
    private static ItemStack riverfishing$cookTheFish(Recipe<?> recipe, RegistryAccess registries,
                                                       RegistryAccess access, Recipe<?> r2,
                                                       NonNullList<ItemStack> items, int maxStack) {
        if (recipe instanceof CookFishRecipe.Smelting || recipe instanceof CookFishRecipe.Smoking) {
            return ((Recipe<SimpleContainer>) recipe).assemble(new SimpleContainer(items.get(0)), registries);
        }
        return recipe.getResultItem(registries);
    }
}
