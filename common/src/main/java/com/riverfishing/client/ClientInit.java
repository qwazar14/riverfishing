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
 * <p>Split in two so the Forge timing works: {@link #registerEvents()} only adds event listeners (no
 * registry objects touched) and runs during mod construction; {@link #registerRenderers()} resolves the
 * menu/block-entity {@code RegistrySupplier}s (which on Forge aren't bound until {@code RegisterEvent},
 * after the constructor) and is deferred to {@code FMLClientSetupEvent} there. Fabric binds synchronously,
 * so {@link #init()} just runs both from {@code onInitializeClient}.
 */
public final class ClientInit {
    private ClientInit() {}

    /** Fabric client entry: everything at once — registry objects are already bound by init time. */
    public static void init() {
        registerEvents();
        registerRenderers();
    }

    /** Event listeners only — safe during Forge mod construction (nothing calls {@code .get()}). */
    public static void registerEvents() {
        // Float-timing + cast-power HUD (Forge RenderGuiEvent.Post → Architectury RENDER_HUD).
        ClientGuiEvent.RENDER_HUD.register(ClientHud::render);

        // Never carry a fishing line into another world (Forge ClientPlayerNetworkEvent.LoggingOut).
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> ClientLineState.clear());

        // /rfrod live pose debugger (Forge RegisterClientCommandsEvent → Architectury client command).
        ClientCommandRegistrationEvent.EVENT.register((dispatcher, registry) -> RodDebugCommand.register(dispatcher));

        // Platform-only event hooks (in-world line render + extra-model bake) — no registry objects.
        ClientPlatform.registerExtraModels();
        ClientPlatform.registerLevelRenderer();
    }

    /** Registry-object-dependent registration — deferred to FMLClientSetupEvent on Forge. */
    public static void registerRenderers() {
        // Assembly / rig screens — per platform: Fabric via Architectury registerScreenFactory, NeoForge via
        // the native RegisterMenuScreensEvent (Architectury's deferred path misses the event there). §multiloader
        ClientPlatform.registerScreens();

        // Block-entity renderers (Forge EntityRenderersEvent → Architectury BlockEntityRendererRegistry).
        BlockEntityRendererRegistry.register(ModBlockEntities.TROPHY_STAND.get(), TrophyStandRenderer::new);
        BlockEntityRendererRegistry.register(ModBlockEntities.ROD_POD.get(), RodPodRenderer::new);
        BlockEntityRendererRegistry.register(ModBlockEntities.AQUARIUM.get(), AquariumRenderer::new);

        // Item BEWLR — Fabric iterates the rod/fish items here (needs them bound); Forge is a mixin no-op.
        ClientPlatform.registerItemRenderers();

        // §lure-color: tint provider for painted lures (needs the items bound, so it lives here).
        ClientPlatform.registerItemColors();

        // Non-solid block render layers (aquarium glass, ice hole, bait trap) — Fabric only; Forge reads
        // "render_type" from the model. Needs the blocks bound, so it lives here with the renderers.
        ClientPlatform.registerRenderTypes();
    }
}
