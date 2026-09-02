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
        // §catch-card: the fish's tooltip component gets its renderer.
        net.fabricmc.fabric.api.client.rendering.v1.TooltipComponentCallback.EVENT.register(data ->
                data instanceof com.riverfishing.item.FishCardTooltip t
                        ? new com.riverfishing.client.FishCardClientTooltip(t) : null);
    }
}
