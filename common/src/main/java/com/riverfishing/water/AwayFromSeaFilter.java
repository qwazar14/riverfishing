package com.riverfishing.water;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.riverfishing.RiverFishing;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;

/**
 * Worldgen placement filter {@code riverfishing:away_from_sea}: passes only where no sea is within {@code radius}.
 * Snags keep out of the ocean with it — the biome filter alone can't, because at a coast the biome is noise:
 * deep water with seagrass reads plains at the bed and ocean a few blocks over, so a snag in that "plains" is in
 * the sea to anyone looking. Rings of samples every 8 blocks, at sea level and at the spot's own height.
 *
 * <p>Samples ask the biome SOURCE, not the level: a worldgen region throws for any chunk beyond its neighbours, and
 * 24 blocks out is past them. §26.3: PlacementFilter is an interface whose type is its codec, the placement
 * modifier registry holds the codecs themselves, and the source answers through a resolver.
 */
public final class AwayFromSeaFilter
        //? if >=26.3 {
        /*implements PlacementFilter
        *///?} else {
        extends PlacementFilter
        //?}
{
    public static final MapCodec<AwayFromSeaFilter> CODEC =
            Codec.intRange(0, 64).fieldOf("radius").xmap(AwayFromSeaFilter::new, f -> f.radius);

    //? if >=26.3 {
    /*private static final DeferredRegister<MapCodec<? extends net.minecraft.world.level.levelgen.placement.PlacementModifier>> TYPES =
            DeferredRegister.create(RiverFishing.MODID, Registries.PLACEMENT_MODIFIER_TYPE);
    public static final RegistrySupplier<MapCodec<AwayFromSeaFilter>> TYPE = TYPES.register("away_from_sea", () -> CODEC);
    *///?} else {
    private static final DeferredRegister<net.minecraft.world.level.levelgen.placement.PlacementModifierType<?>> TYPES =
            DeferredRegister.create(RiverFishing.MODID, Registries.PLACEMENT_MODIFIER_TYPE);
    public static final RegistrySupplier<net.minecraft.world.level.levelgen.placement.PlacementModifierType<AwayFromSeaFilter>> TYPE =
            TYPES.register("away_from_sea", () -> () -> CODEC);
    //?}

    public static void init() {
        TYPES.register();
    }

    private interface Biomes {
        Holder<Biome> at(int qx, int qy, int qz);
    }

    private final int radius;

    private AwayFromSeaFilter(int radius) {
        this.radius = radius;
    }

    @Override
    public boolean shouldPlace(PlacementContext ctx, RandomSource random, BlockPos pos) {
        //? if >=26.3 {
        /*net.minecraft.world.level.biome.BiomeResolver resolver = ctx.generator().getBiomeSource()
                .createUncachedResolver(ctx.getLevel().getLevel().getChunkSource().randomState());
        Biomes biomes = resolver::getNoiseBiome;
        *///?} else {
        net.minecraft.world.level.biome.Climate.Sampler sampler = ctx.getLevel().getLevel().getChunkSource().randomState().sampler();
        Biomes biomes = (x, y, z) -> ctx.generator().getBiomeSource().getNoiseBiome(x, y, z, sampler);
        //?}
        int sea = ctx.generator().getSeaLevel();
        for (int r = 0; r <= radius; r += 8) {
            for (int i = 0; i < (r == 0 ? 1 : 8); i++) {
                double a = i * Math.PI / 4;
                int qx = QuartPos.fromBlock(pos.getX() + (int) Math.round(Math.cos(a) * r));
                int qz = QuartPos.fromBlock(pos.getZ() + (int) Math.round(Math.sin(a) * r));
                if (isSea(biomes.at(qx, QuartPos.fromBlock(sea), qz)) || isSea(biomes.at(qx, QuartPos.fromBlock(pos.getY()), qz))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isSea(Holder<Biome> b) {
        return b.is(BiomeTags.IS_OCEAN) || b.is(BiomeTags.IS_DEEP_OCEAN) || b.is(BiomeTags.IS_BEACH)
                || b.is(ModBiomeTags.IS_SALTWATER);
    }

    //? if >=26.3 {
    /*@Override
    public MapCodec<? extends PlacementFilter> codec() {
        return CODEC;
    }
    *///?} else {
    @Override
    public net.minecraft.world.level.levelgen.placement.PlacementModifierType<?> type() {
        return TYPE.get();
    }
    //?}
}
