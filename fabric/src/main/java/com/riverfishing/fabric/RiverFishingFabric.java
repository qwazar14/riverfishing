package com.riverfishing.fabric;

import com.riverfishing.RiverFishing;
import com.riverfishing.fabric.mixin.PoiTypesInvoker;
import com.riverfishing.registry.ModVillagers;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.entity.ai.village.poi.PoiType;

/**
 * Fabric bootstrap (§multiloader): implements {@link ModInitializer} in place of Forge's {@code @Mod}
 * annotation + event-bus wiring, and hands off to the common {@link RiverFishing#init()}. Registered
 * via the {@code main} entrypoint in {@code fabric.mod.json}.
 */
public final class RiverFishingFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        RiverFishing.init();

        // §fabric-poi: Architectury registers the Fishing-Stall PoiType but not its blockstate→POI mapping
        // (Forge auto-does this), so villagers never claim the job site or take the fisherman profession.
        // Map the stall's states into PoiTypes.TYPE_BY_STATE ourselves now that the registry is bound.
        Holder<PoiType> poi = BuiltInRegistries.POINT_OF_INTEREST_TYPE.get(ModVillagers.FISHERMAN_POI.getKey()).orElseThrow();
        PoiTypesInvoker.riverfishing$registerBlockStates(poi, poi.value().matchingStates());
        // §i: the warden's post, the same way.

        // §cherry-pond: koi live only in cherry groves and vanilla digs no lakes there, so there was nowhere to
        // catch one. Forge/NeoForge add the pond through a biome_modifier JSON; Fabric has no data-driven
        // equivalent, so the same placed feature is attached here.
        // §modded-biomes: a sakura grove is a cherry grove, and the koi do not read release notes.
        BiomeModifications.addFeature(
                BiomeSelectors.tag(com.riverfishing.water.ModBiomeTags.IS_CHERRY),
                GenerationStep.Decoration.LAKES,
                ResourceKey.create(Registries.PLACED_FEATURE, RiverFishing.id("cherry_pond")));
    }
}
