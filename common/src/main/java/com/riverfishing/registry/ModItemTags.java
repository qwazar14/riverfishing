package com.riverfishing.registry;

import com.riverfishing.RiverFishing;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

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
}
