package com.riverfishing.water;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.riverfishing.RiverFishing;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * Worldgen placement filter {@code riverfishing:away_from_sea}: passes only where no sea is within {@code radius}.
 * Snags keep out of the ocean with it — the biome filter alone can't, because at a coast the biome is noise:
 * deep water with seagrass reads plains at the bed and ocean a few blocks over, so a snag in that "plains" is in
 * the sea to anyone looking. Rings of samples every 8 blocks, at sea level and at the spot's own height.
 */
public final class AwayFromSeaFilter extends PlacementFilter {
    public static final MapCodec<AwayFromSeaFilter> CODEC =
            Codec.intRange(0, 64).fieldOf("radius").xmap(AwayFromSeaFilter::new, f -> f.radius);

    private static final DeferredRegister<PlacementModifierType<?>> TYPES =
            DeferredRegister.create(RiverFishing.MODID, Registries.PLACEMENT_MODIFIER_TYPE);
    public static final RegistrySupplier<PlacementModifierType<AwayFromSeaFilter>> TYPE =
            TYPES.register("away_from_sea", () -> () -> CODEC);

    public static void init() {
        TYPES.register();
    }

    private final int radius;

    private AwayFromSeaFilter(int radius) {
        this.radius = radius;
    }

    /**
     * Samples ask the biome SOURCE, not the level: a worldgen region throws for any chunk beyond its neighbours
     * (even for a non-required read in 1.21), and 24 blocks out is past them. The source is the noise the chunks'
     * biomes were drawn from, answers anywhere, and is safe off the server thread.
     */
    @Override
    protected boolean shouldPlace(PlacementContext ctx, RandomSource random, BlockPos pos) {
        net.minecraft.world.level.biome.BiomeSource source = ctx.generator().getBiomeSource();
        net.minecraft.world.level.biome.Climate.Sampler sampler = ctx.getLevel().getLevel().getChunkSource().randomState().sampler();
        int sea = ctx.generator().getSeaLevel();
        for (int r = 0; r <= radius; r += 8) {
            for (int i = 0; i < (r == 0 ? 1 : 8); i++) {
                double a = i * Math.PI / 4;
                int qx = net.minecraft.core.QuartPos.fromBlock(pos.getX() + (int) Math.round(Math.cos(a) * r));
                int qz = net.minecraft.core.QuartPos.fromBlock(pos.getZ() + (int) Math.round(Math.sin(a) * r));
                if (isSea(source.getNoiseBiome(qx, net.minecraft.core.QuartPos.fromBlock(sea), qz, sampler))
                        || isSea(source.getNoiseBiome(qx, net.minecraft.core.QuartPos.fromBlock(pos.getY()), qz, sampler))) {
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

    @Override
    public PlacementModifierType<?> type() {
        return TYPE.get();
    }
}
