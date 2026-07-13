package com.riverfishing.neoforge;

import com.riverfishing.RiverFishing;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * NeoForge bootstrap (§multiloader, 1.21.1). NeoForge injects the mod's event bus into the {@code @Mod}
 * constructor, so — unlike LexForge — there's no {@code FMLJavaModLoadingContext}; Architectury 13 picks
 * the bus up itself. Hands off to the common {@link RiverFishing#init()}, then (client dist only) to the
 * common client bootstrap. Renderer registration is deferred to {@code FMLClientSetupEvent} because the
 * menu/BER RegistrySuppliers aren't bound until after construction.
 */
@Mod(RiverFishing.MODID)
public final class RiverFishingNeoForge {
    public RiverFishingNeoForge(IEventBus modBus) {
        RiverFishing.init();
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            com.riverfishing.client.ClientInit.registerEvents();
            modBus.addListener((FMLClientSetupEvent e) ->
                    e.enqueueWork(com.riverfishing.client.ClientInit::registerRenderers));
        }
    }
}
