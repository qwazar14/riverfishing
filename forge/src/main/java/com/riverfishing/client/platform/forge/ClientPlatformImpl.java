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

    public static void registerScreens() {
        dev.architectury.registry.menu.MenuRegistry.registerScreenFactory(
                com.riverfishing.registry.ModMenus.ROD_ASSEMBLY.get(), com.riverfishing.client.RodAssemblyScreen::new);
        dev.architectury.registry.menu.MenuRegistry.registerScreenFactory(
                com.riverfishing.registry.ModMenus.RIG.get(), com.riverfishing.client.RigScreen::new);
    }

    /**
     * §lure-color: dyed artificial lures tint layer 0 from their display.color NBT. Called from
     * FMLClientSetupEvent.enqueueWork — RegisterColorHandlersEvent has already fired by then, so
     * register straight on the live ItemColors instead of listening for it.
     */
    public static void registerItemColors() {
        net.minecraft.client.color.item.ItemColor tint = (stack, tintIndex) -> {
            if (tintIndex != 0) return -1;
            int rgb = com.riverfishing.item.DyeUtil.color(stack);
            return rgb >= 0 ? (0xFF000000 | rgb) : -1;
        };
        for (dev.architectury.registry.registries.RegistrySupplier<net.minecraft.world.item.Item> r
                : com.riverfishing.registry.ModItems.ALL) {
            if (r.get() instanceof com.riverfishing.item.BaitItem b && b.artificial()) {
                Minecraft.getInstance().getItemColors().register(tint, r.get());
            }
        }
    }

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

    /** Forge reads {@code "render_type"} from each block model JSON, so nothing to do here. */
    public static void registerRenderTypes() {
    }

    /** Forge patches {@code getModel(ResourceLocation)} straight onto the model manager. */
    public static BakedModel bakedModel(ResourceLocation loc) {
        return Minecraft.getInstance().getModelManager().getModel(loc);
    }
}
