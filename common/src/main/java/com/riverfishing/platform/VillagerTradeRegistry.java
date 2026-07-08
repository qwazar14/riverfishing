package com.riverfishing.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;
import dev.architectury.registry.registries.RegistrySupplier;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;

import java.util.List;

/**
 * Villager-trade registration seam (§multiloader). Architectury has no unified villager-trade API, so
 * this is the one spot that bridges Forge's runtime {@code VillagerTradesEvent} and Fabric's
 * {@code TradeOfferHelper}. Common builds the trades (level → listings) and hands them here.
 */
public final class VillagerTradeRegistry {
    private VillagerTradeRegistry() {}

    @ExpectPlatform
    public static void register(RegistrySupplier<VillagerProfession> profession,
                                Int2ObjectMap<List<VillagerTrades.ItemListing>> tradesByLevel) {
        throw new AssertionError("@ExpectPlatform stub — replaced per platform at build time");
    }
}
