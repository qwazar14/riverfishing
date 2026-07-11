package com.riverfishing.platform.neoforge;

import dev.architectury.registry.registries.RegistrySupplier;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;

import java.util.List;

/** NeoForge impl (§multiloader, 1.21.1): add the trades to the runtime {@link VillagerTradesEvent}. */
public final class VillagerTradeRegistryImpl {
    private VillagerTradeRegistryImpl() {}

    public static void register(RegistrySupplier<VillagerProfession> profession,
                                Int2ObjectMap<List<VillagerTrades.ItemListing>> tradesByLevel) {
        NeoForge.EVENT_BUS.addListener((VillagerTradesEvent event) -> {
            if (event.getType() != profession.get()) return;
            tradesByLevel.forEach((level, listings) -> {
                List<VillagerTrades.ItemListing> dest = event.getTrades().get((int) level);
                if (dest != null) dest.addAll(listings);
            });
        });
    }
}
