package com.riverfishing.water;

import com.riverfishing.RiverFishing;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

/** Biome tags the mod ships data for (vanilla + BoP swamps), used by water classification (§10.2). */
public final class ModBiomeTags {
    public static final TagKey<Biome> IS_SWAMP =
            TagKey.create(Registries.BIOME, RiverFishing.id("is_swamp"));

    /**
     * §modded-biomes: Terralith and Oh The Biomes You'll Go add a couple of hundred biomes; vanilla
     * tags carry forest / taiga / mountain / jungle, but nothing says whether the water in one is
     * SALT — and the mod's sea species hang off exactly that. tools/gen_biome_tags.py writes the
     * lists; an absent mod loads its entries as nothing (they are all {@code required: false}).
     */
    public static final TagKey<Biome> IS_SALTWATER =
            TagKey.create(Registries.BIOME, RiverFishing.id("is_saltwater"));
    public static final TagKey<Biome> IS_FRESHWATER =
            TagKey.create(Registries.BIOME, RiverFishing.id("is_freshwater"));
    /** A sakura grove is a cherry grove by another name, and the koi do not read release notes. */
    public static final TagKey<Biome> IS_CHERRY =
            TagKey.create(Registries.BIOME, RiverFishing.id("is_cherry"));
    /** Where reeds / cattails grow wild along the water (worldgen placed_feature patch_reed, patch_cattail). */
    public static final TagKey<Biome> HAS_REEDS =
            TagKey.create(Registries.BIOME, RiverFishing.id("has_reeds"));
    public static final TagKey<Biome> HAS_CATTAILS =
            TagKey.create(Registries.BIOME, RiverFishing.id("has_cattails"));
    /** Where snag piles lie on the bed of fresh water (placed_feature snags) — scenery, not a player's upgrade. */
    public static final TagKey<Biome> HAS_SNAGS =
            TagKey.create(Registries.BIOME, RiverFishing.id("has_snags"));

    private ModBiomeTags() {}
}
