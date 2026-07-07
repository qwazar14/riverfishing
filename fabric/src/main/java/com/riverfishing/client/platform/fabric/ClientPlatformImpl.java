package com.riverfishing.client.platform.fabric;

import com.riverfishing.client.ClientModels;
import com.riverfishing.client.FishItemRenderer;
import com.riverfishing.client.LineRenderer;
import com.riverfishing.client.RodItemRenderer;
import com.riverfishing.registry.ModItems;
import dev.architectury.registry.registries.RegistrySupplier;
import net.fabricmc.fabric.api.client.model.loading.v1.FabricBakedModelManager;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * Fabric side of the client platform seam (§multiloader) — see
 * {@link com.riverfishing.client.platform.ClientPlatform}. Called from {@code onInitializeClient}.
 */
public final class ClientPlatformImpl {
    private ClientPlatformImpl() {}

    public static void registerItemRenderers() {
        // The composited rod icon (§rod-layers) — one shared renderer over all rod blanks.
        BuiltinItemRendererRegistry.DynamicItemRenderer rod =
                (stack, ctx, pose, buffers, light, overlay) ->
                        RodItemRenderer.get().renderByItem(stack, ctx, pose, buffers, light, overlay);
        for (RegistrySupplier<Item> r : ModItems.RODS) {
            BuiltinItemRendererRegistry.INSTANCE.register(r.get(), rod);
        }
        // The weight-scaled fish icon (§fish-scale) — one shared renderer over every species item.
        BuiltinItemRendererRegistry.DynamicItemRenderer fish =
                (stack, ctx, pose, buffers, light, overlay) ->
                        FishItemRenderer.get().renderByItem(stack, ctx, pose, buffers, light, overlay);
        for (RegistrySupplier<Item> f : ModItems.FISH_ITEMS.values()) {
            BuiltinItemRendererRegistry.INSTANCE.register(f.get(), fish);
        }
    }

    public static void registerExtraModels() {
        ModelLoadingPlugin.register(ctx -> ctx.addModels(ClientModels.present(ClientModels.allCandidates())));
    }

    public static void registerLevelRenderer() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context ->
                LineRenderer.render(context.matrixStack(), context.camera().getPosition(), context.tickDelta()));
    }

    /** Fabric's model-loading API mixes {@code getModel(ResourceLocation)} in via FabricBakedModelManager. */
    public static BakedModel bakedModel(ResourceLocation loc) {
        return ((FabricBakedModelManager) Minecraft.getInstance().getModelManager()).getModel(loc);
    }
}
