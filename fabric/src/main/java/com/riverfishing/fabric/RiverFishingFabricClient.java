package com.riverfishing.fabric;

import com.riverfishing.client.ClientInit;
import net.fabricmc.api.ClientModInitializer;

/**
 * Fabric client bootstrap (§multiloader): the {@code client} entrypoint in {@code fabric.mod.json}.
 * Mirrors Forge's client-dist hand-off — it just runs the common {@link ClientInit#init()}.
 */
public final class RiverFishingFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientInit.init();
    }
}
