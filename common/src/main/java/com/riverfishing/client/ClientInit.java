package com.riverfishing.client;

import com.riverfishing.client.platform.ClientPlatform;
import com.riverfishing.registry.ModBlockEntities;
import com.riverfishing.registry.ModMenus;
import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.client.ClientTickEvent;
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
        RodClientSettings.load();   // §rod-client-settings: /rfrod toggles survive relaunches (both loaders pass here)
        // s2c-split (0.4.0): S2C packet receivers are CLIENT-only - dedicated servers crash on the
        // dist-stripped receiver path (see ModNetwork).
        com.riverfishing.network.ModNetwork.registerClientReceivers();

        // §fight-keys: the rod's four bindings. HERE and not in registerRenderers() — NeoForge
        // flushes queued mappings on RegisterKeyMappingsEvent, and anything later only gets in by
        // hand-patching Options. Building a KeyMapping touches no registry object, so it honours
        // this method's contract.
        FightKeys.register();

        // Float-timing + cast-power HUD (Forge RenderGuiEvent.Post → Architectury RENDER_HUD).
        ClientGuiEvent.RENDER_HUD.register(ClientHud::render);

        // §fight-poll-tick: the fight keys are polled on the TICK, not from the renderer. A frame is not
        // a unit of game time — polling there made the input rate the framerate, and let the final
        // "hands off" go unsent whenever the line was not being drawn.
        ClientTickEvent.CLIENT_POST.register(mc -> ClientLineState.pollFightInput());

        // Never carry a fishing line into another world (Forge ClientPlayerNetworkEvent.LoggingOut).
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> ClientLineState.clear());

        // update-check (0.4.0): one quiet version digest per game launch, on first world join.
        ClientPlayerEvent.CLIENT_PLAYER_JOIN.register(player -> UpdateChecker.onJoin());

        // /rfrod + /rfnet, per loader. These used to ride Architectury's client-command event, which
        // never fires on this line — everything after it in this method ran, and the commands did not
        // exist in game. Each loader's own registration path does fire, so that is what they use now.
        ClientPlatform.registerClientCommands();

        // Platform-only event hook (in-world line render) — no registry objects. §26.1: the extra-model
        // bake is gone with the BEWLR icons; item models are data-driven client items now.
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

        // §tackle-box: block tints (needs the blocks bound, so it lives here).
        ClientPlatform.registerColors();

        // Non-solid block render layers (aquarium glass, ice hole, bait trap) — Fabric only; Forge reads
        // "render_type" from the model. Needs the blocks bound, so it lives here with the renderers.
        ClientPlatform.registerRenderTypes();
    }
}
