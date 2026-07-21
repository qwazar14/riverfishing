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

    /** §26.1: no-op — the DYED_COLOR tint ships in the client item definitions now. */
    public static void registerItemColors() {
    }

    /** §26.1: no-op — layers are data-driven (force_translucent in the model; cutout is automatic). */
    public static void registerRenderTypes() {
    }

    /**
     * Register the assembly / rig screens on NeoForge's native {@link RegisterMenuScreensEvent}
     * (Architectury's deferred path fires too late on NeoForge — see the 1.21.1 port notes).
     */
    @SubscribeEvent
    static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.ROD_ASSEMBLY.get(), RodAssemblyScreen::new);
        event.register(ModMenus.RIG.get(), RigScreen::new);
    }

    public static void registerLevelRenderer() {
        //? if <26.2 {
        // §26.1: RenderLevelStageEvent became typed per-stage subclasses (no getStage()).
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent.AfterTranslucentBlocks e) -> {
            LineRenderer.render(e.getPoseStack(), e.getLevelRenderState().cameraRenderState.pos,
                    net.minecraft.client.Minecraft.getInstance().getDeltaTracker()
                            .getGameTimeDeltaPartialTick(false));
        });
        //?}
        // On 26.2 this is a no-op: the stage event fires at DRAW time — too late to submit retained
        // geometry. The cast line goes through the loader-neutral common LevelRendererSubmitMixin.
    }
}
