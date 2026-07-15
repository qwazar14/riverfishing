package com.riverfishing.forge;

import com.riverfishing.RiverFishing;
import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Forge bootstrap (§multiloader): replaces the old single-project {@code @Mod} class. It hands off to
 * the common {@link RiverFishing#init()}, then — on the client dist only — to the common client
 * bootstrap. Forge-only extras (e.g. the JEI plugin) live in this module.
 */
@Mod(RiverFishing.MODID)
public final class RiverFishingForge {
    public RiverFishingForge() {
        // Architectury requires the mod's Forge event bus to be registered BEFORE any DeferredRegister
        // binds — its Forge registries look the bus up by modid. Must come first, or ModBlocks.init()
        // throws "Can't get event bus for mod 'riverfishing'". (Fabric registers directly, so no analogue.)
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        EventBuses.registerModEventBus(RiverFishing.MODID, modBus);
        RiverFishing.init();
        // Client bootstrap. Event listeners (HUD/line/model hooks) register now, during construction, while
        // the mod bus accepts listeners. The renderer registration resolves menu/BER RegistrySuppliers, which
        // Forge only binds on RegisterEvent (after this constructor) — so it's deferred to FMLClientSetupEvent.
        // The dist guard keeps the client-only class off the dedicated server's classloader.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.riverfishing.client.ClientInit.registerEvents();
            modBus.addListener((FMLClientSetupEvent e) ->
                    e.enqueueWork(com.riverfishing.client.ClientInit::registerRenderers));
        }
    }
}
