package com.riverfishing;

import com.riverfishing.platform.PlatformHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common (platform-neutral) entry point for River Fishing.
 *
 * <p>Both loaders call {@link #init()} from their own bootstrap ({@code RiverFishingForge} on Forge,
 * {@code RiverFishingFabric} on Fabric). All game logic, items, blocks, the bite engine and the GUI
 * live in this {@code common} module; anything a platform does differently (registration, events,
 * networking, per-loader renderers) is reached through the {@code platform} layer.
 *
 * <p>Migration status: STAGE 1 — scaffold only. Subsystems move in from the parked legacy Forge
 * sources stage by stage (registries → events → networking/data → client). See MIGRATION.md.
 */
public final class RiverFishing {
    public static final String MOD_ID = "riverfishing";
    public static final Logger LOG = LoggerFactory.getLogger("River Fishing");

    private RiverFishing() {}

    public static void init() {
        LOG.info("[River Fishing] common init on {} platform", PlatformHelper.platformName());
    }
}
