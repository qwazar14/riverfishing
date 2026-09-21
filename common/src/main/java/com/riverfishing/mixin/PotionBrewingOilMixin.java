package com.riverfishing.mixin;

import com.riverfishing.registry.ModItems;
import com.riverfishing.registry.ModPotions;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionBrewing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * §oil-stand (1.0.0): an oily fish over a glass bottle brews into fish oil — on every loader. Vanilla's
 * brewing table type-checks both ends as potions, Forge and NeoForge open a door for an item result and
 * Fabric opens none, so the oil's only road there was the furnace — where, now that every fish cooks,
 * it fought the cooked fish for the same input. Three questions the stand asks, answered here first:
 * is this an ingredient, is there a mix, what comes out.
 */
@Mixin(PotionBrewing.class)
public abstract class PotionBrewingOilMixin {

    @Inject(method = "isIngredient", at = @At("HEAD"), cancellable = true)
    private void riverfishing$oilIngredient(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (ModPotions.isOilyFish(stack)) cir.setReturnValue(true);
    }

    @Inject(method = "hasMix", at = @At("HEAD"), cancellable = true)
    private void riverfishing$oilHasMix(ItemStack input, ItemStack reagent, CallbackInfoReturnable<Boolean> cir) {
        if (ModPotions.isOilyFish(reagent)) cir.setReturnValue(input.is(Items.GLASS_BOTTLE));
    }

    @Inject(method = "mix", at = @At("HEAD"), cancellable = true)
    private void riverfishing$oilMix(ItemStack reagent, ItemStack potion, CallbackInfoReturnable<ItemStack> cir) {
        if (ModPotions.isOilyFish(reagent) && potion.is(Items.GLASS_BOTTLE)) cir.setReturnValue(new ItemStack(ModItems.FISH_OIL.get()));
    }
}
