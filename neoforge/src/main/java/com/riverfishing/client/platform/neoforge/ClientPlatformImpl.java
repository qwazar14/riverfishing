package com.riverfishing.client.platform.neoforge;

import com.riverfishing.RiverFishing;
import com.riverfishing.client.ClientModels;
import com.riverfishing.client.FishItemRenderer;
import com.riverfishing.client.LineRenderer;
import com.riverfishing.client.RodItemRenderer;
import com.riverfishing.client.RodAssemblyScreen;
import com.riverfishing.client.RigScreen;
import com.riverfishing.registry.ModItems;
import com.riverfishing.registry.ModMenus;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge side of the client platform seam (§multiloader, 1.21). The BEWLR + extra-model registration
 * ride the mod bus via the auto-subscribed handlers below (no {@code Item#initializeClient} on NeoForge);
 * the in-world line rides the game-bus {@code RenderLevelStageEvent}.
 */
@EventBusSubscriber(modid = RiverFishing.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientPlatformImpl {
    private ClientPlatformImpl() {}

    /** Handled by {@link #onRegisterClientExtensions} on the mod bus (see there). */
    public static void registerItemRenderers() {
    }

    /** Handled by {@link #onRegisterMenuScreens} on the mod bus — see there. */
    public static void registerScreens() {
    }

    /**
     * Register the assembly / rig screens on NeoForge's native {@link RegisterMenuScreensEvent}. Architectury's
     * {@code registerScreenFactory} defers via {@code FMLClientSetupEvent}, which on NeoForge fires AFTER this
     * event — so its listener was added too late and the client logged "Failed to create screen for menu type"
     * (shift-right-click opened the menu server-side but no GUI appeared). Registering here fires at the right
     * time, with the menu {@code RegistrySupplier}s already bound.
     */
    @SubscribeEvent
    static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.ROD_ASSEMBLY.get(), RodAssemblyScreen::new);
        event.register(ModMenus.RIG.get(), RigScreen::new);
    }

    /**
     * Attach the composited rod icon (§rod-layers) and weight-scaled fish icon (§fish-scale) as
     * {@link IClientItemExtensions#getCustomRenderer() custom item renderers} — the NeoForge counterpart to
     * Fabric's {@code BuiltinItemRendererRegistry}. Both item models parent {@code builtin/entity}, so the
     * baked model reports {@code isCustomRenderer()} and NeoForge routes rendering through these BEWLRs.
     * (21.1.90+ does have {@code RegisterClientExtensionsEvent} — an earlier port note wrongly assumed it
     * only landed in 21.2.)
     */
    @SubscribeEvent
    static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        IClientItemExtensions rod = new IClientItemExtensions() {
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() { return RodItemRenderer.get(); }
        };
        for (RegistrySupplier<Item> r : ModItems.RODS) {
            event.registerItem(rod, r.get());
        }
        IClientItemExtensions fish = new IClientItemExtensions() {
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() { return FishItemRenderer.get(); }
        };
        for (RegistrySupplier<Item> f : ModItems.FISH_ITEMS.values()) {
            event.registerItem(fish, f.get());
        }
    }

    /** Handled by {@link #onRegisterAdditional} on the mod bus. */
    public static void registerExtraModels() {
    }

    /** NeoForge reads {@code "render_type"} from the block model JSON — nothing to do. */
    public static void registerRenderTypes() {
    }

    public static void registerLevelRenderer() {
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent e) -> {
            if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
            LineRenderer.render(e.getPoseStack(), e.getCamera().getPosition(),
                    e.getPartialTick().getGameTimeDeltaPartialTick(false));
        });
    }

    public static BakedModel bakedModel(ResourceLocation loc) {
        return Minecraft.getInstance().getModelManager().getModel(modelId(loc));
    }

    /**
     * The {@link ModelResourceLocation} an extra sprite-layer model is registered/fetched under.
     * NeoForge 21.1 requires side-loaded models (those added via {@link ModelEvent.RegisterAdditional})
     * to use the {@code standalone} variant — registering under {@code inventory} throws
     * "Side-loaded models must use the 'standalone' variant" and cascades into a resource-reload retry
     * (which then double-fires NeoForge's registration events, e.g. "Duplicate cauldron registration").
     */
    private static ModelResourceLocation modelId(ResourceLocation loc) {
        return ModelResourceLocation.standalone(loc);
    }

    @SubscribeEvent
    static void onRegisterAdditional(ModelEvent.RegisterAdditional event) {
        for (ResourceLocation loc : ClientModels.present(ClientModels.allCandidates())) {
            event.register(modelId(loc));
        }
    }

}
