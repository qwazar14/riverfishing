package com.riverfishing.client.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;

/**
 * The CLIENT platform-abstraction seam (§multiloader). These three hooks have no Architectury wrapper,
 * so each loader implements them in {@code com.riverfishing.client.platform.<forge|fabric>.ClientPlatformImpl}:
 * <ul>
 *   <li>{@link #registerItemRenderers()} — the weight/layer BEWLR on the rod &amp; fish items
 *       (Forge {@code IClientItemExtensions} via a mixin / Fabric {@code BuiltinItemRendererRegistry});</li>
 *   <li>{@link #registerExtraModels()} — bake the sprite-layer models
 *       (Forge {@code ModelEvent.RegisterAdditional} / Fabric {@code ModelLoadingPlugin});</li>
 *   <li>{@link #registerLevelRenderer()} — draw the in-world fishing lines
 *       (Forge {@code RenderLevelStageEvent} / Fabric {@code WorldRenderEvents}).</li>
 * </ul>
 * Everything else (screens, BER, HUD, disconnect, the client command) rides Architectury's own
 * client events from {@link com.riverfishing.client.ClientInit}.
 */
public final class ClientPlatform {
    private ClientPlatform() {}

    @ExpectPlatform
    public static void registerItemRenderers() {
        throw new AssertionError("@ExpectPlatform stub — replaced per platform at build time");
    }

    @ExpectPlatform
    public static void registerExtraModels() {
        throw new AssertionError("@ExpectPlatform stub — replaced per platform at build time");
    }

    @ExpectPlatform
    public static void registerLevelRenderer() {
        throw new AssertionError("@ExpectPlatform stub — replaced per platform at build time");
    }

    /**
     * A baked extra model (§rod-layers / §fish-scale) by its plain {@link ResourceLocation}. Vanilla's
     * {@code ModelManager#getModel} only takes a {@code ModelResourceLocation}; the loader-patched
     * lookup (Forge's overload / Fabric's {@code FabricBakedModelManager}) lives per platform.
     */
    @ExpectPlatform
    public static BakedModel bakedModel(ResourceLocation loc) {
        throw new AssertionError("@ExpectPlatform stub — replaced per platform at build time");
    }
}
