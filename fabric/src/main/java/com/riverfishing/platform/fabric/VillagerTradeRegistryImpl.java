package com.riverfishing.platform.fabric;

import dev.architectury.registry.registries.RegistrySupplier;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;

import java.util.List;

/** Fabric impl (§multiloader): register the trades through {@link TradeOfferHelper}. */
public final class VillagerTradeRegistryImpl {
    private VillagerTradeRegistryImpl() {}

    public static void register(RegistrySupplier<VillagerProfession> profession,
                                Int2ObjectMap<List<VillagerTrades.ItemListing>> tradesByLevel) {
        tradesByLevel.forEach((level, listings) ->
                TradeOfferHelper.registerVillagerOffers(profession.get(), (int) level,
                        factories -> factories.addAll(listings)));
    }
}
