package com.riverfishing.client.platform.neoforge;

import com.riverfishing.RiverFishing;
import com.riverfishing.client.ClientModels;
import com.riverfishing.client.FishItemRenderer;
import com.riverfishing.client.LineRenderer;
import com.riverfishing.client.RodItemRenderer;
import com.riverfishing.registry.ModItems;
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
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge side of the client platform seam (§multiloader, 1.21). The BEWLR + extra-model registration
 * ride the mod bus via the auto-subscribed handlers below (no {@code Item#initializeClient} on NeoForge);
 * the in-world line rides the game-bus {@code RenderLevelStageEvent}.
 */
@EventBusSubscriber(modid = RiverFishing.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientPlatformImpl {
    private ClientPlatformImpl() {}

    /**
     * TODO(1.21/NeoForge): attach the rod/fish composite BEWLR. NeoForge 21.1.x has no
     * {@code RegisterClientExtensionsEvent} (that lands in 21.2+), so the mechanism to register
     * {@code IClientItemExtensions#getCustomRenderer} here is still to be wired — until then the rod/fish
     * icons fall back to their builtin/entity model on NeoForge. (Fabric renders them correctly.)
     */
    public static void registerItemRenderers() {
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
