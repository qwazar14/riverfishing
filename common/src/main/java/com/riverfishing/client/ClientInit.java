package com.riverfishing.client;

import com.riverfishing.client.platform.ClientPlatform;
import com.riverfishing.registry.ModBlockEntities;
import com.riverfishing.registry.ModMenus;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent;
import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.registry.client.rendering.BlockEntityRendererRegistry;
import dev.architectury.registry.menu.MenuRegistry;

/**
 * The single common client bootstrap (§multiloader) — the Forge {@code FMLClientSetupEvent} /
 * {@code @Mod.EventBusSubscriber} client classes collapse into this, called once from each loader's
 * client entry point ({@code RiverFishingForge} on the client dist / {@code RiverFishingFabricClient}).
 *
 * <p>Screens, block-entity renderers, the HUD, the disconnect cleanup and the client command all ride
 * Architectury's own client events/registries (safe to register from mod construction — they defer to
 * the right moment on Forge). The three hooks Architectury doesn't wrap — the item BEWLR, the extra
 * models and the in-world line — go through {@link ClientPlatform}.
 */
public final class ClientInit {
    private ClientInit() {}

    public static void init() {
        // Assembly / rig screens (Forge MenuScreens.register → Architectury registerScreenFactory).
        MenuRegistry.registerScreenFactory(ModMenus.ROD_ASSEMBLY.get(), RodAssemblyScreen::new);
        MenuRegistry.registerScreenFactory(ModMenus.RIG.get(), RigScreen::new);

        // Block-entity renderers (Forge EntityRenderersEvent → Architectury BlockEntityRendererRegistry).
        BlockEntityRendererRegistry.register(ModBlockEntities.TROPHY_STAND.get(), TrophyStandRenderer::new);
        BlockEntityRendererRegistry.register(ModBlockEntities.ROD_POD.get(), RodPodRenderer::new);
        BlockEntityRendererRegistry.register(ModBlockEntities.AQUARIUM.get(), AquariumRenderer::new);

        // Float-timing + cast-power HUD (Forge RenderGuiEvent.Post → Architectury RENDER_HUD).
        ClientGuiEvent.RENDER_HUD.register(ClientHud::render);

        // Never carry a fishing line into another world (Forge ClientPlayerNetworkEvent.LoggingOut).
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> ClientLineState.clear());

        // /rfrod live pose debugger (Forge RegisterClientCommandsEvent → Architectury client command).
        ClientCommandRegistrationEvent.EVENT.register((dispatcher, registry) -> RodDebugCommand.register(dispatcher));

        // Platform-only hooks with no Architectury wrapper (§multiloader).
        ClientPlatform.registerItemRenderers();
        ClientPlatform.registerExtraModels();
        ClientPlatform.registerLevelRenderer();
    }
}
