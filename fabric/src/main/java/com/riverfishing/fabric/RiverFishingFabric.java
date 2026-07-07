package com.riverfishing.fabric;

import com.riverfishing.RiverFishing;
import net.fabricmc.api.ModInitializer;

/**
 * Fabric bootstrap (§multiloader): implements {@link ModInitializer} in place of Forge's {@code @Mod}
 * annotation + event-bus wiring, and hands off to the common {@link RiverFishing#init()}. Registered
 * via the {@code main} entrypoint in {@code fabric.mod.json}.
 */
public final class RiverFishingFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        RiverFishing.init();
    }
}
