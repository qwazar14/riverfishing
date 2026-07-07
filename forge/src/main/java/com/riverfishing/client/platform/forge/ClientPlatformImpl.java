package com.riverfishing.client.platform.forge;

import com.riverfishing.client.ClientModels;
import com.riverfishing.client.LineRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Forge side of the client platform seam (§multiloader) — see
 * {@link com.riverfishing.client.platform.ClientPlatform}. Called once from the client bootstrap during
 * mod construction, so the mod event bus is live and we can still add the model/render listeners.
 */
public final class ClientPlatformImpl {
    private ClientPlatformImpl() {}

    /**
     * The rod/fish BEWLR is attached through {@code Item#initializeClient}, which is patched onto our
     * common items by {@code RodItemForgeMixin} / {@code FishItemForgeMixin}. Nothing to register here.
     */
    public static void registerItemRenderers() {
    }

    public static void registerExtraModels() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(ClientPlatformImpl::onRegisterAdditional);
    }

    private static void onRegisterAdditional(ModelEvent.RegisterAdditional event) {
        for (ResourceLocation loc : ClientModels.present(ClientModels.allCandidates())) {
            event.register(loc);
        }
    }

    public static void registerLevelRenderer() {
        MinecraftForge.EVENT_BUS.addListener(ClientPlatformImpl::onRenderLevel);
    }

    private static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        LineRenderer.render(event.getPoseStack(), event.getCamera().getPosition(), event.getPartialTick());
    }

    /** Forge patches {@code getModel(ResourceLocation)} straight onto the model manager. */
    public static BakedModel bakedModel(ResourceLocation loc) {
        return Minecraft.getInstance().getModelManager().getModel(loc);
    }
}
