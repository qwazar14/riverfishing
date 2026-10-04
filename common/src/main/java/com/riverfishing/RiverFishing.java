package com.riverfishing;

import com.riverfishing.platform.PlatformHelper;
import com.riverfishing.registry.ModRegistries;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common (platform-neutral) entry point for River Fishing. Both loaders call {@link #init()} from their
 * own bootstrap ({@code RiverFishingForge} / {@code RiverFishingFabric}).
 *
 * <p>Keeps the same {@code MODID} / {@code LOGGER} / {@code id(..)} API the ~100 game-logic classes were
 * written against, so relocating them into {@code common} needed no call-site churn.
 */
public final class RiverFishing {
    public static final String MODID = "riverfishing";
    public static final Logger LOGGER = LoggerFactory.getLogger("River Fishing");

    private RiverFishing() {}

    public static void init() {
        LOGGER.info("River Fishing: common init on {}", PlatformHelper.platformName());
        // §groundbait-mix: the pantry decides what every mix is worth, and a bad edit to it is
        // silent — wrong numbers just fish badly. Fail on load instead.
        com.riverfishing.groundbait.GroundbaitMix.selfCheck();
        com.riverfishing.config.ConfigLoader.load();    // §config: before anything reads a multiplier
        ModRegistries.init();
        PlatformHelper.registerBrewing();       // §fish-oil-potion: brewing is code, not JSON, on every loader
        // §farm-feed: the mod's seeds compost like any other seed — which also feeds the WORM FARM,
        // because that block asks the vanilla composter what counts as organic matter rather than
        // keeping a list of its own. Registered through listen() so it runs when the item actually
        // exists: on some loaders common init is earlier than the registry is filled.
        // §no-seeds: the plant baits are what is planted now — they compost as the seeds did; the cob and the pod
        // a ripe crop gives compost like a carrot
        for (dev.architectury.registry.registries.RegistrySupplier<net.minecraft.world.item.Item> seed
                : java.util.List.of(com.riverfishing.registry.ModItems.CORN, com.riverfishing.registry.ModItems.PEA,
                                    com.riverfishing.registry.ModItems.PEARL_BARLEY)) {
            seed.listen(item -> net.minecraft.world.level.block.ComposterBlock.COMPOSTABLES
                    .put(item, 0.30f));
        }
        for (dev.architectury.registry.registries.RegistrySupplier<net.minecraft.world.item.Item> crop
                : java.util.List.of(com.riverfishing.registry.ModItems.CORN_COB, com.riverfishing.registry.ModItems.PEA_POD)) {
            crop.listen(item -> net.minecraft.world.level.block.ComposterBlock.COMPOSTABLES
                    .put(item, 0.65f));
        }
        com.riverfishing.network.ModNetwork.register(); // Architectury NetworkManager (was SimpleChannel)
        com.riverfishing.event.ModEvents.init();        // reload/tick/quit/block-break + mob-bait loot
        com.riverfishing.command.JournalCommand.init(); // /rffish
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
