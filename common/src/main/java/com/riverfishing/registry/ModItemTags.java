package com.riverfishing.registry;

import com.riverfishing.RiverFishing;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * §farm-feed: what the maggot farm will take.
 *
 * <p>It used to be one hard-coded item — rotten flesh — which is both thin and unextendable: a pack
 * that wanted spoiled meat from another mod to work had no way in. A tag costs the same in code and
 * hands the decision to data, which is where "what counts as carrion" belongs.
 */
public final class ModItemTags {
    private ModItemTags() {}

    public static final TagKey<Item> MAGGOT_FOOD =
            TagKey.create(Registries.ITEM, RiverFishing.id("maggot_food"));

    /**
     * §pattern-gate: the species that carry a pattern index — the carps and the koi, whose colours the
     * index actually turns. It is a tag rather than a list in Java so the answer stays in data: put a
     * fish item in here and that species starts rolling indices, breeding them through its roe, showing
     * the row on its card and paying the gem's six times. Nothing in code has to know.
     */
    public static final TagKey<Item> PATTERNED =
            TagKey.create(Registries.ITEM, RiverFishing.id("patterned"));

    /** Does this fish wear a pattern? Asked of the STACK, so it works on a client with no profiles. */
    public static boolean patterned(ItemStack fish) {
        return fish.is(PATTERNED);
    }

    /** The same, for a species that has no stack in hand — the journal's board, a roe tooltip. */
    public static boolean patterned(ResourceLocation species) {
        RegistrySupplier<Item> item = species == null ? null : ModItems.FISH_ITEMS.get(species);
        return item != null && new ItemStack(item.get()).is(PATTERNED);
    }
}
