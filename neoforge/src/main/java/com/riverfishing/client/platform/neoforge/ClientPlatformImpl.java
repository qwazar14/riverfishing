package com.riverfishing.client.platform.neoforge;

import com.riverfishing.RiverFishing;
import com.riverfishing.client.LineRenderer;
import com.riverfishing.client.RodAssemblyScreen;
import com.riverfishing.client.RigScreen;
import com.riverfishing.registry.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge side of the client platform seam (§multiloader). §26.1: the BEWLR item renderers, extra
 * models and lure tints are data-driven now (client item definitions in assets/riverfishing/items),
 * so only the menu screens (mod bus) and the in-world line (game-bus render stage) remain.
 */
// §26.1: @EventBusSubscriber routes to the right bus by event type — the bus() attribute is gone.
@EventBusSubscriber(modid = RiverFishing.MODID, value = Dist.CLIENT)
public final class ClientPlatformImpl {
    private ClientPlatformImpl() {}

    /** Handled by {@link #onRegisterMenuScreens} on the mod bus — see there. */
    public static void registerScreens() {
    }

    /** Handled by {@link #onRegisterBlockTints} on the mod bus — see there. */
    public static void registerColors() {
    }

    /**
     * §tackle-box: the placed box's insert. Item tints are data-driven on 26.x; block tints still come
     * from Java, and NeoForge hands them out on its own mod-bus event rather than a registry call.
     */
    @SubscribeEvent
    static void onRegisterBlockTints(
            net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.BlockTintSources event) {
        for (var box : com.riverfishing.registry.ModBlocks.TACKLE_BOXES.values()) {
            event.register(com.riverfishing.client.TackleBoxTint.LAYERS, box.get());
        }
    }

    /** §26.1: no-op — layers are data-driven (force_translucent in the model; cutout is automatic). */
    /** /rfrod + /rfnet on NeoForge's own client-command event — Architectury's never fires here. */
    public static void registerClientCommands() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.RegisterClientCommandsEvent e) -> {
                    com.riverfishing.client.RodDebugCommand.register(e.getDispatcher());
                    com.riverfishing.client.KeepnetDebugCommand.register(e.getDispatcher());
                });
    }

    public static void registerRenderTypes() {
    }

    /** Handled by {@link #onRegisterSpecialModelRenderers} on the mod bus. */
    public static void registerSpecialModelRenderers() {
    }

    /** §fry-icon: the fry bucket's special model renderer, on NeoForge's own registration event. */
    @SubscribeEvent
    static void onRegisterSpecialModelRenderers(
            net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent event) {
        event.register(com.riverfishing.client.FrySpecialRenderer.ID,
                com.riverfishing.client.FrySpecialRenderer.Unbaked.MAP_CODEC);
    }

    /**
     * Register the assembly / rig screens on NeoForge's native {@link RegisterMenuScreensEvent}
     * (Architectury's deferred path fires too late on NeoForge — see the 1.21.1 port notes).
     */
    @SubscribeEvent
    static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.ROD_ASSEMBLY.get(), RodAssemblyScreen::new);
        event.register(ModMenus.RIG.get(), RigScreen::new);
        event.register(ModMenus.TACKLE_STATION.get(), com.riverfishing.client.TackleStationScreen::new);
        // §keepnet + §tackle-box (0.7.0): the two boxes.
        event.register(ModMenus.KEEPNET.get(), com.riverfishing.client.KeepnetScreen::new);
        event.register(ModMenus.TACKLE_BOX.get(), com.riverfishing.client.TackleBoxScreen::new);
        event.register(ModMenus.AQUARIUM.get(), com.riverfishing.client.AquariumScreen::new);
    }

    public static void registerLevelRenderer() {
        //? if <26.2 {
        // §26.1: RenderLevelStageEvent became typed per-stage subclasses (no getStage()).
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent.AfterTranslucentBlocks e) -> {
            LineRenderer.render(e.getPoseStack(), e.getLevelRenderState().cameraRenderState.pos,
                    net.minecraft.client.Minecraft.getInstance().getDeltaTracker()
                            .getGameTimeDeltaPartialTick(false));
        });
        // §shoal: under the water, so it has to go in BEFORE the translucent blocks — that pass writes
        // depth, and anything drawn behind the surface afterwards is thrown away. AfterOpaqueFeatures is
        // the last stage before it (26.1 has no AfterEntities; the stage list is Sky, OpaqueBlocks,
        // OpaqueFeatures, TranslucentBlocks, TranslucentFeatures, TranslucentParticles, Weather, Level).
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent.AfterOpaqueFeatures e) -> {
            com.riverfishing.client.ShoalRenderer.render(e.getPoseStack(),
                    e.getLevelRenderState().cameraRenderState.pos,
                    net.minecraft.client.Minecraft.getInstance().getDeltaTracker()
                            .getGameTimeDeltaPartialTick(false));
        });
        //?}
        // On 26.2 this is a no-op: the stage event fires at DRAW time — too late to submit retained
        // geometry. The cast line goes through the loader-neutral common LevelRendererSubmitMixin.
    }

    /** §catch-card: the fish's tooltip component gets its renderer. */
    @SubscribeEvent
    static void onRegisterTooltips(net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(com.riverfishing.item.FishCardTooltip.class,
                com.riverfishing.client.FishCardClientTooltip::new);
    }
}
