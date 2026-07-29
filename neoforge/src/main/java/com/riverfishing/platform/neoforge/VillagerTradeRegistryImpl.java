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

    private static boolean containsSame(List<VillagerTrades.ItemListing> dest,
                                        VillagerTrades.ItemListing listing) {
        for (VillagerTrades.ItemListing existing : dest) {
            if (existing == listing) return true;
        }
        return false;
    }

    public static void register(RegistrySupplier<VillagerProfession> profession,
                                Int2ObjectMap<List<VillagerTrades.ItemListing>> tradesByLevel) {
        NeoForge.EVENT_BUS.addListener((VillagerTradesEvent event) -> {
            if (event.getType() != profession.get()) return;
            tradesByLevel.forEach((level, listings) -> {
                List<VillagerTrades.ItemListing> dest = event.getTrades().get((int) level);
                if (dest == null) return;
                // Add each listing ONCE. VillagerTradesEvent can fire more than once over a session, and
                // a blind addAll would stack our pool on top of itself every time — the fisherman would
                // then draw from a list where our offers outnumber vanilla's several to one. Reported as
                // "жители с тремя рыбами сильно чаще" on NeoForge, and this is the only asymmetry between
                // the loaders that could produce it. Identity comparison is exactly right: the listings
                // are the same lambda objects on every fire.
                for (VillagerTrades.ItemListing listing : listings) {
                    if (!containsSame(dest, listing)) dest.add(listing);
                }
            });
        });
    }
}
