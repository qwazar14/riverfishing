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
        ModRegistries.init();
        com.riverfishing.event.ModEvents.init();      // reload/tick/quit/block-break + mob-bait loot
        com.riverfishing.command.JournalCommand.init(); // /rffish
        // Stage 4: networking (was SimpleChannel) → NetworkManager, and getPersistentData → SavedData.
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
