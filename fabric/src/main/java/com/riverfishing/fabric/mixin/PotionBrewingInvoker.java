package com.riverfishing.fabric.mixin;

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
