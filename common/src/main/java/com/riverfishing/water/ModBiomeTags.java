package com.riverfishing.water;

import com.riverfishing.RiverFishing;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

/** Biome tags the mod ships data for (vanilla + BoP swamps), used by water classification (§10.2). */
public final class ModBiomeTags {
    public static final TagKey<Biome> IS_SWAMP =
            TagKey.create(Registries.BIOME, RiverFishing.id("is_swamp"));

    private ModBiomeTags() {}
}
